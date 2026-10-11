package com.bozor.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드. 프런트는 HTTP 상태가 아니라 이 코드로 분기한다(docs/03-api.md 1장).
 * 제목은 운영 언어(현재 한국어)로 쓴다.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    IMAGE_INVALID(HttpStatus.BAD_REQUEST, "사진을 읽을 수 없습니다"),
    SCAN_REVIEW_INCOMPLETE(HttpStatus.BAD_REQUEST, "모든 AI 후보를 확인해 주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다"),
    STORE_SLUG_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 상점 주소입니다"),
    STORE_CONTACT_REQUIRED(HttpStatus.CONFLICT, "상점을 공개하려면 연락처가 필요합니다"),
    PRODUCT_PRICE_REQUIRED(HttpStatus.CONFLICT, "발행하려면 가격이 필요합니다"),
    PRODUCT_STATUS_INVALID(HttpStatus.CONFLICT, "바꿀 수 없는 상품 상태입니다"),
    PRODUCT_NOT_DELETABLE(HttpStatus.CONFLICT, "초안 상품만 삭제할 수 있습니다"),
    SCAN_JOB_NOT_REVIEWABLE(HttpStatus.CONFLICT, "아직 분석이 끝나지 않은 스캔입니다"),
    SCAN_JOB_ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 검수한 스캔입니다"),
    SCAN_JOB_NOT_RETRYABLE(HttpStatus.CONFLICT, "실패한 스캔만 다시 시도할 수 있습니다"),
    IMPORT_BATCH_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 분석을 요청한 묶음입니다"),
    ORDER_ITEM_UNAVAILABLE(HttpStatus.CONFLICT, "주문할 수 없는 상품이 있습니다"),
    ORDER_STATUS_INVALID(HttpStatus.CONFLICT, "바꿀 수 없는 주문 상태입니다"),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "사진이 너무 큽니다"),
    AI_BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 응답을 처리하지 못했습니다"),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI를 지금 사용할 수 없습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
