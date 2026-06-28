package com.redface.service;

import com.redface.api.ApiException;
import com.redface.dto.AdminPhotoView;
import com.redface.dto.TokenGenerateRequest;
import com.redface.dto.TokenGenerateResponse;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.PhotoAssetMapper;
import com.redface.model.Token;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TokenAdminService {

    private final TokenGeneratorService tokenGeneratorService;
    private final PhotoAssetMapper photoAssetMapper;
    private final OperationsLogMapper operationsLogMapper;

    public TokenAdminService(TokenGeneratorService tokenGeneratorService,
                             PhotoAssetMapper photoAssetMapper,
                             OperationsLogMapper operationsLogMapper) {
        this.tokenGeneratorService = tokenGeneratorService;
        this.photoAssetMapper = photoAssetMapper;
        this.operationsLogMapper = operationsLogMapper;
    }

    @Transactional
    public TokenGenerateResponse generate(TokenGenerateRequest request) {
        if (!StringUtils.hasText(request.getOperatorId())) {
            throw new IllegalArgumentException("operatorId不能为空");
        }
        if (request.getPlayerId() == null || request.getPlayerId() <= 0) {
            throw new IllegalArgumentException("无效的 playerId");
        }
        if (request.getPoints() == null || request.getPoints() <= 0) {
            throw new IllegalArgumentException("积分必须为正整数");
        }
        if (request.getCount() == null || request.getCount() <= 0 || request.getCount() > 10000) {
            throw new IllegalArgumentException("生成数量必须在 1 到 10000 之间");
        }

        // C18 必改 1：后端强制校验码绑的写真必须属于该选手且 active
        if (StringUtils.hasText(request.getPhotoAssetId())) {
            AdminPhotoView photo = photoAssetMapper.findByAssetId(request.getPhotoAssetId());
            if (photo == null) {
                throw new ApiException(41801, "绑定的写真不存在");
            }
            if (!photo.getPlayerId().equals(request.getPlayerId())) {
                throw new ApiException(41802, "绑定的写真不属于所选选手");
            }
            if (!"active".equals(photo.getStatus())) {
                throw new ApiException(41803, "绑定的写真已下架");
            }
        }

        List<Token> generated = tokenGeneratorService.generateBatch(
                request.getCount(),
                request.getPlayerId(),
                request.getPoints(),
                request.getPhotoAssetId(),
                request.getProductSku()
        );

        if (generated.isEmpty()) {
            throw new IllegalStateException("生成失败");
        }

        String batchId = generated.get(0).getAqisoBatchId();

        operationsLogMapper.insert(
                request.getOperatorId(),
                "token_generate",
                "batch:" + batchId,
                "{\"playerId\":" + request.getPlayerId() + ",\"count\":" + request.getCount() + ",\"points\":" + request.getPoints() + ",\"photoAssetId\":\"" + request.getPhotoAssetId() + "\"}",
                "生成卡密批次"
        );

        TokenGenerateResponse response = new TokenGenerateResponse();
        response.setBatchId(batchId);
        response.setGeneratedCount(generated.size());
        return response;
    }

    public String exportBatch(String batchId) {
        if (!StringUtils.hasText(batchId)) {
            throw new IllegalArgumentException("batchId不能为空");
        }
        return tokenGeneratorService.exportBatch(batchId);
    }
}
