package com.redface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redface.dto.AdminRequests;
import com.redface.dto.ManualSalesEntryResult;
import com.redface.dto.ManualSalesSummaryItem;
import com.redface.entity.ProductPriceConfig;
import com.redface.service.ManualSalesService;
import com.redface.service.ProductPriceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * C20-6 后台手工销量录入测试。
 *
 * <p><b>本文件的防御方向与 C20-4C 相反，必须明说</b>：订单表导入的主要风险是
 * 「无意识丢失」（订单没被计入而无人知晓），故 C20-4C 做硬阻断。手工录入的主要风险是
 * 「无意识多算」——运营多打一个零、或以为没提交成功而重复录入。多算比少算更难被发现，
 * 因为选手人气变高不会有人来投诉。因此本文件的用例集中在「拦住异常放大」，
 * 而非「拦住遗漏」。
 *
 * <p>特别注意 {@code needs_confirm} 这一态：它表示<b>尚未入账</b>。
 * 若把它与「已入账」混同，本该入账的销量会凭空消失；若把它与「幂等拦截」混同，
 * 运营会误以为已经录过。因此每条提示用例都必须断言「此时账本与人气均未变动」。
 */
@SpringBootTest
@ActiveProfiles("test")
class ManualSalesC20Test {

    @Autowired
    private ManualSalesService manualSalesService;
    @Autowired
    private ProductPriceService productPriceService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int ROUND_ID = 1;
    private static final int PLAYER_A = 21;
    private static final int PLAYER_B = 22;
    private static final String CODE_CARD = "P21-CARD";
    private static final String CODE_PHOTO = "P21-PHOTO";
    private static final String CODE_B_CARD = "P22-CARD";

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM manual_sales_ledger");
        jdbcTemplate.update("DELETE FROM product_price_config");
        jdbcTemplate.update("DELETE FROM popularity_ledger");
        jdbcTemplate.update("DELETE FROM player_round_stats");
        jdbcTemplate.update("DELETE FROM player_round");
        jdbcTemplate.update("DELETE FROM teams");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM rounds");
        jdbcTemplate.update("DELETE FROM operations_log");

        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (?, '第一轮', '2026-08-09 20:00:00', '2026-08-09 23:00:00', 'running')
                """, ROUND_ID);
        jdbcTemplate.update("INSERT INTO teams (team_id, name) VALUES (1, '红队')");
        // 注意：这里刻意<b>不</b>写 display_code。C20-6 的全部价值就在于绕开它
        // （display_code 在生产环境无写入入口，见 DEFECT-001）。若本测试依赖它，
        // 就重复了 C20-4C「测试绕过生产唯一入口」的错误。
        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, status)
                VALUES (?, '林一', 1, 'active')
                """, PLAYER_A);
        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, status)
                VALUES (?, '陈二', 2, 'active')
                """, PLAYER_B);
        for (int pid : new int[] {PLAYER_A, PLAYER_B}) {
            jdbcTemplate.update("""
                    INSERT INTO player_round (player_id, round_id, team_id, is_spy, player_status)
                    VALUES (?, ?, 1, 0, 'normal')
                    """, pid, ROUND_ID);
            jdbcTemplate.update("""
                    INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity)
                    VALUES (?, ?, 0, 0)
                    """, pid, ROUND_ID);
        }
        // 明信片 19.9 元、写真 59 元，模拟「同一选手多款商品各有独立编码」的真实规则
        productPriceService.save(CODE_CARD, "林一明信片", "19.9",
                ProductPriceConfig.STATUS_ACTIVE, "tester");
        productPriceService.save(CODE_PHOTO, "林一写真", "59",
                ProductPriceConfig.STATUS_ACTIVE, "tester");
        productPriceService.save(CODE_B_CARD, "陈二明信片", "19.9",
                ProductPriceConfig.STATUS_ACTIVE, "tester");
    }

    private AdminRequests.ManualSalesEntryRequest req(int playerId, String code, int qty, String key) {
        AdminRequests.ManualSalesEntryRequest r = new AdminRequests.ManualSalesEntryRequest();
        r.setRoundId(ROUND_ID);
        r.setPlayerId(playerId);
        r.setMerchantCode(code);
        r.setQuantity(qty);
        r.setOperatorId("tester");
        r.setReason("现场统计销量录入");
        r.setIdempotencyKey(key);
        r.setConfirmed(Boolean.TRUE);
        return r;
    }

    private long popularityOf(int playerId) {
        Long v = jdbcTemplate.queryForObject(
                "SELECT individual_popularity FROM player_round_stats WHERE player_id=? AND round_id=?",
                Long.class, playerId, ROUND_ID);
        return v == null ? 0L : v;
    }

    private int ledgerCount() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manual_sales_ledger", Integer.class);
        return n == null ? 0 : n;
    }

    private int logCount(String actionType) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operations_log WHERE action_type = ?", Integer.class, actionType);
        return n == null ? 0 : n;
    }

    // ---------- 一、基本录入与换算 ----------

    @Test
    @DisplayName("正常录入：换算口径与订单导入一致（单价分 × 件数 × 10）")
    void basicEntryUsesSameFormulaAsOrderImport() {
        ManualSalesEntryResult r = manualSalesService.record(req(PLAYER_A, CODE_CARD, 30, "k1"));

        assertEquals(ManualSalesEntryResult.STATUS_RECORDED, r.getStatus());
        // 19.9 元 = 1990 分；1990 × 30 × 10 = 597000
        assertEquals(1990L, r.getUnitPriceCent());
        assertEquals(597000L, r.getPopularityValue());
        assertEquals(30, r.getTotalQuantityAfter());
        assertEquals("林一", r.getPlayerName());
        assertEquals(597000L, popularityOf(PLAYER_A));
        assertEquals(1, ledgerCount());
    }

    @Test
    @DisplayName("录入必须落库并写操作日志，且日志与账本同生共死")
    void entryPersistsLedgerAndLog() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 5, "k2"));

        assertEquals(1, ledgerCount());
        assertEquals(1, logCount("manual_sales_entry"));
        String reason = jdbcTemplate.queryForObject(
                "SELECT reason FROM manual_sales_ledger WHERE idempotency_key = 'ms_k2'", String.class);
        assertEquals("现场统计销量录入", reason);
    }

    @Test
    @DisplayName("单价快照写入账本：事后改价不改变已入账记录的单价")
    void unitPriceIsSnapshotted() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "k3"));
        // 事后把明信片改成 29.9 元
        productPriceService.save(CODE_CARD, "林一明信片", "29.9",
                ProductPriceConfig.STATUS_ACTIVE, "tester");

        Long snapshot = jdbcTemplate.queryForObject(
                "SELECT unit_price_cent FROM manual_sales_ledger WHERE idempotency_key = 'ms_k3'",
                Long.class);
        assertEquals(1990L, snapshot,
                "已入账记录必须保留当时单价，否则事后核对会用新价重算出一个与账面人气不符的数字");
        assertEquals(199000L, popularityOf(PLAYER_A), "人气值不因改价而追溯变化");
    }

    // ---------- 二、幂等与软重复 ----------

    @Test
    @DisplayName("同一幂等键重复提交被拦截，不重复入账")
    void sameIdempotencyKeyIsBlocked() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "same"));
        ManualSalesEntryResult second = manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "same"));

        assertEquals(ManualSalesEntryResult.STATUS_DUPLICATED, second.getStatus());
        assertEquals(1, ledgerCount());
        assertEquals(199000L, popularityOf(PLAYER_A));
    }

    @Test
    @DisplayName("软重复：不同幂等键但内容完全相同，返回需确认且此时未入账")
    void softDuplicateRequiresConfirmAndDoesNotRecord() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "first"));
        long popularityBefore = popularityOf(PLAYER_A);

        AdminRequests.ManualSalesEntryRequest again = req(PLAYER_A, CODE_CARD, 10, "second");
        again.setConfirmed(Boolean.FALSE);
        ManualSalesEntryResult r = manualSalesService.record(again);

        assertEquals(ManualSalesEntryResult.STATUS_NEEDS_CONFIRM, r.getStatus());
        assertNotNull(r.getConfirmReason());
        assertTrue(r.getConfirmReason().contains("秒内已录入过完全相同的一笔"));
        assertEquals(1, ledgerCount(), "需确认时不得落库");
        assertEquals(popularityBefore, popularityOf(PLAYER_A), "需确认时不得改动人气");
    }

    @Test
    @DisplayName("软重复经确认后可正常入账：提示是提示，不是禁止")
    void softDuplicateCanProceedAfterConfirm() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "first"));
        // confirmed 默认为 true，代表运营看清提示后仍确认这是新的一笔
        ManualSalesEntryResult r = manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "second"));

        assertEquals(ManualSalesEntryResult.STATUS_RECORDED, r.getStatus());
        assertEquals(2, ledgerCount());
        assertEquals(398000L, popularityOf(PLAYER_A));
        assertEquals(20, r.getTotalQuantityAfter());
    }

    // ---------- 三、异常量提示（防多打一个零） ----------

    @Test
    @DisplayName("单笔人气超过本轮最高者两倍时要求二次确认，且此时未入账")
    void abnormalAmountRequiresConfirm() {
        // 先给陈二录入一个基准量：19.9 × 10 × 10 = 199000
        manualSalesService.record(req(PLAYER_B, CODE_B_CARD, 10, "base"));

        // 林一录入 100 件写真：5900 × 100 × 10 = 5,900,000，远超基准两倍
        AdminRequests.ManualSalesEntryRequest big = req(PLAYER_A, CODE_PHOTO, 100, "big");
        big.setConfirmed(Boolean.FALSE);
        ManualSalesEntryResult r = manualSalesService.record(big);

        assertEquals(ManualSalesEntryResult.STATUS_NEEDS_CONFIRM, r.getStatus());
        assertTrue(r.getConfirmReason().contains("多打了一位数字"));
        assertEquals(1, ledgerCount(), "需确认时不得落库");
        assertEquals(0L, popularityOf(PLAYER_A));
    }

    @Test
    @DisplayName("本轮首笔录入不触发异常量提示：无参照基准时任何数字都会触发，只会制造噪音")
    void firstEntryOfRoundSkipsAbnormalCheck() {
        AdminRequests.ManualSalesEntryRequest first = req(PLAYER_A, CODE_PHOTO, 100, "first-big");
        first.setConfirmed(Boolean.FALSE);
        ManualSalesEntryResult r = manualSalesService.record(first);

        assertEquals(ManualSalesEntryResult.STATUS_RECORDED, r.getStatus());
        assertEquals(5900000L, r.getPopularityValue());
    }

    // ---------- 四、负数冲销 ----------

    @Test
    @DisplayName("负数冲销正常扣减人气，并以独立日志类型留痕")
    void negativeQuantityReversesPopularity() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 30, "add"));
        ManualSalesEntryResult r = manualSalesService.record(req(PLAYER_A, CODE_CARD, -10, "rev"));

        assertEquals(ManualSalesEntryResult.STATUS_RECORDED, r.getStatus());
        assertEquals(-199000L, r.getPopularityValue());
        assertEquals(20, r.getTotalQuantityAfter());
        assertEquals(398000L, popularityOf(PLAYER_A), "597000 - 199000 = 398000");
        assertEquals(1, logCount("manual_sales_reversal"),
                "冲销须用独立 action_type，否则审计时无法区分「录入」与「纠错」");
    }

    @Test
    @DisplayName("冲销不得使累计件数为负：负销量没有业务含义，通常意味着选错了冲销对象")
    void reversalCannotDriveTotalNegative() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 5, "add5"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manualSalesService.record(req(PLAYER_A, CODE_CARD, -8, "over-rev")));
        assertTrue(ex.getMessage().contains("冲销件数超出已录入总量"));
        assertEquals(1, ledgerCount());
        assertEquals(99500L, popularityOf(PLAYER_A), "1990 × 5 × 10 = 99500，未被改动");
    }

    @Test
    @DisplayName("冲销上限按「选手+商品」独立计算，不跨商品挪用额度")
    void reversalLimitIsPerProductNotPerPlayer() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "card10"));
        manualSalesService.record(req(PLAYER_A, CODE_PHOTO, 2, "photo2"));

        // 明信片有 10 件，但写真只有 2 件；冲销写真 5 件必须被拒，
        // 不能因为「该选手总件数 12 件」就放行。
        assertThrows(IllegalArgumentException.class,
                () -> manualSalesService.record(req(PLAYER_A, CODE_PHOTO, -5, "bad-rev")));
    }

    // ---------- 五、参数校验 ----------

    @Test
    @DisplayName("件数为 0 被拒：既不改变账面也不表达意图，静默接受只会留下无意义空记录")
    void zeroQuantityRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> manualSalesService.record(req(PLAYER_A, CODE_CARD, 0, "zero")));
    }

    @Test
    @DisplayName("未配置原价的商品编码被拒，并给出可操作的提示")
    void unconfiguredMerchantCodeRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manualSalesService.record(req(PLAYER_A, "P21-UNKNOWN", 5, "nocfg")));
        assertTrue(ex.getMessage().contains("未配置原价"));
    }

    @Test
    @DisplayName("已停用的价格配置不可录入：停用意味着该商品不该再产生人气")
    void disabledPriceRejected() {
        productPriceService.save(CODE_CARD, "林一明信片", "19.9",
                ProductPriceConfig.STATUS_DISABLED, "tester");
        assertThrows(IllegalArgumentException.class,
                () -> manualSalesService.record(req(PLAYER_A, CODE_CARD, 5, "disabled")));
    }

    @Test
    @DisplayName("不存在的选手被拒：接口可被直接调用，孤儿人气记录不会报错只会静默污染账本")
    void nonExistentPlayerRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> manualSalesService.record(req(999, CODE_CARD, 5, "ghost")));
    }

    @Test
    @DisplayName("理由与操作人为必填：每一笔人气变更都必须有人具名负责")
    void reasonAndOperatorRequired() {
        AdminRequests.ManualSalesEntryRequest noReason = req(PLAYER_A, CODE_CARD, 5, "nr");
        noReason.setReason("  ");
        assertThrows(IllegalArgumentException.class, () -> manualSalesService.record(noReason));

        AdminRequests.ManualSalesEntryRequest noOperator = req(PLAYER_A, CODE_CARD, 5, "no");
        noOperator.setOperatorId("");
        assertThrows(IllegalArgumentException.class, () -> manualSalesService.record(noOperator));
    }

    @Test
    @DisplayName("幂等键为空被拒：缺了它连点就会重复入账")
    void idempotencyKeyRequired() {
        AdminRequests.ManualSalesEntryRequest r = req(PLAYER_A, CODE_CARD, 5, "x");
        r.setIdempotencyKey(null);
        assertThrows(IllegalArgumentException.class, () -> manualSalesService.record(r));
    }

    // ---------- 六、汇总核对视图 ----------

    @Test
    @DisplayName("汇总两级展开：外层按选手人气合计，内层按商品件数，不跨商品加件数")
    void summaryGroupsByPlayerThenProduct() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 30, "s1"));
        manualSalesService.record(req(PLAYER_A, CODE_PHOTO, 5, "s2"));
        manualSalesService.record(req(PLAYER_B, CODE_B_CARD, 10, "s3"));

        ManualSalesService.ManualSalesSummary summary = manualSalesService.summarize(ROUND_ID);

        assertEquals(2, summary.getPlayers().size());
        ManualSalesService.PlayerGroup a = summary.getPlayers().stream()
                .filter(p -> p.getPlayerId() == PLAYER_A).findFirst().orElseThrow();
        // 明信片 597000 + 写真 5900×5×10=295000 → 892000
        assertEquals(892000L, a.getTotalPopularity());
        assertEquals(2, a.getProducts().size(), "两款商品必须分行，件数不可相加");
        assertEquals("林一", a.getPlayerName());

        List<ManualSalesSummaryItem> products = a.getProducts();
        ManualSalesSummaryItem card = products.stream()
                .filter(p -> CODE_CARD.equals(p.getMerchantCode())).findFirst().orElseThrow();
        assertEquals(30L, card.getTotalQuantity());
        assertEquals(597000L, card.getTotalPopularity());
        assertEquals(1990L, card.getLatestUnitPriceCent());
        assertFalse(card.isPriceInconsistent());

        // 全场合计 = 892000 + 199000
        assertEquals(1091000L, summary.getGrandTotalPopularity());
    }

    @Test
    @DisplayName("汇总反映冲销后净值，而非累计录入量")
    void summaryReflectsNetAfterReversal() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 30, "n1"));
        manualSalesService.record(req(PLAYER_A, CODE_CARD, -10, "n2"));

        ManualSalesService.ManualSalesSummary summary = manualSalesService.summarize(ROUND_ID);
        ManualSalesSummaryItem card = summary.getPlayers().get(0).getProducts().get(0);
        assertEquals(20L, card.getTotalQuantity());
        assertEquals(398000L, card.getTotalPopularity());
        assertEquals(2L, card.getEntryCount(), "笔数应为 2（含冲销那笔），便于审计追溯");
    }

    @Test
    @DisplayName("同商品多笔单价不一致时给出告警：此时件数×单价无法反推人气，核对会对不上")
    void priceChangeMidRoundProducesWarning() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "p1"));
        productPriceService.save(CODE_CARD, "林一明信片", "29.9",
                ProductPriceConfig.STATUS_ACTIVE, "tester");
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "p2"));

        ManualSalesService.ManualSalesSummary summary = manualSalesService.summarize(ROUND_ID);
        ManualSalesSummaryItem card = summary.getPlayers().get(0).getProducts().get(0);

        assertTrue(card.isPriceInconsistent());
        assertEquals(2990L, card.getLatestUnitPriceCent());
        assertEquals(1990L, card.getEarliestUnitPriceCent());
        assertFalse(summary.getWarnings().isEmpty());
        assertTrue(summary.getWarnings().get(0).contains("单价不一致"));
        // 人气仍应为两次快照之和：199000 + 299000
        assertEquals(498000L, popularityOf(PLAYER_A));
    }

    @Test
    @DisplayName("本轮无任何录入时汇总为空而非报错")
    void emptySummaryIsNotAnError() {
        ManualSalesService.ManualSalesSummary summary = manualSalesService.summarize(ROUND_ID);
        assertTrue(summary.getPlayers().isEmpty());
        assertEquals(0L, summary.getGrandTotalPopularity());
    }

    @Test
    @DisplayName("汇总按轮次隔离：其他轮次的录入不得混入本轮合计")
    void summaryIsScopedByRound() {
        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (2, '第二轮', '2026-08-10 20:00:00', '2026-08-10 23:00:00', 'upcoming')
                """);
        jdbcTemplate.update("""
                INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity)
                VALUES (?, 2, 0, 0)
                """, PLAYER_A);

        manualSalesService.record(req(PLAYER_A, CODE_CARD, 30, "r1"));
        AdminRequests.ManualSalesEntryRequest r2 = req(PLAYER_A, CODE_CARD, 7, "r2");
        r2.setRoundId(2);
        manualSalesService.record(r2);

        assertEquals(597000L, manualSalesService.summarize(ROUND_ID).getGrandTotalPopularity());
        assertEquals(139300L, manualSalesService.summarize(2).getGrandTotalPopularity());
    }

    // ---------- 七、与人气引擎的一致性 ----------

    @Test
    @DisplayName("人气必须走唯一入口落 popularity_ledger，且 source 为 manual")
    void popularityGoesThroughSingleEntryPoint() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "src"));

        String source = jdbcTemplate.queryForObject(
                "SELECT source FROM popularity_ledger WHERE idempotency_key = 'ms_src'", String.class);
        assertEquals("manual", source);
        Long value = jdbcTemplate.queryForObject(
                "SELECT popularity_value FROM popularity_ledger WHERE idempotency_key = 'ms_src'",
                Long.class);
        assertEquals(199000L, value, "manual 源不再二次换算，rawValue 即人气值");
    }

    @Test
    @DisplayName("账本笔数与人气流水笔数一一对应：任何一侧多出记录都意味着账实不符")
    void ledgerAndPopularityRecordsMatchOneToOne() {
        manualSalesService.record(req(PLAYER_A, CODE_CARD, 10, "m1"));
        manualSalesService.record(req(PLAYER_A, CODE_PHOTO, 2, "m2"));
        manualSalesService.record(req(PLAYER_A, CODE_CARD, -3, "m3"));

        Integer ledger = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manual_sales_ledger", Integer.class);
        Integer popularity = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popularity_ledger WHERE source = 'manual'", Integer.class);
        assertEquals(ledger, popularity);
        assertEquals(3, ledger);
    }
}
