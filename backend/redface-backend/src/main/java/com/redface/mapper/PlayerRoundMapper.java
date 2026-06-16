package com.redface.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * player_round 表 Mapper。用于查询指定团队在指定轮次的成员。
 */
@Mapper
public interface PlayerRoundMapper {

    /**
     * 查询指定团队和轮次下的成员 ID，按 player_id 升序返回。
     *
     * @param teamId  团队 ID
     * @param roundId 轮次 ID
     * @return 成员 ID 列表
     */
    @Select("""
            SELECT player_id
            FROM player_round
            WHERE team_id = #{teamId}
              AND round_id = #{roundId}
            ORDER BY player_id ASC
            """)
    List<Integer> findPlayerIdsByTeam(@Param("teamId") int teamId, @Param("roundId") int roundId);
}
