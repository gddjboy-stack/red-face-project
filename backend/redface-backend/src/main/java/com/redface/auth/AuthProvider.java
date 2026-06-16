package com.redface.auth;

/**
 * 登录身份提供者。生产环境可由 DouyinAuthProvider 调用抖音 code2session；
 * C9 彩排和测试阶段使用 MockAuthProvider。
 */
public interface AuthProvider {

    /**
     * 将前端 tt.login code 换取 openid。
     *
     * @param code tt.login 返回的 code
     * @return openid
     */
    String exchangeCodeForOpenid(String code);
}
