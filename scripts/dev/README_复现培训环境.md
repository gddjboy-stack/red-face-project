# 复现培训与验证环境（沙盒版）

本目录保存的是**培训与取证专用**的临时环境脚本，不属于生产部署链路。之所以入库，是因为原先它们只存在于 Manus 沙盒中，沙盒回收后任何人都无法复现当时的截图与验证结论。

## 环境说明与限制

该环境使用 `spring.profiles.active=test`，数据库为 **H2 内存库**，进程一停数据全部消失。它只适用于培训演示与功能取证，**不可用于 8/9 正式场次**。

## 启动步骤

后端启动时**必须显式传入两个令牌环境变量**，这是最容易漏的一步：

```bash
cd backend/redface-backend
nohup env DISPLAY_TOKEN=display2026 ADMIN_TOKEN=train2026 \
  java -cp "target/classes:target/test-classes:$(cat /tmp/cp.txt)" \
  -Dspring.profiles.active=test \
  com.redface.RedfaceBackendApplication > /tmp/backend.log 2>&1 &
```

若漏掉 `DISPLAY_TOKEN`，大屏换票会 fail-closed 返回 40110「展示端未启用」，而报错信息不会提示是环境变量缺失，容易误判为令牌输错。

前端使用培训专用配置启动：

```bash
cd frontend/control-admin
pnpm vite --config vite.config.training.ts --host 0.0.0.0
```

## 灌数据

```bash
bash scripts/dev/seed_training_data.sh      # 基础：6 选手 + 商品原价 + 轮次
python3 scripts/dev/prep_evidence_data.py   # 补至 12 选手、入轮、灌人气
```

## 已知踩坑（按踩到的顺序记录）

| 现象 | 实际原因 |
| --- | --- |
| 新增选手成功但大屏不显示 | 榜单取数 join `player_round`，必须另外调入轮接口 |
| 入轮接口 404 | 路径是单数 `player-round`，不是 `player-rounds` |
| 入轮返回 40000 分队保存失败 | `playerStatus` 合法值为 `normal`/`free`/`eliminated`，不含 `active` |
| 灌人气报错 | 应用 `/api/admin/popularity/manual-adjust`（字段 `rawValue`）；`adjust-coefficient` 是加成系数，上限 ±1.0 |
| 大屏 board 接口 401 | 需先 `POST /api/display-auth/session` 换取 Cookie `RF_DISPLAY`，有效期 43200 秒 |
| 服务空闲一段时间后数据全空 | H2 内存库随连接池空闲回收而释放，属该 profile 固有行为 |

其中第一条不只是环境问题，它在生产环境同样存在，且表现为「静默漏人」，已在 C20-9 交付报告中建议立卡处理。
