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
 * <p>C20-5 追加三项展示端护栏：DISPLAY_TOKEN 未配置、与 ADMIN_TOKEN 相同、
 * 或 Cookie 未启用 Secure，均拒绝启动。需要说明的是展示端拦截器本身已 fail-closed
 * （漏配即全量拒绝，不会裸奔），此处 fail-fast 的目的是让漏配在启动时立刻暴露，
 * 而不是等到开播时大屏白屏。
 *
 * <p>非 prod（本地/dev/test）不启用此护栏，保持联调便利与现有测试不变。
 */
@Component
@Profile("prod")
public class ProdSecurityGuard {

    private final String adminToken;
    private final String displayToken;
    private final boolean displayCookieSecure;

    public ProdSecurityGuard(@Value("${redface.admin.token:}") String adminToken,
                             @Value("${redface.display.token:}") String displayToken,
                             @Value("${redface.display.cookie-secure:false}") boolean displayCookieSecure) {
        this.adminToken = adminToken;
        this.displayToken = displayToken;
        this.displayCookieSecure = displayCookieSecure;
    }

    @PostConstruct
    public void verify() {
        if (!StringUtils.hasText(adminToken)) {
            throw new IllegalStateException(
                    "生产环境必须配置 ADMIN_TOKEN（redface.admin.token），否则 /api/admin 将无鉴权，拒绝启动。");
        }
        if (!StringUtils.hasText(displayToken)) {
            throw new IllegalStateException(
                    "生产环境必须配置 DISPLAY_TOKEN（redface.display.token），否则 /api/display 将全量拒绝导致大屏不可用，拒绝启动。");
        }
        if (adminToken.equals(displayToken)) {
            throw new IllegalStateException(
                    "DISPLAY_TOKEN 不得与 ADMIN_TOKEN 相同：展示令牌需分发给现场执行人员，复用管理令牌等于交出后台写权限，拒绝启动。");
        }
        if (!displayCookieSecure) {
            throw new IllegalStateException(
                    "生产环境必须设置 redface.display.cookie-secure=true，否则展示 Cookie 会在明文信道上传输，拒绝启动。");
        }
    }
}
