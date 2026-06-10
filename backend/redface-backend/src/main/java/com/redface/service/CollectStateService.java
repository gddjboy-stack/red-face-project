package com.redface.service;

import com.redface.entity.CollectState;
import com.redface.mapper.CollectStateMapper;
import com.redface.mapper.OperationsLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 场控状态服务。负责维护当前直播间 like/comment 总增量应归属到哪个目标。
 */
@Service
public class CollectStateService {

    private static final String MODE_PLAYER = "player";
    private static final String MODE_TEAM = "team";
    private static final String MODE_SPY = "spy";
    private static final String MODE_POOL = "pool";
    private static final String ACTION_SET_COLLECT_TARGET = "set_collect_target";

    private final CollectStateMapper collectStateMapper;
    private final OperationsLogMapper operationsLogMapper;

    public CollectStateService(CollectStateMapper collectStateMapper, OperationsLogMapper operationsLogMapper) {
        this.collectStateMapper = collectStateMapper;
        this.operationsLogMapper = operationsLogMapper;
    }

    /**
     * 设置当前场控集赞目标，并写入操作审计日志。
     *
     * @param mode       场控模式，允许 player/team/spy/pool
     * @param targetId   目标 ID，pool 模式可为空
     * @param roundId    当前轮次 ID
     * @param operatorId 操作人 ID
     */
    @Transactional
    public void setCollectTarget(String mode, Integer targetId, Integer roundId, String operatorId) {
        validateCollectTarget(mode, targetId, roundId, operatorId);
        collectStateMapper.upsert(mode, targetId, roundId, operatorId);
        operationsLogMapper.insert(
                operatorId,
                ACTION_SET_COLLECT_TARGET,
                mode + ":" + (targetId == null ? "" : targetId),
                buildDetail(mode, targetId, roundId),
                "切换场控集赞目标"
        );
    }

    /**
     * 获取当前场控状态。
     *
     * @return 当前场控状态
     */
    public CollectState getCurrent() {
        return collectStateMapper.findCurrent();
    }

    private void validateCollectTarget(String mode, Integer targetId, Integer roundId, String operatorId) {
        if (!StringUtils.hasText(mode)) {
            throw new IllegalArgumentException("mode不能为空");
        }
        if (!MODE_PLAYER.equals(mode) && !MODE_TEAM.equals(mode) && !MODE_SPY.equals(mode) && !MODE_POOL.equals(mode)) {
            throw new IllegalArgumentException("未知mode: " + mode);
        }
        if (!MODE_POOL.equals(mode) && targetId == null) {
            throw new IllegalArgumentException("非pool模式targetId不能为空");
        }
        if (roundId == null) {
            throw new IllegalArgumentException("roundId不能为空");
        }
        if (!StringUtils.hasText(operatorId)) {
            throw new IllegalArgumentException("operatorId不能为空");
        }
    }

    private String buildDetail(String mode, Integer targetId, Integer roundId) {
        return "{\"mode\":\"" + mode + "\",\"targetId\":" + (targetId == null ? "null" : targetId)
                + ",\"roundId\":" + roundId + "}";
    }
}
