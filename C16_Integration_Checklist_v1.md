# C16「会员有效期」联调清单 v1

> 作者：Manus AI  
> 日期：2026-06-18  
> 用途：供 John、Claude 与后续真实环境联调使用，避免把本地测试结论误认为生产闭环。

## 一、已在本地完成的验证

| 编号 | 验证项 | 当前结果 |
|---|---|---|
| C16-BE-01 | `user_membership` 主 schema 与 H2 schema 同步建表 | 已完成 |
| C16-BE-02 | 无会员记录时核销后会员 +7 天 | 已通过测试 |
| C16-BE-03 | 已过期会员从当前时间 +7 天 | 已通过测试 |
| C16-BE-04 | 未过期会员从旧到期日 +7 天 | 已通过测试 |
| C16-BE-05 | 同一用户并发核销两张不同卡，最终累计 +14 天 | 已通过测试 |
| C16-BE-06 | 核销成功响应保留 C9 原字段并 additive 返回会员字段 | 已通过测试 |
| C16-BE-07 | 重复核销已用卡不重复增加会员天数 | 已通过测试 |
| C16-BE-08 | `/api/me/photos` 返回独立 `membership` 字段组 | 已通过测试 |
| C16-BE-09 | 全量后端回归 `mvn test` | 70 tests 通过 |
| C16-FE-01 | 小程序核销成功页 JS 语法检查 | 已通过 |
| C16-FE-02 | 小程序我的写真页 JS 语法检查 | 已通过 |

## 二、下一轮本地/预发联调建议

| 编号 | 联调项 | 期望结果 |
|---|---|---|
| C16-API-01 | 调用 `POST /api/tokens/redeem` 核销未使用卡密 | 返回 `membershipAddedDays=7`、`memberActive=true`、`membershipUntil` 非空 |
| C16-API-02 | 同一用户连续核销两张不同卡密 | 第二次返回的 `membershipUntil` 比第一次约多 7 天 |
| C16-API-03 | 重复核销第一张已用卡 | 返回 `already_used`，`user_membership` 不再增加 |
| C16-API-04 | 调用 `GET /api/me/photos`，用户有会员 | `membership.memberActive=true`，`membership.membershipUntil` 非空 |
| C16-API-05 | 调用 `GET /api/me/photos`，用户无会员 | `membership.memberActive=false`，`membership.membershipRemainingDays=0` |
| C16-MP-01 | 小程序核销成功页展示 | 显示“会员有效期已增加 7 天”和到期时间 |
| C16-MP-02 | 小程序我的写真页展示有会员 | 显示“会员有效期至：...” |
| C16-MP-03 | 小程序我的写真页展示无会员 | 显示“暂未开通会员，核销明信片后将增加会员有效期。” |

## 三、真实环境联调待验证

| 编号 | 待验证项 | 责任/依赖 |
|---|---|---|
| C16-REAL-01 | 抖音小程序真机核销成功页展示会员有效期 | 依赖小程序体验版/真机 |
| C16-REAL-02 | 真实后端域名下 `/api/tokens/redeem` 与 `/api/me/photos` 字段一致 | 依赖预发/生产部署 |
| C16-REAL-03 | Agiso 真实发卡后核销会员 +7 天 | 依赖 Agiso 与真实订单联调 |
| C16-REAL-04 | 真实用户多次购买/核销后有效期连续叠加 | 依赖真实订单样本 |

## 四、不得在 C16 联调中要求的事项

| 禁止项 | 原因 |
|---|---|
| 退款后扣回会员天数 | 属于 C14，不在 C16 范围 |
| 撤销或回收会员权益 | 属于反向权益处理，不在本卡范围 |
| 会员等级、连续包月、订阅体系 | 属于会员产品体系，不在本卡范围 |
| 历史已核销用户自动补偿 | 属于运营脚本或历史补偿任务，不在 C16 正式逻辑 |
| 首页展示会员中心 | C16 只展示核销成功页和我的写真页 |

## 五、Claude 复审建议

Claude 复审时建议重点检查：`TokenService.redeem` 中 C16 叠加是否仍在同一事务内；`UserMembershipMapper` 的 H2/MySQL 兼容性；`UserMembershipServiceC16Test` 的并发 +14 测试是否足够；`MyPhotosResponse.membership` 是否符合“独立字段组”裁定；小程序文案是否仍保持克制，不承诺复杂会员权益。
