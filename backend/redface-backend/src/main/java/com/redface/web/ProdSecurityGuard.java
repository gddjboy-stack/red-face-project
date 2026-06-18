package com.redface.web;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 生产启动安全护栏（L3 加固，Claude 验收建议项）。
 *
 * <p>仅在 prod profile 生效：若 ADMIN_TOKEN（redface.admin.token）未配置，
 * 则拒绝启动（fail-fast），从代码层面消除"忘配 ADMIN_TOKEN 导致 /api/admin 在生产裸奔"的可能。
 *
 * <p>非 prod（本地/dev/test）不启用此护栏，保持联调便利与现有测试不变。
 */
@Component
@Profile("prod")
public class ProdSecurityGuard {

    private final String adminToken;

    public ProdSecurityGuard(@Value("${redface.admin.token:}") String adminToken) {
        this.adminToken = adminToken;
    }

    @PostConstruct
    public void verify() {
        if (!StringUtils.hasText(adminToken)) {
            throw new IllegalStateException(
                    "生产环境必须配置 ADMIN_TOKEN（redface.admin.token），否则 /api/admin 将无鉴权，拒绝启动。");
        }
    }
}
