package com.redface.api;

/**
 * 未登录或登录态无效异常。
 */
public class UnauthorizedException extends ApiException {
    public static final int UNAUTHORIZED_CODE = 40101;

    public UnauthorizedException(String message) {
        super(UNAUTHORIZED_CODE, message);
    }
}
