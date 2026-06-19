package com.redface.mapper;

import com.redface.dto.AdminPhotoView;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * C17 后台写真资产 Mapper。
 */
@Mapper
public interface PhotoAssetMapper {

    @Select("""
            SELECT pa.asset_id AS assetId,
                   pa.player_id AS playerId,
                   p.name AS playerName,
                   p.number AS playerNumber,
                   pa.preview_url AS previewUrl,
                   pa.download_url AS downloadUrl,
                   pa.status AS status,
                   pa.is_cover AS isCover,
                   pa.sort_order AS sortOrder,
                   pa.file_name AS fileName,
                   pa.content_type AS contentType,
                   pa.file_size AS fileSize,
                   pa.created_at AS createdAt,
                   pa.updated_at AS updatedAt
            FROM photo_assets pa
            JOIN players p ON p.player_id = pa.player_id
            WHERE (#{playerId} IS NULL OR pa.player_id = #{playerId})
              AND (#{status} IS NULL OR pa.status = #{status})
            ORDER BY p.number ASC, pa.is_cover DESC, pa.sort_order ASC, pa.created_at DESC, pa.asset_id DESC
            """)
    List<AdminPhotoView> findPhotos(@Param("playerId") Integer playerId, @Param("status") String status);

    @Select("""
            SELECT pa.asset_id AS assetId,
                   pa.player_id AS playerId,
                   p.name AS playerName,
                   p.number AS playerNumber,
                   pa.preview_url AS previewUrl,
                   pa.download_url AS downloadUrl,
                   pa.status AS status,
                   pa.is_cover AS isCover,
                   pa.sort_order AS sortOrder,
                   pa.file_name AS fileName,
                   pa.content_type AS contentType,
                   pa.file_size AS fileSize,
                   pa.created_at AS createdAt,
                   pa.updated_at AS updatedAt
            FROM photo_assets pa
            JOIN players p ON p.player_id = pa.player_id
            WHERE pa.asset_id = #{assetId}
            """)
    AdminPhotoView findByAssetId(@Param("assetId") String assetId);

    @Select("""
            SELECT name
            FROM players
            WHERE player_id = #{playerId}
              AND status = 'active'
            """)
    String findActivePlayerName(@Param("playerId") int playerId);

    @Insert("""
            INSERT INTO photo_assets (asset_id, player_id, preview_url, download_url, status, is_cover, sort_order, file_name, content_type, file_size)
            VALUES (#{assetId}, #{playerId}, #{previewUrl}, #{downloadUrl}, #{status}, #{isCover}, #{sortOrder}, #{fileName}, #{contentType}, #{fileSize})
            """)
    int insertPhoto(@Param("assetId") String assetId,
                    @Param("playerId") int playerId,
                    @Param("previewUrl") String previewUrl,
                    @Param("downloadUrl") String downloadUrl,
                    @Param("status") String status,
                    @Param("isCover") boolean isCover,
                    @Param("sortOrder") int sortOrder,
                    @Param("fileName") String fileName,
                    @Param("contentType") String contentType,
                    @Param("fileSize") long fileSize);

    @Update("""
            UPDATE photo_assets
            SET player_id = #{playerId},
                status = #{status},
                is_cover = #{isCover},
                sort_order = #{sortOrder},
                download_url = #{downloadUrl},
                updated_at = CURRENT_TIMESTAMP
            WHERE asset_id = #{assetId}
            """)
    int updateMetadata(@Param("assetId") String assetId,
                       @Param("playerId") int playerId,
                       @Param("status") String status,
                       @Param("isCover") boolean isCover,
                       @Param("sortOrder") int sortOrder,
                       @Param("downloadUrl") String downloadUrl);

    @Update("""
            UPDATE photo_assets
            SET preview_url = #{previewUrl},
                file_name = #{fileName},
                content_type = #{contentType},
                file_size = #{fileSize},
                updated_at = CURRENT_TIMESTAMP
            WHERE asset_id = #{assetId}
            """)
    int updateFile(@Param("assetId") String assetId,
                   @Param("previewUrl") String previewUrl,
                   @Param("fileName") String fileName,
                   @Param("contentType") String contentType,
                   @Param("fileSize") long fileSize);

    @Update("""
            UPDATE photo_assets
            SET status = #{status},
                is_cover = CASE WHEN #{status} = 'inactive' THEN 0 ELSE is_cover END,
                updated_at = CURRENT_TIMESTAMP
            WHERE asset_id = #{assetId}
            """)
    int updateStatus(@Param("assetId") String assetId, @Param("status") String status);

    @Update("""
            UPDATE photo_assets
            SET is_cover = 0,
                updated_at = CURRENT_TIMESTAMP
            WHERE player_id = #{playerId}
              AND asset_id <> #{assetId}
            """)
    int clearOtherCovers(@Param("playerId") int playerId, @Param("assetId") String assetId);

    @Update("""
            UPDATE photo_assets
            SET is_cover = 1,
                status = 'active',
                updated_at = CURRENT_TIMESTAMP
            WHERE asset_id = #{assetId}
            """)
    int setCover(@Param("assetId") String assetId);
}
