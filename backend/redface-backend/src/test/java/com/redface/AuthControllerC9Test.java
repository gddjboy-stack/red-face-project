package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C9 API-0 登录鉴权 Controller 测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerC9Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
    }

    @Test
    void loginShouldReturnUserIdIsNewUserAndToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"code\":\"code_auth_a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").isString())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.path("data").path("token").asText()).isNotBlank();
    }

    @Test
    void repeatedLoginSameCodeShouldReturnStableUserIdAndNewToken() throws Exception {
        String firstUserId = loginAndGetUserId("code_repeat");
        String firstToken = loginAndGetToken("code_repeat");
        String secondUserId = loginAndGetUserId("code_repeat");
        String secondToken = loginAndGetToken("code_repeat");

        assertThat(secondUserId).isEqualTo(firstUserId);
        assertThat(secondToken).isNotEqualTo(firstToken);
    }

    @Test
    void invalidCodeShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"code\":\"invalid_code\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void mePhotosWithoutLoginShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/photos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
