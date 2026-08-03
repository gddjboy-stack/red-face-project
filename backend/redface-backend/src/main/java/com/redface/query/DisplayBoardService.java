package com.redface.query;

import com.redface.dto.DisplayBoardItem;
import com.redface.dto.DisplayBoardResponse;
import com.redface.dto.DisplayGroupVoteItem;
import com.redface.dto.DisplayGroupVoteResponse;
import com.redface.dto.PopularityBoardItem;
import com.redface.dto.PopularityBoardResponse;
import com.redface.mapper.C9QueryMapper;
import com.redface.mapper.GroupVoteLedgerMapper;
import com.redface.dto.GroupVoteSummaryItem;
import com.redface.mapper.RoundMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * C20-5 大屏展示只读聚合服务。
 *
 * <p>复用既有 {@link PopularityBoardService} 与 {@link GroupVoteLedgerMapper} 的查询结果，
 * 不新增任何写路径；在此之上做两件展示层的事：按分值降序计算名次，以及裁剪掉
 * 卧底标记等不应上大屏的字段（见 {@link DisplayBoardItem}）。
 *
 * <p>{@code tab} 仅接受 {@code player} 与 {@code team}。<b>不接受 {@code spy}</b>——
 * 卧底看板即使带正确展示令牌也不可通过大屏接口读取，避免误操作把赛制机密投到屏上。
 */
@Service
public class DisplayBoardService {

    private static final String TAB_PLAYER = "player";
    private static final String TAB_TEAM = "team";

    private final PopularityBoardService popularityBoardService;
    private final C9QueryMapper c9QueryMapper;
    private final GroupVoteLedgerMapper groupVoteLedgerMapper;
    private final RoundMapper roundMapper;

    public DisplayBoardService(PopularityBoardService popularityBoardService,
                               C9QueryMapper c9QueryMapper,
                               GroupVoteLedgerMapper groupVoteLedgerMapper,
                               RoundMapper roundMapper) {
        this.popularityBoardService = popularityBoardService;
        this.c9QueryMapper = c9QueryMapper;
        this.groupVoteLedgerMapper = groupVoteLedgerMapper;
        this.roundMapper = roundMapper;
    }

    /**
     * 查询大屏排行榜。roundId 省略时自动取当前 active 轮次。
     *
     * @param tab     看板类型，仅支持 player / team
     * @param roundId 轮次；小于等于 0 表示自动取 active 轮次
     * @return 大屏排行榜响应
     */
    public DisplayBoardResponse getBoard(String tab, int roundId) {
        String normalizedTab = normalizeTab(tab);
        DisplayBoardResponse response = new DisplayBoardResponse();
        response.setTab(normalizedTab);
        response.setServerTime(Instant.now().getEpochSecond());

        RoundSummary activeRound = c9QueryMapper.findLatestActiveRound();
        int effectiveRoundId = roundId > 0 ? roundId : (activeRound == null ? 0 : activeRound.getRoundId());
        if (effectiveRoundId <= 0) {
            // 无 active 轮次且未显式指定：返回空榜而非报错，避免大屏在开播前显示错误页。
            response.setLiveStatus("idle");
            response.setItems(List.of());
            return response;
        }

        response.setLiveStatus(activeRound != null && activeRound.getRoundId() == effectiveRoundId ? "live" : "idle");
        response.setRoundId(effectiveRoundId);
        response.setRoundName(activeRound == null ? null : activeRound.getRoundName());

        PopularityBoardResponse source = popularityBoardService.getBoard(normalizedTab, effectiveRoundId);
        response.setItems(toRankedItems(source.getItems()));
        return response;
    }

