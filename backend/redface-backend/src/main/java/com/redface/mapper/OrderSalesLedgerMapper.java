package com.redface.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 订单销量流水账（C20-4B）。
 *
 * <p>幂等键为 sub_order_no 的唯一约束：同一份文件重复导入时，重复行插入抛
 * DuplicateKeyException，由服务层计为「已跳过」而非报错。这是防止运营重复点击导入
 * 造成人气翻倍的唯一可靠手段——靠前端禁用按钮不可靠（刷新页面即失效）。
 */
@Mapper
public interface OrderSalesLedgerMapper {

    @Update("""
            INSERT INTO order_sales_ledger
                   (sub_order_no, main_order_no, merchant_code, player_id, quantity,
                    unit_price_cent, popularity_value, order_status, aftersale_status,
                    validity, invalid_reason, in_aftersale, paid_at, payable_amount_cent,
                    round_id, import_batch_id, operator_id, raw_row)
            VALUES (#{subOrderNo}, #{mainOrderNo}, #{merchantCode}, #{playerId}, #{quantity},
                    #{unitPriceCent}, #{popularityValue}, #{orderStatus}, #{aftersaleStatus},
                    #{validity}, #{invalidReason}, #{inAftersale}, #{paidAt}, #{payableAmountCent},
                    #{roundId}, #{importBatchId}, #{operatorId}, #{rawRow})
            """)
    int insert(@Param("subOrderNo") String subOrderNo,
               @Param("mainOrderNo") String mainOrderNo,
               @Param("merchantCode") String merchantCode,
               @Param("playerId") Integer playerId,
               @Param("quantity") int quantity,
               @Param("unitPriceCent") Long unitPriceCent,
               @Param("popularityValue") long popularityValue,
               @Param("orderStatus") String orderStatus,
               @Param("aftersaleStatus") String aftersaleStatus,
               @Param("validity") String validity,
               @Param("invalidReason") String invalidReason,
               @Param("inAftersale") int inAftersale,
               @Param("paidAt") java.time.LocalDateTime paidAt,
               @Param("payableAmountCent") Long payableAmountCent,
               @Param("roundId") Integer roundId,
               @Param("importBatchId") String importBatchId,
               @Param("operatorId") String operatorId,
               @Param("rawRow") String rawRow);

    @Select("SELECT COUNT(*) FROM order_sales_ledger WHERE sub_order_no = #{subOrderNo}")
    int countBySubOrderNo(@Param("subOrderNo") String subOrderNo);

    /**
     * 按商家编码归属选手。
     *
     * <p>复用 players.display_code（已存在且带唯一约束）作为归属键，不新增字段。
     * 不限定 status='active'：已淘汰选手的历史订单仍需能归属，否则淘汰后导入会整批变成无法归属。
     *
     * @param displayCode 选手编号，如 P12
     * @return 选手 ID，未命中返回 null
     */
    @Select("SELECT player_id FROM players WHERE display_code = #{displayCode}")
    Integer findPlayerIdByDisplayCode(@Param("displayCode") String displayCode);

    /**
     * 按商家编码取选手姓名（C20-4C）。
     *
     * <p>按选手汇总核对视图若只显示编号（P12），运营需在脑内完成一次「编号→人」的翻译，
     * 而这道翻译正是最容易出错且最难被发现的一步——编号配错时数字看起来完全正常。
     * 带上姓名后，「P12 选手甲 300 件」这种异常行运营能当场看出。
     *
     * @param displayCode 选手编号，如 P12
     * @return 选手姓名，未命中返回 null
     */
    @Select("SELECT name FROM players WHERE display_code = #{displayCode}")
    String findPlayerNameByDisplayCode(@Param("displayCode") String displayCode);

    /** 批次汇总，供导入后核对与赛后审计 */
    @Select("""
            SELECT validity, COUNT(*) AS row_count, COALESCE(SUM(popularity_value), 0) AS popularity_sum
              FROM order_sales_ledger
             WHERE import_batch_id = #{importBatchId}
             GROUP BY validity
            """)
    List<Map<String, Object>> summarizeBatch(@Param("importBatchId") String importBatchId);

    /** 售后中订单的风险敞口（Claude 裁定要求后台单列显示） */
    @Select("""
            SELECT COALESCE(SUM(popularity_value), 0)
              FROM order_sales_ledger
             WHERE in_aftersale = 1 AND validity = 'valid'
               AND (#{roundId} IS NULL OR round_id = #{roundId})
            """)
    long sumAftersaleExposure(@Param("roundId") Integer roundId);
}
