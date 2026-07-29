package com.redface.mapper;

import com.redface.dto.GroupVoteSummaryItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * C20-3-FIX: 群投票独立账本 Mapper。
 *
 * <p>群投票票数只用于卧底胜负判定，不折算人气值，与 popularity_ledger 物理隔离。
 * 幂等由表内 idempotency_key 唯一约束保证（插入冲突由调用方捕获 DuplicateKeyException）。
 */
@Mapper
public interface GroupVoteLedgerMapper {

    /**
     * 插入一笔群投票流水（正数累加/负数冲销）。
     * idempotency_key 唯一约束冲突时抛 DuplicateKeyException，由调用方处理为幂等拦截。
     */
    @Insert("""
            INSERT INTO group_vote_ledger
                (round_id, player_id, votes, idempotency_key, operator_id, reason)
            VALUES
                (#{roundId}, #{playerId}, #{votes}, #{idempotencyKey}, #{operatorId}, #{reason})
            """)
    int insert(@Param("roundId") int roundId,
               @Param("playerId") int playerId,
               @Param("votes") long votes,
               @Param("idempotencyKey") String idempotencyKey,
               @Param("operatorId") String operatorId,
               @Param("reason") String reason);

    /**
     * 汇总指定轮次各选手累计票数（冲销后净值），左连 players 补选手姓名与序号。
     */
    @Select("""
            SELECT g.player_id AS playerId,
                   p.name AS playerName,
                   p.number AS playerNumber,
                   COALESCE(SUM(g.votes), 0) AS totalVotes,
                   COUNT(*) AS entryCount
            FROM group_vote_ledger g
            LEFT JOIN players p ON p.player_id = g.player_id
            WHERE g.round_id = #{roundId}
            GROUP BY g.player_id, p.name, p.number
            ORDER BY p.number ASC
            """)
    List<GroupVoteSummaryItem> summarize(@Param("roundId") int roundId);

    /**
     * 查询指定轮次指定选手的累计票数净值。
     */
    @Select("""
            SELECT COALESCE(SUM(votes), 0)
            FROM group_vote_ledger
            WHERE round_id = #{roundId}
              AND player_id = #{playerId}
            """)
    long sumVotes(@Param("roundId") int roundId, @Param("playerId") int playerId);

    /**
     * 按幂等键查询是否已存在流水（用于测试与审计）。
     */
    @Select("SELECT COUNT(*) FROM group_vote_ledger WHERE idempotency_key = #{idempotencyKey}")
    int countByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
