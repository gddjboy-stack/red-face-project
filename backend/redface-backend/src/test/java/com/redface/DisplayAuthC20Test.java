package com.redface;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * C20-5 展示端鉴权专项测试（已配置 DISPLAY_TOKEN 的情形）。
 *
 * <p>覆盖 Claude 裁定 2.2 的三条硬约束：物理隔离、只读、Cookie 换票；
 * 以及 Manus 自设边界 D-1（限流）、D-2（Cookie 属性）。
 * fail-closed（未配置令牌）另由 {@link DisplayFailClosedC20Test} 覆盖，
 * 因为两者需要不同的 Spring 上下文配置。
 */
@SpringBootTest(properties = {
        "redface.display.token=display-secret-token-abc",
        "redface.admin.token=admin-secret-token-xyz",
        "redface.display.max-login-failures=3",
        "redface.display.cookie-max-age-seconds=43200"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisplayAuthC20Test {

    private static final String DISPLAY_TOKEN = "display-secret-token-abc";
    private static final String ADMIN_TOKEN = "admin-secret-token-xyz";

    @Autowired
    private MockMvc mockMvc;

    // ===== 1. 无凭证一律 401 =====

    @Test
    void displayWithoutCredentialShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/display/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    @Test
    void displayWithWrongCookieShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/display/ping").cookie(new Cookie("RF_DISPLAY", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    // ===== 2. 物理隔离：两个令牌互不通用 =====

    @Test
    void adminTokenMustNotOpenDisplayEndpoint() throws Exception {
        mockMvc.perform(get("/api/display/ping").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    @Test
    void displayTokenMustNotOpenAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/collect-state").header("X-Admin-Token", DISPLAY_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        mockMvc.perform(get("/api/admin/collect-state").header("X-Display-Token", DISPLAY_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        mockMvc.perform(get("/api/admin/collect-state").cookie(new Cookie("RF_DISPLAY", DISPLAY_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    // ===== 3. 换票：Cookie 属性与可用性 =====

    @Test
    void sessionWithCorrectTokenShouldSetHttpOnlyCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/display-auth/session")
                        .contentType("application/json")
                        .content("{\"token\":\"" + DISPLAY_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(43200))
                .andExpect(cookie().httpOnly("RF_DISPLAY", true))
                .andExpect(cookie().path("RF_DISPLAY", "/api/display"))
                .andExpect(cookie().maxAge("RF_DISPLAY", 43200))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("SameSite=Lax"),
                "展示 Cookie 必须带 SameSite=Lax，实际：" + setCookie);
        // 测试 profile 下 cookie-secure 默认 false，不应出现 Secure 标记（生产由 prod profile 固化为 true）
        org.junit.jupiter.api.Assertions.assertFalse(setCookie.contains("Secure"),
                "非生产环境不应强制 Secure，否则本地 http 联调无法取到 Cookie");
    }

    @Test
    void cookieFromSessionShouldPassDisplayEndpoint() throws Exception {
        Cookie sessionCookie = obtainDisplayCookie();
        mockMvc.perform(get("/api/display/ping").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
    }

    @Test
    void headerModeShouldAlsoPassForObs() throws Exception {
        // 裁定要求「两种模式都能用」：若 OBS 浏览器源不持久化 Cookie，可改用自定义请求头。
        mockMvc.perform(get("/api/display/ping").header("X-Display-Token", DISPLAY_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
    }

    @Test
    void logoutShouldExpireCookie() throws Exception {
        mockMvc.perform(post("/api/display-auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("RF_DISPLAY", 0));
    }

    // ===== 4. 只读约束：非 GET/HEAD 一律 405 =====

    @Test
    void displayPostShouldReturn405EvenWithValidCookie() throws Exception {
        Cookie sessionCookie = obtainDisplayCookie();
        mockMvc.perform(post("/api/display/board").cookie(sessionCookie))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40501));
    }

    @Test
    void displayDeleteShouldReturn405() throws Exception {
        Cookie sessionCookie = obtainDisplayCookie();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/display/board").cookie(sessionCookie))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40501));
    }

    // ===== 5. 限流（边界 D-1）=====

    @Test
    void repeatedWrongTokenShouldBeRateLimited() throws Exception {
        String clientIp = "203.0.113.77";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/display-auth/session")
                            .header("X-Forwarded-For", clientIp)
                            .contentType("application/json")
                            .content("{\"token\":\"bad-token\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // 第 4 次（含正确令牌）应被限流拦下，证明锁定优先于令牌比对
        mockMvc.perform(post("/api/display-auth/session")
                        .header("X-Forwarded-For", clientIp)
                        .contentType("application/json")
                        .content("{\"token\":\"" + DISPLAY_TOKEN + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));
    }

    @Test
    void rateLimitShouldBeScopedPerClient() throws Exception {
        String lockedIp = "203.0.113.88";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/display-auth/session")
                            .header("X-Forwarded-For", lockedIp)
                            .contentType("application/json")
                            .content("{\"token\":\"bad-token\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // 另一来源不受影响，避免一台机器被爆破导致全场大屏无法换票
        mockMvc.perform(post("/api/display-auth/session")
                        .header("X-Forwarded-For", "203.0.113.99")
                        .contentType("application/json")
                        .content("{\"token\":\"" + DISPLAY_TOKEN + "\"}"))
                .andExpect(status().isOk());
    }

    // ===== 6. 空请求体不应 500 =====

    @Test
    void sessionWithEmptyBodyShouldReturn401NotError() throws Exception {
        mockMvc.perform(post("/api/display-auth/session")
                        .header("X-Forwarded-For", "198.51.100.1")
                        .contentType("application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    private Cookie obtainDisplayCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/display-auth/session")
                        .header("X-Forwarded-For", "192.0.2.10")
                        .contentType("application/json")
                        .content("{\"token\":\"" + DISPLAY_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("RF_DISPLAY");
        org.junit.jupiter.api.Assertions.assertNotNull(cookie, "换票成功必须下发 RF_DISPLAY Cookie");
        return cookie;
    }
}
