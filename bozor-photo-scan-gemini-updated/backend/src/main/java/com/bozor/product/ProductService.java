package com.bozor.product;

import com.bozor.catalog.Category;
import com.bozor.catalog.CategoryService;
import com.bozor.catalog.SaleUnit;
import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import com.bozor.common.i18n.Locales;
import com.bozor.common.web.PageResponse;
import com.bozor.common.web.Pages;
import com.bozor.image.Image;
import com.bozor.image.ImageService;
import com.bozor.product.dto.ProductInput;
import com.bozor.product.dto.SellerProductResponse;
import com.bozor.store.Store;
import com.bozor.store.StoreService;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자의 상품 관리. 등록·수정 규칙을 한 곳(build)에 모아 직접 등록과 AI 검수 확정이 같은 규칙을 쓴다. */
@Service
@Transactional
public class ProductService {
    private final ProductRepository products;
    private final StoreService storeService;
    private final CategoryService categoryService;
    private final ImageService imageService;
    private final Locales locales;
    private final NamedParameterJdbcTemplate jdbc;

    public ProductService(ProductRepository products, StoreService storeService, CategoryService categoryService,
                          ImageService imageService, Locales locales, NamedParameterJdbcTemplate jdbc) {
        this.products = products;
        this.storeService = storeService;
        this.categoryService = categoryService;
        this.imageService = imageService;
        this.locales = locales;
        this.jdbc = jdbc;
    }

    // ---- 등록·수정 ---------------------------------------------------------

    /** AI를 거치지 않은 직접 등록. 항상 DRAFT로 만든다. */
    public SellerProductResponse createDirect(Long storeId, Long userId, ProductInput input) {
        Store store = storeService.getOwned(storeId, userId);
        Product product = create(store, input, "", null, List.of());
        return toResponse(product);
    }

    /**
     * 상품 하나를 만든다(DRAFT). 직접 등록과 AI 검수 확정이 함께 쓴다.
     * @param field           오류 응답에 표시할 요청 경로 앞부분(예: "items[0].product.")
     * @param defaultImageIds image_ids 를 비웠을 때 쓸 사진(검수 확정이면 스캔 사진)
     */
    public Product create(Store store, ProductInput input, String field, Long sourceCandidateId,
                          List<Long> defaultImageIds) {
        Product product = new Product(store.getId(), sourceCandidateId);
        apply(product, store, input, field, defaultImageIds);
        return products.save(product);
    }

    /** 상품 수정 폼 저장(전체 교체). 발행 중인 상품은 가격을 지울 수 없다. */
    public SellerProductResponse update(Long productId, Long userId, ProductInput input) {
        Product product = getOwned(productId, userId);
        Store store = storeService.getOwned(product.getStoreId(), userId);
        apply(product, store, input, "", List.of());
        if (product.getStatus() == ProductStatus.PUBLISHED && product.getPrice() == null) {
            throw new ApiException(ErrorCode.PRODUCT_PRICE_REQUIRED, "발행 중인 상품은 가격을 비울 수 없습니다.");
        }
        return toResponse(product);
    }

    private void apply(Product product, Store store, ProductInput in, String field, List<Long> defaultImageIds) {
        Category category = categoryService.requireLeaf(in.categoryId(), field + "category_id");
        SaleUnit unit = in.unit() != null ? in.unit()
                : category.getDefaultUnit() != null ? category.getDefaultUnit() : SaleUnit.PIECE;
        if (in.stockQuantity() != null && unit.countable() && !isWhole(in.stockQuantity())) {
            throw ApiException.invalid(field + "stock_quantity", "개수 단위 상품의 재고는 정수여야 합니다.");
        }
        Set<String> seen = new HashSet<>();
        for (ProductInput.TranslationInput t : in.translations()) {
            if (!seen.add(t.locale())) {
                throw ApiException.invalid(field + "translations", "같은 언어가 두 번 들어 있습니다: " + t.locale());
            }
        }
        List<Long> imageIds = in.imageIds() == null || in.imageIds().isEmpty() ? defaultImageIds : in.imageIds();
        imageService.requireOwned(imageIds, store.getOwnerId(), field + "image_ids");
        product.apply(category.getId(), in.price(),
                in.priceType() != null ? in.priceType() : PriceType.FIXED, unit, in.stockQuantity(),
                in.translations().stream()
                        .map(t -> new ProductTranslation(t.locale(), t.name().strip(), t.description()))
                        .toList(),
                imageIds);
    }

