const env = require('../config/env')
const { TOKEN_KEY } = require('./constants')

function request(options, isRetry = false) {
  const token = tt.getStorageSync(TOKEN_KEY)
  const header = Object.assign({}, options.header || {}, {
    'Content-Type': 'application/json'
  })
  if (options.auth !== false && token) {
    header.Authorization = `Bearer ${token}`
  }

  return new Promise((resolve, reject) => {
    tt.request({
      url: `${env.baseURL}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header,
      success(res) {
        const payload = res.data || {}
        if (res.statusCode >= 200 && res.statusCode < 300 && payload.code === 0) {
          resolve(payload.data)
          return
        }

        // C18 R1: 401 Self-heal
        const isUnauthorized = res.statusCode === 401 || payload.code === 40101
        if (isUnauthorized && options.auth !== false && !isRetry) {
          const { ensureLogin } = require('./auth')
          ensureLogin(true).then(() => {
            // retry original request once
            request(options, true).then(resolve).catch(reject)
          }).catch(loginErr => {
            reject({
              code: 40101,
              message: '重新登录失败，请退出重试',
              data: null
            })
          })
          return
        }

        reject({
          code: payload.code || res.statusCode,
          message: payload.message || '请求失败，请稍后重试',
          data: payload.data || null
        })
      },
      fail(err) {
        console.error('[request fail]', err.errMsg || err)
        reject({ code: -1, message: '网络异常，请检查连接后重试', data: null })
      }
    })
  })
}

module.exports = {
  request
}
