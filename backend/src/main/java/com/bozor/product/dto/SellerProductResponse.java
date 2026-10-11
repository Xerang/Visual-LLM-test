package com.bozor.product.dto;

import com.bozor.catalog.CategoryService.CategoryRef;
import com.bozor.image.ImageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 판매자용 상품 정보. 모든 언어 번역과 출처(AI 스캔)를 함께 준다. */
public record SellerProductResponse(
        Long id,
        Long storeId,
        String status,
        CategoryRef category,
        BigDecimal price,
        String currency,
        String priceType,
        String unit,
        BigDecimal stockQuantity,
        List<Translation> translations,
        List<ImageResponse> images,
        Source source,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public record Translation(String locale, String name, String description, boolean isAutoTranslated) {}

    public record Source(Long scanJobId, Long candidateId) {}
}
