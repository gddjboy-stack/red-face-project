package com.redface.auth;

import com.redface.dto.LoginResponse;
import com.redface.entity.UserIdentityEntity;
import com.redface.entity.UserSessionEntity;
import com.redface.mapper.UserIdentityMapper;
import com.redface.mapper.UserSessionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C9 登录鉴权服务。负责将 code 换为 openid，生成稳定脱敏 userId，并创建 Bearer token 会话。
 */
@Service
public class AuthService {

    private static final int SESSION_DAYS = 30;

    private final AuthProvider authProvider;
    private final UserIdentityMapper userIdentityMapper;
    private final UserSessionMapper userSessionMapper;

    public AuthService(AuthProvider authProvider,
                       UserIdentityMapper userIdentityMapper,
                       UserSessionMapper userSessionMapper) {
        this.authProvider = authProvider;
        this.userIdentityMapper = userIdentityMapper;
        this.userSessionMapper = userSessionMapper;
    }

    /**
     * 登录并创建会话。
     *
     * @param code tt.login 返回的 code
     * @return 登录响应，包含 userId、isNewUser 和 token
     */
    @Transactional
    public LoginResponse login(String code) {
        String openid = authProvider.exchangeCodeForOpenid(code);
        String openidHash = sha256(openid);
        UserIdentityEntity identity = userIdentityMapper.findByOpenidHash(openidHash);
        boolean isNewUser = false;
        String userId;
        if (identity == null) {
            userId = "u_" + openidHash.substring(0, 24);
            userIdentityMapper.insert(userId, openidHash);
            isNewUser = true;
        } else {
            userId = identity.getUserId();
            userIdentityMapper.touchLastLogin(userId);
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        UserSessionEntity session = new UserSessionEntity();
        session.setToken(token);
        session.setUserId(userId);
        session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_DAYS));
        userSessionMapper.insert(session);
        return new LoginResponse(userId, isNewUser, token);
    }

    /**
     * 根据 Bearer token 解析当前用户 ID。
     *
     * @param token Bearer token
     * @return userId；无效或过期时返回 null
     */
    public String resolveUserId(String token) {
        UserSessionEntity session = userSessionMapper.findValidSession(token, LocalDateTime.now());
        if (session == null) {
            return null;
        }
        userSessionMapper.touchLastSeen(token);
        return session.getUserId();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前环境不支持SHA-256", e);
        }
    }
}
