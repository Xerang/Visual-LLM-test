package com.bozor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bozor.scan.ScanJobProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Color;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 핵심 흐름: 사진 → 스캔 요청(202) → 워커 처리(가짜 AI) → 검수 확정 → 상품 발행 → 지표.
 * 테스트에서는 워커가 꺼져 있어 processor.processNext() 를 직접 부른다.
 */
class ScanFlowTest extends ApiTestSupport {
    @Autowired ScanJobProcessor processor;

    String seller;
    long storeId;

    @BeforeEach
    void setUp() throws Exception {
        seller = login(SELLER_FARHOD);
        storeId = storeId("farhod-dried-fruit");
    }

    long requestScan(String caption) throws Exception {
        long imageId = uploadImage(seller, new Color((int) (Math.random() * 0xFFFFFF)));
        String captionJson = caption == null ? "null" : "\"" + caption + "\"";
        JsonNode job = body(mvc.perform(jsonBody(post("/api/v1/seller/stores/" + storeId + "/scan-jobs"), seller,
                        "{\"image_ids\":[" + imageId + "],\"source_type\":\"SNS_IMPORT\",\"caption_text\":" + captionJson + "}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.output_locale").value("ko"))
                .andReturn());
        return job.path("id").asLong();
    }

    JsonNode getJob(long jobId) throws Exception {
        return body(mvc.perform(auth(get("/api/v1/seller/scan-jobs/" + jobId), seller)).andReturn());
    }

    @Test
    void inProgressJobAsksClientToPollAgain() throws Exception {
        long jobId = requestScan(null);
        mvc.perform(auth(get("/api/v1/seller/scan-jobs/" + jobId), seller))
                .andExpect(status().isOk())
                .andExpect(header().string("Retry-After", "2"));
    }

