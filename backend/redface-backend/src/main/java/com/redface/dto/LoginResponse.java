package com.redface.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API-0 登录响应。token 字段经 Claude 裁定批准增加，用于后续 Bearer 鉴权。
 */
public class LoginResponse {
    private final String userId;
    private final boolean isNewUser;
    private final String token;

    public LoginResponse(String userId, boolean isNewUser, String token) {
        this.userId = userId;
        this.isNewUser = isNewUser;
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    @JsonProperty("isNewUser")
    public boolean isNewUser() {
        return isNewUser;
    }

    public String getToken() {
        return token;
    }
}
