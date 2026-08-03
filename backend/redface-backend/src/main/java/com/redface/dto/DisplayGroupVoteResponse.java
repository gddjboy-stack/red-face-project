package com.redface.dto;

import java.util.List;

/**
 * C20-10 大屏群投票汇总响应。与后台的 {@link GroupVoteSummaryResponse} 分开定义，
 * 因为二者可见性边界不同：后台可看识破标记，大屏不可（详见 {@link DisplayGroupVoteItem}）。
 *
 * <p>字段名与后台版保持一致，使大屏前端的取数逻辑无需改写。
 */
public class DisplayGroupVoteResponse {

    private final int roundId;
    private final long totalVotes;
    /**
     * 本轮投票参与人数，即得票占比的分母。
     *
     * <p>{@code null} 表示尚未录入。大屏不得渲染成 0。
     */
    private final Integer voterCount;
    private final List<DisplayGroupVoteItem> items;

    public DisplayGroupVoteResponse(int roundId, long totalVotes,
                                    Integer voterCount, List<DisplayGroupVoteItem> items) {
        this.roundId = roundId;
        this.totalVotes = totalVotes;
        this.voterCount = voterCount;
        this.items = items;
    }

    public int getRoundId() {
        return roundId;
    }

    public long getTotalVotes() {
        return totalVotes;
    }

    public Integer getVoterCount() {
        return voterCount;
    }

    /** 参与人数是否已录入。避免各端自行写 {@code voterCount === 0} 这类误判。 */
    public boolean isVoterCountRecorded() {
        return voterCount != null;
    }

    public List<DisplayGroupVoteItem> getItems() {
        return items;
    }
}
