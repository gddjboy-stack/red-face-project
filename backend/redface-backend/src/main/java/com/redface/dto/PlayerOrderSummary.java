package com.redface.dto;

/**
 * 按选手汇总核对项（C20-4C）。
 *
 * <p>Claude 立卡时将此视为「第二道防线」：即使运营用覆盖入口绕过了硬阻断，
 * 只要在确认前看一眼本视图，编号配错、件数异常、单价配错这三类错误都会以
 * 不合理的数字暴露出来。因此本 DTO 必须同时给出<b>件数</b>与<b>人气值</b>——
 * 只有人气值时无法判断「金额高是因为卖得多还是单价配错」，两者并列才能自证。
 */
public class PlayerOrderSummary {

    /** 选手编号（商家编码），如 P12 */
    private String merchantCode;
    /** 选手姓名。未归属行归属不到选手，此处为 null */
    private String playerName;
    private Integer playerId;
    /** 有效行数（笔数，非件数） */
    private int validRows;
    /** 有效行合计件数 */
    private int quantity;
    /** 有效行合计人气值 */
    private long popularityValue;
    /** 其中处于售后流程中的笔数（风险敞口来源） */
    private int aftersaleRows;
    /** 其中处于售后流程中的人气值 */
    private long aftersaleExposure;
    /** 单价（分）。同一编码若出现多种单价说明配置期间改过价，前端应提示 */
    private Long unitPriceCent;

    public PlayerOrderSummary() {
    }

    public PlayerOrderSummary(String merchantCode) {
        this.merchantCode = merchantCode;
    }

    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public Integer getPlayerId() { return playerId; }
    public void setPlayerId(Integer playerId) { this.playerId = playerId; }

    public int getValidRows() { return validRows; }
    public void setValidRows(int validRows) { this.validRows = validRows; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public long getPopularityValue() { return popularityValue; }
    public void setPopularityValue(long popularityValue) { this.popularityValue = popularityValue; }

    public int getAftersaleRows() { return aftersaleRows; }
    public void setAftersaleRows(int aftersaleRows) { this.aftersaleRows = aftersaleRows; }

    public long getAftersaleExposure() { return aftersaleExposure; }
    public void setAftersaleExposure(long aftersaleExposure) { this.aftersaleExposure = aftersaleExposure; }

    public Long getUnitPriceCent() { return unitPriceCent; }
    public void setUnitPriceCent(Long unitPriceCent) { this.unitPriceCent = unitPriceCent; }

    /** 累加一行有效订单 */
    public void accumulate(OrderRowParseResult r) {
        this.validRows++;
        this.quantity += r.getQuantity();
        this.popularityValue += r.getPopularityValue();
        if (r.isInAftersale()) {
            this.aftersaleRows++;
            this.aftersaleExposure += r.getPopularityValue();
        }
        if (this.unitPriceCent == null) {
            this.unitPriceCent = r.getUnitPriceCent();
        }
        if (this.playerId == null) {
            this.playerId = r.getPlayerId();
        }
        if (this.playerName == null) {
            this.playerName = r.getPlayerName();
        }
    }
}
