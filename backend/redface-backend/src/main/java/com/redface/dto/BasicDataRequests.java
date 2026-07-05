package com.redface.dto;

import java.time.LocalDateTime;

/**
 * C19 基础数据管理请求 DTO 集合，仅覆盖 P0 静态基础数据录入能力。
 */
public final class BasicDataRequests {
    private BasicDataRequests() {
    }

    public static class CreatePlayerRequest {
        private Integer playerId;
        private String name;
        private Integer number;
        private String displayCode;
        private String status;
        private String operatorId;

        public Integer getPlayerId() { return playerId; }
        public void setPlayerId(Integer playerId) { this.playerId = playerId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getNumber() { return number; }
        public void setNumber(Integer number) { this.number = number; }
        public String getDisplayCode() { return displayCode; }
        public void setDisplayCode(String displayCode) { this.displayCode = displayCode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }

    public static class CreateTeamRequest {
        private Integer teamId;
        private String name;
        private String operatorId;

        public Integer getTeamId() { return teamId; }
        public void setTeamId(Integer teamId) { this.teamId = teamId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }

    public static class CreateRoundRequest {
        private Integer roundId;
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;
        private String operatorId;

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
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }

    public static class UpdateRoundStatusRequest {
        private String status;
        private String operatorId;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }

    public static class PlayerRoundRequest {
        private Integer playerId;
        private Integer roundId;
        private Integer teamId;
        private Boolean isSpy;
        private String playerStatus;
        private String operatorId;

        public Integer getPlayerId() { return playerId; }
        public void setPlayerId(Integer playerId) { this.playerId = playerId; }
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public Integer getTeamId() { return teamId; }
        public void setTeamId(Integer teamId) { this.teamId = teamId; }
        public Boolean getIsSpy() { return isSpy; }
        public void setIsSpy(Boolean isSpy) { this.isSpy = isSpy; }
        public String getPlayerStatus() { return playerStatus; }
        public void setPlayerStatus(String playerStatus) { this.playerStatus = playerStatus; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }
}
