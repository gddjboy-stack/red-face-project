package com.redface.dto;

import java.util.List;

/**
 * C20-3 群投票累计票数汇总响应。
 */
public class GroupVoteSummaryResponse {
    private int roundId;
    private long totalVotes;
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

    public List<GroupVoteSummaryItem> getItems() {
        return items;
    }

    public void setItems(List<GroupVoteSummaryItem> items) {
        this.items = items;
    }
}
