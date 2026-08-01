package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C20-4A 场次水位线测试。
 *
 * <p>核心防线：抖音中控台只提供本场累计数，每场开播从 0 重新计数。
 * 8/3 收官水位线 50000，8/9 开播录入 3000，若直接相减会倒扣四万七。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveWatermarkC20Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
        jdbcTemplate.update("DELETE FROM live_metric_watermark");
        prepareOnePlayerRound();
        setCollectPlayer();
    }

    @Test
    @DisplayName("首次录入：水位线为0，增量等于当前总数")
    void firstEntryShouldTreatCurrentTotalAsDelta() throws Exception {
        submitEntry("like_delta", 500, "k1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delta").value(500))
                .andExpect(jsonPath("$.data.previousTotal").value(0));

        assertThat(watermarkOf("like_delta")).isEqualTo(500L);
        assertThat(popularityOf(101)).isEqualTo(500L);
    }

    @Test
    @DisplayName("同场连续录入：只入账增量，不重复累加总数")
    void consecutiveEntriesShouldAccrueOnlyDelta() throws Exception {
        submitEntry("like_delta", 500, "k1").andExpect(status().isOk());
        submitEntry("like_delta", 800, "k2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delta").value(300));

        // 关键：人气总额是 800 而不是 500+800=1300。
        assertThat(popularityOf(101)).isEqualTo(800L);
        assertThat(watermarkOf("like_delta")).isEqualTo(800L);
    }

    @Test
    @DisplayName("总数持平：增量为0，不写流水")
    void sameTotalShouldNotAccrue() throws Exception {
        submitEntry("like_delta", 500, "k1").andExpect(status().isOk());
        submitEntry("like_delta", 500, "k2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delta").value(0));

        assertThat(popularityOf(101)).isEqualTo(500L);
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popularity_ledger WHERE source = 'like'", Long.class);
        assertThat(rows).isEqualTo(1L);
    }

    @Test
    @DisplayName("倒扣防线：8/9录入3000遇水位线50000，返回409且不写入任何数据")
    void lowerTotalShouldRequireCalibrationAndWriteNothing() throws Exception {
        submitEntry("like_delta", 50000, "k1").andExpect(status().isOk());
        long before = popularityOf(101);

        submitEntry("like_delta", 3000, "k2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40910))
                .andExpect(jsonPath("$.data.needsCalibration").value(true))
                .andExpect(jsonPath("$.data.currentTotal").value(3000))
                .andExpect(jsonPath("$.data.lastTotal").value(50000));

        // 绝不产生负增量：人气与水位线都必须原样不动。
        assertThat(popularityOf(101)).isEqualTo(before);
        assertThat(watermarkOf("like_delta")).isEqualTo(50000L);
    }

    @Test
    @DisplayName("校准后重新录入：3000全额入账，历史人气保留不清零")
    void calibrateThenEntryShouldAccrueFullTotalAndKeepHistory() throws Exception {
        submitEntry("like_delta", 50000, "k1").andExpect(status().isOk());
        long historical = popularityOf(101);

        calibrate().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousTotals.like_delta").value(50000));

        assertThat(watermarkOf("like_delta")).isZero();
        // 校准只重置读数基准，人气值不受影响。这是运营最容易误解的一点。
        assertThat(popularityOf(101)).isEqualTo(historical);

        submitEntry("like_delta", 3000, "k2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delta").value(3000));
        assertThat(popularityOf(101)).isEqualTo(historical + 3000);
    }

    @Test
    @DisplayName("校准必须三条来源同时归零，漏一条会在下次录入时倒扣")
    void calibrateShouldResetAllMetrics() throws Exception {
        submitEntry("like_delta", 9000, "k1").andExpect(status().isOk());
        submitEntry("comment_delta", 300, "k2").andExpect(status().isOk());

        calibrate().andExpect(status().isOk());

        assertThat(watermarkOf("like_delta")).isZero();
        assertThat(watermarkOf("comment_delta")).isZero();
        assertThat(watermarkOf("gift")).isZero();
    }

    @Test
    @DisplayName("校准留痕：日志记录归零前原值并标明未影响人气")
    void calibrateShouldLogPreviousTotals() throws Exception {
        submitEntry("like_delta", 80000, "k1").andExpect(status().isOk());
        calibrate().andExpect(status().isOk());

        String detail = jdbcTemplate.queryForObject(
                "SELECT detail FROM operations_log WHERE action_type = 'live_watermark_calibrate'",
                String.class);
        assertThat(detail).contains("\"like_delta\":80000").contains("\"popularityAffected\":false");
    }

    @Test
    @DisplayName("误点校准可撤销：水位线恢复80000，避免下次录入多加八万五")
    void revokeCalibrationShouldRestoreWatermark() throws Exception {
        submitEntry("like_delta", 80000, "k1").andExpect(status().isOk());
        calibrate().andExpect(status().isOk());
        assertThat(watermarkOf("like_delta")).isZero();

        mockMvc.perform(post("/api/admin/live/watermarks/revoke-calibration")
                        .contentType("application/json")
                        .content("{\"operatorId\":\"director\",\"reason\":\"误点校准\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredTotals.like_delta").value(80000));

        assertThat(watermarkOf("like_delta")).isEqualTo(80000L);

        // 撤销后录入 85000 应只加 5000，而非误点未撤销时的 85000。
        submitEntry("like_delta", 85000, "k2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delta").value(5000));
    }

    @Test
    @DisplayName("校准后已录入则拒绝自动撤销，须人工冲销")
    void revokeShouldRejectWhenEntryAlreadyHappened() throws Exception {
        submitEntry("like_delta", 80000, "k1").andExpect(status().isOk());
        calibrate().andExpect(status().isOk());
        submitEntry("like_delta", 1000, "k2").andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/live/watermarks/revoke-calibration")
                        .contentType("application/json")
                        .content("{\"operatorId\":\"director\",\"reason\":\"想撤销\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("人工冲销")));

        assertThat(watermarkOf("like_delta")).isEqualTo(1000L);
    }

    @Test
    @DisplayName("流水metadata写入场次标识，可按场次还原入账总额")
    void ledgerShouldCarrySessionSeqForSegmentation() throws Exception {
        submitEntry("like_delta", 5000, "k1").andExpect(status().isOk());
        String firstSeq = sessionSeqOf("like_delta");
        calibrate().andExpect(status().isOk());
        submitEntry("like_delta", 2000, "k2").andExpect(status().isOk());
        String secondSeq = sessionSeqOf("like_delta");

        assertThat(firstSeq).isNotEqualTo(secondSeq);
        Long firstSession = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popularity_ledger WHERE metadata LIKE ?",
                Long.class, "%" + firstSeq + "%");
        assertThat(firstSession).isEqualTo(1L);
    }

    @Test
    @DisplayName("预演接口不写入任何数据")
    void previewShouldNotMutateState() throws Exception {
        submitEntry("like_delta", 500, "k1").andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/live/metric-entry/preview")
                        .param("metricType", "like_delta")
                        .param("currentTotal", "900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delta").value(400))
                .andExpect(jsonPath("$.data.needsCalibration").value(false));

        assertThat(watermarkOf("like_delta")).isEqualTo(500L);
        assertThat(popularityOf(101)).isEqualTo(500L);
    }

    @Test
    @DisplayName("礼物开关默认关闭，按总数录入被明确拒绝而非静默降级")
    void giftEntryShouldBeRejectedWhenSwitchDisabled() throws Exception {
        submitEntry("gift", 100, "k1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("gift-watermark-enabled")));
    }

    @Test
    @DisplayName("负数总数与未知来源均被拒绝")
    void invalidInputShouldBeRejected() throws Exception {
        submitEntry("like_delta", -1, "k1").andExpect(status().isBadRequest());
        submitEntry("unknown_metric", 100, "k2").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("水位线查询返回三条来源现状")
    void listWatermarksShouldReturnAllMetrics() throws Exception {
        mockMvc.perform(get("/api/admin/live/watermarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    private org.springframework.test.web.servlet.ResultActions submitEntry(
            String metricType, long currentTotal, String key) throws Exception {
        return mockMvc.perform(post("/api/admin/live/metric-entry")
                .contentType("application/json")
                .content("{\"metricType\":\"" + metricType + "\",\"currentTotal\":" + currentTotal
                        + ",\"operatorId\":\"director\",\"idempotencyKey\":\"" + key
                        + "\",\"reason\":\"现场录入\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions calibrate() throws Exception {
        return mockMvc.perform(post("/api/admin/live/watermarks/calibrate")
                .contentType("application/json")
                .content("{\"operatorId\":\"director\",\"reason\":\"新一场直播开播\"}"));
    }

    private long watermarkOf(String metricType) {
        Long v = jdbcTemplate.queryForObject(
                "SELECT last_total FROM live_metric_watermark WHERE metric_type = ?",
                Long.class, metricType);
        return v == null ? 0L : v;
    }

    private String sessionSeqOf(String metricType) {
        return jdbcTemplate.queryForObject(
                "SELECT session_seq FROM live_metric_watermark WHERE metric_type = ?",
                String.class, metricType);
    }

    private long popularityOf(int playerId) {
        Long v = jdbcTemplate.queryForObject(
                "SELECT individual_popularity FROM player_round_stats WHERE player_id = ? AND round_id = 1",
                Long.class, playerId);
        return v == null ? 0L : v;
    }

    private void setCollectPlayer() {
        try {
            mockMvc.perform(post("/api/admin/collect-state")
                    .contentType("application/json")
                    .content("{\"mode\":\"player\",\"targetId\":101,\"roundId\":1,\"operatorId\":\"director\"}"))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException("测试准备失败", e);
        }
    }

    private void prepareOnePlayerRound() {
        insertRound(1, "active");
        insertTeam(10, "A组");
        insertPlayer(101, 3, "林夏");
        insertPlayerRound(101, 1, 10);
        insertTeamStats(10, 1, 0L);
    }
}
