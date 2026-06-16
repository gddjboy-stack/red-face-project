document.addEventListener('DOMContentLoaded', () => {
    const tokenDisplay = document.getElementById('tokenDisplay');
    const oidDisplay = document.getElementById('oidDisplay');
    const openMiniProgramBtn = document.getElementById('openMiniProgramBtn');
    const copyTokenBtn = document.getElementById('copyTokenBtn');
    const fallbackMessage = document.getElementById('fallbackMessage');

    // 从 URL 获取参数
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('t');
    const oid = urlParams.get('oid');

    // 配置抖音小程序跳转链接的基础部分
    // 根据 Claude 裁定，彩排阶段此链接可能为空，此时只提供复制降级方案
    const miniProgramLinkBase = ''; // 示例: 'snssdk1128://microapp/invoke?app_id=ttxxxxxxxxx&path=pages/redeem/index' 
                                   // 真实链接需由抖音平台生成，此处暂留空

    if (token) {
        tokenDisplay.textContent = token;
    } else {
        tokenDisplay.textContent = '未找到核销凭证。';
        openMiniProgramBtn.disabled = true;
        copyTokenBtn.disabled = true;
    }

    if (oid) {
        oidDisplay.textContent = `订单号 (仅展示): ${oid}`;
        oidDisplay.classList.remove('hidden');
    }

    // 处理“打开抖音小程序核销”按钮
    if (miniProgramLinkBase) {
        openMiniProgramBtn.addEventListener('click', () => {
            // 拼接完整的跳转链接，带上 token 参数
            const fullMiniProgramLink = `${miniProgramLinkBase}?token=${token}`;
            window.location.href = fullMiniProgramLink;
        });
    } else {
        // 如果没有配置小程序跳转链接，则隐藏自动跳转按钮，并显示降级提示
        openMiniProgramBtn.classList.add('hidden');
        fallbackMessage.classList.remove('hidden');
    }

    // 处理“复制凭证并手动打开小程序”按钮
    copyTokenBtn.addEventListener('click', () => {
        if (token) {
            navigator.clipboard.writeText(token).then(() => {
                alert('核销凭证已复制到剪贴板！请手动打开抖音小程序进行核销。');
                fallbackMessage.classList.remove('hidden');
            }).catch(err => {
                console.error('复制失败:', err);
                alert('复制失败，请手动复制凭证：' + token);
                fallbackMessage.classList.remove('hidden');
            });
        }
    });
});