    // ---- 상태·재고 ---------------------------------------------------------

    /** 발행·숨김·보관. 발행하려면 가격이 있어야 한다. */
    public SellerProductResponse changeStatus(Long productId, Long userId, ProductStatus next) {
        Product product = getOwned(productId, userId);
        changeStatus(product, next);
        return toResponse(product);
    }

    public void changeStatus(Product product, ProductStatus next) {
        if (product.getStatus() == next) return;
        if (!product.getStatus().canChangeTo(next)) {
            throw new ApiException(ErrorCode.PRODUCT_STATUS_INVALID,
                    product.getStatus() + " 상품은 " + next + "(으)로 바꿀 수 없습니다.");
        }
        if (next == ProductStatus.PUBLISHED && product.getPrice() == null) {
            throw new ApiException(ErrorCode.PRODUCT_PRICE_REQUIRED, "가격을 입력해야 발행할 수 있습니다.");
        }
        product.changeStatus(next);
    }

    /** 판매자가 재고를 직접 고친다. null 이면 "재고 관리 안 함", 0 이면 품절. */
    public SellerProductResponse changeStock(Long productId, Long userId, BigDecimal stockQuantity) {
        Product product = getOwned(productId, userId);
        if (stockQuantity != null && product.getUnit().countable() && !isWhole(stockQuantity)) {
            throw ApiException.invalid("stock_quantity", "개수 단위 상품의 재고는 정수여야 합니다.");
        }
        product.changeStock(stockQuantity);
        return toResponse(product);
    }

    /** 초안만 지울 수 있다. 발행된 적이 있으면 주문 기록이 참조하므로 ARCHIVED 로 보관한다. */
    public void delete(Long productId, Long userId) {
        Product product = getOwned(productId, userId);
        if (product.getStatus() != ProductStatus.DRAFT || product.getSourceCandidateId() != null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_DELETABLE,
                    "초안 상태의 직접 등록 상품만 지울 수 있습니다. 판매를 멈추려면 숨김이나 보관을 쓰세요.");
        }
        products.delete(product);
    }

    // ---- 조회 ------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<SellerProductResponse> list(Long storeId, Long userId, ProductStatus status,
                                                    Integer page, Integer size) {
        storeService.getOwned(storeId, userId);
        Page<Product> result = status == null
                ? products.findByStoreIdOrderByIdDesc(storeId, Pages.of(page, size))
                : products.findByStoreIdAndStatusOrderByIdDesc(storeId, status, Pages.of(page, size));
        return PageResponse.from(result, this::toResponse);
    }

    @Transactional(readOnly = true)
    public SellerProductResponse get(Long productId, Long userId) {
        return toResponse(getOwned(productId, userId));
    }

    @Transactional(readOnly = true)
    public Product getOwned(Long productId, Long userId) {
        Product product = products.findById(productId).orElseThrow(() -> ApiException.notFound("상품"));
        storeService.getOwned(product.getStoreId(), userId);
        return product;
    }

    @Transactional(readOnly = true)
    public SellerProductResponse toResponse(Product p) {
        Map<Long, Image> images = imageService.findAll(p.getImages().stream().map(ProductImageRef::getImageId).toList());
        SellerProductResponse.Source source = null;
        if (p.getSourceCandidateId() != null) {
            Long jobId = jdbc.queryForObject("SELECT scan_job_id FROM scan_candidates WHERE id = :id",
                    new MapSqlParameterSource("id", p.getSourceCandidateId()), Long.class);
            source = new SellerProductResponse.Source(jobId, p.getSourceCandidateId());
        }
        return new SellerProductResponse(p.getId(), p.getStoreId(), p.getStatus().name(),
                categoryService.ref(categoryService.get(p.getCategoryId()), locales.defaultLocale()),
                p.getPrice(), p.getCurrency(), p.getPriceType().name(), p.getUnit().name(), p.getStockQuantity(),
                p.getTranslations().stream()
                        .map(t -> new SellerProductResponse.Translation(t.getLocale(), t.getName(),
                                t.getDescription(), t.isAutoTranslated()))
                        .toList(),
                p.getImages().stream().map(ref -> images.get(ref.getImageId()))
                        .filter(i -> i != null).map(ImageService::response).toList(),
                source, p.getPublishedAt(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private static boolean isWhole(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }
}
