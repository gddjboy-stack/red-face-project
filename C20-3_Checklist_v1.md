# C20-3 逐项完成对照表 v1

**执行人**：Manus　**日期**：2026-07-29　**分支**：`main`
**卡片来源**：《Claude 验收：C20-2 通过 → 蓝军裁定 + 下发 C20-3》（已存档 `collaboration/Claude验收_C20-2通过_下发C20-3.md`）

---

## 〇、本次提交同时包含的其他事项（先声明，非静默）

| 事项 | 说明 |
|---|---|
| C20-6 首页副标题修正 | 微型卡已先行完成并推送：main `fffc6ec` + release `a44d163`，两分支代码内 LOVAL 零命中（`LOVAL LEVEL` → `Beauty Game`）。不在本 commit 内，此处仅备案 |
| share.js 附带进 main | 即 Claude 备注②裁定的附带项，详见第三节 |
| 测试基础设施 | 沙箱补装 JDK17 + Maven 3.8.7 用于编译与测试，不产生代码改动 |

---

## 一、后端实现（卡片步骤 1–5）

| # | 卡片要求 | 状态 | 实现说明 |
|---|---|---|---|
| 1 | 新增接口：按 roundId + playerId 录入得票增量 | ✅ | `POST /api/admin/group-vote/entry`（AdminControlController）；另加 `GET /api/admin/group-vote/summary?roundId=` 供前端累计表使用 |
| 2 | 多次累计（累加非覆盖） | ✅ | 每次录入写一笔 `popularity_ledger` 流水（新来源 `group_vote`，1 票 = 1，raw_value 原样入账），累计值由 SUM 聚合得出，天然累加 |
| 3 | 冲销语义（负数冲销，非覆盖） | ✅ | `PopularityService` 为 `group_vote` 来源放行负数（与 `manual` 同一白名单），负数录入即冲销流水，账本完整保留每一笔 |
| 4 | 必带 operatorId，写 operations_log | ✅ | 请求 DTO 强制校验 operatorId 与 reason；日志 detail 含 roundId / playerId / votes / reason，谁、何时、哪轮、哪位、增减多少全留痕 |
| 5 | 幂等防连点 | ✅ | 沿用现有幂等键机制：前端每次表单会话预生成随机 key，后端以 `gv_` 前缀入库；同 key 重复提交被拦截（不重复记账、不重复写日志），并向前端返回 `duplicated=true` 提示 |

**设计决策（一处需 Claude 知悉）**：群投票录入没有复用 `manual` 来源本身，而是新增了 `group_vote` 来源枚举，冲销语义与 `manual` 一致。理由：8/1 当晚"手动调分"和"群投票录入"会并行使用，若共用 `manual` 来源，事后无法从账本区分哪些是投票、哪些是调分，轮次结算与复盘都会混账。卡片原文是"复用 manual 来源的冲销语义"，我理解为复用**语义**而非复用**来源标识**，如理解有偏请指出。

## 二、后台前端（卡片步骤 6）

| # | 卡片要求 | 状态 | 实现说明 |
|---|---|---|---|
| 6a | 识破/卧底相关区域新增录入表单 | ✅ | 「场控操作」tab 新增独立面板「群投票结果录入（C20-3）」：轮次下拉（默认 active 轮次）+ 选手下拉（N号 姓名）+ 票数增量（可负）+ 原因（必填）+ 二次确认弹窗 + 提交 loading |
| 6b | 展示本轮各选手当前累计票数 | ✅ | 面板下方累计表：序号 / 选手 / 累计票数（冲销后净值）/ 录入笔数 + 合计 + 手动刷新；切换轮次自动刷新；每次录入成功后自动刷新并 toast 显示该选手最新累计 |

**位置说明**：卡片写"识破/卧底相关区域"。「场控监控」tab 的识破面板是只读监控区，录入类操作全部集中在「场控操作」tab（与手动调分、模拟注入同区），故录入面板放在「场控操作」，紧邻手动加成。若 Claude 坚持放监控 tab，一处 template 移动即可，不动逻辑。

## 三、附带项：share.js 进 main（Claude 备注②）

| 要求 | 状态 | 说明 |
|---|---|---|
| share.js 及页面挂载进 main | ✅ | 从 release 分支原样提取 `utils/share.js`（文件级提取而非整 commit cherry-pick，见下），main 全部 **9 页**挂载 `onShareAppMessage`（比 release 多 suspicion / popularity 两页——main 保留完整功能，这两页同样需要自定义分享） |
| 不得把 C20-1/C20-2 的页面删除带进 main | ✅ | 未 cherry-pick 任何 C20-1/C20-2 commit（那些 commit 混含页面删除）；只提取了 share.js 单文件 + 用脚本重新挂载，main 的 tabBar、页面、人气展示全部原样，功能完整性零污染 |

## 四、测试（卡片步骤 7）

新增 `GroupVoteEntryTest`（SpringBootTest + H2，5 项）：

| 测试 | 验证点 | 结果 |
|---|---|---|
| multipleEntriesAccumulate | 30 + 25 = 55，两笔流水并存 | ✅ |
| negativeEntryReverses | 40 − 10 = 30，冲销后净值正确、流水两笔 | ✅ |
| duplicateIdempotencyKeyBlocked | 同幂等键连点只记 1 笔账、1 条日志 | ✅ |
| operationLogPersisted | operations_log detail 含轮次/选手/票数 | ✅ |
| invalidRequestsRejected | votes=0、缺幂等键、缺 operator 均被拒 | ✅ |

**全量回归：108 个测试全过，零失败零错误**（含既有 103 个，无回归）。输出摘要入库 `docs/C20-3_test_output.txt`。前端 `pnpm run build`（vue-tsc 类型检查 + vite 构建）通过。

## 五、验收标准对照

| 验收标准 | 结果 |
|---|---|
| 运营可反复录入 | ✅ 累加流水，测试覆盖 |
| 可冲销纠错 | ✅ 负数冲销，测试覆盖 |
| 可随时查看本轮累计票数 | ✅ 累计表 + 合计 + 自动/手动刷新 |
| 操作全程留痕 | ✅ 流水 + operations_log 双留痕，测试覆盖 |
| share.js 进 main 且不污染功能完整性 | ✅ 9 页挂载，页面/功能零删改 |
| 界面截图 | ⚠️ 沙箱无浏览器截图条件，按卡片约定以代码终态替代（App.vue 群投票面板区块 + 构建通过记录）；请 John 起本地后台后补一张录入面板截图归档 |

## 六、给运营的使用备忘（8/1 当晚）

1. 「场控操作」→「群投票结果录入」：选轮次、选选手、填票数增量（从投票管家读数后**填增量**，不是填总数——例：上次录 30，现在总票 48，填 18）、填原因（建议"8/1粉丝群第N轮投票"）。
2. 录错了就录负数冲销，不要找后端改库。
3. 提交前核对群投票截图；每笔提交都进操作日志，事后可查。
4. **提醒**：投票管家读到的是"当前总数"，本接口收的是"增量"——如果 Claude 认为运营在高压下心算增量易错（与 C20-4 同类问题），可在 C20-4 一并裁定是否给群投票也加"填总数自动算差值"的水位线，我预留了扩展空间。
