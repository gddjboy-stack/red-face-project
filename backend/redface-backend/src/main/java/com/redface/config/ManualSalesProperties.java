package com.redface.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * C20-6 后台手工销量录入相关配置。
 *
 * <p>做成配置项而非常量的理由：这两个阈值都是「打扰运营」与「拦住错误」之间的权衡点，
 * 而正确取值只能在真实直播现场观察后才知道。硬编码意味着调一次要重新构建部署，
 * 在 8/9 当晚不可行。
 */
@Component
@ConfigurationProperties(prefix = "redface.manual-sales")
public class ManualSalesProperties {

    /**
     * 单笔件数异常阈值：单笔录入件数的绝对值超过此值时要求二次确认。
     *
     * <p><b>Claude 裁定 A1：由「2 倍本轮最高选手人气」改为「绝对件数 200」。</b>
     * 原方案错在选错参照物。一笔 30 件、单价 199 元的正常销量折算人气为 597,000，
     * 而轮次初期最高选手人气可能只有几千，原方案会稳定误报——
     * 恰好制造了它本想防止的后果：运营习惯性点确认，防线自动失效。
     *
     * <p>为什么用件数而不是人气：件数是运营亲手敲进去的那个数字，
     * 「多打一个零」这个错误发生在件数上（30 敲成 300），而不是发生在人气上。
     * 校验对象必须与出错对象一致，否则提示文案无法指向运营该复核的地方。
     *
     * <p>为什么取 200：直播现场单个选手单款商品单笔录入超过 200 件已属罕见，
     * 而「30 敲成 300」这类典型错误恰好越过该线。此值需在 8/9 首场后按实际
     * 销量分布复核——若正常销量频繁触及 200，说明取值偏低，会退化成噪音。
     */
    private int abnormalQuantityThreshold = 200;

    /**
     * 软重复检测窗口（秒）：此时间窗内出现「同选手 + 同商品 + 同件数」即提示。
     *
     * <p><b>Claude 裁定 A2：维持 60 秒。</b>
     * 防的是「运营以为没提交成功、手动又点一次」——前端生成的幂等键对此无效，
     * 因为那是一次新的点击，会得到新的幂等键而正常入账。
     */
    private int recentWindowSeconds = 60;

    public int getAbnormalQuantityThreshold() {
        return abnormalQuantityThreshold;
    }

    public void setAbnormalQuantityThreshold(int abnormalQuantityThreshold) {
        this.abnormalQuantityThreshold = abnormalQuantityThreshold;
    }

    public int getRecentWindowSeconds() {
        return recentWindowSeconds;
    }

    public void setRecentWindowSeconds(int recentWindowSeconds) {
        this.recentWindowSeconds = recentWindowSeconds;
    }
}
