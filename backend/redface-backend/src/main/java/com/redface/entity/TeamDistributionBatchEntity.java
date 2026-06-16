package com.redface.entity;

import java.time.LocalDateTime;

/**
 * team_distribution_batches 表实体。记录一次团队池分配的批次元数据。
 */
public class TeamDistributionBatchEntity {
    private Long batchId;
    private Integer teamId;
    private Integer roundId;
    private Long totalValue;
    private String method;
    private String customWeights;
    private String operatorId;
    private String reason;
    private LocalDateTime createdAt;

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public Integer getRoundId() {
        return roundId;
    }

    public void setRoundId(Integer roundId) {
        this.roundId = roundId;
    }

    public Long getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(Long totalValue) {
        this.totalValue = totalValue;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getCustomWeights() {
        return customWeights;
    }

    public void setCustomWeights(String customWeights) {
        this.customWeights = customWeights;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
