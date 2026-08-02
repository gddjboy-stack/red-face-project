package com.redface.dto;

/**
 * C20-6 手工销量录入结果。
 *
 * <p>三种终态必须能被前端区分，不可混为一谈：
 * 已入账（{@code recorded}）、幂等拦截（{@code duplicated}）、需二次确认
 * （{@code needsConfirm}）。特别是后两者：幂等拦截意味着「这一笔早已入账，不必重来」，
 * 而需二次确认意味着「这一笔还没入账，等你点确认」。若前端把两者都显示成
 * 「操作已完成」，前者会让运营重复录入，后者会让本该入账的销量凭空消失。
 */
public class ManualSalesEntryResult {

    public static final String STATUS_RECORDED = "recorded";
    public static final String STATUS_DUPLICATED = "duplicated";
    public static final String STATUS_NEEDS_CONFIRM = "needs_confirm";

    private String status;
    private long popularityValue;
    private long unitPriceCent;
    private int quantity;
    private String productName;
    private Integer playerId;
    private String playerName;
    /** 本轮该选手该商品冲销后的累计件数（入账后的值）。 */
    private int totalQuantityAfter;
    /** 需二次确认时的原因说明，直接展示给运营。 */
    private String confirmReason;

    public static ManualSalesEntryResult duplicated() {
        ManualSalesEntryResult r = new ManualSalesEntryResult();
        r.status = STATUS_DUPLICATED;
        return r;
    }

    public static ManualSalesEntryResult needsConfirm(String reason) {
        ManualSalesEntryResult r = new ManualSalesEntryResult();
        r.status = STATUS_NEEDS_CONFIRM;
        r.confirmReason = reason;
        return r;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getPopularityValue() {
        return popularityValue;
    }

    public void setPopularityValue(long popularityValue) {
        this.popularityValue = popularityValue;
    }

    public long getUnitPriceCent() {
        return unitPriceCent;
    }

    public void setUnitPriceCent(long unitPriceCent) {
        this.unitPriceCent = unitPriceCent;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

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

    public int getTotalQuantityAfter() {
        return totalQuantityAfter;
    }

    public void setTotalQuantityAfter(int totalQuantityAfter) {
        this.totalQuantityAfter = totalQuantityAfter;
    }

    public String getConfirmReason() {
        return confirmReason;
    }

    public void setConfirmReason(String confirmReason) {
        this.confirmReason = confirmReason;
    }
}
