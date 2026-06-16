# C10 + C19 场控后台与基础数据管理合并实施方案 v1

**作者：Manus AI**  
**日期：2026-06-16**  
**状态：待 Claude 最终确认后编码**  
**适用范围：C10 场控后台、C19 基础数据管理后台、同一 Vue3 + Element Plus 工程、同一套 `/api/admin/**` 后端接口**

## 一、整合背景

Claude 已裁定 C10 场控后台方案批准，并明确 C10 当前优先级高于 C11，因为模拟注入是彩排生命线。Claude 同时新增 C19 基础数据管理后台任务卡，要求其并入 C10，同一个 Vue3 工程、同一套 `/api/admin/**` 后端，P0 最简版尽量赶在彩排前完成，以便 John 能自行录入真实选手、队伍、轮次、分队与卧底身份。V3.0 全景手册进一步把“彩排底线”更新为 **C1~C12 + C19 最简版**，并确认当前阶段应先推进 C10 + C19，C11 等彬少正式稿后启动。[1] [2] [3]

因此，本方案不再把 C10 与 C19 视为两套后台，而是将它们合并为一个**彩排运营后台 P0**。该后台的定位是：让运营人员在不写数据库、不碰代码的前提下，完成基础数据录入、直播场控、模拟注入、团队分配、手动调分和数据监控。

## 二、最新裁定与执行顺序

Claude 的最新裁定改变了前端阶段的执行优先级。C10 先做，C11 等正式稿，C19 并入 C10。本方案建议后续编码顺序如下表。

| 顺序 | 任务 | 原因 | 是否需要先等外部物料 |
|---:|---|---|---|
| 1 | C10 后端 Admin API + C19 P0 后端 API | 前端后台必须先有可调用接口；C19 是录入真实数据前提 | 不需要 |
| 2 | C10/C19 Vue 后台单工程 | 场控、模拟注入、基础数据录入均在同一后台完成 | 不需要 |
| 3 | C10/C19 测试与操作截图 | Claude 验证物要求 | 不需要 |
| 4 | C11 抖音小程序 | 等彬少正式稿，避免占位返工 | 需要彬少正式稿；若仍未到，再用占位图 |

> 蓝军提醒：C19 是被补上的关键缺口。如果没有 C19，C10 即使能模拟注入，也缺少真实选手、队伍、轮次和分队数据，最终仍会回到“手写数据库”的危险状态。

## 三、合并后的 P0 范围

C10 与 C19 合并后，P0 后台应包含两个一级菜单：“场控台”和“基础数据”。为了控制范围，不做完整后台系统，不做账号权限体系，不做写真上传，不做卡密管理，不做会员/订单管理。

| 一级菜单 | 页面 | P0 功能 | 调用后端能力 |
|---|---|---|---|
| 场控台 | 实时监控 | 当前直播状态、当前集赞对象、player/team/spy 看板 | API-1/API-2 的 Admin 包装 |
| 场控台 | 集赞开关 | 切换 player/team/spy/pool + targetId + roundId | `CollectStateService.setCollectTarget(...)` |
| 场控台 | 模拟注入 | gift/like_delta/comment_delta 三类事件 | `LiveDataService.simulateInject(...)` |
| 场控台 | 手动调分 | player/team/spy/pool 正负调分，reason 必填 | `PopularityService.applyChange(...)`，source 固定 manual |
| 场控台 | 团队分配 | P0 纳入团队分配按钮，method 默认 equal，custom 可后置 | `TeamDistributionService.distribute(...)` |
| 基础数据 | 选手管理 | 列表 + 新增选手 name/number | 新增 `BasicDataService` + Mapper |
| 基础数据 | 队伍管理 | 列表 + 新增队伍 name | 新增 `BasicDataService` + Mapper |
| 基础数据 | 轮次管理 | 列表 + 新增轮次 + 状态切换 | active 唯一性处理 |
| 基础数据 | 分队与卧底 | 选择轮次，给选手分队并设置 is_spy/player_status | `player_round` upsert |

## 四、后端设计：统一 `/api/admin/**`

后端新增 Admin Controller 时必须遵守 Claude 对 C10 的裁定：Controller 不写业务，只做参数校验、调用已有 Service 或新增 `BasicDataService`。所有 `/api/admin/**` 写操作必须写入 `operations_log`，形成审计留痕。[1] [2]

### 4.1 C10 场控类 Admin API

