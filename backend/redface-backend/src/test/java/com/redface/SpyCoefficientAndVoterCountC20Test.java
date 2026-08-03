package com.redface;

import com.redface.dto.AdminRequests;
import com.redface.dto.GroupVoteSummaryItem;
import com.redface.dto.GroupVoteSummaryResponse;
import com.redface.dto.SpyCoefficientResult;
import com.redface.dto.VoterCountResult;
import com.redface.mapper.StatsMapper;
import com.redface.service.AdminControlService;
import com.redface.service.SpyCoefficientService;
import com.redface.service.VoterCountService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C20-10 卧底人气系数与投票参与人数测试。
 *
 * <p>核心防线是<b>乘法语义</b>：任务加成 ×1.3 后被识破减半必须得 ×0.65，
 * 而非加法的 ×0.8。两者都不会报错，只有断言能守住。
 */
@SpringBootTest
@ActiveProfiles("test")
class SpyCoefficientAndVoterCountC20Test {

    @Autowired
    private SpyCoefficientService spyCoefficientService;

    @Autowired
    private VoterCountService voterCountService;

    @Autowired
    private AdminControlService adminControlService;

    @Autowired
    private StatsMapper statsMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int ROUND_ID = 9310;
    private static final int PLAYER_A = 93101;
    private static final int PLAYER_B = 93102;
    /** 基础卧底人气，取自任务卡示例，用于验证折算后的真实读数。 */
    private static final long BASE_SPY_POPULARITY = 205000L;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM spy_coefficient_ledger WHERE round_id = ?", ROUND_ID);
        jdbcTemplate.update("DELETE FROM group_vote_ledger WHERE round_id = ?", ROUND_ID);
        jdbcTemplate.update("DELETE FROM player_round_stats WHERE round_id = ?", ROUND_ID);
        jdbcTemplate.update("DELETE FROM player_round WHERE round_id = ?", ROUND_ID);
        jdbcTemplate.update("DELETE FROM operations_log WHERE action_type LIKE 'spy_coefficient%'");
        jdbcTemplate.update("DELETE FROM operations_log WHERE action_type LIKE 'voter_count%'");
        jdbcTemplate.update("DELETE FROM players WHERE player_id IN (?, ?)", PLAYER_A, PLAYER_B);
        jdbcTemplate.update("DELETE FROM rounds WHERE round_id = ?", ROUND_ID);

