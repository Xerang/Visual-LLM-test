package com.bozor.common.error;

import java.util.List;
import org.springframework.http.ProblemDetail;

/** 오류 응답(RFC 9457 ProblemDetail + code + errors)을 한 곳에서 만든다. */
public final class Problems {
    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail, List<FieldErrorItem> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        problem.setProperty("errors", errors);
        return problem;
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        return of(code, detail, List.of());
    }

    /** Java 필드 경로(items[0].categoryId)를 요청 JSON 경로(items[0].category_id)로 바꾼다. */
    public static String toSnakeCase(String path) {
        return path.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
