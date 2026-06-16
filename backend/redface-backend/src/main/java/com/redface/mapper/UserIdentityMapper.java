package com.redface.mapper;

import com.redface.entity.UserIdentityEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * user_identity 表 Mapper。用于 C9 登录身份映射。
 */
@Mapper
public interface UserIdentityMapper {

    @Select("""
            SELECT user_id AS userId,
                   openid_hash AS openidHash,
                   created_at AS createdAt,
                   last_login_at AS lastLoginAt
            FROM user_identity
            WHERE openid_hash = #{openidHash}
            """)
    UserIdentityEntity findByOpenidHash(@Param("openidHash") String openidHash);

    @Insert("""
            INSERT INTO user_identity (user_id, openid_hash, created_at, last_login_at)
            VALUES (#{userId}, #{openidHash}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insert(@Param("userId") String userId, @Param("openidHash") String openidHash);

    @Update("""
            UPDATE user_identity
            SET last_login_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
            """)
    int touchLastLogin(@Param("userId") String userId);
}
