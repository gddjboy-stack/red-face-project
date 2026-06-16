const TOKEN_KEY = 'redface_bearer_token'
const USER_ID_KEY = 'redface_user_id'
const REDEEM_SUCCESS_KEY = 'redface_redeem_success_payload'

const ERROR_MESSAGES = {
  40001: '卡密格式不正确，请检查是否为 RFZJ-XXXX-XXXX-XXXX。',
  40002: '未找到该卡密，请确认复制完整后重试。',
  40003: '该卡密已被核销，无法重复使用。',
  40004: '连续输错次数较多，请等待倒计时结束后再试。',
  40005: '当前暂无可计入的直播轮次，请稍后再试或联系工作人员。'
}

module.exports = {
  TOKEN_KEY,
  USER_ID_KEY,
  REDEEM_SUCCESS_KEY,
  ERROR_MESSAGES
}
