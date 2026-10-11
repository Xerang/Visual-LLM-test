package com.bozor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** 목 로그인·권한·기준 데이터 */
class AuthAndCatalogTest extends ApiTestSupport {

    @Test
    void mockLoginIssuesTokenUsableForMe() throws Exception {
        String token = login(SELLER_FARHOD);
        mvc.perform(auth(get("/api/v1/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("판매자 파르호드"))
                .andExpect(jsonPath("$.stores[0].slug").value("farhod-dried-fruit"));
    }

    @Test
    void protectedApiWithoutTokenReturnsProblemWithCode() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void adminApiRejectsNormalUser() throws Exception {
        mvc.perform(auth(get("/api/v1/admin/metrics/scan"), login(BUYER_MADINA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(auth(get("/api/v1/admin/metrics/scan"), login(ADMIN))).andExpect(status().isOk());
    }

    @Test
    void updateMeValidatesLocale() throws Exception {
        String token = login(BUYER_MADINA);
        mvc.perform(jsonBody(put("/api/v1/me"), token, "{\"display_name\":\"마디나\",\"locale\":\"uz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("locale"));
        mvc.perform(jsonBody(put("/api/v1/me"), token, "{\"display_name\":\"마디나\",\"locale\":\"ru\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("ru"));
    }

    @Test
    void categoryTreeHasNineRootsWithDefaultUnits() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].code").value("food"))
                .andExpect(jsonPath("$[0].name").value("식품"))
                .andExpect(jsonPath("$[0].children[0].code").value("food.dried_fruit"))
                .andExpect(jsonPath("$[0].children[0].default_unit").value("KG"));
    }

    @Test
    void marketsArePublic() throws Exception {
        mvc.perform(get("/api/v1/markets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Меҳргон"));
    }
}
