package com.redface.web;

import com.redface.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 场控后台 Admin 鉴权拦截器（任务卡 C-DEPLOY-01）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>仅拦截 /api/admin/**，与用户登录体系（Authorization: Bearer）完全解耦；</li>
 *   <li>校验请求头 X-Admin-Token 是否等于配置项 redface.admin.token（由环境变量 ADMIN_TOKEN 注入）；</li>
 *   <li>当 redface.admin.token 未配置（空）时放行并打印告警——用于本地/dev/test 联调，避免破坏现有无 token 的测试；</li>
 *   <li>一旦配置了 token（生产），无 token 或 token 不匹配一律返回 401，响应体沿用统一 {code,message,data} 结构。</li>
 * </ul>
 * 这是 fail-safe 到安全态的折中：生产配了 token 即强制鉴权，未配则仅联调放行。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);
    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final int UNAUTHORIZED_CODE = 40101;

    private final String configuredToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminAuthInterceptor(@Value("${redface.admin.token:}") String configuredToken) {
        this.configuredToken = configuredToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 未配置 token：联调放行，但打印告警提醒生产必须配置。
        if (!StringUtils.hasText(configuredToken)) {
            log.warn("ADMIN_TOKEN 未配置，/api/admin{} 放行（仅限非生产联调，生产必须配置 ADMIN_TOKEN）",
                    request.getRequestURI());
            return true;
        }

        String provided = request.getHeader(ADMIN_TOKEN_HEADER);
        if (StringUtils.hasText(provided) && configuredToken.equals(provided)) {
            return true;
        }

        log.warn("拦截未授权的 Admin 访问：path={} 缺少或错误的 {}", request.getRequestURI(), ADMIN_TOKEN_HEADER);
        writeUnauthorized(response);
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(UNAUTHORIZED_CODE, "缺少或无效的管理凭证", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
