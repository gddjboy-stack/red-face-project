package com.redface.dto;

/**
 * C14 退款失败响应附加数据，携带业务码供后台前端按码展示固定文案。
 */
public class RefundErrorData {
    private String businessCode;

    public RefundErrorData() {
    }

    public RefundErrorData(String businessCode) {
        this.businessCode = businessCode;
    }

    public String getBusinessCode() {
        return businessCode;
    }

    public void setBusinessCode(String businessCode) {
        this.businessCode = businessCode;
    }
}
