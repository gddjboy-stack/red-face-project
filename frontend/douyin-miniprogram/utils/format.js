function formatNumber(value) {
  const number = Number(value || 0)
  return number.toLocaleString('zh-CN')
}

function formatTime(seconds) {
  if (!seconds) return '--'
  const date = new Date(Number(seconds) * 1000)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = `${date.getHours()}`.padStart(2, '0')
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${month}/${day} ${hour}:${minute}`
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

module.exports = {
  formatNumber,
  formatTime,
  formatDateTime
}
