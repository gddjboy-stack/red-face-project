# 页面级 API 契约当前版本记录 V1.0

**当前契约文件：** `docs/API契约/当前页面级API契约定稿_C9依据_V1.0.md`  
**原始上传文件：** `/home/ubuntu/upload/ClaudeUI二审_回应蓝军报告_API契约定稿.md`  
**契约来源：** Claude UI 二次审查回应 Manus 蓝军报告  
**契约日期：** 2026.06.12  
**记录日期：** 2026.06.14  
**记录人：** Manus AI

## 版本定位

本文件记录 Claude 定稿的页面级 API 契约，作为 C9 Controller 层开发、彬少正式开发交付稿标注、以及 Manus 第二轮“UI—API—测试用例”三方对齐复审的基准。

## 当前 C9 范围

| API 编号 | 接口 | 范围裁定 |
| --- | --- | --- |
| API-0 | `POST /api/auth/login` | 必做，所有 `/api/me/*` 的身份前提。 |
| API-1 | `GET /api/live/home` | 必做，首页直播状态与当前互动归属。 |
| API-2 | `GET /api/popularity/board?tab=player|team|spy&roundId=3` | 必做，人气看板；items 必须按 number 升序，禁止按 value 排序。 |
| API-3 | `POST /api/tokens/redeem` | 必做，卡密核销与错误码状态。 |
| API-4 | `GET /api/me/photos` | 简单列表，一条 SQL 级别实现。 |
| API-5 | `POST /api/suspicion/submit` | P1，彩排后随 C13，当前不实现。 |

## 后续引用原则

后续涉及 C9、UI 对齐、状态设计、前端联调、测试用例和 Claude/Manus 复审时，应优先引用本 API 契约版本，除非 John 或 Claude 明确提供更新版本。
