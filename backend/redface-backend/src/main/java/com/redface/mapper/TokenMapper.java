package com.redface.mapper;

import com.redface.entity.TokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
}
