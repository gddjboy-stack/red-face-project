package com.redface.service;

import com.redface.dto.OrderRowParseResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 抖店订单导出表解析器（C20-4B）。
 *
 * <p>规格依据：《C20-4 抖店订单导出表解析字段规格 V1.0》，经 Claude 裁定确认。三条硬约束：
 * <ol>
 *   <li>按<b>表头名称</b>建立列映射，绝不依赖列位置——自定义报表勾选项因人而异，列位置不稳定</li>
 *   <li>幂等键取<b>子订单编号</b>，不取主订单号——商品维度导出时一个主订单对应多个子订单行</li>
 *   <li>有效性判定必须是<b>订单状态 + 售后状态</b>复合条件。官方明确「售后关闭不等于售后成功」，
 *       故不可用「售后状态为空」作为有效判据</li>
 * </ol>
 *
 * <p>「实销金额」列在 John 提供的截图中存在且能完美区分三类订单，但经核对官方 84 字段清单确认
 * 其为二次加工列（财务合并对账表产物），官方导出中不存在。本解析器<b>绝不将其作为主判据</b>，
 * 仅在存在时做交叉校验并告警。
 */
@Component
public class OrderSheetParser {

    /** 1 元 = 1000 人气值 → 1 分 = 10 人气值。来源：《人气值换算公式初稿 V1.3》第 2.2 节 */
    public static final long POPULARITY_PER_CENT = 10L;

    // ===== 表头别名映射：同一语义字段在标准报表/自定义报表中的可能写法 =====
    private static final Map<String, List<String>> HEADER_ALIASES = new LinkedHashMap<>();
    static {
        HEADER_ALIASES.put("subOrderNo", Arrays.asList("子订单编号", "子订单号"));
        HEADER_ALIASES.put("mainOrderNo", Arrays.asList("主订单编号", "主订单号", "订单号", "订单编号"));
        HEADER_ALIASES.put("merchantCode", Arrays.asList("商家编码", "商家编号", "商品编码"));
        HEADER_ALIASES.put("quantity", Arrays.asList("商品数量", "数量", "购买数量"));
        HEADER_ALIASES.put("orderStatus", Arrays.asList("订单状态"));
        HEADER_ALIASES.put("aftersaleStatus", Arrays.asList("售后状态"));
        HEADER_ALIASES.put("paidAt", Arrays.asList("支付完成时间", "支付时间"));
        HEADER_ALIASES.put("payableAmount", Arrays.asList("订单应付金额", "应付金额"));
        // 可选增强列
        HEADER_ALIASES.put("productName", Arrays.asList("选购商品", "商品名称"));
        HEADER_ALIASES.put("actualSales", Arrays.asList("实销金额"));
    }

    /** 缺失即拒绝解析的必需字段 */
    private static final List<String> REQUIRED_KEYS = Arrays.asList(
            "subOrderNo", "merchantCode", "quantity", "orderStatus", "aftersaleStatus", "paidAt");

