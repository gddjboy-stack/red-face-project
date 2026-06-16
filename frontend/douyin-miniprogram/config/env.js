const currentEnv = 'dev'

const configs = {
  dev: {
    // 开发者工具本地联调可改为 http://localhost:8080；真机调试需换成 HTTPS 域名。
    baseURL: 'http://localhost:8080'
  },
  staging: {
    baseURL: 'https://staging.example.com'
  },
  prod: {
    baseURL: 'https://api.example.com'
  }
}

module.exports = {
  currentEnv,
  ...configs[currentEnv]
}
