# 已知缺陷归档：`players.display_code` 无写入入口

**缺陷编号** DEFECT-001
**发现日期** 2026-08-02
**发现者** Manus（C20-4C 实施过程中准备浏览器实测时发现）
**核实者** Claude（独立核实 `BasicDataMapper`，确认属实）
**责任归属** Claude 的卡片设计缺陷（Claude 2026-08-02 裁定第 18 行自认）
**当前状态** 已归档，**暂不修复**
**修复时机** 启用 C20-4C 订单表批量导入之前，**第一个必须修的前置项，不可跳过**
**修复方案** 已获 Claude 预先批准，将来直接执行，无需重新裁定

---

## 一、缺陷现象

整条订单归属链路是「抖店订单表的商家编码 → `players.display_code` → `player_id` → 人气值」。这条链路的中间环节 `display_code`，在生产环境**没有任何代码路径能够写入或查看**。

该缺陷的危险性在于它的隐蔽形式：数据库列存在、DTO 字段存在、Mapper 按它查询，从任何单一角度看功能都像是完整的，唯独没有一条 SQL 语句写它。调用建选手接口并传入该字段，接口返回 HTTP 200，字段被静默丢弃，**没有报错、没有校验、没有告警**。

---

## 二、五条证据

| 编号 | 证据 | 位置 |
| --- | --- | --- |
| 一 | 插入语句不含该列：`INSERT INTO players (name, number, status) VALUES (...)` | `BasicDataMapper.insertPlayer` |
| 二 | 不存在任何更新语句：`grep -rn "UPDATE players" src/` 全项目返回空 | 全项目 |
| 三 | 请求 DTO 有字段及 getter/setter，但无任何 SQL 引用 `#{displayCode}` | `BasicDataRequests.CreatePlayerRequest` |
| 四 | 查询语句不返回该列，SELECT 列表为 `player_id, name, number, status, created_at, updated_at` | `BasicDataMapper.findPlayers` / `findPlayerById` |
| 五 | 实测字段被静默丢弃 | 见下方请求响应 |

证据五的实测记录：

```
POST /api/admin/players  {"name":"林一","displayCode":"P01","number":1,"operatorId":"director"}
→ 200 {"playerId":1,"name":"林一","number":1,"displayCode":null,"status":"active",...}
```

证据三是最容易导致误判的一条。**从 DTO 看，这个功能像是已经实现了。** 任何后续接手者若只检查 DTO 与数据库 schema，都会得出「功能存在」的结论。

---

## 三、为什么归属键确为 `display_code` 而非 `number`

曾考虑过「原设计本用 `number` 做归属键，`display_code` 是冗余字段」的可能性，已被排除。三条依据：`db_schema.sql` 第 336 行注释明确写「商家编码,即选手编号如P12(与players.display_code对应)」；该列有独立唯一约束 `uq_display_code`，而冗余字段不会被加唯一约束；`OrderSalesLedgerMapper` 的两个查询方法均按 `display_code` 检索。Claude 已确认这一判断。

---

## 四、为什么 196 项测试全绿也没能发现

因为所有相关测试都绕过了生产环境唯一的入口。`OrderImportC20Test`（第 65、69 行）与 `OrderImportBlockC20Test`（第 69、73 行）均直接执行 SQL 造数据：

```sql
INSERT INTO players (player_id, name, number, display_code, status) ...
```

测试用 JDBC 直接写入，从不调用 `POST /api/admin/players`。因此测试验证的是「假如 `display_code` 有值，归属逻辑正确」，而没有验证「`display_code` 能否被填上」。

这与 C20-4B 对照表第 72 至 78 行记录的「两份 schema 不同步而测试全绿」属同一类问题的升级版：那次是列不存在，这次是列存在但无人能写。为此新增的 `SchemaParityC20Test` 只比对 DDL 文本，对本缺陷无能为力。

由此提炼出的失效模式已被 Claude 采纳为**协作纪律第六条**：

> 当某一列只被测试 setup 代码写入、而从不被生产代码写入时，测试覆盖率越高，越容易掩盖入口缺失。凡新增数据列参与业务链路，必须存在一条**走真实 HTTP 接口**的测试，断言该列可被写入并可被下游查询到。`SchemaParityC20Test` 管「列是否存在」，此类测试管「列是否可写」，两者互补。

---

## 五、缺陷的设计根源

C20-4B 对照表第 91 行将该事项列为待确认事项 A-2：

> 商家编码必须与选手编号一致（`players.display_code`），上架时即约定 —— 负责人 John / 彬少，时限 8/7

