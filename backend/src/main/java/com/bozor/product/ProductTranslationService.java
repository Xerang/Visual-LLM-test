package com.bozor.product;

import com.bozor.catalog.CategoryService;
import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import com.bozor.common.i18n.Locales;
import com.bozor.product.dto.SellerProductResponse;
import com.bozor.scan.vision.VisionClient;
import com.bozor.scan.vision.VisionException;
import com.bozor.scan.vision.VisionTranslateRequest;
import com.bozor.scan.vision.VisionTranslateResult;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 다국어 상품 정보(2차 회의 추가 기능 "다국어 상품 정보(타직어·러시아어·영어)").
 * 판매자가 확정한 상품명·설명을 원문으로 AI 번역 초안을 만들고 is_auto_translated=true 로 저장한다.
 *
 * - 판매자가 요청할 때만 만든다(상품 하나에 AI 호출 1번). 무료 키 한도(임시 결정 #7)를 아끼기 위해서다.
 * - 판매자가 직접 쓴 번역(자동 아님)은 절대 덮어쓰지 않는다. 자동 번역은 다시 만들면 바뀐다.
 * - AI 호출(수 초)은 DB 트랜잭션 밖에서 한다(스캔 워커와 같은 이유).
 */
@Service
public class ProductTranslationService {
    /** 2차 회의에서 정한 대상 언어 */
    static final List<String> DEFAULT_TARGETS = List.of("tg", "ru", "en");
    private static final int MAX_DESCRIPTION = 2000;

    private final ProductService productService;
    private final CategoryService categoryService;
    private final VisionClient vision;
    private final Locales locales;
    private final TransactionTemplate tx;

    public ProductTranslationService(ProductService productService, CategoryService categoryService,
                                     VisionClient vision, Locales locales, TransactionTemplate tx) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.vision = vision;
        this.locales = locales;
        this.tx = tx;
    }

    public SellerProductResponse autoTranslate(Long productId, Long userId, List<String> requested) {
        Plan plan = tx.execute(status -> plan(productService.getOwned(productId, userId), requested));
        VisionTranslateResult result;
        try {
            result = vision.translate(plan.request());
        } catch (VisionException e) {
            throw new ApiException(e.retryable() ? ErrorCode.AI_UNAVAILABLE : ErrorCode.AI_BAD_RESPONSE, e.getMessage());
        }
        Map<String, VisionTranslateResult.Translation> byLocale = new HashMap<>();
        for (VisionTranslateResult.Translation t : result.translations()) {
            if (plan.request().targetLocales().contains(t.locale()) && t.name() != null && !t.name().isBlank()) {
                byLocale.put(t.locale(), t);
            }
        }
        if (byLocale.size() != plan.request().targetLocales().size()) {
            throw new ApiException(ErrorCode.AI_BAD_RESPONSE, "AI가 일부 언어의 번역을 돌려주지 않았습니다. 다시 시도해 주세요.");
        }
        return tx.execute(status -> {
            Product product = productService.getOwned(productId, userId);
            ProductTranslation source = find(product, plan.request().sourceLocale());
            if (source == null || !source.sameText(plan.request().name(), plan.request().description())) {
                throw ApiException.invalid("translations", "번역하는 동안 원문이 바뀌었습니다. 다시 시도해 주세요.");
            }
            for (String locale : plan.request().targetLocales()) {
                VisionTranslateResult.Translation t = byLocale.get(locale);
                product.putAutoTranslation(locale, cut(t.name().strip(), 120), cut(t.description(), MAX_DESCRIPTION));
            }
            return productService.toResponse(product);
        });
    }

    /** 원문: 판매자가 직접 쓴 번역 중 앱 기본 언어 우선. 대상: 요청 언어(없으면 tg·ru·en)에서 원문과 판매자가 쓴 언어를 뺀다. */
    private Plan plan(Product product, List<String> requested) {
        ProductTranslation source = product.getTranslations().stream()
                .filter(t -> !t.isAutoTranslated())
                .min(Comparator.comparing((ProductTranslation t) -> !t.getLocale().equals(locales.defaultLocale()))
                        .thenComparing(ProductTranslation::getLocale))
                .orElseThrow(() -> ApiException.invalid("translations", "판매자가 직접 쓴 상품명이 없어 번역할 수 없습니다."));
        List<String> targets = (requested == null || requested.isEmpty() ? DEFAULT_TARGETS : requested).stream()
                .distinct()
                .filter(loc -> !loc.equals(source.getLocale()))
                .filter(loc -> {
                    ProductTranslation existing = find(product, loc);
                    return existing == null || existing.isAutoTranslated();
                })
                .toList();
        if (targets.isEmpty()) {
            throw ApiException.invalid("locales", "번역할 언어가 없습니다. 판매자가 직접 쓴 언어는 덮어쓰지 않습니다.");
        }
        String category = categoryService.nameOf(categoryService.get(product.getCategoryId()), source.getLocale());
        return new Plan(new VisionTranslateRequest(source.getLocale(), targets, source.getName(),
                source.getDescription(), category));
    }

    private static ProductTranslation find(Product product, String locale) {
        return product.getTranslations().stream().filter(t -> t.getLocale().equals(locale)).findFirst().orElse(null);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private record Plan(VisionTranslateRequest request) {}
}
