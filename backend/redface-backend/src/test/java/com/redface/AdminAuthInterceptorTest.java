package com.redface;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 任务卡 C-DEPLOY-01 验收专项：当 redface.admin.token 已配置时，
 * /api/admin/** 无 X-Admin-Token 必须 401，带正确 token 才放行。
 *
 * <p>通过 properties 显式注入 ADMIN_TOKEN，模拟生产配置态；与默认 test profile
 * （未配 token、放行）区分，二者互不影响。
 */
@SpringBootTest(properties = "redface.admin.token=test-secret-token-123")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/collect-state"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void adminWithWrongTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/collect-state").header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void adminWithCorrectTokenShouldPassInterceptor() throws Exception {
        // 带正确 token 时拦截器放行；后续即便业务因空库返回非 200，也不应是 401。
        mockMvc.perform(get("/api/admin/collect-state").header("X-Admin-Token", "test-secret-token-123"))
                .andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }
}
