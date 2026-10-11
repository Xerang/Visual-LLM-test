package com.bozor.store.dto;

public record PublicStoreSummary(String slug, String name, String template, String logoUrl,
                                 StoreResponse.MarketRef market, String locationDetail, long productCount) {}
