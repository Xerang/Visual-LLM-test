package com.bozor.store.dto;

import java.util.List;

/** 구매자용 상점 화면. 판매자 전용 필드(상태·주인 id 등)는 넣지 않는다. */
public record PublicStoreDetail(
        String slug,
        String name,
        String description,
        String template,
        String themeColor,
        String logoUrl,
        String bannerUrl,
        StoreResponse.MarketRef market,
        String locationDetail,
        String addressText,
        String landmark,
        Contact contact,
        List<CategoryCount> categories,
        long productCount) {

    public record Contact(String phone, String whatsappPhone, String instagramHandle, String telegramUsername) {}

    public record CategoryCount(Long id, String code, String name, long productCount) {}
}
