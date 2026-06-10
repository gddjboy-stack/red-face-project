package com.redface.dto;

import java.time.LocalDateTime;

/**
 * 人气值变更请求对象。所有人气值变更必须通过 PopularityService.applyChange 处理。
 */
public class PopularityChangeRequest {
    private String targetType;
    private Integer targetId;
    private String source;
    private long rawValue;
    private Integer roundId;
    private String idempotencyKey;
    private Long distributionBatchId;
    private String operatorId;
    private String reason;
    private String metadata;
    private LocalDateTime occurredAt;

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Integer getTargetId() {
        return targetId;
    }

    public void setTargetId(Integer targetId) {
        this.targetId = targetId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public long getRawValue() {
        return rawValue;
    }

    public void setRawValue(long rawValue) {
        this.rawValue = rawValue;
    }

    public Integer getRoundId() {
        return roundId;
    }

    public void setRoundId(Integer roundId) {
        this.roundId = roundId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getDistributionBatchId() {
        return distributionBatchId;
    }

    public void setDistributionBatchId(Long distributionBatchId) {
        this.distributionBatchId = distributionBatchId;
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

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
