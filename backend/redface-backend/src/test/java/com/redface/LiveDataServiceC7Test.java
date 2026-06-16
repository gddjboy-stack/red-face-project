package com.redface;

import static org.assertj.core.api.Assertions.assertThat;

import com.redface.dto.PopularityChangeResult;
import com.redface.dto.SimResult;
import com.redface.mapper.PopularityLedgerMapper;
import com.redface.mapper.StatsMapper;
import com.redface.service.CollectStateService;
import com.redface.service.LiveDataService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 任务卡 C7 验证：LiveDataService 直播接入与模拟器。
 */
@SpringBootTest
@ActiveProfiles("test")
class LiveDataServiceC7Test {

    private static final int PLAYER_ID = 1;
    private static final int TEAM_ID = 10;
    private static final int ROUND_ID = 1;

    private final LiveDataService liveDataService;
    private final CollectStateService collectStateService;
    private final PopularityLedgerMapper ledgerMapper;
    private final StatsMapper statsMapper;
    private final JdbcTemplate jdbcTemplate;

    LiveDataServiceC7Test(@Autowired LiveDataService liveDataService,
                          @Autowired CollectStateService collectStateService,
                          @Autowired PopularityLedgerMapper ledgerMapper,
                          @Autowired StatsMapper statsMapper,
                          @Autowired JdbcTemplate jdbcTemplate) {
        this.liveDataService = liveDataService;
        this.collectStateService = collectStateService;
        this.ledgerMapper = ledgerMapper;
        this.statsMapper = statsMapper;
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
                """, PLAYER_ID, "C7测试选手", PLAYER_ID, "active");
        jdbcTemplate.update("""
                INSERT INTO teams (team_id, name)
                VALUES (?, ?)
                """, TEAM_ID, "C7测试团队");
        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?)
                """, ROUND_ID, "C7测试轮次", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), "active");
    }

    /**
     * 验证模拟注入点赞时，若当前场控为 player 模式，则按场控目标进入 player 统计。
     */
    @Test
    void simulateLikeDeltaShouldGoToCurrentPlayerTarget() {
        collectStateService.setCollectTarget("player", PLAYER_ID, ROUND_ID, "operator_c7_player");

        SimResult result = liveDataService.simulateInject("like_delta", 77L, null, "operator_c7_player");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isDuplicated()).isFalse();
        assertThat(result.getEventType()).isEqualTo("like_delta");
        assertThat(result.getIdempotencyKey()).startsWith("sim_");
        assertThat(result.getTargetType()).isEqualTo("player");
        assertThat(result.getTargetId()).isEqualTo(PLAYER_ID);
        assertThat(result.getRoundId()).isEqualTo(ROUND_ID);
        assertThat(result.getPopularityValue()).isEqualTo(77L);
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(77L);
        assertThat(ledgerMapper.countByIdempotencyKey(result.getIdempotencyKey())).isEqualTo(1L);
    }

    /**
     * 验证模拟注入点赞时，若当前场控为 team 模式，则按场控目标进入 team 统计。
     */
    @Test
    void simulateLikeDeltaShouldGoToCurrentTeamTarget() {
        collectStateService.setCollectTarget("team", TEAM_ID, ROUND_ID, "operator_c7_team");

        SimResult result = liveDataService.simulateInject("like_delta", 88L, null, "operator_c7_team");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTargetType()).isEqualTo("team");
        assertThat(result.getTargetId()).isEqualTo(TEAM_ID);
        assertThat(result.getRoundId()).isEqualTo(ROUND_ID);
        assertThat(result.getPopularityValue()).isEqualTo(88L);
        assertThat(statsMapper.findTeamPopularity(TEAM_ID, ROUND_ID)).isEqualTo(88L);
        assertThat(ledgerMapper.countByIdempotencyKey(result.getIdempotencyKey())).isEqualTo(1L);
    }

    /**
     * 验证模拟注入礼物 1000 抖币时，显式归属到指定选手并增加 100000 人气值。
     */
    @Test
    void simulateGift1000DoubiShouldAdd100000PopularityToPlayer() {
        SimResult result = liveDataService.simulateInject("gift", 1000L, PLAYER_ID, "operator_c7_gift");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTargetType()).isEqualTo("player");
        assertThat(result.getTargetId()).isEqualTo(PLAYER_ID);
        assertThat(result.getRoundId()).isEqualTo(ROUND_ID);
        assertThat(result.getPopularityValue()).isEqualTo(100_000L);
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(100_000L);
        assertThat(ledgerMapper.countByIdempotencyKey(result.getIdempotencyKey())).isEqualTo(1L);
    }

    /**
     * 验证同一幂等键重复注入指标增量时，只生效一次。
     */
    @Test
    void sameIdempotencyKeyShouldOnlyApplyMetricDeltaOnce() {
        collectStateService.setCollectTarget("player", PLAYER_ID, ROUND_ID, "operator_c7_idem");

        PopularityChangeResult first = liveDataService.onMetricDelta("like_delta", 33L, System.currentTimeMillis(), "c7_like_same_idem");
        PopularityChangeResult second = liveDataService.onMetricDelta("like_delta", 33L, System.currentTimeMillis(), "c7_like_same_idem");

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getTargetType()).isEqualTo("player");
        assertThat(first.getTargetId()).isEqualTo(PLAYER_ID);
        assertThat(first.getRoundId()).isEqualTo(ROUND_ID);
        assertThat(second.isDuplicated()).isTrue();
        assertThat(ledgerMapper.countByIdempotencyKey("c7_like_same_idem")).isEqualTo(1L);
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(33L);
    }
}
