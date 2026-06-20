package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.RefundResult;
import com.redface.dto.ScoreResult;
import com.redface.mapper.PopularityLedgerMapper;
import com.redface.mapper.StatsMapper;
import com.redface.mapper.TokenMapper;
import com.redface.service.PopularityService;
import com.redface.service.RefundException;
import com.redface.service.RefundService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 任务卡 C14 验证：RefundService 退款回滚全流程。
 *
 * <p>覆盖 Claude 裁定的验证物清单：正常退款、防重复退款、退错误态拒绝、负数容忍、
 * 不回收会员与写真、跨轮退款精确扣回原核销轮次。
 */
@SpringBootTest
@ActiveProfiles("test")
class RefundServiceC14Test {

    private static final int PLAYER_ID = 1;
    private static final int ROUND_ID = 1;
    private static final long TOKEN_POINTS = 19_900L;
    private static final String TOKEN_ID = "RFZJ-2345-6789-ABCD";
    private static final String PHOTO_ASSET_ID = "photo_asset_001";
    private static final String USER_ID = "user_buyer";
    private static final String OPERATOR = "director";

    private final RefundService refundService;
    private final PopularityService popularityService;
    private final TokenMapper tokenMapper;
    private final PopularityLedgerMapper ledgerMapper;
    private final StatsMapper statsMapper;
    private final JdbcTemplate jdbcTemplate;

    RefundServiceC14Test(@Autowired RefundService refundService,
                         @Autowired PopularityService popularityService,
                         @Autowired TokenMapper tokenMapper,
                         @Autowired PopularityLedgerMapper ledgerMapper,
                         @Autowired StatsMapper statsMapper,
                         @Autowired JdbcTemplate jdbcTemplate) {
        this.refundService = refundService;
        this.popularityService = popularityService;
        this.tokenMapper = tokenMapper;
        this.ledgerMapper = ledgerMapper;
        this.statsMapper = statsMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        clearTables();
        insertPlayer(PLAYER_ID, 3, "林夏");
        insertRound(ROUND_ID, "active");
    }

    /**
     * 正常退款：used 卡 → status=refunded、人气精确扣减(-points)、写审计、回滚到核销轮次。
     */
    @Test
    void refundUsedTokenShouldMarkRefundedRollbackPopularityAndWriteAudit() {
        seedRedeemedToken(TOKEN_ID, ROUND_ID, TOKEN_POINTS);

        RefundResult result = refundService.refund("  rfzj-2345-6789-abcd  ", OPERATOR, "用户抖店退款");

        assertThat(result.getTokenId()).isEqualTo(TOKEN_ID);
        assertThat(result.getPlayerId()).isEqualTo(PLAYER_ID);
        assertThat(result.getRefundedPoints()).isEqualTo(TOKEN_POINTS);
        assertThat(result.getRoundId()).isEqualTo(ROUND_ID);

        assertThat(tokenMapper.findById(TOKEN_ID).getStatus()).isEqualTo("refunded");
        // 核销 +19900，退款 -19900 → 聚合人气回到 0
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isZero();
        assertThat(ledgerMapper.countByIdempotencyKey("refund_" + TOKEN_ID)).isEqualTo(1L);
        Long refundLedgerValue = jdbcTemplate.queryForObject(
                "SELECT popularity_value FROM popularity_ledger WHERE idempotency_key = ?",
                Long.class, "refund_" + TOKEN_ID);
        assertThat(refundLedgerValue).isEqualTo(-TOKEN_POINTS);
        Long logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operations_log WHERE action_type = 'refund'", Long.class);
        assertThat(logs).isEqualTo(1L);
    }

    /**
     * 防重复退款（关键）：同一卡密退两次，第二次被原子抢占拦截报错，人气不二次扣减。
     */
    @Test
    void refundSameTokenTwiceShouldRejectSecondAndNotDoubleRollback() {
        seedRedeemedToken(TOKEN_ID, ROUND_ID, TOKEN_POINTS);

        refundService.refund(TOKEN_ID, OPERATOR, "第一次退款");

        assertThatThrownBy(() -> refundService.refund(TOKEN_ID, OPERATOR, "第二次重复退款"))
                .isInstanceOf(RefundException.class)
                .satisfies(e -> assertThat(((RefundException) e).getBusinessCode())
                        .isEqualTo(RefundService.CODE_NOT_REFUNDABLE));

        // 只扣一次：聚合人气仍为 0，退款流水只有一条
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isZero();
        assertThat(ledgerMapper.countByIdempotencyKey("refund_" + TOKEN_ID)).isEqualTo(1L);
        assertThat(tokenMapper.findById(TOKEN_ID).getStatus()).isEqualTo("refunded");
    }

