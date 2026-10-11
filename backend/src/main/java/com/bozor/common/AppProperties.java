package com.bozor.common;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.* 설정. 임시 결정(docs/00-temporary-decisions.md)은 대부분 여기서 바꾼다.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String defaultLocale,
        Auth auth,
        Jwt jwt,
        Storage storage,
        Vision vision,
        Scan scan) {

    public record Auth(String mode) {
        public boolean isMock() {
            return "mock".equals(mode);
        }
    }

    public record Jwt(String secret, Duration ttl) {}

    public record Storage(String localDir) {}

    public record Vision(String mode, String url, Duration readTimeout) {
        public boolean isFake() {
            return "fake".equals(mode);
        }
    }

    public record Scan(boolean workerEnabled, long pollIntervalMs, int maxAttempts,
                       Duration baseBackoff, Duration stuckAfter) {}
}
