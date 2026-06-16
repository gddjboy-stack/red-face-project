package com.redface.mapper;

import com.redface.entity.UserSessionEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * user_session 表 Mapper。用于 C9 Bearer token 登录态。
 */
@Mapper
public interface UserSessionMapper {

    @Insert("""
            INSERT INTO user_session (token, user_id, expires_at, created_at, last_seen_at)
            VALUES (#{token}, #{userId}, #{expiresAt}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insert(UserSessionEntity session);

    @Select("""
            SELECT token,
                   user_id AS userId,
                   expires_at AS expiresAt,
                   created_at AS createdAt,
                   last_seen_at AS lastSeenAt
            FROM user_session
            WHERE token = #{token}
              AND expires_at > #{now}
            """)
    UserSessionEntity findValidSession(@Param("token") String token, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE user_session
            SET last_seen_at = CURRENT_TIMESTAMP
            WHERE token = #{token}
            """)
    int touchLastSeen(@Param("token") String token);
}
