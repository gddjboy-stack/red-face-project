package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C19 基础数据管理后台 MockMvc/H2 测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BasicDataControllerC19Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
    }

    @Test
    void createPlayerShouldRejectDuplicateNumberWithFriendlyErrorAndWriteLog() throws Exception {
        mockMvc.perform(post("/api/admin/players")
                        .contentType("application/json")
                        .content("{\"name\":\"林夏\",\"number\":3,\"operatorId\":\"john\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("林夏"))
                .andExpect(jsonPath("$.data.number").value(3))
                .andExpect(jsonPath("$.data.status").value("active"));

        mockMvc.perform(post("/api/admin/players")
                        .contentType("application/json")
                        .content("{\"name\":\"重复序号\",\"number\":3,\"operatorId\":\"john\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("序号3已被占用"));

        Long playerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM players", Long.class);
        Long logCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'basic_create_player'", Long.class);
        assertThat(playerCount).isEqualTo(1L);
        assertThat(logCount).isEqualTo(1L);
    }

    @Test
    void createTeamRoundAndSwitchActiveShouldCompleteOldActiveAndWriteAuditLog() throws Exception {
        insertRound(1, "active");

        String start = LocalDateTime.now().minusMinutes(10).toString();
        String end = LocalDateTime.now().plusHours(2).toString();
        mockMvc.perform(post("/api/admin/teams")
                        .contentType("application/json")
                        .content("{\"name\":\"A组\",\"operatorId\":\"john\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("A组"));

        mockMvc.perform(post("/api/admin/rounds")
                        .contentType("application/json")
                        .content("{\"name\":\"第2轮\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"operatorId\":\"john\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("upcoming"));

        Integer roundId = jdbcTemplate.queryForObject("SELECT round_id FROM rounds WHERE name = '第2轮'", Integer.class);
        mockMvc.perform(put("/api/admin/rounds/" + roundId + "/status")
                        .contentType("application/json")
                        .content("{\"status\":\"active\",\"operatorId\":\"john\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        String oldStatus = jdbcTemplate.queryForObject("SELECT status FROM rounds WHERE round_id = 1", String.class);
        Long autoCompleteLogs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'basic_auto_complete_active_rounds'", Long.class);
        Long statusLogs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'basic_update_round_status'", Long.class);
        assertThat(oldStatus).isEqualTo("completed");
        assertThat(autoCompleteLogs).isEqualTo(1L);
        assertThat(statusLogs).isEqualTo(1L);
    }

    @Test
    void upsertPlayerRoundShouldAssignTeamAndSpyWithoutTouchingStatsTables() throws Exception {
        insertRound(1, "active");
        insertTeam(10, "A组");
        insertPlayer(101, 3, "林夏");

        mockMvc.perform(post("/api/admin/player-round")
                        .contentType("application/json")
                        .content("{\"playerId\":101,\"roundId\":1,\"teamId\":10,\"isSpy\":true,\"playerStatus\":\"normal\",\"operatorId\":\"john\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.playerId").value(101))
                .andExpect(jsonPath("$.data.teamName").value("A组"))
                .andExpect(jsonPath("$.data.isSpy").value(true));

        mockMvc.perform(get("/api/admin/player-round?roundId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].playerName").value("林夏"))
                .andExpect(jsonPath("$.data[0].teamName").value("A组"))
                .andExpect(jsonPath("$.data[0].isSpy").value(true));

        Long statsRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_round_stats", Long.class);
        Long logs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'basic_upsert_player_round'", Long.class);
        assertThat(statsRows).isZero();
        assertThat(logs).isEqualTo(1L);
    }
}
