package com.redface.mapper;

import com.redface.entity.CollectState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * collect_state 单行表 Mapper，用于维护当前场控集赞归属。
 */
@Mapper
public interface CollectStateMapper {

    /**
     * 写入或更新当前场控状态。collect_state 为单行表，固定 id=1。
     *
     * @param mode       场控模式，允许 player/team/spy/pool
     * @param targetId   目标 ID，pool 模式可为空
     * @param roundId    当前轮次 ID
     * @param operatorId 操作人 ID
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO collect_state (id, mode, target_id, round_id, updated_by)
            VALUES (1, #{mode}, #{targetId}, #{roundId}, #{operatorId})
            ON DUPLICATE KEY UPDATE
              mode = VALUES(mode),
              target_id = VALUES(target_id),
              round_id = VALUES(round_id),
              updated_by = VALUES(updated_by)
            """)
    int upsert(@Param("mode") String mode,
               @Param("targetId") Integer targetId,
               @Param("roundId") Integer roundId,
               @Param("operatorId") String operatorId);

    /**
     * 读取当前场控状态。
     *
     * @return 当前场控状态；若未设置则返回 null
     */
    @Select("""
            SELECT id, mode, target_id AS targetId, round_id AS roundId, updated_by AS updatedBy, updated_at AS updatedAt
            FROM collect_state
            WHERE id = 1
            """)
    CollectState findCurrent();
}
