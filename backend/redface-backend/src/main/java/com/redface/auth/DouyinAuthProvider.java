package com.redface.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.redface.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 真实抖音登录提供者（任务卡 C-AUTH-01）。
 *
 * <p>仅在 prod profile 生效；调用抖音 code2session（v2）用 code 换取 openid。
 * 与 MockAuthProvider 实现同一 {@link AuthProvider} 接口，上层 AuthService 无感知。
 *
 * <p>安全要求：
 * <ul>
 *   <li>AppID/AppSecret 来自环境变量注入，绝不硬编码或入库；</li>
 *   <li>session_key 仅服务端可见，绝不返回前端（本实现只取 openid，不外泄 session_key）；</li>
 *   <li>换取失败（err_no!=0 / 网络异常 / openid 为空）一律抛异常，绝不静默放行返回空 openid。</li>
 * </ul>
 *
 * <p>接口契约（官方 v2）：
 * POST https://developer.toutiao.com/api/apps/v2/jscode2session
 * body: {"appid","secret","code"} → resp: {"err_no","err_tips","data":{"openid","session_key",...}}
 */
@Component
@Profile("prod")
public class DouyinAuthProvider implements AuthProvider {

    private static final Logger log = LoggerFactory.getLogger(DouyinAuthProvider.class);

    /** 登录失败业务码，复用 40101 段（鉴权类）。 */
    private static final int LOGIN_FAILED_CODE = 40102;

    private final DouyinProperties properties;
    private final RestClient restClient;

    public DouyinAuthProvider(DouyinProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    @Override
    public String exchangeCodeForOpenid(String code) {
        if (!StringUtils.hasText(code)) {
            throw new ApiException(LOGIN_FAILED_CODE, "登录code不能为空");
        }
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            // 配置缺失属于部署错误，明确暴露而非静默放行。
            throw new IllegalStateException("抖音登录未正确配置：DOUYIN_APP_ID / DOUYIN_APP_SECRET 缺失");
        }

        JsonNode body;
        try {
            body = restClient.post()
                    .uri(properties.getCode2SessionUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "appid", properties.getAppId(),
                            "secret", properties.getAppSecret(),
                            "code", code))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("调用抖音 code2session 失败", e);
            throw new ApiException(LOGIN_FAILED_CODE, "抖音登录服务暂不可用");
        }

        if (body == null) {
            throw new ApiException(LOGIN_FAILED_CODE, "抖音登录返回为空");
        }

        int errNo = body.path("err_no").asInt(-1);
        if (errNo != 0) {
            String tips = body.path("err_tips").asText("");
            // 不记录敏感信息，仅记录错误码与提示。
            log.warn("抖音 code2session 返回错误：err_no={} err_tips={}", errNo, tips);
            throw new ApiException(LOGIN_FAILED_CODE, "抖音登录失败");
        }

        String openid = body.path("data").path("openid").asText("");
        if (!StringUtils.hasText(openid)) {
            throw new ApiException(LOGIN_FAILED_CODE, "抖音登录未返回有效 openid");
        }
        return openid;
    }
}
