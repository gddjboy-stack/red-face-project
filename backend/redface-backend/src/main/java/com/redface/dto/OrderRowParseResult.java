package com.redface.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 单行订单解析结果。
 *
 * <p>C20-4B：解析器只负责「读懂一行」，不负责落库与入账，便于预览与实际入账复用同一套判定逻辑。
 * 预览与入账必须走同一个解析器，否则运营看到的预览与最终结果可能不一致。
 */
public class OrderRowParseResult {

    /** 有效性：计入人气 */
    public static final String VALIDITY_VALID = "valid";
    /** 有效性：不计入（已退款/未支付/已关闭等） */
    public static final String VALIDITY_INVALID = "invalid";
    /** 有效性：无法归属到选手（商家编码缺失或未配置单价） */
    public static final String VALIDITY_UNATTRIBUTED = "unattributed";

    private int rowNumber;
    private String subOrderNo;
    private String mainOrderNo;
    private String merchantCode;
    private Integer playerId;
    private String playerName;
    private int quantity;
    private Long unitPriceCent;
    private long popularityValue;
    private String orderStatus;
    private String aftersaleStatus;
    private String validity;
    private String invalidReason;
    private boolean inAftersale;
    /**
     * C20-4C：订单状态不在已知枚举内。这类行当前归入 invalid 不计入，
     * 但它与「已关闭/已退款」这种<b>确定无效</b>性质不同——它是「我们不认识这个状态」。
     * 抖店平台新增状态名称时，这类行会静默少算，须单独计数并在预览里醒目提示。
     */
    private boolean unknownOrderStatus;
    private LocalDateTime paidAt;
    private Long payableAmountCent;
    private Map<String, String> rawRow;

    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

    public String getSubOrderNo() { return subOrderNo; }
    public void setSubOrderNo(String subOrderNo) { this.subOrderNo = subOrderNo; }

    public String getMainOrderNo() { return mainOrderNo; }
    public void setMainOrderNo(String mainOrderNo) { this.mainOrderNo = mainOrderNo; }

    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }

    public Integer getPlayerId() { return playerId; }
    public void setPlayerId(Integer playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Long getUnitPriceCent() { return unitPriceCent; }
    public void setUnitPriceCent(Long unitPriceCent) { this.unitPriceCent = unitPriceCent; }

    public long getPopularityValue() { return popularityValue; }
    public void setPopularityValue(long popularityValue) { this.popularityValue = popularityValue; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public String getAftersaleStatus() { return aftersaleStatus; }
    public void setAftersaleStatus(String aftersaleStatus) { this.aftersaleStatus = aftersaleStatus; }

    public String getValidity() { return validity; }
    public void setValidity(String validity) { this.validity = validity; }

    public String getInvalidReason() { return invalidReason; }
    public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }

    public boolean isInAftersale() { return inAftersale; }
    public void setInAftersale(boolean inAftersale) { this.inAftersale = inAftersale; }

    public boolean isUnknownOrderStatus() { return unknownOrderStatus; }
    public void setUnknownOrderStatus(boolean unknownOrderStatus) {
        this.unknownOrderStatus = unknownOrderStatus;
    }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public Long getPayableAmountCent() { return payableAmountCent; }
    public void setPayableAmountCent(Long payableAmountCent) { this.payableAmountCent = payableAmountCent; }

    public Map<String, String> getRawRow() { return rawRow; }
    public void setRawRow(Map<String, String> rawRow) { this.rawRow = rawRow; }
}
