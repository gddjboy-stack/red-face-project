package com.redface.mapper;

import com.redface.entity.TokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import java.util.List;

/**
 * tokens 表 Mapper。核销必须使用条件 UPDATE 原子抢占，禁止先 SELECT 再 UPDATE。
 */
@Mapper
public interface TokenMapper {

    /**
     * 原子抢占未使用卡密。只有 status='unused' 时才会更新为 used。
     *
     * @param token  卡密
     * @param userId 核销用户 ID
     * @param source 核销来源
     * @return 受影响行数；只有返回 1 才代表抢占成功
     */
    @Update("""
            UPDATE tokens
            SET status = 'used',
                user_id = #{userId},
                used_at = CURRENT_TIMESTAMP,
                redeem_source = #{source}
            WHERE token_id = #{token}
              AND status = 'unused'
            """)
    int markUsedIfUnused(@Param("token") String token,
                         @Param("userId") String userId,
                         @Param("source") String source);

    /**
     * 原子抢占退款。只有 status='used' 时才会更新为 'refunded'，同一张卡密只能被退一次。
     *
     * <p>C14 退款防重复第一道防线：与 C5 核销同样采用条件 UPDATE + 检查影响行数的成熟模式。
     * 只有返回 1 才代表退款抢占成功；返回 0 说明该卡不是 used 态（已退款或从未核销）。
     *
     * @param token 卡密
     * @return 受影响行数；只有返回 1 才代表抢占成功
     */
    @Update("""
            UPDATE tokens
            SET status = 'refunded'
            WHERE token_id = #{token}
              AND status = 'used'
            """)
    int markRefundedIfUsed(@Param("token") String token);

    /**
     * 根据卡密查询 token 记录。该方法只允许在原子抢占之后用于读取已抢占结果或区分失败原因。
     *
     * @param token 卡密
     * @return 卡密实体；不存在时返回 null
     */
    @Select("""
            SELECT token_id AS tokenId,
                   player_id AS playerId,
                   points,
                   photo_asset_id AS photoAssetId,
                   product_sku AS productSku,
                   aqiso_batch_id AS aqisoBatchId,
                   status,
                   order_id AS orderId,
                   user_id AS userId,
                   redeem_source AS redeemSource,
                   used_at AS usedAt
            FROM tokens
            WHERE token_id = #{token}
            """)
    TokenEntity findById(@Param("token") String token);

    @Select("SELECT COUNT(*) > 0 FROM tokens WHERE token_id = #{tokenId}")
    boolean existsByTokenId(@Param("tokenId") String tokenId);

    @Select("""
            SELECT token_id AS tokenId,
                   player_id AS playerId,
                   points,
                   photo_asset_id AS photoAssetId,
                   product_sku AS productSku,
                   aqiso_batch_id AS aqisoBatchId,
                   status,
                   order_id AS orderId,
                   user_id AS userId,
                   redeem_source AS redeemSource,
                   used_at AS usedAt
            FROM tokens
            WHERE aqiso_batch_id = #{aqisoBatchId}
            """)
    List<TokenEntity> findByAqisoBatchId(@Param("aqisoBatchId") String aqisoBatchId);

    @Insert("""
            <script>
            INSERT INTO tokens (token_id, player_id, points, photo_asset_id, product_sku, aqiso_batch_id, status, created_at)
            VALUES
            <foreach collection='tokens' item='token' separator=','>
                (#{token.tokenId}, #{token.playerId}, #{token.points}, #{token.photoAssetId}, #{token.productSku}, #{token.aqisoBatchId}, #{token.status}, #{token.createdAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("tokens") List<TokenEntity> tokens);
}
