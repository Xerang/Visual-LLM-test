package com.bozor.scan.dto;

import com.bozor.catalog.CategoryService.CategoryRef;
import com.bozor.common.i18n.Locales;
import com.bozor.image.ImageResponse;
import com.bozor.product.dto.ProductInput;
import com.bozor.scan.ScanEnums.Decision;
import com.bozor.scan.ScanEnums.SourceType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 스캔 API 요청·응답 형식(docs/03-api.md 5.5). */
public final class ScanDtos {
    private ScanDtos() {}

    /** 사진 1~3장. output_locale 을 비우면 앱 기본 언어. */
    public record CreateRequest(
            @NotEmpty @Size(max = 3) List<@NotNull Long> imageIds,
            @NotNull SourceType sourceType,
            @Size(max = 2000) String captionText,
            @Pattern(regexp = Locales.PATTERN) String outputLocale) {}

    /** 목록에서는 candidates 를 빼고 candidate_count 만 준다. */
    public record JobResponse(
            Long id,
            Long storeId,
            Long importBatchId,
            String status,
            String sourceType,
            String captionText,
            String outputLocale,
            List<ImageResponse> images,
            int attemptCount,
            Instant availableAt,
            String model,
            String promptVersion,
            Integer elapsedMs,
            ErrorInfo error,
            List<String> imageNotes,
            int candidateCount,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<CandidateResponse> candidates,
            Instant createdAt,
            Instant finishedAt,
            Instant reviewedAt) {}

    public record ErrorInfo(String code, String message) {}

    public record CandidateResponse(
            Long id,
            int candidateIndex,
            String origin,
            String name,
            String description,
            CategoryRef category,
            List<String> visibleText,
            List<String> reviewNotes,
            BigDecimal suggestedPrice,
            String decision,
            Long productId) {}

    /**
     * 검수 확정. AI 후보는 모두 한 번씩 ACCEPTED/REJECTED 로 결정해야 한다.
     * candidate_id 가 null 이면 AI가 놓쳐 판매자가 추가한 상품이다.
     */
    /** AI 후보가 0개인 스캔(상품이 없는 사진)은 items 를 비워서 "상품 없음"으로 확정할 수 있다. */
    public record ReviewRequest(
            boolean publish,
            @NotNull @Size(max = 20) List<@Valid ReviewItem> items) {}

    public record ReviewItem(
            Long candidateId,
            @NotNull Decision decision,
            @Valid ProductInput product) {}

    public record ReviewResponse(Long scanJobId, Instant reviewedAt, List<ReviewedProduct> products) {}

    public record ReviewedProduct(Long id, Long candidateId, String status, String name, BigDecimal price,
                                  String currency, String unit) {}
}
