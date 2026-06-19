# C17「写真上传管理」联调清单 v1

> 作者：Manus AI  
> 日期：2026-06-19  
> 用途：供 John、Claude、运维与后续真实环境联调使用。

## 一、已在本地完成的验证

| 编号 | 验证项 | 当前结果 |
|---|---|---|
| C17-BE-01 | jpg/png/webp 合法图片可上传 | 已通过 C17 专项测试 |
| C17-BE-02 | SVG 被拒绝 | 已通过 C17 专项测试 |
| C17-BE-03 | txt 改名 `.jpg` 被 magic number 拒绝 | 已通过 C17 专项测试 |
| C17-BE-04 | 空文件被拒绝 | 已通过 C17 专项测试 |
| C17-BE-05 | 不存在 playerId 被拒绝 | 已通过 C17 专项测试 |
| C17-BE-06 | 同一选手封面唯一 | 已通过 C17 专项测试 |
| C17-BE-07 | 逻辑下架不删除 `user_photo_collection` | 已通过 C17 专项测试 |
| C17-BE-08 | 替换文件时 assetId 不变 | 已通过 C17 专项测试 |
| C17-BE-09 | 无 `X-Admin-Token` 访问后台写真接口返回 401 | 已通过 C17 专项测试 |
| C17-FE-01 | 管理后台 `pnpm build` | 已通过 |
| C17-REG-01 | 后端全量回归 `mvn test` | 85 tests，0 failures，0 errors |
| C17-SEC-01 | 静态边界扫描 | 未发现物理删除收藏、原始文件名落盘、SVG 接受等风险模式 |

## 二、生产/预发环境配置清单

| 配置项 | 是否必须 | 建议值/说明 |
|---|---|---|
| `ADMIN_TOKEN` | 必须 | 足够强的随机管理口令，由运维私密下发 |
| `PHOTO_UPLOAD_DIR` | 必须 | 指向持久化磁盘，例如 `/data/redface/uploads/photos` |
| `PHOTO_PUBLIC_PATH` | 建议 | 默认 `/uploads/photos/` |
| `PHOTO_PUBLIC_BASE_URL` | 视部署而定 | 如图片需公网绝对地址，可填 `https://domain.com` |
| `PHOTO_MAX_SIZE_BYTES` | 建议 | 默认 `5242880`，即 5MB |
| `PHOTO_MAX_FILE_SIZE` | 建议 | 默认 `5MB` |
| `PHOTO_MAX_REQUEST_SIZE` | 建议 | 默认 `6MB` |

> 运维红线：`PHOTO_UPLOAD_DIR` 不能放在应用代码目录、临时构建目录或容器临时层，必须能跨部署保留，并纳入备份策略。

## 三、数据库联调清单

| 编号 | 检查项 | 期望结果 |
|---|---|---|
| C17-DB-01 | 生产执行 `db/db_schema.sql` 或对应 ALTER | `photo_assets` 拥有 status/is_cover/sort_order/file_name/content_type/file_size/updated_at |
| C17-DB-02 | 老数据兼容 | 老记录默认 `status='active'`、`is_cover=0`、`sort_order=0` |
| C17-DB-03 | 索引检查 | `idx_photo_player_status` 存在 |
| C17-DB-04 | 收藏记录 | 下架写真不删除 `user_photo_collection` |
| C17-DB-05 | token 绑定 | 替换写真文件不改变 `asset_id`，不破坏 `tokens.photo_asset_id` |

## 四、后台人工验收步骤

| 步骤 | 操作 | 期望结果 |
|---|---|---|
| 1 | 打开管理后台，输入 `ADMIN_TOKEN` | 后台接口不再 401 |
| 2 | 进入“写真管理”Tab | 能看到上传区、筛选区和资产列表 |
| 3 | 选择选手，上传 jpg/png/webp | 上传成功，列表出现缩略图、assetId、选手、状态、封面信息 |
| 4 | 上传时勾选“设为封面” | 同一选手旧封面自动取消 |
| 5 | 点击“复制 URL” | 图片 URL 可复制并可访问 |
| 6 | 对某图点击“下架” | 状态变为 inactive，封面状态变 false |
| 7 | 对 inactive 图点击“恢复” | 状态变为 active |
| 8 | 在某图行选择新文件替换 | assetId 保持不变，文件名、类型、大小、更新时间更新 |
| 9 | 上传 SVG | 后端拒绝，前端显示错误 |
| 10 | 将 txt 改名 jpg 上传 | 后端通过文件头识别并拒绝 |

## 五、用户端联动验收

| 页面/接口 | 验收点 | 期望结果 |
|---|---|---|
| 选手列表 | 上传并设封面后刷新 | 选手卡片展示封面图 |
| 选手详情 | 同一选手多图排序 | active 封面优先，其次 sort_order 和创建时间 |
| 下架图片 | 下架后刷新选手详情 | inactive 图片不再作为新查询展示图 |
| 我的写真 | 已收藏记录 | 不因后台下架而删除收藏记录 |
| 核销成功 | token 绑定 | 替换文件不改变 assetId，旧 token 绑定不断链 |

## 六、不得误判为 C17 缺陷的事项

| 事项 | 原因 |
|---|---|
| 没有图片裁剪/水印 | Claude 已批准 C17 不做裁剪、水印、AI 生成和审核流 |
| 没有对象存储 | P0 批准本地存储，后续可迁移对象存储 |
| 没有公开用户上传入口 | C17 是后台管理能力，禁止公开上传 |
| 没有删除用户收藏 | 这是保护用户权益的设计，不能视为缺陷 |
| 没有 CDN 刷新 | 属于运维部署问题，非 C17 P0 |
| 下架不等于删除文件 | 下架是逻辑隐藏，物理文件保留用于追溯和避免断链 |

## 七、上线风险与回滚建议

| 风险 | 处理建议 |
|---|---|
| 上传目录未持久化 | 上线前必须确认目录挂载；否则不要开放上传 |
| 图片 URL 小程序不可访问 | 检查 `PHOTO_PUBLIC_BASE_URL`、域名、HTTPS 和小程序域名白名单 |
| 上传大图影响带宽 | 运营侧先压缩图片；后端 5MB 上限兜底 |
| 错传违规图 | 后台立即下架；后续如需要再做审核流 |
| 本地存储迁移对象存储 | 利用 `PhotoStorageService` 抽象新增对象存储实现，迁移 `preview_url` |

## 八、Claude 复审建议

Claude 复审时建议重点检查：`LocalPhotoStorageService` 是否确实落实扩展名、声明 MIME 和 magic number 三重校验；`PhotoAdminService` 是否不做物理删除且所有写操作写 `operations_log`；`PhotoAssetMapper` 的封面唯一和状态更新是否正确；`PlayerQueryMapper` 的 active/cover 联动是否没有改变用户端响应契约；`http.ts` 的 multipart 请求是否不手写 JSON `Content-Type`；`PhotoAdminControllerC17Test` 是否覆盖恶意文件拒绝。
