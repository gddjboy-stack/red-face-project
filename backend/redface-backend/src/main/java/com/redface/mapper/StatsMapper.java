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
            INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity, coefficient, spy_coefficient)
            VALUES (#{playerId}, #{roundId}, 0, 0, 100, 100)
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
    @org.apache.ibatis.annotations.Update("UPDATE team_round_stats SET coefficient = coefficient + #{delta} WHERE team_id = #{teamId} AND round_id = #{roundId}")
    int updateTeamCoefficient(@Param("teamId") int teamId, @Param("roundId") int roundId, @Param("delta") int delta);

    @org.apache.ibatis.annotations.Insert("INSERT INTO team_coefficient_ledger (team_id, round_id, task_id, task_type, delta, idempotency_key, operator_id, reason) VALUES (#{teamId}, #{roundId}, #{taskId}, #{taskType}, #{delta}, #{idempotencyKey}, #{operatorId}, #{reason})")
    int insertTeamCoefficientLedger(@Param("teamId") int teamId, @Param("roundId") int roundId, @Param("taskId") String taskId, @Param("taskType") String taskType, @Param("delta") int delta, @Param("idempotencyKey") String idempotencyKey, @Param("operatorId") String operatorId, @Param("reason") String reason);

    @org.apache.ibatis.annotations.Insert("INSERT INTO coefficient_ledger (player_id, round_id, task_id, task_type, delta, idempotency_key, operator_id, reason) VALUES (#{playerId}, #{roundId}, #{taskId}, #{taskType}, #{delta}, #{idempotencyKey}, #{operatorId}, #{reason})")
    int insertCoefficientLedger(@Param("playerId") int playerId, @Param("roundId") int roundId, @Param("taskId") String taskId, @Param("taskType") String taskType, @Param("delta") int delta, @Param("idempotencyKey") String idempotencyKey, @Param("operatorId") String operatorId, @Param("reason") String reason);

    @Select("""
            SELECT COALESCE(individual_popularity, 0)
            FROM player_round_stats
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    Long findPlayerIndividualPopularity(@Param("playerId") int playerId, @Param("roundId") int roundId);

    /**
     * 查询指定选手轮次的卧底人气值（C20-10 起为折算后值）。
     *
     * <p>此方法被场控监控的「目标人气」消费（LiveHomeService）。它与卧底榜
     * 必须同步折算：若只改卧底榜而此处仍返裸值，同一选手在卧底榜与场控监控
     * 会显示两个不同数字（如 133250 与 205000），运营会认为系统出错。
     *
     * <p>如需未折算的裸值（例如界面要分行展示「基础值 × 系数 = 折算后」），
     * 请用 {@link #findPlayerSpyPopularityRaw}，不要把本方法改回裸值。
     *
     * @param playerId 选手 ID
     * @param roundId  轮次 ID
     * @return 折算后的卧底人气值
     */
    @Select("""
            SELECT CAST(COALESCE(spy_popularity, 0) * COALESCE(spy_coefficient, 100) / 100 AS SIGNED)
            FROM player_round_stats
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    Long findPlayerSpyPopularity(@Param("playerId") int playerId, @Param("roundId") int roundId);

    /**
     * 查询未经系数折算的卧底人气裸值，仅用于界面分项回显与核对。
     */
    @Select("""
            SELECT COALESCE(spy_popularity, 0)
            FROM player_round_stats
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    Long findPlayerSpyPopularityRaw(@Param("playerId") int playerId, @Param("roundId") int roundId);

    /**
     * 查询选手当前卧底系数（×100 整数，100 为 1.0）。行不存在时返回 null。
     */
    @Select("""
            SELECT COALESCE(spy_coefficient, 100)
            FROM player_round_stats
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    Integer findPlayerSpyCoefficient(@Param("playerId") int playerId, @Param("roundId") int roundId);

    /**
     * 以乘法施加卧底系数因子。注意这里是乘法，不是团队版的加法累加。
     *
     * <p>举例：现有 130（×1.3），施加 factor=50（×0.5）→ 130×50/100 = 65（×0.65）。
     * 若错用加法累加会得到 130+50-100=80（×0.8），两者都不报错，这是 C20-10
     * 刻意不镜像 {@link #updateTeamCoefficient} 的原因。
     *
     * @param factor 乘数因子×100（130=×1.3，50=×0.5）
     */
    @Update("""
            UPDATE player_round_stats
            SET spy_coefficient = CAST(COALESCE(spy_coefficient, 100) * #{factor} / 100 AS SIGNED)
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    int multiplyPlayerSpyCoefficient(@Param("playerId") int playerId,
                                     @Param("roundId") int roundId,
                                     @Param("factor") int factor);

    /**
     * 将卧底系数直接重算为指定值。仅用于撤销后按剩余未撤销账本条目重建，
     * 不得用于日常施加（日常施加请用 {@link #multiplyPlayerSpyCoefficient}）。
     */
    @Update("""
            UPDATE player_round_stats
            SET spy_coefficient = #{coefficient}
            WHERE player_id = #{playerId}
              AND round_id = #{roundId}
            """)
    int resetPlayerSpyCoefficient(@Param("playerId") int playerId,
                                  @Param("roundId") int roundId,
                                  @Param("coefficient") int coefficient);

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

