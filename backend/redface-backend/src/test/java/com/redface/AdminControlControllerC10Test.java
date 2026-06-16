package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C10 场控后台 MockMvc/H2 测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControlControllerC10Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
    }

    @Test
    void setCollectTargetShouldWriteOperationsLogAndExposeState() throws Exception {
        prepareOnePlayerRound();

        mockMvc.perform(post("/api/admin/collect-state")
                        .contentType("application/json")
                        .content("{\"mode\":\"player\",\"targetId\":101,\"roundId\":1,\"operatorId\":\"director\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.action").value("set_collect_target"));

        mockMvc.perform(get("/api/admin/collect-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("player"))
                .andExpect(jsonPath("$.data.targetId").value(101))
                .andExpect(jsonPath("$.data.roundId").value(1));

        Long logs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'set_collect_target'", Long.class);
        assertThat(logs).isEqualTo(1L);
    }

    @Test
    void simulateInjectShouldSupportLikeCommentAndGiftAndRefreshMonitorValues() throws Exception {
        prepareOnePlayerRound();
        mockMvc.perform(post("/api/admin/collect-state")
                        .contentType("application/json")
                        .content("{\"mode\":\"player\",\"targetId\":101,\"roundId\":1,\"operatorId\":\"director\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/live/simulate")
                        .contentType("application/json")
                        .content("{\"eventType\":\"like_delta\",\"value\":10,\"operatorId\":\"director\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.success").value(true))
                .andExpect(jsonPath("$.data.result.popularityValue").value(10));

        mockMvc.perform(post("/api/admin/live/simulate")
                        .contentType("application/json")
                        .content("{\"eventType\":\"comment_delta\",\"value\":2,\"operatorId\":\"director\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.popularityValue").value(200));

        mockMvc.perform(post("/api/admin/live/simulate")
                        .contentType("application/json")
                        .content("{\"eventType\":\"gift\",\"value\":3,\"targetId\":101,\"operatorId\":\"director\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.popularityValue").value(300));

        Long popularity = jdbcTemplate.queryForObject("SELECT individual_popularity FROM player_round_stats WHERE player_id = 101 AND round_id = 1", Long.class);
        Long logs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'simulate_inject'", Long.class);
        assertThat(popularity).isEqualTo(510L);
        assertThat(logs).isEqualTo(3L);

        mockMvc.perform(get("/api/admin/live/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetPopularity").value(510));
    }

    @Test
    void manualAdjustShouldUsePopularityServiceAndWriteAuditLog() throws Exception {
        prepareOnePlayerRound();

        mockMvc.perform(post("/api/admin/popularity/manual-adjust")
                        .contentType("application/json")
                        .content("{\"targetType\":\"player\",\"targetId\":101,\"roundId\":1,\"rawValue\":88,\"operatorId\":\"director\",\"reason\":\"彩排手动加分\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("manual_adjust"))
                .andExpect(jsonPath("$.data.result.popularityValue").value(88));

        Long popularity = jdbcTemplate.queryForObject("SELECT individual_popularity FROM player_round_stats WHERE player_id = 101 AND round_id = 1", Long.class);
        Long ledgerRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM popularity_ledger WHERE source = 'manual'", Long.class);
        Long logs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'manual_adjust'", Long.class);
        assertThat(popularity).isEqualTo(88L);
        assertThat(ledgerRows).isEqualTo(1L);
        assertThat(logs).isEqualTo(1L);
    }

    @Test
    void teamDistributionEqualShouldDistributeTeamPoolAndWriteAuditLog() throws Exception {
        insertRound(1, "active");
        insertTeam(10, "A组");
        insertPlayer(101, 1, "陈微");
        insertPlayer(102, 2, "林夏");
        insertPlayerRound(101, 1, 10);
        insertPlayerRound(102, 1, 10);
        insertTeamStats(10, 1, 101L);

        mockMvc.perform(post("/api/admin/team-distribution")
                        .contentType("application/json")
                        .content("{\"teamId\":10,\"roundId\":1,\"method\":\"equal\",\"operatorId\":\"director\",\"reason\":\"彩排团队均分\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("team_distribution"))
                .andExpect(jsonPath("$.data.result.totalValue").value(101))
                .andExpect(jsonPath("$.data.result.method").value("equal"));

        Long teamPool = jdbcTemplate.queryForObject("SELECT team_popularity FROM team_round_stats WHERE team_id = 10 AND round_id = 1", Long.class);
        Long distributed = jdbcTemplate.queryForObject("SELECT distributed_popularity FROM team_round_stats WHERE team_id = 10 AND round_id = 1", Long.class);
        Long player101 = jdbcTemplate.queryForObject("SELECT individual_popularity FROM player_round_stats WHERE player_id = 101 AND round_id = 1", Long.class);
        Long player102 = jdbcTemplate.queryForObject("SELECT individual_popularity FROM player_round_stats WHERE player_id = 102 AND round_id = 1", Long.class);
        Long logs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_log WHERE action_type = 'team_distribution'", Long.class);
        assertThat(teamPool).isZero();
        assertThat(distributed).isEqualTo(101L);
        assertThat(player101).isEqualTo(51L);
        assertThat(player102).isEqualTo(50L);
        assertThat(logs).isEqualTo(1L);
    }

    private void prepareOnePlayerRound() {
        insertRound(1, "active");
        insertTeam(10, "A组");
        insertPlayer(101, 3, "林夏");
        insertPlayerRound(101, 1, 10);
        insertTeamStats(10, 1, 0L);
    }
}
