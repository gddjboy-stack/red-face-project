package com.redface.dto;

import java.time.LocalDateTime;

/**
 * C16 会员有效期摘要。用于页面级响应的 additive 字段组。
 */
public class UserMembershipSummary {
    private boolean memberActive;
    private LocalDateTime membershipUntil;
    private int membershipRemainingDays;
    private Integer membershipAddedDays;

    public UserMembershipSummary() {
    }

    public UserMembershipSummary(boolean memberActive,
                                 LocalDateTime membershipUntil,
                                 int membershipRemainingDays,
                                 Integer membershipAddedDays) {
        this.memberActive = memberActive;
        this.membershipUntil = membershipUntil;
        this.membershipRemainingDays = membershipRemainingDays;
        this.membershipAddedDays = membershipAddedDays;
    }

    public static UserMembershipSummary inactive() {
        return new UserMembershipSummary(false, null, 0, null);
    }

    public boolean isMemberActive() { return memberActive; }
    public void setMemberActive(boolean memberActive) { this.memberActive = memberActive; }
    public LocalDateTime getMembershipUntil() { return membershipUntil; }
    public void setMembershipUntil(LocalDateTime membershipUntil) { this.membershipUntil = membershipUntil; }
    public int getMembershipRemainingDays() { return membershipRemainingDays; }
    public void setMembershipRemainingDays(int membershipRemainingDays) { this.membershipRemainingDays = membershipRemainingDays; }
    public Integer getMembershipAddedDays() { return membershipAddedDays; }
    public void setMembershipAddedDays(Integer membershipAddedDays) { this.membershipAddedDays = membershipAddedDays; }
}
