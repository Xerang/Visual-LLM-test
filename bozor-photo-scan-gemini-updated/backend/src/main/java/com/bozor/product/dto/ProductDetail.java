package com.bozor.product.dto;

import com.bozor.image.ImageResponse;
import com.bozor.store.dto.PublicStoreDetail;
import java.math.BigDecimal;
import java.util.List;

/** 구매자용 상품 상세. 상품 카드 정보 + 설명·전체 사진·상점 연락처. */
public record ProductDetail(
        Long id,
        String name,
        String description,
        String locale,
        BigDecimal price,
        String currency,
        String priceType,
        String unit,
        BigDecimal stockQuantity,
        List<ImageResponse> images,
        ProductSummary.CategoryBrief category,
        Store store) {

    public record Store(Long id, String slug, String name, String marketName, String locationDetail,
                        PublicStoreDetail.Contact contact) {}
}
