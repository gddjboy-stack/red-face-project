package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redface.api.ApiException;
import com.redface.auth.DouyinAuthProvider;
import com.redface.auth.DouyinProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DouyinAuthProvider 单元测试（任务卡 C-AUTH-01）。
 *
 * <p>用本地 HttpServer 打桩抖音 code2session，覆盖：成功换取 openid、
 * err_no!=0 失败、openid 缺失、空 code、配置缺失等场景，验证"失败必抛异常、绝不静默放行"。
 */
class DouyinAuthProviderTest {

    private HttpServer server;
    private String baseUrl;
    private volatile String responseBody;
    private volatile int responseStatus = 200;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jscode2session", exchange -> {
            byte[] resp = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/jscode2session";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private DouyinAuthProvider providerWithConfig(String appId, String appSecret) {
        DouyinProperties props = new DouyinProperties();
        props.setAppId(appId);
        props.setAppSecret(appSecret);
        props.setCode2SessionUrl(baseUrl);
        return new DouyinAuthProvider(props);
    }

    @Test
    void shouldReturnOpenidOnSuccess() {
        responseBody = "{\"err_no\":0,\"err_tips\":\"success\",\"data\":{\"openid\":\"oid_123\",\"session_key\":\"sk\"}}";
        DouyinAuthProvider provider = providerWithConfig("appid", "secret");
        assertThat(provider.exchangeCodeForOpenid("valid_code")).isEqualTo("oid_123");
    }

    @Test
    void shouldThrowWhenErrNoNotZero() {
        responseBody = "{\"err_no\":40029,\"err_tips\":\"invalid code\",\"data\":{}}";
        DouyinAuthProvider provider = providerWithConfig("appid", "secret");
        assertThatThrownBy(() -> provider.exchangeCodeForOpenid("bad_code"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("抖音登录失败");
    }

    @Test
    void shouldThrowWhenOpenidMissing() {
        responseBody = "{\"err_no\":0,\"err_tips\":\"success\",\"data\":{\"session_key\":\"sk\"}}";
        DouyinAuthProvider provider = providerWithConfig("appid", "secret");
        assertThatThrownBy(() -> provider.exchangeCodeForOpenid("code"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("有效 openid");
    }

    @Test
    void shouldThrowWhenCodeBlank() {
        responseBody = "{}";
        DouyinAuthProvider provider = providerWithConfig("appid", "secret");
        assertThatThrownBy(() -> provider.exchangeCodeForOpenid(" "))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void shouldThrowIllegalStateWhenConfigMissing() {
        responseBody = "{}";
        DouyinAuthProvider provider = providerWithConfig("", "");
        assertThatThrownBy(() -> provider.exchangeCodeForOpenid("code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOUYIN_APP_ID");
    }
}
