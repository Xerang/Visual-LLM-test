package com.bozor.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * SNS 사진 일괄 등록 묶음(2차 회의 추가 기능). 게시물마다 스캔 작업 하나를 만든다.
 * created_at 은 판매자가 사진을 고르자마자(업로드 전) 찍힌다 → "상점 온보딩 소요시간"의 시작점.
 */
@Entity
@Table(name = "import_batches")
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long requestedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** 업로드·캡션 입력을 마치고 AI 분석을 요청한 시각. 한 번만 요청할 수 있다. */
    private Instant submittedAt;

    protected ImportBatch() {}

    public ImportBatch(Long storeId, Long requestedBy) {
        this.storeId = storeId;
        this.requestedBy = requestedBy;
    }

    public void markSubmitted() {
        this.submittedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Long getRequestedBy() { return requestedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSubmittedAt() { return submittedAt; }
}
