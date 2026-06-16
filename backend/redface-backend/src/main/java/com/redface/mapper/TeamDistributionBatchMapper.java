package com.redface.mapper;

import com.redface.entity.TeamDistributionBatchEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * team_distribution_batches 表 Mapper。用于记录团队池分配批次元数据。
 */
@Mapper
public interface TeamDistributionBatchMapper {

    /**
     * 插入团队分配批次，并回填自增 batchId。
     *
     * @param batch 团队分配批次实体
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO team_distribution_batches (
              team_id,
              round_id,
              total_value,
              method,
              custom_weights,
              operator_id,
              reason
            ) VALUES (
              #{teamId},
              #{roundId},
              #{totalValue},
              #{method},
              #{customWeights},
              #{operatorId},
              #{reason}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "batchId", keyColumn = "batch_id")
    int insert(TeamDistributionBatchEntity batch);

    /**
     * 查询指定批次数量，用于测试验证。
     *
     * @param batchId 批次 ID
     * @return 批次数量
     */
    @Select("SELECT COUNT(*) FROM team_distribution_batches WHERE batch_id = #{batchId}")
    long countByBatchId(@Param("batchId") long batchId);
}
