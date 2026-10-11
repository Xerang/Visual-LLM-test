package com.bozor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bozor.scan.ScanJobProcessor;
import com.bozor.scan.ScanJobQueue;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** SNS 사진 일괄 등록과 "상점 온보딩 소요시간" 지표 (2차 회의 추가 기능·검증 지표) */
class ImportBatchTest extends ApiTestSupport {
    @Autowired ScanJobProcessor processor;
    @Autowired ScanJobQueue queue;
    @PersistenceContext EntityManager em;

    String seller;
    long storeId;

    @BeforeEach
    void setUp() throws Exception {
        seller = login(SELLER_FARHOD);
        storeId = storeId("farhod-dried-fruit");
    }

    long photo() throws Exception {
        return uploadImage(seller, new Color((int) (Math.random() * 0xFFFFFF)));
    }

    long startBatch() throws Exception {
        JsonNode batch = body(mvc.perform(auth(post("/api/v1/seller/stores/" + storeId + "/import-batches"), seller))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submitted_at").isEmpty())
                .andExpect(jsonPath("$.progress.posts").value(0))
                .andReturn());
        return batch.path("id").asLong();
    }

    JsonNode submit(long batchId, String itemsJson) throws Exception {
        return body(mvc.perform(jsonBody(post("/api/v1/seller/import-batches/" + batchId + "/jobs"), seller,
                        "{\"items\":" + itemsJson + "}"))
                .andExpect(status().isAccepted()).andReturn());
    }

    JsonNode getBatch(long batchId) throws Exception {
        return body(mvc.perform(auth(get("/api/v1/seller/import-batches/" + batchId), seller)).andReturn());
    }

    @Test
    void eachPostBecomesOneSnsScanJobAndBatchCanBeSubmittedOnce() throws Exception {
        long batchId = startBatch();
        long a = photo(), b = photo(), c = photo();
        JsonNode batch = submit(batchId, """
                [{"image_ids":[%d],"caption_text":"Курага 45 сомони/кг"},
                 {"image_ids":[%d,%d],"caption_text":"  "}]""".formatted(a, b, c));

        assertThat(batch.path("submitted_at").isNull()).isFalse();
        assertThat(batch.path("progress").path("posts").asInt()).isEqualTo(2);
        assertThat(batch.path("progress").path("images").asInt()).isEqualTo(3);
        assertThat(batch.path("progress").path("in_progress").asInt()).isEqualTo(2);
        JsonNode jobs = batch.path("jobs");
        assertThat(jobs.get(0).path("source_type").asText()).isEqualTo("SNS_IMPORT");
        assertThat(jobs.get(0).path("import_batch_id").asLong()).isEqualTo(batchId);
        assertThat(jobs.get(0).path("caption_text").asText()).isEqualTo("Курага 45 сомони/кг");
        assertThat(jobs.get(1).path("caption_text").isNull()).as("빈 캡션은 null").isTrue();
        assertThat(jobs.get(1).path("images")).hasSize(2);

        mvc.perform(auth(get("/api/v1/seller/import-batches/" + batchId), seller))
                .andExpect(status().isOk())
                .andExpect(header().string("Retry-After", "2"));

        mvc.perform(jsonBody(post("/api/v1/seller/import-batches/" + batchId + "/jobs"), seller,
                        "{\"items\":[{\"image_ids\":[" + photo() + "]}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_BATCH_ALREADY_SUBMITTED"));
    }

