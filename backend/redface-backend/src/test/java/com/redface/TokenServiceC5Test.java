package com.redface;

import static org.assertj.core.api.Assertions.assertThat;

import com.redface.dto.RedeemResult;
import com.redface.mapper.PopularityLedgerMapper;
import com.redface.mapper.StatsMapper;
import com.redface.mapper.TokenMapper;
import com.redface.mapper.UserPhotoCollectionMapper;
import com.redface.service.TokenService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 任务卡 C5 验证：TokenService.redeem 全流程、并发抢占、防爆破和无轮次保护。
 */
@SpringBootTest
@ActiveProfiles("test")
class TokenServiceC5Test {

    private static final int PLAYER_ID = 1;
    private static final int ROUND_ID = 1;
    private static final long TOKEN_POINTS = 19_900L;
    private static final String TOKEN_ID = "RFZJ-2345-6789-ABCD";
    private static final String PHOTO_ASSET_ID = "photo_asset_001";

    private final TokenService tokenService;
    private final TokenMapper tokenMapper;
    private final PopularityLedgerMapper ledgerMapper;
    private final StatsMapper statsMapper;
    private final UserPhotoCollectionMapper userPhotoCollectionMapper;
    private final JdbcTemplate jdbcTemplate;

    TokenServiceC5Test(@Autowired TokenService tokenService,
                       @Autowired TokenMapper tokenMapper,
                       @Autowired PopularityLedgerMapper ledgerMapper,
                       @Autowired StatsMapper statsMapper,
                       @Autowired UserPhotoCollectionMapper userPhotoCollectionMapper,
                       @Autowired JdbcTemplate jdbcTemplate) {
        this.tokenService = tokenService;
        this.tokenMapper = tokenMapper;
        this.ledgerMapper = ledgerMapper;
        this.statsMapper = statsMapper;
        this.userPhotoCollectionMapper = userPhotoCollectionMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 每个测试用例执行前清空业务数据并插入基础选手、轮次、写真和卡密。
     */
    @BeforeEach
    void setUp() {
        clearTables();
        insertPlayer();
        insertRound(ROUND_ID, "active");
        insertPhotoAsset();
        insertToken(TOKEN_ID, PHOTO_ASSET_ID);
    }

    /**
     * 验证正常核销：status 变 used、人气值正确入账、写真自动收藏。
     */
    @Test
    void redeemShouldMarkTokenUsedApplyPopularityAndCollectPhoto() {
        RedeemResult result = tokenService.redeem("  rfzj-2345-6789-abcd  ", "user_success", "h5");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCode()).isEqualTo("success");
        assertThat(result.getTokenId()).isEqualTo(TOKEN_ID);
        assertThat(result.getPoints()).isEqualTo(TOKEN_POINTS);
        assertThat(result.getPhotoAssetId()).isEqualTo(PHOTO_ASSET_ID);
        assertThat(tokenMapper.findById(TOKEN_ID).getStatus()).isEqualTo("used");
        assertThat(ledgerMapper.countByIdempotencyKey("token_" + TOKEN_ID)).isEqualTo(1L);
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(TOKEN_POINTS);
        assertThat(userPhotoCollectionMapper.countByUserAndToken("user_success", TOKEN_ID)).isEqualTo(1L);
    }

    /**
     * 验证重复核销：第二次返回 already_used，人气值不重复增加。
     */
    @Test
    void redeemSameTokenTwiceShouldReturnAlreadyUsedAndNotApplyPopularityAgain() {
        RedeemResult first = tokenService.redeem(TOKEN_ID, "user_first", "h5");
        RedeemResult second = tokenService.redeem(TOKEN_ID, "user_second", "h5");

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isFalse();
        assertThat(second.getCode()).isEqualTo("already_used");
        assertThat(ledgerMapper.countByIdempotencyKey("token_" + TOKEN_ID)).isEqualTo(1L);
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(TOKEN_POINTS);
    }

