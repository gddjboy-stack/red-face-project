package com.redface.mapper;

import com.redface.dto.MyPhotoItem;
import com.redface.dto.PopularityBoardItem;
import com.redface.dto.RedeemResponse;
import com.redface.query.PlayerDisplayRow;
import com.redface.query.RoundSummary;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * C9 页面级只读查询 Mapper。仅用于 Controller/QueryService 组装 DTO，不做写操作。
 */
@Mapper
public interface C9QueryMapper {

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
            SELECT p.player_id AS playerId,
                   p.number AS number,
                   p.name AS name,
                   pr.team_id AS teamId,
                   t.name AS teamName, pr.is_spy AS isSpy
            FROM players p
            LEFT JOIN player_round pr ON pr.player_id = p.player_id AND pr.round_id = #{roundId}
            LEFT JOIN teams t ON t.team_id = pr.team_id
            WHERE p.player_id = #{playerId}
            """)
    PlayerDisplayRow findPlayerDisplay(@Param("playerId") int playerId, @Param("roundId") int roundId);

    @Select("SELECT name FROM teams WHERE team_id = #{teamId}")
    String findTeamName(@Param("teamId") int teamId);

    /**
     * 选手榜。与团队榜同一范式：账本存裸人气值，读取时乘上 {@code coefficient} 折算。
     * {@code COALESCE(prs.coefficient, 100)} 保证 LEFT JOIN 未命中（无 stats 行）时按 1.0 处理，
     * 否则未建 stats 行的选手人气会被误归零。
     */
    @Select("""
            SELECT p.number AS number,
                   p.name AS name,
                   t.name AS teamName, pr.is_spy AS isSpy,
                   CAST(COALESCE(prs.individual_popularity, 0) * COALESCE(prs.coefficient, 100) / 100 AS SIGNED) AS popularityValue
            FROM player_round pr
            JOIN players p ON p.player_id = pr.player_id
            LEFT JOIN teams t ON t.team_id = pr.team_id
            LEFT JOIN player_round_stats prs ON prs.player_id = pr.player_id AND prs.round_id = pr.round_id
            WHERE pr.round_id = #{roundId}
            ORDER BY p.number ASC
            """)
    List<PopularityBoardItem> findPlayerBoard(@Param("roundId") int roundId);

    @Select("""
            SELECT t.team_id AS number,
                   t.name AS name,
                   t.name AS teamName,
                   CAST(COALESCE(trs.team_popularity, 0) * COALESCE(trs.coefficient, 100) / 100 AS SIGNED) AS popularityValue
            FROM teams t
            LEFT JOIN team_round_stats trs ON trs.team_id = t.team_id AND trs.round_id = #{roundId}
            WHERE EXISTS (
              SELECT 1 FROM player_round pr WHERE pr.team_id = t.team_id AND pr.round_id = #{roundId}
            )
            ORDER BY t.team_id ASC
            """)
    List<PopularityBoardItem> findTeamBoard(@Param("roundId") int roundId);

    /**
     * 卧底榜。C20-10 起在读取时乘上 {@code spy_coefficient} 折算，
     * 与 {@link #findTeamBoard} 的团队系数折算保持同一范式（账本存裸值，读取时折算）。
     *
     * <p>必须保留 {@code AS popularityValue} 别名：{@code PopularityBoardItem} 靠
     * {@code setPopularityValue(long)} 这个额外 setter 接住它，而前端表格读的是
     * {@code prop="value"}。改则前端那一列会静默变空，不报错。
     *
     * <p>{@code COALESCE(prs.spy_coefficient, 100)} 的兼容作用：LEFT JOIN 未命中时
     * 整行为 NULL，此时应视为系数 1.0 而非 0，否则未建 stats 行的选手卧底人气会被归零。
     */
    @Select("""
            SELECT p.number AS number,
                   p.name AS name,
                   t.name AS teamName, pr.is_spy AS isSpy,
                   CAST(COALESCE(prs.spy_popularity, 0) * COALESCE(prs.spy_coefficient, 100) / 100 AS SIGNED) AS popularityValue
            FROM player_round pr
            JOIN players p ON p.player_id = pr.player_id
            LEFT JOIN teams t ON t.team_id = pr.team_id
            LEFT JOIN player_round_stats prs ON prs.player_id = pr.player_id AND prs.round_id = pr.round_id
            WHERE pr.round_id = #{roundId}
            ORDER BY p.number ASC
            """)
    List<PopularityBoardItem> findSpyBoard(@Param("roundId") int roundId);

    @Select("""
            SELECT p.number AS playerNumber,
                   p.name AS playerName,
                   t.name AS teamName, pr.is_spy AS isSpy,
                   tk.points AS points,
                   tk.photo_asset_id AS photoAssetId,
                   pa.preview_url AS photoPreviewUrl,
                   CASE WHEN upc.id IS NULL THEN FALSE ELSE TRUE END AS collected
            FROM tokens tk
            JOIN players p ON p.player_id = tk.player_id
            LEFT JOIN photo_assets pa ON pa.asset_id = tk.photo_asset_id
            LEFT JOIN player_round pr ON pr.player_id = tk.player_id
              AND pr.round_id = (
                SELECT MAX(pr2.round_id) FROM player_round pr2 WHERE pr2.player_id = tk.player_id
              )
            LEFT JOIN teams t ON t.team_id = pr.team_id
            LEFT JOIN user_photo_collection upc ON upc.user_id = #{userId} AND upc.token_id = tk.token_id
            WHERE tk.token_id = #{tokenId}
            """)
    RedeemResponse findRedeemResponse(@Param("tokenId") String tokenId, @Param("userId") String userId);

    @Select("""
            SELECT pa.asset_id AS assetId,
                   pa.preview_url AS previewUrl,
                   p.name AS playerName,
                   upc.created_at AS createdAt
            FROM user_photo_collection upc
            JOIN photo_assets pa ON pa.asset_id = upc.asset_id
            JOIN players p ON p.player_id = pa.player_id
            WHERE upc.user_id = #{userId}
            ORDER BY upc.created_at DESC, upc.id DESC
            """)
    List<MyPhotoItem> findPhotosByUser(@Param("userId") String userId);
}
