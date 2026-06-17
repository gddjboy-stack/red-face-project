# C13「卧底识破」实施报告 v1

> 作者：Manus AI  
> 日期：2026-06-17  
> 状态：C13 编码完成，开发环境验证通过，待 Claude 审查与真实彩排验证。  
> 前置依据：Claude 已批准 C13 技术方案，并明确 6 个裁定问题全部采纳。[1] [2]

## 〇、结论

C13「卧底识破」已经按 Claude 裁定完成最小可参与闭环：后台可通过 `collect_state.mode=spy` 开启入口，小程序首页在开启时跳转到“真相识破”页面，用户可在当前轮次候选中提交一次判断，后端返回按选手序号排列的判断分布，后台监控页也可查看同一分布。实现过程严格遵守两条红线：第一，用户侧不暴露 `isSpy` 或真实卧底身份；第二，候选与分布按选手序号展示，不做排行、不用“领先/冲榜”等文案。

> Claude 对 C13 的核心裁定是：防剧透是第一红线；一次提交限制必须有事务保护；合规文案按“识破/判断/线索/真相”包装；不碰 C1~C12 主链路，只新增 Suspicion 相关类、改 `LiveHomeService` 一个字段、小程序加一页。[1]

## 一、交付范围

本次 C13 同时完成了用户侧必选闭环与后台监控卡片。后台监控原本为可选项，但 Claude 建议如有余力尽量做，以便今晚内测时能现场展示“大家怎么判断”的分布效果。[1]

| 模块 | 文件或能力 | 状态 | 说明 |
|---|---|---|---|
| 后端 API | `SuspicionController` | 已完成 | 提供 `/api/suspicion/status` 与 `/api/suspicion/submit` |
| 后端服务 | `SuspicionService` | 已完成 | 开启校验、候选校验、每用户每轮一次、分布统计、防剧透 |
| 后端 Mapper | `SuspicionMapper` | 已完成 | 查询候选与分布、查重、插入提交记录 |
| 后台 API | `AdminSuspicionController` | 已完成 | 提供 `/api/admin/suspicion/status` 监控接口 |
| 首页入口 | `pages/home/index.js` | 已完成 | `spyChannelOpen=true` 时跳转 C13 页面 |
| 小程序页面 | `pages/suspicion/` | 已完成 | 展示候选、提交判断、显示分布、已提交置灰 |
| 后台卡片 | `frontend/control-admin/src/App.vue` | 已完成 | 场控监控页新增“真相识破监控”卡片 |
| 测试 | `SuspicionControllerC13Test` | 已完成 | 新增 6 个测试覆盖关键红线 |

## 二、关键实现说明

C13 没有新增活动开关表，而是复用 C10 场控能力：当存在 active 轮次，且当前 `collect_state.mode='spy'`，并且 `collect_state.round_id` 与 active 轮次一致或为空时，`spyChannelOpen=true`。该变更只影响 `LiveHomeService` 的 `spyChannelOpen` 字段计算，其余首页聚合逻辑未改。[1]

| 裁定点 | 实现结果 | 说明 |
|---|---|---|
| Q1：spyChannelOpen 开启规则 | 已实现 | active 轮次 + `collect_state.mode='spy'` |
| Q2：每用户每轮一次 | 已实现 | Service 层先查 `userId + roundId` 任意记录，再插入；提交方法加 `@Transactional` |
| Q3：候选集合 | 已实现 | 当前轮 `player_round` 中 `normal/free` 且已分队选手；排除 `eliminated` |
| Q4：用户侧防剧透 | 已实现 | DTO 与用户 API 不含 `isSpy`、`actualSpyPlayerId` |
| Q5：不做复杂逻辑 | 已遵守 | 未做自动揭晓、奖励、淘汰、回滚、复杂配置 |
| Q6：后台监控可选 | 已实现 | 后台只展示判断分布，不展示真实卧底身份 |

## 三、API 与前端行为

用户侧 API 继续使用统一 `{ code, message, data }` 响应包裹。`GET /api/suspicion/status` 需要登录态，因为它要返回当前用户是否已经提交以及提交对象；`POST /api/suspicion/submit` 只接受 `roundId` 与 `suspectPlayerId`，`userId` 由 Bearer token 注入，前端不可伪造。

