package com.redface.service.impl;

import com.redface.config.AppConstants;
import com.redface.model.Token;
import com.redface.repository.TokenRepository;
import com.redface.service.TokenGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private TokenRepository tokenRepository;

    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    public List<Token> generateBatch(int count, int playerId, long points, String photoAssetId, String productSku) {
        String aqisoBatchId = "BATCH-" + System.currentTimeMillis();
        List<Token> generatedTokens = new ArrayList<>();
        Set<String> uniqueTokenIds = new HashSet<>();

        for (int i = 0; i < count; i++) {
            String tokenId;
            do {
                tokenId = generateUniqueToken();
            } while (uniqueTokenIds.contains(tokenId) || tokenRepository.existsByTokenId(tokenId));
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

        return tokenRepository.saveAll(generatedTokens);
    }

    @Override
    public String exportBatch(String batchId) {
        List<Token> tokens = tokenRepository.findByAqisoBatchId(batchId);
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
