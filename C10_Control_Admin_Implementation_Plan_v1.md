# C10 场控后台技术实施方案 v1

**作者：Manus AI**  
**日期：2026-06-16**  
**状态：待 Claude 确认后编码**  
**适用范围：C10 场控后台，Vue3 + Element Plus Web 管理界面，以及为 C10 联调所需的最小后端后台 API**

## 一、背景与裁定依据

Claude 已确认 **C9 通过，后端 C1~C9 收官**，并裁定项目进入前端阶段。C10 被定义为运营人员使用的场控后台，彩排关键能力包括集赞开关、模拟注入、手动调分和数据监控；同时 Claude 明确指出，C10 需要后端补充几个场控专用 API，包括 `set_collect_target`、`simulate_inject`、`manual_adjust` 的 Controller，这些接口不属于 C9 的 5 个用户端 API，但 C10 开发时需要一并补上，并且仍必须遵守“Controller 不写业务、调用已有 Service”的边界。[1]

从项目既有技术栈看，运营后台已定为 **Vue3 + Element Plus(Web)**，用户端是抖音小程序原生框架，后端是 Java 17 + Spring Boot 3.x + MyBatis。[2] 因此 C10 不应混入小程序代码，也不应复用用户端 API 的身份机制作为运营后台登录的最终方案。考虑到彩排阶段时间压力，本方案建议 C10 先做**最小可用场控后台**：前端 Web 页面 + 后端 Admin Controller，后台鉴权先采用简单 operatorId/header 或环境保护方式，正式上线前再补权限体系。

> Claude 对 C10 的开卡描述是：“运营人员用的管理界面，彩排关键（模拟注入靠它）”。因此 C10 的优先级不是视觉炫酷，而是**彩排可控、误操作可追踪、操作路径稳定**。[1]

## 二、蓝军判断：C10 的真实风险

C10 的风险不在于搭建 Vue 页面，而在于后台操作直接影响人气流水、场控归属和直播彩排效果。如果把后台做成“万能数据库编辑器”，会破坏 C2~C9 已建立的业务边界；如果把后台做得过度复杂，又会拖慢彩排。因此本方案把 C10 收敛为“只调用既有业务 Service 的控制台”，不直接写 `popularity_ledger`、`player_round_stats`、`team_round_stats` 等核心表。

| 风险 | 蓝军判断 | 方案控制 |
|---|---|---|
| 后台绕过 Service 直接改分 | 会破坏“人气值唯一入口”铁律 | 后台 API 只能调用 `PopularityService.applyChange(...)`、`CollectStateService.setCollectTarget(...)`、`LiveDataService.simulateInject(...)`。 |
| 模拟注入与真实直播逻辑不一致 | 彩排数据失真，上线后不可复用 | C10 模拟注入只调用 C7 `LiveDataService.simulateInject(...)`，保持和真实事件同一路径。[3] |
| 操作员误切集赞目标 | 会导致点赞/留言归属错误 | 前端加入二次确认、当前状态高亮、最近更新时间；后端写 `operations_log`。 |
| 手动调分无审计 | 后期无法解释异常人气值 | `manual_adjust` 必填 operatorId、reason、idempotencyKey，并统一走 `PopularityService.applyChange(...)`。 |
| 后台 API 暴露到公网后无保护 | 有被外部刷接口风险 | 彩排阶段至少增加 `/api/admin/**` 前缀、operatorId/header、部署层 IP/基础认证建议；上线前再独立补鉴权卡。 |

## 三、C10 范围定义

C10 本期只做场控后台的彩排生命线功能，不做完整 CMS，不做选手资料编辑，不做订单管理，不做会员管理，不做真相识破完整运营台。后者应放到 C13/C16 或上线后后台增强任务。

