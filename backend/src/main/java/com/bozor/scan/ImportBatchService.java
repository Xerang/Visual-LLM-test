package com.bozor.scan;

import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import com.bozor.common.i18n.Locales;
import com.bozor.image.ImageService;
import com.bozor.scan.ScanEnums.JobStatus;
import com.bozor.scan.ScanEnums.SourceType;
import com.bozor.scan.dto.ImportDtos;
import com.bozor.store.Store;
import com.bozor.store.StoreService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SNS 사진 일괄 등록(2차 회의 추가 기능 "기존 SNS 이미지 일괄 등록").
 * 1) 사진을 고르자마자 묶음을 만든다(온보딩 시작 시각) → 2) 사진 업로드 → 3) 게시물별 스캔 작업을 한 번에 요청.
 * AI 처리·검수는 단건 스캔과 같은 워커·검수 API를 그대로 쓴다.
 */
@Service
@Transactional
public class ImportBatchService {
    private final ImportBatchRepository batches;
    private final ScanJobRepository jobs;
    private final ScanJobService scanJobService;
    private final StoreService storeService;
    private final ImageService imageService;
    private final Locales locales;

    public ImportBatchService(ImportBatchRepository batches, ScanJobRepository jobs, ScanJobService scanJobService,
                              StoreService storeService, ImageService imageService, Locales locales) {
        this.batches = batches;
        this.jobs = jobs;
        this.scanJobService = scanJobService;
        this.storeService = storeService;
        this.imageService = imageService;
        this.locales = locales;
    }

    public ImportDtos.BatchResponse start(Long storeId, Long userId) {
        Store store = storeService.getOwned(storeId, userId);
        return toResponse(batches.save(new ImportBatch(store.getId(), userId)), List.of());
    }

    /** 게시물마다 SNS_IMPORT 스캔 작업을 만든다. 한 묶음은 한 번만 요청할 수 있다. */
    public ImportDtos.BatchResponse submit(Long batchId, Long userId, ImportDtos.SubmitRequest request) {
        ImportBatch batch = getOwned(batchId, userId);
        if (batch.getSubmittedAt() != null) {
            throw new ApiException(ErrorCode.IMPORT_BATCH_ALREADY_SUBMITTED, "이미 분석을 요청한 묶음입니다.");
        }
        Store store = storeService.getOwned(batch.getStoreId(), userId);
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.items().size(); i++) {
            String field = "items[" + i + "].image_ids";
            List<Long> ids = request.items().get(i).imageIds();
            imageService.requireOwned(ids, store.getOwnerId(), field);
            for (Long id : ids) {
                if (!seen.add(id)) {
                    // 같은 사진이 두 게시물에 들어가면 같은 상품이 두 번 만들어진다.
                    throw ApiException.invalid(field, "다른 게시물에 이미 들어 있는 사진입니다: " + id);
                }
            }
        }
        String locale = locales.resolve(request.outputLocale());
        for (ImportDtos.Item item : request.items()) {
            ScanJob job = new ScanJob(store.getId(), userId, SourceType.SNS_IMPORT,
                    ScanJobService.normalizeCaption(item.captionText()), locale, item.imageIds());
            job.assignToBatch(batch.getId());
            jobs.save(job);
        }
        batch.markSubmitted();
        return get(batchId, userId);
    }

    @Transactional(readOnly = true)
    public ImportDtos.BatchResponse get(Long batchId, Long userId) {
        ImportBatch batch = getOwned(batchId, userId);
        return toResponse(batch, jobs.findByImportBatchIdOrderByIdAsc(batch.getId()));
    }

    private ImportBatch getOwned(Long batchId, Long userId) {
        ImportBatch batch = batches.findById(batchId).orElseThrow(() -> ApiException.notFound("일괄 등록"));
        storeService.getOwned(batch.getStoreId(), userId);
        return batch;
    }

    private ImportDtos.BatchResponse toResponse(ImportBatch batch, List<ScanJob> list) {
        int images = 0, inProgress = 0, succeeded = 0, failed = 0, reviewed = 0;
        Instant lastReview = null;
        for (ScanJob job : list) {
            images += job.getImages().size();
            if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RUNNING) inProgress++;
            if (job.getStatus() == JobStatus.SUCCEEDED) succeeded++;
            if (job.getStatus() == JobStatus.FAILED) failed++;
            if (job.getReviewedAt() != null) {
                reviewed++;
                if (lastReview == null || job.getReviewedAt().isAfter(lastReview)) lastReview = job.getReviewedAt();
            }
        }
        // 모든 게시물을 검수해야 끝난 것이다. 실패한 게시물이 남아 있으면 다시 시도해서 검수해야 끝난다.
        Instant completedAt = !list.isEmpty() && reviewed == list.size() ? lastReview : null;
        Long seconds = completedAt == null ? null : Duration.between(batch.getCreatedAt(), completedAt).toSeconds();
        return new ImportDtos.BatchResponse(batch.getId(), batch.getStoreId(), batch.getCreatedAt(),
                batch.getSubmittedAt(),
                new ImportDtos.Progress(list.size(), images, inProgress, succeeded, failed, reviewed),
                completedAt, seconds,
                list.stream().map(job -> scanJobService.toResponse(job, false)).toList());
    }
}