    /**
     * 查询大屏群投票汇总。数据来自独立账本 group_vote_ledger，不与人气账本混算。
     *
     * <p>C20-10 带上参与人数与得票占比，使大屏能打出「得票 42%」这类读数。
     *
     * <p><b>返回专用的 {@link DisplayGroupVoteResponse} 而非后台的
     * {@code GroupVoteSummaryResponse}</b>：后者带 {@code exposed}（识破标记）。
     * 此处曾经的实现是复用后台 DTO 但「不给 exposed 赋值」，那是错的：
     * 字段仍在类上，Jackson 照样输出 {@code "exposed": false}，观众开控制台即可看到。
     * 安全边界必须由<b>类型定义</b>保证，而不是靠「记得不赋值」的约定。
     * 同理，本类拒接 {@code tab=spy}。
     *
     * @param roundId 轮次；小于等于 0 表示自动取当前 active 轮次
     * @return 大屏群投票汇总响应（无识破标记）
     */
    public DisplayGroupVoteResponse getGroupVoteSummary(int roundId) {
        int effectiveRoundId = roundId;
        if (effectiveRoundId <= 0) {
            RoundSummary activeRound = c9QueryMapper.findLatestActiveRound();
            effectiveRoundId = activeRound == null ? 0 : activeRound.getRoundId();
        }
        if (effectiveRoundId <= 0) {
            return new DisplayGroupVoteResponse(0, 0L, null, List.of());
        }
        List<GroupVoteSummaryItem> source = groupVoteLedgerMapper.summarize(effectiveRoundId);
        if (source == null) {
            source = List.of();
        }
        long total = source.stream().mapToLong(GroupVoteSummaryItem::getTotalVotes).sum();
        Integer voterCount = roundMapper.findVoterCount(effectiveRoundId);
        List<DisplayGroupVoteItem> items = new ArrayList<>(source.size());
        for (GroupVoteSummaryItem item : source) {
            // 占比在此处重算而非读源对象：源对象的 votePercent 由后台路径填充，
            // 两边各算一次看似冗余，但它保证大屏不依赖后台 DTO 的任何中间状态。
            Double percent = null;
            if (voterCount != null) {
                percent = voterCount > 0
                        ? Math.round(item.getTotalVotes() * 1000.0 / voterCount) / 10.0
                        : 0.0;
            }
            items.add(new DisplayGroupVoteItem(item.getPlayerId(), item.getPlayerName(),
                    item.getPlayerNumber(), item.getTotalVotes(), item.getEntryCount(), percent));
        }
        return new DisplayGroupVoteResponse(effectiveRoundId, total, voterCount, items);
    }

    private List<DisplayBoardItem> toRankedItems(List<PopularityBoardItem> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<PopularityBoardItem> sorted = new ArrayList<>(source);
        // 分值降序；同分按编号升序，保证同一时刻多屏渲染顺序一致。
        sorted.sort(Comparator.comparingLong(PopularityBoardItem::getValue).reversed()
                .thenComparing(item -> item.getNumber() == null ? Integer.MAX_VALUE : item.getNumber()));

        List<DisplayBoardItem> result = new ArrayList<>(sorted.size());
        int rank = 0;
        long previousValue = Long.MIN_VALUE;
        for (int index = 0; index < sorted.size(); index++) {
            PopularityBoardItem item = sorted.get(index);
            // 同分并列同名次，下一个名次跳号（1,1,3），与体育赛事惯例一致。
            if (item.getValue() != previousValue) {
                rank = index + 1;
                previousValue = item.getValue();
            }
            result.add(new DisplayBoardItem(rank, item.getNumber(), item.getName(),
                    item.getTeamName(), item.getValue()));
        }
        return result;
    }

    private String normalizeTab(String tab) {
        if (!StringUtils.hasText(tab)) {
            return TAB_PLAYER;
        }
        String normalized = tab.trim().toLowerCase();
        if (!TAB_PLAYER.equals(normalized) && !TAB_TEAM.equals(normalized)) {
            throw new IllegalArgumentException("大屏仅支持 player / team 看板: " + tab);
        }
        return normalized;
    }
}
