package com.redface.mapper;

import com.redface.dto.BasicDataRequests;
import com.redface.dto.BasicDataViews;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * C19 基础数据管理 Mapper。写入白名单仅限 players、teams、rounds、player_round 四张静态表。
 */
@Mapper
public interface BasicDataMapper {
    @Select("""
            SELECT player_id AS playerId, name, number, display_code AS displayCode, status, created_at AS createdAt, updated_at AS updatedAt
            FROM players
            ORDER BY number ASC, player_id ASC
            """)
    List<BasicDataViews.PlayerView> findPlayers();

    @Insert("""
            INSERT INTO players (name, number, display_code, status)
            VALUES (#{name}, #{number}, #{displayCode}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "playerId", keyColumn = "player_id")
    int insertPlayer(BasicDataRequests.CreatePlayerRequest request);

    /** C20-12：取当前最大序号，用于自动生成下一个序号。表为空时返回 null。 */
    @Select("SELECT MAX(number) FROM players")
    Integer findMaxPlayerNumber();

    /** C20-12：统计某编号是否已被占用，用于区分"序号冲突"与"编号冲突"。 */
    @Select("SELECT COUNT(*) FROM players WHERE display_code = #{displayCode}")
    int countPlayersByDisplayCode(@Param("displayCode") String displayCode);

    @Select("""
            SELECT player_id AS playerId, name, number, display_code AS displayCode, status, created_at AS createdAt, updated_at AS updatedAt
            FROM players
            WHERE player_id = #{playerId}
            """)
    BasicDataViews.PlayerView findPlayerById(@Param("playerId") int playerId);

    @Select("""
            SELECT team_id AS teamId, name, created_at AS createdAt
            FROM teams
            ORDER BY team_id ASC
            """)
    List<BasicDataViews.TeamView> findTeams();

    @Insert("""
            INSERT INTO teams (name)
            VALUES (#{name})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "teamId", keyColumn = "team_id")
    int insertTeam(BasicDataRequests.CreateTeamRequest request);

    @Select("""
            SELECT team_id AS teamId, name, created_at AS createdAt
            FROM teams
            WHERE team_id = #{teamId}
            """)
    BasicDataViews.TeamView findTeamById(@Param("teamId") int teamId);

    @Select("""
            SELECT round_id AS roundId, name, start_time AS startTime, end_time AS endTime, status
            FROM rounds
            ORDER BY start_time ASC, round_id ASC
            """)
    List<BasicDataViews.RoundView> findRounds();

    @Select("""
            SELECT round_id AS roundId, name, start_time AS startTime, end_time AS endTime, status
            FROM rounds
            WHERE status = 'active'
            ORDER BY round_id ASC
            """)
    List<BasicDataViews.RoundView> findActiveRounds();

    @Insert("""
            INSERT INTO rounds (name, start_time, end_time, status)
            VALUES (#{name}, #{startTime}, #{endTime}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "roundId", keyColumn = "round_id")
    int insertRound(BasicDataRequests.CreateRoundRequest request);

    @Select("""
            SELECT round_id AS roundId, name, start_time AS startTime, end_time AS endTime, status
            FROM rounds
            WHERE round_id = #{roundId}
            """)
    BasicDataViews.RoundView findRoundById(@Param("roundId") int roundId);

    @Update("""
            UPDATE rounds
            SET status = 'completed'
            WHERE status = 'active'
            AND round_id <> #{roundId}
            """)
    int completeOtherActiveRounds(@Param("roundId") int roundId);

    @Update("""
            UPDATE rounds
            SET status = #{status}
            WHERE round_id = #{roundId}
            """)
    int updateRoundStatus(@Param("roundId") int roundId, @Param("status") String status);

    @Insert("""
            INSERT INTO player_round (player_id, round_id, team_id, is_spy, player_status)
            VALUES (#{playerId}, #{roundId}, #{teamId}, #{isSpy}, #{playerStatus})
            ON DUPLICATE KEY UPDATE
                team_id = #{teamId},
                is_spy = #{isSpy},
                player_status = #{playerStatus}
            """)
    int upsertPlayerRound(BasicDataRequests.PlayerRoundRequest request);

    @Select("""
            SELECT p.player_id AS playerId,
                   p.number AS number,
                   p.display_code AS displayCode,
                   p.name AS playerName,
                   r.round_id AS roundId,
                   r.name AS roundName,
                   pr.team_id AS teamId,
                   t.name AS teamName,
                   COALESCE(pr.is_spy, 0) AS isSpy,
                   COALESCE(pr.player_status, 'normal') AS playerStatus
            FROM players p
            CROSS JOIN rounds r
            LEFT JOIN player_round pr ON pr.player_id = p.player_id AND pr.round_id = r.round_id
            LEFT JOIN teams t ON t.team_id = pr.team_id
            WHERE r.round_id = #{roundId}
            ORDER BY p.number ASC, p.player_id ASC
            """)
    List<BasicDataViews.PlayerRoundView> findPlayerRounds(@Param("roundId") int roundId);
}