    // ===== 状态枚举（官方《抖店「订单管理」使用指南》）=====
    /** 订单状态：这两种一律无效 */
    private static final Set<String> ORDER_STATUS_INVALID = new HashSet<>(Arrays.asList("待支付", "已关闭"));
    /** 订单状态：已支付完成，可计入（入账门槛设在支付完成，John 2026-08-01 决策） */
    private static final Set<String> ORDER_STATUS_PAID = new HashSet<>(
            Arrays.asList("待发货", "已发货", "部分发货", "已完成"));
    /** 售后状态：退款成功即无效 */
    private static final Set<String> AFTERSALE_REFUNDED = new HashSet<>(
            Arrays.asList("退款成功", "退货成功"));
    /** 售后状态：进行中，按 Claude 裁定「正常计入有效，但后台单列显示风险敞口」 */
    private static final Set<String> AFTERSALE_IN_PROGRESS = new HashSet<>(
            Arrays.asList("售后中", "待商家处理"));

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
    };

    /**
     * 建立表头到列索引的映射。
     *
     * @param headerRow 表头行
     * @return 语义键 → 列索引
     * @throws IllegalArgumentException 必需字段缺失时抛出，并列出全部缺失项（一次性告知，避免运营反复试错）
     */
    public Map<String, Integer> buildColumnMap(List<String> headerRow) {
        if (headerRow == null || headerRow.isEmpty()) {
            throw new IllegalArgumentException("表头行为空，无法解析。请确认导出文件未损坏，且首行为表头");
        }
        Map<String, Integer> columnMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : HEADER_ALIASES.entrySet()) {
            for (int i = 0; i < headerRow.size(); i++) {
                String cell = normalizeHeader(headerRow.get(i));
                if (cell.isEmpty()) {
                    continue;
                }
                for (String alias : entry.getValue()) {
                    if (cell.equals(alias)) {
                        columnMap.putIfAbsent(entry.getKey(), i);
                        break;
                    }
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            if (!columnMap.containsKey(key)) {
                missing.add(HEADER_ALIASES.get(key).get(0));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("导出文件缺少必需列：" + String.join("、", missing)
                    + "。请确认导出时【维度】选「商品维度」、【报表类型】选「标准报表」");
        }
        return columnMap;
    }

    /**
     * 解析单行。不做落库、不做归属查库，归属与单价由上层服务注入。
     *
     * @param rowNumber  行号（1 基，含表头，用于错误定位）
     * @param row        数据行
     * @param columnMap  列映射
     * @param headerRow  表头（用于原始行快照）
     * @return 解析结果，validity 已判定但 playerId/unitPriceCent/popularityValue 待上层填充
     */
    public OrderRowParseResult parseRow(int rowNumber, List<String> row,
                                        Map<String, Integer> columnMap, List<String> headerRow) {
        OrderRowParseResult r = new OrderRowParseResult();
        r.setRowNumber(rowNumber);
        r.setSubOrderNo(cell(row, columnMap, "subOrderNo"));
        r.setMainOrderNo(cell(row, columnMap, "mainOrderNo"));
        r.setMerchantCode(cell(row, columnMap, "merchantCode"));
        r.setOrderStatus(cell(row, columnMap, "orderStatus"));
        r.setAftersaleStatus(cell(row, columnMap, "aftersaleStatus"));
        r.setQuantity(parseQuantity(cell(row, columnMap, "quantity")));
        r.setPaidAt(parseDateTime(cell(row, columnMap, "paidAt")));
        r.setPayableAmountCent(parseAmountToCent(cell(row, columnMap, "payableAmount")));
        r.setRawRow(buildRawRow(row, headerRow));

        judgeValidity(r);
        return r;
    }

    /**
     * 有效性判定。仅依据订单状态 + 售后状态，不依赖任何加工列。
     */
    private void judgeValidity(OrderRowParseResult r) {
        if (isBlank(r.getSubOrderNo())) {
            r.setValidity(OrderRowParseResult.VALIDITY_INVALID);
            r.setInvalidReason("子订单编号为空，无法建立幂等键");
            return;
        }
        String orderStatus = r.getOrderStatus() == null ? "" : r.getOrderStatus();
        String aftersale = r.getAftersaleStatus() == null ? "" : r.getAftersaleStatus();

        if (ORDER_STATUS_INVALID.contains(orderStatus)) {
            r.setValidity(OrderRowParseResult.VALIDITY_INVALID);
            r.setInvalidReason("订单状态为「" + orderStatus + "」，未支付完成或已关闭");
            return;
        }
        if (!ORDER_STATUS_PAID.contains(orderStatus)) {
            // 未知状态一律不计入。宁可少算也不能凭猜测加分——加错分事后无法证明清白。
            // C20-4C：另打 unknownOrderStatus 标记。「不认识的状态」与「确定无效」必须在预览里区分：
            // 前者意味着可能存在平台新增状态而我们静默少算，后者是预期内的不计入。
            r.setUnknownOrderStatus(true);
            r.setValidity(OrderRowParseResult.VALIDITY_INVALID);
            r.setInvalidReason("订单状态「" + orderStatus + "」不在已知的支付完成状态内，需人工确认");
            return;
        }
        if (r.getPaidAt() == null) {
            // 入账门槛设在支付完成，无支付完成时间即无法确认已付款
            r.setValidity(OrderRowParseResult.VALIDITY_INVALID);
            r.setInvalidReason("支付完成时间为空，无法确认已支付完成");
            return;
        }
        if (AFTERSALE_REFUNDED.contains(aftersale)) {
            r.setValidity(OrderRowParseResult.VALIDITY_INVALID);
            r.setInvalidReason("售后状态为「" + aftersale + "」，款项已退回");
            return;
        }
        if (AFTERSALE_IN_PROGRESS.contains(aftersale)) {
            // Claude 裁定：售后中按有效计入（钱已付货已发，结算时刻它就是有效订单），
            // 若最终退款成功，按结算红线在下一轮扣减。但后台须单列显示风险敞口。
            r.setInAftersale(true);
        }
        if (r.getQuantity() <= 0) {
            r.setValidity(OrderRowParseResult.VALIDITY_INVALID);
            r.setInvalidReason("商品数量为 " + r.getQuantity() + "，无法计算人气");
            return;
        }
        r.setValidity(OrderRowParseResult.VALIDITY_VALID);
    }

    /**
     * 「实销金额」交叉校验。该列非官方字段，仅在存在时校验结论一致性。
     *
     * @return 不一致时返回告警文案，一致或列不存在时返回 null
     */
    public String crossCheckActualSales(List<String> row, Map<String, Integer> columnMap,
                                        OrderRowParseResult r) {
        if (!columnMap.containsKey("actualSales")) {
            return null;
        }
        Long actual = parseAmountToCent(cell(row, columnMap, "actualSales"));
        if (actual == null) {
            return null;
        }
        boolean judgedValid = OrderRowParseResult.VALIDITY_VALID.equals(r.getValidity());
        boolean salesPositive = actual > 0L;
        if (judgedValid != salesPositive) {
            return "第 " + r.getRowNumber() + " 行：实销金额为 " + (actual / 100.0)
                    + " 元，与状态判定结论（" + (judgedValid ? "有效" : "无效") + "）不一致，建议人工复核";
        }
        return null;
    }

    /** 按原价 × 件数计算人气值（John 2026-08-01 决策：按金额、按原价） */
    public long computePopularity(long unitPriceCent, int quantity) {
        return unitPriceCent * quantity * POPULARITY_PER_CENT;
    }

    // ===== 清洗工具 =====

    /**
     * 空值归一化。截图显示空值以 `–`（U+2013 EN DASH）呈现，与 ASCII 连字符不是同一字符，
     * 若只处理 ASCII 会把 EN DASH 当成有效状态值，导致「售后状态 = –」被判为未知状态。
     */
    public String normalizeValue(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim()
                .replace('\u00A0', ' ')   // NBSP
                .replace('\u3000', ' ')   // 全角空格
                .trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.equals("-") || s.equals("\u2013") || s.equals("\u2014") || s.equals("\uFF0D")
                || s.equals("--") || s.equals("/") || s.equals("N/A") || s.equals("null")) {
            return "";
        }
        return s;
    }

    private String normalizeHeader(String raw) {
        String s = normalizeValue(raw);
        return s.replace(" ", "").replace("\ufeff", "");
    }

    /** 数量解析：容忍千分位逗号与全角数字 */
    public int parseQuantity(String raw) {
        String s = toHalfWidthDigits(normalizeValue(raw)).replace(",", "");
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return new BigDecimal(s).setScale(0, RoundingMode.DOWN).intValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 金额解析为「分」，避免浮点误差 */
    public Long parseAmountToCent(String raw) {
        String s = toHalfWidthDigits(normalizeValue(raw))
                .replace(",", "").replace("¥", "").replace("￥", "").replace("元", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s).multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public LocalDateTime parseDateTime(String raw) {
        String s = toHalfWidthDigits(normalizeValue(raw));
        if (s.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }

    /** 全角数字与全角句点转半角 */
    private String toHalfWidthDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '\uFF10' && c <= '\uFF19') {
                sb.append((char) (c - '\uFF10' + '0'));
            } else if (c == '\uFF0E') {
                sb.append('.');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String cell(List<String> row, Map<String, Integer> columnMap, String key) {
        Integer idx = columnMap.get(key);
        if (idx == null || idx >= row.size()) {
            return "";
        }
        return normalizeValue(row.get(idx));
    }

    private Map<String, String> buildRawRow(List<String> row, List<String> headerRow) {
        Map<String, String> raw = new LinkedHashMap<>();
        if (headerRow == null) {
            return raw;
        }
        for (int i = 0; i < headerRow.size() && i < row.size(); i++) {
            String h = normalizeHeader(headerRow.get(i));
            if (!h.isEmpty()) {
                raw.put(h, row.get(i) == null ? "" : row.get(i));
            }
        }
        return raw;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
