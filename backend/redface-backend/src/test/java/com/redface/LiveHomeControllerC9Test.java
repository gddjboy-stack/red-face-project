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
 * C9 API-1 首页直播状态 Controller 测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveHomeControllerC9Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
    }

    @Test
    void activeRoundWithPlayerCollectStateShouldReturnLiveHomeDto() throws Exception {
        insertRound(1, "active");
        insertTeam(10, "A组");
        insertPlayer(1, 3, "林夏");
        insertPlayerRound(1, 1, 10);
        insertPlayerStats(1, 1, 125800L, 0L);
        insertTeamStats(10, 1, 402500L);
        jdbcTemplate.update("""
                INSERT INTO collect_state (id, mode, target_id, round_id, updated_by, updated_at)
                VALUES (1, 'player', 1, 1, 'operator_c9', ?)
                """, LocalDateTime.now());

        mockMvc.perform(get("/api/live/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.liveStatus").value("live"))
                .andExpect(jsonPath("$.data.roundId").value(1))
                .andExpect(jsonPath("$.data.roundName").value("第1轮"))
                .andExpect(jsonPath("$.data.currentMode").value("player"))
                .andExpect(jsonPath("$.data.targetDisplayName").value("3号 林夏 A组"))
                .andExpect(jsonPath("$.data.targetPopularity").value(125800L))
                .andExpect(jsonPath("$.data.teamDisplayName").value("A组"))
                .andExpect(jsonPath("$.data.teamPopularity").value(402500L))
                .andExpect(jsonPath("$.data.spyChannelOpen").value(false));
    }

    @Test
    void noActiveRoundShouldReturnIdleAndNoneMode() throws Exception {
        insertRound(1, "upcoming");

        mockMvc.perform(get("/api/live/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.liveStatus").value("idle"))
                .andExpect(jsonPath("$.data.currentMode").value("none"))
                .andExpect(jsonPath("$.data.spyChannelOpen").value(false));
    }
}
