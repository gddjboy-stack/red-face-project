package com.redface.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单导入预览结果（C20-4B 建立，C20-4C 扩展）。
 *
 * <p>预览与实际入账走同一套解析逻辑，只在最后一步「是否写库」上分叉。若两条路径各自实现判定，
 * 运营看到的预览与最终入账结果就可能不一致，而这种不一致在直播现场无法被察觉。
 *
 * <p>C20-4C 新增三组字段，分别服务于三层防护：{@code blockedByUnattributed} 是硬阻断，
 * {@code byPlayerDetail} 是绕过阻断后的第二道核对防线，{@code unknownStatusRows} 是
 * 对「平台新增状态导致静默少算」这一未被阻断风险的显性暴露。
 */
public class OrderImportPreview {

    /** 预览令牌，确认导入时必须回传，防止「看的是 A 文件、导的是 B 文件」 */
    private String previewToken;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int unattributedRows;
    private int duplicateRows;
    private int aftersaleRows;
    /**
     * C20-4C：订单状态不在已知枚举内的行数。这些行已计入 {@code invalidRows}，
     * 此处重复单列的目的是提醒运营：这不是「确定不算」，而是「我们不认识这个状态，可能该算」。
     */
    private int unknownStatusRows;
    private long totalPopularity;
    /** C20-4C：有效行合计件数。与人气值并列展示，便于一眼判断单价是否配错 */
    private int totalQuantity;
    /** 售后中订单折算的人气值，属风险敞口，须单列显示（Claude 裁定） */
    private long aftersaleExposure;
    /** 按选手汇总：选手编号 → 人气值。C20-4B 既有字段，保留以兼容已有调用与测试 */
    private Map<String, Long> byPlayer = new LinkedHashMap<>();
    /**
     * C20-4C 按选手汇总核对视图：含件数、笔数、姓名、单价的完整汇总。
     *
     * <p>这是硬阻断之外的第二道防线：即使运营用覆盖入口绕过了阻断，只要在确认前看一眼本表，
     * 编号配错、件数异常、单价配错这三类错误都会以不合理的数字暴露出来。
     */
    private List<PlayerOrderSummary> byPlayerDetail = new ArrayList<>();
    /**
     * C20-4C 未归属行的子订单号列表。前端逐笔展示供运营勾选覆盖；
     * 后端亦凭此校验覆盖请求中提交的子订单号是否真属于本次预览的未归属行。
     */
    private List<String> unattributedSubOrderNos = new ArrayList<>();
    /**
     * C20-4C 硬阻断标记。为 true 时 {@code /orders/confirm} 将拒绝，
     * 必须改走 {@code /orders/confirm-override} 并逐笔勾选。前端据此禁用普通确认按钮。
     */
    private boolean blockedByUnattributed;
    /** C20-4C 阻断原因文案（面向运营，可直接展示），无阻断时为 null */
    private String blockReason;
    /** 逐行明细，供运营核对 */
    private List<OrderRowParseResult> rows = new ArrayList<>();
    /** 阻断性错误（如缺列），非空时不允许确认导入 */
    private List<String> blockingErrors = new ArrayList<>();
    /** 非阻断告警（如实销金额交叉校验不一致、未配置单价） */
    private List<String> warnings = new ArrayList<>();

    public String getPreviewToken() { return previewToken; }
    public void setPreviewToken(String previewToken) { this.previewToken = previewToken; }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getValidRows() { return validRows; }
    public void setValidRows(int validRows) { this.validRows = validRows; }

    public int getInvalidRows() { return invalidRows; }
    public void setInvalidRows(int invalidRows) { this.invalidRows = invalidRows; }

    public int getUnattributedRows() { return unattributedRows; }
    public void setUnattributedRows(int unattributedRows) { this.unattributedRows = unattributedRows; }

    public int getDuplicateRows() { return duplicateRows; }
    public void setDuplicateRows(int duplicateRows) { this.duplicateRows = duplicateRows; }

    public int getAftersaleRows() { return aftersaleRows; }
    public void setAftersaleRows(int aftersaleRows) { this.aftersaleRows = aftersaleRows; }

    public int getUnknownStatusRows() { return unknownStatusRows; }
    public void setUnknownStatusRows(int unknownStatusRows) {
        this.unknownStatusRows = unknownStatusRows;
    }

    public long getTotalPopularity() { return totalPopularity; }
    public void setTotalPopularity(long totalPopularity) { this.totalPopularity = totalPopularity; }

    public int getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }

    public long getAftersaleExposure() { return aftersaleExposure; }
    public void setAftersaleExposure(long aftersaleExposure) { this.aftersaleExposure = aftersaleExposure; }

    public Map<String, Long> getByPlayer() { return byPlayer; }
    public void setByPlayer(Map<String, Long> byPlayer) { this.byPlayer = byPlayer; }

    public List<PlayerOrderSummary> getByPlayerDetail() { return byPlayerDetail; }
    public void setByPlayerDetail(List<PlayerOrderSummary> byPlayerDetail) {
        this.byPlayerDetail = byPlayerDetail;
    }

    public List<String> getUnattributedSubOrderNos() { return unattributedSubOrderNos; }
    public void setUnattributedSubOrderNos(List<String> unattributedSubOrderNos) {
        this.unattributedSubOrderNos = unattributedSubOrderNos;
    }

    public boolean isBlockedByUnattributed() { return blockedByUnattributed; }
    public void setBlockedByUnattributed(boolean blockedByUnattributed) {
        this.blockedByUnattributed = blockedByUnattributed;
    }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public List<OrderRowParseResult> getRows() { return rows; }
    public void setRows(List<OrderRowParseResult> rows) { this.rows = rows; }

    public List<String> getBlockingErrors() { return blockingErrors; }
    public void setBlockingErrors(List<String> blockingErrors) { this.blockingErrors = blockingErrors; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
