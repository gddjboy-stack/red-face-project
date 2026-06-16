const { request } = require('./request')

function getLiveHome() {
  return request({ url: '/api/live/home' })
}

function getPopularityBoard(tab, roundId) {
  return request({ url: `/api/popularity/board?tab=${encodeURIComponent(tab)}&roundId=${roundId}` })
}

function redeemToken(token) {
  return request({
    url: '/api/tokens/redeem',
    method: 'POST',
    data: { token }
  })
}

function getMyPhotos() {
  return request({ url: '/api/me/photos' })
}

module.exports = {
  getLiveHome,
  getPopularityBoard,
  redeemToken,
  getMyPhotos
}
