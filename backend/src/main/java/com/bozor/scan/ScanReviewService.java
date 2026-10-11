package com.bozor.scan;

import com.bozor.catalog.CategoryService;
import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import com.bozor.common.error.FieldErrorItem;
import com.bozor.product.Product;
import com.bozor.product.ProductService;
import com.bozor.product.ProductStatus;
import com.bozor.product.dto.ProductInput;
import com.bozor.scan.ScanEnums.Decision;
import com.bozor.scan.ScanEnums.JobStatus;
import com.bozor.scan.ScanEnums.Origin;
import com.bozor.scan.dto.ScanDtos;
import com.bozor.store.Store;
import com.bozor.store.StoreService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검수 확정(docs/03-api.md 5.5). 한 번의 요청·한 트랜잭션으로 처리한다.
 * - 모든 AI 후보를 ACCEPTED/REJECTED 로 결정하게 강제한다 → 지표 계산에 PENDING 이 남지 않는다.
 * - AI가 놓친 상품(candidate_id=null)은 origin=SELLER 후보로 남긴다 → 재현율 계산에 쓴다.
 * - AI 후보가 없고 판매자도 추가하지 않으면(items 비어 있음) 상품 없이 검수만 끝낸다.
 *   SNS 게시물에는 가게 전경처럼 상품이 아닌 사진도 섞여 있어서, 이것을 끝낼 수 있어야 일괄 등록이 끝난다.
 */
@Service
public class ScanReviewService {
    private final ScanJobService scanJobService;
    private final ScanCandidateRepository candidates;
    private final StoreService storeService;
    private final ProductService productService;
    private final CategoryService categoryService;

    public ScanReviewService(ScanJobService scanJobService, ScanCandidateRepository candidates,
                             StoreService storeService, ProductService productService,
                             CategoryService categoryService) {
        this.scanJobService = scanJobService;
        this.candidates = candidates;
        this.storeService = storeService;
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @Transactional
    public ScanDtos.ReviewResponse review(Long jobId, Long userId, ScanDtos.ReviewRequest request) {
        ScanJob job = scanJobService.getOwned(jobId, userId);
        if (job.getReviewedAt() != null) {
            throw new ApiException(ErrorCode.SCAN_JOB_ALREADY_REVIEWED, "이미 검수를 마친 스캔입니다.");
        }
        if (job.getStatus() != JobStatus.SUCCEEDED) {
            throw new ApiException(ErrorCode.SCAN_JOB_NOT_REVIEWABLE, "AI 분석이 끝난 뒤에 검수할 수 있습니다.");
        }
        Store store = storeService.getOwned(job.getStoreId(), userId);
        checkEveryAiCandidateDecided(job, request.items());

        Map<Long, ScanCandidate> byId = job.getCandidates().stream()
                .collect(Collectors.toMap(ScanCandidate::getId, Function.identity()));
        List<Long> scanImages = job.getImages().stream().map(ScanJobImageRef::getImageId).toList();
        List<ScanDtos.ReviewedProduct> created = new ArrayList<>();

        for (int i = 0; i < request.items().size(); i++) {
            ScanDtos.ReviewItem item = request.items().get(i);
            String field = "items[" + i + "].";
            ScanCandidate candidate;
            if (item.candidateId() != null) {
                candidate = byId.get(item.candidateId());
                if (item.decision() == Decision.PENDING) {
                    throw ApiException.invalid(field + "decision", "ACCEPTED 또는 REJECTED 를 골라 주세요.");
                }
                candidate.decide(item.decision());
                if (item.decision() == Decision.REJECTED) continue;
            } else {
                // AI가 놓친 상품: 판매자가 입력한 값으로 SELLER 후보를 남긴다.
                if (item.decision() != Decision.ACCEPTED) {
                    throw ApiException.invalid(field + "decision", "직접 추가한 상품은 ACCEPTED 여야 합니다.");
                }
                ProductInput p = requireProduct(item, field);
                categoryService.requireLeaf(p.categoryId(), field + "product.category_id");
                ProductInput.TranslationInput first = p.translations().get(0);
                candidate = ScanCandidate.addedBySeller(job, job.nextCandidateIndex(), first.name().strip(),
                        first.description(), p.categoryId());
                job.addCandidate(candidate);
                candidates.save(candidate);
            }
            ProductInput input = requireProduct(item, field);
            if (request.publish() && input.price() == null) {
                throw new ApiException(ErrorCode.PRODUCT_PRICE_REQUIRED, "바로 발행하려면 모든 상품에 가격이 필요합니다.",
                        List.of(new FieldErrorItem(field + "product.price", "가격을 입력해 주세요.")));
            }
            Product product = productService.create(store, input, field + "product.", candidate.getId(), scanImages);
            if (request.publish()) {
                productService.changeStatus(product, ProductStatus.PUBLISHED);
            }
            created.add(new ScanDtos.ReviewedProduct(product.getId(), candidate.getId(), product.getStatus().name(),
                    product.getTranslations().get(0).getName(), product.getPrice(), product.getCurrency(),
                    product.getUnit().name()));
        }
        job.markReviewed();
        return new ScanDtos.ReviewResponse(job.getId(), job.getReviewedAt(), created);
    }

    private static ProductInput requireProduct(ScanDtos.ReviewItem item, String field) {
        if (item.product() == null) {
            throw ApiException.invalid(field + "product", "수락한 상품의 정보를 입력해 주세요.");
        }
        return item.product();
    }

    /** AI 후보가 빠짐없이, 한 번씩만 결정됐는지 확인한다. */
    private static void checkEveryAiCandidateDecided(ScanJob job, List<ScanDtos.ReviewItem> items) {
        Set<Long> aiIds = job.getCandidates().stream()
                .filter(c -> c.getOrigin() == Origin.AI).map(ScanCandidate::getId).collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            Long id = items.get(i).candidateId();
            if (id == null) continue;
            if (!aiIds.contains(id)) {
                throw ApiException.invalid("items[" + i + "].candidate_id", "이 스캔의 후보가 아닙니다: " + id);
            }
            if (!seen.add(id)) {
                throw ApiException.invalid("items[" + i + "].candidate_id", "같은 후보가 두 번 들어 있습니다: " + id);
            }
        }
        List<FieldErrorItem> missing = aiIds.stream().filter(id -> !seen.contains(id)).sorted()
                .map(id -> new FieldErrorItem("items", "결정하지 않은 후보: " + id)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.SCAN_REVIEW_INCOMPLETE,
                    "AI 후보 " + missing.size() + "개를 수락하거나 거절해 주세요.", missing);
        }
    }
}
