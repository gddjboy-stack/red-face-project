# red-face-project（红颜局中局）

直播选秀项目代码仓库。仓库根目录同时存放了历史策划文档（大量中文 `.md` 文件），**源代码全部集中在下方三个目录中**，从根目录列表里不易一眼看到，故在此建立索引。

## 源代码在哪里

| 内容 | 路径 | 直达链接 |
| --- | --- | --- |
| 后端（Spring Boot + MyBatis + MySQL/H2） | `backend/redface-backend/` | [打开](backend/redface-backend) |
| 后端 Java 源码（206 个文件） | `backend/redface-backend/src/main/java/com/redface/` | [打开](backend/redface-backend/src/main/java/com/redface) |
| 后端单元测试 | `backend/redface-backend/src/test/java/com/redface/` | [打开](backend/redface-backend/src/test/java/com/redface) |
| 场控后台前端（Vue 3 + Vite + TS） | `frontend/control-admin/` | [打开](frontend/control-admin) |
| 抖音小程序 | `frontend/douyin-miniprogram/` | [打开](frontend/douyin-miniprogram) |
| H5 桥接层 | `frontend/h5-bridge/` | [打开](frontend/h5-bridge) |
| 数据库 schema（生产） | `db/db_schema.sql` | [打开](db/db_schema.sql) |
| 数据库 schema（测试 H2） | `backend/redface-backend/src/test/resources/schema-h2.sql` | [打开](backend/redface-backend/src/test/resources/schema-h2.sql) |
| 部署说明 | `backend/redface-backend/DEPLOY.md` | [打开](backend/redface-backend/DEPLOY.md) |

## 后端源码分层

| 目录 | 职责 |
| --- | --- |
| `controller/` | HTTP 端点，仅做参数接收与转发 |
| `service/` | 业务逻辑与事务边界，规则校验都在这一层 |
| `mapper/` | MyBatis 注解式 SQL，读写数据库 |
| `dto/` | 请求与响应对象。**大屏与后台使用分离的 DTO，禁止复用**（详见 C20-10 交付报告第四节） |
| `query/` | 只读查询服务（大屏、看板、直播首页） |
| `entity/` | 数据库实体 |

## 关键工程约定

约定一：**人气与系数分离存储，账本存裸值、读取时折算。** 数据库里保存的是未经系数加工的原始人气，系数单独存列，展示与分配时才乘上去。这样运营中途调整系数不会影响已入账的历史流水。相关折算表达式见 `mapper/C9QueryMapper.java`。

约定二：**大屏响应不得复用后台 DTO。** 赛制机密字段（如卧底识破状态）必须在类型定义层面不存在于大屏响应中，而不是靠「运行时不赋值」。后者会被 Jackson 照样序列化输出，观众打开浏览器控制台即可看到字段存在。

约定三：**所有写账本的操作都需要幂等键**，重复提交返回既有结果而非重复入账。

## 最近交付

| 卡片 | 内容 | 状态 | 报告 |
| --- | --- | --- | --- |
| C20-9 | 大屏回归修复、直播录入与校准 | 已验收 | — |
| C20-10 | 投票参与人数录入、识破标记、卧底人气系数 | 已验收 | [交付报告](docs/C20-10_交付报告_投票参与人数与卧底人气系数.md) ・ [实测证据](evidence/C20-10) |
| C20-11 | 团队人气折算不一致修复 | **未开工**，3 项阻塞待裁定 | [反对意见与链路推演](docs/C20-11_开工前反对意见与链路推演_v1.md) |

## 本地运行

后端需 JDK 17。测试使用内存 H2，无需外部数据库：

```bash
cd backend/redface-backend
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn test
```

前端场控页：

```bash
cd frontend/control-admin
pnpm install && pnpm dev
```

## 协作分工

项目由 3 位人类与 2 个 AI 协作。John 任项目经理，管控进度、人力与成本；Vincent 负责赛制流程与娱乐性设计；彬少负责平面与 UI 设计。Claude 负责任务卡下发与代码验收，Manus 负责实现与取证。任务卡的实现遵循「开工前先交反对意见与链路推演，待裁定后再动代码」的流程。