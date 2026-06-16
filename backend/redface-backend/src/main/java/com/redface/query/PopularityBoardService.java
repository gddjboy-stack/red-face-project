package com.redface.query;

import com.redface.dto.PopularityBoardItem;
import com.redface.dto.PopularityBoardResponse;
import com.redface.mapper.C9QueryMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * API-2 人气看板只读聚合服务。排序规则服务端强制执行，不按人气值排序。
 */
@Service
public class PopularityBoardService {
    private static final String TAB_PLAYER = "player";
    private static final String TAB_TEAM = "team";
    private static final String TAB_SPY = "spy";

    private final C9QueryMapper c9QueryMapper;

    public PopularityBoardService(C9QueryMapper c9QueryMapper) {
        this.c9QueryMapper = c9QueryMapper;
    }

    public PopularityBoardResponse getBoard(String tab, int roundId) {
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        String normalizedTab = normalizeTab(tab);
        List<PopularityBoardItem> items;
        if (TAB_TEAM.equals(normalizedTab)) {
            items = c9QueryMapper.findTeamBoard(roundId);
        } else if (TAB_SPY.equals(normalizedTab)) {
            items = c9QueryMapper.findSpyBoard(roundId);
        } else {
            items = c9QueryMapper.findPlayerBoard(roundId);
        }
        PopularityBoardResponse response = new PopularityBoardResponse();
        response.setTab(normalizedTab);
        response.setRoundId(roundId);
        response.setSpyTabEnabled(false);
        response.setItems(items);
        return response;
    }

    private String normalizeTab(String tab) {
        if (!StringUtils.hasText(tab)) {
            return TAB_PLAYER;
        }
        String normalized = tab.trim().toLowerCase();
        if (!TAB_PLAYER.equals(normalized) && !TAB_TEAM.equals(normalized) && !TAB_SPY.equals(normalized)) {
            throw new IllegalArgumentException("未知tab: " + tab);
        }
        return normalized;
    }
}
