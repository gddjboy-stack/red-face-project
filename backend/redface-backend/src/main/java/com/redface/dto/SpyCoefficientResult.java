package com.redface.dto;

import java.util.List;

/**
 * C20-10 卧底系数操作结果。
 *
 * <p>五种终态必须能被前端区分，不可合并成「成功/失败」两态：
 * <ul>
 *   <li>{@code applied}：本次已施加，系数已变</li>
 *   <li>{@code duplicated}：幂等拦截，早已施加过，<b>不必重来</b></li>
 *   <li>{@code rejected}：被业务规则拒绝（识破重复施加、触及上下限），<b>本次未施加</b></li>
 *   <li>{@code revoked}：撤销成功，系数已按剩余账本重建</li>
 *   <li>{@code inspected}：只读回显</li>
 * </ul>
 *
 * <p>其中 duplicated 与 rejected 最容易被混为一谈，但含义相反：前者是「已生效」，
 * 后者是「未生效」。若前端统一提示「操作失败」，运营会对已生效的加成再点一次；
 * 若统一提示「操作完成」，被拒的识破减半会被当成已标记而漏掉。
 */
public class SpyCoefficientResult {

    public static final String STATUS_APPLIED = "applied";
    public static final String STATUS_DUPLICATED = "duplicated";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_REVOKED = "revoked";
    public static final String STATUS_INSPECTED = "inspected";

    private String status;
    private Integer playerId;
    private Integer roundId;
    /** 本次施加的乘数因子×100；撤销与只读时为 null。 */
    private Integer factorApplied;
    private String factorType;
    /** 操作前系数×100。 */
    private Integer coefficientBefore;
    /** 操作后系数×100（幂等/被拒时等于当前值）。 */
    private Integer coefficientAfter;
    /** 本轮该选手未撤销的任务加成次数，界面须显示，防止运营重复施加而不自知。 */
    private int taskBonusCount;
    /** 本轮该选手是否已被识破（以账本 exposed_halve 有效记录为唯一真相来源）。 */
    private boolean exposed;
    /** 被拒原因，直接展示给运营，须含时间与操作人。 */
    private String rejectReason;
    /** 被撤销的账本条目 ID。 */
    private Long revokedLedgerId;
    /** 未折算的卧底人气裸值（仅只读回显时填充）。 */
    private Long spyPopularityRaw;
    /** 折算后的卧底人气（仅只读回显时填充）。 */
    private Long spyPopularityAdjusted;
    /** 账本明细，含已撤销条目（仅只读回显时填充）。 */
    private List<SpyCoefficientLedgerItem> ledger;

    public static SpyCoefficientResult applied(int playerId, int roundId, int factor, String factorType,
                                               int before, int after, int taskBonusCount) {
        SpyCoefficientResult r = base(STATUS_APPLIED, playerId, roundId, before, after, taskBonusCount);
        r.factorApplied = factor;
        r.factorType = factorType;
        return r;
    }

    public static SpyCoefficientResult duplicated(int playerId, int roundId, int current, int taskBonusCount) {
        return base(STATUS_DUPLICATED, playerId, roundId, current, current, taskBonusCount);
    }

    public static SpyCoefficientResult rejected(int playerId, int roundId, int current,
                                                int taskBonusCount, String reason) {
        SpyCoefficientResult r = base(STATUS_REJECTED, playerId, roundId, current, current, taskBonusCount);
        r.rejectReason = reason;
        return r;
    }

    public static SpyCoefficientResult revoked(int playerId, int roundId, long ledgerId, String factorType,
                                               int before, int after, int taskBonusCount) {
        SpyCoefficientResult r = base(STATUS_REVOKED, playerId, roundId, before, after, taskBonusCount);
        r.revokedLedgerId = ledgerId;
        r.factorType = factorType;
        return r;
    }

    public static SpyCoefficientResult inspected(int playerId, int roundId, int current, int taskBonusCount) {
        return base(STATUS_INSPECTED, playerId, roundId, current, current, taskBonusCount);
    }

    private static SpyCoefficientResult base(String status, int playerId, int roundId,
                                             int before, int after, int taskBonusCount) {
        SpyCoefficientResult r = new SpyCoefficientResult();
        r.status = status;
        r.playerId = playerId;
        r.roundId = roundId;
        r.coefficientBefore = before;
        r.coefficientAfter = after;
        r.taskBonusCount = taskBonusCount;
        return r;
    }

    /**
     * 供界面直接展示的当前累计系数文本，如「×1.3」「×0.65」。
     */
    public String getCoefficientLabel() {
        if (coefficientAfter == null) {
            return null;
        }
        return coefficientAfter % 100 == 0
                ? "×" + (coefficientAfter / 100)
                : "×" + (coefficientAfter / 100.0);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public Integer getRoundId() {
        return roundId;
    }

    public void setRoundId(Integer roundId) {
        this.roundId = roundId;
    }

    public Integer getFactorApplied() {
        return factorApplied;
    }

    public void setFactorApplied(Integer factorApplied) {
        this.factorApplied = factorApplied;
    }

    public String getFactorType() {
        return factorType;
    }

    public void setFactorType(String factorType) {
        this.factorType = factorType;
    }

    public Integer getCoefficientBefore() {
        return coefficientBefore;
    }

    public void setCoefficientBefore(Integer coefficientBefore) {
        this.coefficientBefore = coefficientBefore;
    }

    public Integer getCoefficientAfter() {
        return coefficientAfter;
    }

    public void setCoefficientAfter(Integer coefficientAfter) {
        this.coefficientAfter = coefficientAfter;
    }

    public int getTaskBonusCount() {
        return taskBonusCount;
    }

    public void setTaskBonusCount(int taskBonusCount) {
        this.taskBonusCount = taskBonusCount;
    }

    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Long getRevokedLedgerId() {
        return revokedLedgerId;
    }

    public void setRevokedLedgerId(Long revokedLedgerId) {
        this.revokedLedgerId = revokedLedgerId;
    }

    public Long getSpyPopularityRaw() {
        return spyPopularityRaw;
    }

    public void setSpyPopularityRaw(Long spyPopularityRaw) {
        this.spyPopularityRaw = spyPopularityRaw;
    }

    public Long getSpyPopularityAdjusted() {
        return spyPopularityAdjusted;
    }

    public void setSpyPopularityAdjusted(Long spyPopularityAdjusted) {
        this.spyPopularityAdjusted = spyPopularityAdjusted;
    }

    public List<SpyCoefficientLedgerItem> getLedger() {
        return ledger;
    }

    public void setLedger(List<SpyCoefficientLedgerItem> ledger) {
        this.ledger = ledger;
    }
}
