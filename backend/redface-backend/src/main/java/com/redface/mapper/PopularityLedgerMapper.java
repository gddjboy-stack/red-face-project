package com.redface.mapper;

import com.redface.dto.GroupVoteSummaryItem;
import com.redface.entity.PopularityLedgerEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * popularity_ledger 表 Mapper。全项目仅允许通过 PopularityService 间接调用写入流水。
 */
@Mapper
public interface PopularityLedgerMapper {

    /**
     * 插入一条人气值流水。幂等性由 idempotency_key 唯一索引保证。
     *
     * @param ledger 人气值流水实体
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO popularity_ledger (
              target_type,
              target_id,
              source,
              raw_value,
              popularity_value,
              round_id,
              idempotency_key,
              distribution_batch_id,
              operator_id,
              reason,
              metadata,
              occurred_at
            ) VALUES (
              #{targetType},
              #{targetId},
              #{source},
              #{rawValue},
              #{popularityValue},
              #{roundId},
              #{idempotencyKey},
              #{distributionBatchId},
              #{operatorId},
              #{reason},
              #{metadata},
              #{occurredAt}
            )
            """)
    int insert(PopularityLedgerEntity ledger);

    /**
     * 统计指定幂等键的流水数量，用于测试与审计校验。
     *
     * @param idempotencyKey 幂等键
     * @return 匹配流水数量
     */
    @Select("SELECT COUNT(*) FROM popularity_ledger WHERE idempotency_key = #{idempotencyKey}")
    long countByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 按幂等键反查该笔原始流水所归属的轮次，供退款回滚精确扣回原核销轮次使用（只读）。
     *
     * <p>C14 退款专用：退款必须把人气扣回核销当时记账的那一轮，避免跨轮退款扣错轮次导致账面对不上。
     *
     * @param idempotencyKey 原始入账流水的幂等键，例如 token_RFZJ-XXXX-XXXX-XXXX
     * @return 该流水的 round_id；不存在或为空时返回 null
     */
    @Select("SELECT round_id FROM popularity_ledger WHERE idempotency_key = #{idempotencyKey}")
    Integer findRoundIdByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 汇总指定目标在指定轮次下的流水人气值，用于测试与审计校验。
     *
     * @param targetType 目标类型
     * @param targetId   目标 ID
     * @param roundId    轮次 ID
     * @return 人气值流水总和
     */
    @Select("""
            SELECT COALESCE(SUM(popularity_value), 0)
            FROM popularity_ledger
            WHERE target_type = #{targetType}
              AND target_id = #{targetId}
              AND round_id = #{roundId}
            """)
    long sumPopularityValue(@Param("targetType") String targetType,
                            @Param("targetId") int targetId,
                            @Param("roundId") int roundId);

    /**
     * C20-3: 汇总指定轮次下指定来源的各选手累计票数（冲销后净值）。
     *
     * <p>以 raw_value 为准（group_vote 源 1票=1，raw_value 即票数），左连 players 补选手姓名与序号。
     *
     * @param roundId 轮次 ID
     * @param source  流水来源（如 group_vote）
     * @return 各选手累计票数列表，按选手序号升序
     */
    @Select("""
            SELECT l.target_id AS playerId,
                   p.name AS playerName,
                   p.number AS playerNumber,
                   COALESCE(SUM(l.raw_value), 0) AS totalVotes,
                   COUNT(*) AS entryCount
            FROM popularity_ledger l
            LEFT JOIN players p ON p.player_id = l.target_id
            WHERE l.round_id = #{roundId}
              AND l.source = #{source}
            GROUP BY l.target_id, p.name, p.number
            ORDER BY p.number ASC
            """)
    List<GroupVoteSummaryItem> summarizeBySource(@Param("roundId") int roundId,
                                                 @Param("source") String source);
}
