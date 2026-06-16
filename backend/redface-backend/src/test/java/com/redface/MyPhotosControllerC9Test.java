package com.redface;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C9 API-4 我的写真 Controller 测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyPhotosControllerC9Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
        insertPlayer(1, 3, "林夏");
        insertPlayer(2, 5, "陈微");
        insertPhotoAsset("photo_p3_0001", 1, "https://example.com/p3_preview.jpg");
        insertPhotoAsset("photo_p5_0001", 2, "https://example.com/p5_preview.jpg");
        insertToken("RFZJ-2345-6789-ABCD", 1, 19900L, "photo_p3_0001", "used");
        insertToken("RFZJ-2345-6789-EFGH", 2, 19900L, "photo_p5_0001", "used");
    }

    @Test
    void noPhotosShouldReturnEmptyList() throws Exception {
        String token = loginAndGetToken("code_empty_photos");

        mockMvc.perform(get("/api/me/photos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void photosShouldReturnOnlyCurrentUserCollections() throws Exception {
        String userA = loginAndGetUserId("code_photo_user_a");
        String tokenA = loginAndGetToken("code_photo_user_a");
        String userB = loginAndGetUserId("code_photo_user_b");
        jdbcTemplate.update("""
                INSERT INTO user_photo_collection (user_id, asset_id, token_id, created_at)
                VALUES (?, ?, ?, ?)
                """, userA, "photo_p3_0001", "RFZJ-2345-6789-ABCD", LocalDateTime.now());
        jdbcTemplate.update("""
                INSERT INTO user_photo_collection (user_id, asset_id, token_id, created_at)
                VALUES (?, ?, ?, ?)
                """, userB, "photo_p5_0001", "RFZJ-2345-6789-EFGH", LocalDateTime.now());

        mockMvc.perform(get("/api/me/photos").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].assetId").value("photo_p3_0001"))
                .andExpect(jsonPath("$.data.items[0].previewUrl").value("https://example.com/p3_preview.jpg"))
                .andExpect(jsonPath("$.data.items[0].playerName").value("林夏"))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists());
    }

    @Test
    void photosWithoutLoginShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/photos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
