package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C16 API-3 核销会员有效期 additive 字段测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenRedeemControllerC16Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
    }

    @Test
    void redeemSuccessShouldReturnMembershipFieldsAndKeepC9Fields() throws Exception {
        seedRedeemData("RFZJ-2345-6789-ABCD", "unused", true);
        String token = loginAndGetToken("code_c16_redeem_success");

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
                .andExpect(jsonPath("$.data.collected").value(true))
                .andExpect(jsonPath("$.data.membershipAddedDays").value(7))
                .andExpect(jsonPath("$.data.memberActive").value(true))
                .andExpect(jsonPath("$.data.membershipUntil").exists());
    }

    @Test
    void alreadyUsedShouldNotGrantMembership() throws Exception {
        seedRedeemData("RFZJ-2345-6789-ABCD", "used", true);
        String userId = loginAndGetUserId("code_c16_already_used");
        String token = loginAndGetToken("code_c16_already_used");

        mockMvc.perform(post("/api/tokens/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"token\":\"RFZJ-2345-6789-ABCD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40003))
                .andExpect(jsonPath("$.data.businessCode").value("already_used"));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_membership WHERE user_id = ?",
                Integer.class,
                userId
        );
        assertThat(rows).isZero();
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
