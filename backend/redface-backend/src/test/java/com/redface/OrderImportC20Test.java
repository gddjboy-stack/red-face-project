package com.redface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redface.dto.OrderImportPreview;
import com.redface.dto.OrderRowParseResult;
import com.redface.entity.ProductPriceConfig;
import com.redface.service.OrderImportService;
import com.redface.service.OrderSheetParser;
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
 * C20-4B 订单表批量导入测试。
 *
 * <p>重点覆盖三类风险：<b>有效性判定</b>（错判直接影响谁赢）、<b>幂等</b>（重复导入等于重复加分）、
 * <b>归属与原价</b>（按 John 决策改为「单价配置 × 件数」后，配置缺失必须拦截而非静默算 0）。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderImportC20Test {

    @Autowired
    private OrderImportService orderImportService;
    @Autowired
    private ProductPriceService productPriceService;
    @Autowired
    private OrderSheetParser parser;
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
        jdbcTemplate.update("""
                INSERT INTO player_round (player_id, round_id, team_id, is_spy, player_status)
                VALUES (12, ?, 1, 0, 'normal')
                """, ROUND_ID);
        jdbcTemplate.update("""
                INSERT INTO player_round (player_id, round_id, team_id, is_spy, player_status)
                VALUES (13, ?, 1, 0, 'normal')
                """, ROUND_ID);
        jdbcTemplate.update("""
                INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity)
                VALUES (12, ?, 0, 0)
                """, ROUND_ID);
        jdbcTemplate.update("""
                INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity)
                VALUES (13, ?, 0, 0)
                """, ROUND_ID);
    }

    /** 标准表头，顺序与抖店导出保持一致。 */
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

    // ================= 一、三行截图样例验收（规格第四节） =================

    @Test
    @DisplayName("三行样例：已关闭+退款成功→无效；已关闭+空→无效；已完成+空→有效计1件")
    void threeRowSampleAcceptance() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        // 第2行：已关闭 + 退款成功 → 无效
        rows.add(row("1001", "P12", "1", "19.9", "已关闭", "退款成功", "2026-08-09 20:01:00"));
        // 第3行：已关闭 + 空值以 EN DASH 呈现 → 无效
        rows.add(row("1002", "P12", "1", "19.9", "已关闭", "\u2013", "2026-08-09 20:02:00"));
        // 第4行：已完成 + 空 → 有效，计 1 件
        rows.add(row("1003", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:03:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);

        assertTrue(preview.getBlockingErrors().isEmpty(),
                "标准表头不应产生阻塞错误：" + preview.getBlockingErrors());
        assertEquals(3, preview.getTotalRows());
        assertEquals(1, preview.getValidRows(), "只有第4行有效");
        assertEquals(2, preview.getInvalidRows(), "第2、3行均无效");
        // 19.9 元 = 1990 分 = 19900 人气值（《人气值换算公式初稿 V1.3》第 2.2 节）
        assertEquals(19900L, preview.getTotalPopularity());
        assertEquals(19900L, preview.getByPlayer().get("P12"));
    }

    @Test
    @DisplayName("售后关闭是有效订单：不可用『售后状态为空』作为有效判据")
    void aftersaleClosedIsStillValid() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("2001", "P12", "1", "19.9", "已完成", "售后关闭", "2026-08-09 20:10:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(1, preview.getValidRows(), "售后关闭意味着售后已了结，订单依然成立");
        assertEquals(19900L, preview.getTotalPopularity());
    }

    @Test
    @DisplayName("售后中按有效计入，但单列风险敞口供运营知悉")
    void aftersaleInProgressCountedButExposed() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("2101", "P12", "1", "19.9", "已发货", "售后中", "2026-08-09 20:11:00"));
        rows.add(row("2102", "P12", "1", "19.9", "已发货", "待商家处理", "2026-08-09 20:12:00"));
        rows.add(row("2103", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:13:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(3, preview.getValidRows());
        assertEquals(2, preview.getAftersaleRows(), "两笔处于售后流程中");
        assertEquals(39800L, preview.getAftersaleExposure(),
                "风险敞口 = 售后中订单已计入的人气值，用于评估最坏情况下要扣多少");
    }

    @Test
    @DisplayName("待支付订单不计入：入账门槛设在支付完成")
    void unpaidOrderNotCounted() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("2201", "P12", "1", "19.9", "待支付", "\u2013", "\u2013"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(0, preview.getValidRows(),
                "下单不付款若能拿分，等于零成本刷榜");
        assertEquals(0L, preview.getTotalPopularity());
    }

    @Test
    @DisplayName("未知订单状态判无效，不猜测加分")
    void unknownOrderStatusIsInvalid() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("2301", "P12", "1", "19.9", "平台介入中", "\u2013", "2026-08-09 20:20:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(0, preview.getValidRows(), "官方后续新增状态时，宁可少算也不猜测");
        assertEquals(1, preview.getInvalidRows());
    }

    // ================= 二、按金额 × 件数换算（John 2026-08-01 决策） =================

    @Test
    @DisplayName("人气值 = 配置原价 × 件数，与订单应付金额无关（不受优惠券运费影响）")
    void popularityFromConfiguredPriceNotPayableAmount() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        // 应付金额只有 9.9（用了 10 元优惠券），但人气按原价 19.9 × 2 件计
        rows.add(row("3001", "P12", "2", "29.8", "已完成", "\u2013", "2026-08-09 20:30:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(39800L, preview.getTotalPopularity(),
                "19.9 元 × 2 件 = 39800 人气值，优惠与运费一律不影响");
    }

    @Test
    @DisplayName("原价解析走字符串切分，避免浮点误差")
    void priceParsingIsExact() {
        ProductPriceConfig saved = productPriceService.save(
                "P13", "定制款", "99.99", ProductPriceConfig.STATUS_ACTIVE, "tester");
        assertEquals(9999L, saved.getUnitPriceCent(), "99.99 元必须精确等于 9999 分");
        assertEquals(99990L, parser.computePopularity(9999L, 1),
                "9999 分 × 10 = 99990 人气值");
    }

    // ================= 三、归属与配置缺失 =================

    @Test
    @DisplayName("商家编码未配置原价：判未归属并给出可执行提示，不静默计 0")
    void missingPriceConfigBlocksAttribution() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4001", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:40:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(0, preview.getValidRows());
        assertEquals(1, preview.getUnattributedRows());
        assertEquals(0L, preview.getTotalPopularity());
        assertFalse(preview.getWarnings().isEmpty(),
                "缺配置必须明确告知运营去补录，否则这批订单会无声消失");
        assertTrue(preview.getWarnings().get(0).contains("P12"));
    }

    @Test
    @DisplayName("商家编码匹配不到选手：判未归属")
    void unknownMerchantCodeUnattributed() {
        configurePrice("P99", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4101", "P99", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:41:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(1, preview.getUnattributedRows());
    }

    @Test
    @DisplayName("价格配置停用后不再计入")
    void disabledPriceConfigNotCounted() {
        productPriceService.save("P12", "明信片", "19.9",
                ProductPriceConfig.STATUS_DISABLED, "tester");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("4201", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 20:42:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(1, preview.getUnattributedRows());
    }

    // ================= 四、表头缺失与空值归一 =================

    @Test
    @DisplayName("必需列缺失时一次性列出全部缺失项，避免运营反复试错")
    void missingRequiredColumnsListedAtOnce() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("子订单编号", "商家编码"));
        rows.add(Arrays.asList("5001", "P12"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertFalse(preview.getBlockingErrors().isEmpty());
        String msg = preview.getBlockingErrors().get(0);
        assertTrue(msg.contains("商品数量"), "应列出缺失的商品数量：" + msg);
        assertTrue(msg.contains("订单状态"), "应列出缺失的订单状态：" + msg);
        assertTrue(msg.contains("售后状态"), "应列出缺失的售后状态：" + msg);
        assertTrue(msg.contains("支付完成时间"), "应列出缺失的支付完成时间：" + msg);
    }

    @Test
    @DisplayName("表头别名可识别：订单号/数量等自定义报表写法")
    void headerAliasesRecognized() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("子订单号", "订单号", "商家编号", "数量",
                "订单状态", "售后状态", "支付时间"));
        rows.add(Arrays.asList("6001", "M6001", "P12", "1",
                "已完成", "\u2013", "2026/08/09 20:50:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertTrue(preview.getBlockingErrors().isEmpty(),
                "自定义报表的别名写法应能识别：" + preview.getBlockingErrors());
        assertEquals(1, preview.getValidRows());
    }

    @Test
    @DisplayName("数量列容忍千分位与全角字符")
    void quantityToleratesSeparators() {
        configurePrice("P12", "1");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("6101", "P12", "1,024", "1024", "已完成", "\u2013", "2026-08-09 20:51:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(1, preview.getValidRows());
        assertEquals(1024L * 100 * 10, preview.getTotalPopularity(),
                "1 元 = 100 分 = 1000 人气值，1024 件 = 1024000");
    }

    // ================= 五、幂等 =================

    @Test
    @DisplayName("同一文件内子订单号重复：仅首行计入，重复行明确标注来源行号")
    void duplicateWithinSameFile() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("7001", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:00:00"));
        rows.add(row("7001", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:00:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        assertEquals(1, preview.getValidRows());
        assertEquals(19900L, preview.getTotalPopularity(), "重复行不得翻倍计分");
        OrderRowParseResult dup = preview.getRows().get(1);
        assertTrue(dup.getInvalidReason().contains("重复"),
                "必须说明与哪一行重复，否则运营无法判断是导出问题还是真有两笔");
    }

    @Test
    @DisplayName("重复导入同一文件：第二次全部跳过，人气值不翻倍")
    void repeatedImportIsIdempotent() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("7101", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:10:00"));

        OrderImportPreview first = orderImportService.preview(rows, ROUND_ID);
        Map<String, Object> r1 = orderImportService.confirm(first.getPreviewToken(), "tester");
        assertEquals(19900L, r1.get("popularityApplied"));

        Long pop1 = jdbcTemplate.queryForObject(
                "SELECT individual_popularity FROM player_round_stats WHERE player_id=12 AND round_id=?",
                Long.class, ROUND_ID);
        assertEquals(19900L, pop1);

        // 第二次导入同一文件
        OrderImportPreview second = orderImportService.preview(rows, ROUND_ID);
        assertEquals(1, second.getDuplicateRows(), "预览阶段就应提示已入账");
        assertEquals(0L, second.getTotalPopularity());
        orderImportService.confirm(second.getPreviewToken(), "tester");

        Long pop2 = jdbcTemplate.queryForObject(
                "SELECT individual_popularity FROM player_round_stats WHERE player_id=12 AND round_id=?",
                Long.class, ROUND_ID);
        assertEquals(19900L, pop2, "重复导入绝不可翻倍加分");
    }

    @Test
    @DisplayName("预览令牌一次性消费：防重复点击确认，也防『看A文件导B文件』")
    void previewTokenSingleUse() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("7201", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:20:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        orderImportService.confirm(preview.getPreviewToken(), "tester");

        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> orderImportService.confirm(preview.getPreviewToken(), "tester"));
        assertTrue(ex.getMessage().contains("已使用") || ex.getMessage().contains("无效"));
    }

    // ================= 六、落库完整性 =================

    @Test
    @DisplayName("无效与未归属行同样落库：赛后要能解释『为什么这笔没算』")
    void invalidRowsArePersistedForAudit() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("8001", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:30:00"));
        rows.add(row("8002", "P12", "1", "19.9", "已关闭", "退款成功", "2026-08-09 21:31:00"));
        rows.add(row("8003", "P77", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:32:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        Map<String, Object> result = orderImportService.confirm(preview.getPreviewToken(), "tester");
        assertEquals(3, result.get("insertedRows"), "三行全部落库，含无效与未归属");

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_sales_ledger", Integer.class);
        assertEquals(3, total);

        Long popularity = jdbcTemplate.queryForObject(
                "SELECT popularity_value FROM order_sales_ledger WHERE sub_order_no='8002'",
                Long.class);
        assertEquals(0L, popularity, "无效行落库但人气值必须为 0");

        assertNotNull(result.get("importBatchId"));
    }

    @Test
    @DisplayName("人气入账写入 popularity_ledger 且 source=order，可追溯子订单号")
    void popularityLedgerTraceable() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("8101", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:40:00"));

        OrderImportPreview preview = orderImportService.preview(rows, ROUND_ID);
        orderImportService.confirm(preview.getPreviewToken(), "tester");

        Map<String, Object> ledger = jdbcTemplate.queryForMap(
                "SELECT source, idempotency_key, metadata FROM popularity_ledger WHERE source='order'");
        assertEquals("order", ledger.get("source"));
        assertEquals("order:8101", ledger.get("idempotency_key"));
        assertTrue(String.valueOf(ledger.get("metadata")).contains("8101"),
                "metadata 必须能追溯到具体子订单，否则选手质疑时无法举证");
    }

    @Test
    @DisplayName("改价只影响此后导入，已入账订单不追溯")
    void priceChangeDoesNotRetroact() {
        configurePrice("P12", "19.9");
        List<List<String>> rows = new ArrayList<>();
        rows.add(header());
        rows.add(row("8201", "P12", "1", "19.9", "已完成", "\u2013", "2026-08-09 21:50:00"));
        OrderImportPreview p1 = orderImportService.preview(rows, ROUND_ID);
        orderImportService.confirm(p1.getPreviewToken(), "tester");

        configurePrice("P12", "99");

        Long recorded = jdbcTemplate.queryForObject(
                "SELECT popularity_value FROM order_sales_ledger WHERE sub_order_no='8201'",
                Long.class);
        assertEquals(19900L, recorded, "改价不得追溯改写已入账记录");

        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operations_log WHERE action_type='product_price_save'",
                Integer.class);
        assertEquals(2, logCount, "每次改价都要留痕，否则事后无法解释人气差异");
    }
}
