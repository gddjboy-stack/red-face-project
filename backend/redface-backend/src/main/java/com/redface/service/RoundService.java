package com.redface.service;

import com.redface.mapper.RoundMapper;
import org.springframework.stereotype.Service;

/**
 * 轮次服务。负责决定异步核销/入账应归属的轮次。
 */
@Service
public class RoundService {

    private final RoundMapper roundMapper;

    public RoundService(RoundMapper roundMapper) {
        this.roundMapper = roundMapper;
    }

    /**
     * 获取核销/入账应归属的轮次。
     * 规则：优先取当前 active 轮次；若无 active，则取最早 upcoming 轮次；若仍不存在则返回 null。
     *
     * @return 入账轮次 ID；无可用轮次时返回 null
     */
    public Integer getCurrentAccrualRoundId() {
        Integer activeRoundId = roundMapper.findLatestActiveRoundId();
        if (activeRoundId != null) {
            return activeRoundId;
        }
        return roundMapper.findEarliestUpcomingRoundId();
    }
}
