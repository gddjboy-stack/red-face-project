package com.redface.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * user_photo_collection 表 Mapper，用于核销成功后自动收藏数字写真。
 */
@Mapper
public interface UserPhotoCollectionMapper {

    /**
     * 插入用户写真收藏。唯一键冲突由上层捕获并忽略。
     *
     * @param userId  用户 ID
     * @param assetId 写真资产 ID
     * @param tokenId 卡密
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO user_photo_collection (user_id, asset_id, token_id)
            VALUES (#{userId}, #{assetId}, #{tokenId})
            """)
    int insert(@Param("userId") String userId,
               @Param("assetId") String assetId,
               @Param("tokenId") String tokenId);

    /**
     * 统计指定用户与卡密的收藏记录数量，用于测试验证。
     *
     * @param userId  用户 ID
     * @param tokenId 卡密
     * @return 收藏记录数量
     */
    @Select("SELECT COUNT(*) FROM user_photo_collection WHERE user_id = #{userId} AND token_id = #{tokenId}")
    long countByUserAndToken(@Param("userId") String userId, @Param("tokenId") String tokenId);
}