| 模块 | 本期是否做 | 说明 |
|---|---:|---|
| 当前场控状态面板 | 做 | 展示当前 round、mode、target、目标人气、团队人气。 |
| 集赞目标切换 | 做 | player/team/spy/pool 四种模式；调用后台 `set_collect_target`。 |
| 模拟注入面板 | 做 | gift、like_delta、comment_delta；调用 `simulate_inject`。 |
| 手动调分 | 做 | manual source 正负调整；必填 reason。 |
| 实时数据监控 | 做 | 基于现有 API-1/API-2 或后台查询接口轮询展示。 |
| 团队分配触发 | 建议做成 P1 可选 | C8 已有 Service；如果 Claude 认为彩排需要，可加后台按钮。 |
| 系数调整触发 | 建议做成 P1 可选 | C8 已有 Service；但彩排未必必须。 |
| 选手/团队/轮次编辑 | 暂不做 | 运维文档提到未来日常运营会需要数据录入支持，但 C10 彩排先不扩大范围。[2] |
| 真相识破管理 | 不做 | 留 C13。 |
| 会员/订单管理 | 不做 | 留 C16 或运维后台增强。 |

## 四、建议的目录结构

由于仓库当前没有独立前端工程目录，本方案建议新增 `frontend/control-admin` 作为 C10 Web 管理后台目录。技术栈采用 Vite + Vue3 + TypeScript + Element Plus。若后续需要统一部署，可将构建产物交由 Nginx 或 Spring Boot 静态资源托管，但 C10 开发期不建议与后端 Maven 工程耦合。

```text
frontend/
  control-admin/
    package.json
    index.html
    vite.config.ts
    src/
      main.ts
      App.vue
      api/
        http.ts
        admin.ts
        live.ts
        popularity.ts
      components/
        CurrentStatusCard.vue
        CollectTargetPanel.vue
        SimInjectPanel.vue
        ManualAdjustPanel.vue
        MonitorBoard.vue
      pages/
        Dashboard.vue
      types/
        api.ts
```

## 五、C10 需要补充的后端 Admin API

C9 已经实现用户端 API-0~API-4，但 C10 需要运营端 API。为避免污染用户端契约，建议统一放在 `/api/admin/**`。这些 Controller 仍在后端 Spring Boot 项目内新增，且只做参数校验和调用已有 Service。

| API | 方法 | 请求 | 调用 Service | 说明 |
|---|---|---|---|---|
| `/api/admin/collect-state` | `GET` | 无 | `CollectStateService.getCurrent()` + 查询聚合 | 读取当前场控状态，前端加载即调用。 |
| `/api/admin/collect-state` | `POST` | `{mode,targetId,roundId,operatorId}` | `CollectStateService.setCollectTarget(...)` | 切换集赞目标，后端已有审计日志。[4] |
| `/api/admin/live/simulate` | `POST` | `{eventType,value,targetId,operatorId}` | `LiveDataService.simulateInject(...)` | 模拟 gift/like/comment，走真实入账路径。[3] |
| `/api/admin/popularity/manual-adjust` | `POST` | `{targetType,targetId,roundId,rawValue,operatorId,reason}` | `PopularityService.applyChange(...)` | 手动调分，source 固定 manual，允许正负值。[5] |
| `/api/admin/popularity/board` | `GET` | `tab,roundId` | 复用 C9 看板查询 | 数据监控，保持排序合规。 |
| `/api/admin/live/home` | `GET` | 无 | 复用 C9 首页聚合 | 当前直播状态监控。 |

### 5.1 API 设计原则

后台 API 不应直接暴露 `PopularityChangeRequest` 给前端完全自由填写，因为这会把业务边界推给 UI。建议新增更收敛的后台 DTO。例如 `ManualAdjustRequest` 只允许 `targetType`、`targetId`、`roundId`、`rawValue`、`operatorId`、`reason`，后端固定 `source="manual"`、自动生成或接收幂等键。模拟注入请求也只允许 `gift`、`like_delta`、`comment_delta` 三类事件，与 C7 `LiveDataService` 保持一致。[3]

### 5.2 后台鉴权的最小策略

C10 彩排阶段不建议临时引入复杂账号体系，否则会拖慢开发并引入新表。建议先采用以下三层保护：第一，接口路径统一 `/api/admin/**`；第二，请求头必须带 `X-Operator-Id` 或请求体必须带 `operatorId`；第三，部署层使用内网、白名单或基础认证。上线前如果后台要长期使用，应新增独立任务卡实现后台账号、角色和操作权限。

## 六、C10 前端页面设计

