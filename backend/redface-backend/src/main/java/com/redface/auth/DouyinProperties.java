package com.redface.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 抖音登录配置（任务卡 C-AUTH-01）。
 * 取值来自 application-prod.yml 的 douyin.app-id / douyin.app-secret，
 * 由环境变量 DOUYIN_APP_ID / DOUYIN_APP_SECRET 注入，严禁明文入库。
 */
@ConfigurationProperties(prefix = "douyin")
public class DouyinProperties {

    /** 抖音小程序 AppID。 */
    private String appId;

    /** 抖音小程序 AppSecret（敏感，仅服务端使用）。 */
    private String appSecret;

    /** code2session 接口地址，默认官方 v2 地址，可按需覆盖（便于测试打桩）。 */
    private String code2SessionUrl = "https://developer.toutiao.com/api/apps/v2/jscode2session";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getCode2SessionUrl() {
        return code2SessionUrl;
    }

    public void setCode2SessionUrl(String code2SessionUrl) {
        this.code2SessionUrl = code2SessionUrl;
    }
}
