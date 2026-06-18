package com.redface.mapper;

import com.redface.entity.UserMembershipEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * C16 用户会员有效期聚合表 Mapper。
 */
@Mapper
public interface UserMembershipMapper {

    /**
     * 确保用户会员行存在。若已存在，则保持原会员有效期不变。
     */
    @Insert("""
            INSERT INTO user_membership (user_id, membership_until, last_token_id, created_at, updated_at)
            VALUES (#{userId}, #{initialUntil}, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """)
    int ensureRow(@Param("userId") String userId, @Param("initialUntil") LocalDateTime initialUntil);

    /**
     * 在当前事务内锁定用户会员行，避免同一用户并发核销丢失 +7 天更新。
     */
    @Select("""
            SELECT user_id AS userId,
                   membership_until AS membershipUntil,
                   last_token_id AS lastTokenId,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM user_membership
            WHERE user_id = #{userId}
            FOR UPDATE
            """)
    UserMembershipEntity lockByUserId(@Param("userId") String userId);

    /**
     * 只读查询用户会员行。
     */
    @Select("""
            SELECT user_id AS userId,
                   membership_until AS membershipUntil,
                   last_token_id AS lastTokenId,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM user_membership
            WHERE user_id = #{userId}
            """)
    UserMembershipEntity findByUserId(@Param("userId") String userId);

    /**
     * 更新会员有效期聚合态。
     */
    @Update("""
            UPDATE user_membership
            SET membership_until = #{membershipUntil},
                last_token_id = #{lastTokenId},
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
            """)
    int updateMembership(@Param("userId") String userId,
                         @Param("membershipUntil") LocalDateTime membershipUntil,
                         @Param("lastTokenId") String lastTokenId);
}
