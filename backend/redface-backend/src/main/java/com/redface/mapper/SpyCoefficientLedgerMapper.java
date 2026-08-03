package com.redface.mapper;

import com.redface.dto.SpyCoefficientLedgerItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * C20-10: 卧底人气系数账本 Mapper。
 *
 * <p><b>与 team_coefficient_ledger 的关键差异：本表存乘数因子（factor），不存增量（delta）。</b>
 * 卧底系数规则是「任务加成 × 识破减半」相乘：×1.3 后再 ×0.5 应得 ×0.65（130×50/100=65）。
 * 若沿用团队版的 delta 加法累加会得到 100+30-50=80（×0.8）。以基础卧底人气 205000 计，
 * 两者相差 30750 人气，且两个数都不报错，运营无从判断哪个对——这是刻意不镜像团队版的原因。
 *
 * <p>幂等由表内 idempotency_key 唯一约束保证，插入冲突抛 DuplicateKeyException 由调用方处理。
 *
 * <p>撤销采用 revoked 标记而非物理删除：系数直接影响选手去留判定，撤销动作本身必须可追溯。
 */
@Mapper
public interface SpyCoefficientLedgerMapper {

    /**
     * 插入一条系数施加记录。
     *
     * @param factor     乘数因子×100（130 表示 ×1.3，50 表示 ×0.5），不是增量
     * @param factorType task_bonus / exposed_halve / manual
     */
    @Insert("""
            INSERT INTO spy_coefficient_ledger
                (player_id, round_id, factor, factor_type, idempotency_key, operator_id, reason)
            VALUES
                (#{playerId}, #{roundId}, #{factor}, #{factorType}, #{idempotencyKey}, #{operatorId}, #{reason})
            """)
    int insert(@Param("playerId") int playerId,
               @Param("roundId") int roundId,
               @Param("factor") int factor,
               @Param("factorType") String factorType,
               @Param("idempotencyKey") String idempotencyKey,
               @Param("operatorId") String operatorId,
               @Param("reason") String reason);

    /**
     * 列出指定选手轮次的全部账本条目（含已撤销，供界面分项回显与审计）。
     * 已撤销条目不参与系数计算，但必须可见，否则运营无法解释系数为何变化。
     */
    @Select("""
            SELECT l.id AS id,
                   l.player_id AS playerId,
                   l.round_id AS roundId,
                   l.factor AS factor,
                   l.factor_type AS factorType,
                   l.operator_id AS operatorId,
                   l.reason AS reason,
                   l.revoked AS revoked,
                   l.created_at AS createdAt
            FROM spy_coefficient_ledger l
            WHERE l.round_id = #{roundId}
              AND l.player_id = #{playerId}
            ORDER BY l.id ASC
            """)
    List<SpyCoefficientLedgerItem> listByPlayerRound(@Param("playerId") int playerId,
                                                     @Param("roundId") int roundId);

    /**
     * 取未撤销条目的 factor 列表，按施加顺序返回，用于撤销后按乘法重建系数。
     */
    @Select("""
            SELECT factor
            FROM spy_coefficient_ledger
            WHERE round_id = #{roundId}
              AND player_id = #{playerId}
              AND revoked = 0
            ORDER BY id ASC
            """)
    List<Integer> listActiveFactors(@Param("playerId") int playerId, @Param("roundId") int roundId);

    /**
     * 查询单条账本条目（撤销前需校验归属轮次与选手，避免跨轮误撤）。
     */
    @Select("""
            SELECT l.id AS id,
                   l.player_id AS playerId,
                   l.round_id AS roundId,
                   l.factor AS factor,
                   l.factor_type AS factorType,
                   l.operator_id AS operatorId,
                   l.reason AS reason,
                   l.revoked AS revoked,
                   l.created_at AS createdAt
            FROM spy_coefficient_ledger l
            WHERE l.id = #{id}
            """)
    SpyCoefficientLedgerItem findById(@Param("id") long id);

    /**
     * 标记撤销。返回 0 表示该条目不存在或已被撤销过（调用方据此避免重复撤销）。
     */
    @Update("""
            UPDATE spy_coefficient_ledger
            SET revoked = 1
            WHERE id = #{id}
              AND revoked = 0
            """)
    int markRevoked(@Param("id") long id);

    /**
     * 是否已存在同类型未撤销条目。用于阻断「识破减半」被重复施加：
     * 同一选手同一轮只应减半一次，重复施加会让卧底人气变成 ×0.25 且无人察觉。
     */
    @Select("""
            SELECT COUNT(*)
            FROM spy_coefficient_ledger
            WHERE round_id = #{roundId}
              AND player_id = #{playerId}
              AND factor_type = #{factorType}
              AND revoked = 0
            """)
    int countActiveByType(@Param("playerId") int playerId,
                          @Param("roundId") int roundId,
                          @Param("factorType") String factorType);

    @Select("SELECT COUNT(*) FROM spy_coefficient_ledger WHERE idempotency_key = #{idempotencyKey}")
    int countByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