    /**
     * 验证并发核销：两个线程同时核销同一卡密，有且只有一个成功，ledger 只有一条 token 流水。
     */
    @Test
    void concurrentRedeemSameTokenShouldOnlyAllowOneSuccess() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<RedeemResult> taskA = concurrentRedeemTask(ready, start, "user_concurrent_a");
        Callable<RedeemResult> taskB = concurrentRedeemTask(ready, start, "user_concurrent_b");

        Future<RedeemResult> futureA = executor.submit(taskA);
        Future<RedeemResult> futureB = executor.submit(taskB);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<RedeemResult> results = List.of(futureA.get(10, TimeUnit.SECONDS), futureB.get(10, TimeUnit.SECONDS));
        executor.shutdownNow();

        long successCount = results.stream().filter(RedeemResult::isSuccess).count();
        assertThat(successCount).isEqualTo(1L);
        assertThat(ledgerMapper.countByIdempotencyKey("token_" + TOKEN_ID)).isEqualTo(1L);
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(TOKEN_POINTS);
        assertThat(tokenMapper.findById(TOKEN_ID).getStatus()).isEqualTo("used");
    }

    /**
     * 验证防爆破：连续输错 5 次后，第 6 次返回 locked 及剩余秒数。
     */
    @Test
    void fiveFailuresShouldLockUserOnSixthAttempt() {
        String userId = "user_lock_test";
        for (int i = 0; i < 5; i++) {
            RedeemResult result = tokenService.redeem("RFZJ-AAAA-BBBB-CCCC", userId, "h5");
            assertThat(result.getCode()).isEqualTo("not_found");
        }

        RedeemResult locked = tokenService.redeem("RFZJ-DDDD-EEEE-FFFF", userId, "h5");

        assertThat(locked.isSuccess()).isFalse();
        assertThat(locked.getCode()).isEqualTo("locked");
        assertThat(locked.getRemainingSeconds()).isGreaterThan(0L);
    }

    /**
     * 验证无 active/upcoming 轮次时核销失败，且卡密不会被消耗。
     */
    @Test
    void noAvailableRoundShouldReturnFailAndKeepTokenUnused() {
        jdbcTemplate.update("DELETE FROM rounds");
        RedeemResult result = tokenService.redeem(TOKEN_ID, "user_no_round", "h5");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("round_not_available");
        assertThat(tokenMapper.findById(TOKEN_ID).getStatus()).isEqualTo("unused");
        assertThat(ledgerMapper.countByIdempotencyKey("token_" + TOKEN_ID)).isZero();
    }

    private Callable<RedeemResult> concurrentRedeemTask(CountDownLatch ready, CountDownLatch start, String userId) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return tokenService.redeem(TOKEN_ID, userId, "h5");
        };
    }

    private void clearTables() {
        jdbcTemplate.update("DELETE FROM user_photo_collection");
        jdbcTemplate.update("DELETE FROM popularity_ledger");
        jdbcTemplate.update("DELETE FROM tokens");
        jdbcTemplate.update("DELETE FROM photo_assets");
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

    private void insertPlayer() {
        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, status)
                VALUES (?, ?, ?, ?)
                """, PLAYER_ID, "测试选手", PLAYER_ID, "active");
    }

    private void insertRound(int roundId, String status) {
        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?)
                """, roundId, "测试轮次" + roundId,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), status);
    }

    private void insertPhotoAsset() {
        jdbcTemplate.update("""
                INSERT INTO photo_assets (asset_id, player_id, preview_url, download_url)
                VALUES (?, ?, ?, ?)
                """, PHOTO_ASSET_ID, PLAYER_ID, "https://example.com/preview.jpg", "https://example.com/download.jpg");
    }

    private void insertToken(String tokenId, String photoAssetId) {
        jdbcTemplate.update("""
                INSERT INTO tokens (token_id, player_id, points, photo_asset_id, product_sku, status)
                VALUES (?, ?, ?, ?, ?, 'unused')
                """, tokenId, PLAYER_ID, TOKEN_POINTS, photoAssetId, "sku_test");
    }
}
