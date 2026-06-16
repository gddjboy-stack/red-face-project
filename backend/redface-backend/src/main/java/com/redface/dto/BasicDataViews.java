package com.redface.dto;

import java.time.LocalDateTime;

/**
 * C19 基础数据管理响应视图集合。
 */
public final class BasicDataViews {
    private BasicDataViews() {
    }

    public static class PlayerView {
        private Integer playerId;
        private String name;
        private Integer number;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Integer getPlayerId() { return playerId; }
        public void setPlayerId(Integer playerId) { this.playerId = playerId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getNumber() { return number; }
        public void setNumber(Integer number) { this.number = number; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class TeamView {
        private Integer teamId;
        private String name;
        private LocalDateTime createdAt;

        public Integer getTeamId() { return teamId; }
        public void setTeamId(Integer teamId) { this.teamId = teamId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class RoundView {
        private Integer roundId;
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;

        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class PlayerRoundView {
        private Integer playerId;
        private Integer number;
        private String playerName;
        private Integer roundId;
        private String roundName;
        private Integer teamId;
        private String teamName;
        private Boolean isSpy;
        private String playerStatus;

        public Integer getPlayerId() { return playerId; }
        public void setPlayerId(Integer playerId) { this.playerId = playerId; }
        public Integer getNumber() { return number; }
        public void setNumber(Integer number) { this.number = number; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public String getRoundName() { return roundName; }
        public void setRoundName(String roundName) { this.roundName = roundName; }
        public Integer getTeamId() { return teamId; }
        public void setTeamId(Integer teamId) { this.teamId = teamId; }
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public Boolean getIsSpy() { return isSpy; }
        public void setIsSpy(Boolean isSpy) { this.isSpy = isSpy; }
        public String getPlayerStatus() { return playerStatus; }
        public void setPlayerStatus(String playerStatus) { this.playerStatus = playerStatus; }
    }
}
