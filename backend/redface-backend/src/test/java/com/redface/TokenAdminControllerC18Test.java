package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * C18 卡密生成与导出后台接口测试。
 */
@SpringBootTest(properties = "redface.admin.token=test-admin")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenAdminControllerC18Test extends C9MockMvcSupport {

    private static final String ADMIN_TOKEN = "test-admin";

    @BeforeEach
    void setUp() {
        clearTables();
        insertPlayer(1, 1, "DEMO_A");
        insertPlayer(2, 2, "DEMO_B");
        insertPhotoAsset("photo_A", 1, "url_A");
        insertPhotoAsset("photo_B", 2, "url_B");
        insertPhotoAsset("photo_A_inactive", 1, "url_A2");
        jdbcTemplate.update("UPDATE photo_assets SET status = 'inactive' WHERE asset_id = 'photo_A_inactive'");
    }

    @Test
    void shouldRejectWhenPhotoBelongsToAnotherPlayer() throws Exception {
        mockMvc.perform(post("/api/admin/tokens/generate")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"john\",\"playerId\":1,\"points\":100,\"count\":5,\"photoAssetId\":\"photo_B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(41802));
    }

    @Test
    void shouldRejectWhenPhotoIsInactive() throws Exception {
        mockMvc.perform(post("/api/admin/tokens/generate")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"john\",\"playerId\":1,\"points\":100,\"count\":5,\"photoAssetId\":\"photo_A_inactive\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(41803));
    }

    @Test
    void shouldGenerateAndExportSuccessfully() throws Exception {
        String body = mockMvc.perform(post("/api/admin/tokens/generate")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"john\",\"playerId\":1,\"points\":100,\"count\":3,\"photoAssetId\":\"photo_A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.generatedCount").value(3))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        String batchId = root.path("data").path("batchId").asText();
        assertThat(batchId).startsWith("BATCH-");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'token_generate'", Integer.class))
                .isEqualTo(1);

        String csv = mockMvc.perform(get("/api/admin/tokens/export?batchId=" + batchId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();

        String[] lines = csv.trim().split("\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).startsWith("RFZJ-");
    }
}