    /**
     * 退一张 unused（从未核销）的卡 → 拒绝，且不写任何退款流水。
     */
    @Test
    void refundUnusedTokenShouldReject() {
        insertToken(TOKEN_ID, ROUND_ID, TOKEN_POINTS, "unused");

        assertThatThrownBy(() -> refundService.refund(TOKEN_ID, OPERATOR, "退未核销卡"))
                .isInstanceOf(RefundException.class)
                .satisfies(e -> assertThat(((RefundException) e).getBusinessCode())
                        .isEqualTo(RefundService.CODE_NOT_REFUNDABLE));

        assertThat(tokenMapper.findById(TOKEN_ID).getStatus()).isEqualTo("unused");
        assertThat(ledgerMapper.countByIdempotencyKey("refund_" + TOKEN_ID)).isZero();
    }

    /**
     * 退一张不存在的卡 → 拒绝。
     */
    @Test
    void refundNonexistentTokenShouldReject() {
        assertThatThrownBy(() -> refundService.refund("RFZJ-9999-9999-9999", OPERATOR, "退不存在卡"))
                .isInstanceOf(RefundException.class)
                .satisfies(e -> assertThat(((RefundException) e).getBusinessCode())
                        .isEqualTo(RefundService.CODE_NOT_REFUNDABLE));
    }

    /**
     * 空卡密 → invalid_token。
     */
    @Test
    void refundBlankTokenShouldReturnInvalidToken() {
        assertThatThrownBy(() -> refundService.refund("   ", OPERATOR, "空卡"))
                .isInstanceOf(RefundException.class)
                .satisfies(e -> assertThat(((RefundException) e).getBusinessCode())
                        .isEqualTo(RefundService.CODE_INVALID_TOKEN));
    }

    /**
     * 负数容忍：选手人气已被消费到 0 后退款，统计表如实变负数不报错；
     * 展示层 computeScore 把负积分钳为 0，排序所依赖的不变量不破坏。
     */
    @Test
    void refundShouldTolerateNegativePopularityAndDisplayLayerClampsToZero() {
        // 核销入账 +19900
        seedRedeemedToken(TOKEN_ID, ROUND_ID, TOKEN_POINTS);
        // 模拟该选手这部分人气已被"消费/转移"：手动扣减 -19900，使聚合回到 0
        manualAdjust(PLAYER_ID, ROUND_ID, -TOKEN_POINTS, "模拟人气已被消费");
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isZero();

        // 此时退款，再扣 -19900 → 统计表变负数，不应报错
        refundService.refund(TOKEN_ID, OPERATOR, "0人气时退款");

        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isEqualTo(-TOKEN_POINTS);
        // 展示层兜底：最终积分钳为 0
        ScoreResult score = popularityService.computeScore(PLAYER_ID, ROUND_ID);
        assertThat(score.getPopularity()).isEqualTo(-TOKEN_POINTS);
        assertThat(score.getScoreFinal()).isZero();
    }

