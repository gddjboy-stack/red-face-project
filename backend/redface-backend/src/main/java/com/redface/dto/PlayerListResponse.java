package com.redface.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * C15 选手列表响应。
 */
public class PlayerListResponse {
    private Integer roundId;
    private String roundName;
    private List<PlayerListItem> items = new ArrayList<>();

    public Integer getRoundId() { return roundId; }
    public void setRoundId(Integer roundId) { this.roundId = roundId; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public List<PlayerListItem> getItems() { return items; }
    public void setItems(List<PlayerListItem> items) { this.items = items; }
}
