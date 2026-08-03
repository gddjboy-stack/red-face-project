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
     * C20-4C 确认导入并显式排除未归属订单。
     *
     * <p>{@code overrideSubOrderNos} 必须逐笔列出且与预览的未归属行完全一致；
     * 设计上不接受「全选标志位」这类参数——一个布尔值能被前端默认勾上，
     * 而逐笔子订单号无法在不看内容的情况下凭空填写。
     * {@code overrideReason} 为必填，空白将被服务层拒绝。
     */
    public static class OrderImportOverrideRequest {
        private String previewToken;
        private String operatorId;
        private java.util.List<String> overrideSubOrderNos;
        private String overrideReason;
        public String getPreviewToken() { return previewToken; }
        public void setPreviewToken(String previewToken) { this.previewToken = previewToken; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public java.util.List<String> getOverrideSubOrderNos() { return overrideSubOrderNos; }
        public void setOverrideSubOrderNos(java.util.List<String> overrideSubOrderNos) {
            this.overrideSubOrderNos = overrideSubOrderNos;
        }
        public String getOverrideReason() { return overrideReason; }
        public void setOverrideReason(String overrideReason) { this.overrideReason = overrideReason; }
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

    /**
     * C20-6 后台手工销量录入请求。
     *
     * <p>{@code quantity} 允许负数（冲销纠错），但不允许为 0——0 件既不改变账面
     * 也不表达意图，通常是运营填错或前端未校验，静默接受只会在账本里留下无意义的空记录。
     *
     * <p>{@code confirmed} 用于软重复与异常量的二次确认：首次提交置 false，
     * 若服务端检测到「近期已有完全相同的录入」或「单笔人气异常偏高」，
     * 返回 needs_confirm 且<b>不入账</b>；运营看清提示后带 confirmed=true 重提才真正入账。
     * 两次提交的幂等键必须一致，因此二次确认不会造成重复入账。
     */
    public static class ManualSalesEntryRequest {
        private Integer roundId;
        private Integer playerId;
        private String merchantCode;
        private Integer quantity;
        private String operatorId;
        private String reason;
        private String idempotencyKey;
        private Boolean confirmed;
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public Integer getPlayerId() { return playerId; }
        public void setPlayerId(Integer playerId) { this.playerId = playerId; }
        public String getMerchantCode() { return merchantCode; }
        public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
        public Boolean getConfirmed() { return confirmed; }
        public void setConfirmed(Boolean confirmed) { this.confirmed = confirmed; }
    }

    /**
     * C20-10 投票参与人数录入请求。
     *
     * <p>{@code voterCount} 允许为 0（0 表示确实无人投票），不允许为负数。
     * {@code confirmed=true} 表示运营已在二次确认弹窗中看过冲突详情并坚持写入。
     */
    public static class VoterCountEntryRequest {
        private Integer roundId;
        private Integer voterCount;
        private String operatorId;
        private String reason;
        private Boolean confirmed;
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public Integer getVoterCount() { return voterCount; }
        public void setVoterCount(Integer voterCount) { this.voterCount = voterCount; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Boolean getConfirmed() { return confirmed; }
        public void setConfirmed(Boolean confirmed) { this.confirmed = confirmed; }
    }

    /**
     * C20-10 卧底人气系数施加请求。
     *
     * <p><b>{@code factor} 是乘数因子×100（130=×1.3，50=×0.5），不是增量。</b>
     * 与 {@link ManualBonusRequest#getDelta()} 的加法语义不同，不可套用：
     * 传 130 表示「乘 1.3」，而非「加 1.3」。
     *
     * <p>{@code factorType} 只接受 task_bonus 与 exposed_halve。后者 factor 固定 50，
     * 且同轮同选手只能施加一次。
     */
    public static class SpyCoefficientApplyRequest {
        private Integer roundId;
        private Integer playerId;
        private Integer factor;
        private String factorType;
        private String operatorId;
        private String reason;
        private String idempotencyKey;
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public Integer getPlayerId() { return playerId; }
        public void setPlayerId(Integer playerId) { this.playerId = playerId; }
        public Integer getFactor() { return factor; }
        public void setFactor(Integer factor) { this.factor = factor; }
        public String getFactorType() { return factorType; }
        public void setFactorType(String factorType) { this.factorType = factorType; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    /**
     * C20-10 卧底人气系数撤销请求。必须同时传 playerId 与 roundId，
     * 服务层会校验账本条目归属，防跳轮/跳选手误撤。
     */
    public static class SpyCoefficientRevokeRequest {
        private Long ledgerId;
        private Integer roundId;
        private Integer playerId;
        private String operatorId;
        private String reason;
        public Long getLedgerId() { return ledgerId; }
        public void setLedgerId(Long ledgerId) { this.ledgerId = ledgerId; }
        public Integer getRoundId() { return roundId; }
        public void setRoundId(Integer roundId) { this.roundId = roundId; }
        public Integer getPlayerId() { return playerId; }
        public void setPlayerId(Integer playerId) { this.playerId = playerId; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
