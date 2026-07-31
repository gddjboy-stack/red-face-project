package com.redface.dto;

import java.util.List;

/**
 * C20-5 大屏排行榜响应（边界 D-3：数据面收窄）。
 *
 * <p>只包含选手序号、姓名、队名、名次、分值与轮次状态。
 * <b>不含</b>订单号、买家昵称、手机号、收货信息等任何买家个人信息——
 * 大屏会被观众拍摄与直播录制，任何个人信息露出都是不可回收的泄露。
 */
public class DisplayBoardResponse {

    private String liveStatus;
    private Integer roundId;
    private String roundName;
    private String tab;
    private long serverTime;
    private List<DisplayBoardItem> items;

    public String getLiveStatus() {
        return liveStatus;
    }

    public void setLiveStatus(String liveStatus) {
        this.liveStatus = liveStatus;
    }

    public Integer getRoundId() {
        return roundId;
    }

    public void setRoundId(Integer roundId) {
        this.roundId = roundId;
    }

    public String getRoundName() {
        return roundName;
    }

    public void setRoundName(String roundName) {
        this.roundName = roundName;
    }

    public String getTab() {
        return tab;
    }

    public void setTab(String tab) {
        this.tab = tab;
    }

    public long getServerTime() {
        return serverTime;
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
    }

    public List<DisplayBoardItem> getItems() {
        return items;
    }

    public void setItems(List<DisplayBoardItem> items) {
        this.items = items;
    }
}
