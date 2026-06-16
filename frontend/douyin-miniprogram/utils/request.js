const env = require('../config/env')
const { TOKEN_KEY } = require('./constants')

function request(options) {
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
        reject({
          code: payload.code || res.statusCode,
          message: payload.message || '请求失败，请稍后重试',
          data: payload.data || null
        })
      },
      fail(err) {
        reject({ code: -1, message: err.errMsg || '网络异常，请稍后重试', data: null })
      }
    })
  })
}

module.exports = {
  request
}