    /**
     * 退款只扣人气，不回收会员天数、不删写真收藏。
     */
    @Test
    void refundShouldNotTouchMembershipOrPhotoCollection() {
        seedRedeemedToken(TOKEN_ID, ROUND_ID, TOKEN_POINTS);
        insertPhotoAsset(PHOTO_ASSET_ID, PLAYER_ID);
        // 写真收藏与会员（模拟核销时已发放的虚拟权益）
        jdbcTemplate.update("INSERT INTO user_photo_collection (user_id, asset_id, token_id) VALUES (?, ?, ?)",
                USER_ID, PHOTO_ASSET_ID, TOKEN_ID);
        LocalDateTime membershipUntil = LocalDateTime.now().plusDays(7);
        jdbcTemplate.update("""
                INSERT INTO user_membership (user_id, membership_until, last_token_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, USER_ID, membershipUntil, TOKEN_ID, LocalDateTime.now(), LocalDateTime.now());

        refundService.refund(TOKEN_ID, OPERATOR, "退款不动权益");

        Long photoCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_photo_collection WHERE user_id = ? AND token_id = ?",
                Long.class, USER_ID, TOKEN_ID);
        Long membershipCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_membership WHERE user_id = ?", Long.class, USER_ID);
        assertThat(photoCount).isEqualTo(1L);
        assertThat(membershipCount).isEqualTo(1L);
    }

    /**
     * 跨轮退款：核销发生在第1轮，退款时已进入第2轮 active，
     * 人气必须扣回核销当时的第1轮，绝不扣到第2轮（验证按原核销轮次回滚）。
     */
    @Test
    void refundShouldRollbackToOriginalRedeemRoundNotCurrentRound() {
        // 第1轮核销入账
        seedRedeemedToken(TOKEN_ID, ROUND_ID, TOKEN_POINTS);
        // 第1轮结束、第2轮开始
        jdbcTemplate.update("UPDATE rounds SET status = 'completed' WHERE round_id = ?", ROUND_ID);
        int round2 = 2;
        insertRound(round2, "active");

        RefundResult result = refundService.refund(TOKEN_ID, OPERATOR, "跨轮退款");

        assertThat(result.getRoundId()).isEqualTo(ROUND_ID);
        // 第1轮被扣回 0，第2轮完全不受影响（无记录 → 视为 0）
        assertThat(statsMapper.findPlayerIndividualPopularity(PLAYER_ID, ROUND_ID)).isZero();
        Long round2Pop = statsMapper.findPlayerIndividualPopularity(PLAYER_ID, round2);
        assertThat(round2Pop == null ? 0L : round2Pop).isZero();
    }

    // ===== 造数辅助 =====

    /** 通过人气引擎写入一条核销入账流水，模拟一张被核销过的 used 卡（含 token_xxx 流水 + 统计 +points）。 */
    private void seedRedeemedToken(String tokenId, int roundId, long points) {
        insertToken(tokenId, roundId, points, "used");
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setTargetType("player");
        req.setTargetId(PLAYER_ID);
        req.setSource("token");
        req.setRawValue(points);
        req.setRoundId(roundId);
        req.setIdempotencyKey("token_" + tokenId);
        req.setOperatorId(USER_ID);
        req.setReason("核销入账(测试造数)");
        req.setOccurredAt(LocalDateTime.now());
        popularityService.applyChange(req);
    }

    private void manualAdjust(int playerId, int roundId, long rawValue, String reason) {
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setTargetType("player");
        req.setTargetId(playerId);
        req.setSource("manual");
        req.setRawValue(rawValue);
        req.setRoundId(roundId);
        req.setIdempotencyKey("manual_test_" + System.nanoTime());
        req.setOperatorId(OPERATOR);
        req.setReason(reason);
        req.setOccurredAt(LocalDateTime.now());
        popularityService.applyChange(req);
    }

    private void clearTables() {
        jdbcTemplate.update("DELETE FROM user_photo_collection");
        jdbcTemplate.update("DELETE FROM user_membership");
        jdbcTemplate.update("DELETE FROM popularity_ledger");
        jdbcTemplate.update("DELETE FROM tokens");
        jdbcTemplate.update("DELETE FROM photo_assets");
        jdbcTemplate.update("DELETE FROM operations_log");
        jdbcTemplate.update("DELETE FROM player_round_stats");
        jdbcTemplate.update("DELETE FROM team_round_stats");
        jdbcTemplate.update("DELETE FROM pool_round_stats");
        jdbcTemplate.update("DELETE FROM player_round");
        jdbcTemplate.update("DELETE FROM teams");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM rounds");
    }

    private void insertPlayer(int playerId, int number, String name) {
        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, status)
                VALUES (?, ?, ?, 'active')
                """, playerId, name, number);
    }

    private void insertRound(int roundId, String status) {
        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?)
                """, roundId, "测试轮次" + roundId,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), status);
    }

    private void insertPhotoAsset(String assetId, int playerId) {
        jdbcTemplate.update("""
                INSERT INTO photo_assets (asset_id, player_id, preview_url, download_url)
                VALUES (?, ?, ?, ?)
                """, assetId, playerId, "https://example.com/preview.jpg", "https://example.com/download.jpg");
    }

    private void insertToken(String tokenId, int roundId, long points, String status) {
        jdbcTemplate.update("""
                INSERT INTO tokens (token_id, player_id, points, product_sku, status, user_id, used_at, created_at)
                VALUES (?, ?, ?, 'sku_test', ?, ?, ?, ?)
                """, tokenId, PLAYER_ID, points, status, USER_ID,
                "used".equals(status) ? LocalDateTime.now() : null, LocalDateTime.now());
    }
}
