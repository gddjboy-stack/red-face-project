package com.redface;

import static org.assertj.core.api.Assertions.assertThat;

import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.PopularityLedgerMapper;
import com.redface.mapper.StatsMapper;
import com.redface.service.CollectStateService;
import com.redface.service.PopularityService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 任务卡 C3 验证：like/comment 场控归属、team/pool 统计累加、manual 负值与操作日志。
 */
@SpringBootTest
@ActiveProfiles("test")
class PopularityServiceC3Test {

    private static final int PLAYER_ID = 1;
    private static final int TEAM_ID = 10;
    private static final int ROUND_ID = 1;

    private final PopularityService popularityService;
    private final CollectStateService collectStateService;
    private final PopularityLedgerMapper ledgerMapper;
    private final StatsMapper statsMapper;
    private final OperationsLogMapper operationsLogMapper;
    private final JdbcTemplate jdbcTemplate;

    PopularityServiceC3Test(@Autowired PopularityService popularityService,
                            @Autowired CollectStateService collectStateService,
                            @Autowired PopularityLedgerMapper ledgerMapper,
                            @Autowired StatsMapper statsMapper,
                            @Autowired OperationsLogMapper operationsLogMapper,
                            @Autowired JdbcTemplate jdbcTemplate) {
        this.popularityService = popularityService;
        this.collectStateService = collectStateService;
        this.ledgerMapper = ledgerMapper;
        this.statsMapper = statsMapper;
        this.operationsLogMapper = operationsLogMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 每个测试用例执行前清空业务数据并插入基础选手、团队和轮次。
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
        jdbcTemplate.update("""
                INSERT INTO teams (team_id, name)
                VALUES (?, ?)
                """, TEAM_ID, "A组");
        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?)
                """, ROUND_ID, "测试轮次", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), "active");
    }

    /**
     * 验证场控设为 team 模式时，like 增量正确进入 team_round_stats。
     */
    @Test
    void likeDeltaShouldGoToTeamStatsWhenCollectModeIsTeam() {
        collectStateService.setCollectTarget("team", TEAM_ID, ROUND_ID, "operator_a");

        PopularityChangeResult result = popularityService.applyChange(buildMetricRequest("like", 77L, "like_delta_team"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTargetType()).isEqualTo("team");
        assertThat(result.getTargetId()).isEqualTo(TEAM_ID);
        assertThat(result.getPopularityValue()).isEqualTo(77L);
        assertThat(statsMapper.findTeamPopularity(TEAM_ID, ROUND_ID)).isEqualTo(77L);
        assertThat(ledgerMapper.sumPopularityValue("team", TEAM_ID, ROUND_ID)).isEqualTo(77L);
    }

    /**
     * 验证场控设为 pool 模式时，comment 增量正确进入 pool_round_stats。
     */
    @Test
    void commentDeltaShouldGoToPoolStatsWhenCollectModeIsPool() {
        collectStateService.setCollectTarget("pool", null, ROUND_ID, "operator_b");

        PopularityChangeResult result = popularityService.applyChange(buildMetricRequest("comment", 3L, "comment_delta_pool"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTargetType()).isEqualTo("pool");
        assertThat(result.getTargetId()).isNull();
        assertThat(result.getPopularityValue()).isEqualTo(300L);
        assertThat(statsMapper.findPoolPopularity(ROUND_ID)).isEqualTo(300L);
        assertThat(ledgerMapper.sumPopularityValue("pool", 0, ROUND_ID)).isEqualTo(0L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(popularity_value), 0) FROM popularity_ledger WHERE target_type = 'pool' AND target_id IS NULL AND round_id = ?",
                Long.class,
                ROUND_ID
        )).isEqualTo(300L);
    }

    /**
     * 验证 manual 负值能正常走通，并正确写入负值流水和统计表。
     */
    @Test
    void manualNegativeValueShouldPassValidationAndWriteNegativeLedger() {
        PopularityChangeRequest request = new PopularityChangeRequest();
        request.setTargetType("player");
        request.setTargetId(PLAYER_ID);
        request.setSource("manual");
        request.setRawValue(-5_000L);
        request.setRoundId(ROUND_ID);
        request.setIdempotencyKey("manual_negative_001");
        request.setOperatorId("operator_c");
        request.setReason("C3 P0修复验证");
        request.setOccurredAt(LocalDateTime.now());

        PopularityChangeResult result = popularityService.applyChange(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPopularityValue()).isEqualTo(-5_000L);
        assertThat(ledgerMapper.countByIdempotencyKey("manual_negative_001")).isEqualTo(1L);
        assertThat(ledgerMapper.sumPopularityValue("player", PLAYER_ID, ROUND_ID)).isEqualTo(-5_000L);
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(-5_000L);
    }

    /**
     * 验证场控切换会写入 operations_log。
     */
    @Test
    void setCollectTargetShouldWriteOperationsLog() {
        collectStateService.setCollectTarget("team", TEAM_ID, ROUND_ID, "operator_d");
        collectStateService.setCollectTarget("pool", null, ROUND_ID, "operator_d");

        assertThat(operationsLogMapper.countByActionType("set_collect_target")).isEqualTo(2L);
    }

    private PopularityChangeRequest buildMetricRequest(String source, long rawValue, String idempotencyKey) {
        PopularityChangeRequest request = new PopularityChangeRequest();
        request.setSource(source);
        request.setRawValue(rawValue);
        request.setRoundId(ROUND_ID);
        request.setIdempotencyKey(idempotencyKey);
        request.setOccurredAt(LocalDateTime.now());
        return request;
    }
}
