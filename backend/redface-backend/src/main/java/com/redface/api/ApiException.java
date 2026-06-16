package com.redface.api;

/**
 * API 层业务异常。用于将鉴权、参数和业务错误映射为统一响应 code/message。
 */
public class ApiException extends RuntimeException {
    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
