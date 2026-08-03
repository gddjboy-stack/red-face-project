package com.redface.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * C20-10：查询本轮投票参与人数。
     *
     * <p><b>返回 null 与返回 0 含义不同，调用方不得用 COALESCE 抹平：</b>
     * null 表示「还没录」，0 表示「确实一个人都没投」。若在 SQL 里把 null 折成 0，
     * 得票占比会变成除零，且「未录入」的提示永远不会出现——场控看到 0% 会以为
     * 是真实数据，而不是提醒自己漏录。
     *
     * @return 参与人数；未录入时返回 null；轮次不存在时也返回 null
     */
    @Select("""
            SELECT voter_count
            FROM rounds
            WHERE round_id = #{roundId}
            """)
    Integer findVoterCount(@Param("roundId") int roundId);

    /**
     * C20-10：写入/覆盖本轮投票参与人数。
     *
     * <p>本方法只做写入，不做任何校验。「参与人数不得小于任一选手得票数」的
     * 校验必须在服务层完成，因为被拒时要向场控回报是哪位选手、得票多少，
     * Mapper 层拿不到这些信息。
     *
     * @return 受影响行数；0 表示轮次不存在
     */
    @Update("""
            UPDATE rounds
            SET voter_count = #{voterCount}
            WHERE round_id = #{roundId}
            """)
    int updateVoterCount(@Param("roundId") int roundId, @Param("voterCount") Integer voterCount);
}
