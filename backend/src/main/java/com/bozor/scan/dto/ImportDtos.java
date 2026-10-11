package com.bozor.scan.dto;

import com.bozor.common.i18n.Locales;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** SNS 사진 일괄 등록 API 형식(docs/03-api.md 5.5). */
public final class ImportDtos {
    private ImportDtos() {}

    /** 한 묶음에 게시물 최대 20개. 근거: 2차 지표 "이미지 20장 기준", 무료 AI 키 한도(임시 결정 #7). */
    public static final int MAX_ITEMS = 20;

    public record SubmitRequest(
            @NotEmpty @Size(max = MAX_ITEMS) List<@Valid @NotNull Item> items,
            @Pattern(regexp = Locales.PATTERN) String outputLocale) {}

    /** 게시물 하나 = 사진 1~3장(캐러셀) + 캡션. 단건 스캔과 같은 규칙. */
    public record Item(
            @NotEmpty @Size(max = 3) List<@NotNull Long> imageIds,
            @Size(max = 2000) String captionText) {}

    public record BatchResponse(
            Long id,
            Long storeId,
            Instant createdAt,
            Instant submittedAt,
            Progress progress,
            /** 모든 게시물의 검수를 마친 시각(마지막 검수 시각). 아직이면 null. */
            Instant completedAt,
            /** 온보딩 소요시간(초) = created_at → completed_at. 아직이면 null. */
            Long onboardingSeconds,
            List<ScanDtos.JobResponse> jobs) {}

    public record Progress(int posts, int images, int inProgress, int succeeded, int failed, int reviewed) {}
}
