package com.redface.service;

import com.redface.dto.DistributionResult;
import com.redface.dto.PopularityChangeRequest;
import com.redface.entity.TeamDistributionBatchEntity;
import com.redface.mapper.PlayerRoundMapper;
import com.redface.mapper.StatsMapper;
import com.redface.mapper.TeamDistributionBatchMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 团队池分配服务。负责将团队池当前全部余额按指定方法分配给团队成员。
 */
@Service
public class TeamDistributionService {

    private static final String METHOD_EQUAL = "equal";
    private static final String METHOD_CUSTOM = "custom";
    private static final String TARGET_PLAYER = "player";
    private static final String SOURCE_TEAM_DISTRIBUTION = "team_distribution";

    private final StatsMapper statsMapper;
    private final PlayerRoundMapper playerRoundMapper;
    private final TeamDistributionBatchMapper batchMapper;
    private final PopularityService popularityService;

    public TeamDistributionService(StatsMapper statsMapper,
                                   PlayerRoundMapper playerRoundMapper,
                                   TeamDistributionBatchMapper batchMapper,
                                   PopularityService popularityService) {
        this.statsMapper = statsMapper;
        this.playerRoundMapper = playerRoundMapper;
        this.batchMapper = batchMapper;
        this.popularityService = popularityService;
    }

    /**
     * 将团队池当前全部余额分配给该团队本轮成员。
     *
     * @param teamId        团队 ID
     * @param roundId       轮次 ID
     * @param method        分配方式：equal 或 custom
     * @param customWeights 自定义权重，custom 模式必填
     * @param operatorId    操作人 ID
     * @param reason        分配原因
     * @return 团队分配结果
     */
    @Transactional
    public DistributionResult distribute(int teamId,
                                         int roundId,
                                         String method,
                                         Map<Integer, Integer> customWeights,
                                         String operatorId,
                                         String reason) {
        validateBaseRequest(teamId, roundId, method, operatorId, reason);
        String normalizedMethod = method.trim().toLowerCase();
        List<Integer> members = playerRoundMapper.findPlayerIdsByTeam(teamId, roundId);
        if (members.isEmpty()) {
            throw new IllegalStateException("团队无成员");
        }

        long totalValue = valueOrZero(statsMapper.findTeamPopularity(teamId, roundId));
        if (totalValue <= 0) {
            throw new IllegalStateException("团队池无可分配人气");
        }

        Map<Integer, Long> shares = calculateShares(normalizedMethod, totalValue, members, customWeights);
        TeamDistributionBatchEntity batch = buildBatch(teamId, roundId, totalValue, normalizedMethod, customWeights, operatorId, reason);
        batchMapper.insert(batch);
        if (batch.getBatchId() == null) {
            throw new IllegalStateException("团队分配批次创建后未返回batchId");
        }

        int updatedRows = statsMapper.distributeTeamPopularity(teamId, roundId, totalValue);
        if (updatedRows != 1) {
            throw new IllegalStateException("团队池余额不足或已被并发分配");
        }

        for (Map.Entry<Integer, Long> entry : shares.entrySet()) {
            if (entry.getValue() > 0) {
                popularityService.applyChange(buildDistributionRequest(entry.getKey(), roundId, entry.getValue(),
                        batch.getBatchId(), operatorId, reason));
            }
        }
        return DistributionResult.success(batch.getBatchId(), teamId, roundId, totalValue, normalizedMethod, shares);
    }

