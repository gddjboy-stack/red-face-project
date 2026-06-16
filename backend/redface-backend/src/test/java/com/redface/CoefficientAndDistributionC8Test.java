package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redface.dto.CoefficientResult;
import com.redface.dto.DistributionResult;
import com.redface.mapper.CoefficientLedgerMapper;
import com.redface.mapper.StatsMapper;
import com.redface.mapper.TeamDistributionBatchMapper;
import com.redface.service.CoefficientService;
import com.redface.service.TeamDistributionService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 任务卡 C8 验证：CoefficientService 加成系数与 TeamDistributionService 团队池分配。
 */
@SpringBootTest
@ActiveProfiles("test")
class CoefficientAndDistributionC8Test {

    private static final int ROUND_ID = 1;
    private static final int TEAM_ID = 10;
    private static final int MEMBER_COUNT = 7;

    private final CoefficientService coefficientService;
    private final TeamDistributionService teamDistributionService;
    private final CoefficientLedgerMapper coefficientLedgerMapper;
    private final TeamDistributionBatchMapper batchMapper;
    private final StatsMapper statsMapper;
    private final JdbcTemplate jdbcTemplate;

    CoefficientAndDistributionC8Test(@Autowired CoefficientService coefficientService,
                                     @Autowired TeamDistributionService teamDistributionService,
                                     @Autowired CoefficientLedgerMapper coefficientLedgerMapper,
                                     @Autowired TeamDistributionBatchMapper batchMapper,
                                     @Autowired StatsMapper statsMapper,
                                     @Autowired JdbcTemplate jdbcTemplate) {
        this.coefficientService = coefficientService;
        this.teamDistributionService = teamDistributionService;
        this.coefficientLedgerMapper = coefficientLedgerMapper;
        this.batchMapper = batchMapper;
        this.statsMapper = statsMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 每个测试用例执行前清空业务数据并插入基础轮次、团队、7名成员及团队归属。
     */
    @BeforeEach
    void setUp() {
        clearTables();
        insertRound();
        insertTeam();
        insertMembers(MEMBER_COUNT);
    }

    /**
     * 验证同一 taskId 重复调用时，系数只变化一次，coefficient_ledger 只有一条幂等流水。
     */
    @Test
    void sameTaskIdRepeatedAdjustCoefficientShouldOnlyApplyOnce() {
        CoefficientResult first = coefficientService.adjustCoefficient(1, ROUND_ID, "task_same", "task", true, "operator_c8_coef");
        CoefficientResult second = coefficientService.adjustCoefficient(1, ROUND_ID, "task_same", "task", true, "operator_c8_coef");

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getDelta()).isEqualTo(10);
        assertThat(first.getCoefficient()).isEqualTo(110);
        assertThat(second.isDuplicated()).isTrue();
        assertThat(second.getCoefficient()).isEqualTo(110);
        assertThat(coefficientLedgerMapper.countByIdempotencyKey("coef_task_same_1")).isEqualTo(1L);
        assertThat(statsMapper.findPlayerCoefficient(1, ROUND_ID)).isEqualTo(110);
    }

    /**
     * 验证任务完成系数 +0.1，任务失败系数 -0.1。
     */
    @Test
    void completedAndFailedTaskShouldAdjustCoefficientByPlusAndMinusTen() {
        CoefficientResult completed = coefficientService.adjustCoefficient(1, ROUND_ID, "task_complete", "task", true, "operator_c8_coef");
        CoefficientResult failed = coefficientService.adjustCoefficient(2, ROUND_ID, "task_failed", "task", false, "operator_c8_coef");

        assertThat(completed.isSuccess()).isTrue();
        assertThat(completed.getDelta()).isEqualTo(10);
        assertThat(completed.getCoefficient()).isEqualTo(110);
        assertThat(failed.isSuccess()).isTrue();
        assertThat(failed.getDelta()).isEqualTo(-10);
        assertThat(failed.getCoefficient()).isEqualTo(90);
        assertThat(statsMapper.findPlayerCoefficient(1, ROUND_ID)).isEqualTo(110);
        assertThat(statsMapper.findPlayerCoefficient(2, ROUND_ID)).isEqualTo(90);
    }

