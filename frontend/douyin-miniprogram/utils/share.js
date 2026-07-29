const SHARE_CONFIG = {
  title: '红颜·局中局 | 活动信息与粉丝权益',
  desc: '查看活动动态，管理你的专属权益与数字写真',
  path: '/pages/home/index'
}

function buildShareMessage() {
  return {
    title: SHARE_CONFIG.title,
    desc: SHARE_CONFIG.desc,
    path: SHARE_CONFIG.path
  }
}

module.exports = {
  SHARE_CONFIG,
  buildShareMessage
}
