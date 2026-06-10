package com.redface;

import static org.assertj.core.api.Assertions.assertThat;

import com.redface.dto.ScoreResult;
import com.redface.service.PopularityService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 任务卡 C4 验证：computeScore 的衰减、系数和边界条件。
 */
@SpringBootTest
@ActiveProfiles("test")
class PopularityServiceC4Test {

    private static final int PLAYER_ID = 1;

    private final PopularityService popularityService;
    private final JdbcTemplate jdbcTemplate;

    PopularityServiceC4Test(@Autowired PopularityService popularityService,
                            @Autowired JdbcTemplate jdbcTemplate) {
        this.popularityService = popularityService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 每个测试用例执行前清空业务数据并插入基础选手和轮次。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM popularity_ledger");
        jdbcTemplate.update("DELETE FROM collect_state");
        jdbcTemplate.update("DELETE FROM operations_log");
        jdbcTemplate.update("DELETE FROM player_round_stats");
        jdbcTemplate.update("DELETE FROM team_round_stats");
        jdbcTemplate.update("DELETE FROM pool_round_stats");
        jdbcTemplate.update("DELETE FROM player_round");
        jdbcTemplate.update("DELETE FROM teams");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM rounds");

        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, status)
                VALUES (?, ?, ?, ?)
                """, PLAYER_ID, "测试选手", PLAYER_ID, "active");
        for (int roundId = 1; roundId <= 5; roundId++) {
            jdbcTemplate.update("""
                    INSERT INTO rounds (round_id, name, start_time, end_time, status)
                    VALUES (?, ?, ?, ?, ?)
                    """, roundId, "测试轮次" + roundId,
                    LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), "active");
        }
    }

    /**
     * 验证上轮 10 万、本轮 20 万时，超过阈值部分按 0.1 衰减，得到 15.5 万。
     */
    @Test
    void previous100000AndCurrent200000ShouldDecayTo155000() {
        insertPlayerStats(1, 100_000L, 100);
        insertPlayerStats(2, 200_000L, 100);

        ScoreResult result = popularityService.computeScore(PLAYER_ID, 2);

        assertThat(result.getPopularity()).isEqualTo(200_000L);
        assertThat(result.getCoefficient()).isEqualTo(100);
        assertThat(result.getScoreBeforeDecay()).isEqualTo(155_000L);
        assertThat(result.getScoreFinal()).isEqualTo(155_000L);
        assertThat(result.isDecayApplied()).isTrue();
    }

    /**
     * 验证上轮为 0 或首轮无上轮记录时不触发衰减。
     */
    @Test
    void previousZeroOrNoPreviousRecordShouldNotApplyDecay() {
        insertPlayerStats(1, 80_000L, 100);
        ScoreResult noPrevious = popularityService.computeScore(PLAYER_ID, 1);
        assertThat(noPrevious.getScoreBeforeDecay()).isEqualTo(80_000L);
        assertThat(noPrevious.getScoreFinal()).isEqualTo(80_000L);
        assertThat(noPrevious.isDecayApplied()).isFalse();

        jdbcTemplate.update("DELETE FROM player_round_stats");
        insertPlayerStats(1, 0L, 100);
        insertPlayerStats(2, 120_000L, 100);
        ScoreResult previousZero = popularityService.computeScore(PLAYER_ID, 2);
        assertThat(previousZero.getScoreBeforeDecay()).isEqualTo(120_000L);
        assertThat(previousZero.getScoreFinal()).isEqualTo(120_000L);
        assertThat(previousZero.isDecayApplied()).isFalse();
    }

    /**
     * 验证本轮恰好等于阈值 1.5 倍时不触发衰减，只有大于阈值才触发。
     */
    @Test
    void exactlyAtThresholdShouldNotApplyDecay() {
        insertPlayerStats(1, 100_000L, 100);
        insertPlayerStats(2, 150_000L, 100);

        ScoreResult result = popularityService.computeScore(PLAYER_ID, 2);

        assertThat(result.getScoreBeforeDecay()).isEqualTo(150_000L);
        assertThat(result.getScoreFinal()).isEqualTo(150_000L);
        assertThat(result.isDecayApplied()).isFalse();
    }

    /**
     * 验证 coefficient=110 时，最终积分等于衰减后值乘以 110 / 100。
     */
    @Test
    void coefficient110ShouldMultiplyDecayedScore() {
        insertPlayerStats(1, 100_000L, 100);
        insertPlayerStats(2, 200_000L, 110);

        ScoreResult result = popularityService.computeScore(PLAYER_ID, 2);

        assertThat(result.getCoefficient()).isEqualTo(110);
        assertThat(result.getScoreBeforeDecay()).isEqualTo(155_000L);
        assertThat(result.getScoreFinal()).isEqualTo(170_500L);
        assertThat(result.isDecayApplied()).isTrue();
    }

    /**
     * 验证理论负分场景下最终积分返回 0。
     */
    @Test
    void negativeScoreAfterCoefficientShouldReturnZero() {
        insertPlayerStats(1, 100_000L, 100);
        insertPlayerStats(2, -5_000L, 100);

        ScoreResult result = popularityService.computeScore(PLAYER_ID, 2);

        assertThat(result.getPopularity()).isEqualTo(-5_000L);
        assertThat(result.getScoreBeforeDecay()).isEqualTo(-5_000L);
        assertThat(result.getScoreFinal()).isZero();
        assertThat(result.isDecayApplied()).isFalse();
    }

    private void insertPlayerStats(int roundId, long individualPopularity, int coefficient) {
        jdbcTemplate.update("""
                INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity, coefficient)
                VALUES (?, ?, ?, 0, ?)
                """, PLAYER_ID, roundId, individualPopularity, coefficient);
    }
}
