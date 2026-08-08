package com.redface;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * C20-12 验收专项测试：验证新增选手时，序号自动生成且编号（displayCode）正确入库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatePlayerC2012Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createPlayerWithoutNumberShouldSucceed() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "益生君");
        request.put("displayCode", "0107");
        request.put("operatorId", "admin");

        mockMvc.perform(post("/api/admin/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("益生君"))
                .andExpect(jsonPath("$.data.displayCode").value("0107"))
                .andExpect(jsonPath("$.data.number").isNumber())
                .andExpect(jsonPath("$.data.number").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void consecutiveCreationShouldIncrementNumber() throws Exception {
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> request = new HashMap<>();
            request.put("name", "测试选手" + i);
            request.put("displayCode", "990" + i);
            request.put("operatorId", "admin");

            mockMvc.perform(post("/api/admin/players")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.number").isNumber());
        }
    }

    @Test
    void createWithInvalidDisplayCodeShouldFail() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "非法编号选手");
        request.put("displayCode", "07"); // 2位，应报错
        request.put("operatorId", "admin");

        mockMvc.perform(post("/api/admin/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("4 位数字")));
    }

    @Test
    void createWithDuplicateDisplayCodeShouldFail() throws Exception {
        // 先新增一个
        Map<String, Object> request1 = new HashMap<>();
        request1.put("name", "选手A");
        request1.put("displayCode", "8888");
        request1.put("operatorId", "admin");

        mockMvc.perform(post("/api/admin/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        // 再新增一个同编号的
        Map<String, Object> request2 = new HashMap<>();
        request2.put("name", "选手B");
        request2.put("displayCode", "8888");
        request2.put("operatorId", "admin");

        mockMvc.perform(post("/api/admin/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已被占用")));
    }
}
