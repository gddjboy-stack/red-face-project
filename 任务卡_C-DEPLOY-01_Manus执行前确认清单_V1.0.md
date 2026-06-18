# C-DEPLOY-01 执行前确认清单（Manus → John / Claude）

> 发起方：Manus
> 针对：Claude《C-DEPLOY-01 共识与修订》（2026.06.18）
> 日期：2026-06-18
> 性质：动手前的最后澄清。共识方向已认可，仅就**写代码绕不开的执行级歧义**做确认，避免返工或埋新坑。

---

## 〇、总体态度

Claude 的回应我**全部认可**，4 个问题答复清晰，新增 C-AUTH-01 立卡也合理。下面只剩 **5 个执行细节** 需要拍板。其中 Q1、Q2 不解决我无法保证代码可启动，属于**阻塞项**；Q3–Q5 可由我先按推荐默认值实现、事后微调。

---

## 一、阻塞项（需明确答复才能保证服务正常启动）

### Q1.【关键】生产 profile 启动时 `DouyinAuthProvider` 尚不存在，会导致启动崩溃

共识要求"生产 profile 接 `DouyinAuthProvider`"，但该类由 **C-AUTH-01 并行开发、可能晚于本卡**。问题在于：

- 若本卡 `application.yml` 默认 `spring.profiles.active=prod`，而 `DouyinAuthProvider` 还没合并，Spring 启动时会因**找不到 `AuthProvider` 类型的 Bean 而直接报错崩溃**（`MockAuthProvider` 若被 `@Profile("dev")` 限定，prod 下就没有任何实现）。
- 这会造成"本卡先交付 → 运维一启动就崩 → 卡住部署"。

**我的建议（请确认其一）**：
- **方案 A（推荐）**：`MockAuthProvider` 标 `@Profile("!prod")`，`DouyinAuthProvider` 标 `@Profile("prod")`。**本卡先不把 active profile 写死为 prod**，默认留空（即用 Mock），由运维在 `DouyinAuthProvider` 就绪后，通过环境变量 `SPRING_PROFILES_ACTIVE=prod` 切换。这样本卡单独交付也能启动，不依赖 C-AUTH-01 的合并时序。
- **方案 B**：本卡与 C-AUTH-01 约定**同一次合并**，否则 prod 无法启动。

> 我倾向 A：解耦两卡时序，本卡可独立通过"能启动"验收。请 Claude 确认是否接受由运维用环境变量激活 prod，而非配置写死。

### Q2.【关键】Admin 鉴权凭证方案二选一，需定调

Claude 说"按改动量最小者实现"，我评估后给出明确倾向，请确认：

- **复用现有 `AuthService` token 机制**：问题是普通用户登录后拿到的也是合法 Bearer token，无法区分"普通用户"和"场控/运维"，等于任何登录用户都能进 Admin。**除非 user 表有角色字段**——但我核查过，`user_identity` 表**没有 role/admin 字段**。故此方案需额外改表+改登录逻辑，改动量反而大，且侵入 C-AUTH-01。
- **配置式独立 Admin Token（推荐）**：拦截器只校验请求头 `X-Admin-Token == ${ADMIN_TOKEN}`（环境变量注入）。**改动最小、与用户登录体系完全解耦、不污染 C-AUTH-01**，适合 6/22 体量。

> 我倾向**独立 `X-Admin-Token`**。请确认。若 Claude 坚持复用用户 token，则需要先定义角色字段，这会牵动 C-AUTH-01，时间上不划算。

---

## 二、非阻塞项（我可先按推荐默认实现，确认后微调）

### Q3. Admin 拦截器放行范围
`/api/admin/**` 全拦。但需确认：是否存在**需要对外开放的 admin 子路径**（如大屏只读展示 `/api/admin/live/home` 给前端轮询）？若有，公网前端无法带 `X-Admin-Token`，会被 401 挡住。
- **默认处理**：全部 `/api/admin/**` 一律需要 token。若大屏前端需匿名读，请列出白名单路径，我单独放行。

### Q4. actuator 健康检查（建议项）
共识列为"非 gating 建议项"。我倾向**做**，但只暴露 `/actuator/health`，且 health 端点也纳入 Admin 之外的公开路径（探针需匿名访问）。确认是否一并提交。

### Q5. MyBatis 驼峰映射
现注解式 SQL 里已手工写了 `AS aliasName` 做列名映射（如 `token_id AS tokenId`）。我会在 `application.yml` 加 `mybatis.configuration.map-underscore-to-camel-case=true` 作为双保险，但**不会删除现有手写别名**（避免破坏已通过的测试）。确认无异议。

---

## 三、我的执行计划（共识达成后立即执行）

1. `application.yml`：MySQL 连接（环境变量占位）、JDBC URL 带字符集+时区、`sql.init.mode=never`、移除 `mapper-locations`、无 OSS 段、MyBatis 驼峰映射、profile 说明。
2. `MockAuthProvider` 加 `@Profile`（按 Q1 方案）；**不实现 `DouyinAuthProvider`（属 C-AUTH-01）**，但预留 prod profile 占位与注释。
3. Admin 鉴权 `HandlerInterceptor`（按 Q2 方案），无 token 返回 401。
4. `DEPLOY.md`：JDK17、打包/启动命令、profile 激活方式、完整环境变量清单、建库（utf8mb4）+执行 `db_schema.sql`、Mock 不可对外警示。
5. 本地 `mvn clean package` 验证可编译、测试通过后再推送 `main`，回报 commit ID。

> 仅 Q1、Q2 需明确答复即可解锁开发；Q3–Q5 若无异议我按上述默认值执行。请 John 转 Claude 回应，或直接拍板。
