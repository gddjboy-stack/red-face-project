package com.redface.service;

import com.redface.config.AppConstants;
import com.redface.model.Token;
import com.redface.repository.TokenRepository;
import com.redface.service.impl.TokenGeneratorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TokenGeneratorServiceTest {

    @Mock
    private TokenRepository tokenRepository;

    @InjectMocks
    private TokenGeneratorServiceImpl tokenGeneratorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGenerateBatch() {
        int count = 5;
        int playerId = 1;
        long points = 100L;
        String photoAssetId = "asset123";
        String productSku = "sku456";

        when(tokenRepository.existsByTokenId(anyString())).thenReturn(false);
        when(tokenRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Token> tokens = invocation.getArgument(0);
            tokens.forEach(token -> {}); // No ID generation needed for TokenId, it's set in generateUniqueToken
            return tokens;
        });

        List<Token> generatedTokens = tokenGeneratorService.generateBatch(count, playerId, points, photoAssetId, productSku);

        assertNotNull(generatedTokens);
        assertEquals(count, generatedTokens.size());
        verify(tokenRepository, times(1)).saveAll(anyList());

        for (Token token : generatedTokens) {
            assertNotNull(token.getTokenId());
            assertTrue(token.getTokenId().startsWith(AppConstants.TOKEN_PREFIX));
            assertEquals(AppConstants.TOKEN_TOTAL_LENGTH, token.getTokenId().length());
            assertEquals(playerId, token.getPlayerId());
            assertEquals(points, token.getPoints());
            assertEquals(photoAssetId, token.getPhotoAssetId());
            assertEquals(productSku, token.getProductSku());
            assertNotNull(token.getAqisoBatchId());
            assertEquals("unused", token.getStatus());
            assertNotNull(token.getCreatedAt());
        }

        // Verify uniqueness of generated tokens within the batch
        long distinctTokens = generatedTokens.stream().map(Token::getTokenId).distinct().count();
        assertEquals(count, distinctTokens);
    }

    @Test
    void testExportBatch() {
        String batchId = "BATCH-12345";
        List<Token> tokens = Arrays.asList(
                createToken("RED-AAAA-BBBB-CCCC", batchId),
                createToken("RED-DDDD-EEEE-FFFF", batchId)
        );

        when(tokenRepository.findByAqisoBatchId(batchId)).thenReturn(tokens);

        String exportedContent = tokenGeneratorService.exportBatch(batchId);

        assertNotNull(exportedContent);
        assertTrue(exportedContent.contains("RED-AAAA-BBBB-CCCC"));
        assertTrue(exportedContent.contains("RED-DDDD-EEEE-FFFF"));
        assertEquals("RED-AAAA-BBBB-CCCC\nRED-DDDD-EEEE-FFFF\n", exportedContent);
    }

    @Test
    void testExportBatch_NoTokensFound() {
        String batchId = "NON_EXISTENT_BATCH";
        when(tokenRepository.findByAqisoBatchId(batchId)).thenReturn(new ArrayList<>());

        String exportedContent = tokenGeneratorService.exportBatch(batchId);

        assertNotNull(exportedContent);
        assertTrue(exportedContent.contains("No tokens found for batchId: " + batchId));
    }

    private Token createToken(String tokenId, String batchId) {
        Token token = new Token();
        token.setTokenId(tokenId);
        token.setAqisoBatchId(batchId);
        token.setPlayerId(1);
        token.setPoints(100L);
        token.setPhotoAssetId("asset123");
        token.setProductSku("sku456");
        token.setStatus("unused");
        token.setCreatedAt(LocalDateTime.now());
        return token;
    }
}
