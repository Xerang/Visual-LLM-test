package com.bozor.store;

import com.bozor.common.web.PageResponse;
import com.bozor.store.dto.PublicStoreDetail;
import com.bozor.store.dto.PublicStoreSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자: 상점 조회 (docs/03-api.md 5.7). 상점 상품 목록은 PublicProductController 에 있다. */
@RestController
@RequestMapping("/api/v1/stores")
public class PublicStoreController {
    private final PublicStoreService publicStoreService;

    public PublicStoreController(PublicStoreService publicStoreService) {
        this.publicStoreService = publicStoreService;
    }

    @GetMapping
    public PageResponse<PublicStoreSummary> list(@RequestParam(name = "market_id", required = false) Long marketId,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return publicStoreService.list(marketId, q, page, size);
    }

    @GetMapping("/{slug}")
    public PublicStoreDetail detail(@PathVariable String slug, @RequestParam(required = false) String locale) {
        return publicStoreService.detail(slug, locale);
    }
}
