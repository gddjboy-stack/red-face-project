package com.redface.mapper;

import com.redface.entity.CoefficientLedgerEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * coefficient_ledger 表 Mapper。用于记录加成系数变更流水并提供幂等校验。
 */
@Mapper
public interface CoefficientLedgerMapper {

    /**
     * 插入一条系数变更流水。幂等性由 idempotency_key 唯一索引保证。
     *
     * @param ledger 系数流水实体
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO coefficient_ledger (
              player_id,
              round_id,
              task_id,
              task_type,
              delta,
              idempotency_key,
              operator_id,
              reason
            ) VALUES (
              #{playerId},
              #{roundId},
              #{taskId},
              #{taskType},
              #{delta},
              #{idempotencyKey},
              #{operatorId},
              #{reason}
            )
            """)
    int insert(CoefficientLedgerEntity ledger);

    /**
     * 统计指定幂等键的系数流水数量。
     *
     * @param idempotencyKey 幂等键
     * @return 匹配流水数量
     */
    @Select("SELECT COUNT(*) FROM coefficient_ledger WHERE idempotency_key = #{idempotencyKey}")
    long countByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
