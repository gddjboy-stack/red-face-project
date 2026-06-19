package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

/**
 * C17 写真上传管理安全与回归测试。
 */
@SpringBootTest(properties = {
        "redface.admin.token=test-admin",
        "redface.photo-storage.upload-dir=${java.io.tmpdir}/redface-c17-test",
        "redface.photo-storage.public-path=/uploads/photos/",
        "redface.photo-storage.max-size-bytes=64"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhotoAdminControllerC17Test extends C9MockMvcSupport {

    private static final String ADMIN_TOKEN = "test-admin";

    @BeforeEach
    void setUp() {
        clearTables();
        insertPlayer(1, 1, "DEMO_小红");
        insertPlayer(2, 2, "DEMO_小蓝");
    }

    @Test
    void shouldUploadValidPngAndWriteAuditLog() throws Exception {
        String body = mockMvc.perform(multipart("/api/admin/photos/upload")
                        .file(new MockMultipartFile("file", "stage.png", "image/png", pngBytes()))
                        .param("operatorId", "john")
                        .param("playerId", "1")
                        .param("isCover", "true")
                        .param("sortOrder", "0")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.playerId").value(1))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.isCover").value(true))
                .andExpect(jsonPath("$.data.previewUrl").value(org.hamcrest.Matchers.containsString("/uploads/photos/photo_1_")))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        String assetId = root.path("data").path("assetId").asText();
        assertThat(assetId).startsWith("photo_1_");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM photo_assets WHERE asset_id = ? AND file_name = ? AND content_type = ?", Integer.class, assetId, "stage.png", "image/png"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'photo_upload' AND operator_id = 'john'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectSvgRenamedTextEmptyAndMissingPlayer() throws Exception {
        mockMvc.perform(multipart("/api/admin/photos/upload")
                        .file(new MockMultipartFile("file", "bad.svg", "image/svg+xml", "<svg></svg>".getBytes()))
                        .param("operatorId", "john")
                        .param("playerId", "1")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(41701));

        mockMvc.perform(multipart("/api/admin/photos/upload")
                        .file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not-real-image".getBytes()))
                        .param("operatorId", "john")
                        .param("playerId", "1")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(41701));

        mockMvc.perform(multipart("/api/admin/photos/upload")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .param("operatorId", "john")
                        .param("playerId", "1")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(41701));

        mockMvc.perform(multipart("/api/admin/photos/upload")
                        .file(new MockMultipartFile("file", "stage.png", "image/png", pngBytes()))
                        .param("operatorId", "john")
                        .param("playerId", "999")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(41704));
    }

    @Test
    void shouldKeepSingleCoverAndUseLogicalInactiveWithoutDeletingCollections() throws Exception {
        String first = uploadPhoto("a.png", "image/png", pngBytes(), 1, true);
        String second = uploadPhoto("b.webp", "image/webp", webpBytes(), 1, true);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM photo_assets WHERE player_id = 1 AND is_cover = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT is_cover FROM photo_assets WHERE asset_id = ?", Integer.class, first))
                .isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject("SELECT is_cover FROM photo_assets WHERE asset_id = ?", Integer.class, second))
                .isEqualTo(1);

        insertToken("RFZJ-TEST-0001-0001", 1, 100, second, "used");
        jdbcTemplate.update("INSERT INTO user_photo_collection (user_id, asset_id, token_id) VALUES ('user_1', ?, 'RFZJ-TEST-0001-0001')", second);
        mockMvc.perform(put("/api/admin/photos/{assetId}/status", second)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"john\",\"status\":\"inactive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("inactive"))
                .andExpect(jsonPath("$.data.isCover").value(false));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_photo_collection WHERE asset_id = ?", Integer.class, second))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'photo_status'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void shouldReplaceFileWithoutChangingAssetIdAndRejectUnauthorizedAdminAccess() throws Exception {
        String assetId = uploadPhoto("a.png", "image/png", pngBytes(), 1, false);

        mockMvc.perform(post("/api/admin/photos/{assetId}/cover", assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"john\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(multipart("/api/admin/photos/{assetId}/replace", assetId)
                        .file(new MockMultipartFile("file", "new.jpg", "image/jpeg", jpegBytes()))
                        .param("operatorId", "john")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value(assetId))
                .andExpect(jsonPath("$.data.fileName").value("new.jpg"))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"));
    }

    private String uploadPhoto(String name, String contentType, byte[] bytes, int playerId, boolean isCover) throws Exception {
        String body = mockMvc.perform(multipart("/api/admin/photos/upload")
                        .file(new MockMultipartFile("file", name, contentType, bytes))
                        .param("operatorId", "john")
                        .param("playerId", String.valueOf(playerId))
                        .param("isCover", String.valueOf(isCover))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("assetId").asText();
    }

    private byte[] pngBytes() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    }

    private byte[] webpBytes() {
        return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    }
}
