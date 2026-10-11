package com.bozor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 다국어 상품 정보: AI 번역 초안과 "판매자가 확인하지 않은 번역" 표시 규칙 (가짜 AI는 "[ru] 원문" 형태로 번역) */
class ProductTranslationTest extends ApiTestSupport {
    @PersistenceContext EntityManager em;
    String seller;
    long storeId;

    @BeforeEach
    void setUp() throws Exception {
        seller = login(SELLER_FARHOD);
        storeId = storeId("farhod-dried-fruit");
    }

    long create(String name) throws Exception {
        long image = uploadImage(seller, new Color((int) (Math.random() * 0xFFFFFF)));
        return body(mvc.perform(jsonBody(post("/api/v1/seller/stores/" + storeId + "/products"), seller, """
                        {"category_id":%d,"translations":[{"locale":"ko","name":"%s","description":"씨를 뺀 살구"}],
                         "price":45,"image_ids":[%d]}""".formatted(categoryId("food.dried_fruit"), name, image)))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
    }

    JsonNode autoTranslate(long productId, String body) throws Exception {
        return body(mvc.perform(jsonBody(post("/api/v1/seller/products/" + productId + "/translations/auto"), seller, body))
                .andExpect(status().isOk()).andReturn());
    }

    static Map<String, JsonNode> byLocale(JsonNode product) {
        Map<String, JsonNode> map = new HashMap<>();
        product.path("translations").forEach(t -> map.put(t.path("locale").asText(), t));
        return map;
    }

    /** 화면처럼: 판매자 언어(ko)는 고쳐서, 나머지 번역은 받은 그대로 다시 보낸다(is_auto_translated 없이). */
    String putBody(JsonNode product, String koName, String ruName) {
        StringBuilder tr = new StringBuilder();
        for (JsonNode t : product.path("translations")) {
            String loc = t.path("locale").asText();
            String name = loc.equals("ko") ? koName : loc.equals("ru") && ruName != null ? ruName : t.path("name").asText();
            if (tr.length() > 0) tr.append(',');
            tr.append("{\"locale\":\"%s\",\"name\":\"%s\",\"description\":%s}".formatted(loc, name,
                    t.path("description").isNull() ? "null" : "\"" + t.path("description").asText() + "\""));
        }
        return "{\"category_id\":%d,\"translations\":[%s],\"price\":45}".formatted(
                product.path("category").path("id").asLong(), tr);
    }

    @Test
    void draftsTajikRussianEnglishFromSellerText() throws Exception {
        long id = create("말린 살구");
        Map<String, JsonNode> t = byLocale(autoTranslate(id, ""));
        assertThat(t.keySet()).containsExactlyInAnyOrder("ko", "tg", "ru", "en");
        assertThat(t.get("ru").path("name").asText()).isEqualTo("[ru] 말린 살구");
        assertThat(t.get("ru").path("description").asText()).isEqualTo("[ru] 씨를 뺀 살구");
        assertThat(t.get("ru").path("is_auto_translated").asBoolean()).isTrue();
        assertThat(t.get("ko").path("is_auto_translated").asBoolean()).as("판매자 원문").isFalse();
    }

    @Test
    void savingOtherFieldsKeepsAutoFlagButEditingOrConfirmingClearsIt() throws Exception {
        long id = create("말린 살구");
        JsonNode drafted = autoTranslate(id, "{\"locales\":[\"ru\",\"tg\"]}");

        // 가격·원문만 고쳐 저장: 손대지 않은 기계 번역은 여전히 "자동 번역"
        JsonNode saved = body(mvc.perform(jsonBody(put("/api/v1/seller/products/" + id), seller, putBody(drafted, "말린 살구", null)))
                .andExpect(status().isOk()).andReturn());
        assertThat(byLocale(saved).get("ru").path("is_auto_translated").asBoolean()).isTrue();

        // 러시아어를 판매자가 고침 → 판매자 확인
        saved = body(mvc.perform(jsonBody(put("/api/v1/seller/products/" + id), seller, putBody(saved, "말린 살구", "Курага")))
                .andExpect(status().isOk()).andReturn());
        assertThat(byLocale(saved).get("ru").path("is_auto_translated").asBoolean()).isFalse();
        assertThat(byLocale(saved).get("tg").path("is_auto_translated").asBoolean()).isTrue();

        // 다시 만들어도 판매자가 쓴 러시아어는 덮어쓰지 않는다
        Map<String, JsonNode> again = byLocale(autoTranslate(id, ""));
        assertThat(again.get("ru").path("name").asText()).isEqualTo("Курага");
        assertThat(again.get("en").path("is_auto_translated").asBoolean()).isTrue();

        // 고치지 않고 "확인"만: is_auto_translated=false 를 보낸다
        String confirm = "{\"category_id\":%d,\"translations\":[{\"locale\":\"ko\",\"name\":\"말린 살구\",\"description\":\"씨를 뺀 살구\"},"
                + "{\"locale\":\"tg\",\"name\":\"%s\",\"description\":\"%s\",\"is_auto_translated\":false}],\"price\":45}";
        Map<String, JsonNode> confirmed = byLocale(body(mvc.perform(jsonBody(put("/api/v1/seller/products/" + id), seller,
                        confirm.formatted(categoryId("food.dried_fruit"), again.get("tg").path("name").asText(),
                                again.get("tg").path("description").asText())))
                .andExpect(status().isOk()).andReturn()));
        assertThat(confirmed.get("tg").path("is_auto_translated").asBoolean()).isFalse();
    }

    @Test
    void nothingToTranslateWhenSellerWroteEveryLanguage() throws Exception {
        long id = create("말린 살구");
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + id + "/translations/auto"), seller, "{\"locales\":[\"ko\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("locales"));
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + id + "/translations/auto"), seller, "{\"locales\":[\"xx\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aiRateLimitBecomesRetryable503AndOtherSellerIsForbidden() throws Exception {
        long id = create("#fail429 살구");
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + id + "/translations/auto"), seller, ""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + id + "/translations/auto"), login(SELLER_NILUFAR), ""))
                .andExpect(status().isForbidden());
    }

    @Test
    void buyerSeesRequestedLanguageWithAutoTranslatedMark() throws Exception {
        long id = create("말린 살구");
        autoTranslate(id, "{\"locales\":[\"ru\"]}");
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + id + "/status"), seller, "{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
        em.flush();   // 공개 상세는 SQL로 DB를 직접 읽는다(테스트는 한 트랜잭션이라 먼저 써 준다)

        mvc.perform(get("/api/v1/products/" + id).param("locale", "ru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("[ru] 말린 살구"))
                .andExpect(jsonPath("$.locale").value("ru"))
                .andExpect(jsonPath("$.is_auto_translated").value(true));
        mvc.perform(get("/api/v1/products/" + id).param("locale", "ko"))
                .andExpect(jsonPath("$.name").value("말린 살구"))
                .andExpect(jsonPath("$.is_auto_translated").value(false));
        // 번역이 없는 언어는 기본 언어(판매자 원문)
        mvc.perform(get("/api/v1/products/" + id).param("locale", "tg"))
                .andExpect(jsonPath("$.locale").value("ko"));
    }
}