    /**
     * 验证 pk_win 只接受 completed=true，completed=false 时抛出明确异常。
     */
    @Test
    void pkWinWithCompletedFalseShouldThrowException() {
        assertThatThrownBy(() -> coefficientService.adjustCoefficient(1, ROUND_ID, "pk_fail", "pk_win", false, "operator_c8_pk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pk_win仅支持completed=true");
        assertThat(coefficientLedgerMapper.countByIdempotencyKey("coef_pk_fail_1")).isZero();
        assertThat(statsMapper.findPlayerCoefficient(1, ROUND_ID)).isNull();
    }

    /**
     * 验证团队池 1000 平分给 7 人时，余数按 player_id 升序逐个 +1，总额不丢分。
     */
    @Test
    void equalDistribution1000ToSevenMembersShouldNotLoseRemainder() {
        insertTeamStats(1000L);

        DistributionResult result = teamDistributionService.distribute(TEAM_ID, ROUND_ID, "equal", null, "operator_c8_dist", "C8平分测试");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalValue()).isEqualTo(1000L);
        assertThat(result.getDistributedValue()).isEqualTo(1000L);
        assertThat(result.getMemberShares()).hasSize(7);
        for (int playerId = 1; playerId <= 6; playerId++) {
            assertThat(result.getMemberShares().get(playerId)).isEqualTo(143L);
            assertThat(statsMapper.findPlayerIndividualPopularity(playerId, ROUND_ID)).isEqualTo(143L);
        }
        assertThat(result.getMemberShares().get(7)).isEqualTo(142L);
        assertThat(statsMapper.findPlayerIndividualPopularity(7, ROUND_ID)).isEqualTo(142L);
        assertThat(result.getMemberShares().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(1000L);
    }

    /**
     * 验证团队分配后每人都有带 batch_id 的流水，团队池扣减且已分配值增加。
     */
    @Test
    void distributionShouldWriteLedgerWithBatchIdAndUpdateTeamStats() {
        insertTeamStats(1000L);

        DistributionResult result = teamDistributionService.distribute(TEAM_ID, ROUND_ID, "equal", null, "operator_c8_dist", "C8批次流水测试");

        assertThat(batchMapper.countByBatchId(result.getBatchId())).isEqualTo(1L);
        assertThat(countDistributionLedgerRows(result.getBatchId())).isEqualTo(7L);
        assertThat(sumDistributionLedger(result.getBatchId())).isEqualTo(1000L);
        assertThat(statsMapper.findTeamPopularity(TEAM_ID, ROUND_ID)).isEqualTo(0L);
        assertThat(statsMapper.findTeamDistributedPopularity(TEAM_ID, ROUND_ID)).isEqualTo(1000L);
    }

    /**
     * 验证团队池余额小于等于 0 时抛出明确异常，且不会创建空批次。
     */
    @Test
    void noTeamPopularityShouldThrowAndNotCreateBatch() {
        insertTeamStats(0L);

        assertThatThrownBy(() -> teamDistributionService.distribute(TEAM_ID, ROUND_ID, "equal", null, "operator_c8_dist", "空池测试"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("团队池无可分配人气");
        assertThat(countDistributionBatches()).isZero();
    }

    /**
     * 验证团队无成员时抛出明确异常，避免除零且不会创建批次。
     */
    @Test
    void noTeamMembersShouldThrowAndNotCreateBatch() {
        jdbcTemplate.update("DELETE FROM player_round");
        insertTeamStats(1000L);

        assertThatThrownBy(() -> teamDistributionService.distribute(TEAM_ID, ROUND_ID, "equal", null, "operator_c8_dist", "无成员测试"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("团队无成员");
        assertThat(countDistributionBatches()).isZero();
    }

    /**
     * 验证 custom 模式按 player_id 升序补余数，与 equal 规则保持一致。
     */
    @Test
    void customDistributionShouldFillRemainderByPlayerIdAscending() {
        insertTeamStats(100L);
        Map<Integer, Integer> weights = Map.of(1, 1, 2, 1, 3, 1, 4, 1, 5, 1, 6, 1, 7, 1);

        DistributionResult result = teamDistributionService.distribute(TEAM_ID, ROUND_ID, "custom", weights, "operator_c8_custom", "C8自定义分配测试");

        assertThat(result.getDistributedValue()).isEqualTo(100L);
        assertThat(result.getMemberShares().get(1)).isEqualTo(15L);
        assertThat(result.getMemberShares().get(2)).isEqualTo(15L);
        assertThat(result.getMemberShares().get(3)).isEqualTo(14L);
        assertThat(result.getMemberShares().get(7)).isEqualTo(14L);
        assertThat(sumDistributionLedger(result.getBatchId())).isEqualTo(100L);
    }

    private void clearTables() {
        jdbcTemplate.update("DELETE FROM popularity_ledger");
        jdbcTemplate.update("DELETE FROM coefficient_ledger");
        jdbcTemplate.update("DELETE FROM team_distribution_batches");
        jdbcTemplate.update("DELETE FROM collect_state");
        jdbcTemplate.update("DELETE FROM operations_log");
        jdbcTemplate.update("DELETE FROM player_round_stats");
        jdbcTemplate.update("DELETE FROM team_round_stats");
        jdbcTemplate.update("DELETE FROM pool_round_stats");
        jdbcTemplate.update("DELETE FROM player_round");
        jdbcTemplate.update("DELETE FROM teams");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM rounds");
    }

    private void insertRound() {
        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?)
                """, ROUND_ID, "C8测试轮次", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), "active");
    }

    private void insertTeam() {
        jdbcTemplate.update("""
                INSERT INTO teams (team_id, name)
                VALUES (?, ?)
                """, TEAM_ID, "C8测试团队");
    }

    private void insertMembers(int count) {
        for (int playerId = 1; playerId <= count; playerId++) {
            jdbcTemplate.update("""
                    INSERT INTO players (player_id, name, number, status)
                    VALUES (?, ?, ?, ?)
                    """, playerId, "C8测试选手" + playerId, playerId, "active");
            jdbcTemplate.update("""
                    INSERT INTO player_round (player_id, round_id, team_id, is_spy, player_status)
                    VALUES (?, ?, ?, ?, ?)
                    """, playerId, ROUND_ID, TEAM_ID, 0, "normal");
        }
    }

    private void insertTeamStats(long teamPopularity) {
        jdbcTemplate.update("""
                INSERT INTO team_round_stats (team_id, round_id, team_popularity, distributed_popularity)
                VALUES (?, ?, ?, ?)
                """, TEAM_ID, ROUND_ID, teamPopularity, 0L);
    }

    private long countDistributionLedgerRows(long batchId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popularity_ledger WHERE distribution_batch_id = ?",
                Long.class,
                batchId
        );
    }

    private long sumDistributionLedger(long batchId) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(popularity_value), 0) FROM popularity_ledger WHERE distribution_batch_id = ?",
                Long.class,
                batchId
        );
    }

    private long countDistributionBatches() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_distribution_batches", Long.class);
    }
}
