package com.bozor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * 화면 주소는 로그인 없이 열리고(권한은 API에서 검사), API 권한은 그대로인지.
 * 빌드한 화면이 target/classes/static 에 있으면 200(index.html), 없으면 404 가 정상이다. 401·403 이면 안 된다.
 */
class FrontendRoutingTest extends ApiTestSupport {
    @Test
    void screenAddressIsNotBlockedBySecurity() throws Exception {
        int status = mvc.perform(get("/seller/stores/1")).andReturn().getResponse().getStatus();
        assertThat(status).isIn(200, 404);
    }

    @Test
    void apiStillRequiresLoginAndUnknownApiIs404() throws Exception {
        mvc.perform(get("/api/v1/seller/stores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }
}
