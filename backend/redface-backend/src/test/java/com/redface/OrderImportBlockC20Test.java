package com.redface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redface.dto.OrderImportPreview;
import com.redface.dto.PlayerOrderSummary;
import com.redface.entity.ProductPriceConfig;
import com.redface.exception.OrderImportBlockedException;
import com.redface.service.OrderImportService;
import com.redface.service.ProductPriceService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * C20-4C 订单导入硬阻断、显式覆盖留痕、按选手汇总核对与前置检查测试。
 *
 * <p>本文件与 {@code OrderImportC20Test} 分开，因为二者验证的对象不同：后者验证
 * <b>算得对不对</b>（有效性判定、换算、幂等），本文件验证<b>拦不拦得住、说不说得清</b>。
 * 混在一起会让「阻断行为变更」的影响面难以从测试列表上看出。
 *
 * <p>核心命题：阻断防的是「无意识丢失」，不是禁止「有意识排除」。因此每一条阻断
 * 都必须配一条对应的「显式确认后可放行」用例，否则阻断会在现场被绕过或被要求撤掉。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderImportBlockC20Test {

    @Autowired
    private OrderImportService orderImportService;
    @Autowired
    private ProductPriceService productPriceService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int ROUND_ID = 1;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_sales_ledger");
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
        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, display_code, status)
                VALUES (12, '选手甲', 12, 'P12', 'active')
                """);
        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, display_code, status)
                VALUES (13, '选手乙', 13, 'P13', 'active')
                """);
        for (int pid : new int[] {12, 13}) {
            jdbcTemplate.update("""
                    INSERT INTO player_round (player_id, round_id, team_id, is_spy, player_status)
                    VALUES (?, ?, 1, 0, 'normal')
                    """, pid, ROUND_ID);
            jdbcTemplate.update("""
                    INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity)
                    VALUES (?, ?, 0, 0)
                    """, pid, ROUND_ID);
        }
    }

    private List<String> header() {
        return Arrays.asList("子订单编号", "主订单编号", "选购商品", "商家编码",
                "商品数量", "订单应付金额", "订单状态", "售后状态", "支付完成时间");
    }

    private List<String> row(String subOrderNo, String code, String qty, String payable,
                             String orderStatus, String aftersale, String paidAt) {
        return Arrays.asList(subOrderNo, "M" + subOrderNo, "明信片", code,
                qty, payable, orderStatus, aftersale, paidAt);
    }

    private void configurePrice(String code, String yuan) {
        productPriceService.save(code, "明信片标准款", yuan, ProductPriceConfig.STATUS_ACTIVE, "tester");
    }

    private int ledgerCount() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_sales_ledger", Integer.class);
        return n == null ? 0 : n;
    }

    private long popularityOf(int playerId) {
        Long v = jdbcTemplate.queryForObject(
                "SELECT individual_popularity FROM player_round_stats WHERE player_id=? AND round_id=?",
                Long.class, playerId, ROUND_ID);
        return v == null ? 0L : v;
    }

    // ================= 一、硬阻断：三种未归属情形 =================

    @Test
    @DisplayName("漏配单价时禁止入账：这是卡片的核心验收要点")
    void missingPriceConfigBlocksConfirm() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("3001", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:01:00"));
        // P13 是真实选手，但没配单价 —— 最危险的一种漏配：选手存在，钱收到了，人气不加
        rows.add(row("3002", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:02:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertTrue(preview.isBlockedByUnattributed(), "漏配单价必须置阻断标记");
        assertEquals(1, preview.getUnattributedRows());
        assertEquals(List.of("3002"), preview.getUnattributedSubOrderNos());
        assertNotNull(preview.getBlockReason(), "阻断必须给出面向运营的原因文案");

        OrderImportBlockedException ex = assertThrows(OrderImportBlockedException.class,
                () -> orderImportService.confirm(preview.getPreviewToken(), "tester"));
        assertEquals(List.of("3002"), ex.getUnattributedSubOrderNos(),
                "异常须携带具体子订单号，否则运营不知道该去查哪一笔");

        assertEquals(0, ledgerCount(), "阻断时不得写入任何明细");
        assertEquals(0L, popularityOf(12), "阻断时连本可有效的那行也不得入账——半批入账更难对账");
    }

    @Test
    @DisplayName("商家编码无匹配选手时禁止入账")
    void unknownMerchantCodeBlocksConfirm() {
        configurePrice("P12", "19.9");
        configurePrice("P99", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("3101", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:05:00"));
        // P99 配了价但没有对应选手：编号印错、或选手退赛未同步
        rows.add(row("3102", "P99", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:06:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertTrue(preview.isBlockedByUnattributed());
        assertThrows(OrderImportBlockedException.class,
                () -> orderImportService.confirm(preview.getPreviewToken(), "tester"));
        assertEquals(0, ledgerCount());
    }

    @Test
    @DisplayName("价格配置已停用时禁止入账：停用不等于免费，须人工判断")
    void disabledPriceConfigBlocksConfirm() {
        configurePrice("P12", "19.9");
        productPriceService.save("P13", "明信片标准款", "19.9",
                ProductPriceConfig.STATUS_DISABLED, "tester");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("3201", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:07:00"));
        rows.add(row("3202", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:08:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertTrue(preview.isBlockedByUnattributed());
        assertThrows(OrderImportBlockedException.class,
                () -> orderImportService.confirm(preview.getPreviewToken(), "tester"));
        assertEquals(0, ledgerCount());
    }

    @Test
    @DisplayName("无未归属行时不阻断：阻断不得干扰正常导入")
    void noUnattributedDoesNotBlock() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("3301", "P12", "2", "39.8", "已完成", "\u2013", "2026-08-09 20:09:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertFalse(preview.isBlockedByUnattributed());
        assertNull(preview.getBlockReason());
        assertTrue(preview.getUnattributedSubOrderNos().isEmpty());

        Map<String, Object> result = orderImportService.confirm(preview.getPreviewToken(), "tester");
        assertEquals(1, result.get("insertedRows"));
        assertEquals(0, result.get("overriddenRows"), "无覆盖时 overriddenRows 须为 0");
        assertEquals(39800L, popularityOf(12));
    }

    @Test
    @DisplayName("重复行与无效行不触发阻断：重复属正常幂等，不该拦住整批")
    void duplicateAndInvalidRowsDoNotBlock() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("3401", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:10:00"));
        // 文件内重复
        rows.add(row("3401", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:10:00"));
        // 确定无效
        rows.add(row("3402", "P12", "1", "19.9", "已关闭", "退款成功", "2026-08-09 20:11:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertFalse(preview.isBlockedByUnattributed(),
                "重复与确定无效不属于『无意识丢失』，阻断范围不应扩大到此");
        assertEquals(0, preview.getUnattributedRows());
        Map<String, Object> result = orderImportService.confirm(preview.getPreviewToken(), "tester");
        // 文件内重复的那行子订单号相同，被数据库唯一键拦下而计入 skippedRows。
        // 这是比「强写两条」更安全的行为：库里一个子订单号只应存在一行，
        // 否则赛后按子订单号反查会得到两条矛盾记录。重复信息已在预览阶段告知。
        assertEquals(2, result.get("insertedRows"), "重复子订单号被库层幂等拦下");
        assertEquals(1, result.get("skippedRows"), "被拦下的重复行计入 skippedRows，不得静默消失");
        assertEquals(19900L, popularityOf(12), "重复行不得重复加分");
    }

    @Test
    @DisplayName("阻断不消费预览令牌：补齐配置后仍可重新走流程，避免误判『已导过』")
    void blockDoesNotConsumeToken() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("3501", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:12:00"));
        rows.add(row("3502", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:13:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertThrows(OrderImportBlockedException.class,
                () -> orderImportService.confirm(preview.getPreviewToken(), "tester"));
        // 第二次仍应得到阻断而非「令牌已失效」：令牌被吞会让运营以为已经导入过一次
        OrderImportBlockedException again = assertThrows(OrderImportBlockedException.class,
                () -> orderImportService.confirm(preview.getPreviewToken(), "tester"));
        assertFalse(again.getMessage().contains("已使用"),
                "阻断不是失败，不应表现为令牌失效");
        assertEquals(0, ledgerCount());
    }

    // ================= 二、显式覆盖与留痕 =================

    @Test
    @DisplayName("逐笔确认后可放行，且必须写 operations_log 记录谁放行了哪些子订单与原因")
    void overrideConfirmWritesAuditLog() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4001", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:20:00"));
        rows.add(row("4002", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:21:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        Map<String, Object> result = orderImportService.confirmWithOverride(
                preview.getPreviewToken(), "operator-john",
                List.of("4002"), "P13 周边未参加本场活动，经 Vincent 确认不计入");

        assertEquals(2, result.get("insertedRows"), "两行均须落库，被排除的行也要有记录");
        assertEquals(1, result.get("overriddenRows"));
        assertEquals(19900L, result.get("popularityApplied"), "被排除的行不得计入人气");
        assertEquals(19900L, popularityOf(12));
        assertEquals(0L, popularityOf(13), "P13 被显式排除，人气必须为 0");

        Map<String, Object> log = jdbcTemplate.queryForMap("""
                SELECT operator_id, action_type, target, detail, reason
                  FROM operations_log WHERE action_type=?
                """, OrderImportService.ACTION_IMPORT_OVERRIDE);
        assertEquals("operator-john", log.get("operator_id"), "必须记得住是谁放行的");
        assertTrue(String.valueOf(log.get("detail")).contains("4002"),
                "日志须列出被放行的具体子订单号，否则赛后无法举证");
        assertTrue(String.valueOf(log.get("reason")).contains("Vincent"),
                "原因原文须入库，不得被改写或截断成空");
        assertEquals(result.get("importBatchId"), log.get("target"),
                "日志须与导入批次关联，才能从批次反查放行记录");
    }

    @Test
    @DisplayName("被排除的行落库时人气值为 0 且保留未归属原因：赛后要能回答『为什么这笔没算』")
    void overriddenRowKeepsReason() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4101", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:25:00"));
        rows.add(row("4102", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:26:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        orderImportService.confirmWithOverride(preview.getPreviewToken(), "tester",
                List.of("4102"), "确认无需计入");

        Map<String, Object> ledgerRow = jdbcTemplate.queryForMap("""
                SELECT validity, popularity_value, invalid_reason
                  FROM order_sales_ledger WHERE sub_order_no='4102'
                """);
        assertEquals("unattributed", ledgerRow.get("validity"));
        assertEquals(0L, ((Number) ledgerRow.get("popularity_value")).longValue());
        assertTrue(String.valueOf(ledgerRow.get("invalid_reason")).contains("原价"),
                "须保留具体未归属原因，『未归属』三个字本身解释不了任何事");
    }

    @Test
    @DisplayName("未填原因拒绝放行：无原因的放行在赛后等同于没有记录")
    void overrideWithoutReasonRejected() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4201", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:30:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        for (String blank : new String[] {null, "", "   "}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderImportService.confirmWithOverride(
                            preview.getPreviewToken(), "tester", List.of("4201"), blank));
            assertTrue(ex.getMessage().contains("原因"));
        }
        assertEquals(0, ledgerCount(), "校验失败不得留下半批数据");
        // 注：不能统计 operations_log 全表——configurePrice 自身会写一条
        // product_price_save 日志。只能按放行动作类型过滤，否则断言在测别的事。
        Integer logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operations_log WHERE action_type=?",
                Integer.class, OrderImportService.ACTION_IMPORT_OVERRIDE);
        assertEquals(0, logs, "拒绝放行时不应写放行日志");
    }

    @Test
    @DisplayName("只勾选部分未归属订单不予放行：否则未勾选的会被无声排除")
    void partialOverrideRejected() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4301", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:35:00"));
        rows.add(row("4302", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:36:00"));
        rows.add(row("4303", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:37:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(3, preview.getUnattributedRows());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderImportService.confirmWithOverride(preview.getPreviewToken(), "tester",
                        List.of("4301"), "只勾了一笔"));
        assertTrue(ex.getMessage().contains("4302") || ex.getMessage().contains("未勾选"));
        assertEquals(0, ledgerCount());

        // 空列表同样拒绝：这是「点了确认但什么都没勾」的情形
        assertThrows(IllegalArgumentException.class,
                () -> orderImportService.confirmWithOverride(preview.getPreviewToken(), "tester",
                        List.of(), "什么都没勾"));
        assertThrows(IllegalArgumentException.class,
                () -> orderImportService.confirmWithOverride(preview.getPreviewToken(), "tester",
                        null, "传了 null"));
        assertEquals(0, ledgerCount());
    }

    @Test
    @DisplayName("提交不属于本次预览的子订单号予以拒绝：防凭旧页面提交")
    void overrideWithForeignSubOrderRejected() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4401", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:40:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderImportService.confirmWithOverride(preview.getPreviewToken(), "tester",
                        List.of("4401", "9999"), "多提交了一笔不存在的"));
        assertTrue(ex.getMessage().contains("9999"));
        assertEquals(0, ledgerCount());
    }

    @Test
    @DisplayName("无未归属行时不得使用排除入口：避免把它当成万能确认按钮")
    void overrideOnCleanPreviewRejected() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4501", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:45:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderImportService.confirmWithOverride(preview.getPreviewToken(), "tester",
                        List.of("4501"), "本不该走这个入口"));
        assertTrue(ex.getMessage().contains("无需") || ex.getMessage().contains("普通确认"));
        assertEquals(0, ledgerCount(), "拒绝后原令牌应仍可走普通确认");

        Map<String, Object> result = orderImportService.confirm(preview.getPreviewToken(), "tester");
        assertEquals(1, result.get("insertedRows"));
    }

    @Test
    @DisplayName("覆盖放行的令牌一次性消费：防重复点击造成重复批次")
    void overrideTokenSingleUse() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4601", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:50:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        orderImportService.confirmWithOverride(preview.getPreviewToken(), "tester",
                List.of("4601"), "确认排除");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderImportService.confirmWithOverride(preview.getPreviewToken(), "tester",
                        List.of("4601"), "再点一次"));
        assertTrue(ex.getMessage().contains("已使用") || ex.getMessage().contains("无效"));
        Integer logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operations_log WHERE action_type=?",
                Integer.class, OrderImportService.ACTION_IMPORT_OVERRIDE);
        assertEquals(1, logs, "重复点击不得产生第二条放行日志");
    }

    // ================= 三、按选手汇总核对视图 =================

    @Test
    @DisplayName("汇总视图给出件数与人气值：只有人气值时无法区分『卖得多』与『单价配错』")
    void byPlayerDetailReportsQuantityAndPopularity() {
        configurePrice("P12", "19.9");
        configurePrice("P13", "29.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("5001", "P12", "2", "39.8", "已完成", "\u2013", "2026-08-09 21:00:00"));
        rows.add(row("5002", "P12", "3", "59.7", "已完成", "\u2013", "2026-08-09 21:01:00"));
        rows.add(row("5003", "P13", "1", "29.9", "已完成", "\u2013", "2026-08-09 21:02:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(2, preview.getByPlayerDetail().size());
        assertEquals(6, preview.getTotalQuantity(), "合计件数 2+3+1");

        PlayerOrderSummary p12 = preview.getByPlayerDetail().stream()
                .filter(s -> "P12".equals(s.getMerchantCode())).findFirst().orElseThrow();
        assertEquals("选手甲", p12.getPlayerName(),
                "汇总须显示姓名：只给编号时运营要在脑内翻译，而编号配错时数字看起来完全正常");
        assertEquals(12, p12.getPlayerId());
        assertEquals(2, p12.getValidRows(), "笔数为 2");
        assertEquals(5, p12.getQuantity(), "件数为 5，与笔数必须区分");
        assertEquals(99500L, p12.getPopularityValue(), "1990 分 × 5 件 × 10");
        assertEquals(1990L, p12.getUnitPriceCent());

        PlayerOrderSummary p13 = preview.getByPlayerDetail().stream()
                .filter(s -> "P13".equals(s.getMerchantCode())).findFirst().orElseThrow();
        assertEquals("选手乙", p13.getPlayerName());
        assertEquals(1, p13.getQuantity());
        assertEquals(29900L, p13.getPopularityValue());
    }

    @Test
    @DisplayName("汇总视图与逐行合计一致，且与实际入账人气一致：三者不一致说明汇总口径有误")
    void byPlayerDetailMatchesActualLedger() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("5101", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:05:00"));
        rows.add(row("5102", "P12", "4", "79.6", "已完成", "\u2013", "2026-08-09 21:06:00"));
        rows.add(row("5103", "P12", "1", "19.9", "已关闭", "退款成功", "2026-08-09 21:07:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        long detailSum = preview.getByPlayerDetail().stream()
                .mapToLong(PlayerOrderSummary::getPopularityValue).sum();
        assertEquals(preview.getTotalPopularity(), detailSum,
                "汇总合计必须等于总计，否则运营核对的是另一个数");

        orderImportService.confirm(preview.getPreviewToken(), "tester");
        assertEquals(detailSum, popularityOf(12),
                "预览汇总必须等于实际入账，否则运营核对通过但结果不同");
    }

    @Test
    @DisplayName("汇总视图单列售后敞口：售后中订单可能退款，须与稳定人气区分")
    void byPlayerDetailReportsAftersaleExposure() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("5201", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:10:00"));
        // 「售后中」是 OrderSheetParser 中 AFTERSALE_IN_PROGRESS 的枚举值（另一个是「待商家处理」）。
        // 这里不能自创「退款中」：它不在枚举内，会被当成普通有效行而测不出敮口。
        rows.add(row("5202", "P12", "1", "19.9", "已完成", "售后中", "2026-08-09 21:11:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        PlayerOrderSummary p12 = preview.getByPlayerDetail().get(0);
        assertEquals(2, p12.getValidRows());
        assertEquals(39800L, p12.getPopularityValue());
        assertEquals(1, p12.getAftersaleRows());
        assertEquals(19900L, p12.getAftersaleExposure(),
                "退款中的那笔须单列为敞口，运营才知道有多少人气可能被退掉");
    }

    @Test
    @DisplayName("未归属行不进入汇总视图：汇总只反映『将要计入』的数字")
    void unattributedNotInSummary() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("5301", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:15:00"));
        rows.add(row("5302", "P13", "9", "179.1", "已完成", "\u2013", "2026-08-09 21:16:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(1, preview.getByPlayerDetail().size(), "只有 P12 进入汇总");
        assertEquals("P12", preview.getByPlayerDetail().get(0).getMerchantCode());
        assertEquals(1, preview.getTotalQuantity(),
                "未归属的 9 件不得计入合计件数，否则运营会以为它们算了");
    }

    // ================= 四、前置检查 =================

    @Test
    @DisplayName("前置检查不落库、不产生令牌：赛前空跑不得留下可误点的确认入口")
    void preflightWritesNothing() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("6001", "P12", "2", "39.8", "已完成", "\u2013", "2026-08-09 21:20:00"));

        OrderImportPreview pre = orderImportService.preflight(rows, ROUND_ID);
        assertNull(pre.getPreviewToken(), "前置检查不得产生令牌");
        assertEquals(1, pre.getValidRows());
        assertEquals(39800L, pre.getTotalPopularity());
        assertEquals(2, pre.getTotalQuantity());

        assertEquals(0, ledgerCount(), "前置检查不得写入订单明细");
        Integer pop = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popularity_ledger", Integer.class);
        assertEquals(0, pop, "前置检查不得写入人气流水");
        assertEquals(0L, popularityOf(12));
        // 同上：按导入相关动作类型过滤，避开 configurePrice 写入的配价日志。
        Integer logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operations_log WHERE action_type LIKE 'order_import%'",
                Integer.class);
        assertEquals(0, logs, "前置检查不得写导入相关操作日志");
    }

    @Test
    @DisplayName("前置检查同样报出漏配与阻断：空跑的意义就在于提前发现这些")
    void preflightReportsBlocking() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("6101", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:25:00"));
        rows.add(row("6102", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:26:00"));

        OrderImportPreview pre = orderImportService.preflight(rows, ROUND_ID);
        assertTrue(pre.isBlockedByUnattributed(), "空跑须给出与正式导入相同的阻断结论");
        assertEquals(List.of("6102"), pre.getUnattributedSubOrderNos());
        assertTrue(pre.getWarnings().stream().anyMatch(w -> w.contains("P13")),
                "须点名具体编码，运营才知道去补哪一条配置");
        assertEquals(0, ledgerCount());
    }

    @Test
    @DisplayName("前置检查结论与正式预览一致：否则『空跑通过、正式被拦』无法在赛前发现")
    void preflightConsistentWithPreview() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("6201", "P12", "3", "59.7", "已完成", "\u2013", "2026-08-09 21:30:00"));
        rows.add(row("6202", "P13", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:31:00"));
        rows.add(row("6203", "P12", "1", "19.9", "已关闭", "退款成功", "2026-08-09 21:32:00"));

        OrderImportPreview pre = orderImportService.preflight(rows, ROUND_ID);
        OrderImportPreview pv = orderImportService.preview(rows, ROUND_ID);

        assertEquals(pre.getTotalRows(), pv.getTotalRows());
        assertEquals(pre.getValidRows(), pv.getValidRows());
        assertEquals(pre.getInvalidRows(), pv.getInvalidRows());
        assertEquals(pre.getUnattributedRows(), pv.getUnattributedRows());
        assertEquals(pre.getTotalPopularity(), pv.getTotalPopularity());
        assertEquals(pre.getTotalQuantity(), pv.getTotalQuantity());
        assertEquals(pre.isBlockedByUnattributed(), pv.isBlockedByUnattributed());
        assertEquals(pre.getUnattributedSubOrderNos(), pv.getUnattributedSubOrderNos());
        assertEquals(pre.getByPlayerDetail().size(), pv.getByPlayerDetail().size());
    }

    @Test
    @DisplayName("前置检查如实反映已入账查重：不得让运营误以为这些行还会计入")
    void preflightReportsAlreadyImported() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("6301", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:35:00"));

        OrderImportPreview first = orderImportService.preview(rows, ROUND_ID);
        orderImportService.confirm(first.getPreviewToken(), "tester");

        OrderImportPreview pre = orderImportService.preflight(rows, ROUND_ID);
        assertEquals(1, pre.getDuplicateRows(),
                "同一份文件二次空跑须显示已入账，隐藏查重会让运营误判");
        assertEquals(0, pre.getValidRows());
        assertEquals(0L, pre.getTotalPopularity());
    }

    // ================= 五、未知订单状态的显性暴露 =================

    @Test
    @DisplayName("未知订单状态单列计数并告警：平台改状态名时不能静默少算")
    void unknownOrderStatusIsSurfaced() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("7001", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:40:00"));
        rows.add(row("7002", "P12", "1", "19.9", "平台介入中", "\u2013", "2026-08-09 21:41:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(1, preview.getUnknownStatusRows());
        assertEquals(1, preview.getInvalidRows(), "未知状态仍计入无效行总数，不重复统计口径");
        assertTrue(preview.getWarnings().stream().anyMatch(w -> w.contains("平台介入中")),
                "告警须列出具体状态名，否则无法判断是否该补录");
        // 说明当前边界：未知状态未被纳入硬阻断范围（卡片限定只阻断未归属行）。
        // 该断言是对现状的如实记录，若 Claude 裁定升级为阻断，此处须同步修改。
        assertFalse(preview.isBlockedByUnattributed(),
                "当前未知状态不触发阻断——这是已知漏口，等待裁定");
    }
}
