package com.redface.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 各类人气统计表 Mapper。统计更新必须使用 UPDATE ... SET x = x + ? 的累加写法。
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
     * 按累加方式更新选手卧底人气值。
     *
     * @param playerId        选手 ID
     * @param roundId         轮次 ID
     * @param popularityValue 本次增加的人气值
     * @return 受影响行数
     */
    @Update("""
            UPDATE player_round_stats
            SET spy_popularity = spy_popularity + #{popularityValue}
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    int incrementPlayerSpyPopularity(@Param("playerId") int playerId,
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

    /**
     * 查询指定选手轮次的卧底人气值。
     *
     * @param playerId 选手 ID
     * @param roundId  轮次 ID
     * @return 当前卧底人气值
     */
    @Select("""
            SELECT COALESCE(spy_popularity, 0)
            FROM player_round_stats
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    Long findPlayerSpyPopularity(@Param("playerId") int playerId, @Param("roundId") int roundId);

    /**
     * 确保团队轮次统计行存在。若已存在，则保持原值不变。
     *
     * @param teamId  团队 ID
     * @param roundId 轮次 ID
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO team_round_stats (team_id, round_id, team_popularity, distributed_popularity)
            VALUES (#{teamId}, #{roundId}, 0, 0)
            ON DUPLICATE KEY UPDATE team_id = team_id
            """)
    int ensureTeamRoundStats(@Param("teamId") int teamId, @Param("roundId") int roundId);

    /**
     * 按累加方式更新团队池人气值。
     *
     * @param teamId          团队 ID
     * @param roundId         轮次 ID
     * @param popularityValue 本次增加的人气值
     * @return 受影响行数
     */
    @Update("""
            UPDATE team_round_stats
            SET team_popularity = team_popularity + #{popularityValue}
            WHERE team_id = #{teamId}
              AND round_id = #{roundId}
            """)
    int incrementTeamPopularity(@Param("teamId") int teamId,
                                @Param("roundId") int roundId,
                                @Param("popularityValue") long popularityValue);

    /**
     * 查询指定团队轮次的人气池数值。
     *
     * @param teamId  团队 ID
     * @param roundId 轮次 ID
     * @return 当前团队池人气值
     */
    @Select("""
            SELECT COALESCE(team_popularity, 0)
            FROM team_round_stats
            WHERE team_id = #{teamId}
              AND round_id = #{roundId}
            """)
    Long findTeamPopularity(@Param("teamId") int teamId, @Param("roundId") int roundId);

    /**
     * 确保赛事总池轮次统计行存在。若已存在，则保持原值不变。
     *
     * @param roundId 轮次 ID
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO pool_round_stats (round_id, pool_popularity)
            VALUES (#{roundId}, 0)
            ON DUPLICATE KEY UPDATE round_id = round_id
            """)
    int ensurePoolRoundStats(@Param("roundId") int roundId);

    /**
     * 按累加方式更新赛事总池人气值。
     *
     * @param roundId         轮次 ID
     * @param popularityValue 本次增加的人气值
     * @return 受影响行数
     */
    @Update("""
            UPDATE pool_round_stats
            SET pool_popularity = pool_popularity + #{popularityValue}
            WHERE round_id = #{roundId}
            """)
    int incrementPoolPopularity(@Param("roundId") int roundId, @Param("popularityValue") long popularityValue);

    /**
     * 查询指定轮次的赛事总池人气值。
     *
     * @param roundId 轮次 ID
     * @return 当前赛事总池人气值
     */
    @Select("""
            SELECT COALESCE(pool_popularity, 0)
            FROM pool_round_stats
            WHERE round_id = #{roundId}
            """)
    Long findPoolPopularity(@Param("roundId") int roundId);

    /**
     * 查询指定选手当前轮次的加成系数。
     *
     * @param playerId 选手 ID
     * @param roundId  轮次 ID
     * @return 当前加成系数；无记录时返回 null
     */
    @Select("""
            SELECT coefficient
            FROM player_round_stats
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    Integer findPlayerCoefficient(@Param("playerId") int playerId, @Param("roundId") int roundId);

    /**
     * 按累加方式更新选手加成系数。禁止先 SELECT 再 SET。
     *
     * @param playerId 选手 ID
     * @param roundId  轮次 ID
     * @param delta    系数变化量
     * @return 受影响行数
     */
    @Update("""
            UPDATE player_round_stats
            SET coefficient = coefficient + #{delta}
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    int incrementPlayerCoefficient(@Param("playerId") int playerId,
                                   @Param("roundId") int roundId,
                                   @Param("delta") int delta);

    /**
     * 原子扣减团队池并累加已分配值。必须带 team_popularity >= totalValue 防止并发超分配。
     *
     * @param teamId     团队 ID
     * @param roundId    轮次 ID
     * @param totalValue 本次分配总额
     * @return 受影响行数
     */
    @Update("""
            UPDATE team_round_stats
            SET team_popularity = team_popularity - #{totalValue},
                distributed_popularity = distributed_popularity + #{totalValue}
            WHERE team_id = #{teamId}
              AND round_id = #{roundId}
              AND team_popularity >= #{totalValue}
            """)
    int distributeTeamPopularity(@Param("teamId") int teamId,
                                 @Param("roundId") int roundId,
                                 @Param("totalValue") long totalValue);

    /**
     * 查询指定团队轮次已分配的人气值。
     *
     * @param teamId  团队 ID
     * @param roundId 轮次 ID
     * @return 已分配人气值
     */
    @Select("""
            SELECT COALESCE(distributed_popularity, 0)
            FROM team_round_stats
            WHERE team_id = #{teamId}
              AND round_id = #{roundId}
            """)
    Long findTeamDistributedPopularity(@Param("teamId") int teamId, @Param("roundId") int roundId);

    /**
     * 查询指定选手当前 round_id 之前最近一轮的个人人气值。
     *
     * @param playerId 选手 ID
     * @param roundId  当前轮次 ID
     * @return 上一轮个人人气值；无记录时返回 null
     */
    @Select("""
            SELECT individual_popularity
            FROM player_round_stats
            WHERE player_id = #{playerId}
              AND round_id < #{roundId}
            ORDER BY round_id DESC
            LIMIT 1
            """)
    Long findPreviousRoundIndividualPopularity(@Param("playerId") int playerId, @Param("roundId") int roundId);
}

