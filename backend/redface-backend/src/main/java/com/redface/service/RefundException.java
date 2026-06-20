package com.redface.service;

/**
 * C14 退款业务异常，用于把退款失败原因映射为固定的业务错误码。
 *
 * <p>退款涉及"钱"，任何边界失败都必须显式抛出并回滚事务，绝不放过。
 */
public class RefundException extends RuntimeException {
    private final String businessCode;

    public RefundException(String businessCode, String message) {
        super(message);
        this.businessCode = businessCode;
    }

    public String getBusinessCode() {
        return businessCode;
    }
}
