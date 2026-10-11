package com.bozor.scan;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * scan_jobs 테이블을 작업 큐로 쓴다(별도 메시지 큐 없이 PostgreSQL만으로).
 * FOR UPDATE SKIP LOCKED: 워커가 여러 개여도 같은 Job을 두 번 가져가지 않는다.
 */
@Component
public class ScanJobQueue {
    private final NamedParameterJdbcTemplate jdbc;

    public ScanJobQueue(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 실행할 수 있는(QUEUED, available_at 지남) Job 하나를 RUNNING 으로 바꾸고 id를 돌려준다.
     * now() 는 트랜잭션 시작 시각이라, 같은 트랜잭션에서 방금 만든 Job을 못 볼 수 있다.
     * 그래서 실제 현재 시각인 clock_timestamp() 와 비교한다.
     */
    public Optional<Long> claimNext() {
        List<Long> ids = jdbc.queryForList("""
                UPDATE scan_jobs SET status = 'RUNNING', started_at = clock_timestamp(),
                       attempt_count = attempt_count + 1
                WHERE id = (SELECT id FROM scan_jobs
                            WHERE status = 'QUEUED' AND available_at <= clock_timestamp()
                            ORDER BY available_at, id
                            FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING id
                """, new MapSqlParameterSource(), Long.class);
        return ids.stream().findFirst();
    }

    /**
     * 너무 오래 RUNNING 인 Job(서버가 처리 중에 꺼진 경우)을 되돌린다.
     * 재시도 횟수를 다 쓴 Job은 실패로 끝낸다.
     */
    public int recoverStuck(Duration stuckAfter, int maxAttempts) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("seconds", stuckAfter.toSeconds())
                .addValue("maxAttempts", maxAttempts);
        int failed = jdbc.update("""
                UPDATE scan_jobs SET status = 'FAILED', error_code = 'AI_TIMEOUT',
                       error_message = '분석이 너무 오래 걸려 중단했습니다. 다시 시도해 주세요.', finished_at = now()
                WHERE status = 'RUNNING' AND started_at < now() - make_interval(secs => :seconds)
                  AND attempt_count >= :maxAttempts
                """, params);
        int requeued = jdbc.update("""
                UPDATE scan_jobs SET status = 'QUEUED', available_at = now()
                WHERE status = 'RUNNING' AND started_at < now() - make_interval(secs => :seconds)
                """, params);
        return failed + requeued;
    }
}
