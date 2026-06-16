const { request } = require('./request')
const { TOKEN_KEY, USER_ID_KEY } = require('./constants')

function ttLogin() {
  return new Promise((resolve, reject) => {
    tt.login({
      success(res) {
        if (res.code) {
          resolve(res.code)
        } else {
          reject(new Error('未获取到登录 code'))
        }
      },
      fail(err) {
        reject(new Error(err.errMsg || '登录失败'))
      }
    })
  })
}

async function loginByCode(code) {
  const data = await request({
    url: '/api/auth/login',
    method: 'POST',
    auth: false,
    data: { code }
  })
  tt.setStorageSync(TOKEN_KEY, data.token)
  tt.setStorageSync(USER_ID_KEY, data.userId)
  return data
}

async function ensureLogin() {
  const token = tt.getStorageSync(TOKEN_KEY)
  if (token) {
    return { token, userId: tt.getStorageSync(USER_ID_KEY) }
  }
  const code = await ttLogin()
  return loginByCode(code)
}

function clearLogin() {
  tt.removeStorageSync(TOKEN_KEY)
  tt.removeStorageSync(USER_ID_KEY)
}

module.exports = {
  ensureLogin,
  loginByCode,
  clearLogin
}
