package com.bozor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** 판매자 상점 관리와 공개 상점 조회 */
class StoreTest extends ApiTestSupport {

    @Test
    void storeWithoutSlugGetsStoreIdSlugAndStartsAsDraft() throws Exception {
        String token = login(BUYER_MADINA);
        JsonNode store = body(mvc.perform(jsonBody(post("/api/v1/seller/stores"), token,
                        "{\"name\":\"마디나 향신료\",\"template\":\"LIST\"}"))
                .andExpect(status().isCreated()).andReturn());
        assertThat(store.path("slug").asText())
                .isEqualTo("store-" + store.path("id").asLong());
        assertThat(store.path("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void duplicateSlugIsRejected() throws Exception {
        mvc.perform(jsonBody(post("/api/v1/seller/stores"), login(BUYER_MADINA),
                        "{\"name\":\"중복\",\"slug\":\"farhod-dried-fruit\",\"template\":\"GRID\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_SLUG_TAKEN"));
    }

    @Test
    void storeCannotBeActivatedWithoutContact() throws Exception {
        String token = login(BUYER_MADINA);
        long id = body(mvc.perform(jsonBody(post("/api/v1/seller/stores"), token,
                "{\"name\":\"연락처 없음\",\"template\":\"GRID\"}")).andReturn()).path("id").asLong();
        mvc.perform(jsonBody(post("/api/v1/seller/stores/" + id + "/status"), token, "{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_CONTACT_REQUIRED"));
        mvc.perform(jsonBody(put("/api/v1/seller/stores/" + id), token,
                        "{\"name\":\"연락처 있음\",\"template\":\"GRID\",\"whatsapp_phone\":\"+992000000011\"}"))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(post("/api/v1/seller/stores/" + id + "/status"), token, "{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void otherSellersStoreIsForbidden() throws Exception {
        mvc.perform(auth(get("/api/v1/seller/stores/" + storeId("farhod-dried-fruit")), login(SELLER_NILUFAR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void hiddenStoreDisappearsFromPublicApi() throws Exception {
        mvc.perform(get("/api/v1/stores/nilufar-atlas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template").value("LIST"))
                .andExpect(jsonPath("$.categories[0].code").value("textiles.ikat"));
        mvc.perform(jsonBody(post("/api/v1/seller/stores/" + storeId("nilufar-atlas") + "/status"),
                login(SELLER_NILUFAR), "{\"status\":\"HIDDEN\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/stores/nilufar-atlas")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/stores/nilufar-atlas/products")).andExpect(status().isNotFound());
    }
}
