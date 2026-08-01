package com.redface.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单导入预览结果（C20-4B）。
 *
 * <p>预览与实际入账走同一套解析逻辑，只在最后一步「是否写库」上分叉。若两条路径各自实现判定，
 * 运营看到的预览与最终入账结果就可能不一致，而这种不一致在直播现场无法被察觉。
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
    private long totalPopularity;
    /** 售后中订单折算的人气值，属风险敞口，须单列显示（Claude 裁定） */
    private long aftersaleExposure;
    /** 按选手汇总：选手编号 → 人气值 */
    private Map<String, Long> byPlayer = new LinkedHashMap<>();
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

    public long getTotalPopularity() { return totalPopularity; }
    public void setTotalPopularity(long totalPopularity) { this.totalPopularity = totalPopularity; }

    public long getAftersaleExposure() { return aftersaleExposure; }
    public void setAftersaleExposure(long aftersaleExposure) { this.aftersaleExposure = aftersaleExposure; }

    public Map<String, Long> getByPlayer() { return byPlayer; }
    public void setByPlayer(Map<String, Long> byPlayer) { this.byPlayer = byPlayer; }

    public List<OrderRowParseResult> getRows() { return rows; }
    public void setRows(List<OrderRowParseResult> rows) { this.rows = rows; }

    public List<String> getBlockingErrors() { return blockingErrors; }
    public void setBlockingErrors(List<String> blockingErrors) { this.blockingErrors = blockingErrors; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
