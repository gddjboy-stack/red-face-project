(() => {
  'use strict'

  const bridgeConfig = {
    // 抖音小程序跳转链接必须来自平台能力或后续后端安全生成。
    // 彩排阶段默认留空；留空时不展示“打开小程序核销页”入口，只保留复制降级路径。
    miniProgramLinkBase: '',
    miniProgramTokenParamName: 'token'
  }

  const params = new URLSearchParams(window.location.search)
  const token = normalizeValue(params.get('t'))
  const orderId = normalizeValue(params.get('oid'))

  const tokenValue = document.getElementById('tokenValue')
  const orderPanel = document.getElementById('orderPanel')
  const orderValue = document.getElementById('orderValue')
  const statusMessage = document.getElementById('statusMessage')
  const openMiniProgramLink = document.getElementById('openMiniProgramLink')
  const copyTokenButton = document.getElementById('copyTokenButton')

  init()

  function init() {
    if (!token) {
      tokenValue.textContent = '未读取到核销凭证'
      copyTokenButton.disabled = true
      setStatus('当前链接缺少 t 参数，请联系工作人员重新获取核销链接。', true)
      return
    }

    tokenValue.textContent = token
    copyTokenButton.addEventListener('click', () => copyToken(token))

    if (orderId) {
      orderValue.textContent = orderId
      orderPanel.classList.remove('is-hidden')
    }

    const miniProgramLink = buildMiniProgramLink(token)
    if (miniProgramLink) {
      openMiniProgramLink.href = miniProgramLink
      openMiniProgramLink.classList.remove('is-hidden')
      setStatus('可尝试打开小程序核销页；如未成功，请复制凭证后手动进入小程序核销。', false)
    } else {
      setStatus('当前未配置平台跳转链接，请复制凭证后手动进入小程序核销。', false)
    }
  }

  function normalizeValue(value) {
    return value == null ? '' : String(value).trim()
  }

  function buildMiniProgramLink(currentToken) {
    const base = normalizeValue(bridgeConfig.miniProgramLinkBase)
    if (!base) return ''

    try {
      const url = new URL(base, window.location.href)
      url.searchParams.set(bridgeConfig.miniProgramTokenParamName, currentToken)
      return url.toString()
    } catch (error) {
      setStatus('平台跳转链接配置异常，请复制凭证后手动进入小程序核销。', true)
      return ''
    }
  }

  async function copyToken(currentToken) {
    try {
      await writeTextToClipboard(currentToken)
      setStatus('核销凭证已复制，请打开抖音 App 进入小程序核销页粘贴使用。', false)
    } catch (error) {
      setStatus('自动复制失败，请长按核销凭证手动复制。', true)
    }
  }

  async function writeTextToClipboard(text) {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return
    }

    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', 'readonly')
    textarea.style.position = 'fixed'
    textarea.style.top = '-9999px'
    textarea.style.left = '-9999px'
    document.body.appendChild(textarea)
    textarea.focus()
    textarea.select()

    const copied = document.execCommand('copy')
    document.body.removeChild(textarea)
    if (!copied) {
      throw new Error('copy command failed')
    }
  }

  function setStatus(message, isError) {
    statusMessage.textContent = message
    statusMessage.classList.toggle('is-error', Boolean(isError))
  }
})()