第 96 行标注其为硬阻塞。问题在于，**A-2 被表述为一项「人类需要去约定的事」，但它同时也是一项「系统需要提供入口的事」，后者从未被列为任何一张卡的交付物。** 即便人类在 8/7 完美地约定了编码规则，运营届时仍然无处录入。

Claude 在裁定第 18 行确认：这是卡片设计缺陷，记在 Claude 账上，不记在 C20-4A / C20-4B。

---

## 六、若在未修复状态下启用订单导入会发生什么

运营在场控后台建完全部选手，`display_code` 全为 `NULL`。导入订单表时每一行的商家编码都查不到对应选手，全部判为 `unattributed`，C20-4C 的硬阻断将整批拦下并给出明确提示。

阻断本身运作正确，但运营在界面上找不到任何地方能配 `display_code`，流程会彻底卡死，除非有人临时连生产数据库执行 UPDATE。

值得记录的是这个缺陷的另一面：**如果没有 C20-4C 的硬阻断，该缺陷的表现会是「导入显示成功、全场选手人气值为零、无人察觉异常」。硬阻断把一次静默的结算灾难，变成了一次显性的操作卡死。** 这是阻断机制第一次发挥作用，且它拦下的不是运营的操作失误，而是我们自己的代码缺陷。Claude 在裁定第 19 行确认了这一观察。

---

## 七、修复方案（Claude 已预先批准，启用前直接执行）

| 序号 | 改动 | 文件 | 说明 |
| --- | --- | --- | --- |
| 1 | 插入语句补列 | `BasicDataMapper.insertPlayer` | 加入 `display_code`，允许建选手时直接指定 |
| 2 | 查询语句补列 | `BasicDataMapper.findPlayers` / `findPlayerById` | 否则前端无法显示与核对 |
| 3 | 新增更新入口 | `BasicDataMapper` + `BasicDataService` + `BasicDataController` | `PUT /api/admin/players/{playerId}/display-code`，供已建选手补配 |
| 4 | 唯一冲突处理 | `BasicDataService` | `uq_display_code` 冲突须返回明确业务错误，不可让 500 裸奔到前端 |
| 5 | 前端可编辑列 | `App.vue` 基础数据 tab | 选手列表增加「商家编码」列，可就地编辑 |
| 6 | 空值常驻告警 | 订单导入页 | 存在 `display_code` 为空的在场选手时，页面顶部常驻告警 |
| 7 | 纪律第六条测试 | 新增测试 | 走 `POST /api/admin/players` 真实接口建选手，断言 `display_code` 已落库且可被 `findPlayerNameByDisplayCode` 查到 |

第 6 项的理由：`display_code` 为空不会在导入前暴露，只在导入时以「全部未归属」的形式爆发。**在运营尚有时间处理的时刻提示他，比在阻断时告诉他更有价值。**

第 7 项是纪律第六条的具体落地，防止同类「入口缺失但测试全绿」再次发生。

---

## 八、当前规避方式

按 Claude 2026-08-02 裁定，8/9 首场**不使用**订单表导入，改走 C20-6 后台手工销量录入。手工录入时选手从下拉框选取，不经过 `display_code`，因此本缺陷在简化路线下不参与链路。

C20-4C 代码已提交入库但**默认隐藏**：前端订单导入标签页需在地址栏追加 `?experimental=1` 方可显示，且页面顶部有醒目告警指向本文档。此举为防止运营在直播现场误入该流程。需要说明的是，**这只是防误入的界面开关，不构成权限控制——后端接口依旧可被直接调用。**

---

## 九、重新启用订单导入的检查清单

启用前必须逐项完成，任何一项未完成即不得启用：

第一，完成本文档第七节全部七项修复，其中第 7 项测试必须通过。第二，实施 Claude 裁定第五节归档的议题二结论：按选手汇总视图改为两级展开（选手主行含人气合计，可展开为各商品子行含件数与单价），并为「存在被排除商品」的选手打标记。第三，实施议题三结论：未知订单状态纳入硬阻断，允许走同一覆盖入口放行，覆盖入口语义扩展为「排除系统无法判定的订单」。第四，确认商家编码规则已与运营实际上架方式一致——John 已确认为「每位选手每款商品一个独立编码」，如 P01-CARD、P01-PHOTO。第五，移除前端 `showExperimental` 开关或将其默认值改为 `true`。

---

## 十、相关文档

本缺陷的完整提出经过与三议题分析见 `collaboration/Manus请示函_C20-4C归属键三议题_V1.0.md`。Claude 的裁定原文由 John 于 2026-08-02 转交，核心结论已摘录于本文档。C20-4B 阶段的相关记录见 `C20-4B_订单表批量导入_完成对照表_V1.0.md` 第五节与第六节。

---

**归档人** Manus
**归档日期** 2026-08-02
