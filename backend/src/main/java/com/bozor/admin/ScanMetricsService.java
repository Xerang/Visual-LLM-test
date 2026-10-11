package com.bozor.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2차 회의 검증 지표(docs/02-erd.md 지표 쿼리). 주간 보고서에 그대로 옮겨 쓸 수 있게 계산한다.
 * - 카테고리 정확도(소분류·대분류): AI 제안 = 판매자 확정
 * - 정밀도: 수락된 AI 후보 / 결정된 AI 후보
 * - 재현율: 수락된 AI 후보 / 수락된 전체(AI + 판매자 추가)
 * - 등록 소요시간: 스캔 요청 → 상품 발행, 중앙값. 단건 스캔만 센다(일괄 등록 작업은 다른 게시물 뒤에 줄 서므로 제외).
 * - 상점 온보딩 소요시간: 일괄 등록 묶음 시작(사진 고른 직후) → 모든 게시물 검수 완료, 중앙값
 */
@Service
@Transactional(readOnly = true)
public class ScanMetricsService {
    private static final ZoneId DUSHANBE = ZoneId.of("Asia/Dushanbe");
    /** 2차 회의에서 정한 목표 */
    private static final Map<String, Number> TARGETS = Map.of("category_accuracy_leaf", 0.8, "seconds_to_publish", 30,
            "onboarding_seconds_for_20_images", 300);
    private static final int RECENT_BATCHES = 20;

    private final NamedParameterJdbcTemplate jdbc;