    @Test
    void workerCreatesCandidatesAndUsesOnlyCaptionPrice() throws Exception {
        long jobId = requestScan("Курага 45 сомони/кг");
        assertThat(processor.processNext()).isTrue();

        JsonNode job = getJob(jobId);
        assertThat(job.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(job.path("candidates")).hasSize(2);
        assertThat(job.path("candidates").get(0).path("suggested_price").decimalValue()).isEqualByComparingTo("45");
        assertThat(job.path("candidates").get(1).path("suggested_price").isNull()).isTrue();
        assertThat(job.path("candidates").get(0).path("category").path("code").asText()).isEqualTo("food.dried_fruit");

        long noCaption = requestScan(null);
        processor.processNext();
        assertThat(getJob(noCaption).path("candidates").get(0).path("suggested_price").isNull()).isTrue();
    }

    @Test
    void rateLimitIsRetriedLaterAndBadResponseFailsImmediately() throws Exception {
        long limited = requestScan("#fail429");
        processor.processNext();
        JsonNode job = getJob(limited);
        assertThat(job.path("status").asText()).isEqualTo("QUEUED");
        assertThat(job.path("error").path("code").asText()).isEqualTo("AI_RATE_LIMITED");
        assertThat(Instant.parse(job.path("available_at").asText())).isAfter(Instant.now().plusSeconds(20));
        assertThat(processor.processNext()).as("미뤄진 Job은 바로 다시 가져가지 않는다").isFalse();

        long broken = requestScan("#fail");
        processor.processNext();
        assertThat(getJob(broken).path("status").asText()).isEqualTo("FAILED");
        mvc.perform(auth(post("/api/v1/seller/scan-jobs/" + broken + "/retry"), seller))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.attempt_count").value(0));
        mvc.perform(auth(post("/api/v1/seller/scan-jobs/" + broken + "/retry"), seller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCAN_JOB_NOT_RETRYABLE"));
    }

    @Test
    void reviewMustDecideEveryAiCandidate() throws Exception {
        long jobId = requestScan(null);
        mvc.perform(jsonBody(post("/api/v1/seller/scan-jobs/" + jobId + "/review"), seller,
                        "{\"publish\":false,\"items\":[{\"candidate_id\":null,\"decision\":\"ACCEPTED\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCAN_JOB_NOT_REVIEWABLE"));
        processor.processNext();
        long first = getJob(jobId).path("candidates").get(0).path("id").asLong();
        mvc.perform(jsonBody(post("/api/v1/seller/scan-jobs/" + jobId + "/review"), seller,
                        "{\"publish\":false,\"items\":[{\"candidate_id\":" + first + ",\"decision\":\"REJECTED\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCAN_REVIEW_INCOMPLETE"));
    }

    @Test
    void reviewCreatesPublishedProductsAndFeedsMetrics() throws Exception {
        long jobId = requestScan("Курага 45");
        processor.processNext();
        JsonNode candidates = getJob(jobId).path("candidates");
        long accept = candidates.get(0).path("id").asLong();
        long reject = candidates.get(1).path("id").asLong();
        long driedFruit = categoryId("food.dried_fruit");
        long nuts = categoryId("food.nuts");

        // 가격 없이 바로 발행하면 거절
        mvc.perform(jsonBody(post("/api/v1/seller/scan-jobs/" + jobId + "/review"), seller, """
                        {"publish":true,"items":[
                          {"candidate_id":%d,"decision":"ACCEPTED","product":{"category_id":%d,
                            "translations":[{"locale":"ko","name":"말린 살구"}]}},
                          {"candidate_id":%d,"decision":"REJECTED"}]}
                        """.formatted(accept, driedFruit, reject)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_PRICE_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].product.price"));

        JsonNode review = body(mvc.perform(jsonBody(post("/api/v1/seller/scan-jobs/" + jobId + "/review"), seller, """
                        {"publish":true,"items":[
                          {"candidate_id":%d,"decision":"ACCEPTED","product":{"category_id":%d,
                            "translations":[{"locale":"ko","name":"말린 살구"}],"price":45,"price_type":"NEGOTIABLE"}},
                          {"candidate_id":%d,"decision":"REJECTED"},
                          {"candidate_id":null,"decision":"ACCEPTED","product":{"category_id":%d,
                            "translations":[{"locale":"ko","name":"아몬드"}],"price":70}}]}
                        """.formatted(accept, driedFruit, reject, nuts)))
                .andExpect(status().isCreated()).andReturn());
        assertThat(review.path("products")).hasSize(2);
        assertThat(review.path("products").get(0).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(review.path("products").get(0).path("unit").asText()).as("카테고리 기본 단위").isEqualTo("KG");

        JsonNode after = getJob(jobId);
        assertThat(after.path("reviewed_at").isNull()).isFalse();
        assertThat(after.path("candidates")).hasSize(3);
        assertThat(after.path("candidates").get(2).path("origin").asText()).isEqualTo("SELLER");
        assertThat(after.path("candidates").get(0).path("product_id").asLong())
                .isEqualTo(review.path("products").get(0).path("id").asLong());

        // 스캔 사진이 상품 사진으로 붙었는지(공개 상세)
        long productId = review.path("products").get(0).path("id").asLong();
        mvc.perform(get("/api/v1/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.price_type").value("NEGOTIABLE"));

        mvc.perform(jsonBody(post("/api/v1/seller/scan-jobs/" + jobId + "/review"), seller,
                        "{\"publish\":false,\"items\":[{\"candidate_id\":" + reject + ",\"decision\":\"REJECTED\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCAN_JOB_ALREADY_REVIEWED"));

        // 지표: AI 후보 2개 중 1개 수락, 판매자 추가 1개 → 정밀도 0.5, 재현율 0.5, 카테고리 일치 1.0
        mvc.perform(auth(get("/api/v1/admin/metrics/scan"), login(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.seller_added").value(1))
                .andExpect(jsonPath("$.precision").value(0.5))
                .andExpect(jsonPath("$.recall").value(0.5))
                .andExpect(jsonPath("$.category_accuracy.leaf").value(1.0));
    }

    @Test
    void otherSellerCannotSeeOrReviewMyScan() throws Exception {
        long jobId = requestScan(null);
        mvc.perform(auth(get("/api/v1/seller/scan-jobs/" + jobId), login(SELLER_NILUFAR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void scanRejectsSomeoneElsesImage() throws Exception {
        long othersImage = uploadImage(login(SELLER_NILUFAR), Color.BLUE);
        mvc.perform(jsonBody(post("/api/v1/seller/stores/" + storeId + "/scan-jobs"), seller,
                        "{\"image_ids\":[" + othersImage + "],\"source_type\":\"UPLOAD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("image_ids"));
    }
}
