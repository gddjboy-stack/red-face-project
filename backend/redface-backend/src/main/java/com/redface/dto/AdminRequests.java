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

    public static class ManualBonusRequest {
        private String operatorId;
        private Integer roundId;
        private String targetType; // player/team
        private Integer targetId;
        private Integer delta;
        private String idempotencyKey;
        private String reason;
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public Integer getTargetId() { return targetId; }
        public void setTargetId(Integer targetId) { this.targetId = targetId; }
        public Integer getDelta() { return delta; }
        public void setDelta(Integer delta) { this.delta = delta; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * C20-3 群投票结果录入请求。votes 为增量（可负数冲销）；idempotencyKey 由前端生成以防连点重复。
     */
    public static class GroupVoteEntryRequest {
        private Integer roundId;
        private Integer playerId;
        private Long votes;
        private String operatorId;
        private String reason;
        private String idempotencyKey;
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public Integer getPlayerId() { return playerId; }
        public void setPlayerId(Integer playerId) { this.playerId = playerId; }
        public Long getVotes() { return votes; }
        public void setVotes(Long votes) { this.votes = votes; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    /**
     * C20-4A 直播互动数据录入请求。currentTotal 为「中控台当前累计总数」而非增量，
     * 增量由系统减去水位线计算，避免运营手算出错且无从校验。
     */
    public static class LiveMetricEntryRequest {
        private String metricType;
        private Long currentTotal;
        private String operatorId;
        private String reason;
        private String idempotencyKey;
        public String getMetricType() { return metricType; }
        public void setMetricType(String metricType) { this.metricType = metricType; }
        public Long getCurrentTotal() { return currentTotal; }
        public void setCurrentTotal(Long currentTotal) { this.currentTotal = currentTotal; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    /**
     * C20-4A 水位线校准/撤销校准请求。校准只重置中控台读数基准，不改变任何选手人气值。
     */
    public static class WatermarkCalibrateRequest {
        private String operatorId;
        private String reason;
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * C20-4B 确认导入订单。必须回传预览令牌，防止「看的是 A 文件、导的是 B 文件」。
     */
    public static class OrderImportConfirmRequest {
        private String previewToken;
        private String operatorId;
        public String getPreviewToken() { return previewToken; }
        public void setPreviewToken(String previewToken) { this.previewToken = previewToken; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }

    /**
     * C20-4B 商品原价配置。单价以「元」提交（如 19.9），服务层转成「分」存储，
     * 全链路整数运算避免浮点误差。
     */
    public static class ProductPriceRequest {
        private String merchantCode;
        private String productName;
        private String unitPriceYuan;
        private String status;
        private String operatorId;
        public String getMerchantCode() { return merchantCode; }
        public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getUnitPriceYuan() { return unitPriceYuan; }
        public void setUnitPriceYuan(String unitPriceYuan) { this.unitPriceYuan = unitPriceYuan; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }
}
