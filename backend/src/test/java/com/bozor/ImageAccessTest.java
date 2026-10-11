package com.bozor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.awt.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** 사진 접근 제어(임시 결정 #9): 공개 사진이거나 서명 주소일 때만 파일을 내준다. */
class ImageAccessTest extends ApiTestSupport {
    @PersistenceContext EntityManager em;
    String seller;
    long storeId;

    @BeforeEach
    void setUp() throws Exception {
        seller = login(SELLER_FARHOD);
        storeId = storeId("farhod-dried-fruit");
    }

    JsonNode upload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png",
                png(320, 240, new Color((int) (Math.random() * 0xFFFFFF))));
        return body(mvc.perform(auth(multipart("/api/v1/images").file(file), seller))
                .andExpect(status().isCreated()).andReturn());
    }

    /**
     * 테스트는 요청 전체를 한 트랜잭션으로 묶고 롤백하므로, JPA 변경이 DB에 아직 안 써졌을 수 있다.
     * 공개 여부 검사는 SQL로 DB를 직접 보므로 먼저 써 준다. 실제 요청은 매번 커밋되므로 필요 없다.
     */
    void flush() {
        em.flush();
    }

    static String plain(long imageId) {
        return "/api/v1/images/" + imageId + "/file";
    }

    @Test
    void uploadedPhotoIsVisibleOnlyThroughSignedUrl() throws Exception {
        JsonNode image = upload();
        long id = image.path("id").asLong();
        assertThat(image.path("url").asText()).startsWith(plain(id) + "?exp=").contains("&sig=");

        mvc.perform(get(image.path("url").asText()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600, private"));
        mvc.perform(get(image.path("thumbnail_url").asText())).andExpect(status().isOk());

        mvc.perform(get(plain(id))).andExpect(status().isNotFound());
        mvc.perform(get(plain(id) + "?size=thumb")).andExpect(status().isNotFound());
    }

    @Test
    void signatureCannotBeReusedForAnotherPhotoOrTampered() throws Exception {
        JsonNode mine = upload();
        long other = upload().path("id").asLong();
        String query = mine.path("url").asText().substring(mine.path("url").asText().indexOf('?'));

        mvc.perform(get(plain(other) + query)).andExpect(status().isNotFound());
        mvc.perform(get(mine.path("url").asText().replace("sig=", "sig=x"))).andExpect(status().isNotFound());
        // 만료 시각을 늘리면 서명이 맞지 않는다
        String exp = query.substring("?exp=".length(), query.indexOf('&'));
        String longer = mine.path("url").asText().replace("exp=" + exp, "exp=" + (Long.parseLong(exp) + 86400));
        mvc.perform(get(longer)).andExpect(status().isNotFound());
    }

    @Test
    void publishedProductPhotoIsPublicUntilHidden() throws Exception {
        long imageId = upload().path("id").asLong();
        long productId = body(mvc.perform(jsonBody(post("/api/v1/seller/stores/" + storeId + "/products"), seller, """
                        {"category_id":%d,"translations":[{"locale":"ko","name":"접근 테스트"}],"price":10,"image_ids":[%d]}
                        """.formatted(categoryId("food.spices"), imageId)))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
        flush();

        mvc.perform(get(plain(imageId))).andExpect(status().isNotFound());   // 초안은 아직 비공개

        mvc.perform(jsonBody(post("/api/v1/seller/products/" + productId + "/status"), seller, "{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
        flush();
        mvc.perform(get(plain(imageId) + "?size=thumb"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=86400, public"));

        mvc.perform(jsonBody(post("/api/v1/seller/products/" + productId + "/status"), seller, "{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk());
        flush();
        mvc.perform(get(plain(imageId))).andExpect(status().isNotFound());

        // 판매자 화면은 숨긴 상품 사진도 서명 주소로 계속 볼 수 있다
        JsonNode product = body(mvc.perform(auth(get("/api/v1/seller/products/" + productId), seller)).andReturn());
        mvc.perform(get(product.path("images").get(0).path("thumbnail_url").asText())).andExpect(status().isOk());
    }

    @Test
    void activeStoreLogoIsPublicButHiddenStoreLogoIsNot() throws Exception {
        long logo = upload().path("id").asLong();
        jdbc.update("UPDATE stores SET logo_image_id = ? WHERE id = ?", logo, storeId);
        mvc.perform(get(plain(logo))).andExpect(status().isOk());

        jdbc.update("UPDATE stores SET status = 'HIDDEN' WHERE id = ?", storeId);
        mvc.perform(get(plain(logo))).andExpect(status().isNotFound());
    }
}
