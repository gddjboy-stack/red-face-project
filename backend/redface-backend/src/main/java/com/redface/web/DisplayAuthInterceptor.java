package com.redface.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redface.api.ApiResponse;
import com.redface.config.DisplayProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * C20-5 大屏展示端鉴权拦截器（Claude 裁定 2.2）。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li><b>fail-closed</b>：{@code redface.display.token} 未配置时，一律 401，不放行。
 *       与 admin 侧的 fail-open 妥协不同——display 侧漏配等于全网公开排行榜，风险性质不同。</li>
 *   <li><b>物理隔离</b>：只认 display 令牌，不接受 {@code X-Admin-Token}；反之 admin 拦截器
 *       也不认 display 令牌。二者无任何交叉。</li>
 *   <li><b>只读</b>：{@code /api/display/**} 下仅允许 GET/HEAD，其他方法直接 405，
 *       从传输层杜绝用展示令牌写数据的可能。</li>
 * </ol>
 *
 * <p>凭证读取顺序为 Cookie {@code RF_DISPLAY} 优先，其次 header {@code X-Display-Token}。
 * 保留 header 通道是为满足裁定要求的「两种模式都能用」：若实测 OBS 浏览器源不持久化 Cookie，
 * 可改用 OBS 自定义请求头注入，无需改动后端。
 */
@Component
public class DisplayAuthInterceptor implements HandlerInterceptor {

    /** 展示令牌 Cookie 名。 */
    public static final String DISPLAY_COOKIE_NAME = "RF_DISPLAY";
    /** 展示令牌请求头名（OBS 自定义请求头模式使用）。 */
    public static final String DISPLAY_TOKEN_HEADER = "X-Display-Token";
    /**
     * 展示端鉴权失败错误码。
     *
     * <p>取 40110 而不是顺序的 40102：40102 已被
     * {@link com.redface.auth.DouyinAuthProvider} 占用为「抖音登录失败」。
     * 若两边共用同一码，现场排障时无法从日志或前端报错区分「大屏令牌错」
     * 与「选手登录挂了」，而这两个故障的处理人和处理方式完全不同。
     */
    public static final int DISPLAY_UNAUTHORIZED_CODE = 40110;
    /** 展示端写操作拒绝错误码。 */
    public static final int DISPLAY_METHOD_NOT_ALLOWED_CODE = 40501;

    private static final Logger log = LoggerFactory.getLogger(DisplayAuthInterceptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DisplayProperties properties;

    public DisplayAuthInterceptor(DisplayProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 约束三：只读。展示端不承载任何写操作。
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            log.warn("拒绝展示端非只读请求：method={} path={}", method, request.getRequestURI());
            writeError(response, HttpStatus.METHOD_NOT_ALLOWED, DISPLAY_METHOD_NOT_ALLOWED_CODE,
                    "展示端为只读接口，不支持该方法");
            return false;
        }

        // 约束一：fail-closed。未配置令牌即全量拒绝。
        String configured = properties.getToken();
        if (!StringUtils.hasText(configured)) {
            log.error("DISPLAY_TOKEN 未配置，拒绝展示端请求 path={}（fail-closed，不放行）",
                    request.getRequestURI());
            writeError(response, HttpStatus.UNAUTHORIZED, DISPLAY_UNAUTHORIZED_CODE,
                    "展示端未启用：服务端未配置展示令牌");
            return false;
        }

        String provided = readCookie(request);
        if (!StringUtils.hasText(provided)) {
            provided = request.getHeader(DISPLAY_TOKEN_HEADER);
        }
        if (StringUtils.hasText(provided) && constantTimeEquals(configured, provided)) {
            return true;
        }

        log.warn("拦截未授权的展示端访问：path={}（缺少或错误的展示凭证）", request.getRequestURI());
        writeError(response, HttpStatus.UNAUTHORIZED, DISPLAY_UNAUTHORIZED_CODE, "缺少或无效的展示凭证");
        return false;
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (DISPLAY_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** 定长比较，避免通过响应时间差侧信道推断令牌。 */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, HttpStatus status, int code, String message)
            throws Exception {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(code, message, null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
