package com.bozor.scan;

import com.bozor.scan.ScanEnums.Decision;
import com.bozor.scan.ScanEnums.Origin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI가 제안한 상품 후보(origin=AI)와, AI가 놓쳐 판매자가 추가한 상품(origin=SELLER).
 * 둘을 같은 테이블에 둬서 카테고리 정확도·정밀도·재현율을 SQL로 바로 계산한다(ERD 결정 4).
 * AI 후보의 값은 AI가 낸 그대로 두고, 판매자가 고친 값은 products 에 저장한다.
 */
@Entity
@Table(name = "scan_candidates")
public class ScanCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_job_id")
    private ScanJob scanJob;

    @Column(nullable = false)
    private short candidateIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Origin origin;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Long categoryId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> visibleText = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> reviewNotes = new ArrayList<>();

    /** SNS 캡션에 적힌 가격만. 사진만 보고 추측한 가격은 넣지 않는다. */
    @Column(precision = 12, scale = 2)
    private BigDecimal suggestedPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Decision decision;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ScanCandidate() {}

    private ScanCandidate(ScanJob scanJob, int candidateIndex, Origin origin, String name, String description,
                          Long categoryId, List<String> visibleText, List<String> reviewNotes,
                          BigDecimal suggestedPrice, Decision decision) {
        this.scanJob = scanJob;
        this.candidateIndex = (short) candidateIndex;
        this.origin = origin;
        this.name = name;
        this.description = description;
        this.categoryId = categoryId;
        this.visibleText = new ArrayList<>(visibleText);
        this.reviewNotes = new ArrayList<>(reviewNotes);
        this.suggestedPrice = suggestedPrice;
        this.decision = decision;
    }

    public static ScanCandidate fromAi(ScanJob job, int index, String name, String description, Long categoryId,
                                       List<String> visibleText, List<String> reviewNotes, BigDecimal suggestedPrice) {
        return new ScanCandidate(job, index, Origin.AI, truncate(name), description, categoryId,
                visibleText, reviewNotes, suggestedPrice, Decision.PENDING);
    }

    /** AI가 놓친 상품. 판매자가 직접 추가한 것이므로 항상 ACCEPTED 다(DB CHECK 제약과 같음). */
    public static ScanCandidate addedBySeller(ScanJob job, int index, String name, String description, Long categoryId) {
        return new ScanCandidate(job, index, Origin.SELLER, truncate(name), description, categoryId,
                List.of(), List.of(), null, Decision.ACCEPTED);
    }

    public void decide(Decision decision) {
        this.decision = decision;
    }

    private static String truncate(String s) {
        return s.length() <= 120 ? s : s.substring(0, 120);
    }

    public Long getId() { return id; }
    public ScanJob getScanJob() { return scanJob; }
    public short getCandidateIndex() { return candidateIndex; }
    public Origin getOrigin() { return origin; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getCategoryId() { return categoryId; }
    public List<String> getVisibleText() { return visibleText; }
    public List<String> getReviewNotes() { return reviewNotes; }
    public BigDecimal getSuggestedPrice() { return suggestedPrice; }
    public Decision getDecision() { return decision; }
}
