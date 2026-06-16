package com.redface;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C9 API-3 卡密核销 Controller 测试，重点覆盖 40001~40005 错误码。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenRedeemControllerC9Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
    }

    @Test
    void redeemSuccessShouldReturnPageLevelDto() throws Exception {
        seedRedeemData("RFZJ-2345-6789-ABCD", "unused", true);
        String token = loginAndGetToken("code_redeem_success");

        mockMvc.perform(post("/api/tokens/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"token\":\"RFZJ-2345-6789-ABCD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.playerNumber").value(3))
                .andExpect(jsonPath("$.data.playerName").value("林夏"))
                .andExpect(jsonPath("$.data.teamName").value("A组"))
                .andExpect(jsonPath("$.data.points").value(19900L))
                .andExpect(jsonPath("$.data.photoAssetId").value("photo_p3_0001"))
                .andExpect(jsonPath("$.data.photoPreviewUrl").value("https://example.com/preview.jpg"))
                .andExpect(jsonPath("$.data.collected").value(true));
    }

    @Test
    void invalidFormatShouldReturn40001() throws Exception {
        String token = loginAndGetToken("code_invalid_format");

        mockMvc.perform(post("/api/tokens/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"token\":\"bad-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.businessCode").value("invalid_format"));
    }

    @Test
    void notFoundShouldReturn40002() throws Exception {
        seedRedeemData("RFZJ-2345-6789-ABCD", "unused", true);
        String token = loginAndGetToken("code_not_found");

        mockMvc.perform(post("/api/tokens/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"token\":\"RFZJ-2345-6789-WXYZ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.data.businessCode").value("not_found"));
    }

    @Test
    void alreadyUsedShouldReturn40003() throws Exception {
        seedRedeemData("RFZJ-2345-6789-ABCD", "used", true);
        String token = loginAndGetToken("code_already_used");

        mockMvc.perform(post("/api/tokens/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"token\":\"RFZJ-2345-6789-ABCD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40003))
                .andExpect(jsonPath("$.data.businessCode").value("already_used"));
    }

    @Test
    void lockedShouldReturn40004WithRemainingSeconds() throws Exception {
        seedRedeemData("RFZJ-2345-6789-ABCD", "unused", true);
        String token = loginAndGetToken("code_locked");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/tokens/redeem")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("{\"token\":\"RFZJ-2345-6789-WXY" + i + "\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/tokens/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"token\":\"RFZJ-2345-6789-ABCD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40004))
                .andExpect(jsonPath("$.data.businessCode").value("locked"))
                .andExpect(jsonPath("$.data.remainingSeconds").isNumber());
    }

    @Test
    void noRoundShouldReturn40005AndKeepTokenUnused() throws Exception {
        seedRedeemData("RFZJ-2345-6789-ABCD", "unused", false);
        String token = loginAndGetToken("code_no_round");

        mockMvc.perform(post("/api/tokens/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"token\":\"RFZJ-2345-6789-ABCD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40005))
                .andExpect(jsonPath("$.data.businessCode").value("round_not_available"));
    }

    @Test
    void redeemWithoutLoginShouldReturnUnauthorized() throws Exception {
        seedRedeemData("RFZJ-2345-6789-ABCD", "unused", true);

        mockMvc.perform(post("/api/tokens/redeem")
                        .contentType("application/json")
                        .content("{\"token\":\"RFZJ-2345-6789-ABCD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private void seedRedeemData(String tokenId, String status, boolean withRound) {
        if (withRound) {
            insertRound(1, "active");
        }
        insertTeam(10, "A组");
        insertPlayer(1, 3, "林夏");
        if (withRound) {
            insertPlayerRound(1, 1, 10);
        }
        insertPhotoAsset("photo_p3_0001", 1, "https://example.com/preview.jpg");
        insertToken(tokenId, 1, 19900L, "photo_p3_0001", status);
    }
}