| API | 方法 | P0 | 说明 |
|---|---|---:|---|
| `/api/admin/live/home` | GET | 是 | 包装 C9 首页聚合，用于后台监控。 |
| `/api/admin/board?tab=&roundId=` | GET | 是 | 包装 C9 看板查询，用于后台监控，不按人气排序。 |
| `/api/admin/collect-state` | GET | 是 | 查询当前场控状态。 |
| `/api/admin/collect-state` | POST | 是 | 切换集赞目标，写 `operations_log`。 |
| `/api/admin/live/simulate` | POST | 是 | 模拟 gift/like/comment，写 `operations_log`。 |
| `/api/admin/popularity/manual-adjust` | POST | 是 | 手动调分，reason 必填，后端生成幂等键，写 `operations_log`。 |
| `/api/admin/team-distribution` | POST | 是 | 团队分配按钮纳入 P0，调用 C8 服务，写 `operations_log`。 |
| `/api/admin/coefficient/adjust` | POST | 否 | 系数调整为 P1 可选，不阻塞彩排。 |

### 4.2 C19 基础数据类 Admin API

| API | 方法 | P0 | 说明 |
|---|---|---:|---|
| `/api/admin/players` | GET | 是 | 选手列表，按 number ASC。 |
| `/api/admin/players` | POST | 是 | 新增选手，number 重复返回友好错误。 |
| `/api/admin/players/{id}` | PUT | 否 | 编辑/停用选手，P1。 |
| `/api/admin/teams` | GET | 是 | 队伍列表。 |
| `/api/admin/teams` | POST | 是 | 新增队伍。 |
| `/api/admin/rounds` | GET | 是 | 轮次列表，按 start_time 或 round_id 排序。 |
| `/api/admin/rounds` | POST | 是 | 新增轮次，校验 end_time > start_time。 |
| `/api/admin/rounds/{id}/status` | PUT | 是 | 切换状态；active 唯一性按 Claude 裁定处理。 |
| `/api/admin/player-round` | GET | 是 | 查询某轮分队/卧底情况。 |
| `/api/admin/player-round` | POST | 是 | upsert 分队与卧底，写 `player_round`。 |

## 五、后端 Service 与 Mapper 边界

C19 的铁律是只写静态基础数据表：`players`、`teams`、`rounds`、`player_round`。任何人气值、积分、系数变更不得在 C19 中直接写表，仍必须走 `PopularityService` 或 `CoefficientService`。[2]

| 组件 | 新增/复用 | 职责 |
|---|---|---|
| `AdminControlController` | 新增 | C10 场控类 API 入口。 |
| `BasicDataController` | 新增 | C19 基础数据类 API 入口。 |
| `AdminControlService` | 新增或轻量 facade | 组合调用 `CollectStateService`、`LiveDataService`、`PopularityService`、`TeamDistributionService`，并统一写操作日志。 |
| `BasicDataService` | 新增 | 承载 players/teams/rounds/player_round 的校验、写入、active 唯一性和日志。 |
| `BasicDataMapper` | 新增 | players、teams、rounds、player_round 的 P0 查询、插入、更新、upsert。 |
| `OperationsLogMapper` | 复用 | 所有写操作审计留痕。 |

### 5.1 active 轮次唯一性裁定建议

C19 任务卡允许“切某轮为 active 时，其余 active 自动转 completed，或拒绝并提示”。我的建议是**自动将其他 active 转 completed**，原因是直播现场操作更需要“一键切当前轮”，拒绝会增加场控负担。但这会产生业务含义：被转 completed 的轮次视为已结束。若 Claude 更保守，可裁定为拒绝并提示。

| 方案 | 优点 | 缺点 | Manus 建议 |
|---|---|---|---|
| 自动转 completed | 场控操作简单，适合直播现场 | 可能误结束旧轮次 | 推荐，用 operations_log 记录旧 active。 |
| 拒绝并提示 | 更保守，避免误操作 | 现场需要先手动关旧轮 | 备选。 |

## 六、前端工程：C10 与 C19 同一 Vue3 项目

仓库当前没有 `frontend` 目录，因此建议新增 `frontend/control-admin`，使用 Vue3 + TypeScript + Element Plus + Vite。C19 并入 C10 后，不另起工程，只在同一后台增加“基础数据”菜单。

```text
frontend/control-admin/
  package.json
  vite.config.ts
  src/
    main.ts
    App.vue
    router/index.ts
    api/
      http.ts
      adminControl.ts
      basicData.ts
    pages/
      control/Dashboard.vue
      control/CollectState.vue
      control/SimulateInject.vue
      control/ManualAdjust.vue
      control/TeamDistribution.vue
      basic/Players.vue
      basic/Teams.vue
      basic/Rounds.vue
      basic/PlayerRound.vue
    components/
      CurrentStatusCard.vue
      MonitorBoard.vue
      OperatorBar.vue
```

