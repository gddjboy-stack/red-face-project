const { request } = require('./request')

function getLiveHome() {
  return request({ url: '/api/live/home' })
}

function getPlayers(roundId) {
  const query = roundId ? `?roundId=${encodeURIComponent(roundId)}` : ''
  return request({ url: `/api/players${query}` })
}

function getPlayerDetail(playerId, roundId) {
  const query = roundId ? `?roundId=${encodeURIComponent(roundId)}` : ''
  return request({ url: `/api/players/${encodeURIComponent(playerId)}${query}` })
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
  getPlayers,
  getPlayerDetail,
  redeemToken,
  getMyPhotos
}
