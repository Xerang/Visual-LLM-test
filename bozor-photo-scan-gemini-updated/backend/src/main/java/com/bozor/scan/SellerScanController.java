package com.bozor.scan;

import com.bozor.auth.LoginUser;
import com.bozor.common.web.PageResponse;
import com.bozor.scan.ScanEnums.JobStatus;
import com.bozor.scan.dto.ScanDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 판매자: AI 스캔과 검수 (docs/03-api.md 5.5) */
@RestController
@RequestMapping("/api/v1/seller")
public class SellerScanController {
    private final ScanJobService scanJobService;
    private final ScanReviewService scanReviewService;

    public SellerScanController(ScanJobService scanJobService, ScanReviewService scanReviewService) {
        this.scanJobService = scanJobService;
        this.scanReviewService = scanReviewService;
    }

    /** 202: 받았고 처리는 나중에. 응답의 id 로 GET /seller/scan-jobs/{id} 를 폴링한다. */
    @PostMapping("/stores/{storeId}/scan-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanDtos.JobResponse create(@LoginUser Long userId, @PathVariable Long storeId,
                                       @Valid @RequestBody ScanDtos.CreateRequest request) {
        return scanJobService.create(storeId, userId, request);
    }

    /** 검수 대기함: ?status=SUCCEEDED&reviewed=false */
    @GetMapping("/stores/{storeId}/scan-jobs")
    public PageResponse<ScanDtos.JobResponse> list(@LoginUser Long userId, @PathVariable Long storeId,
                                                   @RequestParam(required = false) JobStatus status,
                                                   @RequestParam(required = false) Boolean reviewed,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        return scanJobService.list(storeId, userId, status, reviewed, page, size);
    }

    /** 진행 중이면 Retry-After: 2 를 붙여 폴링 간격을 알려준다. */
    @GetMapping("/scan-jobs/{jobId}")
    public ResponseEntity<ScanDtos.JobResponse> get(@LoginUser Long userId, @PathVariable Long jobId) {
        ScanDtos.JobResponse job = scanJobService.get(jobId, userId);
        boolean inProgress = job.status().equals(JobStatus.QUEUED.name()) || job.status().equals(JobStatus.RUNNING.name());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (inProgress) response.header("Retry-After", "2");
        return response.body(job);
    }

    @PostMapping("/scan-jobs/{jobId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanDtos.JobResponse retry(@LoginUser Long userId, @PathVariable Long jobId) {
        return scanJobService.retry(jobId, userId);
    }

    @PostMapping("/scan-jobs/{jobId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ScanDtos.ReviewResponse review(@LoginUser Long userId, @PathVariable Long jobId,
                                         @Valid @RequestBody ScanDtos.ReviewRequest request) {
        return scanReviewService.review(jobId, userId, request);
    }
}
