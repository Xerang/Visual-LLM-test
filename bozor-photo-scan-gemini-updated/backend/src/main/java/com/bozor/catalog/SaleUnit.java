package com.bozor.catalog;

/** 판매 단위. 말린 과일·향신료(KG), 원단(METER)처럼 무게·길이로 파는 상품이 많다(조사 §5). */
public enum SaleUnit {
    PIECE, KG, LITER, METER, SET;

    /** 개수 단위는 소수 수량을 받지 않는다. */
    public boolean countable() {
        return this == PIECE || this == SET;
    }
}
