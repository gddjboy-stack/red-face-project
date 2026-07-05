package com.redface.service;

import com.redface.config.AppConstants;
import com.redface.dto.CoefficientResult;
import com.redface.entity.CoefficientLedgerEntity;
import com.redface.mapper.CoefficientLedgerMapper;
import com.redface.mapper.StatsMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import com.redface.api.ApiException;
import org.springframework.util.StringUtils;

/**
 * 加成系数服务。负责按任务结果调整选手轮次系数，并用 coefficient_ledger 保证幂等。
 */
@Service
public class CoefficientService {

    private static final String TASK_TYPE_PK_WIN = "pk_win";

    private final CoefficientLedgerMapper coefficientLedgerMapper;
    private final StatsMapper statsMapper;

    public CoefficientService(CoefficientLedgerMapper coefficientLedgerMapper, StatsMapper statsMapper) {
        this.coefficientLedgerMapper = coefficientLedgerMapper;
        this.statsMapper = statsMapper;
    }

    /**
     * 调整选手指定轮次的加成系数。同一 taskId + playerId 只允许生效一次。
     *
     * @param playerId   选手 ID
     * @param roundId    轮次 ID
     * @param taskId     任务 ID
     * @param taskType   任务类型，pk_win 表示 PK 获胜
     * @param completed  是否完成；pk_win 仅允许 true
     * @param operatorId 操作人 ID
     * @return 系数调整结果
     */
    @Transactional
    public void manualAdjustPlayer(int playerId, int roundId, int delta, String idempotencyKey, String operatorId, String reason) {
        if (Math.abs(delta) > 100) throw new ApiException(400, "单次加成调整不能超过 ±1.0");
        try {
            coefficientLedgerMapper.insert(buildLedger(playerId, roundId, "manual_" + idempotencyKey, "manual_bonus", delta, idempotencyKey, operatorId));
        } catch (DuplicateKeyException e) {
            return; // 幂等忽略
        }
        statsMapper.ensurePlayerRoundStats(playerId, roundId);
        statsMapper.incrementPlayerCoefficient(playerId, roundId, delta);
    }

    @Transactional
    public void manualAdjustTeam(int teamId, int roundId, int delta, String idempotencyKey, String operatorId, String reason) {
        if (Math.abs(delta) > 100) throw new ApiException(400, "单次加成调整不能超过 ±1.0");
        try {
            statsMapper.insertTeamCoefficientLedger(teamId, roundId, "manual_" + idempotencyKey, "manual_bonus", delta, idempotencyKey, operatorId, reason);
        } catch (DuplicateKeyException e) {
            return; // 幂等忽略
        }
        statsMapper.ensureTeamRoundStats(teamId, roundId);
        statsMapper.updateTeamCoefficient(teamId, roundId, delta);
    }

    @Transactional
    public CoefficientResult adjustCoefficient(int playerId,
                                                int roundId,
                                                String taskId,
                                                String taskType,
                                                boolean completed,
                                                String operatorId) {
        validateRequest(playerId, roundId, taskId, taskType, operatorId);
        int delta = resolveDelta(taskType, completed);
        String idempotencyKey = "coef_" + taskId + "_" + playerId;

        statsMapper.ensurePlayerRoundStats(playerId, roundId);
        try {
            coefficientLedgerMapper.insert(buildLedger(playerId, roundId, taskId, taskType, delta, idempotencyKey, operatorId));
        } catch (DuplicateKeyException e) {
            return CoefficientResult.duplicated(playerId, roundId, idempotencyKey, currentCoefficient(playerId, roundId));
        }

        int updatedRows = statsMapper.incrementPlayerCoefficient(playerId, roundId, delta);
        if (updatedRows != 1) {
            throw new IllegalStateException("更新player_round_stats.coefficient失败");
        }
        return CoefficientResult.success(playerId, roundId, delta, currentCoefficient(playerId, roundId), idempotencyKey);
    }

    private void validateRequest(int playerId, int roundId, String taskId, String taskType, String operatorId) {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId必须为正数");
        }
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("taskId不能为空");
        }
        if (!StringUtils.hasText(taskType)) {
            throw new IllegalArgumentException("taskType不能为空");
        }
        if (!StringUtils.hasText(operatorId)) {
            throw new IllegalArgumentException("operatorId不能为空");
        }
    }

    private int resolveDelta(String taskType, boolean completed) {
        if (TASK_TYPE_PK_WIN.equals(taskType)) {
            if (!completed) {
                throw new IllegalArgumentException("pk_win仅支持completed=true, 未定义PK失败扣分规则");
            }
            return AppConstants.COEFFICIENT_PK_WIN;
        }
        return completed ? AppConstants.COEFFICIENT_TASK_DELTA : -AppConstants.COEFFICIENT_TASK_DELTA;
    }

    private CoefficientLedgerEntity buildLedger(int playerId,
                                                int roundId,
                                                String taskId,
                                                String taskType,
                                                int delta,
                                                String idempotencyKey,
                                                String operatorId) {
        CoefficientLedgerEntity ledger = new CoefficientLedgerEntity();
        ledger.setPlayerId(playerId);
        ledger.setRoundId(roundId);
        ledger.setTaskId(taskId);
        ledger.setTaskType(taskType);
        ledger.setDelta(delta);
        ledger.setIdempotencyKey(idempotencyKey);
        ledger.setOperatorId(operatorId);
        ledger.setReason("系数调整:" + taskType);
        return ledger;
    }

    private int currentCoefficient(int playerId, int roundId) {
        Integer coefficient = statsMapper.findPlayerCoefficient(playerId, roundId);
        return coefficient == null ? AppConstants.COEFFICIENT_BASE : coefficient;
    }
}
