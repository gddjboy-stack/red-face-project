# C10 + C19 彩排运营后台实施报告 v1

**作者：Manus AI**  
**日期：2026-06-16**  
**适用范围：C10 场控后台、C19 基础数据管理后台、后端 `/api/admin/**`、Vue3 + Element Plus 前端工程**

## 一、执行背景

本次实施依据 Claude 对 `C10_C19_Admin_Integrated_Implementation_Plan_v1` 的最终确认执行。Claude 明确批准将 C10 场控台与 C19 基础数据管理合并为一个“彩排运营后台”，并裁定执行顺序为先做后端 Admin API，再做 Vue 页面。核心边界是：Controller 不写业务，C19 只写 `players`、`teams`、`rounds`、`player_round` 四张静态表，所有写操作进入 `operations_log`，且不得改 C2~C9 核心业务逻辑。[1]

本次实施严格遵守 Claude 五项裁定：active 轮次切换采用“自动转 completed”并审计；团队分配 P0 只做 equal；分队不初始化 `player_round_stats`；彩排版 operatorId 使用 localStorage；`frontend/control-admin` 使用 Vue3 + Element Plus 工程结构。[1]

## 二、后端实现范围

后端新增 C10 场控后台和 C19 基础数据管理的最小 Admin API，路径统一为 `/api/admin/**`。C10 场控类 API 只组合调用已有 Service，不直接写人气、统计或系数表；C19 基础数据类 API 只管理静态基础数据表。

| 类别 | 新增文件 | 说明 |
|---|---|---|
| Controller | `AdminControlController` | C10 场控 API：监控、集赞切换、模拟注入、手动调分、团队均分。 |
| Controller | `BasicDataController` | C19 基础数据 API：选手、队伍、轮次、分队/卧底。 |
| Service | `AdminControlService` | 组合调用 `CollectStateService`、`LiveDataService`、`PopularityService`、`TeamDistributionService`。 |
| Service | `BasicDataService` | 负责基础数据校验、active 唯一性、number 友好错误、审计日志。 |
| Mapper | `BasicDataMapper` | 静态表白名单写入：`players`、`teams`、`rounds`、`player_round`。 |
| DTO | `AdminRequests`、`BasicDataRequests` | 限制前端可提交字段，避免前端直接操纵底层业务 DTO。 |
| DTO | `AdminOperationResult`、`BasicDataViews` | 后台写操作和基础数据列表响应。 |

### 2.1 C10 Admin API

| API | 方法 | 实现状态 | 审计 |
|---|---|---:|---:|
| `/api/admin/live/home` | GET | 已实现 | 只读，无需审计 |
| `/api/admin/board` | GET | 已实现 | 只读，无需审计 |
| `/api/admin/collect-state` | GET/POST | 已实现 | POST 写 `set_collect_target` |
| `/api/admin/live/simulate` | POST | 已实现 | 写 `simulate_inject` |
| `/api/admin/popularity/manual-adjust` | POST | 已实现 | 写 `manual_adjust` |
| `/api/admin/team-distribution` | POST | 已实现 | 写 `team_distribution` |

### 2.2 C19 Admin API

| API | 方法 | 实现状态 | 审计 |
|---|---|---:|---:|
| `/api/admin/players` | GET/POST | 已实现 | POST 写 `basic_create_player` |
| `/api/admin/teams` | GET/POST | 已实现 | POST 写 `basic_create_team` |
| `/api/admin/rounds` | GET/POST | 已实现 | POST 写 `basic_create_round` |
| `/api/admin/rounds/{roundId}/status` | PUT | 已实现 | 写 `basic_update_round_status`；如自动结束旧 active，另写 `basic_auto_complete_active_rounds` |
| `/api/admin/player-round` | GET/POST | 已实现 | POST 写 `basic_upsert_player_round` |

## 三、关键裁定落地说明

| Claude 裁定 | 落地方式 |
|---|---|
| Q1 先后端 API 再 Vue 页面 | 已先完成并测试后端，后创建前端工程。 |
| Q2 active 唯一性自动转 completed | `BasicDataService.updateRoundStatus` 在切 active 前自动完成其他 active，并写详细日志。 |
| Q3 团队分配 P0 只做 equal | 前端 P0 只提供 equal 按钮；后端仍兼容已有 `TeamDistributionService` 方法参数。 |
| Q4 分队不初始化 `player_round_stats` | `BasicDataService.upsertPlayerRound` 只写 `player_round`，测试断言 `player_round_stats` 行数为 0。 |
| Q5 operatorId localStorage | 前端顶部输入 operatorId 并写入 localStorage，所有写请求统一带上。 |

## 四、前端实现范围

前端新增 `frontend/control-admin` 工程，技术栈为 Vue3 + TypeScript + Element Plus + Vite。该后台是单页彩排运营后台，包含“场控监控”“场控操作”“基础数据”三个页签。

