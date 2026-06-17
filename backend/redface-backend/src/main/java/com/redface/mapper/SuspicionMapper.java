package com.redface.mapper;

import com.redface.dto.SuspicionCandidateView;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * C13 真相识破 Mapper。用户侧查询严禁暴露 is_spy 或真实卧底身份。
 */
@Mapper
public interface SuspicionMapper {

    @Select("""
            SELECT p.player_id AS playerId,
                   p.number AS number,
                   p.name AS playerName,
                   pr.team_id AS teamId,
                   t.name AS teamName,
                   COUNT(sv.vote_id) AS count
            FROM player_round pr
            JOIN players p ON p.player_id = pr.player_id
            LEFT JOIN teams t ON t.team_id = pr.team_id
            LEFT JOIN suspicion_votes sv ON sv.round_id = pr.round_id
              AND sv.suspect_player_id = pr.player_id
            WHERE pr.round_id = #{roundId}
              AND pr.team_id IS NOT NULL
              AND COALESCE(pr.player_status, 'normal') IN ('normal', 'free')
            GROUP BY p.player_id, p.number, p.name, pr.team_id, t.name
            ORDER BY p.number ASC, p.player_id ASC
            """)
    List<SuspicionCandidateView> findCandidatesWithCounts(@Param("roundId") int roundId);

    @Select("""
            SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END
            FROM player_round pr
            WHERE pr.round_id = #{roundId}
              AND pr.player_id = #{playerId}
              AND COALESCE(pr.player_status, 'normal') IN ('normal', 'free')
            """)
    boolean existsCandidate(@Param("roundId") int roundId, @Param("playerId") int playerId);

    @Select("""
            SELECT suspect_player_id
            FROM suspicion_votes
            WHERE user_id = #{userId}
              AND round_id = #{roundId}
            ORDER BY voted_at ASC, vote_id ASC
            LIMIT 1
            """)
    Integer findSubmittedPlayerId(@Param("userId") String userId, @Param("roundId") int roundId);

    @Select("""
            SELECT COUNT(1)
            FROM suspicion_votes
            WHERE round_id = #{roundId}
            """)
    long countByRound(@Param("roundId") int roundId);

    @Insert("""
            INSERT INTO suspicion_votes (user_id, round_id, team_id, suspect_player_id, voted_at)
            SELECT #{userId}, #{roundId}, pr.team_id, #{suspectPlayerId}, CURRENT_TIMESTAMP
            FROM player_round pr
            WHERE pr.round_id = #{roundId}
              AND pr.player_id = #{suspectPlayerId}
              AND COALESCE(pr.player_status, 'normal') IN ('normal', 'free')
            """)
    int insertSubmission(@Param("userId") String userId,
                         @Param("roundId") int roundId,
                         @Param("suspectPlayerId") int suspectPlayerId);
}
