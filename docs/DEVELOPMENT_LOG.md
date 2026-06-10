# DEVELOPMENT_LOG.md

## 2026-06-09

*   **决策**：与 John 确认，正式启动并行开发模式。后端（Manus）先行，前端（彬少）同步进行 UI 优化。
*   **状态**：进入 Phase 1：开发环境初始化与底层逻辑构建。
*   **计划**：
    1.  初始化 GitHub 开发日志 (`DEVELOPMENT_LOG.md`)。
    2.  创建自检清单 (`SELF_CHECKLIST.md`)。
    3.  开始编写数据库 Schema。


## 2026-06-10

### 文件结构调整与 C1 任务进展

*   **文件结构调整**: 根据 Claude 的最新指导，项目目录结构已调整如下：
    *   `red-face-project/docs/`: 存放所有项目文档，包括 `DEVELOPMENT_LOG.md` 和 `SELF_CHECKLIST.md`。
    *   `red-face-project/db/`: 存放数据库 Schema 文件 `db_schema.sql`。
    *   `red-face-project/backend/`: 存放 Spring Boot 后端代码。
*   **数据库 Schema 更新**: `db/db_schema.sql` 已更新为 Claude 提供的最终版本，该版本修复了 3 个 P0 问题，并完全取代了之前的 Schema。
*   **C1 任务 - 项目骨架创建**: 已在 `backend/` 目录下创建 `redface-backend` Spring Boot 项目骨架，并创建了 `AppConstants.java` 文件。
    *   `pom.xml` 已配置 Spring Boot Web, MyBatis, MySQL 依赖，Java 版本 17。
    *   `AppConstants.java` 已根据手册内容初始化。

**下一步计划**：

1.  解决 MySQL 环境问题，执行 `db/db_schema.sql` 创建数据库表。
2.  获取 `SHOW TABLES` 输出作为 C1 任务的验证物。
3.  更新 `DEVELOPMENT_LOG.md` 和 `SELF_CHECKLIST.md`。
4.  提交所有更改到 GitHub，并向用户报告 C1 任务完成情况。

## 2026-06-10 C1 审查修复记录

Claude 对 C1 的审查结论为“有条件通过”，并指出 `db/db_schema.sql` 存在两项必须修复的问题：其一是 SQL 文件首尾混入 Markdown 代码块标记，其二是 Manus 擅自添加了引用不存在的 `users` 表的外键。现已按照审查意见完成整改，`db/db_schema.sql` 已删除 Markdown 代码块标记、`fk_suspicion_votes_user_id` 外键语句以及相关推断性注释。项目不再假设存在 `users` 表，`user_id` 仅作为抖音授权返回的字符串标识保存。

为解决本地 MySQL 服务不可用对后续 C2 至 C8 单元测试的影响，后端项目已补充 H2 内存数据库测试环境。`pom.xml` 已添加 H2 测试依赖，`src/test/resources/application-test.yml` 已配置 H2 MySQL 兼容模式，`src/test/resources/schema-h2.sql` 已由主 Schema 转换生成，并去除了 H2 不支持的 MySQL 专用表选项。新增 `SchemaInitializationTest` 用于验证 H2 环境可初始化 16 张 C1 核心表且不包含三张废弃表。

| 验证项 | 结果 | 验证物 |
|---|---|---|
| Schema 清洁检查 | 通过，未发现 Markdown 代码块标记或 `users` 外键引用 | `docs/C1_schema_clean_check.txt` |
| H2 Schema 初始化测试 | 通过，`Tests run: 1, Failures: 0, Errors: 0` | `docs/C1_h2_schema_test_output.txt` |
| 真实 MySQL/MariaDB 复核 | Claude 环境真实执行通过，创建 16 张表 | `docs/C1_real_show_tables_by_claude.md` |

下一步应先将 C1 修复提交到 GitHub 并等待 Claude 复核确认。根据 John 的明确安排，C2 至 C5 属于系统心脏模块，人气值引擎与卡密核销建议切换到 Manus 1.6 Max 模型执行；C6 起可再切回 Lite。

## 2026-06-10 C2 实现记录

Claude 确认 C1 正式关闭并开出 C2 后，Manus 已在 Max 模型下完成 `PopularityService.convert` 与 `applyChange(player 直接归属)` 的 C2 实现。该实现严格限定在 C2 范围内：当请求显式指定 `targetType="player"` 和 `targetId` 时，系统完成礼物换算、写入 `popularity_ledger`、累加更新 `player_round_stats.individual_popularity` 并返回结果；`like/comment` 的场控归属逻辑未提前实现，保留至 C3。

| 验证项 | 结果 | 验证物 |
|---|---|---|
| 礼物 1000 抖币 → +100,000 人气值 | 通过 | `docs/C2_junit_output.txt` |
| 相同幂等键调用两次只生效一次 | 通过 | `docs/C2_junit_output.txt` |
| 全量 H2 JUnit 测试 | 通过，`Tests run: 3, Failures: 0, Errors: 0` | `docs/C2_junit_output.txt` |
| C2 自检 | 通过，未发现 `double/float` 和主代码字段注入 | `docs/C2_self_check_output.txt` |

C2 中统计更新采用“先确保统计行存在，再执行 `UPDATE ... SET individual_popularity = individual_popularity + ?`”的方式，避免先查询再覆盖写入导致并发不安全。下一步应提交 GitHub 并请 Claude 审查 C2。若 C2 通过，继续在 Max 模型下进入 C3。

## 2026-06-10 C3 实现记录

Claude 审查确认 C2 通过并开出 C3 后，Manus 已继续在 Max 模型下完成 C3。C3 的核心变化是将 `like/comment` 总增量从 C2 的“暂不支持”推进为“按当前场控状态归属”，并补齐 `CollectStateService` 对 `collect_state` 单行表和 `operations_log` 操作审计日志的写入能力。

本次也合并修复了 C2 审查中指出的 P0 问题：`PopularityService.validateRequest()` 不再一刀切拒绝 `rawValue <= 0`，而是改为所有 source 均拒绝 `rawValue=0`，`manual/refund` 允许负值，`gift/like/comment/token/team_distribution` 等来源仍要求正值。该修复已通过 `manual rawValue=-5000` 的 JUnit 测试验证。

| 验证项 | 结果 | 验证物 |
|---|---|---|
| team 场控下 like 增量进入 `team_round_stats` | 通过 | `docs/C3_junit_output.txt` |
| pool 场控下 comment 增量进入 `pool_round_stats` | 通过 | `docs/C3_junit_output.txt` |
| manual 负值 `-5000` 正常走通 | 通过 | `docs/C3_junit_output.txt` |
| 场控切换写入 `operations_log` | 通过 | `docs/C3_junit_output.txt` |
| 全量 H2 JUnit 测试 | 通过，`Tests run: 7, Failures: 0, Errors: 0` | `docs/C3_junit_output.txt` |
| C3 自检 | 通过，未发现 `double/float` 和主代码字段注入 | `docs/C3_self_check_output.txt` |

下一步应提交 GitHub 并请 Claude 审查 C3。若 C3 通过，继续在 Max 模型下进入 C4：`computeScore` 衰减与系数积分计算。
