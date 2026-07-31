package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.config.DisplayProperties;
import com.redface.dto.DisplayBoardResponse;
import com.redface.dto.GroupVoteSummaryResponse;
import com.redface.query.DisplayBoardService;
import com.redface.web.DisplayAuthInterceptor;
import com.redface.web.DisplayLoginGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C20-5 大屏展示端 API。
 *
 * <p>路径分工：
 * <ul>
 *   <li>{@code POST /api/display-auth/session}：换票端点。<b>不在 {@code /api/display/**} 之下</b>，
 *       因此不被只读拦截器管辖，可以是 POST；同时它自己承担限流与令牌比对。</li>
 *   <li>{@code GET /api/display/**}：只读展示端点，全部由 {@link DisplayAuthInterceptor} 守卫。</li>
 * </ul>
 *
 * <p>把换票端点单独开一个前缀，而不是在拦截器里 exclude 某个子路径，是为了让「
 * {@code /api/display/**} 全部只读」这条规则在路由层面无例外，避免后续有人往 exclude 名单里加东西。
 */
@RestController
public class DisplayController {

    private static final Logger log = LoggerFactory.getLogger(DisplayController.class);

    private final DisplayProperties properties;
    private final DisplayLoginGuard loginGuard;
    private final DisplayBoardService displayBoardService;

    public DisplayController(DisplayProperties properties,
                             DisplayLoginGuard loginGuard,
                             DisplayBoardService displayBoardService) {
        this.properties = properties;
        this.loginGuard = loginGuard;
        this.displayBoardService = displayBoardService;
    }

    /**
     * 换票：页面内输入展示令牌 → 校验通过后下发 HttpOnly Cookie。
     *
     * <p>此后大屏所有轮询请求都靠 Cookie 携带凭证，令牌不再出现在 URL、localStorage
     * 或任何可见位置，因此 OBS 窗口捕获、观众拍屏、截图外传都不会泄露令牌。
     */
    @PostMapping("/api/display-auth/session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSession(
            @RequestBody(required = false) SessionRequest request,
            HttpServletRequest httpRequest) {

        String clientKey = resolveClientKey(httpRequest);

        // fail-closed：未配置展示令牌时，换票端点同样一律拒绝。
        if (!StringUtils.hasText(properties.getToken())) {
            log.error("DISPLAY_TOKEN 未配置，拒绝换票请求（fail-closed）");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(DisplayAuthInterceptor.DISPLAY_UNAUTHORIZED_CODE,
                            "展示端未启用：服务端未配置展示令牌", null));
        }

        if (loginGuard.isLocked(clientKey)) {
            long remaining = loginGuard.remainingSeconds(clientKey);
            log.warn("换票请求被限流：clientKey={} 剩余锁定 {} 秒", clientKey, remaining);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(42901, "尝试次数过多，请 " + remaining + " 秒后重试", null));
        }

        String provided = request == null ? null : request.getToken();
        if (!StringUtils.hasText(provided)
                || !MessageDigest.isEqual(properties.getToken().getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8))) {
            loginGuard.recordFailure(clientKey);
            log.warn("换票失败：clientKey={} 令牌不匹配", clientKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(DisplayAuthInterceptor.DISPLAY_UNAUTHORIZED_CODE,
                            "展示令牌错误", null));
        }

        loginGuard.clear(clientKey);
        ResponseCookie cookie = ResponseCookie.from(DisplayAuthInterceptor.DISPLAY_COOKIE_NAME,
                        properties.getToken())
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/api/display")
                .maxAge(properties.getCookieMaxAgeSeconds())
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(ApiResponse.success(Map.of(
                        "expiresInSeconds", properties.getCookieMaxAgeSeconds())));
    }

    /**
     * 主动登出：清除展示 Cookie。现场换手或撤屏时使用。
     */
    @PostMapping("/api/display-auth/logout")
    public ResponseEntity<ApiResponse<Void>> destroySession(HttpServletResponse httpResponse) {
        ResponseCookie cookie = ResponseCookie.from(DisplayAuthInterceptor.DISPLAY_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/api/display")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(ApiResponse.success(null));
    }

    /**
     * 大屏排行榜（只读）。tab 支持 player / team；roundId 省略时自动取 active 轮次。
     */
    @GetMapping("/api/display/board")
    public ApiResponse<DisplayBoardResponse> getBoard(
            @RequestParam(defaultValue = "player") String tab,
            @RequestParam(defaultValue = "0") int roundId) {
        return ApiResponse.success(displayBoardService.getBoard(tab, roundId));
    }

    /**
     * 大屏群投票汇总（只读）。数据取自独立账本 group_vote_ledger。
     */
    @GetMapping("/api/display/group-vote")
    public ApiResponse<GroupVoteSummaryResponse> getGroupVote(
            @RequestParam(defaultValue = "0") int roundId) {
        return ApiResponse.success(displayBoardService.getGroupVoteSummary(roundId));
    }

    /**
     * 凭证探活。大屏页加载时先调此接口判断 Cookie 是否仍有效，避免直接轮询导致满屏报错。
     */
    @GetMapping("/api/display/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.success(Map.of("ok", true));
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : "unknown";
    }

    /** 换票请求体。 */
    public static class SessionRequest {
        private String token;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
