package com.redface.dto;

/**
 * C20-10 投票参与人数录入结果。
 *
 * <p>两种终态：{@code recorded}（已写入）与 {@code needs_confirm}（尚未写入，等确认）。
 * 后者必须与前者严格区分：若前端把 needs_confirm 显示成「录入成功」，
 * 参与人数会一直是空值，而大屏上的得票占比会永远显示不出来，
 * 现场只会看到一片空白而不知道原因。
 */
public class VoterCountResult {

    public static final String STATUS_RECORDED = "recorded";
    public static final String STATUS_NEEDS_CONFIRM = "needs_confirm";

    private String status;
    private Integer roundId;
    /** 录入前的参与人数；null 表示本轮此前未录入。 */
    private Integer voterCountBefore;
    /** 本次要录入/已录入的参与人数。 */
    private Integer voterCountAfter;
    /** 本轮已录得票最高值，供界面与确认弹窗展示。 */
    private long topVotes;
    /** 得票最高选手姓名，用于定位该核对谁的票。 */
    private String topPlayerName;
    /** 需二次确认时的完整冲突说明，直接展示给运营。 */
    private String confirmReason;
    /** 是否在「参与人数小于最高得票」的冲突下被强制写入，用于界面继续标红提醒。 */
    private boolean forcedOverConflict;

    public static VoterCountResult recorded(int roundId, Integer before, int after) {
        VoterCountResult r = new VoterCountResult();
        r.status = STATUS_RECORDED;
        r.roundId = roundId;
        r.voterCountBefore = before;
        r.voterCountAfter = after;
        return r;
    }

    public static VoterCountResult needsConfirm(int roundId, Integer before, int after, String reason) {
        VoterCountResult r = new VoterCountResult();
        r.status = STATUS_NEEDS_CONFIRM;
        r.roundId = roundId;
        r.voterCountBefore = before;
        r.voterCountAfter = after;
        r.confirmReason = reason;
        return r;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRoundId() {
        return roundId;
    }

    public void setRoundId(Integer roundId) {
        this.roundId = roundId;
    }

    public Integer getVoterCountBefore() {
        return voterCountBefore;
    }

    public void setVoterCountBefore(Integer voterCountBefore) {
        this.voterCountBefore = voterCountBefore;
    }

    public Integer getVoterCountAfter() {
        return voterCountAfter;
    }

    public void setVoterCountAfter(Integer voterCountAfter) {
        this.voterCountAfter = voterCountAfter;
    }

    public long getTopVotes() {
        return topVotes;
    }

    public void setTopVotes(long topVotes) {
        this.topVotes = topVotes;
    }

    public String getTopPlayerName() {
        return topPlayerName;
    }

    public void setTopPlayerName(String topPlayerName) {
        this.topPlayerName = topPlayerName;
    }

    public String getConfirmReason() {
        return confirmReason;
    }

    public void setConfirmReason(String confirmReason) {
        this.confirmReason = confirmReason;
    }

    public boolean isForcedOverConflict() {
        return forcedOverConflict;
    }

    public void setForcedOverConflict(boolean forcedOverConflict) {
        this.forcedOverConflict = forcedOverConflict;
    }
}
