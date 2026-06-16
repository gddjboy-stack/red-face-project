package com.redface.dto;

/**
 * API-3 卡密核销失败响应 data，保留后端字符串业务语义。
 */
public class RedeemErrorData {
    private final String businessCode;
    private final Long remainingSeconds;

    public RedeemErrorData(String businessCode, Long remainingSeconds) {
        this.businessCode = businessCode;
        this.remainingSeconds = remainingSeconds;
    }

    public String getBusinessCode() { return businessCode; }
    public Long getRemainingSeconds() { return remainingSeconds; }
}
