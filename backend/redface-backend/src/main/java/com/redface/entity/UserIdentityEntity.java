package com.redface.entity;

import java.time.LocalDateTime;

/**
 * user_identity 表实体。openid 不明文落库，只保存 hash 与脱敏 userId。
 */
public class UserIdentityEntity {
    private String userId;
    private String openidHash;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOpenidHash() {
        return openidHash;
    }

    public void setOpenidHash(String openidHash) {
        this.openidHash = openidHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
