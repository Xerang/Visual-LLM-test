package com.bozor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 주문: 스냅샷·재고 확인·판매자 접수 시 선택적 재고 차감·취소 시 복구 */
class OrderTest extends ApiTestSupport {
    String buyer;
    String seller;
    long storeId;
    long apricotId;   // 말린 살구 45 TJS/kg, 흥정 가능, 재고 120kg
    long raisinId;    // 건포도 38 TJS/kg, 정가, 재고 80kg

    @BeforeEach
    void setUp() throws Exception {
        buyer = login(BUYER_MADINA);
        seller = login(SELLER_FARHOD);
        storeId = storeId("farhod-dried-fruit");
        apricotId = productIdByName("말린 살구(쿠라가)");
        raisinId = productIdByName("건포도(마비즈)");
    }

    long productIdByName(String name) {
        return jdbc.queryForObject("SELECT product_id FROM product_translations WHERE name = ?", Long.class, name);
    }

    BigDecimal stockOf(long productId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", BigDecimal.class, productId);
    }

    JsonNode place(String itemsJson) throws Exception {
        return body(mvc.perform(jsonBody(post("/api/v1/orders"), buyer,
                        "{\"store_id\":" + storeId + ",\"items\":" + itemsJson + ",\"fulfillment_type\":\"PICKUP\"}"))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void orderSnapshotsPricesAndMarksNegotiableTotalsAsEstimate() throws Exception {
        JsonNode order = place("[{\"product_id\":" + apricotId + ",\"quantity\":1.5},{\"product_id\":" + raisinId + ",\"quantity\":2}]");
        assertThat(order.path("order_number").asText()).matches("BZ-\\d{6}-\\d{5}");
        assertThat(order.path("total_amount").decimalValue()).isEqualByComparingTo("143.50");   // 67.5 + 76
        assertThat(order.path("is_estimate").asBoolean()).isTrue();
        assertThat(order.path("contact_phone").asText()).isEqualTo(BUYER_MADINA);
        assertThat(order.path("status").asText()).isEqualTo("REQUESTED");
    }

    @Test
    void cannotOrderMoreThanStock() throws Exception {
        mvc.perform(jsonBody(post("/api/v1/orders"), buyer, "{\"store_id\":" + storeId + ",\"items\":[{\"product_id\":"
                        + raisinId + ",\"quantity\":81}],\"fulfillment_type\":\"PICKUP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ITEM_UNAVAILABLE"));
    }

    @Test
    void cannotMixProductsFromAnotherStore() throws Exception {
        long atlas = productIdByName("아틀라스 원단");
        mvc.perform(jsonBody(post("/api/v1/orders"), buyer, "{\"store_id\":" + storeId + ",\"items\":[{\"product_id\":"
                        + atlas + ",\"quantity\":1}],\"fulfillment_type\":\"PICKUP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("items[0]"));
    }

    @Test
    void stockIsDeductedOnlyWhenSellerChoosesAndRestoredOnCancel() throws Exception {
        long withoutDeduct = place("[{\"product_id\":" + raisinId + ",\"quantity\":2}]").path("id").asLong();
        mvc.perform(jsonBody(post("/api/v1/seller/orders/" + withoutDeduct + "/status"), seller,
                        "{\"status\":\"CONFIRMED\",\"deduct_stock\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock_deducted").value(false));
        assertThat(stockOf(raisinId)).isEqualByComparingTo("80");

        long withDeduct = place("[{\"product_id\":" + raisinId + ",\"quantity\":2.5}]").path("id").asLong();
        mvc.perform(jsonBody(post("/api/v1/seller/orders/" + withDeduct + "/status"), seller,
                        "{\"status\":\"CONFIRMED\",\"deduct_stock\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock_deducted").value(true));
        assertThat(stockOf(raisinId)).isEqualByComparingTo("77.5");

        mvc.perform(auth(post("/api/v1/orders/" + withDeduct + "/cancel"), buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stockOf(raisinId)).isEqualByComparingTo("80");
    }

    @Test
    void sellerFollowsStatusOrderAndBuyerCannotCancelAfterReady() throws Exception {
        long id = place("[{\"product_id\":" + raisinId + ",\"quantity\":1}]").path("id").asLong();
        String url = "/api/v1/seller/orders/" + id + "/status";
        mvc.perform(jsonBody(post(url), seller, "{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATUS_INVALID"));
        mvc.perform(jsonBody(post(url), seller, "{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
        mvc.perform(jsonBody(post(url), seller, "{\"status\":\"READY\"}")).andExpect(status().isOk());
        mvc.perform(auth(post("/api/v1/orders/" + id + "/cancel"), buyer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATUS_INVALID"));
        mvc.perform(jsonBody(post(url), seller, "{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void ordersAreVisibleOnlyToBuyerAndStoreOwner() throws Exception {
        long id = place("[{\"product_id\":" + raisinId + ",\"quantity\":1}]").path("id").asLong();
        mvc.perform(auth(get("/api/v1/orders/" + id), login("+992000000012"))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/seller/orders/" + id), login(SELLER_NILUFAR))).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/seller/stores/" + storeId + "/orders"), seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
    }
}
