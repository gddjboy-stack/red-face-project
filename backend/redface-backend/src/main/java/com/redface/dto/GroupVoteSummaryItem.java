package com.redface.dto;

/**
 * C20-3 群投票累计票数汇总项。totalVotes 为冲销后净值（raw_value 求和）。
 *
 * <p>C20-10 新增 {@code votePercent} 与 {@code exposed} 两个展示字段。
 */
public class GroupVoteSummaryItem {
    private Integer playerId;
    private String playerName;
    private Integer playerNumber;
    private long totalVotes;
    private long entryCount;
    /**
     * C20-10 得票占比（百分数值，保留一位小数，如68.4 表示 68.4%）。
     *
     * <p><b>null 不是 0</b>：本轮参与人数未录入时为 null，表示「算不出」；
     * 录了参与人数但该选手 0 票时为 0.0，表示「确实无人投他」。
     * 两者在界面上必须分开展示（前者显示「——」并提示补录），
     * 否则漏录参与人数会伪装成「全场零票」。
     */
    private Double votePercent;
    /**
     * C20-10 本轮是否已被识破。以 spy_coefficient_ledger 中
     * factor_type='exposed_halve' 的未撤销记录为唯一真相来源，
     * 不读已废弃的 player_round.spy_status（见 DEBT-003）。
     */
    private boolean exposed;

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Integer getPlayerNumber() {
        return playerNumber;
    }

    public void setPlayerNumber(Integer playerNumber) {
        this.playerNumber = playerNumber;
    }

    public long getTotalVotes() {
        return totalVotes;
    }

    public void setTotalVotes(long totalVotes) {
        this.totalVotes = totalVotes;
    }

    public long getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(long entryCount) {
        this.entryCount = entryCount;
    }

    public Double getVotePercent() {
        return votePercent;
    }

    public void setVotePercent(Double votePercent) {
        this.votePercent = votePercent;
    }

    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }
}
