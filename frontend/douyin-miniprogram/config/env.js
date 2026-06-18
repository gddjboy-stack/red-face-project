// 体验版连生产：currentEnv 设为 'prod'（C-FE-01）。
// 注意：baseURL 为根域，不带 /api、不带结尾斜杠；各请求路径自带 /api/...，
// request.js 拼接为 `${baseURL}${url}`，最终形如 https://hongyanjuzhongju.cn/api/...
const currentEnv = 'prod'

const configs = {
  dev: {
    // 开发者工具本地联调用；真机无法访问 localhost。
    baseURL: 'http://localhost:8080'
  },
  staging: {
    baseURL: 'https://staging.example.com'
  },
  prod: {
    // 生产根域，与抖音后台"request 合法域名"、运维 Nginx 绑定保持一致。
    baseURL: 'https://hongyanjuzhongju.cn'
  }
}

module.exports = {
  currentEnv,
  ...configs[currentEnv]
}
