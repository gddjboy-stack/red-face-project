# 《Manus 对 Claude 开发指导手册 V1.0 的蓝军审查意见_V1.0》

**项目名称：** 红颜局中局  
**审查对象：** 《红颜局中局小程序开发指导手册 V1.0》  
**审查人：** Manus AI  
**日期：** 2026-06-08  
**审查目的：** 在正式编码前，对 Claude 提供的开发指导手册进行接口、数据模型、MVP 范围和外部链路一致性审查，明确我不同意、不理解或需要 Claude 进一步澄清的事项。

---

## 1. 总体结论

我总体认可 Claude 手册的工程化方向，尤其是**人气值单一入口、整数计算、幂等键、流水账只增不改、模块自检报告**这几项原则。这些原则能够显著降低直播选秀项目中最容易出错的“积分算错、重复加分、事后无法追溯”等风险。

但是，我不建议 Manus 立即完全按该手册开工。原因是手册中有几处接口与我们此前已经确认的业务口径不一致，尤其集中在**直播免费互动归因、团队人气池、卡密格式、H5 中间页传参、技术栈假设**五个方面。如果不先让 Claude 修订，后续代码很可能出现“接口很规范，但业务对象建错”的问题。

| 优先级 | 结论类型 | 数量 | 是否阻塞开工 | 说明 |
|---|---:|---:|---|---|
| P0 | 必须修改 | 7 项 | 是 | 涉及接口签名、数据表结构、卡密链路和免费互动归因。 |
| P1 | 需要 Claude/John 确认 | 6 项 | 部分阻塞 | 涉及衰减边界、任务系数、卧底投票规则、退款优先级等。 |
| P2 | 可接受但建议优化 | 5 项 | 否 | 主要是工程实现细节和排期措辞。 |

---

## 2. 我认可并建议保留的部分

Claude 手册中最有价值的部分，是把人气值系统设计为一个**强约束的会计系统**。我建议保留以下原则，并把它们作为后续代码审查的硬标准。

| 手册设计 | 我的意见 | 原因 |
|---|---|---|
| 所有人气值变化只能经过 `apply_popularity_change()` | 认可 | 避免前端、核销、场控、退款等模块各自改人气，导致口径漂移。 |
| 人气值和金额全部用整数 | 认可 | 能避免 `19.9 * 1000` 这类浮点误差。 |
| 幂等键唯一约束 | 认可 | 能防止直播事件重放、核销重复点击、退款回调重复触发。 |
| `popularity_ledger` 只增不改 | 认可 | 有利于赛后复盘、争议追溯和数据重算。 |
| 每个模块完成后提交自检报告 | 认可 | 符合 John 作为项目经理对进度和风险可见性的要求。 |
| MVP 明确可推迟真实直播 API、卧底识破、退款完整功能 | 基本认可 | 但退款最迟必须在正式上线前闭环，不能长期留空。 |

---

## 3. P0：我不同意或认为必须修改的事项

### 3.1 M-LIVE 的事件模型不应把点赞建模为带 `player_id` 的逐条事件

Claude 手册中 `on_live_event(event)` 的示例为：

> `event = {"type":"gift"|"like"|"comment", "player_id":int, "value":int, "msg_id":str, "ts":int}`

我不同意这个接口设计，至少对 `like` 不成立。我们此前已经确认，点赞不能精确归因到“某个用户给某个选手点了赞”，只能采用**时间窗口内直播间总点赞增量**模型。此前我对 Claude 审查意见的回复中已经写明：点赞事件无法提供用户级归因，因此如保留点赞，只能把直播间总点赞增量统一归给当前集赞窗口的目标对象。[3]

因此，M-LIVE 应拆成两类输入模型。礼物如果官方接口可以给出明确的礼物事件，则可按事件处理；点赞应按聚合增量处理，由 M-OP 的当前场控状态决定归属。

| 当前手册接口 | 风险 | 建议改法 |
|---|---|---|
| `on_live_event(event)` 中要求 `like` 带 `player_id` | 与此前确认的技术口径冲突，可能误导开发成“逐用户点赞归因”。 | 增加 `on_live_metric_delta(metric_type, delta, total, occurred_at, provider_event_id)`。 |
| `simulate_inject(player_id, event_type, value)` | 模拟器也会误导为所有互动都有 player_id。 | 模拟器应支持“按当前场控目标注入”，不要求手动传 player_id。 |

