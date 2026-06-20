package com.redface.dto;

/**
 * C14 退款请求（后台场控发起）。
 *
 * <p>退款主键是 token（卡密），由后台客服在阿奇索/抖店核对退款订单后输入对应卡密发起；
 * 不接受 order_id 入参，避免推翻 C12 "前端不上报 oid" 的既定契约。
 */
public class RefundRequest {
    private String token;
    private String operatorId;
    private String reason;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