C10 本期建议做成单页 Dashboard，而不是多页面后台系统。场控人员在直播中需要快速判断和快速操作，页面应该避免深层菜单。

| 区块 | 信息/操作 | 刷新策略 | 风险控制 |
|---|---|---|---|
| 顶部状态栏 | 当前轮次、live/idle、当前 mode、更新时间 | 5 秒轮询 | live/idle 用颜色区分。 |
| 集赞目标切换 | mode 单选、target 下拉/输入、roundId、确认按钮 | 操作后立即刷新 | 非 pool 模式 targetId 必填；提交前二次确认。 |
| 模拟注入 | eventType、value、targetId、operatorId | 提交后显示 SimResult | gift 无 targetId 时提示只允许当前 player 模式省略。 |
| 手动调分 | targetType、targetId、roundId、rawValue、reason | 提交后刷新看板 | rawValue 可正可负，但不能为 0；reason 必填。 |
| 数据监控 | player/team/spy 三 tab，按序号或 team_id 展示 | 5 秒轮询 | 前端不按 value 排序，沿用后端顺序。 |

## 七、测试与验收标准

C10 的验证物应包含后端 Admin Controller 测试、前端构建检查和操作演示截图。考虑到 C10 涉及后台补 API，建议先补后端测试再写前端，以免前端对接空接口。

| 验收项 | 验证方式 | 通过标准 |
|---|---|---|
| set_collect_target | MockMvc/H2 测试 | 当前 collect_state 被 upsert，operations_log 有记录。 |
| simulate_inject gift | MockMvc/H2 测试 | 返回 SimResult，player 人气增加。 |
| simulate_inject like/comment | MockMvc/H2 测试 | 通过当前场控状态自动归属。 |
| manual_adjust 正负调分 | MockMvc/H2 测试 | 走 `PopularityService.applyChange(...)`，统计表变化，ledger 有 manual source。 |
| 前端构建 | `pnpm build` | 构建成功。 |
| 彩排操作 | 截图/录屏 | 能切集赞对象、模拟注入、手动调分、查看数据变化。 |

## 八、建议 Claude 裁定的问题

| 编号 | 问题 | Manus 建议 |
|---|---|---|
| C10-Q1 | C10 是否允许在本卡内补 `/api/admin/**` 后端 Controller？ | 建议允许。Claude 已提示 C10 需要补场控专用 API，否则前端无法联调。[1] |
| C10-Q2 | 后台鉴权彩排版如何处理？ | 建议先用 `/api/admin/**` + operatorId + 部署层保护，正式后台账号权限另开卡。 |
| C10-Q3 | C10 是否包含团队分配和系数调整按钮？ | 建议列为 P1 可选，若彩排需要再做；P0 先保集赞切换、模拟注入、手动调分、监控。 |
| C10-Q4 | 手动调分的 idempotencyKey 由前端传还是后端生成？ | 建议后端默认生成 `manual_时间_operator_随机值`，前端不负责幂等细节。 |
| C10-Q5 | 数据监控是否复用用户端 API-1/API-2？ | 建议复用只读能力，但后台可增加 `/api/admin` 包装，避免前端跨语义调用用户端接口。 |

## 九、结论

C10 应定位为**彩排场控控制台**，而不是完整运营后台。第一阶段只做单页 Dashboard，并补最小 Admin Controller，使运营人员能稳定完成集赞切换、模拟注入、手动调分和实时监控。该方案最大限度复用 C2~C9 已通过的 Service 与查询能力，避免破坏后端收官成果。

## References

[1]: /home/ubuntu/upload/Claude审查_C9通过_后端收官_C10C11开卡.md "Claude 审查 — C9通过 后端收官 C10/C11开卡"
[2]: docs/小程序运维需求_报价版V2.0-2.md "小程序运维需求_报价版V2.0-2"
[3]: backend/redface-backend/src/main/java/com/redface/service/LiveDataService.java "LiveDataService"
[4]: backend/redface-backend/src/main/java/com/redface/service/CollectStateService.java "CollectStateService"
[5]: backend/redface-backend/src/main/java/com/redface/service/PopularityService.java "PopularityService"
[6]: backend/redface-backend/src/main/java/com/redface/service/TeamDistributionService.java "TeamDistributionService"
