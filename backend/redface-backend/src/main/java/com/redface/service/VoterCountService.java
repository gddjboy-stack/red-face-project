package com.redface.service;

import com.redface.dto.GroupVoteSummaryItem;
import com.redface.dto.VoterCountResult;
import com.redface.mapper.GroupVoteLedgerMapper;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.RoundMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C20-10 投票参与人数服务。
 *
 * <p>参与人数（{@code rounds.voter_count}）的唯一用途是算出「得票占比」，
 * 让大屏与场控知道某位选手拿到了多少比例的票。它本身不参与人气计算。
 *
 * <p><b>两侧校验的原因</b>：录入顺序在现场无法约束。有时先数完人数再统计票数，
 * 有时票数先出来。因此「参与人数不得小于任一选手得票数」这条约束必须在两侧都查：
 * 只在一侧查，另一侧就能绕过去，最终得到 120% 的得票占比挂在大屏上。
 *
 * <p><b>为什么只有参与人数一侧允许强制覆盖</b>：参与人数是人工清点的观测值，
 * 数错、漏数、中途有人进场都很常见，必须允许更正。而票数是累计流水，
 * 覆盖会破坏账本的可追溯性——票数录错只能用负数冲销，让错误与更正都留在账上。
 */
@Service
public class VoterCountService {

    private final RoundMapper roundMapper;
    private final GroupVoteLedgerMapper groupVoteLedgerMapper;
    private final OperationsLogMapper operationsLogMapper;

    public VoterCountService(RoundMapper roundMapper,
                             GroupVoteLedgerMapper groupVoteLedgerMapper,
                             OperationsLogMapper operationsLogMapper) {
        this.roundMapper = roundMapper;
        this.groupVoteLedgerMapper = groupVoteLedgerMapper;
        this.operationsLogMapper = operationsLogMapper;
    }

    /**
     * 录入或更正本轮投票参与人数。
     *
     * @param confirmed 为 true 时表示运营已在弹窗中确认覆盖/冲突，跳过二次确认拦截
     */
    @Transactional
    public VoterCountResult record(int roundId, int voterCount, boolean confirmed,
                                   String operatorId, String reason) {
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        // 允许 0：0 表示「确实一个人都没投」，是合法观测值。负数无业务含义。
        if (voterCount < 0) {
            throw new IllegalArgumentException("参与人数不能为负数，收到：" + voterCount);
        }
        if (!StringUtils.hasText(operatorId)) {
            throw new IllegalArgumentException("operatorId不能为空");
        }
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("reason不能为空");
        }

        Integer existing = roundMapper.findVoterCount(roundId);
        GroupVoteSummaryItem top = groupVoteLedgerMapper.findTopVoted(roundId);
        long topVotes = top == null ? 0L : top.getTotalVotes();

        // 冲突一：参与人数小于已录得票最高值。数学上不可能，必然一侧录错。
        String conflict = null;
        if (top != null && voterCount < topVotes) {
            conflict = "本次要录入的参与人数 " + voterCount + " 人，小于本轮已录得票最高的选手「"
                    + describePlayer(top) + "」的 " + topVotes + " 票。"
                    + "两者必有一个录错：若是人数数错，请改人数；若是票数录错，"
                    + "请到群投票录入用负数冲销后重录（票数不提供覆盖）";
        }

        // 冲突二：覆盖已有值。覆盖本身合法，但必须让运营看见旧值，
        // 否则「以为在首次录入、实际抹掉了别人录的数」不会有任何痕迹提示。
        String overwriteNote = null;
        if (existing != null && !existing.equals(voterCount)) {
            overwriteNote = "本轮参与人数已由他人或此前录为 " + existing + " 人，本次将改为 "
                    + voterCount + " 人。旧值会被覆盖（覆盖记录写入操作日志，可追溯）";
        }

        if (!confirmed && (conflict != null || overwriteNote != null)) {
            StringBuilder sb = new StringBuilder();
            if (conflict != null) {
                sb.append(conflict);
            }
            if (overwriteNote != null) {
                if (sb.length() > 0) {
                    sb.append("；另外：");
                }
                sb.append(overwriteNote);
            }
            VoterCountResult r = VoterCountResult.needsConfirm(roundId, existing, voterCount, sb.toString());
            r.setTopVotes(topVotes);
            r.setTopPlayerName(top == null ? null : top.getPlayerName());
            return r;
        }

        // 留痕先于写入。若日志写失败则整笔回滚，宁可不写，
        // 也不允许出现「参与人数变了但查不到是谁改的、原值是多少」。
        operationsLogMapper.insert(operatorId,
                existing == null ? "voter_count_entry" : "voter_count_overwrite",
                "round:" + roundId,
                "{\"roundId\":" + roundId
                        + ",\"voterCountBefore\":" + (existing == null ? "null" : existing)
                        + ",\"voterCountAfter\":" + voterCount
                        + ",\"topVotes\":" + topVotes
                        + ",\"topPlayerId\":" + (top == null ? "null" : top.getPlayerId())
                        + ",\"conflict\":" + (conflict == null ? "null" : "\"" + safe(conflict) + "\"")
                        + ",\"confirmed\":" + confirmed + "}",
                reason);

        int rows = roundMapper.updateVoterCount(roundId, voterCount);
        if (rows != 1) {
            throw new IllegalArgumentException("轮次不存在或未更新：roundId=" + roundId);
        }

        VoterCountResult result = VoterCountResult.recorded(roundId, existing, voterCount);
        result.setTopVotes(topVotes);
        result.setTopPlayerName(top == null ? null : top.getPlayerName());
        result.setForcedOverConflict(conflict != null);
        return result;
    }

    /**
     * 校验一笔即将录入的票数是否会超过已录参与人数。
     *
     * <p>由群投票录入侧调用。返回 null 表示无冲突；返回非 null 时为提示文案。
     * 这里<b>只提示不阻断</b>由调用方决定：票数一侧的正确修复动作是负数冲销，
     * 硬阻断会让现场在参与人数录错时完全无法录票。
     *
     * @param votesAfter 该选手本次录入后的累计票数
     */
    public String checkAgainstVoterCount(int roundId, String playerName, long votesAfter) {
        Integer voterCount = roundMapper.findVoterCount(roundId);
        if (voterCount == null) {
            // 未录参与人数：不是错误，只是还没录，此时无从校验。
            return null;
        }
        if (votesAfter <= voterCount) {
            return null;
        }
        return "选手「" + (StringUtils.hasText(playerName) ? playerName : "未知")
                + "」录入后累计 " + votesAfter + " 票，已超过本轮已录参与人数 " + voterCount
                + " 人。请核对：若票数录错，用负数冲销；若人数数错，到参与人数录入处更正";
    }

    /**
     * 查询本轮参与人数与得票占比基数，供界面回显。
     *
     * @return 参与人数；未录入时返回 null（不可折成 0，否则占比会除零且漏录提示永不出现）
     */
    public Integer getVoterCount(int roundId) {
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        return roundMapper.findVoterCount(roundId);
    }

    private String describePlayer(GroupVoteSummaryItem item) {
        String name = StringUtils.hasText(item.getPlayerName()) ? item.getPlayerName() : "未知姓名";
        return item.getPlayerNumber() == null ? name : (item.getPlayerNumber() + "号 " + name);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
