package com.redface.auth;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * C9 默认 Mock 登录提供者。真实抖音 code2session 等上线前配置 DouyinAuthProvider 后替换。
 */
@Component
public class MockAuthProvider implements AuthProvider {

    @Override
    public String exchangeCodeForOpenid(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code不能为空");
        }
        String normalized = code.trim();
        if (normalized.startsWith("invalid")) {
            throw new IllegalArgumentException("无效登录code");
        }
        return "mock_openid_" + normalized;
    }
}
