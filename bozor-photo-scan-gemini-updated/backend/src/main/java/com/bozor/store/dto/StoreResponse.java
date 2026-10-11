package com.bozor.store.dto;

import com.bozor.image.ImageResponse;
import java.time.Instant;

/** 판매자용 상점 정보(모든 필드). */
public record StoreResponse(
        Long id,
        String slug,
        String name,
        String description,
        String template,
        String themeColor,
        ImageResponse logo,
        ImageResponse banner,
        MarketRef market,
        String locationDetail,
        String addressText,
        String landmark,
        String contactPhone,
        String whatsappPhone,
        String instagramHandle,
        String telegramUsername,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public record MarketRef(Long id, String name) {}
}
