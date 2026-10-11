package com.bozor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 통합 테스트 공통 설정.
 * - 실제 PostgreSQL 테스트 DB(bozor_test)를 쓴다. application-test.yml 참고.
 * - 테스트마다 트랜잭션을 롤백해서 서로 영향을 주지 않는다.
 * - dev 목 데이터(사용자 6명, 상점 3곳)를 그대로 쓴다. id 는 하드코딩하지 않고 전화번호·slug 로 찾는다.
 * - AI는 가짜 모드(app.vision.mode=fake)라 Gemini 키 없이 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;

    protected static final String SELLER_FARHOD = "+992000000001";
    protected static final String SELLER_NILUFAR = "+992000000002";
    protected static final String BUYER_MADINA = "+992000000011";
    protected static final String ADMIN = "+992000000099";

    protected long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM users WHERE phone_number = ?", Long.class, phone);
    }

    protected long storeId(String slug) {
        return jdbc.queryForObject("SELECT id FROM stores WHERE slug = ?", Long.class, slug);
    }

    protected long categoryId(String code) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE code = ?", Long.class, code);
    }

    /** 목 로그인으로 토큰을 받는다. */
    protected String login(String phone) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/dev/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"user_id\":" + userId(phone) + "}")).andReturn();
        return body(result).path("access_token").asText();
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder request, String token, String body) {
        return auth(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected JsonNode body(MvcResult result) throws IOException {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    /** 단색 PNG. 색을 바꾸면 SHA-256 이 달라져 새 사진으로 저장된다. */
    protected static byte[] png(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    protected long uploadImage(String token, Color color) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png(320, 240, color));
        MvcResult result = mvc.perform(auth(multipart("/api/v1/images").file(file), token)).andReturn();
        return body(result).path("id").asLong();
    }
}
