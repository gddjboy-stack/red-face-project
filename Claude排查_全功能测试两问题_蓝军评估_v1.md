# Claude 全功能测试排查报告：蓝军评估与执行边界建议 v1

> 作者：Manus AI  
> 日期：2026-06-21  
> 状态：评估完成，待 Claude 确认执行边界  
> 依据：对 `main` 分支代码的静态复核

## 一、蓝军评估结论

我已对 Claude 提出的两个问题（登录态自愈缺失、卡密绑定缺口）进行了全仓代码复核。结论是：**Claude 的排查极其精准，指出的两个根因全部命中当前代码盲区。**

### 1. 关于问题一：登录态无效
- **Claude 诊断**：小程序 `auth.js/request.js` 缺少 401 自愈逻辑；正式环境后端会话可能因重部署或切域名而丢失。
- **蓝军复核**：完全属实。`request.js` 目前遇到 401 直接 reject，`ensureLogin` 只看本地 `tt.getStorageSync`，导致一旦后端清库或切域名，小程序就会死锁在旧 token 上永远报错。
- **关于后端会话存储（回复 R2）**：当前 `user_session` 表是 MySQL 实体表，**是持久化的**。本次失效的真正原因是：6/19 运维将域名切到了 `api.jiurongjs.com` 并重新初始化了生产库，导致旧域名的 token 在新库中不存在。

### 2. 关于问题二：卡密绑定与导出缺口
- **Claude 诊断**：卡密绑定逻辑在底层是安全的（A码换A图），但整个项目没有暴露 `generateBatch` 的 Controller，也没有前端操作入口，导致运营无法把上传的写真和码绑起来并导出。
- **蓝军复核**：完全属实。`TokenGeneratorServiceImpl.generateBatch` 目前是个“孤岛”，没有任何 HTTP 接口调用它，也没有任何脚本调用它。运营在后台传了图之后，流程就断了。

---

## 二、执行边界建议（提交 Claude 裁定）

既然问题找准了，我建议立即开启 **C18「全链路闭环修复」**。但在编码前，我需要向 Claude 提出以下边界建议，请 Claude 裁定是否同意：

### 针对 R1：登录态自愈（P0）
- **蓝军方案**：在 `request.js` 拦截 HTTP 401。当发现 401 时，**自动调 `clearLogin()` 并重新 `ensureLogin()`**，然后重放原请求。最多自动重试 1 次，避免死循环。
- **提问 Claude**：是否同意采用“拦截 401 -> 清理本地缓存 -> 重新 login -> 重放请求 1 次”的标准自愈模式？

### 针对 R4-R7：卡密生成与导出（P0）
- **蓝军方案**：
  1. 新增 `POST /api/admin/tokens/generate`，入参 `playerId, photoAssetId, points, count, productSku`。
  2. 新增 `GET /api/admin/tokens/export?batchId=xxx`，返回 CSV 文本流。
  3. 后台前端新增 **“卡密管理”** Tab，提供表单：选选手 -> 联动出该选手 active 写真 -> 填积分和数量 -> 点击生成 -> 弹窗显示下载 CSV 链接。
- **提问 Claude**：是否同意把生成和导出拆分为两个接口？导出格式是否直接返回 `text/csv`（包含 tokenId, points, sku, photoAssetId）最简单可靠？

### 针对 R8-R9：上传无反馈（P1）
- **蓝军方案**：当前上传其实是成功的（只是图片较大或网络慢时没 loading）。修复方案是：在上传期间加全屏 loading；上传成功后，把筛选条件的 `playerId` 自动设为刚上传的选手，并调 `refreshPhotos`，让新图直接出现在列表第一行。
- **提问 Claude**：是否同意用加 loading 和自动切换筛选条件来解决“无反馈”感？

---

## 三、下一步动作

请 John 将本报告（或其 GitHub 链接）转给 Claude。如果 Claude 批准了上述“蓝军方案”与边界，我将立即进入 C18 正式编码，彻底打通发码与核销的真实闭环。
