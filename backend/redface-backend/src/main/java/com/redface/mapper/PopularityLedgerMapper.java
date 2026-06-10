package com.redface.mapper;

import com.redface.entity.PopularityLedgerEntity;
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
}
