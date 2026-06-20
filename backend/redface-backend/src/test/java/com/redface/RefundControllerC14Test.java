package com.redface;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.redface.dto.PopularityChangeRequest;
import com.redface.service.PopularityService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 任务卡 C14 验证：POST /api/admin/refund 的 HTTP 行为与 admin 鉴权。
 *
 * <p>显式配置 redface.admin.token 模拟生产态：无 X-Admin-Token 必须 401；
 * 带正确 token 才能退款；退错误态返回 42002 业务码。
 */
@SpringBootTest(properties = "redface.admin.token=test-secret-token-123")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefundControllerC14Test {

    private static final String ADMIN_TOKEN = "test-secret-token-123";
    private static final int PLAYER_ID = 1;
    private static final int ROUND_ID = 1;
    private static final long TOKEN_POINTS = 19_900L;
    private static final String TOKEN_ID = "RFZJ-2345-6789-ABCD";
    private static final String USER_ID = "user_buyer";

    private final MockMvc mockMvc;
    private final PopularityService popularityService;
    private final JdbcTemplate jdbcTemplate;

    RefundControllerC14Test(@Autowired MockMvc mockMvc,
                            @Autowired PopularityService popularityService,
                            @Autowired JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.popularityService = popularityService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        clearTables();
        insertPlayer();
        insertRound(ROUND_ID, "active");
        seedRedeemedToken();
    }

    @Test
    void refundWithoutAdminTokenShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/admin/refund")
                        .contentType("application/json")
                        .content("{\"token\":\"" + TOKEN_ID + "\",\"operatorId\":\"director\",\"reason\":\"退款\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void refundWithAdminTokenShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/admin/refund")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType("application/json")
                        .content("{\"token\":\"" + TOKEN_ID + "\",\"operatorId\":\"director\",\"reason\":\"用户退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.action").value("refund"))
                .andExpect(jsonPath("$.data.result.tokenId").value(TOKEN_ID))
                .andExpect(jsonPath("$.data.result.refundedPoints").value(TOKEN_POINTS));

        Long status = jdbcTemplate.queryForObject(
                "SELECT individual_popularity FROM player_round_stats WHERE player_id = ? AND round_id = ?",
                Long.class, PLAYER_ID, ROUND_ID);
        assertEqualsZero(status);
    }

    @Test
    void refundAlreadyRefundedTokenShouldReturn42002() throws Exception {
        // 先成功退一次
        mockMvc.perform(post("/api/admin/refund")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType("application/json")
                        .content("{\"token\":\"" + TOKEN_ID + "\",\"operatorId\":\"director\",\"reason\":\"第一次\"}"))
                .andExpect(status().isOk());

        // 再退一次应被拒绝
        mockMvc.perform(post("/api/admin/refund")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType("application/json")
                        .content("{\"token\":\"" + TOKEN_ID + "\",\"operatorId\":\"director\",\"reason\":\"重复退\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(42002))
                .andExpect(jsonPath("$.data.businessCode").value("not_refundable"));
    }

    private void assertEqualsZero(Long value) {
        org.assertj.core.api.Assertions.assertThat(value).isZero();
    }

    private void seedRedeemedToken() {
        jdbcTemplate.update("""
                INSERT INTO tokens (token_id, player_id, points, product_sku, status, user_id, used_at, created_at)
                VALUES (?, ?, ?, 'sku_test', 'used', ?, ?, ?)
                """, TOKEN_ID, PLAYER_ID, TOKEN_POINTS, USER_ID, LocalDateTime.now(), LocalDateTime.now());
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setTargetType("player");
        req.setTargetId(PLAYER_ID);
        req.setSource("token");
        req.setRawValue(TOKEN_POINTS);
        req.setRoundId(ROUND_ID);
        req.setIdempotencyKey("token_" + TOKEN_ID);
        req.setOperatorId(USER_ID);
        req.setReason("核销入账(测试造数)");
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

    private void insertPlayer() {
        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, status)
                VALUES (?, ?, ?, 'active')
                """, PLAYER_ID, "林夏", 3);
    }

    private void insertRound(int roundId, String status) {
        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?)
                """, roundId, "测试轮次" + roundId,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), status);
    }
}
