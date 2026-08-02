package com.redface.dto;

/**
 * C20-6 手工销量汇总项：一行代表「某选手 + 某商品」在本轮的累计情况。
 *
 * <p>为什么细到商品维度而不只按选手：商家编码规则为「每位选手每款商品一个独立编码」
 * （John 2026-08-02 确认）。若只按选手汇总件数，明信片 30 件与写真 5 件会被加成
 * 「35 件」，这个数字没有业务含义，也无法用来判断单价是否配错——而「件数与人气值
 * 并列展示」的全部价值正在于让运营看出单价配错。故保留商品维度，由上层再聚合出
 * 选手级人气合计。
 *
 * <p>{@code latestUnitPriceCent} 与 {@code earliestUnitPriceCent} 并列返回是为了
 * 暴露一种沉默的风险：同一选手同一商品的多笔录入若单价不一致，说明期间有人改过价，
 * 此时「件数 × 单价」再也无法反推出人气合计，核对会得出对不上的数字。两值不等时
 * 前端须显示告警，而不是任选其一展示。
 */
public class ManualSalesSummaryItem {

    private Integer playerId;
    private String playerName;
    private Integer playerNumber;
    private String merchantCode;
    private String productName;
    private long totalQuantity;
    private long totalPopularity;
    private long entryCount;
    private long latestUnitPriceCent;
    private long earliestUnitPriceCent;

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Integer getPlayerNumber() {
        return playerNumber;
    }

    public void setPlayerNumber(Integer playerNumber) {
        this.playerNumber = playerNumber;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public long getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(long totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public long getTotalPopularity() {
        return totalPopularity;
    }

    public void setTotalPopularity(long totalPopularity) {
        this.totalPopularity = totalPopularity;
    }

    public long getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(long entryCount) {
        this.entryCount = entryCount;
    }

    public long getLatestUnitPriceCent() {
        return latestUnitPriceCent;
    }

    public void setLatestUnitPriceCent(long latestUnitPriceCent) {
        this.latestUnitPriceCent = latestUnitPriceCent;
    }

    public long getEarliestUnitPriceCent() {
        return earliestUnitPriceCent;
    }

    public void setEarliestUnitPriceCent(long earliestUnitPriceCent) {
        this.earliestUnitPriceCent = earliestUnitPriceCent;
    }

    /** 同一选手同一商品的多笔录入单价不一致，说明期间改过价，核对将无法用件数×单价反推人气。 */
    public boolean isPriceInconsistent() {
        return latestUnitPriceCent != earliestUnitPriceCent;
    }
}
