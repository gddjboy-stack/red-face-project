package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.redface.mapper.PopularityLedgerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C20-4A R-1 回归断言：场次分段标识写入后，既有人气汇总链路结果不变。
 *
 * <p>Claude 裁定 V3.0 第 1.2 条要求。此前 C20-4A 的测试只验证新功能正确，
 * 未验证既有汇总逻辑是否被污染——若 metadata / session_tag 的写入影响了任一
 * 聚合查询，仅靠新功能测试无法发现。这一层是本项目「人气值只增不改、账实相符」
 * 铁律的守门测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerSessionTagRegressionC20Test extends C9MockMvcSupport {

    @Autowired
    private PopularityLedgerMapper popularityLedgerMapper;

    @BeforeEach
    void setUp() {
        clearTables();
        jdbcTemplate.update("DELETE FROM live_metric_watermark");
        insertRound(1, "active");
        insertTeam(10, "A组");
        insertPlayer(101, 3, "林夏");
        insertPlayerRound(101, 1, 10);
        insertTeamStats(10, 1, 0L);
        setCollectPlayer();
    }

    @Test
    @DisplayName("回归：带场次标识的流水与不带标识的手动流水，汇总口径完全一致")
    void sessionTaggedLedgerShouldNotDistortSummation() throws Exception {
        // 手动调分：不带场次标识（session_tag 为 null 的对照组）
        manualAdjust(200, "对照组");

        long afterManual = popularityLedgerMapper.sumPopularityValue("player", 101, 1);
        assertThat(afterManual).isEqualTo(200L);

        // 直播录入：带场次标识
        mockMvc.perform(post("/api/admin/live/metric-entry")
                        .contentType("application/json")
                        .content("{\"metricType\":\"like_delta\",\"currentTotal\":500,"
                                + "\"operatorId\":\"director\",\"idempotencyKey\":\"reg1\","
                                + "\"reason\":\"现场录入\"}"))
                .andExpect(status().isOk());

        // 汇总必须等于两笔之和，标识的存在不得让任一笔被漏计或重计。
        long total = popularityLedgerMapper.sumPopularityValue("player", 101, 1);
        assertThat(total).isEqualTo(700L);

        // 流水汇总与 stats 表必须一致（账实相符）。
        Long stats = jdbcTemplate.queryForObject(
                "SELECT individual_popularity FROM player_round_stats WHERE player_id = 101 AND round_id = 1",
                Long.class);
        assertThat(stats).isEqualTo(total);
    }

    @Test
    @DisplayName("回归：仅直播来源填充场次标识，手动来源保持为空")
    void onlyLiveSourcesShouldCarrySessionTag() throws Exception {
        manualAdjust(50, "手动");
        mockMvc.perform(post("/api/admin/live/metric-entry")
                        .contentType("application/json")
                        .content("{\"metricType\":\"like_delta\",\"currentTotal\":300,"
                                + "\"operatorId\":\"director\",\"idempotencyKey\":\"reg2\","
                                + "\"reason\":\"现场录入\"}"))
                .andExpect(status().isOk());

        Long manualTagged = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popularity_ledger WHERE source = 'manual' "
                        + "AND metadata IS NOT NULL AND metadata LIKE '%sessionSeq%'",
                Long.class);
        assertThat(manualTagged).isZero();

        Long liveTagged = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popularity_ledger WHERE source = 'like' "
                        + "AND metadata LIKE '%sessionSeq%'",
                Long.class);
        assertThat(liveTagged).isEqualTo(1L);
    }

    @Test
    @DisplayName("回归：退款反查原核销轮次不受新字段影响")
    void refundRoundLookupShouldStayIntact() throws Exception {
        mockMvc.perform(post("/api/admin/live/metric-entry")
                        .contentType("application/json")
                        .content("{\"metricType\":\"like_delta\",\"currentTotal\":800,"
                                + "\"operatorId\":\"director\",\"idempotencyKey\":\"reg3\","
                                + "\"reason\":\"现场录入\"}"))
                .andExpect(status().isOk());

        String key = jdbcTemplate.queryForObject(
                "SELECT idempotency_key FROM popularity_ledger WHERE source = 'like'", String.class);
        assertThat(popularityLedgerMapper.findRoundIdByIdempotencyKey(key)).isEqualTo(1);
        assertThat(popularityLedgerMapper.countByIdempotencyKey(key)).isEqualTo(1L);
    }

    @Test
    @DisplayName("回归：跨场次校准后历史流水汇总不变，仅新增段落累加")
    void calibrationShouldNotRewriteHistoricalSummation() throws Exception {
        mockMvc.perform(post("/api/admin/live/metric-entry")
                        .contentType("application/json")
                        .content("{\"metricType\":\"like_delta\",\"currentTotal\":50000,"
                                + "\"operatorId\":\"director\",\"idempotencyKey\":\"reg4\","
                                + "\"reason\":\"8/3收官\"}"))
                .andExpect(status().isOk());
        long beforeCalibration = popularityLedgerMapper.sumPopularityValue("player", 101, 1);

        mockMvc.perform(post("/api/admin/live/watermarks/calibrate")
                        .contentType("application/json")
                        .content("{\"operatorId\":\"director\",\"reason\":\"8/9开播\"}"))
                .andExpect(status().isOk());

        // 校准只重置读数基准，绝不回写历史流水。
        assertThat(popularityLedgerMapper.sumPopularityValue("player", 101, 1))
                .isEqualTo(beforeCalibration);

        mockMvc.perform(post("/api/admin/live/metric-entry")
                        .contentType("application/json")
                        .content("{\"metricType\":\"like_delta\",\"currentTotal\":3000,"
                                + "\"operatorId\":\"director\",\"idempotencyKey\":\"reg5\","
                                + "\"reason\":\"8/9录入\"}"))
                .andExpect(status().isOk());

        assertThat(popularityLedgerMapper.sumPopularityValue("player", 101, 1))
                .isEqualTo(beforeCalibration + 3000);
    }

    private void manualAdjust(long rawValue, String reason) throws Exception {
        mockMvc.perform(post("/api/admin/popularity/manual-adjust")
                        .contentType("application/json")
                        .content("{\"targetType\":\"player\",\"targetId\":101,\"roundId\":1,"
                                + "\"rawValue\":" + rawValue + ",\"operatorId\":\"director\","
                                + "\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk());
    }

    private void setCollectPlayer() {
        try {
            mockMvc.perform(post("/api/admin/collect-state")
                    .contentType("application/json")
                    .content("{\"mode\":\"player\",\"targetId\":101,\"roundId\":1,"
                            + "\"operatorId\":\"director\"}"))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException("测试准备失败", e);
        }
    }
}
