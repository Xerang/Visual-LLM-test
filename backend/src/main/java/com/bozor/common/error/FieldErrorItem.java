package com.bozor.common.error;

/** 필드별 입력 오류. field 는 요청 JSON 의 경로(snake_case)다. 예: items[0].product.price */
public record FieldErrorItem(String field, String message) {}
