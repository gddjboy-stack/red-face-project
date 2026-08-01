package com.redface.mapper;

import com.redface.entity.LiveMetricWatermark;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * C20-4A live_metric_watermark 水位线 Mapper。
 *
 * <p>抖音官方直播中控台只提供「本场直播」的累计数，不提供跨场次历史累计，
 * 每场开播三个数字都从 0 重新开始。运营录入的是「当前累计总数」，
 * 本表保存上次录入的总数作为水位线，本次增量 = 当前总数 - 水位线。
 */
@Mapper
public interface LiveMetricWatermarkMapper {

    /**
     * 读取指定来源的水位线。
     *
     * @param metricType 数据来源
     * @return 水位线记录，不存在时返回 null
     */
    @Select("""
            SELECT metric_type, last_total, session_seq, prev_total, prev_session_seq,
                   calibrated_at, entry_count, operator_id, updated_at
              FROM live_metric_watermark
             WHERE metric_type = #{metricType}
            """)
    LiveMetricWatermark findByMetricType(@Param("metricType") String metricType);

    /**
     * 首次插入水位线行。仅在该来源尚无记录时调用。
     *
     * @param metricType 数据来源
     * @param lastTotal  水位线值
     * @param sessionSeq 计数周期标识
     * @param operatorId 操作人
     * @return 受影响行数
     */
    @Update("""
            INSERT INTO live_metric_watermark
                   (metric_type, last_total, session_seq, entry_count, operator_id)
            VALUES (#{metricType}, #{lastTotal}, #{sessionSeq}, 0, #{operatorId})
            """)
    int insert(@Param("metricType") String metricType,
               @Param("lastTotal") long lastTotal,
               @Param("sessionSeq") String sessionSeq,
               @Param("operatorId") String operatorId);

    /**
     * 录入后推进水位线，并累加本周期录入次数。
     *
     * <p>带 last_total 条件是乐观锁：并发录入时只有一个请求能推进成功，
     * 另一个请求受影响行数为 0，由服务层判定为冲突并拒绝，避免同一增量被重复入账。
     *
     * @param metricType    数据来源
     * @param newTotal      新水位线（本次录入的当前总数）
     * @param expectedTotal 期望的原水位线，用于乐观锁比对
     * @param operatorId    操作人
     * @return 受影响行数，0 表示乐观锁冲突
     */
    @Update("""
            UPDATE live_metric_watermark
               SET last_total = #{newTotal},
                   entry_count = entry_count + 1,
                   operator_id = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE metric_type = #{metricType}
               AND last_total = #{expectedTotal}
            """)
    int advance(@Param("metricType") String metricType,
                @Param("newTotal") long newTotal,
                @Param("expectedTotal") long expectedTotal,
                @Param("operatorId") String operatorId);

    /**
     * 校准（归零）水位线。保留归零前原值与周期标识，供撤销与人工冲销核算。
     *
     * @param metricType 数据来源
     * @param sessionSeq 新的计数周期标识
     * @param operatorId 操作人
     * @return 受影响行数
     */
    @Update("""
            UPDATE live_metric_watermark
               SET prev_total = last_total,
                   prev_session_seq = session_seq,
                   last_total = 0,
                   session_seq = #{sessionSeq},
                   entry_count = 0,
                   calibrated_at = CURRENT_TIMESTAMP,
                   operator_id = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE metric_type = #{metricType}
            """)
    int calibrate(@Param("metricType") String metricType,
                  @Param("sessionSeq") String sessionSeq,
                  @Param("operatorId") String operatorId);

    /**
     * 撤销最近一次校准，把水位线与周期标识恢复为归零前的值。
     *
     * <p>带 entry_count = 0 条件：校准后一旦发生过录入，流水已按新周期入账，
     * 此时自动恢复会造成账实不符，必须走人工冲销，因此这里直接拒绝。
     *
     * @param metricType 数据来源
     * @param operatorId 操作人
     * @return 受影响行数，0 表示不满足撤销条件
     */
    @Update("""
            UPDATE live_metric_watermark
               SET last_total = prev_total,
                   session_seq = prev_session_seq,
                   prev_total = NULL,
                   prev_session_seq = NULL,
                   calibrated_at = NULL,
                   operator_id = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE metric_type = #{metricType}
               AND entry_count = 0
               AND prev_total IS NOT NULL
               AND prev_session_seq IS NOT NULL
            """)
    int revokeCalibration(@Param("metricType") String metricType,
                          @Param("operatorId") String operatorId);
}
