package com.redface.entity;

import java.time.LocalDateTime;

/**
 * popularity_ledger 表实体，记录一次只增不改的人气值变更流水。
 */
public class PopularityLedgerEntity {
    private Long ledgerId;
    private String targetType;
    private Integer targetId;
    private String source;
    private long rawValue;
    private long popularityValue;
    private Integer roundId;
    private String idempotencyKey;
    private Long distributionBatchId;
    private String operatorId;
    private String reason;
    private String metadata;
    private LocalDateTime occurredAt;

    public Long getLedgerId() {
        return ledgerId;
    }

    public void setLedgerId(Long ledgerId) {
        this.ledgerId = ledgerId;
    }

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

    public long getPopularityValue() {
        return popularityValue;
    }

    public void setPopularityValue(long popularityValue) {
        this.popularityValue = popularityValue;
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
