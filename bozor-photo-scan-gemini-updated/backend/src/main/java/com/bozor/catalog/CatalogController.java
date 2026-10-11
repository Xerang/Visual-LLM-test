package com.bozor.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기준 데이터(카테고리·시장). 누구나 조회할 수 있다. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CategoryService categoryService;
    private final MarketRepository markets;

    public CatalogController(CategoryService categoryService, MarketRepository markets) {
        this.categoryService = categoryService;
        this.markets = markets;
    }

    @GetMapping("/categories")
    public List<CategoryService.CategoryNode> categories(@RequestParam(required = false) String locale) {
        return categoryService.tree(locale);
    }

    @GetMapping("/markets")
    public List<MarketResponse> markets() {
        return markets.findAllByOrderByIdAsc().stream()
                .map(m -> new MarketResponse(m.getId(), m.getName(), m.getCity()))
                .toList();
    }

    public record MarketResponse(Long id, String name, String city) {}
}
