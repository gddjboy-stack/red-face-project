package com.redface.dto;

/**
 * C20-10 大屏群投票汇总项。
 *
 * <p><b>为什么不直接复用 {@link GroupVoteSummaryItem}</b>：
 * 后者带 {@code exposed}（识破标记）。最初的做法是复用同一个类、大屏侧「不给
 * exposed 赋值」，以为这样就不会泄露。这个想法是错的：字段仍在类上，Jackson
 * 照样序列化，观众打开浏览器控制台会看到 {@code "exposed": false} 出现在每一项里。
 * 恒为 false 反而坐实了该字段真实存在——某一轮一旦有人变 true，卧底立刻被锁定；
 * 即便永远不变，字段名本身也已泄露「系统在跟踪谁被识破」这一赛制信息。
 *
 * <p>因此大屏侧必须用一个<b>物理上不含该字段</b>的类型。安全边界要靠类型定义来保证，
 * 而不是靠「记得不要赋值」这种约定——约定会在下一次有人图省事复用 DTO 时失效。
 *
 * <p>同理，本类也不应在日后被加上任何卧底相关字段。要加，请先想清楚它会不会
 * 出现在观众可见的响应体里。
 */
public class DisplayGroupVoteItem {

    private final Integer playerId;
    private final String playerName;
    private final Integer playerNumber;
    private final long totalVotes;
    private final long entryCount;
    /**
     * 得票占比（百分数值，保留一位小数）。
     *
     * <p>{@code null} 表示本轮参与人数未录入，占比算不出。大屏应显示为空或「--」，
     * <b>不得显示 0%</b>：观众会把「还没数人」误读成「这位选手一票没有」。
     */
    private final Double votePercent;

    public DisplayGroupVoteItem(Integer playerId, String playerName, Integer playerNumber,
                                long totalVotes, long entryCount, Double votePercent) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.playerNumber = playerNumber;
        this.totalVotes = totalVotes;
        this.entryCount = entryCount;
        this.votePercent = votePercent;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Integer getPlayerNumber() {
        return playerNumber;
    }

    public long getTotalVotes() {
        return totalVotes;
    }

    public long getEntryCount() {
        return entryCount;
    }

    public Double getVotePercent() {
        return votePercent;
    }
}
