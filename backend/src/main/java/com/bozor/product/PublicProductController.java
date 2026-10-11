package com.bozor.product;

import com.bozor.common.error.ApiException;
import com.bozor.common.web.PageResponse;
import com.bozor.common.web.Pages;
import com.bozor.image.Image;
import com.bozor.image.ImageService;
import com.bozor.product.dto.ProductDetail;
import com.bozor.product.dto.ProductSummary;
import com.bozor.store.PublicStoreService;
import com.bozor.store.Store;
import com.bozor.store.dto.PublicStoreDetail;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자: 상품 검색·상세, 상점의 상품 목록 (docs/03-api.md 5.7). 로그인 없이 볼 수 있다. */
@RestController
public class PublicProductController {
    private final ProductQueryRepository query;
    private final PublicStoreService publicStoreService;
    private final ImageService imageService;

    public PublicProductController(ProductQueryRepository query, PublicStoreService publicStoreService,
                                   ImageService imageService) {
        this.query = query;
        this.publicStoreService = publicStoreService;
        this.imageService = imageService;
    }

    @GetMapping("/api/v1/products")
    @Transactional(readOnly = true)
    public PageResponse<ProductSummary> search(@RequestParam(required = false) String q,
                                               @RequestParam(name = "category_id", required = false) Long categoryId,
                                               @RequestParam(name = "market_id", required = false) Long marketId,
                                               @RequestParam(name = "store_slug", required = false) String storeSlug,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(required = false) String locale,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        PageRequest p = Pages.of(page, size);
        return query.search(new ProductQueryRepository.Condition(q, categoryId, marketId, storeSlug, parseSort(sort)),
                locale, p.getPageNumber(), p.getPageSize());
    }

    @GetMapping("/api/v1/stores/{slug}/products")
    @Transactional(readOnly = true)
    public PageResponse<ProductSummary> storeProducts(@PathVariable String slug,
                                                      @RequestParam(name = "category_id", required = false) Long categoryId,
                                                      @RequestParam(required = false) String sort,
                                                      @RequestParam(required = false) String locale,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        publicStoreService.requireActive(slug);   // 비공개 상점이면 404
        PageRequest p = Pages.of(page, size);
        return query.search(new ProductQueryRepository.Condition(null, categoryId, null, slug, parseSort(sort)),
                locale, p.getPageNumber(), p.getPageSize());
    }

    @GetMapping("/api/v1/products/{productId}")
    @Transactional(readOnly = true)
    public ProductDetail detail(@PathVariable Long productId, @RequestParam(required = false) String locale) {
        ProductQueryRepository.Row row = query.findPublic(productId, locale)
                .orElseThrow(() -> ApiException.notFound("상품"));
        ProductSummary s = row.summary();
        Store store = publicStoreService.requireActive(s.store().slug());
        List<Long> imageIds = query.imageIdsOf(productId);
        Map<Long, Image> images = imageService.findAll(imageIds);
        return new ProductDetail(s.id(), s.name(), row.description(), s.locale(), row.autoTranslated(),
                s.price(), s.currency(),
                s.priceType(), s.unit(), s.stockQuantity(),
                imageIds.stream().map(images::get).filter(i -> i != null).map(ImageService::response).toList(),
                s.category(),
                new ProductDetail.Store(store.getId(), store.getSlug(), store.getName(), s.store().marketName(),
                        row.locationDetail(),
                        new PublicStoreDetail.Contact(store.getContactPhone(), store.getWhatsappPhone(),
                                store.getInstagramHandle(), store.getTelegramUsername())));
    }

    private static ProductQueryRepository.Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return null;
        try {
            return ProductQueryRepository.Sort.valueOf(sort.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("sort", "recent, price_asc, price_desc, relevance 중 하나여야 합니다.");
        }
    }
}
