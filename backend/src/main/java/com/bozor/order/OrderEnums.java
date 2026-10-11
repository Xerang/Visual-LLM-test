package com.bozor.order;

import java.util.Map;
import java.util.Set;

/** 주문 상태값(docs/03-api.md 값 목록·6장). */
public final class OrderEnums {
    private OrderEnums() {}

    public enum Status {
        REQUESTED, CONFIRMED, READY, COMPLETED, REJECTED, CANCELLED;

        private static final Map<Status, Set<Status>> BY_SELLER = Map.of(
                REQUESTED, Set.of(CONFIRMED, REJECTED),
                CONFIRMED, Set.of(READY),
                READY, Set.of(COMPLETED));

        public boolean sellerCanChangeTo(Status next) {
            return BY_SELLER.getOrDefault(this, Set.of()).contains(next);
        }

        /** 구매자는 판매자가 준비를 마치기 전까지만 취소할 수 있다. */
        public boolean buyerCanCancel() {
            return this == REQUESTED || this == CONFIRMED;
        }
    }

    /** PICKUP = 매장 픽업, DELIVERY = 판매자가 직접 배송(배송 추적은 범위 밖) */
    public enum FulfillmentType { PICKUP, DELIVERY }

    /** CASH = 대면·착불 현금, TRANSFER = 지갑·QR 송금(판매자가 확인). 실결제(PG)는 범위 밖. */
    public enum PaymentMethod { CASH, TRANSFER }
}
