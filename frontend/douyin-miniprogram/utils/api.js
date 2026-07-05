const { request } = require('./request')

function getLiveHome() {
  return request({ url: '/api/live/home' })
}

function getPopularityBoard(tab, roundId) {
  return request({ url: `/api/popularity/board?tab=${encodeURIComponent(tab)}&roundId=${roundId}` })
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

function getSuspicionStatus(roundId) {
  const query = roundId ? `?roundId=${encodeURIComponent(roundId)}` : ''
  return request({ url: `/api/suspicion/status${query}` })
}

function submitSuspicion(roundId, suspectPlayerIds) {
  return request({
    url: '/api/suspicion/submit',
    method: 'POST',
    data: { roundId, suspectPlayerIds }
  })
}

function getMyPhotos() {
  return request({ url: '/api/me/photos' })
}

module.exports = {
  getLiveHome,
  getPopularityBoard,
  getPlayers,
  getPlayerDetail,
  redeemToken,
  getSuspicionStatus,
  submitSuspicion,
  getMyPhotos
}
