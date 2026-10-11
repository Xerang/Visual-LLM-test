package com.bozor.admin;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자: 검증 지표 (docs/03-api.md 5.9). 권한은 SecurityConfig 에서 ROLE_ADMIN 으로 막는다. */
@RestController
public class AdminMetricsController {
    private final ScanMetricsService metrics;

    public AdminMetricsController(ScanMetricsService metrics) {
        this.metrics = metrics;
    }

    /** 기간을 비우면 최근 30일. */
    @GetMapping("/api/v1/admin/metrics/scan")
    public ScanMetricsService.ScanMetrics scan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "prompt_version", required = false) String promptVersion) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneId.of("Asia/Dushanbe"));
        LocalDate start = from != null ? from : end.minusDays(29);
        return metrics.compute(start, end, promptVersion);
    }
}