    public ScanMetricsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ScanMetrics compute(LocalDate from, LocalDate to, String promptVersion) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", java.sql.Timestamp.from(from.atStartOfDay(DUSHANBE).toInstant()))
                .addValue("to", java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(DUSHANBE).toInstant()))
                .addValue("pv", promptVersion == null ? "" : promptVersion);
        String jobs = "j.created_at >= :from AND j.created_at < :to AND (:pv = '' OR j.prompt_version = :pv)";

        Map<String, Object> job = jdbc.queryForMap("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE j.status = 'SUCCEEDED') AS succeeded,
                       count(*) FILTER (WHERE j.status = 'FAILED') AS failed,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY j.elapsed_ms)
                           FILTER (WHERE j.status = 'SUCCEEDED') AS median_elapsed_ms
                FROM scan_jobs j WHERE\s""" + jobs, p);

        Map<String, Object> cand = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE c.origin = 'AI') AS ai_total,
                       count(*) FILTER (WHERE c.origin = 'AI' AND c.decision = 'ACCEPTED') AS accepted,
                       count(*) FILTER (WHERE c.origin = 'AI' AND c.decision = 'REJECTED') AS rejected,
                       count(*) FILTER (WHERE c.origin = 'SELLER') AS seller_added
                FROM scan_candidates c JOIN scan_jobs j ON j.id = c.scan_job_id WHERE\s""" + jobs, p);

        Map<String, Object> acc = jdbc.queryForMap("""
                SELECT avg((c.category_id = pr.category_id)::int) AS leaf,
                       avg((coalesce(cc.parent_id, cc.id) = coalesce(pc.parent_id, pc.id))::int) AS top
                FROM scan_candidates c
                JOIN scan_jobs j ON j.id = c.scan_job_id
                JOIN products pr ON pr.source_candidate_id = c.id
                JOIN categories cc ON cc.id = c.category_id
                JOIN categories pc ON pc.id = pr.category_id
                WHERE c.origin = 'AI' AND\s""" + jobs, p);

        Double medianPublish = jdbc.queryForObject("""
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM pr.published_at - j.created_at))
                FROM products pr
                JOIN scan_candidates c ON c.id = pr.source_candidate_id
                JOIN scan_jobs j ON j.id = c.scan_job_id
                WHERE pr.published_at IS NOT NULL AND j.import_batch_id IS NULL AND\s""" + jobs, p, Double.class);

        long accepted = num(cand.get("accepted"));
        long decided = accepted + num(cand.get("rejected"));
        long allAccepted = accepted + num(cand.get("seller_added"));
        return new ScanMetrics(from, to, promptVersion,
                new ScanMetrics.Jobs(num(job.get("total")), num(job.get("succeeded")), num(job.get("failed")),
                        round(job.get("median_elapsed_ms"), 0)),
                new ScanMetrics.Candidates(num(cand.get("ai_total")), accepted, num(cand.get("rejected")),
                        num(cand.get("seller_added"))),
                new ScanMetrics.Accuracy(round(acc.get("leaf"), 2), round(acc.get("top"), 2)),
                ratio(accepted, decided), ratio(accepted, allAccepted), round(medianPublish, 1),
                onboarding(p), TARGETS);
    }

    /**
     * 상점 온보딩(SNS 일괄 등록). 기간은 묶음 시작 시각 기준. 프롬프트 버전 필터는 적용하지 않는다(묶음 단위 지표).
     * "이미지 20장 기준"이므로 묶음별 사진 수를 함께 내보낸다. 사진 수가 다른 묶음을 20장으로 환산하지는 않는다.
     */
    private ScanMetrics.Onboarding onboarding(MapSqlParameterSource p) {
        String batches = """
                WITH b AS (
                    SELECT ib.id, ib.store_id, ib.created_at, ib.submitted_at,
                           count(j.id) AS posts,
                           count(j.id) FILTER (WHERE j.reviewed_at IS NOT NULL) AS reviewed,
                           max(j.reviewed_at) AS last_reviewed_at,
                           (SELECT count(*) FROM scan_job_images sji JOIN scan_jobs j2 ON j2.id = sji.scan_job_id
                            WHERE j2.import_batch_id = ib.id) AS images,
                           (SELECT count(*) FROM products pr
                            JOIN scan_candidates c ON c.id = pr.source_candidate_id
                            JOIN scan_jobs j3 ON j3.id = c.scan_job_id
                            WHERE j3.import_batch_id = ib.id AND pr.published_at IS NOT NULL) AS published
                    FROM import_batches ib
                    LEFT JOIN scan_jobs j ON j.import_batch_id = ib.id
                    WHERE ib.created_at >= :from AND ib.created_at < :to
                    GROUP BY ib.id),
                done AS (
                    SELECT *, extract(epoch FROM last_reviewed_at - created_at) AS seconds,
                           extract(epoch FROM submitted_at - created_at) AS prepare_seconds
                    FROM b WHERE posts > 0 AND reviewed = posts)
                """;
        Map<String, Object> summary = jdbc.queryForMap(batches + """
                SELECT (SELECT count(*) FROM b) AS started,
                       (SELECT count(*) FROM b WHERE submitted_at IS NOT NULL) AS submitted,
                       count(*) AS completed,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY seconds) AS median_seconds,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY prepare_seconds) AS median_prepare_seconds
                FROM done
                """, p);
        List<ScanMetrics.Batch> recent = jdbc.query(batches + """
                SELECT id, store_id, images, posts, published, seconds, prepare_seconds FROM done
                ORDER BY created_at DESC LIMIT :limit
                """, new MapSqlParameterSource(p.getValues()).addValue("limit", RECENT_BATCHES),
                (rs, i) -> new ScanMetrics.Batch(rs.getLong("id"), rs.getLong("store_id"), rs.getInt("images"),
                        rs.getInt("posts"), rs.getInt("published"), round(rs.getObject("seconds"), 0),
                        round(rs.getObject("prepare_seconds"), 0)));
        return new ScanMetrics.Onboarding(num(summary.get("started")), num(summary.get("submitted")),
                num(summary.get("completed")), round(summary.get("median_seconds"), 0),
                round(summary.get("median_prepare_seconds"), 0), recent);
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static BigDecimal round(Object value, int scale) {
        if (value == null) return null;
        return new BigDecimal(value.toString()).setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        return denominator == 0 ? null
                : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    public record ScanMetrics(LocalDate from, LocalDate to, String promptVersion, Jobs scanJobs,
                              Candidates candidates, Accuracy categoryAccuracy, BigDecimal precision,
                              BigDecimal recall, BigDecimal medianSecondsToPublish, Onboarding onboarding,
                              Map<String, Number> targets) {
        public record Jobs(long total, long succeeded, long failed, BigDecimal medianElapsedMs) {}

        public record Candidates(long aiTotal, long accepted, long rejected, long sellerAdded) {}

        public record Accuracy(BigDecimal leaf, BigDecimal top) {}

        /** started: 묶음 시작, submitted: AI 분석 요청, completed: 모든 게시물 검수 완료 */
        public record Onboarding(long started, long submitted, long completed, BigDecimal medianSeconds,
                                 BigDecimal medianPrepareSeconds, List<Batch> recent) {}

        /** 완료된 묶음 하나. prepare_seconds = 시작 → 분석 요청(사진 업로드·캡션 입력 시간) */
        public record Batch(long id, long storeId, int images, int posts, int published, BigDecimal seconds,
                            BigDecimal prepareSeconds) {}
    }
}
