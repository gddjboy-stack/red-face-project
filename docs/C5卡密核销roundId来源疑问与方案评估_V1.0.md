# C5 卡密核销 `roundId` 来源疑问与方案评估 V1.0

> 作者：Manus AI  
> 日期：2026-06-10  
> 面向对象：Claude（架构师 / 审查 AI）  
> 背景任务卡：C5 — `TokenService.redeem` 卡密核销全流程  
> 当前状态：C5 实现前的阻塞性架构确认问题

## 一、问题背景

在审阅 Claude《C4 通过，C5 开卡》后，Manus 准备按手册 5.2 实现 `TokenService.redeem(String rawInput, String userId, String source)`。但在进入编码前发现一个必须先确认的关键问题：手册给出的 `TokenService.redeem` 方法签名中没有 `roundId` 参数，而当前 `PopularityService.applyChange()` 已要求写入 `round_id`，否则无法把卡密核销产生的人气值计入某一轮的 `player_round_stats`。

该问题涉及 **卡密核销入账轮次的唯一来源**。如果 Manus 自行猜测，将可能违反《红颜局中局开发指导手册 V2.0》中“骨架不清晰时先问，不准自己猜”的工作纪律。因此，本文件用于向 Claude 提交 C5 实现前的疑问与备选方案。

## 二、当前约束

| 约束项 | 当前事实 | 影响 |
|---|---|---|
| `TokenService.redeem` 签名 | `redeem(String rawInput, String userId, String source)` | 方法参数中没有 `roundId` |
| `PopularityService.applyChange` | 需要构造 `PopularityChangeRequest`，并写入 `roundId` | token 入账必须知道所属轮次 |
| `tokens` 表 | 当前 Schema 中没有 `round_id` 字段 | 不能从卡密表直接读取轮次 |
| C5 验证要求 | 正常核销、重复核销、并发核销、防爆破锁定 | 测试中必须能明确人气入账到某轮 |
| 手册纪律 | 骨架不清晰时先问，不准自行改架构 | 需要 Claude 明确批准实现路径 |

## 三、可选方案评估

| 方案 | 做法 | 优点 | 风险 |
|---|---|---|---|
| 方案 A | 在 `TokenService` 中额外注入 `CollectStateService`，核销时读取当前 `collect_state.round_id` 作为入账轮次 | 不改 `redeem()` 方法签名，不改 Schema；业务上最自然，卡密核销归属当前直播轮次 | 超出手册 5.2 原骨架依赖，需要 Claude 批准 `TokenService` 可依赖 `CollectStateService` |
| 方案 B | 给 `redeem()` 增加 `roundId` 参数 | 入账轮次最显式 | 改变手册给定方法签名，可能违反“照抄骨架”纪律；H5/后台调用方也要同步改 |
| 方案 C | 给 `tokens` 表增加 `round_id` 字段 | 卡密与轮次绑定清晰 | 修改已通过审查的 Schema，影响卡密生成与阿奇索批次逻辑，风险最大 |
| 方案 D | 暂时写死 `roundId=1` 或在测试中假设首轮 | 实现最快 | 架构上明显不可靠，彩排后容易形成隐患，不建议 |

## 四、Manus 的蓝军建议

Manus 建议采用 **方案 A**：保持 `TokenService.redeem(String rawInput, String userId, String source)` 方法签名不变，但在 `TokenService` 内部读取当前场控状态的 `round_id`，作为 token 人气入账轮次。如果当前场控状态不存在，或 `round_id` 为空，则返回失败结果。

这样做的理由是：第一，它不改变手册给出的 `redeem()` 方法签名，降低接口扩散风险；第二，它不修改已经通过 Claude 真实执行验证的数据库 Schema；第三，卡密核销发生在直播/彩排当前上下文中，读取当前场控轮次作为入账轮次，在业务语义上较自然；第四，它仍然可以通过 JUnit 明确验证正常核销、重复核销、并发核销与防爆破锁定。

## 五、请求 Claude 确认的问题

请 Claude 明确确认以下事项：

| 编号 | 待确认问题 | Manus 建议 |
|---|---|---|
| Q1 | C5 的 `TokenService` 是否允许额外依赖 `CollectStateService` 来读取当前 `round_id`？ | 建议允许 |
| Q2 | 若当前 `collect_state` 不存在或 `round_id` 为空，`redeem()` 是否应返回失败结果而不是抛出未处理异常？ | 建议返回 `fail("round_not_set", "当前轮次未设置")` |
| Q3 | C5 JUnit 的正常核销、重复核销、并发核销用例，是否可以先通过 `collectStateService.setCollectTarget("pool", null, roundId, operatorId)` 设置当前轮次，再执行核销？ | 建议可以 |
| Q4 | C5 是否仍保持 `redeem(String rawInput, String userId, String source)` 签名不变？ | 建议保持不变 |

## 六、若 Claude 批准方案 A，Manus 的实现计划

若 Claude 批准方案 A，Manus 将按以下方式继续 C5：`TokenService` 使用构造器注入 `TokenMapper`、`PopularityService`、`FailureCounter`、`UserPhotoCollectionMapper` 和 `CollectStateService`。核销流程仍严格按手册骨架执行：先规范化输入，再检查防爆破锁定，然后通过 `UPDATE tokens SET status='used' ... WHERE token_id=? AND status='unused'` 原子抢占。只有抢占成功后才读取 token 详情、读取当前 `collect_state.round_id`、调用 `PopularityService.applyChange(source="token", rawValue=t.getPoints(), idempotencyKey="token_" + token)` 入账，最后插入 `user_photo_collection`，唯一键冲突捕获并忽略。

并发核销测试仍将使用 `CountDownLatch` 同时发车，断言两个线程中有且只有一个成功，且 `popularity_ledger` 中 `token_卡密` 幂等键只有一条流水。

---

**请 Claude 先确认上述 Q1 至 Q4。收到确认后，Manus 将继续执行 C5 编码。**
