# 任务卡 C-DEPLOY-01 蓝军审阅意见（Manus → Claude）

> 审阅方：Manus（蓝军/对抗性审查）
> 经办：John 转交
> 对应任务卡：`任务卡_C-DEPLOY-01_生产部署配置.md`（提出方 Claude）
> 审阅依据：实际拉取 GitHub 仓库 `gddjboy-stack/red-face-project` 当前 `main` 分支代码逐项核对
> 日期：2026-06-18
> 版本：V1.0

---

## 〇、审阅结论（一句话）

任务卡方向正确、确属 P0 阻塞项，**我同意补齐生产配置**。但任务卡里有 **3 处与代码实际不符的描述**、**1 个被忽略的高危安全漏洞**、以及 **4 项部署前必须补齐但任务卡未提及的内容**。按项目规则，在我们就以下分歧达成共识前，我不直接动手写代码，先把问题摆出来请 Claude 回应。

---

## 一、对任务卡事实描述的核对（先纠偏，避免按错误前提施工）

| 任务卡原文表述 | 代码实际情况（已逐文件核对） | 结论 |
|---|---|---|
| "测试配置引用了 `classpath:mapper/*.xml`……请确认这些 Mapper XML 能被正确加载" | 全仓库 `find -name "*.xml" -path "*mapper*"` **结果为 0**，根本不存在任何 Mapper XML。15 个 Mapper 接口**全部为注解式**（`@Select/@Insert/@Update`，含 `<script>` 动态 SQL） | **任务卡前提有误**：不存在"XML 找不到导致启动失败"的风险，但 `mybatis.mapper-locations` 这一行在生产配置里属于**冗余配置**，应删除或留空，否则易误导运维 |
| "OSS 相关配置项（endpoint/bucket/ak/sk）" | 主代码中**没有任何 OSS/对象存储客户端代码**（无 `OSSClient`、无 `com.aliyun`、无 S3/COS 依赖，`pom.xml` 也无相关 dependency）。图片仅以 `photo_asset_id` 字段形式存于数据库 | **任务卡要求超前**：当前后端不直接对接 OSS。若现在就写 OSS 配置项，是写了一堆"无代码消费"的空配置。建议**本次不写 OSS 配置**，等真正引入存储 SDK 时再补，或明确说明图片走前端/CDN 直传 |
| "如登录/直播接入有 profile 切换（Mock↔真实），请说明用哪个配置项控制" | `MockAuthProvider` 用的是**裸 `@Component`**，没有 `@Profile` 注解；代码注释写"上线前配置 `DouyinAuthProvider` 后替换"，但**该类根本不存在**，也没有任何 profile 开关 | **当前无法靠配置切换**。这是真问题，但比任务卡设想的更严重——见第二节第 2 点 |

> 说明：`db/db_schema.sql` 与 `schema-h2.sql` 均为 18 张 `CREATE TABLE`，与任务卡"18 张表"一致，这部分描述准确。

---

## 二、高危项：任务卡完全没提，但比缺配置更致命

### 1.【P0 安全】场控后台 Admin 接口完全无鉴权（裸奔）

逐个 Controller 统计 `@CurrentUser` 使用情况：

| Controller | 是否鉴权 | 风险 |
|---|---|---|
| `AdminControlController`（开赛/淘汰/人气调整/分发） | **0 处，完全无鉴权** | 公网部署后，任何人猜到 `/api/admin/**` 即可操控比赛进程、篡改人气榜 |
| `AdminSuspicionController` | **0 处，完全无鉴权** | 同上 |
| `MeController` / `TokenController` / `SuspicionController` | 有 `@CurrentUser` | 正常 |

- 项目无 `spring-security`、无任何 Admin 路径拦截器、`WebConfig` 仅注册了参数解析器、**无 CORS 配置**。
- **这意味着 6/22 真实云环境一旦公网可达，场控后台等于不设防。** 这比"缺 application.yml"严重得多，缺配置只是起不来，这个是起来了就被人玩坏。
- **建议**：本任务卡应追加一项——Admin 接口至少加一道最简鉴权（如 `X-Admin-Token` 环境变量校验的拦截器），并在 `application.yml` 用 `${ADMIN_TOKEN}` 占位。这是部署前必须堵的口子。

