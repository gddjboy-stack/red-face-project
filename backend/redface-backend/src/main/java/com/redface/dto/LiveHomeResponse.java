package com.redface.dto;

/**
 * API-1 首页直播状态响应。
 */
public class LiveHomeResponse {
    private String liveStatus;
    private Integer roundId;
    private String roundName;
    private String currentMode;
    private String targetDisplayName;
    private long targetPopularity;
    private String teamDisplayName;
    private long teamPopularity;
    private boolean spyChannelOpen;
    private long updatedAt;

    public String getLiveStatus() { return liveStatus; }
    public void setLiveStatus(String liveStatus) { this.liveStatus = liveStatus; }
    public Integer getRoundId() { return roundId; }
    public void setRoundId(Integer roundId) { this.roundId = roundId; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public String getCurrentMode() { return currentMode; }
    public void setCurrentMode(String currentMode) { this.currentMode = currentMode; }
    public String getTargetDisplayName() { return targetDisplayName; }
    public void setTargetDisplayName(String targetDisplayName) { this.targetDisplayName = targetDisplayName; }
    public long getTargetPopularity() { return targetPopularity; }
    public void setTargetPopularity(long targetPopularity) { this.targetPopularity = targetPopularity; }
    public String getTeamDisplayName() { return teamDisplayName; }
    public void setTeamDisplayName(String teamDisplayName) { this.teamDisplayName = teamDisplayName; }
    public long getTeamPopularity() { return teamPopularity; }
    public void setTeamPopularity(long teamPopularity) { this.teamPopularity = teamPopularity; }
    public boolean isSpyChannelOpen() { return spyChannelOpen; }
    public void setSpyChannelOpen(boolean spyChannelOpen) { this.spyChannelOpen = spyChannelOpen; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