| API | 方法 | 状态 | 关键点 |
|---|---|---|---|
| `/api/suspicion/status?roundId=1` | GET | 已完成 | 返回开启状态、候选、分布、当前用户提交状态；不返回卧底身份 |
| `/api/suspicion/submit` | POST | 已完成 | 每用户每轮一次；未开启、非法候选、重复提交均返回固定错误码 |
| `/api/admin/suspicion/status?roundId=1` | GET | 已完成 | 后台监控分布；不返回卧底身份 |

错误码按方案落地为 `41001 not_open`、`41002 invalid_candidate`、`41003 already_submitted`、`41004 round_mismatch` 与 `41000 unknown`。小程序页面对这些错误码使用固定文案，不把后端内部细节展示给用户。

## 四、验证结果

C13 新增后端专项测试 6 个，覆盖 Claude 要求的关键验证物：未开启不能提交、非法候选拒绝、每用户每轮只能提交一次、提交后分布正确、`spyChannelOpen` 按 spy 模式正确开关、status 返回不含卧底身份字段。[1]

| 验证项 | 命令或方法 | 结果 |
|---|---|---|
| C13 专项测试 | `mvn clean test -Dtest=SuspicionControllerC13Test` | 6 tests，0 failures，0 errors |
| 后端全量回归 | `mvn clean test` | 64 tests，0 failures，0 errors |
| 小程序 JS 语法 | `node --check pages/suspicion/index.js` 等 | 通过 |
| 后台类型检查 | `pnpm exec vue-tsc --noEmit` | 通过 |
| 防剧透静态检查 | grep `isSpy/actualSpyPlayerId` 用户侧新增 API 与页面 | 未发现用户响应字段泄露；仅服务方法名与注释出现相关字样 |
| 敏感文案检查 | grep `第几名/领先/冲榜/投票/打赏` 用户侧新增页面 | 未发现用户侧敏感文案 |

> 注：首次单独运行 C13 测试时遇到旧 `target/test-classes` 资源污染导致的 H2 表未初始化问题；使用 `mvn clean test` 清理后通过。这与 C12 时的 Maven target 污染现象一致，不属于 C13 业务逻辑失败。

## 五、今晚内测演示脚本

内测可按以下步骤演示 C13 的玩法闭环。该脚本不需要真实奖励、揭晓或淘汰逻辑，主持人仍在直播间口播最终真相。

| 步骤 | 操作 | 预期结果 |
|---|---|---|
| 1 | 后台基础数据中为当前轮次设置分队与卧底 | `player_round.is_spy` 已维护，但小程序用户侧不可见 |
| 2 | 后台“集赞目标切换”选择 `spy` 模式并保存 | 首页 `spyChannelOpen=true`，入口显示进行中 |
| 3 | 观众进入首页点击“真相识破” | 跳转到 C13 页面 |
| 4 | 观众选择候选并提交判断 | 提交成功后按钮置灰，页面提示等待直播间揭晓 |
| 5 | 多用户提交后刷新页面或后台监控 | 分布按选手序号展示，不做排行 |
| 6 | 主持人在直播间揭晓 | 系统不自动揭晓、不自动淘汰、不自动发奖励 |

## 六、后续彩排卡路线说明

根据 John 最新决策，除“退款”与“最后一个安全相关卡片”外，其余卡片均应推进出来用于彩排。本次 C13 完成后，建议后续优先梳理并推进 C15/C16 等非退款、非安全卡；其中 C16 会员有效期在既有文档中被明确为上线前需要补的后端缺口，但要避免把退款、撤销、风控封禁等反向逻辑混入当前彩排范围。[3]

| 卡片方向 | 当前建议 | 蓝军备注 |
|---|---|---|
| C13 卧底识破 | 已完成编码与测试 | 待 Claude 审查与内测反馈 |
| C14 退款 | 暂不推进 | John 明确排除 |
| C16 会员有效期 | 建议后续推进 | 只做正向叠加与展示，退款/撤销后置 |
| 最后安全相关卡 | 暂不推进 | John 明确排除 |
| 其他非退款非安全卡 | 需按卡片清单逐一确认 | 建议每张仍先出方案或至少边界说明 |

## 七、引用

[1]: docs/c13/Claude_Ruling_C13_Spy_Recognition_Approved.md "Claude 裁定 — C13 卧底识破方案确认（可编码）"
[2]: C13_Spy_Recognition_Technical_Plan_v1.md "C13「卧底识破」技术方案 v1"
[3]: docs/API契约/当前页面级API契约定稿_C9依据_V1.0.md "当前页面级 API 契约定稿：C16 会员有效期与 C13/C14 同批提示"
