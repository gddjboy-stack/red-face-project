package com.redface.entity;

import java.time.LocalDateTime;

/**
 * C16 用户会员有效期聚合态。
 */
public class UserMembershipEntity {
    private String userId;
    private LocalDateTime membershipUntil;
    private String lastTokenId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public LocalDateTime getMembershipUntil() { return membershipUntil; }
    public void setMembershipUntil(LocalDateTime membershipUntil) { this.membershipUntil = membershipUntil; }
    public String getLastTokenId() { return lastTokenId; }
    public void setLastTokenId(String lastTokenId) { this.lastTokenId = lastTokenId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
