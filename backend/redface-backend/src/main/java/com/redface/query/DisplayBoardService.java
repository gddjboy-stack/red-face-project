package com.redface.query;

import com.redface.dto.DisplayBoardItem;
import com.redface.dto.DisplayBoardResponse;
import com.redface.dto.GroupVoteSummaryResponse;
import com.redface.dto.PopularityBoardItem;
import com.redface.dto.PopularityBoardResponse;
import com.redface.mapper.C9QueryMapper;
import com.redface.mapper.GroupVoteLedgerMapper;
import com.redface.dto.GroupVoteSummaryItem;
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

    public DisplayBoardService(PopularityBoardService popularityBoardService,
                               C9QueryMapper c9QueryMapper,
                               GroupVoteLedgerMapper groupVoteLedgerMapper) {
        this.popularityBoardService = popularityBoardService;
        this.c9QueryMapper = c9QueryMapper;
        this.groupVoteLedgerMapper = groupVoteLedgerMapper;
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
     * @param roundId 轮次；小于等于 0 表示自动取 active 轮次
     * @return 群投票汇总响应
     */
    public GroupVoteSummaryResponse getGroupVoteSummary(int roundId) {
        int effectiveRoundId = roundId;
        if (effectiveRoundId <= 0) {
            RoundSummary activeRound = c9QueryMapper.findLatestActiveRound();
            effectiveRoundId = activeRound == null ? 0 : activeRound.getRoundId();
        }
        if (effectiveRoundId <= 0) {
            return new GroupVoteSummaryResponse(0, 0L, List.of());
        }
        List<GroupVoteSummaryItem> items = groupVoteLedgerMapper.summarize(effectiveRoundId);
        long total = items.stream().mapToLong(GroupVoteSummaryItem::getTotalVotes).sum();
        return new GroupVoteSummaryResponse(effectiveRoundId, total, items);
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