建议 Claude 将直播免费互动接口改为：

```python
def on_live_metric_delta(
    metric_type: str,        # "like_delta" | "comment_delta"
    delta_value: int,        # 本窗口新增量
    total_value: int,        # 平台侧累计值，可选，用于校验
    occurred_at: int,
    idempotency_key: str
) -> dict:
    """不传 player_id，由 M-CORE 按 occurred_at 查询 M-OP 当前场控目标。"""
```

### 3.2 `apply_popularity_change(player_id=...)` 不能覆盖团队池和赛事总池

Claude 手册把人气值变化入口设计为 `apply_popularity_change(player_id, source, raw_value, ...)`。这个接口对个人加分很清晰，但无法自然表达以下三类已确认需求：**团队人气值、卧底人气值、赛事总人气池**。

在 V2.2/V2.4 的功能定义中，小程序已被定义为直播伴随工具，并需要支持个人、团队、赛事总三档人气看板；卡密链路也已更新为 H5 中间页 + 阿奇索变量传参。[2] 如果核心接口只能接受 `player_id`，就会迫使团队池和赛事池伪装成某个选手，后续平分、审计和展示都会混乱。

我建议把入口接口从“选手中心”改成“目标对象中心”：

```python
def apply_popularity_change(
    target_type: str,         # "player" | "team" | "spy" | "pool"
    target_id: int | None,    # pool 可为空；team/player/spy 必填
    source: str,
    raw_value: int,
    occurred_at: int,
    idempotency_key: str,
    round_id: int | None = None,
    operator_id: str | None = None,
    reason: str | None = None,
    metadata: dict | None = None
) -> dict:
    pass
```

这样才能让“团队人气值先进入团队池，再由后台手动或自动平分给团队成员”成为一等公民，而不是靠 `player_id` 的特殊约定硬凑。

### 3.3 数据库 Schema 缺少团队人气、赛事总池和归属对象字段

Claude 手册中的 `popularity_ledger` 只有 `player_id`、`attributed_to`，`player_round_stats` 只有 `individual_popularity` 和 `spy_popularity`。这不足以支撑 V2.2 以后确认的三档看板和团队人气分配。

| 当前字段 | 问题 | 建议新增或调整 |
|---|---|---|
| `popularity_ledger.player_id` | 对 team/pool 归属不自然。 | 改为 `target_type`、`target_id`，并允许 `player_id` 作为可选冗余字段。 |
| `player_round_stats` | 只适合选手个人统计。 | 新增 `team_round_stats(team_id, round_id, team_popularity, distributed_popularity)`。 |
| 无赛事总池表 | 无法记录非 PK 时段人气。 | 新增 `pool_round_stats(round_id, pool_popularity)` 或用 ledger 汇总。 |
| 无团队平分流水 | 团队人气平分后无法追溯。 | 新增 `distribution_batch_id` 或 `source="team_distribution"` 的二级流水。 |

我的建议是把 `popularity_ledger` 分成“原始归属流水”和“分配流水”两类，或者至少通过 `metadata` 和 `distribution_batch_id` 记录平分来源。否则团队平分后，赛后很难解释“某位选手的这笔人气来自哪一次团队池分配”。

### 3.4 卡密格式与 V2.1/V2.4 不一致

Claude 手册中 `generate_tokens()` 的说明写的是：

> `格式 XXXX-XXXX-XXXX-XXXX，字符集排除 0/1/I/L/O`

但我们此前已确认卡密格式采用 `RFZJ-XXXX-XXXX-XXXX`，并且阿奇索模板中使用 `{$卡券信息}` 变量将卡密注入发货内容。[2] [4] 这个差异不大，但必须统一，否则导出的卡密格式、前端校验规则、用户手动输入提示会不一致。

我建议 Claude 将卡密格式统一为：

```text
RFZJ-XXXX-XXXX-XXXX
```

