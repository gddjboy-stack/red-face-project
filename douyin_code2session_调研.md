# 抖音小程序 code2session 接口调研结论

> 来源：抖音开放平台官方文档（developer.open-douyin.com / developer.toutiao.com）+ 第三方实现交叉验证
> 用途：C-AUTH-01 DouyinAuthProvider 实现依据

## 请求

| 项 | 值 |
|---|---|
| 新版 URL | `https://developer.toutiao.com/api/apps/v2/jscode2session` |
| 旧版 URL（仍可用） | `https://developer.toutiao.com/api/apps/jscode2session` |
| Method | POST |
| Content-Type | application/json |

### 请求体（v2，JSON）

```json
{
  "appid": "小程序 AppID",
  "secret": "小程序 AppSecret",
  "code": "tt.login 返回的 code"
}
```

- `code` 与 `anonymous_code` 二选一；非匿名登录传 `code`。
- code 5 分钟内有效，且只能使用一次。

## 响应（v2，data 嵌套结构）

```json
{
  "err_no": 0,
  "err_tips": "success",
  "data": {
    "session_key": "会话密钥",
    "openid": "用户 openid",
    "anonymous_openid": "匿名 openid",
    "unionid": "unionid（需绑定开放平台才有）"
  }
}
```

- `err_no == 0` 表示成功，否则失败，`err_tips` 为错误描述。
- `session_key` 用于解密，**绝不可返回前端**。

## 关键安全注意

1. AppSecret 是敏感信息，只能在服务端，走环境变量注入，严禁硬编码/入库/返回前端。
2. session_key 不传前端。
3. code 一次性、5 分钟有效，失败需返回明确错误，不得静默放行。

## 对本项目的映射

- `AuthProvider.exchangeCodeForOpenid(code)` 内部：POST v2 接口 → 取 `data.openid` 返回。
- 失败（err_no != 0 / 网络异常 / openid 为空）→ 抛异常，由上层返回明确错误码，绝不返回 mock/空 openid。
- AppID/AppSecret 来自 `application-prod.yml` 的 `douyin.app-id` / `douyin.app-secret`（C-DEPLOY-01 已预留占位）。