    private void validateBaseRequest(int teamId, int roundId, String method, String operatorId, String reason) {
        if (teamId <= 0) {
            throw new IllegalArgumentException("teamId必须为正数");
        }
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        if (!StringUtils.hasText(method)) {
            throw new IllegalArgumentException("method不能为空");
        }
        String normalizedMethod = method.trim().toLowerCase();
        if (!METHOD_EQUAL.equals(normalizedMethod) && !METHOD_CUSTOM.equals(normalizedMethod)) {
            throw new IllegalArgumentException("未知method: " + method);
        }
        if (!StringUtils.hasText(operatorId)) {
            throw new IllegalArgumentException("operatorId不能为空");
        }
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("reason不能为空");
        }
    }

    private Map<Integer, Long> calculateShares(String method,
                                               long totalValue,
                                               List<Integer> members,
                                               Map<Integer, Integer> customWeights) {
        if (METHOD_EQUAL.equals(method)) {
            return calculateEqualShares(totalValue, members);
        }
        return calculateCustomShares(totalValue, members, customWeights);
    }

    private Map<Integer, Long> calculateEqualShares(long totalValue, List<Integer> members) {
        Map<Integer, Long> shares = new LinkedHashMap<>();
        long base = totalValue / members.size();
        long remainder = totalValue % members.size();
        for (int i = 0; i < members.size(); i++) {
            long share = base + (i < remainder ? 1 : 0);
            shares.put(members.get(i), share);
        }
        return shares;
    }

    private Map<Integer, Long> calculateCustomShares(long totalValue,
                                                     List<Integer> members,
                                                     Map<Integer, Integer> customWeights) {
        if (customWeights == null || customWeights.isEmpty()) {
            throw new IllegalArgumentException("custom模式customWeights不能为空");
        }
        List<Integer> sortedMembers = members.stream().sorted().toList();
        if (!customWeights.keySet().stream().allMatch(sortedMembers::contains)) {
            throw new IllegalArgumentException("customWeights不能包含非团队成员");
        }
        for (Integer member : sortedMembers) {
            Integer weight = customWeights.get(member);
            if (weight == null || weight <= 0) {
                throw new IllegalArgumentException("custom模式每个团队成员都必须有正权重");
            }
        }
        long weightSum = customWeights.values().stream().filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        if (weightSum <= 0) {
            throw new IllegalArgumentException("customWeights权重总和必须为正数");
        }

        Map<Integer, Long> shares = new LinkedHashMap<>();
        long allocated = 0L;
        for (Integer member : sortedMembers) {
            long share = totalValue * customWeights.get(member) / weightSum;
            shares.put(member, share);
            allocated += share;
        }
        long remainder = totalValue - allocated;
        for (int i = 0; i < remainder; i++) {
            Integer member = sortedMembers.get(i % sortedMembers.size());
            shares.put(member, shares.get(member) + 1);
        }
        return shares;
    }

    private TeamDistributionBatchEntity buildBatch(int teamId,
                                                   int roundId,
                                                   long totalValue,
                                                   String method,
                                                   Map<Integer, Integer> customWeights,
                                                   String operatorId,
                                                   String reason) {
        TeamDistributionBatchEntity batch = new TeamDistributionBatchEntity();
        batch.setTeamId(teamId);
        batch.setRoundId(roundId);
        batch.setTotalValue(totalValue);
        batch.setMethod(method);
        batch.setCustomWeights(METHOD_CUSTOM.equals(method) ? serializeWeights(customWeights) : null);
        batch.setOperatorId(operatorId);
        batch.setReason(reason);
        return batch;
    }

    private String serializeWeights(Map<Integer, Integer> customWeights) {
        if (customWeights == null || customWeights.isEmpty()) {
            return null;
        }
        return customWeights.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private PopularityChangeRequest buildDistributionRequest(int playerId,
                                                             int roundId,
                                                             long share,
                                                             long batchId,
                                                             String operatorId,
                                                             String reason) {
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setTargetType(TARGET_PLAYER);
        req.setTargetId(playerId);
        req.setSource(SOURCE_TEAM_DISTRIBUTION);
        req.setRawValue(share);
        req.setRoundId(roundId);
        req.setDistributionBatchId(batchId);
        req.setIdempotencyKey("teamdist_" + batchId + "_" + playerId);
        req.setOperatorId(operatorId);
        req.setReason(reason);
        req.setOccurredAt(LocalDateTime.now());
        return req;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
