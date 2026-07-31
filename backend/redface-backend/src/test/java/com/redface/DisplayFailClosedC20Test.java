package com.redface;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C20-5 展示端 fail-closed 专项测试（Claude 裁定 2.2 的核心分歧点）。
 *
 * <p>Admin 侧为兼容联调，在 token 未配置时选择放行（fail-open）；展示端<b>不得</b>沿用该行为。
 * 原因在于风险性质不同：admin 漏配是「后台被人写数据」，尚有部署流程与内网可依赖；
 * display 漏配则是「排行榜对全网公开」，一旦被抓取即形成不可回收的数据外流。
 * 因此这里显式断言未配置令牌时 <b>全部 401</b>，任何把 display 改成 fail-open 的改动都会打红这些用例。
 *
 * <p>本类不设置 {@code redface.display.token}，与 {@link DisplayAuthC20Test} 形成互补的两套上下文。
 */
@SpringBootTest(properties = {
        "redface.display.token=",
        "redface.admin.token="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisplayFailClosedC20Test {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void displayEndpointShouldRejectWhenTokenNotConfigured() throws Exception {
        mockMvc.perform(get("/api/display/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    @Test
    void displayBoardShouldRejectWhenTokenNotConfigured() throws Exception {
        mockMvc.perform(get("/api/display/board").param("tab", "player"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    @Test
    void emptyCredentialMustNotMatchEmptyConfiguredToken() throws Exception {
        // 防御「空令牌匹配空配置」这类等值比较陷阱：即便客户端主动送空 Cookie/空 header，也必须 401。
        mockMvc.perform(get("/api/display/ping").cookie(new Cookie("RF_DISPLAY", "")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
        mockMvc.perform(get("/api/display/ping").header("X-Display-Token", ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    @Test
    void sessionEndpointShouldRejectWhenTokenNotConfigured() throws Exception {
        // 换票端点同样 fail-closed，避免出现「服务端没配令牌，客户端送空串就换到 Cookie」的旁路。
        mockMvc.perform(post("/api/display-auth/session")
                        .contentType("application/json")
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    @Test
    void adminStillFailOpenWhenTokenNotConfigured() throws Exception {
        // 反向确认：本次改动没有把 display 的 fail-closed 策略误加到 admin 上，
        // 既有 AdminAuthInterceptorTest 与联调行为保持不变。
        mockMvc.perform(get("/api/admin/collect-state"))
                .andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }
}
