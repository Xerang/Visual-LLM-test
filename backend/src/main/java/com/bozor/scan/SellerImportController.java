package com.bozor.scan;

import com.bozor.auth.LoginUser;
import com.bozor.scan.dto.ImportDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 판매자: SNS 사진 일괄 등록 (docs/03-api.md 5.5) */
@RestController
@RequestMapping("/api/v1/seller")
public class SellerImportController {
    private final ImportBatchService importBatchService;

    public SellerImportController(ImportBatchService importBatchService) {
        this.importBatchService = importBatchService;
    }

    /** 사진을 고르자마자(업로드 전) 부른다. 이 시각이 온보딩 소요시간의 시작이다. */
    @PostMapping("/stores/{storeId}/import-batches")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportDtos.BatchResponse start(@LoginUser Long userId, @PathVariable Long storeId) {
        return importBatchService.start(storeId, userId);
    }

    /** 202: 게시물마다 스캔 작업을 만들었다. GET 으로 진행 상황을 폴링한다. */
    @PostMapping("/import-batches/{batchId}/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportDtos.BatchResponse submit(@LoginUser Long userId, @PathVariable Long batchId,
                                           @Valid @RequestBody ImportDtos.SubmitRequest request) {
        return importBatchService.submit(batchId, userId, request);
    }

    /** 분석 중인 게시물이 있으면 Retry-After: 2 */
    @GetMapping("/import-batches/{batchId}")
    public ResponseEntity<ImportDtos.BatchResponse> get(@LoginUser Long userId, @PathVariable Long batchId) {
        ImportDtos.BatchResponse batch = importBatchService.get(batchId, userId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (batch.progress().inProgress() > 0) response.header("Retry-After", "2");
        return response.body(batch);
    }
}
