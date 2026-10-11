package com.bozor.product.dto;

import java.math.BigDecimal;

/** 구매자용 상품 카드. locale 은 실제로 쓴 번역 언어다(요청 언어 번역이 없으면 대체됨). */
public record ProductSummary(
        Long id,
        String name,
        String locale,
        BigDecimal price,
        String currency,
        String priceType,
        String unit,
        BigDecimal stockQuantity,
        String thumbnailUrl,
        CategoryBrief category,
        StoreBrief store) {

    public record CategoryBrief(Long id, String code, String name) {}

    /** id 는 장바구니에서 상점별로 주문을 나눌 때 쓴다. */
    public record StoreBrief(Long id, String slug, String name, String marketName) {}
}
