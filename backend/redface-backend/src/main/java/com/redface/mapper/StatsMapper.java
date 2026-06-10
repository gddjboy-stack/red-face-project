package com.redface.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 各类人气统计表 Mapper。C2 仅实现 player 直接归属所需的 player_round_stats 操作。
 */
@Mapper
public interface StatsMapper {

    /**
     * 确保选手轮次统计行存在。若已存在，则保持原值不变。
     *
     * @param playerId 选手 ID
     * @param roundId  轮次 ID
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity, coefficient)
            VALUES (#{playerId}, #{roundId}, 0, 0, 100)
            ON DUPLICATE KEY UPDATE player_id = player_id
            """)
    int ensurePlayerRoundStats(@Param("playerId") int playerId, @Param("roundId") int roundId);

    /**
     * 按累加方式更新选手个人人气值。禁止先 SELECT 再 SET。
     *
     * @param playerId        选手 ID
     * @param roundId         轮次 ID
     * @param popularityValue 本次增加的人气值
     * @return 受影响行数
     */
    @Update("""
            UPDATE player_round_stats
            SET individual_popularity = individual_popularity + #{popularityValue}
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    int incrementPlayerIndividualPopularity(@Param("playerId") int playerId,
                                            @Param("roundId") int roundId,
                                            @Param("popularityValue") long popularityValue);

    /**
     * 查询指定选手轮次的个人人气值。
     *
     * @param playerId 选手 ID
     * @param roundId  轮次 ID
     * @return 当前个人人气值
     */
    @Select("""
            SELECT COALESCE(individual_popularity, 0)
            FROM player_round_stats
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    Long findPlayerIndividualPopularity(@Param("playerId") int playerId, @Param("roundId") int roundId);
}
