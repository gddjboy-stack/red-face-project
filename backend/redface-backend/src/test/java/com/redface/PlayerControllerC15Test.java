package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C15 用户端选手列表/详情只读接口测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlayerControllerC15Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
        seedPlayers();
    }

    @Test
    void listShouldReturnPlayerIdAndKeepNumberOrder() throws Exception {
        mockMvc.perform(get("/api/players?roundId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roundId").value(1))
                .andExpect(jsonPath("$.data.roundName").value("第1轮"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].playerId").value(1))
                .andExpect(jsonPath("$.data.items[0].number").value(3))
                .andExpect(jsonPath("$.data.items[0].name").value("林夏"))
                .andExpect(jsonPath("$.data.items[0].teamName").value("A组"))
                .andExpect(jsonPath("$.data.items[0].popularityValue").value(1200))
                .andExpect(jsonPath("$.data.items[0].photoPreviewUrl").value("https://example.com/p3_preview_2.jpg"))
                .andExpect(jsonPath("$.data.items[1].number").value(5));
    }

    @Test
    void detailShouldReturnPhotosButNeverRevealSpyFields() throws Exception {
        String body = mockMvc.perform(get("/api/players/1?roundId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.playerId").value(1))
                .andExpect(jsonPath("$.data.number").value(3))
                .andExpect(jsonPath("$.data.name").value("林夏"))
                .andExpect(jsonPath("$.data.teamName").value("A组"))
                .andExpect(jsonPath("$.data.popularityValue").value(1200))
                .andExpect(jsonPath("$.data.photos.length()").value(2))
                .andExpect(jsonPath("$.data.photos[0].assetId").value("photo_p3_0002"))
                .andExpect(jsonPath("$.data.supportHint").value("增加人气值请在直播间进行。"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("isSpy", "spyStatus", "hidden", "revealed", "exposed");
    }

    @Test
    void missingPlayerShouldReturnSafeBusinessError() throws Exception {
        mockMvc.perform(get("/api/players/999?roundId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40410))
                .andExpect(jsonPath("$.message").value("选手不存在或不可用"));
    }

    private void seedPlayers() {
        insertRound(1, "active");
        insertTeam(10, "A组");
        insertTeam(20, "B组");
        insertPlayer(1, 3, "林夏");
        insertPlayer(2, 5, "陈微");
        insertPlayerRound(1, 1, 10);
        insertPlayerRound(2, 1, 20);
        insertPlayerStats(1, 1, 1200, 9000);
        insertPlayerStats(2, 1, 9999, 0);
        insertPhotoAsset("photo_p3_0001", 1, "https://example.com/p3_preview_1.jpg");
        insertPhotoAsset("photo_p3_0002", 1, "https://example.com/p3_preview_2.jpg");
        insertPhotoAsset("photo_p5_0001", 2, "https://example.com/p5_preview_1.jpg");
    }
}
