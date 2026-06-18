package com.redface.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * C9 默认 Mock 登录提供者。仅在非 prod profile 下生效（彩排/dev/test 兑底）。
 * 生产 prod profile 由 DouyinAuthProvider（C-AUTH-01 产出，@Profile("prod")）接管。
 * 警告：Mock 下任意合法 code 均可登录成功，严禁对公众开放。
 */
@Component
@Profile("!prod")
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
