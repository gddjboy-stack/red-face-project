package com.redface.mapper;

import com.redface.dto.ManualSalesSummaryItem;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * C20-6: 后台手工销量录入账本 Mapper。
 *
 * <p>与 {@code order_sales_ledger}（C20-4C 订单表批量导入）物理隔离。两表口径不同：
 * 本表无子订单号、无订单状态、无售后判定，是运营手工抄录的结果；混表会让日后对账
 * 无法区分「哪些人气来自订单表、哪些来自人工录入」。
 *
 * <p>幂等由表内 {@code uq_msl_idem} 唯一约束保证。调用方沿用 C20-3-FIX 群投票录入的
 * 「先查后插」策略，避免在 {@code @Transactional} 内捕获 DuplicateKeyException
 * 导致事务被标记回滚。
 */
@Mapper
public interface ManualSalesLedgerMapper {

    /**
     * 插入一笔手工销量流水。quantity 与 popularityValue 均可为负（负数为冲销纠错）。
     *
     * <p>unitPriceCent 是录入时刻的价格快照而非外键引用：商品原价可被改动，
     * 若不快照，事后核对会用新价重算出与当时实际入账不符的数字。
     */
    @Insert("""
            INSERT INTO manual_sales_ledger
                (round_id, player_id, merchant_code, product_name, quantity,
                 unit_price_cent, popularity_value, idempotency_key, operator_id, reason)
            VALUES
                (#{roundId}, #{playerId}, #{merchantCode}, #{productName}, #{quantity},
                 #{unitPriceCent}, #{popularityValue}, #{idempotencyKey}, #{operatorId}, #{reason})
            """)
    int insert(@Param("roundId") int roundId,
               @Param("playerId") int playerId,
               @Param("merchantCode") String merchantCode,
               @Param("productName") String productName,
               @Param("quantity") int quantity,
               @Param("unitPriceCent") long unitPriceCent,
               @Param("popularityValue") long popularityValue,
               @Param("idempotencyKey") String idempotencyKey,
               @Param("operatorId") String operatorId,
               @Param("reason") String reason);

    /** 按幂等键查询是否已存在流水（先查后插的查询侧，兼供测试与审计）。 */
    @Select("SELECT COUNT(*) FROM manual_sales_ledger WHERE idempotency_key = #{idempotencyKey}")
    int countByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 按「选手 + 商品」汇总本轮已录入销量。
     *
     * <p>为什么必须细到商品维度而不只按选手：John 2026-08-02 确认商家编码规则为
     * 「每位选手每款商品一个独立编码」。若只按选手汇总件数，明信片 30 件与写真 5 件
     * 会被加成「35 件」——这个数字没有业务含义，无法用来判断单价是否配错，
     * 而「件数与人气值并列展示」的全部价值正在于此。故汇总保留商品维度，
     * 由上层再聚合出选手级人气合计（两级展开，对应 Claude 裁定议题二方案 C）。
     */
    @Select("""
            SELECT m.player_id AS playerId,
                   p.name AS playerName,
                   p.number AS playerNumber,
                   m.merchant_code AS merchantCode,
                   MAX(m.product_name) AS productName,
                   COALESCE(SUM(m.quantity), 0) AS totalQuantity,
                   COALESCE(SUM(m.popularity_value), 0) AS totalPopularity,
                   COUNT(*) AS entryCount,
                   MAX(m.unit_price_cent) AS latestUnitPriceCent,
                   MIN(m.unit_price_cent) AS earliestUnitPriceCent
            FROM manual_sales_ledger m
            LEFT JOIN players p ON p.player_id = m.player_id
            WHERE m.round_id = #{roundId}
            GROUP BY m.player_id, p.name, p.number, m.merchant_code
            ORDER BY p.number ASC, m.merchant_code ASC
            """)
    List<ManualSalesSummaryItem> summarize(@Param("roundId") int roundId);

    /**
     * 查询本轮某选手某商品的累计件数净值（冲销后）。
     * 用于阻止「冲销到负数总量」——负销量没有业务含义，通常意味着运营选错了冲销对象。
     */
    @Select("""
            SELECT COALESCE(SUM(quantity), 0)
            FROM manual_sales_ledger
            WHERE round_id = #{roundId}
              AND player_id = #{playerId}
              AND merchant_code = #{merchantCode}
            """)
    int sumQuantity(@Param("roundId") int roundId,
                    @Param("playerId") int playerId,
                    @Param("merchantCode") String merchantCode);

    /**
     * 统计最近若干秒内「同轮次 + 同选手 + 同商品 + 同件数」的录入笔数。
     *
     * <p>用于软重复提示。既有幂等键机制（前端生成 UUID）只能防「同一次点击的重复提交」，
     * 防不住「运营以为没成功、手动又点一次」——那是一次新点击，会得到新的幂等键而正常入账。
     * 本方法把这种情形暴露出来，交由运营二次确认，而不是静默接受或静默丢弃。
     */
    @Select("""
            SELECT COUNT(*)
            FROM manual_sales_ledger
            WHERE round_id = #{roundId}
              AND player_id = #{playerId}
              AND merchant_code = #{merchantCode}
              AND quantity = #{quantity}
              AND created_at >= #{since}
            """)
    int countRecentSame(@Param("roundId") int roundId,
                        @Param("playerId") int playerId,
                        @Param("merchantCode") String merchantCode,
                        @Param("quantity") int quantity,
                        @Param("since") java.time.LocalDateTime since);

    /** 查询本轮全部选手的人气合计（用于超额提示的参照基准）。 */
    @Select("""
            SELECT COALESCE(MAX(t.total), 0)
            FROM (
                SELECT SUM(popularity_value) AS total
                FROM manual_sales_ledger
                WHERE round_id = #{roundId}
                GROUP BY player_id
            ) t
            """)
    long maxPlayerPopularity(@Param("roundId") int roundId);
}
