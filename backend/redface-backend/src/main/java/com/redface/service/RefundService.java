package com.redface.service;

import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.dto.RefundResult;
import com.redface.entity.TokenEntity;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.PopularityLedgerMapper;
import com.redface.mapper.TokenMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C14 退款服务（Claude 裁定 2026.06.20，4 个边界问题均采纳方案 A）。
 *
 * <p>设计铁律（涉及"钱"，逐条对应 John/Claude 的约束）：
 * <ul>
 *   <li>退款是"在现有逻辑上加回滚"，绝不改 C5 核销主干、绝不改 C2 applyChange 核心；</li>
 *   <li>退款主键用 token（卡密），不引入 order_id，不推翻 C12 契约；</li>
 *   <li>防重复退款双重防线：① tokens 状态 used→refunded 的原子抢占；② applyChange 的幂等键 refund_{token}；</li>
 *   <li>人气回滚走人气引擎的正规入口 applyChange（source=refund，负数），绝不直接改统计库；</li>
 *   <li>整数运算：扣减额取核销原始 points 取负（long），无任何浮点/比例换算，零误差；</li>
 *   <li>退款只扣人气，不回收会员天数、不删写真收藏（已发的虚拟权益不收回）；</li>
 *   <li>允许统计表人气退成负数（如实记账），底层不加防负数拦截，展示层已兜底。</li>
 * </ul>
 */
@Service
public class RefundService {

    /** 退款人气回滚来源，必须与 PopularityService 中允许负数的 refund 来源一致。 */
    private static final String SOURCE_REFUND = "refund";
    /** 人气目标类型：退款回滚的是选手个人人气（与核销入账对称）。 */
    private static final String TARGET_PLAYER = "player";
    /** 退款审计动作类型。 */
    private static final String ACTION_REFUND = "refund";

    /** 退款失败业务码：卡密为空或格式不可识别。 */
    public static final String CODE_INVALID_TOKEN = "invalid_token";
    /** 退款失败业务码：卡密不存在或不是可退款的 used 态（含已退款、从未核销）。 */
    public static final String CODE_NOT_REFUNDABLE = "not_refundable";

    private final TokenMapper tokenMapper;
    private final PopularityService popularityService;
    private final PopularityLedgerMapper popularityLedgerMapper;
    private final OperationsLogMapper operationsLogMapper;

    public RefundService(TokenMapper tokenMapper,
                         PopularityService popularityService,
                         PopularityLedgerMapper popularityLedgerMapper,
                         OperationsLogMapper operationsLogMapper) {
        this.tokenMapper = tokenMapper;
        this.popularityService = popularityService;
        this.popularityLedgerMapper = popularityLedgerMapper;
        this.operationsLogMapper = operationsLogMapper;
    }

    /**
     * 后台退款。固定流程：规范化 → 原子抢占退款态 → 读取核销信息 → 反查原核销轮次 →
     * 人气负数回滚（走 applyChange + 幂等键）→ 写审计 → 返回。全程同一事务，任一步失败整体回滚。
     *
     * @param rawToken   后台输入的卡密（容忍大小写/全角差异）
     * @param operatorId 操作人（后台场控/客服）ID
     * @param reason     退款原因（审计必填）
     * @return 退款结果
     */
    @Transactional
    public RefundResult refund(String rawToken, String operatorId, String reason) {
        validateText(operatorId, "operatorId不能为空");
        validateText(reason, "reason不能为空");

        // === 第1步：输入规范化（与核销一致：trim + 全角转半角 + 大写），不做强制正则校验 ===
        String token = normalize(rawToken);

        // === 第2步：原子抢占退款（防重复第一道）。used→refunded，影响行数≠1 一律拒绝 ===
        int rows = tokenMapper.markRefundedIfUsed(token);
        if (rows != 1) {
            // 不是 used 态：可能不存在、从未核销、或已退款。统一拒绝，绝不放过。
            throw new RefundException(CODE_NOT_REFUNDABLE, "卡密不存在或不可退款（未核销或已退款）");
        }

        // === 第3步：读取核销信息（抢占成功后才读，拿 playerId 与 points） ===
        TokenEntity t = tokenMapper.findById(token);
        if (t == null) {
            throw new IllegalStateException("退款抢占成功后未查询到卡密记录");
        }
        if (t.getPlayerId() == null) {
            throw new IllegalStateException("退款卡密缺少绑定选手，无法回滚人气");
        }

        // === 第4步：反查原核销轮次，把人气精确扣回核销当时记账的那一轮，避免跨轮退款扣错轮次 ===
        Integer roundId = popularityLedgerMapper.findRoundIdByIdempotencyKey("token_" + token);
        if (roundId == null) {
            // 理论上 used 态必有核销流水；若缺失说明数据异常，宁可整体回滚也不乱扣轮次。
            throw new IllegalStateException("未找到该卡密的原始核销人气流水，无法确定回滚轮次");
        }

        // === 第5步：人气负数回滚（走人气引擎正规入口；幂等键防重复第二道） ===
        long refundPoints = t.getPoints();
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setTargetType(TARGET_PLAYER);
        req.setTargetId(t.getPlayerId());
        req.setSource(SOURCE_REFUND);
        req.setRawValue(-refundPoints); // 整数取负，零误差；refund 来源允许负数
        req.setRoundId(roundId);
        req.setIdempotencyKey("refund_" + token);
        req.setOperatorId(operatorId);
        req.setReason(reason);
        req.setOccurredAt(LocalDateTime.now());
        PopularityChangeResult changeResult = popularityService.applyChange(req);
        if (changeResult.isDuplicated()) {
            // 幂等键已存在：说明这张卡的退款流水已写过。状态抢占已拦住绝大多数重复，
            // 这里是第二道防线，命中即视为重复退款，整体回滚并拒绝。
            throw new RefundException(CODE_NOT_REFUNDABLE, "该卡密退款已处理，请勿重复退款");
        }

        // === 第6步：写操作审计日志 ===
        operationsLogMapper.insert(operatorId, ACTION_REFUND, "token:" + token,
                "{\"tokenId\":\"" + safe(token) + "\",\"playerId\":" + t.getPlayerId()
                        + ",\"refundedPoints\":" + refundPoints + ",\"roundId\":" + roundId + "}",
                reason);

        // === 第7步：返回（refundedPoints 用正数展示，实际写入流水为负数） ===
        return new RefundResult(token, t.getPlayerId(), refundPoints, roundId);
    }

    /**
     * 卡密规范化：与核销侧规则一致（trim + 全角转半角 + 大写）。
     * 故意不在此做正则格式校验——退款是否合法最终由"数据库里是否存在且为 used 态"裁定。
     */
    private String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new RefundException(CODE_INVALID_TOKEN, "卡密不能为空");
        }
        return toHalfWidth(raw.trim()).toUpperCase();
    }

    private String toHalfWidth(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == 12288) {
                builder.append(' ');
            } else if (c >= 65281 && c <= 65374) {
                builder.append((char) (c - 65248));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private void validateText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