### 6.1 前端操作约束

所有写操作表单必须有 `operatorId`。彩排版可在顶部 `OperatorBar` 输入一次并存入 `localStorage`，后续请求统一带上。手动调分、团队分配、状态切换等高风险操作必须二次确认。基础数据保存成功后，应立即刷新列表并显示操作结果。

| 操作 | 前端校验 |
|---|---|
| 新增选手 | name 必填，number 正整数。 |
| 新增队伍 | name 必填。 |
| 新增轮次 | name/startTime/endTime/status 必填，endTime 晚于 startTime。 |
| 切 active | 二次确认。 |
| 分队设卧底 | 必须先选择 roundId；team 可空；isSpy 布尔。 |
| 模拟注入 | eventType/value/operatorId 必填；gift targetId 建议必填。 |
| 手动调分 | rawValue 不能为 0；reason 必填。 |
| 团队分配 | teamId/roundId/operatorId/reason 必填。 |

## 七、测试与验证物

C10+C19 合并后，验证物应覆盖后端 API、前端构建和操作截图。后端测试优先使用 MockMvc + H2，延续 C9 的 Controller 测试风格。

| 验证物 | 覆盖内容 |
|---|---|
| `AdminControlControllerC10Test` | set_collect_target、simulate_inject 三类事件、manual_adjust、team_distribution、operations_log。 |
| `BasicDataControllerC19Test` | 新增选手、number 重复友好错误、新增队伍、新增轮次、active 唯一性、分队设卧底、operations_log。 |
| 全量 `mvn test` 输出 | C1~C9 既有 51 测试 + C10/C19 新测全绿。 |
| 前端 `pnpm build` 输出 | `frontend/control-admin` 构建成功。 |
| 操作截图 | 录入选手→建队→建轮→分队设卧底→切集赞→模拟注入→监控变化。 |
| 实施报告 Markdown | 记录变更范围、测试结果、截图路径或链接。 |

## 八、需要 Claude 最终确认的问题

| 编号 | 问题 | Manus 建议 |
|---|---|---|
| C10C19-Q1 | 是否同意先实现 C10 后端 Admin API + C19 P0 后端 API，再做 Vue 页面？ | 同意。前端需要稳定接口，且测试可先锁定业务边界。 |
| C10C19-Q2 | active 唯一性采用“自动转 completed”还是“拒绝并提示”？ | 建议自动转 completed，并写 operations_log；请 Claude 最终裁定。 |
| C10C19-Q3 | team_distribution P0 是否只做 equal，custom 是否后置？ | 建议 P0 先做 equal 按钮，custom 留 P1；C8 已有 custom 能力但 UI 可后置。 |
| C10C19-Q4 | C19 是否需要为 player_round 插入时同步初始化 `player_round_stats`？ | 建议不要自动写统计表，除非现有 Service 需要；人气首次入账会按既有逻辑 ensure/update。请 Claude 确认。 |
| C10C19-Q5 | 彩排版 operatorId 是否允许 localStorage 输入方式？ | 建议允许，C18 再替换正式后台权限。 |

## 九、结论

C10 与 C19 应合并实施：先补 `/api/admin/**` 后端，后做同一个 Vue3 + Element Plus 后台。P0 目标不是“完整运营系统”，而是让 John 和运营人员能在彩排前完成真实基础数据录入、直播场控、模拟注入、团队分配、手动调分和监控闭环。该方案严格继承 C1~C9 的铁律：人气值变更不直接写表，基础数据管理只碰静态表，所有写操作必须审计留痕。

## References

[1]: Claude裁定_C10C11方案_写真上传功能.md "Claude 裁定 — C10/C11 方案确认 + 写真上传功能裁定"
[2]: 任务卡C19_基础数据管理后台.md "任务卡 C19 — 基础数据管理后台"
[3]: 红颜局中局开发指导手册V3.0全景版.md "红颜局中局开发指导手册 V3.0 全景版"
[4]: C10_Control_Admin_Implementation_Plan_v1.md "C10 场控后台技术实施方案 v1"
[5]: backend/redface-backend/src/main/java/com/redface/mapper/OperationsLogMapper.java "OperationsLogMapper"
[6]: backend/redface-backend/src/main/java/com/redface/service/LiveDataService.java "LiveDataService"
[7]: backend/redface-backend/src/main/java/com/redface/service/TeamDistributionService.java "TeamDistributionService"
