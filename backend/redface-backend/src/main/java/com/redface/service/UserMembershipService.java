package com.redface.service;

import com.redface.dto.UserMembershipSummary;
import com.redface.entity.UserMembershipEntity;
import com.redface.mapper.UserMembershipMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C16 会员有效期服务。只做正向叠加与展示，不做撤销、退款或权益回收。
 */
@Service
public class UserMembershipService {
    public static final int MEMBERSHIP_DAYS_PER_REDEEM = 7;

    private final UserMembershipMapper userMembershipMapper;

    public UserMembershipService(UserMembershipMapper userMembershipMapper) {
        this.userMembershipMapper = userMembershipMapper;
    }

    /**
     * 核销成功后为用户正向叠加 7 天会员。
     *
     * <p>调用方应处于核销事务中。本方法也标注事务，确保被单独测试或复用时仍有事务边界。
     */
    @Transactional
    public UserMembershipSummary grantSevenDays(String userId, String tokenId) {
        validateUserId(userId);
        if (!StringUtils.hasText(tokenId)) {
            throw new IllegalArgumentException("tokenId不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        userMembershipMapper.ensureRow(userId, now.minusSeconds(1));
        UserMembershipEntity locked = userMembershipMapper.lockByUserId(userId);
        if (locked == null) {
            throw new IllegalStateException("会员行初始化后未找到用户会员记录");
        }

        LocalDateTime currentUntil = locked.getMembershipUntil();
        LocalDateTime base = currentUntil != null && currentUntil.isAfter(now) ? currentUntil : now;
        LocalDateTime newUntil = base.plusDays(MEMBERSHIP_DAYS_PER_REDEEM);
        int updated = userMembershipMapper.updateMembership(userId, newUntil, tokenId);
        if (updated != 1) {
            throw new IllegalStateException("会员有效期更新失败");
        }
        return buildSummary(newUntil, now, MEMBERSHIP_DAYS_PER_REDEEM);
    }

    /**
     * 读取用户当前会员摘要；无记录或已过期均返回安全字段。
     */
    public UserMembershipSummary getSummary(String userId) {
        validateUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        UserMembershipEntity entity = userMembershipMapper.findByUserId(userId);
        if (entity == null || entity.getMembershipUntil() == null || !entity.getMembershipUntil().isAfter(now)) {
            return UserMembershipSummary.inactive();
        }
        return buildSummary(entity.getMembershipUntil(), now, null);
    }

    private UserMembershipSummary buildSummary(LocalDateTime membershipUntil, LocalDateTime now, Integer addedDays) {
        boolean active = membershipUntil != null && membershipUntil.isAfter(now);
        int remainingDays = active ? ceilDays(now, membershipUntil) : 0;
        return new UserMembershipSummary(active, membershipUntil, remainingDays, addedDays);
    }

    private int ceilDays(LocalDateTime now, LocalDateTime until) {
        long seconds = Math.max(0L, Duration.between(now, until).getSeconds());
        return (int) Math.ceil(seconds / 86400.0d);
    }

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId不能为空");
        }
    }
}
