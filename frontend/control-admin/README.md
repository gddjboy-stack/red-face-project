# 场控运营后台（control-admin）部署说明

> 任务卡：C-ADMIN-FE-01
> 技术栈：Vue 3 + Element Plus + Vite + TypeScript
> 用途：6/22 现场场控操作后台（开赛、淘汰、改人气、团队分配等），对接后端 `/api/admin/**`

---

## 一、构建

```bash
cd frontend/control-admin
pnpm install
pnpm build      # 产物输出到 dist/
```

`dist/` 为纯静态文件，交由 Nginx 托管。

---

## 二、生产 API 基址：推荐「同域部署」（方案 a）

后台请求使用相对路径 `/api/...`（`VITE_API_BASE_URL` 默认空）。**推荐同域部署，无需改任何构建变量**：

- 将 `control-admin` 的 `dist/` 静态文件与后端部署在**同一域名** `hongyanjuzhongju.cn` 下；
- Nginx 把 `/api` 反向代理到后端 `8080`，其余路径返回后台静态文件。

Nginx 示意（仅说明转发关系，具体以运维为准）：

```nginx
server {
    server_name hongyanjuzhongju.cn;

    # /api 转后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        # 第二层防护：建议对 /api/admin/ 加运维/场控 IP 白名单
        # location /api/admin/ { allow <运维IP>; deny all; proxy_pass ...; }
    }

    # 其余返回后台静态文件
    location / {
        root /var/www/control-admin/dist;
        try_files $uri $uri/ /index.html;
    }
}
```

此时前端相对 `/api/...` 直接命中后端，无跨域问题。

### 备选：分域部署（方案 b，不推荐）
若后台与后端不同域，构建时设环境变量：

```bash
VITE_API_BASE_URL=https://hongyanjuzhongju.cn pnpm build
```

并需后端额外放通 CORS 跨域（当前后端未配 CORS，故不推荐此方案）。

---

## 三、管理口令（X-Admin-Token）使用说明

后端 `/api/admin/**` 已开启鉴权（C-DEPLOY-01）：请求须带 `X-Admin-Token`，否则返回 401。本后台的处理方式：

- **口令不写进代码、不进构建产物**，由运营在后台顶部「管理口令」输入框输入一次，存于浏览器 `localStorage`，后续 `/api/admin` 请求自动携带；
- 首次进入若未设置口令，会弹窗提示输入；
- 收到 **401** 时自动清除旧口令并提示「管理口令无效，请重新输入」；
- 口令值（`ADMIN_TOKEN`）由 John/运维通过**私密渠道**下发给现场操作员，**切勿写入任何公开文档或代码仓库**。

> 注意：口令存在 `localStorage`，仅当前浏览器有效；换浏览器/清缓存需重新输入。配合运维侧 Nginx 对 `/api/admin/**` 的 IP 白名单，构成双层防护。

---

## 四、上线前自检

1. 后端已切到生产 profile、设好 `ADMIN_TOKEN` 并正常启动；
2. 后台与后端同域，Nginx `/api` 转发正常；
3. 打开后台 → 输入正确管理口令 → 监控数据能正常加载（说明鉴权打通）；
4. 故意输错口令 → 应提示重新输入（说明 401 处理生效）。