    @Test
    void samePhotoInTwoPostsAndTooManyPostsAreRejected() throws Exception {
        long batchId = startBatch();
        long a = photo(), b = photo();
        mvc.perform(jsonBody(post("/api/v1/seller/import-batches/" + batchId + "/jobs"), seller,
                        "{\"items\":[{\"image_ids\":[%d]},{\"image_ids\":[%d,%d]}]}".formatted(a, b, a)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[1].image_ids"));

        String tooMany = IntStream.range(0, 21).mapToObj(i -> "{\"image_ids\":[" + a + "]}")
                .collect(Collectors.joining(",", "[", "]"));
        mvc.perform(jsonBody(post("/api/v1/seller/import-batches/" + batchId + "/jobs"), seller,
                        "{\"items\":" + tooMany + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void otherSellerCannotStartOrSeeMyBatch() throws Exception {
        String other = login(SELLER_NILUFAR);
        mvc.perform(auth(post("/api/v1/seller/stores/" + storeId + "/import-batches"), other))
                .andExpect(status().isForbidden());
        long batchId = startBatch();
        mvc.perform(auth(get("/api/v1/seller/import-batches/" + batchId), other))
                .andExpect(status().isForbidden());
    }

    /** 다른 판매자의 일괄 등록이 쌓여 있어도 단건 스캔(화면에서 기다리는 판매자)을 먼저 처리한다. */
    @Test
    void singleScanIsProcessedBeforeQueuedBatchJobs() throws Exception {
        long batchId = startBatch();
        submit(batchId, "[{\"image_ids\":[" + photo() + "]},{\"image_ids\":[" + photo() + "]}]");
        long single = body(mvc.perform(jsonBody(post("/api/v1/seller/stores/" + storeId + "/scan-jobs"), seller,
                        "{\"image_ids\":[" + photo() + "],\"source_type\":\"UPLOAD\"}"))
                .andExpect(status().isAccepted()).andReturn()).path("id").asLong();

        assertThat(queue.claimNext()).contains(single);
    }

    @Test
    void reviewingEveryPostCompletesOnboardingAndFeedsMetric() throws Exception {
        long batchId = startBatch();
        long a = photo(), b = photo(), c = photo();
        JsonNode submitted = submit(batchId, """
                [{"image_ids":[%d],"caption_text":"Курага 45"},
                 {"image_ids":[%d,%d],"caption_text":"가게 전경 #empty"}]""".formatted(a, b, c));
        List<Long> jobIds = new ArrayList<>();
        submitted.path("jobs").forEach(j -> jobIds.add(j.path("id").asLong()));
        while (processor.processNext()) { /* 두 게시물 모두 처리 */ }

        JsonNode processed = getBatch(batchId);
        assertThat(processed.path("progress").path("succeeded").asInt()).isEqualTo(2);
        assertThat(processed.path("completed_at").isNull()).as("검수 전에는 끝나지 않음").isTrue();

        // 게시물 1: 첫 후보만 수락해서 바로 판매
        JsonNode candidates = body(mvc.perform(auth(get("/api/v1/seller/scan-jobs/" + jobIds.get(0)), seller))
                .andReturn()).path("candidates");
        mvc.perform(jsonBody(post("/api/v1/seller/scan-jobs/" + jobIds.get(0) + "/review"), seller, """
                        {"publish":true,"items":[
                          {"candidate_id":%d,"decision":"ACCEPTED","product":{"category_id":%d,
                            "translations":[{"locale":"ko","name":"말린 살구"}],"price":45}},
                          {"candidate_id":%d,"decision":"REJECTED"}]}
                        """.formatted(candidates.get(0).path("id").asLong(), categoryId("food.dried_fruit"),
                        candidates.get(1).path("id").asLong())))
                .andExpect(status().isCreated());
        assertThat(getBatch(batchId).path("completed_at").isNull()).as("게시물 하나가 남음").isTrue();

        // 게시물 2: AI가 상품을 못 찾은 사진 → 상품 없이 검수 완료
        assertThat(body(mvc.perform(auth(get("/api/v1/seller/scan-jobs/" + jobIds.get(1)), seller)).andReturn())
                .path("candidate_count").asInt()).isZero();
        mvc.perform(jsonBody(post("/api/v1/seller/scan-jobs/" + jobIds.get(1) + "/review"), seller,
                        "{\"publish\":true,\"items\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.products.length()").value(0));

        JsonNode done = getBatch(batchId);
        assertThat(done.path("progress").path("reviewed").asInt()).isEqualTo(2);
        assertThat(done.path("completed_at").isNull()).isFalse();
        assertThat(done.path("onboarding_seconds").asLong()).isGreaterThanOrEqualTo(0);

        em.flush();   // 지표는 SQL로 DB를 직접 읽는다(테스트는 한 트랜잭션이라 먼저 써 준다)
        JsonNode onboarding = body(mvc.perform(auth(get("/api/v1/admin/metrics/scan"), login(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets.onboarding_seconds_for_20_images").value(300))
                .andExpect(jsonPath("$.median_seconds_to_publish").isEmpty())   // 일괄 등록은 단건 지표에서 제외
                .andReturn()).path("onboarding");
        assertThat(onboarding.path("completed").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode mine = null;
        for (JsonNode row : onboarding.path("recent")) {
            if (row.path("id").asLong() == batchId) mine = row;
        }
        assertThat(mine).isNotNull();
        assertThat(mine.path("images").asInt()).isEqualTo(3);
        assertThat(mine.path("posts").asInt()).isEqualTo(2);
        assertThat(mine.path("published").asInt()).isEqualTo(1);
    }

    @Test
    void emptyReviewIsRejectedWhenAiFoundProducts() throws Exception {
        long batchId = startBatch();
        long jobId = submit(batchId, "[{\"image_ids\":[" + photo() + "]}]").path("jobs").get(0).path("id").asLong();
        processor.processNext();
        mvc.perform(jsonBody(post("/api/v1/seller/scan-jobs/" + jobId + "/review"), seller,
                        "{\"publish\":false,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCAN_REVIEW_INCOMPLETE"));
    }
}
