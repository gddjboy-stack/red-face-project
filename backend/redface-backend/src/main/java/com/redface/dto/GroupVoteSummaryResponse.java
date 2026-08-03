package com.redface.dto;

import java.util.List;

/**
 * C20-3 群投票累计票数汇总响应。
 *
 * <p>C20-10 新增 {@code voterCount}，使汇总自带占比基数。
 */
public class GroupVoteSummaryResponse {
    private int roundId;
    private long totalVotes;
    /**
     * C20-10 本轮投票参与人数，即得票占比的分母。
     *
     * <p>null 表示<b>尚未录入</b>，与 0（确实无人投票）严格区分。
     * 前端不得将 null 渲染为 0：那会让场控误以为数据已齐而不去补录。
     */
    private Integer voterCount;
    private List<GroupVoteSummaryItem> items;

    public GroupVoteSummaryResponse() {
    }

    public GroupVoteSummaryResponse(int roundId, long totalVotes, List<GroupVoteSummaryItem> items) {
        this.roundId = roundId;
        this.totalVotes = totalVotes;
        this.items = items;
    }

    public int getRoundId() {
        return roundId;
    }

    public void setRoundId(int roundId) {
        this.roundId = roundId;
    }

    public long getTotalVotes() {
        return totalVotes;
    }

    public void setTotalVotes(long totalVotes) {
        this.totalVotes = totalVotes;
    }

    public Integer getVoterCount() {
        return voterCount;
    }

    public void setVoterCount(Integer voterCount) {
        this.voterCount = voterCount;
    }

    /**
     * 参与人数是否已录入。供前端直接判断是否要展示「待补录」提示，
     * 避免各端自行写 {@code voterCount === 0} 这类会把 0 误当未录入的判断。
     */
    public boolean isVoterCountRecorded() {
        return voterCount != null;
    }

    public List<GroupVoteSummaryItem> getItems() {
        return items;
    }

    public void setItems(List<GroupVoteSummaryItem> items) {
        this.items = items;
    }
}
