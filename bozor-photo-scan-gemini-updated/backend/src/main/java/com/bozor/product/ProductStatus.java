package com.bozor.product;

import java.util.Map;
import java.util.Set;

/** 상품 상태와 허용되는 변경(docs/03-api.md 6장). */
public enum ProductStatus {
    DRAFT, PUBLISHED, HIDDEN, ARCHIVED;

    private static final Map<ProductStatus, Set<ProductStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(PUBLISHED, ARCHIVED),   // AI 검수로 만든 초안은 지우지 않고 보관(지표용 연결 유지)
            PUBLISHED, Set.of(HIDDEN, ARCHIVED),
            HIDDEN, Set.of(PUBLISHED, ARCHIVED),
            ARCHIVED, Set.of());   // 주문 기록 보존용 최종 상태

    public boolean canChangeTo(ProductStatus next) {
        return ALLOWED.get(this).contains(next);
    }
}
