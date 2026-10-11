package com.bozor.common.error;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 컨트롤러의 예외를 docs/03-api.md 의 오류 형식으로 바꾼다. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        return Problems.of(ex.code(), ex.getMessage(), ex.errors());
    }

    /** @Valid 검사 실패: 필드별 사유를 errors 에 담는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalid(MethodArgumentNotValidException ex) {
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorItem(Problems.toSnakeCase(e.getField()), e.getDefaultMessage()))
                .toList();
        return Problems.of(ErrorCode.VALIDATION_FAILED, "입력값을 확인해 주세요.", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return Problems.of(ErrorCode.VALIDATION_FAILED, "요청 본문(JSON) 형식이나 값의 종류가 올바르지 않습니다.");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ProblemDetail handleBadParameter(Exception ex) {
        return Problems.of(ErrorCode.VALIDATION_FAILED, "요청 파라미터를 확인해 주세요.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return Problems.of(ErrorCode.IMAGE_TOO_LARGE, "사진은 5MB 이하로 올려 주세요.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return Problems.of(ErrorCode.NOT_FOUND, "없는 주소입니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethod(HttpRequestMethodNotSupportedException ex) {
        return Problems.of(ErrorCode.NOT_FOUND, "지원하지 않는 요청 방식입니다.");
    }

    /** 예상하지 못한 오류: 내용은 로그에만 남기고 응답에는 일반 문장만 보낸다. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception", ex);
        return Problems.of(ErrorCode.INTERNAL_ERROR, "잠시 후 다시 시도해 주세요.");
    }
}
