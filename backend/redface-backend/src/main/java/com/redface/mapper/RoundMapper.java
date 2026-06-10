package com.redface.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * rounds 表 Mapper，用于查询核销/入账应归属的轮次。
 */
@Mapper
public interface RoundMapper {

    /**
     * 查询当前激活轮次。若存在多条 active，取 round_id 最大的一条。
     *
     * @return 当前激活轮次 ID；不存在时返回 null
     */
    @Select("""
            SELECT round_id
            FROM rounds
            WHERE status = 'active'
            ORDER BY round_id DESC
            LIMIT 1
            """)
    Integer findLatestActiveRoundId();

    /**
     * 查询下一条即将开始的轮次。若存在多条 upcoming，取 round_id 最小的一条。
     *
     * @return 下一条即将开始的轮次 ID；不存在时返回 null
     */
    @Select("""
            SELECT round_id
            FROM rounds
            WHERE status = 'upcoming'
            ORDER BY round_id ASC
            LIMIT 1
            """)
    Integer findEarliestUpcomingRoundId();
}
