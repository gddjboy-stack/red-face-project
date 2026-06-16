package com.redface.dto;

/**
 * API-3 卡密核销成功页面级响应。
 */
public class RedeemResponse {
    private Integer playerNumber;
    private String playerName;
    private String teamName;
    private long points;
    private String photoAssetId;
    private String photoPreviewUrl;
    private boolean collected;

    public Integer getPlayerNumber() { return playerNumber; }
    public void setPlayerNumber(Integer playerNumber) { this.playerNumber = playerNumber; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public long getPoints() { return points; }
    public void setPoints(long points) { this.points = points; }
    public String getPhotoAssetId() { return photoAssetId; }
    public void setPhotoAssetId(String photoAssetId) { this.photoAssetId = photoAssetId; }
    public String getPhotoPreviewUrl() { return photoPreviewUrl; }
    public void setPhotoPreviewUrl(String photoPreviewUrl) { this.photoPreviewUrl = photoPreviewUrl; }
    public boolean isCollected() { return collected; }
    public void setCollected(boolean collected) { this.collected = collected; }
}
