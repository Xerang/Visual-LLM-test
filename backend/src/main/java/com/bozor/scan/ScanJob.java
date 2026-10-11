package com.bozor.scan;

import com.bozor.scan.ScanEnums.JobStatus;
import com.bozor.scan.ScanEnums.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI 스캔 작업(비동기 Job). 2차 회의 결정: AI 응답(5~30초+)을 요청 하나로 기다리지 않는다.
 * raw_result(AI 원본)는 저장 후 절대 고치지 않는다. 판매자 확정본은 products 에 따로 있다.
 */
@Entity
@Table(name = "scan_jobs")
public class ScanJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SourceType sourceType;

    /** SNS 게시물 캡션. AI는 여기에 적힌 가격만 suggested_price 로 뽑는다. */
    @Column(columnDefinition = "text")
    private String captionText;

    @Column(nullable = false, length = 2)
    private String outputLocale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobStatus status;

    @Column(nullable = false)
    private short attemptCount;

    /** 이 시각 이후에 워커가 가져간다. 재시도할 때 뒤로 미룬다. */
    @Column(nullable = false)
    private Instant availableAt;

    @Column(length = 100)
    private String model;

    @Column(length = 40)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode rawResult;

    @Column(length = 40)
    private String errorCode;

    @Column(length = 500)
    private String errorMessage;

    private Integer elapsedMs;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;
    private Instant finishedAt;
    private Instant reviewedAt;

    /** SNS 일괄 등록 묶음(import_batches). 단건 스캔이면 null. */
    private Long importBatchId;

    @ElementCollection
    @CollectionTable(name = "scan_job_images", joinColumns = @JoinColumn(name = "scan_job_id"))
    @OrderBy("sortOrder")
    private List<ScanJobImageRef> images = new ArrayList<>();

    @OneToMany(mappedBy = "scanJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("candidateIndex")
    private List<ScanCandidate> candidates = new ArrayList<>();

    protected ScanJob() {}

    public ScanJob(Long storeId, Long requestedBy, SourceType sourceType, String captionText, String outputLocale,
                   List<Long> imageIds) {
        this.storeId = storeId;
        this.requestedBy = requestedBy;
        this.sourceType = sourceType;
        this.captionText = captionText;
        this.outputLocale = outputLocale;
        this.status = JobStatus.QUEUED;
        this.availableAt = Instant.now();
        for (int i = 0; i < imageIds.size(); i++) {
            this.images.add(new ScanJobImageRef(imageIds.get(i), i));
        }
    }

    public void succeed(String model, String promptVersion, JsonNode rawResult, int elapsedMs) {
        this.status = JobStatus.SUCCEEDED;
        this.model = model;
        this.promptVersion = promptVersion;
        this.rawResult = rawResult;
        this.elapsedMs = elapsedMs;
        this.errorCode = null;
        this.errorMessage = null;
        this.finishedAt = Instant.now();
    }

    /** 재시도 가능한 실패: 다시 줄을 세우되 availableAt 까지 기다리게 한다. */
    public void requeue(String errorCode, String errorMessage, Instant availableAt) {
        this.status = JobStatus.QUEUED;
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage);
        this.availableAt = availableAt;
    }

    public void fail(String errorCode, String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage);
        this.finishedAt = Instant.now();
    }

    /** 판매자가 실패한 스캔을 다시 시도한다. 재시도 횟수도 새로 센다. */
    public void retryByUser() {
        this.status = JobStatus.QUEUED;
        this.attemptCount = 0;
        this.availableAt = Instant.now();
        this.finishedAt = null;
    }

    public void assignToBatch(Long importBatchId) {
        this.importBatchId = importBatchId;
    }

    public void markReviewed() {
        this.reviewedAt = Instant.now();
    }

    public void addCandidate(ScanCandidate candidate) {
        candidates.add(candidate);
    }

    public int nextCandidateIndex() {
        return candidates.stream().mapToInt(ScanCandidate::getCandidateIndex).max().orElse(-1) + 1;
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Long getRequestedBy() { return requestedBy; }
    public SourceType getSourceType() { return sourceType; }
    public String getCaptionText() { return captionText; }
    public String getOutputLocale() { return outputLocale; }
    public JobStatus getStatus() { return status; }
    public short getAttemptCount() { return attemptCount; }
    public Instant getAvailableAt() { return availableAt; }
    public String getModel() { return model; }
    public String getPromptVersion() { return promptVersion; }
    public JsonNode getRawResult() { return rawResult; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getElapsedMs() { return elapsedMs; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Long getImportBatchId() { return importBatchId; }
    public List<ScanJobImageRef> getImages() { return images; }
    public List<ScanCandidate> getCandidates() { return candidates; }
}