| 页签 | 功能 | 说明 |
|---|---|---|
| 场控监控 | 直播状态、人气看板 | 包装调用 `/api/admin/live/home` 与 `/api/admin/board`。 |
| 场控操作 | 集赞切换、模拟注入、手动调分、团队均分 | 高风险操作有二次确认，写操作带 operatorId。 |
| 基础数据 | 选手、队伍、轮次、分队/卧底 | P0 最简录入能力，满足真实数据彩排。 |

前端工程没有实现 C17 写真上传、C18 后台账号权限、卡密管理或会员/订单管理。后台权限仍沿用 Claude 批准的彩排版策略：`/api/admin/**` + operatorId + 部署层保护，正式上线前由 C18 替换为账号权限体系。[1]

## 五、后端测试结果

已在 `backend/redface-backend` 执行全量后端测试，并将完整输出保存为 `reports/C10_C19_mvn_test_output_v1.txt`。本次新增 7 个测试后，全量测试从 C9 的 51 个增加到 58 个，全部通过。

```bash
mvn clean test
```

```text
[INFO] Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| 测试类 | 测试数 | 覆盖重点 | 结果 |
|---|---:|---|---:|
| `AdminControlControllerC10Test` | 4 | 集赞切换、三类模拟注入、手动调分、团队 equal 均分、审计日志 | 通过 |
| `BasicDataControllerC19Test` | 3 | 新增选手、number 重复友好错误、建队建轮、active 自动完成旧轮、分队设卧底、不触碰统计表、审计日志 | 通过 |
| C1~C9 既有测试 | 51 | 后端核心回归 | 通过 |

## 六、前端构建结果

已在 `frontend/control-admin` 执行依赖安装与构建，并将完整输出保存为 `reports/C10_C19_frontend_build_output_v1.txt`。

```bash
pnpm install
pnpm build
```

```text
vite v8.0.16 building client environment for production...
✓ built in 940ms
```

构建过程中出现两类 Vite/Rolldown 警告：一类是第三方依赖 `@vueuse/core` 的 pure annotation 位置提示，另一类是单页后台打包 chunk 超过 500KB 的提示。两者均未导致构建失败，当前 P0 后台已成功生成 `dist/index.html`、CSS 与 JS 产物。考虑到本后台是内部彩排工具，暂不为了 chunk 拆分增加复杂度；若后台长期运营，可在 C18 权限体系或后台增强阶段做代码分包优化。

## 七、边界自检

| 禁止项 | 是否触碰 | 说明 |
|---|---:|---|
| 改 C2~C9 核心业务逻辑 | 否 | 仅新增 Admin 层、基础数据 Service/Mapper、前端工程。 |
| C19 写人气/统计/系数表 | 否 | 测试明确验证分队不写 `player_round_stats`。 |
| 绕过 `PopularityService.applyChange()` 改人气 | 否 | 手动调分走 `PopularityService.applyChange(source=manual)`。 |
| 绕过 `TeamDistributionService` 做团队分配 | 否 | 团队均分调用 C8 服务。 |
| 实现 C17/C18/卡密管理 | 否 | 本次只做 C10+C19 P0。 |
| 提交 `target`、`node_modules`、`dist` 生成物 | 待提交前清理 | 源码和报告保留，生成物不纳入 Git。 |

## 八、验证物说明

本次已完成代码级验证物：后端 MockMvc/H2 测试、全量 Maven 输出、前端构建输出。Claude 裁定中提到的“完整操作截图”需要在后台服务和前端服务运行时通过浏览器执行录入选手→建队→建轮→分队设卧底→切集赞→模拟注入→看监控变化的流程。当前仓库提交包含实现与测试，截图可在后续联调演示阶段补充；若 Claude 要求本次必须附图，我将启动本地后端与前端服务后补充截图文件再提交一个 v2 报告。

## 九、结论

C10+C19 P0 已完成后端 Admin API、MockMvc/H2 测试、Vue3 + Element Plus 彩排运营后台和构建验证。后端全量测试结果为 **Tests run: 58, Failures: 0, Errors: 0, Skipped: 0**，前端构建成功。当前实现已具备提交 Claude 复核的条件。

## References

[1]: Claude确认_C10C19合并方案.md "Claude 最终确认 — C10+C19 合并方案"
[2]: C10_C19_Admin_Integrated_Implementation_Plan_v1.md "C10+C19 场控后台与基础数据管理合并实施方案 v1"
[3]: reports/C10_C19_mvn_test_output_v1.txt "C10+C19 Maven 全量测试输出 v1"
[4]: reports/C10_C19_frontend_build_output_v1.txt "C10+C19 前端构建输出 v1"
[5]: backend/redface-backend/src/test/java/com/redface/AdminControlControllerC10Test.java "AdminControlControllerC10Test"
[6]: backend/redface-backend/src/test/java/com/redface/BasicDataControllerC19Test.java "BasicDataControllerC19Test"
