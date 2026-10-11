package com.bozor.scan;

import com.bozor.catalog.CategoryService;
import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import com.bozor.common.i18n.Locales;
import com.bozor.common.web.PageResponse;
import com.bozor.common.web.Pages;
import com.bozor.image.Image;
import com.bozor.image.ImageService;
import com.bozor.scan.ScanEnums.JobStatus;
import com.bozor.scan.dto.ScanDtos;
import com.bozor.store.Store;
import com.bozor.store.StoreService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자의 스캔 요청·조회·재시도. 실제 AI 처리는 ScanJobProcessor(워커)가 한다. */
@Service
@Transactional
public class ScanJobService {
    private final ScanJobRepository jobs;
    private final ScanCandidateRepository candidates;
    private final StoreService storeService;
    private final ImageService imageService;
    private final CategoryService categoryService;
    private final Locales locales;

    public ScanJobService(ScanJobRepository jobs, ScanCandidateRepository candidates, StoreService storeService,
                          ImageService imageService, CategoryService categoryService, Locales locales) {
        this.jobs = jobs;
        this.candidates = candidates;
        this.storeService = storeService;
        this.imageService = imageService;
        this.categoryService = categoryService;
        this.locales = locales;
    }

    /** 바로 QUEUED 상태로 저장하고 돌려준다. 프런트는 GET 으로 상태를 확인한다(폴링). */
    public ScanDtos.JobResponse create(Long storeId, Long userId, ScanDtos.CreateRequest request) {
        Store store = storeService.getOwned(storeId, userId);
        imageService.requireOwned(request.imageIds(), store.getOwnerId(), "image_ids");
        ScanJob job = jobs.save(new ScanJob(store.getId(), userId, request.sourceType(),
                normalizeCaption(request.captionText()), locales.resolve(request.outputLocale()), request.imageIds()));
        return toResponse(job, true);
    }

    /** 빈 캡션은 null 로 저장한다(AI 프롬프트에 빈 캡션 블록이 들어가지 않게). */
    static String normalizeCaption(String caption) {
        return caption == null || caption.isBlank() ? null : caption.strip();
    }

    @Transactional(readOnly = true)
    public PageResponse<ScanDtos.JobResponse> list(Long storeId, Long userId, JobStatus status, Boolean reviewed,
                                                   Integer page, Integer size) {
        storeService.getOwned(storeId, userId);
        return PageResponse.from(jobs.search(storeId, status, reviewed, Pages.of(page, size)),
                job -> toResponse(job, false));
    }

    @Transactional(readOnly = true)
    public ScanDtos.JobResponse get(Long jobId, Long userId) {
        return toResponse(getOwned(jobId, userId), true);
    }

    public ScanDtos.JobResponse retry(Long jobId, Long userId) {
        ScanJob job = getOwned(jobId, userId);
        if (job.getStatus() != JobStatus.FAILED) {
            throw new ApiException(ErrorCode.SCAN_JOB_NOT_RETRYABLE, "실패한 스캔만 다시 시도할 수 있습니다.");
        }
        job.retryByUser();
        return toResponse(job, true);
    }

    @Transactional(readOnly = true)
    public ScanJob getOwned(Long jobId, Long userId) {
        ScanJob job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("스캔"));
        storeService.getOwned(job.getStoreId(), userId);
        return job;
    }

    @Transactional(readOnly = true)
    public ScanDtos.JobResponse toResponse(ScanJob job, boolean withCandidates) {
        List<Long> imageIds = job.getImages().stream().map(ScanJobImageRef::getImageId).toList();
        Map<Long, Image> images = imageService.findAll(imageIds);
        List<ScanDtos.CandidateResponse> candidateResponses = null;
        if (withCandidates) {
            Map<Long, Long> productByCandidate = productLinks(job.getCandidates());
            candidateResponses = job.getCandidates().stream()
                    .map(c -> new ScanDtos.CandidateResponse(c.getId(), c.getCandidateIndex(), c.getOrigin().name(),
                            c.getName(), c.getDescription(),
                            categoryService.ref(categoryService.get(c.getCategoryId()), job.getOutputLocale()),
                            c.getVisibleText(), c.getReviewNotes(), c.getSuggestedPrice(), c.getDecision().name(),
                            productByCandidate.get(c.getId())))
                    .toList();
        }
        return new ScanDtos.JobResponse(job.getId(), job.getStoreId(), job.getImportBatchId(), job.getStatus().name(),
                job.getSourceType().name(), job.getCaptionText(), job.getOutputLocale(),
                imageIds.stream().map(images::get).filter(i -> i != null).map(imageService::signedResponse).toList(),
                job.getAttemptCount(), job.getAvailableAt(), job.getModel(), job.getPromptVersion(),
                job.getElapsedMs(),
                job.getErrorCode() == null ? null : new ScanDtos.ErrorInfo(job.getErrorCode(), job.getErrorMessage()),
                imageNotes(job.getRawResult()), job.getCandidates().size(), candidateResponses,
                job.getCreatedAt(), job.getFinishedAt(), job.getReviewedAt());
    }

    private Map<Long, Long> productLinks(List<ScanCandidate> list) {
        Map<Long, Long> result = new HashMap<>();
        if (list.isEmpty()) return result;
        for (Object[] row : candidates.findProductLinks(list.stream().map(ScanCandidate::getId).toList())) {
            result.put(((Number) row[1]).longValue(), ((Number) row[0]).longValue());
        }
        return result;
    }

    /** AI 원본 응답(raw_result)은 내보내지 않고, 판매자에게 필요한 image_notes 만 꺼낸다. */
    private static List<String> imageNotes(JsonNode raw) {
        List<String> notes = new ArrayList<>();
        if (raw != null && raw.path("image_notes").isArray()) {
            raw.path("image_notes").forEach(n -> notes.add(n.asText()));
        }
        return notes;
    }
}
