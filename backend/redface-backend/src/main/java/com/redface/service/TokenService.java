package com.redface.service;

import com.redface.config.AppConstants;
import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.RedeemResult;
import com.redface.entity.TokenEntity;
import com.redface.mapper.TokenMapper;
import com.redface.mapper.UserPhotoCollectionMapper;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 卡密核销。最大风险:同一卡密被并发核销两次。
 * 防御:条件UPDATE抢占+检查影响行数。绝对禁止"先SELECT再UPDATE"。
 */
@Service
public class TokenService {

    private static final String TOKEN_SOURCE = "token";
    private static final String DEFAULT_REDEEM_SOURCE = "backend";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^RFZJ-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}$");

    private final TokenMapper tokenMapper;
    private final PopularityService popularityService;
    private final FailureCounter failureCounter;
    private final UserPhotoCollectionMapper userPhotoCollectionMapper;
    private final RoundService roundService;

    public TokenService(TokenMapper tokenMapper,
                        PopularityService popularityService,
                        FailureCounter failureCounter,
                        UserPhotoCollectionMapper userPhotoCollectionMapper,
                        RoundService roundService) {
        this.tokenMapper = tokenMapper;
        this.popularityService = popularityService;
        this.failureCounter = failureCounter;
        this.userPhotoCollectionMapper = userPhotoCollectionMapper;
        this.roundService = roundService;
    }

    /**
     * 核销卡密。固定流程:规范化 → 防爆破 → 轮次预检查 → 原子抢占 → 人气入账 → 写真收藏 → 返回。
     *
     * @param rawInput 用户输入卡密
     * @param userId   用户 ID
     * @param source   核销来源，例如 h5/manual/backend
     * @return 核销结果
     */
    @Transactional
    public RedeemResult redeem(String rawInput, String userId, String source) {
        validateUserId(userId);

        // === 第1步:输入规范化 ===
        String token;
        try {
            token = normalize(rawInput);
        } catch (IllegalArgumentException e) {
            failureCounter.recordFailure(userId);
            return RedeemResult.fail("invalid_format", e.getMessage());
        }

        // === 第2步:防爆破检查 ===
        if (failureCounter.isLocked(userId)) {
            return RedeemResult.locked(failureCounter.remainingSeconds(userId));
        }

        // === 第3步:轮次预检查 ===
        Integer roundId = roundService.getCurrentAccrualRoundId();
        if (roundId == null) {
            return RedeemResult.fail("round_not_available", "当前无可用轮次,请联系工作人员");
        }

        // === 第4步:原子抢占(核心!) ===
        int rows = tokenMapper.markUsedIfUnused(token, userId, normalizeRedeemSource(source));
        if (rows != 1) {
            failureCounter.recordFailure(userId);
            TokenEntity existing = tokenMapper.findById(token);
            if (existing != null && "used".equals(existing.getStatus())) {
                return RedeemResult.alreadyUsed();
            }
            return RedeemResult.fail("not_found", "卡密不存在或不可用");
        }

        TokenEntity t = tokenMapper.findById(token);
        if (t == null) {
            throw new IllegalStateException("卡密抢占成功后未查询到记录");
        }

        // === 第5步:抢占成功才加人气值 ===
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setTargetType("player");
        req.setTargetId(t.getPlayerId());
        req.setSource(TOKEN_SOURCE);
        req.setRawValue(t.getPoints());
        req.setRoundId(roundId);
        req.setIdempotencyKey("token_" + token);
        req.setOperatorId(userId);
        req.setReason("卡密核销入账");
        req.setOccurredAt(LocalDateTime.now());
        popularityService.applyChange(req);

        // === 第6步:自动收藏写真 ===
        collectPhotoIfPresent(userId, token, t.getPhotoAssetId());

        failureCounter.clear(userId);
        return RedeemResult.success(token, t.getPlayerId(), t.getPoints(), t.getPhotoAssetId());
    }

    private String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("卡密不能为空");
        }
        String normalized = toHalfWidth(raw.trim()).toUpperCase();
        if (!TOKEN_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("卡密格式错误");
        }
        return normalized;
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

    private void collectPhotoIfPresent(String userId, String token, String photoAssetId) {
        if (!StringUtils.hasText(photoAssetId)) {
            return;
        }
        try {
            userPhotoCollectionMapper.insert(userId, photoAssetId, token);
        } catch (DuplicateKeyException ignored) {
            // 用户写真收藏使用唯一键兜底，重复收藏不影响核销主流程。
        }
    }

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId不能为空");
        }
    }

    private String normalizeRedeemSource(String source) {
        return StringUtils.hasText(source) ? source : DEFAULT_REDEEM_SOURCE;
    }
}
