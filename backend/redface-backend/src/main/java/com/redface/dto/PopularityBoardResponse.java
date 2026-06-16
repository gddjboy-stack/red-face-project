package com.redface.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * API-2 人气看板响应。
 */
public class PopularityBoardResponse {
    private String tab;
    private Integer roundId;
    private boolean spyTabEnabled;
    private List<PopularityBoardItem> items = new ArrayList<>();

    public String getTab() { return tab; }
    public void setTab(String tab) { this.tab = tab; }
    public Integer getRoundId() { return roundId; }
    public void setRoundId(Integer roundId) { this.roundId = roundId; }
    public boolean isSpyTabEnabled() { return spyTabEnabled; }
    public void setSpyTabEnabled(boolean spyTabEnabled) { this.spyTabEnabled = spyTabEnabled; }
    public List<PopularityBoardItem> getItems() { return items; }
    public void setItems(List<PopularityBoardItem> items) { this.items = items; }
}
