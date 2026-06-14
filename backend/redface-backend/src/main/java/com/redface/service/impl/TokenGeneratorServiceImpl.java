package com.redface.service.impl;

import com.redface.config.AppConstants;
import com.redface.model.Token;
import com.redface.mapper.TokenMapper;
import com.redface.entity.TokenEntity;
import com.redface.service.TokenGeneratorService;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Service
public class TokenGeneratorServiceImpl implements TokenGeneratorService {

    private final TokenMapper tokenMapper;

    public TokenGeneratorServiceImpl(TokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    public List<Token> generateBatch(int count, int playerId, long points, String photoAssetId, String productSku) {
        String aqisoBatchId = "BATCH-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        List<Token> generatedTokens = new ArrayList<>();
        Set<String> uniqueTokenIds = new HashSet<>();

        for (int i = 0; i < count; i++) {
            String tokenId;
            do {
                tokenId = generateUniqueToken();
            } while (uniqueTokenIds.contains(tokenId) || tokenMapper.existsByTokenId(tokenId));
            uniqueTokenIds.add(tokenId);

            Token token = new Token();
            token.setTokenId(tokenId);
            token.setPlayerId(playerId);
            token.setPoints(points);
            token.setPhotoAssetId(photoAssetId);
            token.setProductSku(productSku);
            token.setAqisoBatchId(aqisoBatchId);
            token.setStatus("unused"); // 初始状态为未使用
            token.setCreatedAt(LocalDateTime.now());
            generatedTokens.add(token);
        }

        List<TokenEntity> tokenEntities = generatedTokens.stream()
                .map(token -> {
                    TokenEntity entity = new TokenEntity();
                    entity.setTokenId(token.getTokenId());
                    entity.setPlayerId(token.getPlayerId());
                    entity.setPoints(token.getPoints());
                    entity.setPhotoAssetId(token.getPhotoAssetId());
                    entity.setProductSku(token.getProductSku());
                    entity.setAqisoBatchId(token.getAqisoBatchId());
                    entity.setStatus(token.getStatus());
                    entity.setCreatedAt(token.getCreatedAt());
                    return entity;
                }).collect(Collectors.toList());
        tokenMapper.insertBatch(tokenEntities);
        return generatedTokens;
    }

    @Override
    public String exportBatch(String batchId) {
        List<TokenEntity> tokenEntities = tokenMapper.findByAqisoBatchId(batchId);
        List<Token> tokens = tokenEntities.stream()
                .map(entity -> {
                    Token token = new Token();
                    token.setTokenId(entity.getTokenId());
                    token.setPlayerId(entity.getPlayerId());
                    token.setPoints(entity.getPoints());
                    token.setPhotoAssetId(entity.getPhotoAssetId());
                    token.setProductSku(entity.getProductSku());
                    token.setAqisoBatchId(entity.getAqisoBatchId());
                    token.setStatus(entity.getStatus());
                    token.setCreatedAt(entity.getCreatedAt());
                    return token;
                }).collect(Collectors.toList());
        if (tokens.isEmpty()) {
            return "No tokens found for batchId: " + batchId;
        }

        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            sb.append(token.getTokenId()).append("\n");
        }
        return sb.toString();
    }

    private String generateUniqueToken() {
        StringBuilder tokenBuilder = new StringBuilder(AppConstants.TOKEN_PREFIX);
        tokenBuilder.append("-");
        for (int i = 0; i < AppConstants.TOKEN_SECTION_COUNT; i++) {
            for (int j = 0; j < AppConstants.TOKEN_SECTION_LENGTH; j++) {
                tokenBuilder.append(AppConstants.TOKEN_CHARSET.charAt(secureRandom.nextInt(AppConstants.TOKEN_CHARSET.length())));
            }
            if (i < AppConstants.TOKEN_SECTION_COUNT - 1) {
                tokenBuilder.append("-");
            }
        }
        return tokenBuilder.toString();
    }
}
