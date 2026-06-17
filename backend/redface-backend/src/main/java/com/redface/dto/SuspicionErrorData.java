package com.redface.dto;

/**
 * C13 真相识破错误响应附加数据。
 */
public class SuspicionErrorData {
    private String businessCode;

    public SuspicionErrorData() {
    }

    public SuspicionErrorData(String businessCode) {
        this.businessCode = businessCode;
    }

    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
}
