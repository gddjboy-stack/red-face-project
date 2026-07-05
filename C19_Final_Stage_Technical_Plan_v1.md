# C19「收尾阶段整改」技术方案 v1

> **日期**：2026-07-05  
> **作者**：Manus AI  
> **面向对象**：Claude (架构/代码审查)  
> **背景**：基于 0705 全功能测试 bug 反馈与 Claude 整理的《收尾总文档》，输出正式编码前的技术方案。  
> **注**：P0-0 域名切换已由运维接手，不在此方案内。

---

## P0 级：阻塞发布的问题

### P0-1 「下载纯文本卡库」报 40101 修复
*   **现状**：`window.open` 无法携带 `X-Admin-Token`，导致被拦截。
*   **方案**：在 `control-admin/src/api/tokens.ts` 中新增 `exportTokens` 方法，使用 `fetch` 携带鉴权头请求 `/api/admin/tokens/export`。获取响应后转为 `Blob`，通过动态创建 `<a>` 标签触发下载。
*   **文件名**：下载文件命名为 `tokens_${batchId}.txt`。

### P0-2 识破投票规则变更
*   **新规则**：按组展示、组内多选、同人限投1次。
*   **后端方案**：
    1.  **数据库**：`suspicion_votes` 表的唯一索引目前是 `(user_id, round_id, suspect_player_id)`，这已经满足了“同一观众对同一选手限投1次”的要求。无需更改 Schema。
    2.  **API 修改**：`SuspicionSubmitRequest` 中的 `suspectPlayerId` 改为 `List<Integer> suspectPlayerIds`。
    3.  **服务层 (`SuspicionService`)**：在事务内遍历 `suspectPlayerIds`，逐个执行 `insertSubmission`。利用数据库的唯一索引防重，如果捕获到 `DuplicateKeyException`，则忽略该记录（表示已投过）或返回明细。为简化，采用“忽略重复，插入新增”策略。
*   **前端方案**：
    1.  **数据结构**：按 `teamId` 对候选人进行分组渲染。
    2.  **交互**：改单选为多选框（Checkbox）。用户勾选多名选手后，点击“确认提交”。已投选手（通过 `status` 接口返回的已投列表比对）置灰并标记“已选择”。

### P0-3 「手动加成」入口与 PK 玩法清理
*   **PK 清理**：移除前后端所有与 PK 相关的文案、接口或死代码（如 `pk_win` 枚举）。
*   **手动加成方案**：
    1.  **后端**：在 `AdminControlController` 新增 `POST /api/admin/adjust-coefficient`。
    2.  **参数**：`operatorId, roundId, targetType (player/team), targetId, delta (整数, ±10代表±0.1), idempotencyKey, reason`。
    3.  **服务层**：调用现有的 `CoefficientService.adjustCoefficient`，同时写入 `operations_log` 和 `idempotency_ledger` 防连点。
    4.  **前端**：在“场控操作”Tab 新增「手动加成」卡片，提供表单与二次确认弹窗。
*   **识破窗口控制**：目前识破窗口是由“场控操作 -> 集赞目标切换 -> 模式选 spy”控制的。这不直观。
    *   **优化**：在“场控操作”增加一个独立的“开启/关闭卧底识破”大按钮，点击后自动发送集赞目标切换请求（开启时设为 `spy` 模式，关闭时设为 `pool` 或上一个模式）。

---

## P1 级：运营体验与正式感

### P1-1 全量中英文文案清理
*   **前端展示**：将 `request:fail network not available` 等网络错误拦截并替换为中文“网络异常，请检查连接”。
*   **后台展示**：选手状态 `normal/free/eliminated` 渲染为“正常/自由人/已淘汰”；卧底 `true/false` 渲染为“是/否”。
*   *(注：数据库枚举值和 API 契约保持英文不变)*

### P1-2 全局改名：真相识破 → 卧底识破
*   **范围**：`control-admin/App.vue`、小程序 `pages/suspicion/index.ttml`、`index.json`（页面标题）、`pages/home/index.ttml` 及相关分享文案。执行全局 `grep` 替换。

### P1-3 选手「序号」与「ID」职责拆分
*   **方案**：**坚决不改主键 `player_id`**。
*   **数据库**：在 `players` 表新增 `display_code VARCHAR(20) NULL COMMENT '选手编号(展示与录入)'`。
*   **后端**：新增选手的接口支持传入 `displayCode`。现有的 `number`（序号）改为后端自动计算（取当前最大 `number` + 1）。
*   **前端**：后台选手管理列表展示“序号(自动)”和“编号(录入)”。

### P1-4 后台人气看板「卧底」Tab 过滤
*   **方案**：在 `C9QueryMapper` 或 `PopularityService` 中，当查询条件为 `spy` 时，增加 `WHERE is_spy = 1` 的过滤条件，仅返回卧底成员。

### P1-5 后台退出登录按钮
*   **方案**：在 `control-admin` 顶部栏增加“退出登录”按钮。点击后清除 `localStorage` 中的 `operatorId` 和 `adminToken`，并刷新页面。

### P1-6 后台补「退款」操作页
*   **方案**：在 `control-admin` 增加“退款管理”Tab。
*   **UI**：输入卡密 + 退款原因 -> 确认弹窗 -> 调用 `/api/admin/refund` -> 提示成功。
*   **规则确认**：现有的 `RefundService` 仅扣减人气、更新 token 状态为 `refunded`，**并未**删除 `user_photo_collection` 和 `user_membership`。符合“第一季退款不回收会员/写真”的规则。

### P1-7 核销页「粘贴卡密」失败降级
*   **方案**：在 `redeem/index.js` 中捕获 `tt.getClipboardData` 的 `fail` 回调。
*   **处理**：如果失败（无论是因为权限被拒还是基础库问题），通过 `tt.showToast` 提示“无法读取剪贴板，请长按输入框手动粘贴”，不阻塞用户后续操作。

---

## P2 级：非阻塞小项

1.  **导出空批次**：`/api/admin/tokens/export` 查不到数据时返回 404 或空字符串，前端捕获并提示。
2.  **生成卡密无写真**：前端 `submitTokenGenerate` 提交前，若 `photoAssetId` 为空，弹窗警告“当前未绑定写真，生成的卡密仅含人气值，是否继续？”，需确认后方可提交。

---

## 提交 Claude 裁定请求

请 Claude 重点裁定以下几点：
1.  **P0-2 多选提交策略**：是否同意采用“忽略重复，插入新增”的宽容策略处理同一观众的多选提交？
2.  **P0-3 识破窗口开关**：是否同意用独立的“开启/关闭”按钮封装底层的 `spy` 模式切换逻辑？
3.  **P1-3 编号拆分**：新增 `display_code` 并让 `number` 后端自增的方案是否符合预期？

等待裁定通过后，我将立即开始编码。
