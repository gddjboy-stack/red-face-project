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
     * 礼物是否纳入水位线机制（即录入「中控台礼物累计总数」并按当前场控目标归属），
     * 默认 false，即礼物保持原有的逐笔入账、显式归属选手。
     *
     * <p>该开关对应一个未定裁定：中控台的礼物累计数是<b>全场维度、不区分选手</b>的，
     * 若改为填总数则归属完全依赖运营及时切换场控目标，切慢了礼物就会归错人——
     * 这意味着礼物归属本质上变成近似值，属于赛制精度问题而非纯技术选择。
     * 开关存在的目的是让两种裁定结果都能一行切换，无需重写逻辑。
     */
    private boolean giftWatermarkEnabled = false;

    public boolean isGiftWatermarkEnabled() {
        return giftWatermarkEnabled;
    }

    public void setGiftWatermarkEnabled(boolean giftWatermarkEnabled) {
        this.giftWatermarkEnabled = giftWatermarkEnabled;
    }
}
