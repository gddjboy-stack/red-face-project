# 红颜局中局 后端部署说明（DEPLOY.md）

> 任务卡：C-DEPLOY-01
> 适用：运维方将后端部署上线至腾讯云生产环境
> 本文档面向运维人员，按步骤执行即可。所有密钥/密码一律通过环境变量注入，仓库内无任何明文。

---

## 一、运行环境要求

| 项 | 要求 |
|---|---|
| JDK | **JDK 17**（项目以 Java 17 编译，`pom.xml` 中 `java.version=17`） |
| 数据库 | MySQL 8.x 或 MariaDB 10.11（已在 MariaDB 10.11 实跑验证） |
| 构建工具 | Maven 3.6+（仅打包阶段需要，运行只需 JDK） |

---

## 二、打包

在 `backend/redface-backend/` 目录下执行：

```bash
mvn clean package
```

- 产物路径：`target/redface-backend-0.0.1-SNAPSHOT.jar`（约 27 MB，可执行 fat jar）。
- 若打包机已验证测试，可用 `mvn clean package -DskipTests` 跳过测试加速。

---

## 三、初始化数据库（运维手工执行，程序不自动建表）

> 重要：生产配置 `spring.sql.init.mode=never`，**程序不会自动建表**。建库与建表均由运维手工完成。

1. **建库**（务必指定 utf8mb4，否则昵称中的 emoji/生僻字会乱码或写入失败）：

   ```sql
   CREATE DATABASE redface CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   ```

2. **建账号并授权**（密码请自行设置强口令，下面仅为示例）：

   ```sql
   CREATE USER 'redface'@'%' IDENTIFIED BY '<强密码>';
   GRANT ALL PRIVILEGES ON redface.* TO 'redface'@'%';
   FLUSH PRIVILEGES;
   ```

3. **执行建表脚本**（仓库 `db/db_schema.sql`，共 18 张表）：

   ```bash
   mysql -h <DB_HOST> -P <DB_PORT> -u redface -p redface < db/db_schema.sql
   ```

   执行后 `SHOW TABLES;` 应返回 18 张表。

---

## 四、需要运维设置的环境变量清单

| 变量名 | 含义 | 是否必填 | 示例/默认 |
|---|---|---|---|
| `DB_HOST` | 数据库主机地址 | 是 | `127.0.0.1` |
| `DB_PORT` | 数据库端口 | 否 | 默认 `3306` |
| `DB_NAME` | 数据库名 | 否 | 默认 `redface` |
| `DB_USERNAME` | 数据库用户名 | **是** | `redface` |
| `DB_PASSWORD` | 数据库密码 | **是** | （强口令，勿入库/勿明文） |
| `SERVER_PORT` | 服务监听端口 | 否 | 默认 `8080` |
| `ADMIN_TOKEN` | **场控后台 Admin 鉴权令牌** | **是（生产）** | 足够强的随机串 |
| `SPRING_PROFILES_ACTIVE` | 激活 profile | 见第五节 | 真实登录测试时设 `prod` |
| `DOUYIN_APP_ID` | 抖音小程序 AppID（prod 下需要） | prod 时是 | 由 John 提供 |
| `DOUYIN_APP_SECRET` | 抖音小程序 AppSecret（prod 下需要） | prod 时是 | 由 John 提供，严禁明文 |

> 生成强随机 `ADMIN_TOKEN` 示例：`openssl rand -hex 24`

---

## 五、启动命令与 Profile 说明

### 5.1 默认启动（Mock 登录，仅限内部联调，不可对公众开放）

```bash
export DB_HOST=<...> DB_USERNAME=<...> DB_PASSWORD=<...>
export ADMIN_TOKEN=<强随机串>
java -jar target/redface-backend-0.0.1-SNAPSHOT.jar
```

- 默认（不激活 prod）使用 **MockAuthProvider**：任意合法 code 都能登录成功，**登录形同虚设，严禁对公众开放**。
- 仅用于后端独立联调、6/22 前真实登录（C-AUTH-01）尚未就绪时的兜底测试。

### 5.2 生产启动（真实抖音登录，6/22 正式测试）

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=<...> DB_USERNAME=<...> DB_PASSWORD=<...>
export ADMIN_TOKEN=<强随机串>
export DOUYIN_APP_ID=<...> DOUYIN_APP_SECRET=<...>
java -jar target/redface-backend-0.0.1-SNAPSHOT.jar
```

> **真实登录已实现（C-AUTH-01）**：`prod` profile 下登录由 `DouyinAuthProvider` 接管（已合并进 main），调用抖音官方 `code2session` 用 code 换取 openid。启用 `prod` 必须同时配置 `DOUYIN_APP_ID` 与 `DOUYIN_APP_SECRET`，否则登录会失败报错（不会静默放行）。
>
> **L3 安全护栏（fail-fast）**：`prod` profile 启动时强制校验 `ADMIN_TOKEN` 非空，**若未配置则拒绝启动**（提示"生产环境必须配置 ADMIN_TOKEN"），从代码层面杜绝"忘配即 Admin 裸奔"。请务必在 `prod` 启动前设好 `ADMIN_TOKEN`。

---

## 六、Admin 后台安全（场控/运维务必知悉）

- 场控后台接口 `/api/admin/**` 已加鉴权拦截器：请求头须携带 `X-Admin-Token: <ADMIN_TOKEN>`，否则返回 `401`。
- 若未设置 `ADMIN_TOKEN` 环境变量，拦截器会**放行并打印告警日志**（仅为本地/联调便利）；**生产必须设置 `ADMIN_TOKEN`**，否则后台等于不设防。
- 建议运维在 **Nginx 层对 `/api/admin/**` 额外加 IP 白名单**（仅放行场控/运维 IP），与 token 形成双保险。

---

## 七、健康检查

- 探针地址：`GET /actuator/health`，匿名可访问，正常返回 `{"status":"UP"}`。
- 仅暴露 `health` 端点，未暴露 `env/beans/configprops/mappings` 等敏感端点。

---

## 八、本地验证记录（供参考）

本配置已在沙箱用 **MariaDB 10.11 + JDK17** 实跑验证：

- 手工建库（utf8mb4）+ 执行 `db_schema.sql`（18 表）成功；
- 默认 profile 应用约 3 秒正常启动；
- `/api/admin/collect-state` 无 token / 错 token 均返回 `401`，正确 `X-Admin-Token` 返回 `200`；
- `/actuator/health` 匿名返回 `200 {"status":"UP"}`；
- 全部 67 个单元/集成测试通过。