同时前端输入框应兼容用户粘贴时带空格、全角字符、换行等情况，后端统一做规范化，例如去空格、转大写、校验前缀。

### 3.5 M-TOKEN 缺少阿奇索 `oid`、SKU 和批次字段

阿奇索官方文档确认内容模板可以创建、选择默认或随机模板，并通过插入变量构造发货文本。[1] John 提供的截图显示可用变量包含 `{$卡券信息}` 和 `{$订单号}`。我们在 V2.4 已经把发货链接标准化为：

```text
https://hjzj.com/go?t={$卡券信息}&oid={$订单号}
```

Claude 手册的 `tokens` 表只有 `token, player_id, points, status, used_at, user_id`，无法完整支持阿奇索链路中的订单追溯与对账。我建议至少增加以下字段：

| 字段 | 类型建议 | 目的 |
|---|---|---|
| `sku_id` | string / nullable | 标记该卡密对应哪个抖店 SKU，支撑“SKU 绑定选手”。 |
| `aqiso_batch_id` | string / nullable | 标记导出给阿奇索的卡密批次，便于售后排查。 |
| `order_id` | string / nullable | 用户通过 H5 或核销页带入 `oid` 后绑定订单号。 |
| `redeem_source` | enum | 区分 H5 自动带入、手动输入、后台补录。 |
| `photo_asset_id` | string / nullable | 写真资产与卡密权益绑定，避免只存一个裸 URL。 |

### 3.6 H5 中间页应以 `t` 和 `oid` 为准，不能只写 token

Claude 手册中 M-FE3 的描述是“解析 token 参数后跳转小程序核销”。这个描述偏旧，需要对齐 V2.4。按照阿奇索最终模板，H5 中间页应解析的是 `t` 和 `oid`，然后再把 `t` 转换为小程序核销页能够识别的 `token` 参数。

建议 Claude 将 H5 中间页接口明确为：

```text
外部入口：/go?t=RFZJ-XXXX-XXXX-XXXX&oid=订单号
小程序入口：/pages/redeem/index?token=RFZJ-XXXX-XXXX-XXXX&oid=订单号
```

此外，我不建议把“自动唤起小程序”写成一定成功。抖音 Schema 的生成和端内外唤起可能受到平台权限、环境、客户端版本影响。H5 页面必须有两个按钮：**一键打开小程序**和**复制卡密后手动领取**。

### 3.7 手册假设 Python 函数签名，但实际技术栈需要先确认

Claude 手册直接给出 Python 函数签名，并要求 Manus “严格实现这两个函数签名”。我理解 Claude 是用 Python 表达接口契约，但如果最终项目采用小程序云开发、Node/TypeScript 服务端、或 WebDev 模板，那么“严格实现 Python 函数签名”会造成歧义。

我的建议是：Claude 可以保留 Python 伪代码，但必须增加一句：**函数签名表达的是业务契约，不限定具体语言；如果实际后端采用 TypeScript/Node，则以同名 service 方法和同字段 API contract 实现。** 否则，在代码审查时容易出现“业务实现正确但语言形式不一致”的争议。

---

## 4. P1：我需要 Claude 或 John 进一步确认的事项

### 4.1 衰减算法的临界值和前一轮为 0 的处理

Claude 手册写道：“本轮人气值超过上轮 ×1.5 阈值，超出部分按 0.1 计分”。这里需要明确三个边界条件。

| 待确认问题 | 建议口径 |
|---|---|
| 等于 1.5 倍时是否触发？ | 建议仅 `>` 触发，`=` 不触发。 |
| 上轮人气值为 0 时如何处理？ | 建议设置最低基准值，避免任何增长都被衰减。 |
| 退款或手动扣分导致本轮为负数怎么办？ | 建议积分最低不低于 0，负值只影响流水和审计。 |

### 4.2 `set_coefficient()` 缺少任务 ID，可能重复加减系数

当前接口为：

```python
def set_coefficient(player_id, round_id, task_type, completed, operator_id)
```

如果同一轮有多个任务，或者同一任务被重复点击完成/失败，系统无法判断是否重复调整。我建议增加 `task_id` 和幂等键，并把系数变化写入独立 `coefficient_ledger`。

