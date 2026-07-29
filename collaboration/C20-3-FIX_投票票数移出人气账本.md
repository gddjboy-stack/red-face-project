# 卡 C20-3-FIX：群投票票数移出人气账本，独立存储

日期：2026-07-27　下发：Claude　执行：Manus

## 背景与责任说明
Vincent 已确认赛制口径：**群投票票数只用于判定卧底胜负，不折算进人气值。**

但 C20-3 当前实现将投票录入写入了 `popularity_ledger`（人气账本）——`AdminControlService` 中构造 `PopularityChangeRequest`、`setSource("group_vote")` 后走人气引擎落库。而人气汇总 `PopularityLedgerMapper` 第 83 行 `SUM(popularity_value)` **未按 source 排除 group_vote**，会导致投票票数被计入卧底人气总值，8/1 一录票即污染人气数据。

**责任说明**：此隐患一半源于 Claude——C20-3 验收时留了"票数是否计入人气"待 Vincent 确认，却未等答案即通过卡。现答案明确，据此返工。非 Manus 执行错误（当时口径未定，写入人气账本是合理默认）。

## 需 Manus 先确认的一点
当前 group_vote 写入时，`popularity_value` 字段实际写入的是什么值？（0 / 等于票数 / 经系数换算后的值）——这决定当前污染程度，请在返工说明中写明。无论该值为何，处理方向一致：**票数不应存在于 popularity_ledger。**

## 目标
群投票票数与人气账本**物理隔离**：投票有独立存储，人气汇总永不包含投票数据；C20-3 已实现的累计、冲销、幂等、留痁、summary 查询能力全部保留，只改"存到哪"。

## 方案（Manus 可提出更优解，先出方案 Claude 裁定）
**推荐方案：新建独立投票表 `group_vote_ledger`**
1. 新建表 `group_vote_ledger`：字段含 round_id、player_id、votes（正数累加/负数冲销）、idempotency_key（唯一）、operator_id、reason、created_at；带 round_id+player_id 索引
   - 建表 SQL 需过 MySQL 8 严格模式（TIMESTAMP NOT NULL 带 DEFAULT）
2. 录入接口改为写入 `group_vote_ledger`，不再构造 PopularityChangeRequest、不再触碰 popularity_ledger
3. summary 查询改为从 `group_vote_ledger` 按 round_id+player_id 汇总 votes 净值
4. 幂等、冲销、operations_log 留痕全部保留（幂等键仍用 gv_ 前缀或表内唯一约束）
5. **数据清理**：确认现有 popularity_ledger 中是否已有 group_vote 测试数据；若有，提供清理 SQL（生产库尚无真实数据，测试数据可直接删）

**验收后必须自证的关键点**
- 录入一笔投票后，查询"卧底人气总值"**不包含**该票数（写一条断言：录投票前后，该选手 spy 人气 SUM 不变）
- summary 查询票数正确（累计、冲销）
- 幂等、留痕不回退

## 交付
逐项对照表 + commit + 测试输出（**必含"投票不影响人气 SUM"的断言**）+ 数据清理说明

## 与 C20-4 的关系
C20-4 的"群投票填总数算差值"改为基于 `group_vote_ledger` 的水位线，不受本返工影响，可在本卡完成后衔接。若 C20-4 尚未开始，建议 **先做本 FIX 卡再做 C20-4**，避免 C20-4 建在错误的存储上返工。
