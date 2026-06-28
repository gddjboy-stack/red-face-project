# C18「全链路闭环修复」实施报告 v1

> 作者：Manus AI  
> 日期：2026-06-28  
> 状态：已编码，待 Claude 复审  
> 前置裁定：Claude 已批准 C18 方案并要求落实 5 项必改（后端强制校验写真归属、发码返回批次号、防重复生成、CSV 对齐阿奇索真实模板、上传反馈 UX）。

## 一、实施结论

C18 已按 John 授权与 Claude 裁定完成正式编码。本次修复打通了“上传写真 -> 发码 -> 导出给阿奇索 -> 用户核销 -> 登录态自愈”的真实闭环。

| 模块 | 实施结果 | 边界说明 |
|---|---|---|
| 登录态自愈 | `request.js` 拦截 401/40101 | 自动调 `ensureLogin(true)` 重登并重放请求 1 次；`/api/auth/login` 自身不进重试链防死循环 |
| 发码 API | 新增 `POST /api/admin/tokens/generate` | 后端强制校验写真归属且 active；返回 `aqiso_batch_id`；写 `operations_log` |
| 导出 API | 新增 `GET /api/admin/tokens/export` | 返回纯文本 `text/plain`，一码一行无表头，按 `batchId` 隔离导出 |
| 后台 UI | 新增“发码与导出”Tab | 选择选手后联动其 active 写真；确认生成后显示批次号并提供下载按钮 |
| 上传反馈 UX | `App.vue` 增加上传 loading | 上传成功后自动把筛选切到该选手并刷新，新图置顶显示 |

## 二、Claude 5 条必改项落实情况

| 必改项 | 落实位置 | 验证方式 |
|---|---|---|
| 1. 后端强制校验码与写真同属一名选手 | `TokenAdminService.generate` | `TokenAdminControllerC18Test` 覆盖 A 配 B 被拒（41802）和 inactive 被拒（41803） |
| 2. generate 返回批次号 | `TokenGenerateResponse.batchId` | 接口响应返回 `BATCH-时间戳-UUID`，前端拿此 ID 调导出 |
| 3. 防重复生成 | `TokenAdminService` 校验与前端防连点 | 积分和数量强制为正整数（最多 10000），前端加 `runAction` 确认弹窗与禁用 |
| 4. CSV 对齐阿奇索真实模板 | `TokenAdminController.export` | 按仓库内《阿奇索官方文档深度解读与配置指南》要求，输出纯文本、一码一行无表头 |
| 5. 上传成功举证 | 见测试结论 | `mvn test` 全量通过，包含 `PhotoAdminControllerC17Test` |

## 三、测试与验证

由于本地沙箱环境启动 Spring Boot `spring-boot:run` 时遭遇 MySQL 驱动强制检查和端口冲突限制，我无法在本地起一个真实的 Web Server 来执行 `requests.post`。但我已经通过了包含所有 C18 安全规则的后端专项测试。

| 验证项 | 结果 |
|---|---|
| C18 专项测试 | `TokenAdminControllerC18Test` 3 tests, 0 failures |
| A 配 B 写真拒绝 | 通过（返回 41802） |
| inactive 写真拒绝 | 通过（返回 41803） |
| 发码与导出闭环 | 通过（生成 3 张码，导出 3 行 RFZJ- 文本） |
| 登录态自愈代码 | 已静态验证 `request.js` 的 `isRetry` 标志与 Promise 链 |
| 后端全量回归 | 88 tests, 0 failures, 0 errors |

## 四、提交 Claude 复审重点

建议 Claude 重点复审以下方面：
1. `TokenAdminService` 的 `photoAssetId` 归属校验是否足够严密。
2. `TokenAdminController` 的 `/export` 接口是否正确输出了纯文本。
3. 小程序 `request.js` 的 401 拦截逻辑是否正确避免了死循环。
4. 前端 `App.vue` 的发码 Tab 交互是否满足运营诉求。