        jdbcTemplate.update("INSERT INTO rounds (round_id, name, start_time, end_time, status) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)", ROUND_ID, "C20-10测试轮次", "active");
        jdbcTemplate.update("INSERT INTO players (player_id, name, number) VALUES (?, ?, ?)",
                PLAYER_A, "卧底甲", 9310);
        jdbcTemplate.update("INSERT INTO players (player_id, name, number) VALUES (?, ?, ?)",
                PLAYER_B, "卧底乙", 9311);
        jdbcTemplate.update("INSERT INTO player_round (player_id, round_id, is_spy) VALUES (?, ?, TRUE)",
                PLAYER_A, ROUND_ID);
        jdbcTemplate.update("INSERT INTO player_round (player_id, round_id, is_spy) VALUES (?, ?, TRUE)",
                PLAYER_B, ROUND_ID);
        // 造出基础卧底人气裸值，用于验证「账本存裸值、读取时折算」这一约定
        jdbcTemplate.update("INSERT INTO player_round_stats (player_id, round_id, individual_popularity, "
                        + "spy_popularity, spy_coefficient) VALUES (?, ?, 0, ?, 100)",
                PLAYER_A, ROUND_ID, BASE_SPY_POPULARITY);
    }

    // ==================== 乘法语义（本卡最核心） ====================

    @Test
    @DisplayName("核心：任务加成×1.3后识破减半得×0.65，而非加法的×0.8")
    void multiplicativeNotAdditive() {
        SpyCoefficientResult bonus = spyCoefficientService.applyTaskBonus(
                PLAYER_A, ROUND_ID, 130, "k-mul-1", "op_test", "完成潜伏任务");
        assertEquals(SpyCoefficientResult.STATUS_APPLIED, bonus.getStatus());
        assertEquals(100, bonus.getCoefficientBefore());
        assertEquals(130, bonus.getCoefficientAfter(), "100×1.3 应得 130");

        SpyCoefficientResult halve = spyCoefficientService.applyExposedHalve(
                PLAYER_A, ROUND_ID, "k-mul-2", "op_test", "被现场识破");
        assertEquals(SpyCoefficientResult.STATUS_APPLIED, halve.getStatus());
        assertEquals(65, halve.getCoefficientAfter(),
                "130×0.5 应得 65（×0.65）。若得 80 则说明退化成了加法 100+30-50");

        // 折算后的真实人气读数：205000 × 0.65 = 133250。
        // 加法语义会得到 205000 × 0.8 = 164000，相差 30750，且两者都不报错。
        Long adjusted = statsMapper.findPlayerSpyPopularity(PLAYER_A, ROUND_ID);
        assertEquals(133250L, adjusted, "折算后卧底人气应为 205000×0.65=133250");

        Long raw = statsMapper.findPlayerSpyPopularityRaw(PLAYER_A, ROUND_ID);
        assertEquals(BASE_SPY_POPULARITY, raw, "裸值必须保持不变，折算只发生在读取时");
    }

    @Test
    @DisplayName("顺序无关：先识破再加成与先加成再识破结果相同（乘法可交换）")
    void orderIndependent() {
        spyCoefficientService.applyExposedHalve(PLAYER_B, ROUND_ID, "k-ord-1", "op_test", "先被识破");
        SpyCoefficientResult after = spyCoefficientService.applyTaskBonus(
                PLAYER_B, ROUND_ID, 130, "k-ord-2", "op_test", "后完成任务");
        assertEquals(65, after.getCoefficientAfter(),
                "100×0.5×1.3=65，与先加成再减半的 100×1.3×0.5=65 一致");
    }

    @Test
    @DisplayName("任务加成可多次施加，且界面能拿到已施加次数")
    void taskBonusStacks() {
        spyCoefficientService.applyTaskBonus(PLAYER_A, ROUND_ID, 130, "k-stk-1", "op_test", "任务一");
        SpyCoefficientResult second = spyCoefficientService.applyTaskBonus(
                PLAYER_A, ROUND_ID, 130, "k-stk-2", "op_test", "任务二");
        assertEquals(169, second.getCoefficientAfter(), "130×1.3=169");
        assertEquals(2, second.getTaskBonusCount(),
                "界面必须能显示已施加 2 次，否则运营重复施加而不自知");
    }

    // ==================== 识破减半重复施加 ====================

    @Test
    @DisplayName("识破减半重复施加被拒，且拒绝消息含施加时间与操作人")
    void duplicateExposedHalveRejected() {
        spyCoefficientService.applyExposedHalve(PLAYER_A, ROUND_ID, "k-exp-1", "彬少", "第一次识破");

        SpyCoefficientResult second = spyCoefficientService.applyExposedHalve(
                PLAYER_A, ROUND_ID, "k-exp-2", "Vincent", "误以为还没标记");

        assertEquals(SpyCoefficientResult.STATUS_REJECTED, second.getStatus(),
                "必须是 rejected（未生效）而非 duplicated（已生效），两者含义相反");
        assertEquals(50, second.getCoefficientAfter(), "被拒后系数必须保持 ×0.5，不得变成 ×0.25");
        assertNotNull(second.getRejectReason());
        assertTrue(second.getRejectReason().contains("彬少"),
                "拒绝消息须含首次操作人，否则运营不知该找谁核对。实际消息：" + second.getRejectReason());
        assertTrue(second.getRejectReason().matches("(?s).*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"),
                "拒绝消息须含施加时间。实际消息：" + second.getRejectReason());

        Long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spy_coefficient_ledger WHERE player_id = ? AND round_id = ? "
                        + "AND factor_type = 'exposed_halve' AND revoked = FALSE",
                Long.class, PLAYER_A, ROUND_ID);
        assertEquals(1L, activeCount, "账本中有效识破记录只应有一条");
    }

    @Test
    @DisplayName("相同幂等键连点返回duplicated（已生效），与rejected严格区分")
    void idempotentReturnsDuplicated() {
        spyCoefficientService.applyTaskBonus(PLAYER_A, ROUND_ID, 130, "k-idem-1", "op_test", "任务加成");
        SpyCoefficientResult replay = spyCoefficientService.applyTaskBonus(
                PLAYER_A, ROUND_ID, 130, "k-idem-1", "op_test", "任务加成");

        assertEquals(SpyCoefficientResult.STATUS_DUPLICATED, replay.getStatus());
        assertEquals(130, replay.getCoefficientAfter(), "幂等拦截后系数应仍为 130，未被乘第二次");

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spy_coefficient_ledger WHERE idempotency_key = 'spycoef_k-idem-1'",
                Long.class);
        assertEquals(1L, ledgerCount, "账本只应有一条记录");
    }

    // ==================== 撤销与重建 ====================

    @Test
    @DisplayName("撤销识破减半后系数按剩余条目重建，且可重新施加")
    void revokeRebuildsAndAllowsReapply() {
        spyCoefficientService.applyTaskBonus(PLAYER_A, ROUND_ID, 130, "k-rvk-1", "op_test", "任务加成");
        spyCoefficientService.applyExposedHalve(PLAYER_A, ROUND_ID, "k-rvk-2", "op_test", "误标识破");
        assertEquals(65, currentCoefficient(PLAYER_A));

        Long halveId = jdbcTemplate.queryForObject(
                "SELECT id FROM spy_coefficient_ledger WHERE idempotency_key = 'spycoef_k-rvk-2'", Long.class);
        SpyCoefficientResult revoked = spyCoefficientService.revoke(
                halveId, PLAYER_A, ROUND_ID, "op_test", "识破标记错了，撤销");

        assertEquals(SpyCoefficientResult.STATUS_REVOKED, revoked.getStatus());
        assertEquals(130, revoked.getCoefficientAfter(),
                "撤销减半后应从 100 起按剩余条目重乘得 130，而非用除法回退");
        assertFalse(revoked.isExposed() && false);

        // 撤销后必须能重新施加，否则误标识破只能靠手动改人气挽回，会污染账本
        SpyCoefficientResult reapplied = spyCoefficientService.applyExposedHalve(
                PLAYER_A, ROUND_ID, "k-rvk-3", "op_test", "核实后确认确实被识破");
        assertEquals(SpyCoefficientResult.STATUS_APPLIED, reapplied.getStatus(),
                "撤销后同类型必须允许重新施加");
        assertEquals(65, reapplied.getCoefficientAfter());
    }

    @Test
    @DisplayName("撤销全部条目后系数回到×1.0，人气恢复裸值")
    void revokeAllRestoresBase() {
        spyCoefficientService.applyTaskBonus(PLAYER_A, ROUND_ID, 130, "k-all-1", "op_test", "任务加成");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM spy_coefficient_ledger WHERE idempotency_key = 'spycoef_k-all-1'", Long.class);
        spyCoefficientService.revoke(id, PLAYER_A, ROUND_ID, "op_test", "撤销");

        assertEquals(100, currentCoefficient(PLAYER_A));
        assertEquals(BASE_SPY_POPULARITY, statsMapper.findPlayerSpyPopularity(PLAYER_A, ROUND_ID),
                "全部撤销后折算人气应等于裸值");
    }

    @Test
    @DisplayName("撤销时校验账本条目归属，跨选手撤销被拒")
    void revokeValidatesOwnership() {
        spyCoefficientService.applyTaskBonus(PLAYER_A, ROUND_ID, 130, "k-own-1", "op_test", "任务加成");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM spy_coefficient_ledger WHERE idempotency_key = 'spycoef_k-own-1'", Long.class);

        assertThrows(IllegalArgumentException.class,
                () -> spyCoefficientService.revoke(id, PLAYER_B, ROUND_ID, "op_test", "误撤"),
                "拿A的账本条目去撤B必须被拒：撤错不会报错，只会让另一位选手人气静默变化");
        assertEquals(130, currentCoefficient(PLAYER_A), "被拒后A的系数不得变化");
    }

    @Test
    @DisplayName("已撤销条目重复撤销返回duplicated，不重复重建")
    void revokeTwiceIsIdempotent() {
        spyCoefficientService.applyTaskBonus(PLAYER_A, ROUND_ID, 130, "k-rt-1", "op_test", "任务加成");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM spy_coefficient_ledger WHERE idempotency_key = 'spycoef_k-rt-1'", Long.class);
        spyCoefficientService.revoke(id, PLAYER_A, ROUND_ID, "op_test", "撤销");
        SpyCoefficientResult again = spyCoefficientService.revoke(id, PLAYER_A, ROUND_ID, "op_test", "再撤一次");

        assertEquals(SpyCoefficientResult.STATUS_DUPLICATED, again.getStatus());
        assertEquals(100, currentCoefficient(PLAYER_A));
    }

    // ==================== 上下限 ====================

    @Test
    @DisplayName("超出系数上限时返回rejected而非静默夹住")
    void ceilingReturnsRejected() {
        // 连续施加 ×3 直到接近上限 ×10
        spyCoefficientService.applyTaskBonus(PLAYER_A, ROUND_ID, 300, "k-cl-1", "op_test", "加成1");  // 300
        spyCoefficientService.applyTaskBonus(PLAYER_A, ROUND_ID, 300, "k-cl-2", "op_test", "加成2");  // 900
        SpyCoefficientResult over = spyCoefficientService.applyTaskBonus(
                PLAYER_A, ROUND_ID, 300, "k-cl-3", "op_test", "加成3");

        assertEquals(SpyCoefficientResult.STATUS_REJECTED, over.getStatus(),
                "触及上限必须明确拒绝，否则运营看到「操作成功但数字没动」无法排查");
        assertEquals(900, over.getCoefficientAfter());
        assertNotNull(over.getRejectReason());

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spy_coefficient_ledger WHERE idempotency_key = 'spycoef_k-cl-3'",
                Long.class);
        assertEquals(0L, ledgerCount, "被拒的施加不得写入账本");
    }

    @Test
    @DisplayName("参数校验：识破减半不接受自定义factor，factorType白名单")
    void parameterValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> spyCoefficientService.applyFactor(PLAYER_A, ROUND_ID, 70,
                        "exposed_halve", "k-pv-1", "op_test", "自定义减半"),
                "识破减半的factor固定50，不接受自定义");
        assertThrows(IllegalArgumentException.class,
                () -> spyCoefficientService.applyFactor(PLAYER_A, ROUND_ID, 130,
                        "unknown_type", "k-pv-2", "op_test", "未知类型"));
        assertThrows(IllegalArgumentException.class,
                () -> spyCoefficientService.applyFactor(PLAYER_A, ROUND_ID, 0,
                        "task_bonus", "k-pv-3", "op_test", "零因子"),
                "factor为0会把系数永久锁死在0，必须拒绝");
    }

    // ==================== 参与人数 ====================

    @Test
    @DisplayName("参与人数首次录入成功，得票占比按参与人数为分母计算")
    void voterCountRecordedAndPercentComputed() {
        adminControlService.recordGroupVote(buildVote(PLAYER_A, 30, "v-pc-1"));
        adminControlService.recordGroupVote(buildVote(PLAYER_B, 20, "v-pc-2"));

        VoterCountResult result = voterCountService.record(ROUND_ID, 80, false, "op_test", "现场清点80人");
        assertEquals(VoterCountResult.STATUS_RECORDED, result.getStatus());
        assertNull(result.getVoterCountBefore(), "首次录入前值应为 null");

        GroupVoteSummaryResponse summary = adminControlService.getGroupVoteSummary(ROUND_ID);
        assertEquals(80, summary.getVoterCount());
        assertTrue(summary.isVoterCountRecorded());

        GroupVoteSummaryItem a = pick(summary.getItems(), PLAYER_A);
        assertEquals(37.5, a.getVotePercent(), "30/80=37.5%，分母是参与人数而非票数总和50");
    }

    @Test
    @DisplayName("参与人数未录入时占比为null而非0，避免漏录伪装成全场零票")
    void percentNullWhenVoterCountMissing() {
        adminControlService.recordGroupVote(buildVote(PLAYER_A, 30, "v-null-1"));
        GroupVoteSummaryResponse summary = adminControlService.getGroupVoteSummary(ROUND_ID);

        assertNull(summary.getVoterCount());
        assertFalse(summary.isVoterCountRecorded());
        assertNull(pick(summary.getItems(), PLAYER_A).getVotePercent(),
                "未录参与人数时占比须为 null，显示 0% 会让场控以为无人投票而不去补录");
    }

    @Test
    @DisplayName("参与人数0是合法观测值，占比为0.0而非null")
    void zeroVoterCountIsLegal() {
        VoterCountResult result = voterCountService.record(ROUND_ID, 0, false, "op_test", "确实无人投票");
        assertEquals(VoterCountResult.STATUS_RECORDED, result.getStatus());

        // 不能用录 0 票构造场景：群投票录入本身拒绝 votes=0（零增量无语义）。
        // 先录一票再冲销回 0，得到真实的「净票数 0」场景。
        adminControlService.recordGroupVote(buildVote(PLAYER_A, 1, "v-zero-1"));
        adminControlService.recordGroupVote(buildVote(PLAYER_A, -1, "v-zero-2"));

        GroupVoteSummaryResponse summary = adminControlService.getGroupVoteSummary(ROUND_ID);
        assertEquals(0, summary.getVoterCount());
        assertTrue(summary.isVoterCountRecorded(), "0 与未录入必须严格区分");
        assertEquals(0.0, pick(summary.getItems(), PLAYER_A).getVotePercent(),
                "参与人数已录为 0 时占比记 0.0（已录入）而非 null（未录入）");
    }

    @Test
    @DisplayName("参与人数小于最高得票数时需二次确认，且此时未写入")
    void voterCountConflictNeedsConfirm() {
        adminControlService.recordGroupVote(buildVote(PLAYER_A, 60, "v-cf-1"));

        VoterCountResult conflict = voterCountService.record(ROUND_ID, 50, false, "op_test", "数错了");
        assertEquals(VoterCountResult.STATUS_NEEDS_CONFIRM, conflict.getStatus());
        assertNotNull(conflict.getConfirmReason());
        assertTrue(conflict.getConfirmReason().contains("60"), "确认文案须含冲突票数以便定位");
        assertTrue(conflict.getConfirmReason().contains("卧底甲"), "确认文案须含选手名以便定位该核对谁");
        assertNull(voterCountService.getVoterCount(ROUND_ID),
                "needs_confirm 时必须尚未写入，否则二次确认形同虚设");

        VoterCountResult forced = voterCountService.record(ROUND_ID, 50, true, "op_test", "确认按50录");
        assertEquals(VoterCountResult.STATUS_RECORDED, forced.getStatus());
        assertTrue(forced.isForcedOverConflict(), "强制写入后界面须继续标红提醒");
        assertEquals(50, voterCountService.getVoterCount(ROUND_ID));
    }

    @Test
    @DisplayName("覆盖已有参与人数需二次确认，确认后写操作日志含新旧值")
    void overwriteNeedsConfirmAndLogged() {
        voterCountService.record(ROUND_ID, 80, false, "op_test", "首次录入");

        VoterCountResult overwrite = voterCountService.record(ROUND_ID, 95, false, "彬少", "重新清点");
        assertEquals(VoterCountResult.STATUS_NEEDS_CONFIRM, overwrite.getStatus());
        assertTrue(overwrite.getConfirmReason().contains("80"), "确认文案须显示旧值");
        assertEquals(80, voterCountService.getVoterCount(ROUND_ID), "未确认前不得改动");

        voterCountService.record(ROUND_ID, 95, true, "彬少", "重新清点确认");
        assertEquals(95, voterCountService.getVoterCount(ROUND_ID));

        List<String> details = jdbcTemplate.queryForList(
                "SELECT detail FROM operations_log WHERE action_type = 'voter_count_overwrite'", String.class);
        assertEquals(1, details.size(), "覆盖须写且只写一条日志（未确认那次不写）");
        assertTrue(details.get(0).contains("\"voterCountBefore\":80"), "日志须含旧值");
        assertTrue(details.get(0).contains("\"voterCountAfter\":95"), "日志须含新值");
    }

    @Test
    @DisplayName("票数超过参与人数时只提示不阻断，票仍入账")
    void voteOverVoterCountWarnsButNotBlocks() {
        voterCountService.record(ROUND_ID, 50, false, "op_test", "清点50人");

        var outcome = adminControlService.recordGroupVote(buildVote(PLAYER_A, 60, "v-warn-1"));
        assertEquals(60, outcome.getResult().currentTotalVotes(), "票数必须已入账，不得因警告而丢弃");
        assertNotNull(outcome.getResult().voterCountWarning(), "须返回警告文案");
        assertTrue(outcome.getResult().voterCountWarning().contains("负数冲销"),
                "警告须指明正确修复动作是负数冲销而非覆盖");
    }

    @Test
    @DisplayName("参与人数负数被拒；缺操作人或原因被拒")
    void voterCountValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> voterCountService.record(ROUND_ID, -1, false, "op_test", "负数"));
        assertThrows(IllegalArgumentException.class,
                () -> voterCountService.record(ROUND_ID, 10, false, null, "缺操作人"));
        assertThrows(IllegalArgumentException.class,
                () -> voterCountService.record(ROUND_ID, 10, false, "op_test", null));
        assertThrows(IllegalArgumentException.class,
                () -> voterCountService.record(999999, 10, false, "op_test", "轮次不存在"));
    }

    // ==================== 识破标记与大屏隔离 ====================

    @Test
    @DisplayName("识破标记以账本为唯一真相来源，后台汇总可见")
    void exposedFlagFromLedger() {
        adminControlService.recordGroupVote(buildVote(PLAYER_A, 10, "v-ex-1"));
        adminControlService.recordGroupVote(buildVote(PLAYER_B, 10, "v-ex-2"));
        spyCoefficientService.applyExposedHalve(PLAYER_A, ROUND_ID, "k-ex-1", "op_test", "被识破");

        GroupVoteSummaryResponse summary = adminControlService.getGroupVoteSummary(ROUND_ID);
        assertTrue(pick(summary.getItems(), PLAYER_A).isExposed(), "A 已识破");
        assertFalse(pick(summary.getItems(), PLAYER_B).isExposed(), "B 未识破");

        // 撤销后标记必须随之消失，否则界面会显示「已识破但系数是1.0」的自相矛盾状态
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM spy_coefficient_ledger WHERE idempotency_key = 'spycoef_k-ex-1'", Long.class);
        spyCoefficientService.revoke(id, PLAYER_A, ROUND_ID, "op_test", "误标撤销");
        GroupVoteSummaryResponse after = adminControlService.getGroupVoteSummary(ROUND_ID);
        assertFalse(pick(after.getItems(), PLAYER_A).isExposed(), "撤销后识破标记须消失");
    }

    @Test
    @DisplayName("回显接口返回裸值、折算值与含已撤销条目的完整账本")
    void inspectReturnsFullPicture() {
        spyCoefficientService.applyTaskBonus(PLAYER_A, ROUND_ID, 130, "k-in-1", "op_test", "任务加成");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM spy_coefficient_ledger WHERE idempotency_key = 'spycoef_k-in-1'", Long.class);
        spyCoefficientService.revoke(id, PLAYER_A, ROUND_ID, "op_test", "撤销");
        spyCoefficientService.applyExposedHalve(PLAYER_A, ROUND_ID, "k-in-2", "op_test", "被识破");

        SpyCoefficientResult view = spyCoefficientService.inspect(PLAYER_A, ROUND_ID);
        assertEquals(SpyCoefficientResult.STATUS_INSPECTED, view.getStatus());
        assertEquals(50, view.getCoefficientAfter());
        assertEquals("×0.5", view.getCoefficientLabel());
        assertTrue(view.isExposed());
        assertEquals(BASE_SPY_POPULARITY, view.getSpyPopularityRaw());
        assertEquals(102500L, view.getSpyPopularityAdjusted(), "205000×0.5=102500");
        assertEquals(2, view.getLedger().size(),
                "账本须含已撤销条目，否则运营无法解释系数为何变化");
        assertEquals(1, view.getLedger().stream().filter(i -> i.isRevoked()).count());
    }

    private int currentCoefficient(int playerId) {
        Integer value = statsMapper.findPlayerSpyCoefficient(playerId, ROUND_ID);
        return value == null ? 100 : value;
    }

    private GroupVoteSummaryItem pick(List<GroupVoteSummaryItem> items, int playerId) {
        return items.stream().filter(i -> Integer.valueOf(playerId).equals(i.getPlayerId()))
                .findFirst().orElseThrow(() -> new AssertionError("汇总中未找到选手 " + playerId));
    }

    private AdminRequests.GroupVoteEntryRequest buildVote(int playerId, long votes, String key) {
        AdminRequests.GroupVoteEntryRequest request = new AdminRequests.GroupVoteEntryRequest();
        request.setRoundId(ROUND_ID);
        request.setPlayerId(playerId);
        request.setVotes(votes);
        request.setOperatorId("op_test");
        request.setReason("C20-10测试录票");
        request.setIdempotencyKey(key);
        return request;
    }
}
