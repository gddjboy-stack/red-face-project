package com.redface.mapper;

import com.redface.dto.PlayerDetailResponse;
import com.redface.dto.PlayerListItem;
import com.redface.dto.PlayerPhotoItem;
import com.redface.query.RoundSummary;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * C15 用户端选手页只读查询 Mapper。禁止返回任何卧底身份字段。
 */
@Mapper
public interface PlayerQueryMapper {

    @Select("""
            SELECT round_id AS roundId,
                   name AS roundName
            FROM rounds
            WHERE status = 'active'
            ORDER BY round_id DESC
            LIMIT 1
            """)
    RoundSummary findLatestActiveRound();

    @Select("""
            SELECT name
            FROM rounds
            WHERE round_id = #{roundId}
            """)
    String findRoundName(@Param("roundId") Integer roundId);

    @Select("""
            SELECT p.player_id AS playerId,
                   p.number AS number,
                   p.name AS name,
                   pr.team_id AS teamId,
                   t.name AS teamName,
                   COALESCE(prs.individual_popularity, 0) AS popularityValue,
                   (
                     SELECT pa.preview_url
                     FROM photo_assets pa
                     WHERE pa.player_id = p.player_id
                       AND pa.status = 'active'
                     ORDER BY pa.is_cover DESC, pa.sort_order ASC, pa.created_at DESC, pa.asset_id DESC
                     LIMIT 1
                   ) AS photoPreviewUrl
            FROM players p
            LEFT JOIN player_round pr ON pr.player_id = p.player_id AND pr.round_id = #{roundId}
            LEFT JOIN teams t ON t.team_id = pr.team_id
            LEFT JOIN player_round_stats prs ON prs.player_id = p.player_id AND prs.round_id = #{roundId}
            WHERE p.status = 'active'
            ORDER BY p.number ASC
            """)
    List<PlayerListItem> findPlayers(@Param("roundId") Integer roundId);

    @Select("""
            SELECT p.player_id AS playerId,
                   p.number AS number,
                   p.name AS name,
                   pr.team_id AS teamId,
                   t.name AS teamName,
                   #{roundId} AS roundId,
                   r.name AS roundName,
                   COALESCE(prs.individual_popularity, 0) AS popularityValue
            FROM players p
            LEFT JOIN rounds r ON r.round_id = #{roundId}
            LEFT JOIN player_round pr ON pr.player_id = p.player_id AND pr.round_id = #{roundId}
            LEFT JOIN teams t ON t.team_id = pr.team_id
            LEFT JOIN player_round_stats prs ON prs.player_id = p.player_id AND prs.round_id = #{roundId}
            WHERE p.player_id = #{playerId}
              AND p.status = 'active'
            """)
    PlayerDetailResponse findPlayerDetail(@Param("playerId") int playerId,
                                          @Param("roundId") Integer roundId);

    @Select("""
            SELECT asset_id AS assetId,
                   preview_url AS previewUrl
            FROM photo_assets
            WHERE player_id = #{playerId}
              AND status = 'active'
            ORDER BY is_cover DESC, sort_order ASC, created_at DESC, asset_id DESC
            """)
    List<PlayerPhotoItem> findPhotosByPlayer(@Param("playerId") int playerId);
}