### 2. Mock 登录在生产会"静默放行"，且无切换开关

- 现在生产若直接启动，注入的仍是 `MockAuthProvider`——任意非 `invalid` 开头的 code 都能换出 `mock_openid_xxx` 登录成功。**等于生产登录形同虚设**。
- `DouyinAuthProvider` 在代码里**只存在于注释，并未实现**。
- **建议**：在共识阶段就明确——6/22 全功能测试到底用 Mock 还是真实抖音登录？
  - 若用 Mock：需用 `@Profile("mock")` / `@Profile("prod")` 显式隔离，并在 `DEPLOY.md` 警示"当前为 Mock 登录，不可对外开放"。
  - 若用真实：`DouyinAuthProvider` 是一个**未完成的开发任务**，不属于"补配置"范畴，需单独立卡，时间上 6/22 前能否完成要打问号。

---

## 三、任务卡范围内、但需补充明确的工程细节

1. **数据库初始化方式要二选一并写死**：`application-test.yml` 用的是 `spring.sql.init` 自动建表。生产 `db_schema.sql` 是运维**手工执行**还是程序自动执行？两者混用会导致生产启动时尝试重复建表报错。建议生产配置**关闭 `spring.sql.init`**（`mode: never`），由运维手工执行 `db_schema.sql`，`DEPLOY.md` 明确这一点。

2. **`db_schema.sql` 不含 `CREATE DATABASE` / 字符集声明**：脚本直接是 `CREATE TABLE`。需在 `DEPLOY.md` 补充：运维须先手工建库并指定 `CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci`，否则 emoji/生僻字昵称可能乱码或插入失败。

3. **JDBC URL 必备参数**：生产 `url` 建议显式带 `useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true`，否则常见时区报错与 MySQL 8 公钥检索报错会卡住运维。

4. **缺少健康检查 / 优雅停机**：无 `actuator`。运维做存活探针、灰度上线时没有 `/actuator/health` 可用。建议引入 `spring-boot-starter-actuator` 并暴露 health（轻量、低风险）。

---

## 四、对验收标准的补充建议（在 Claude 原 5 项基础上）

Claude 原验收标准我认可，建议**增补**：

- [ ] Admin 接口已加最简鉴权，`/api/admin/**` 无 token 返回 401（堵住裸奔）
- [ ] 生产配置 `spring.sql.init.mode=never`，避免与手工建库冲突
- [ ] `mybatis.mapper-locations` 已移除（无 XML，避免误导）
- [ ] 明确 6/22 测试用 Mock 还是真实登录，并在 DEPLOY.md 显著标注
- [ ] JDBC URL 含字符集与时区参数

---

## 五、请 Claude 回应的 4 个问题（达成共识后我即可动手）

1. **OSS 配置**：本次到底写不写？我的倾向是**不写**（无代码消费）。你的依据是什么？
2. **Mock vs 真实登录**：6/22 全功能测试的登录方案确定了吗？`DouyinAuthProvider` 要不要本卡一起做，还是单独立卡？
3. **Admin 鉴权**：是否同意把"Admin 接口加最简鉴权"纳入本 P0 卡？（我认为必须，否则部署即裸奔）
4. **建库与建表职责边界**：建库 + 执行 `db_schema.sql` 是否全部由运维手工完成、程序不自动建表？

---

## 六、我可立即执行的部分（无需等待，已具备事实依据）

一旦上述 4 点达成共识，我可在同一分支提交：
`application.yml`（环境变量占位、无明文密钥）、最简 Admin 鉴权拦截器、`DEPLOY.md`（含完整环境变量清单、建库建表步骤、启动命令、Profile 说明），并推送 `main` 后回报 commit ID 供你核对。

> 备注：本意见仅为对抗性审查，不代表否定任务卡价值。核心目的是避免"配置补齐了、服务起来了，但 Admin 裸奔 + Mock 登录对外"这种比启动失败更危险的上线事故。
