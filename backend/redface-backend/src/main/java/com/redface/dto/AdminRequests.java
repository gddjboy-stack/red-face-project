package com.redface.dto;

import java.util.Map;

/**
 * C10 场控后台请求 DTO 集合。Controller 只接收受控字段，避免前端直接操纵底层业务 DTO。
 */
public final class AdminRequests {
    private AdminRequests() {
    }

    public static class CollectStateRequest {
        private String mode;
        private Integer targetId;
        private Integer roundId;
        private String operatorId;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Integer getTargetId() { return targetId; }
        public void setTargetId(Integer targetId) { this.targetId = targetId; }
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }

    public static class SimulateInjectRequest {
        private String eventType;
        private long value;
        private Integer targetId;
        private String operatorId;

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public long getValue() { return value; }
        public void setValue(long value) { this.value = value; }
        public Integer getTargetId() { return targetId; }
        public void setTargetId(Integer targetId) { this.targetId = targetId; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }

    public static class ManualAdjustRequest {
        private String targetType;
        private Integer targetId;
        private Integer roundId;
        private long rawValue;
        private String operatorId;
        private String reason;

        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public Integer getTargetId() { return targetId; }
        public void setTargetId(Integer targetId) { this.targetId = targetId; }
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public long getRawValue() { return rawValue; }
        public void setRawValue(long rawValue) { this.rawValue = rawValue; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class TeamDistributionRequest {
        private int teamId;
        private int roundId;
        private String method;
        private Map<Integer, Integer> customWeights;
        private String operatorId;
        private String reason;

        public int getTeamId() { return teamId; }
        public void setTeamId(int teamId) { this.teamId = teamId; }
        public int getRoundId() { return roundId; }
        public void setRoundId(int roundId) { this.roundId = roundId; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public Map<Integer, Integer> getCustomWeights() { return customWeights; }
        public void setCustomWeights(Map<Integer, Integer> customWeights) { this.customWeights = customWeights; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
