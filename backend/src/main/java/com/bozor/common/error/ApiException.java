package com.bozor.common.error;

import java.util.List;

/**
 * 서비스에서 던지는 예상된 오류. GlobalExceptionHandler가 ProblemDetail로 바꿔 응답한다.
 * detail 은 화면에 그대로 보여줄 문장이다.
 */
public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final List<FieldErrorItem> errors;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public ApiException(ErrorCode code, String detail, List<FieldErrorItem> errors) {
        super(detail);
        this.code = code;
        this.errors = errors;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + "을(를) 찾을 수 없습니다.");
    }

    public static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message,
                List.of(new FieldErrorItem(field, message)));
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldErrorItem> errors() {
        return errors;
    }
}
