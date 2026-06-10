package com.redface;

import static org.assertj.core.api.Assertions.assertThat;

import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.mapper.PopularityLedgerMapper;
import com.redface.mapper.StatsMapper;
import com.redface.service.PopularityService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 任务卡 C2 验证：PopularityService.convert 与 applyChange 的 player 直接归属部分。
 */
@SpringBootTest
@ActiveProfiles("test")
class PopularityServiceC2Test {

    private static final int PLAYER_ID = 1;
    private static final int ROUND_ID = 1;
    private static final long GIFT_DOUBI_VALUE = 1000L;
    private static final long EXPECTED_POPULARITY = 100_000L;

    private final PopularityService popularityService;
    private final PopularityLedgerMapper ledgerMapper;
    private final StatsMapper statsMapper;
    private final JdbcTemplate jdbcTemplate;

    PopularityServiceC2Test(@Autowired PopularityService popularityService,
                            @Autowired PopularityLedgerMapper ledgerMapper,
                            @Autowired StatsMapper statsMapper,
                            @Autowired JdbcTemplate jdbcTemplate) {
        this.popularityService = popularityService;
        this.ledgerMapper = ledgerMapper;
        this.statsMapper = statsMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 每个测试用例执行前清空业务数据并插入基础选手和轮次。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM popularity_ledger");
        jdbcTemplate.update("DELETE FROM player_round_stats");
        jdbcTemplate.update("DELETE FROM player_round");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM rounds");

        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, status)
                VALUES (?, ?, ?, ?)
                """, PLAYER_ID, "测试选手", PLAYER_ID, "active");
        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?)
                """, ROUND_ID, "测试轮次", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), "active");
    }

    /**
     * 验证礼物 1000 抖币换算为 100000 人气值，并正确写入 ledger 和 player_round_stats。
     */
    @Test
    void gift1000DoubiShouldAdd100000PopularityToLedgerAndPlayerRoundStats() {
        PopularityChangeResult result = popularityService.applyChange(buildGiftRequest("gift_msg_001"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isDuplicated()).isFalse();
        assertThat(result.getPopularityValue()).isEqualTo(EXPECTED_POPULARITY);
        assertThat(ledgerMapper.countByIdempotencyKey("gift_msg_001")).isEqualTo(1L);
        assertThat(ledgerMapper.sumPopularityValue("player", PLAYER_ID, ROUND_ID)).isEqualTo(EXPECTED_POPULARITY);
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(EXPECTED_POPULARITY);
    }

    /**
     * 验证相同幂等键调用两次时，第二次返回 duplicated，且人气值只增加一次。
     */
    @Test
    void sameIdempotencyKeyShouldOnlyApplyOnceAndSecondCallReturnsDuplicated() {
        PopularityChangeResult firstResult = popularityService.applyChange(buildGiftRequest("gift_msg_duplicate"));
        PopularityChangeResult secondResult = popularityService.applyChange(buildGiftRequest("gift_msg_duplicate"));

        assertThat(firstResult.isSuccess()).isTrue();
        assertThat(secondResult.isDuplicated()).isTrue();
        assertThat(ledgerMapper.countByIdempotencyKey("gift_msg_duplicate")).isEqualTo(1L);
        assertThat(ledgerMapper.sumPopularityValue("player", PLAYER_ID, ROUND_ID)).isEqualTo(EXPECTED_POPULARITY);
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(EXPECTED_POPULARITY);
    }

    private PopularityChangeRequest buildGiftRequest(String idempotencyKey) {
        PopularityChangeRequest request = new PopularityChangeRequest();
        request.setTargetType("player");
        request.setTargetId(PLAYER_ID);
        request.setSource("gift");
        request.setRawValue(GIFT_DOUBI_VALUE);
        request.setRoundId(ROUND_ID);
        request.setIdempotencyKey(idempotencyKey);
        request.setOccurredAt(LocalDateTime.now());
        return request;
    }
}
