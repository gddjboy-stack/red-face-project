package com.redface.service;

/**
 * C13 真相识破业务异常，用于固定错误码映射。
 */
public class SuspicionException extends RuntimeException {
    private final String businessCode;

    public SuspicionException(String businessCode, String message) {
        super(message);
        this.businessCode = businessCode;
    }

    public String getBusinessCode() {
        return businessCode;
    }
}