### 4.3 团队人气平分应是系统动作还是手动动作

John 之前提出“工作人员在后台可以把团队人气值自动或手动平分给团队成员”。Claude 手册目前只有 `manual_adjust()`，没有团队池分配接口。我建议新增：

```python
def distribute_team_popularity(team_id: int, round_id: int, method: str, operator_id: str, reason: str) -> dict:
    """把团队池的人气按 equal/custom 分配给成员，并生成分配批次流水。"""
```

这个动作可以最终仍调用 `apply_popularity_change()`，但不应让场控人员逐个手动调分，否则操作成本高且容易出错。

### 4.4 卧底识破投票规则需要按“每组”建模

John 原始需求是“观众可以选择每一组的 1 到多位选手投票”。Claude 手册的 `submit_suspicion(user_id, suspect_ids, round_id)` 只表达“一轮一次，多选若干人”，没有表达“每组”的约束。虽然 M-SPY 可在 6/14 后补，但接口一旦写错，后面会返工。

建议至少把投票结构改为：

```json
{
  "round_id": 1,
  "user_id": "xxx",
  "choices": [
    {"team_id": 1, "suspect_ids": [101, 102]},
    {"team_id": 2, "suspect_ids": [201]}
  ]
}
```

### 4.5 退款模块是否真的可以 6/14 后补

我同意彩排时可以不用完整退款模块，但如果 6/23 正式上线涉及真实付费商品，退款扣分必须闭环。此前我们已经建议采用“支付成功即加分 + 退款触发扣分”的方案，并细分付款后、发货后、收货后三种退款时序。[5]

因此我建议 Claude 在手册中把退款标为“彩排可后补，正式上线 P0”，而不是笼统放在第六优先级。

### 4.6 阿奇索文档适用平台与抖店实操仍需 John 确认

John 提供的阿奇索文档标题是“多多、闲鱼、京东如何设置内容模板”，并说明这些平台如何设置内容模板。[1] 商家回复称“这个形式只支持聊天窗口发”。从目前截图看，抖店/聊天窗口应该可用，但我建议 John 仍向商家确认两点：第一，抖店订单是否可以稳定触发该内容模板；第二，`{$卡券信息}` 和 `{$订单号}` 在抖店聊天窗口里是否都会被正确替换。

---

## 5. P2：可接受但建议优化的事项

| 事项 | 当前手册写法 | 建议优化 |
|---|---|---|
| Redis 作为强依赖 | 架构图将 Redis 写为实时缓存 | 6/14 彩排阶段可以把 Redis 设为可选项，先用 MySQL + 内存缓存或轮询，避免部署复杂度过高。 |
| API 网关过早抽象 | 手册提出统一 API 网关 | MVP 可以实现为同一后端服务的路由层，不必单独拆网关。 |
| “前一个没通过不做下一个” | 严格串行 | 原则上同意，但 DB Schema、接口类型定义、自检脚本可以先并行准备，避免等待 Claude 审查时完全停工。 |
| `photo_url` 裸字段 | `redeem_token()` 返回 `photo_url` | 建议返回 `asset_id`、`preview_url`、`download_url` 或 `gallery_items`，便于未来写真收藏。 |
| 操作日志“不可篡改” | 手册写不可篡改 | MVP 可先做到 append-only + 禁止前端删除；真正防篡改可后续加哈希链或定期导出。 |

---

## 6. 我建议发给 Claude 的具体问题清单

为了减少 John 反复转述，我建议直接把以下问题发给 Claude，请 Claude 修订手册 V1.1 后再让 Manus 开工。

