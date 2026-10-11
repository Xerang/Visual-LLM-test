package com.bozor.scan;

import com.bozor.common.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spring 안에서 도는 스캔 워커(별도 서버 없음). app.scan.worker-enabled=false 면 꺼진다(테스트).
 * 한 번에 하나씩 처리한다. 시연 규모에서는 충분하고, 늘려야 하면 워커 인스턴스를 더 띄워도
 * SKIP LOCKED 덕분에 같은 Job을 두 번 처리하지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "app.scan", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class ScanWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ScanWorker.class);
    private final ScanJobProcessor processor;
    private final ScanJobQueue queue;
    private final AppProperties.Scan settings;

    public ScanWorker(ScanJobProcessor processor, ScanJobQueue queue, AppProperties properties) {
        this.processor = processor;
        this.queue = queue;
        this.settings = properties.scan();
    }

    @Scheduled(fixedDelayString = "${app.scan.poll-interval-ms}")
    public void poll() {
        processor.processNext();
    }

    @Scheduled(fixedDelay = 60_000)
    public void recoverStuck() {
        int count = queue.recoverStuck(settings.stuckAfter(), settings.maxAttempts());
        if (count > 0) {
            LOG.warn("Recovered {} stuck scan jobs", count);
        }
    }
}
