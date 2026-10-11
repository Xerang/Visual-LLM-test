package com.bozor.scan;

import com.bozor.catalog.Category;
import com.bozor.catalog.CategoryService;
import com.bozor.common.AppProperties;
import com.bozor.image.ImageService;
import com.bozor.scan.vision.VisionClient;
import com.bozor.scan.vision.VisionException;
import com.bozor.scan.vision.VisionScanRequest;
import com.bozor.scan.vision.VisionScanResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스캔 Job 하나를 처리한다. 워커(ScanWorker)와 테스트가 호출한다.
 *
 * AI 호출(수 초~수십 초)은 DB 트랜잭션 밖에서 한다. 트랜잭션을 길게 잡으면 DB 연결을 오래 붙잡기 때문이다.
 * 그래서 ① 요청 준비 ② AI 호출 ③ 결과 저장 을 나눠서 처리한다.
 */
@Service
public class ScanJobProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ScanJobProcessor.class);
    private static final int MAX_CANDIDATES = 10;

    private final ScanJobQueue queue;
    private final ScanJobRepository jobs;
    private final CategoryService categoryService;
    private final ImageService imageService;
    private final VisionClient vision;
    private final TransactionTemplate tx;
    private final AppProperties.Scan settings;

    public ScanJobProcessor(ScanJobQueue queue, ScanJobRepository jobs, CategoryService categoryService,
                            ImageService imageService, VisionClient vision, TransactionTemplate tx,
                            AppProperties properties) {
        this.queue = queue;
        this.jobs = jobs;
        this.categoryService = categoryService;
        this.imageService = imageService;
        this.vision = vision;
        this.tx = tx;
        this.settings = properties.scan();
    }

    /** 처리할 Job이 있으면 하나 처리하고 true. */
    public boolean processNext() {
        Optional<Long> jobId = queue.claimNext();
        jobId.ifPresent(this::process);
        return jobId.isPresent();
    }

    void process(Long jobId) {
        VisionScanRequest request = tx.execute(status -> buildRequest(jobId));
        try {
            VisionScanResult result = vision.scan(request);
            tx.executeWithoutResult(status -> saveSuccess(jobId, result));
            LOG.info("Scan job {} succeeded: {} candidates", jobId, result.candidates().size());
        } catch (VisionException e) {
            tx.executeWithoutResult(status -> saveFailure(jobId, e));
        } catch (RuntimeException e) {
            LOG.error("Scan job {} failed unexpectedly", jobId, e);
            tx.executeWithoutResult(status -> saveFailure(jobId,
                    new VisionException(VisionException.UNAVAILABLE, true, "AI 분석 중 오류가 발생했습니다.")));
        }
    }

    private VisionScanRequest buildRequest(Long jobId) {
        ScanJob job = jobs.findById(jobId).orElseThrow();
        List<byte[]> images = job.getImages().stream()
                .map(ref -> imageService.file(ref.getImageId(), false).bytes())
                .toList();
        List<VisionScanRequest.CategoryOption> categories = categoryService.activeLeaves().stream()
                .sorted(Comparator.comparing(Category::getCode))
                .map(c -> new VisionScanRequest.CategoryOption(c.getCode(), categoryService.nameOf(c, job.getOutputLocale())))
                .toList();
        return new VisionScanRequest(images, job.getOutputLocale(), job.getCaptionText(), categories, MAX_CANDIDATES);
    }

    private void saveSuccess(Long jobId, VisionScanResult result) {
        ScanJob job = jobs.findById(jobId).orElseThrow();
        Map<String, Category> leaves = categoryService.activeLeaves().stream()
                .collect(Collectors.toMap(Category::getCode, Function.identity()));
        Category other = leaves.get("other");
        boolean hasCaption = job.getCaptionText() != null && !job.getCaptionText().isBlank();
        int index = 0;
        for (VisionScanResult.Candidate c : result.candidates()) {
            if (c.name() == null || c.name().isBlank() || index >= MAX_CANDIDATES) continue;
            List<String> notes = new ArrayList<>(c.reviewNotes());
            Category category = leaves.get(c.categoryCode());
            if (category == null) {
                // FastAPI가 목록 밖 코드를 걸러야 하지만, 혹시 들어오면 '기타'로 두고 판매자에게 알린다.
                category = other != null ? other : leaves.values().iterator().next();
                notes.add("AI가 목록에 없는 카테고리를 제안해 '기타'로 바꿨습니다.");
            }
            job.addCandidate(ScanCandidate.fromAi(job, index++, c.name().strip(), c.description(), category.getId(),
                    c.visibleText(), notes, hasCaption ? c.suggestedPrice() : null));
        }
        job.succeed(result.model(), result.promptVersion(), result.raw(), result.elapsedMs());
    }

    /** 재시도 가능한 오류는 30s → 60s → 120s 뒤로 미루고, 횟수를 다 쓰면 실패로 끝낸다. */
    private void saveFailure(Long jobId, VisionException e) {
        ScanJob job = jobs.findById(jobId).orElseThrow();
        if (e.retryable() && job.getAttemptCount() < settings.maxAttempts()) {
            long factor = 1L << Math.max(0, job.getAttemptCount() - 1);
            Instant next = Instant.now().plus(settings.baseBackoff().multipliedBy(factor));
            job.requeue(e.code(), e.getMessage(), next);
            LOG.warn("Scan job {} will retry at {} ({})", jobId, next, e.code());
        } else {
            job.fail(e.code(), e.getMessage());
            LOG.warn("Scan job {} failed: {} {}", jobId, e.code(), e.getMessage());
        }
    }
}
