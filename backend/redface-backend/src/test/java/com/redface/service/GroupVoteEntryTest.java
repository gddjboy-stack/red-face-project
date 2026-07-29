package com.redface.service;

import com.redface.dto.AdminOperationResult;
import com.redface.dto.AdminRequests;
import com.redface.dto.GroupVoteSummaryItem;
import com.redface.dto.GroupVoteSummaryResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C20-3 群投票结果录入测试。
 * 覆盖卡片验收四项：多次累计正确、冲销正确、连点不重复（幂等）、日志落库。
 */
@SpringBootTest
@ActiveProfiles("test")
class GroupVoteEntryTest {

    @Autowired
    private AdminControlService adminControlService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int ROUND_ID = 901;
    private static final int PLAYER_A = 9001;
    private static final int PLAYER_B = 9002;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM popularity_ledger WHERE round_id = ?", ROUND_ID);
        jdbcTemplate.update("DELETE FROM player_round_stats WHERE round_id = ?", ROUND_ID);
        jdbcTemplate.update("DELETE FROM operations_log WHERE action_type = 'group_vote_entry'");
        jdbcTemplate.update("DELETE FROM players WHERE player_id IN (?, ?)", PLAYER_A, PLAYER_B);
        jdbcTemplate.update("DELETE FROM rounds WHERE round_id = ?", ROUND_ID);
        jdbcTemplate.update("INSERT INTO rounds (round_id, name, start_time, end_time, status) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)", ROUND_ID, "测试轮次901", "active");
        jdbcTemplate.update("INSERT INTO players (player_id, name, number) VALUES (?, ?, ?)", PLAYER_A, "测试选手甲", 901);
        jdbcTemplate.update("INSERT INTO players (player_id, name, number) VALUES (?, ?, ?)", PLAYER_B, "测试选手乙", 902);
    }

    private AdminRequests.GroupVoteEntryRequest buildRequest(int playerId, long votes, String idemKey) {
        AdminRequests.GroupVoteEntryRequest request = new AdminRequests.GroupVoteEntryRequest();
        request.setRoundId(ROUND_ID);
        request.setPlayerId(playerId);
        request.setVotes(votes);
        request.setOperatorId("op_test");
        request.setReason("8/1群投票录入测试");
        request.setIdempotencyKey(idemKey);
        return request;
    }

    @Test
    @DisplayName("多次录入同轮同选手，票数累加而非覆盖")
    void multipleEntriesAccumulate() {
        adminControlService.recordGroupVote(buildRequest(PLAYER_A, 30, "k-acc-1"));
        AdminOperationResult<AdminControlService.GroupVoteEntryOutcome> second =
                adminControlService.recordGroupVote(buildRequest(PLAYER_A, 25, "k-acc-2"));

        assertFalse(second.getResult().duplicated());
        assertEquals(55, second.getResult().currentTotalVotes(), "两次录入应累加 30+25=55");

        GroupVoteSummaryResponse summary = adminControlService.getGroupVoteSummary(ROUND_ID);
        GroupVoteSummaryItem itemA = summary.getItems().stream()
                .filter(i -> Integer.valueOf(PLAYER_A).equals(i.getPlayerId())).findFirst().orElseThrow();
        assertEquals(55, itemA.getTotalVotes());
        assertEquals(2, itemA.getEntryCount());
    }

    @Test
    @DisplayName("负数录入冲销，账本保留两笔流水，净值正确")
    void negativeEntryReverses() {
        adminControlService.recordGroupVote(buildRequest(PLAYER_A, 40, "k-rev-1"));
        // 录错了，冲销10票
        AdminOperationResult<AdminControlService.GroupVoteEntryOutcome> reversal =
                adminControlService.recordGroupVote(buildRequest(PLAYER_A, -10, "k-rev-2"));

        assertEquals(30, reversal.getResult().currentTotalVotes(), "冲销后净值应为 40-10=30");

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popularity_ledger WHERE round_id = ? AND target_id = ? AND source = 'group_vote'",
                Long.class, ROUND_ID, PLAYER_A);
        assertEquals(2L, ledgerCount, "冲销必须新增流水而非修改原流水（账本只增不改）");

        Long spyPopularity = jdbcTemplate.queryForObject(
                "SELECT spy_popularity FROM player_round_stats WHERE player_id = ? AND round_id = ?",
                Long.class, PLAYER_A, ROUND_ID);
        assertEquals(30L, spyPopularity, "统计表 spy_popularity 应与账本净值一致");
    }

    @Test
    @DisplayName("相同幂等键连点重复提交，只记账一次")
    void duplicateIdempotencyKeyBlocked() {
        AdminOperationResult<AdminControlService.GroupVoteEntryOutcome> first =
                adminControlService.recordGroupVote(buildRequest(PLAYER_B, 20, "k-dup-1"));
        AdminOperationResult<AdminControlService.GroupVoteEntryOutcome> duplicate =
                adminControlService.recordGroupVote(buildRequest(PLAYER_B, 20, "k-dup-1"));

        assertFalse(first.getResult().duplicated());
        assertTrue(duplicate.getResult().duplicated(), "第二次相同幂等键应被拦截");
        assertEquals(20, duplicate.getResult().currentTotalVotes(), "累计票数应仍为20，未重复记账");

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popularity_ledger WHERE idempotency_key = 'gv_k-dup-1'", Long.class);
        assertEquals(1L, ledgerCount, "账本只应有一笔流水");

        Long logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operations_log WHERE action_type = 'group_vote_entry'", Long.class);
        assertEquals(1L, logCount, "幂等拦截的重复提交不应再写操作日志");
    }

    @Test
    @DisplayName("操作日志落库，含操作人/轮次/选手/增减票数")
    void operationLogPersisted() {
        adminControlService.recordGroupVote(buildRequest(PLAYER_A, 15, "k-log-1"));

        List<String> details = jdbcTemplate.queryForList(
                "SELECT detail FROM operations_log WHERE action_type = 'group_vote_entry' AND operator_id = 'op_test'",
                String.class);
        assertEquals(1, details.size());
        String detail = details.get(0);
        assertTrue(detail.contains("\"roundId\":" + ROUND_ID), "日志应含轮次");
        assertTrue(detail.contains("\"playerId\":" + PLAYER_A), "日志应含选手");
        assertTrue(detail.contains("\"votes\":15"), "日志应含增减票数");
    }

    @Test
    @DisplayName("参数校验：votes为0、缺幂等键、缺操作人均被拒绝")
    void invalidRequestsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> adminControlService.recordGroupVote(buildRequest(PLAYER_A, 0, "k-bad-1")));

        AdminRequests.GroupVoteEntryRequest noKey = buildRequest(PLAYER_A, 10, null);
        assertThrows(IllegalArgumentException.class, () -> adminControlService.recordGroupVote(noKey));

        AdminRequests.GroupVoteEntryRequest noOperator = buildRequest(PLAYER_A, 10, "k-bad-2");
        noOperator.setOperatorId(null);
        assertThrows(IllegalArgumentException.class, () -> adminControlService.recordGroupVote(noOperator));
    }
}
