package com.redface.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * C20-5 大屏展示端配置。
 *
 * <p>与场控后台 {@code redface.admin.token} 物理隔离：展示令牌只能读取 {@code /api/display/**}
 * 下的只读接口，无论如何都无法用于 {@code /api/admin/**}。
 *
 * <p>安全基线（Claude 裁定 2.2）：display 侧一律 <b>fail-closed</b>，
 * 即 {@code token} 未配置时拦截器直接拒绝，绝不放行。这与 admin 侧为兼容联调保留的
 * fail-open 行为不同，不得互相套用。
 */
@Component
@ConfigurationProperties(prefix = "redface.display")
public class DisplayProperties {

    /** 展示令牌。生产通过环境变量 DISPLAY_TOKEN 注入；留空即全量拒绝。 */
    private String token = "";

    /** Cookie 是否仅限 HTTPS。生产必须 true，本地 http 联调置 false。 */
    private boolean cookieSecure = false;

    /** Cookie 有效期（秒）。默认 12 小时，覆盖单场直播全程后自然失效。 */
    private int cookieMaxAgeSeconds = 43200;

    /** 同一 IP 连续换票失败次数上限，超过后锁定。 */
    private int maxLoginFailures = 10;

    /** 换票失败锁定时长（秒）。 */
    private int loginLockSeconds = 600;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public int getCookieMaxAgeSeconds() {
        return cookieMaxAgeSeconds;
    }

    public void setCookieMaxAgeSeconds(int cookieMaxAgeSeconds) {
        this.cookieMaxAgeSeconds = cookieMaxAgeSeconds;
    }

    public int getMaxLoginFailures() {
        return maxLoginFailures;
    }

    public void setMaxLoginFailures(int maxLoginFailures) {
        this.maxLoginFailures = maxLoginFailures;
    }

    public int getLoginLockSeconds() {
        return loginLockSeconds;
    }

    public void setLoginLockSeconds(int loginLockSeconds) {
        this.loginLockSeconds = loginLockSeconds;
    }
}
