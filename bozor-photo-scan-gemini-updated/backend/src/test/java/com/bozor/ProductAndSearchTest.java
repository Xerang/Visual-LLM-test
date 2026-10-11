package com.bozor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 판매자 상품 관리(직접 등록·상태·재고)와 구매자 검색 */
class ProductAndSearchTest extends ApiTestSupport {
    String seller;
    long storeId;

    @BeforeEach
    void setUp() throws Exception {
        seller = login(SELLER_FARHOD);
        storeId = storeId("farhod-dried-fruit");
    }

    long createDraft(String categoryCode, String name, String priceJson) throws Exception {
        long image = uploadImage(seller, new Color((int) (Math.random() * 0xFFFFFF)));
        JsonNode product = body(mvc.perform(jsonBody(post("/api/v1/seller/stores/" + storeId + "/products"), seller, """
                        {"category_id":%d,"translations":[{"locale":"ko","name":"%s"}],"price":%s,"image_ids":[%d]}
                        """.formatted(categoryId(categoryCode), name, priceJson, image)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn());
        return product.path("id").asLong();
    }

    @Test
    void parentCategoryIsNotSelectable() throws Exception {
        mvc.perform(jsonBody(post("/api/v1/seller/stores/" + storeId + "/products"), seller,
                        "{\"category_id\":" + categoryId("food") + ",\"translations\":[{\"locale\":\"ko\",\"name\":\"x\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category_id"));
    }

    @Test
    void publishRequiresPriceAndFollowsStatusRules() throws Exception {
        long id = createDraft("food.spices", "쿠민", "null");
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + id + "/status"), seller, "{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_PRICE_REQUIRED"));

        long priced = createDraft("food.spices", "쿠민 씨앗", "30");
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + priced + "/status"), seller, "{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("KG"))
                .andExpect(jsonPath("$.published_at").isNotEmpty());
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + priced + "/status"), seller, "{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + priced + "/status"), seller, "{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_STATUS_INVALID"));
        mvc.perform(auth(delete("/api/v1/seller/products/" + priced), seller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_DELETABLE"));
        mvc.perform(auth(delete("/api/v1/seller/products/" + id), seller)).andExpect(status().isNoContent());
    }

    @Test
    void stockIsManagedBySellerAndMustBeWholeForCountableUnits() throws Exception {
        long id = createDraft("household.kitchen", "찻잔", "20");
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + id + "/stock"), seller, "{\"stock_quantity\":1.5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + id + "/stock"), seller, "{\"stock_quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock_quantity").value(0));
        mvc.perform(jsonBody(post("/api/v1/seller/products/" + id + "/stock"), seller, "{\"stock_quantity\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock_quantity").isEmpty());
    }

    @Test
    void shortKoreanQueryFindsProducts() throws Exception {
        mvc.perform(get("/api/v1/products").param("q", "살구"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("말린 살구(쿠라가)"))
                .andExpect(jsonPath("$.items[0].store.slug").value("farhod-dried-fruit"));
    }

    @Test
    void parentCategoryFilterIncludesChildren() throws Exception {
        JsonNode result = body(mvc.perform(get("/api/v1/products").param("category_id", String.valueOf(categoryId("food"))))
                .andExpect(status().isOk()).andReturn());
        assertThat(result.path("total_elements").asLong()).isEqualTo(3);
    }

    @Test
    void draftsAreNotPublicAndLocaleFallsBack() throws Exception {
        long draft = createDraft("food.spices", "공개 안 됨", "10");
        mvc.perform(get("/api/v1/products/" + draft)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products").param("q", "말린 살구").param("locale", "ru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].locale").value("ko"));   // ru 번역이 없어 기본 언어로 대체
    }

    @Test
    void invalidSortIsRejected() throws Exception {
        mvc.perform(get("/api/v1/products").param("sort", "cheap"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }
}
