package com.redface.mapper;

import com.redface.entity.ProductPriceConfig;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 商品原价配置（C20-4B）。
 *
 * <p>人气按「原价 × 件数」计算（John 2026-08-01 决策），原价由我方自行定义而非从订单导出表反推，
 * 因为「订单应付金额」已扣除运费与各类优惠，会让包邮与用券的订单人气缩水。
 */
@Mapper
public interface ProductPriceConfigMapper {

    @Select("""
            SELECT merchant_code, product_name, unit_price_cent, status, operator_id,
                   created_at, updated_at
              FROM product_price_config
             WHERE merchant_code = #{merchantCode}
            """)
    ProductPriceConfig findByMerchantCode(@Param("merchantCode") String merchantCode);

    @Select("""
            SELECT merchant_code, product_name, unit_price_cent, status, operator_id,
                   created_at, updated_at
              FROM product_price_config
             ORDER BY merchant_code
            """)
    List<ProductPriceConfig> findAll();

    /**
     * 新增或更新配置。改价必须留痕，故不做物理覆盖以外的静默处理，
     * 上层服务在改价时写操作日志（改价会改变历史订单的换算依据，属高风险操作）。
     */
    @Update("""
            INSERT INTO product_price_config
                   (merchant_code, product_name, unit_price_cent, status, operator_id)
            VALUES (#{merchantCode}, #{productName}, #{unitPriceCent}, #{status}, #{operatorId})
            ON DUPLICATE KEY UPDATE
                   product_name = #{productName},
                   unit_price_cent = #{unitPriceCent},
                   status = #{status},
                   operator_id = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
            """)
    int upsert(@Param("merchantCode") String merchantCode,
               @Param("productName") String productName,
               @Param("unitPriceCent") long unitPriceCent,
               @Param("status") String status,
               @Param("operatorId") String operatorId);
}
