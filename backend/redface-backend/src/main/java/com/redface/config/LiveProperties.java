package com.redface.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * C20-4A 直播数据录入相关配置。
 */
@Component
@ConfigurationProperties(prefix = "redface.live")
public class LiveProperties {

    /**
     * 礼物是否纳入水位线机制（即录入「中控台礼物累计总数」并按当前场控目标归属）。
     *
     * <p><b>Claude 裁定 V3.0 R-2：采用方案 (a)，默认开启。</b>
     * 决定性事实是抖音直播间的礼物在平台层面送给的是「直播间」，
     * 不存在「送给某位选手」的原生机制；团播场景下一个直播间有多位选手，
     * 但礼物只有一个去处。因此归属只能由我方自行标记时段，与既有场控状态
     * （pool / player / team / spy + targetId）设计一致。
     *
     * <p><b>精度代价（Vincent 已知晓）</b>：礼物归属本质上是「按时段近似」，
     * 不精确到每一笔。若主持人已切到 B 选手而运营晚半分钟切换场控目标，
     * 这半分钟的礼物会记到 A 头上。缓解措施见《中控台运营执行手册》：
     * 主持人口播信号、<b>换场控目标前先录一次数</b>、大额存疑事后手动调分冲正。
     *
     * <p>保留开关而非硬编码，是为了在 8/3 首场实测后若发现归属误差不可接受，
     * 能一行回退到逐笔录入，无需重写逻辑。
     */
    private boolean giftWatermarkEnabled = true;

    public boolean isGiftWatermarkEnabled() {
        return giftWatermarkEnabled;
    }

    public void setGiftWatermarkEnabled(boolean giftWatermarkEnabled) {
        this.giftWatermarkEnabled = giftWatermarkEnabled;
    }
}
