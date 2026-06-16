package com.redface.query;

import com.redface.dto.RedeemResponse;
import com.redface.mapper.C9QueryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * API-3 卡密核销成功后的只读页面 DTO 查询服务。
 */
@Service
public class RedeemViewService {
    private final C9QueryMapper c9QueryMapper;

    public RedeemViewService(C9QueryMapper c9QueryMapper) {
        this.c9QueryMapper = c9QueryMapper;
    }

    public RedeemResponse getRedeemResponse(String tokenId, String userId) {
        if (!StringUtils.hasText(tokenId)) {
            throw new IllegalArgumentException("tokenId不能为空");
        }
        RedeemResponse response = c9QueryMapper.findRedeemResponse(tokenId, userId);
        if (response == null) {
            throw new IllegalStateException("核销成功后未找到页面级核销结果");
        }
        return response;
    }
}
