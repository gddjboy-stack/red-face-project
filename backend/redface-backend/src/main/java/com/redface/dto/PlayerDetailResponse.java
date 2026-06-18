package com.redface.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * C15 选手详情响应。严格不包含任何卧底身份字段。
 */
public class PlayerDetailResponse {
    private Integer playerId;
    private Integer number;
    private String name;
    private Integer teamId;
    private String teamName;
    private Integer roundId;
    private String roundName;
    private long popularityValue;
    private List<PlayerPhotoItem> photos = new ArrayList<>();
    private String supportHint = "增加人气值请在直播间进行。";

    public Integer getPlayerId() { return playerId; }
    public void setPlayerId(Integer playerId) { this.playerId = playerId; }
    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getTeamId() { return teamId; }
    public void setTeamId(Integer teamId) { this.teamId = teamId; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public Integer getRoundId() { return roundId; }
    public void setRoundId(Integer roundId) { this.roundId = roundId; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public long getPopularityValue() { return popularityValue; }
    public void setPopularityValue(long popularityValue) { this.popularityValue = popularityValue; }
    public List<PlayerPhotoItem> getPhotos() { return photos; }
    public void setPhotos(List<PlayerPhotoItem> photos) { this.photos = photos; }
    public String getSupportHint() { return supportHint; }
    public void setSupportHint(String supportHint) { this.supportHint = supportHint; }
}