| 编号 | 给 Claude 的问题 | 期望 Claude 回复形式 |
|---:|---|---|
| Q1 | 是否同意把 M-LIVE 的点赞/留言模型从 `player_id` 事件改为“直播间总增量 + 场控当前目标归属”？ | 修改接口示例和模拟器接口。 |
| Q2 | 是否同意把 `apply_popularity_change(player_id,...)` 改为 `target_type + target_id` 模型，以覆盖 player/team/spy/pool？ | 给出新函数签名。 |
| Q3 | 是否同意补充 `team_round_stats`、`pool_round_stats`、`distribution_batch_id` 或等价字段？ | 给出新版 Schema。 |
| Q4 | 是否同意统一卡密格式为 `RFZJ-XXXX-XXXX-XXXX`，并把 H5 参数明确为 `t` 和 `oid`？ | 修改 M-TOKEN 与 M-FE3。 |
| Q5 | Python 函数签名是否只是伪代码契约？如果实际采用 TypeScript/Node，Claude 是否接受同字段 service contract？ | 明确技术栈约束。 |
| Q6 | 衰减算法在等于 1.5 倍、上轮为 0、扣分为负数时如何处理？ | 给出边界规则。 |
| Q7 | 团队人气平分是否需要独立接口 `distribute_team_popularity()`？ | 给出接口或解释为何只用 manual_adjust。 |
| Q8 | 卧底识破是否应按“每组 1 到多位”建模，而不是简单 suspect_ids 列表？ | 给出新版投票数据结构。 |
| Q9 | 退款模块是否应标为“彩排后补、正式上线前必须完成”？ | 调整里程碑。 |

---

## 7. 我的执行建议

在 Claude 修订手册 V1.1 前，我建议 Manus **暂不正式进入生产代码开发**，最多只做两类不易返工的准备工作：第一，搭建项目目录、测试框架和配置文件框架；第二，整理数据库迁移草案但不执行最终 Schema。原因是当前最大不确定性不是编码能力，而是**核心数据对象是否建模正确**。

如果 John 因时间压力要求立即推进，我建议采用“最小安全开工”策略：先实现不依赖争议字段的公共基础设施，例如配置加载、日志、自检脚本、整数换算工具和幂等键工具。等 Claude 明确 Q1-Q4 后，再写 M-CORE、M-LIVE 和 M-TOKEN。

---

## 8. References

[1]: https://www.agiso.com/detail/160.html "阿奇索：多多、闲鱼、京东如何设置内容模板"  
[2]: https://github.com/gddjboy-stack/red-face-project/blob/main/%E7%BA%A2%E9%A2%9C%E5%B1%80%E4%B8%AD%E5%B1%80%E5%B0%8F%E7%A8%8B%E5%BA%8F%E5%8A%9F%E8%83%BD%E5%AE%9A%E4%B9%89%E6%96%87%E6%A1%A3_V2.4.md "红颜局中局小程序功能定义文档_V2.4"  
[3]: https://github.com/gddjboy-stack/red-face-project/blob/main/Manus%E5%AF%B9Claude%E6%9C%80%E6%96%B0%E5%AE%A1%E6%9F%A5%E6%84%8F%E8%A7%81%E7%9A%84%E5%9B%9E%E5%A4%8D_V1.0.md "Manus 对 Claude 最新审查意见的回复_V1.0"  
[4]: https://github.com/gddjboy-stack/red-face-project/blob/main/%E7%BA%A2%E9%A2%9C%E5%B1%80%E4%B8%AD%E5%B1%80%C2%B7%E9%98%BF%E5%A5%87%E7%B4%A2%E5%AE%98%E6%96%B9%E6%96%87%E6%A1%A3%E6%B7%B1%E5%BA%A6%E8%A7%A3%E8%AF%BB%E4%B8%8E%E9%85%8D%E7%BD%AE%E6%8C%87%E5%8D%97_V1.0.md "红颜局中局·阿奇索官方文档深度解读与配置指南_V1.0"  
[5]: https://github.com/gddjboy-stack/red-face-project/blob/main/archive/Manus%E5%AF%B9Claude%E5%AE%A1%E6%9F%A5%E6%84%8F%E8%A7%81%E7%9A%84%E5%9B%9E%E5%A4%8D%E4%B8%8E%E5%BB%BA%E8%AE%AE.md "Manus 对 Claude 审查意见的回复与建议"

---

## 9. 版本记录

| 版本 | 日期 | 作者 | 内容 |
|---|---|---|---|
| V1.0 | 2026-06-08 | Manus AI | 首次生成对 Claude 开发指导手册 V1.0 的蓝军审查意见。 |
