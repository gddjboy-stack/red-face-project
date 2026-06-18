package com.redface.dto;

/**
 * C15 选手列表项。只包含用户端可展示字段，禁止包含卧底身份字段。
 */
public class PlayerListItem {
    private Integer playerId;
    private Integer number;
    private String name;
    private Integer teamId;
    private String teamName;
    private long popularityValue;
    private String photoPreviewUrl;

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
    public long getPopularityValue() { return popularityValue; }
    public void setPopularityValue(long popularityValue) { this.popularityValue = popularityValue; }
    public String getPhotoPreviewUrl() { return photoPreviewUrl; }
    public void setPhotoPreviewUrl(String photoPreviewUrl) { this.photoPreviewUrl = photoPreviewUrl; }
}
