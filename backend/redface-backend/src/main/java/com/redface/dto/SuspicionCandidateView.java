package com.redface.dto;

/**
 * C13 真相识破候选项。用户侧严禁暴露 isSpy 或真实卧底身份。
 */
public class SuspicionCandidateView {
    private int playerId;
    private int number;
    private String playerName;
    private Integer teamId;
    private String teamName;
    private long count;
    private double ratio;

    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public Integer getTeamId() { return teamId; }
    public void setTeamId(Integer teamId) { this.teamId = teamId; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
    public double getRatio() { return ratio; }
    public void setRatio(double ratio) { this.ratio = ratio; }
}
