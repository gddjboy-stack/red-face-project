package com.redface.service;

import com.redface.config.AppConstants;
import com.redface.model.Token;
import com.redface.mapper.TokenMapper;
import com.redface.entity.TokenEntity;
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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TokenGeneratorServiceTest {

    @Mock
    private TokenMapper tokenMapper;

    @InjectMocks
    private TokenGeneratorServiceImpl tokenGeneratorService;

    @BeforeEach
    void setUp() {
        // Setup is handled in individual tests to avoid UnnecessaryStubbingException
    }

    @Test
    void testGenerateBatch() {
        int count = 5;
        int playerId = 1;
        long points = 100L;
        String photoAssetId = "asset123";
        String productSku = "sku456";

        when(tokenMapper.existsByTokenId(anyString())).thenReturn(false);
        when(tokenMapper.insertBatch(any(List.class))).thenReturn(count);

        List<Token> generatedTokens = tokenGeneratorService.generateBatch(count, playerId, points, photoAssetId, productSku);

        assertNotNull(generatedTokens);
        assertEquals(count, generatedTokens.size());
        verify(tokenMapper, times(1)).insertBatch(anyList());

        // Regex for RFZJ-XXXX-XXXX-XXXX where X is from the allowed character set
        // and excludes 0, 1, I, L, O
        String regex = "^RFZJ-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$";
        Pattern pattern = Pattern.compile(regex);

        for (Token token : generatedTokens) {
            assertNotNull(token.getTokenId());
            assertTrue(pattern.matcher(token.getTokenId()).matches(), "Token ID format mismatch: " + token.getTokenId());

            // Verify character exclusions
            assertFalse(token.getTokenId().contains("0"), "Token ID contains '0': " + token.getTokenId());
            assertFalse(token.getTokenId().contains("1"), "Token ID contains '1': " + token.getTokenId());
            assertFalse(token.getTokenId().contains("I"), "Token ID contains 'I': " + token.getTokenId());
            assertFalse(token.getTokenId().contains("L"), "Token ID contains 'L': " + token.getTokenId());
            assertFalse(token.getTokenId().contains("O"), "Token ID contains 'O': " + token.getTokenId());

            assertEquals(playerId, token.getPlayerId());
            assertEquals(points, token.getPoints());
            assertEquals(photoAssetId, token.getPhotoAssetId());
            assertEquals(productSku, token.getProductSku());
            assertNotNull(token.getAqisoBatchId());
            assertTrue(token.getAqisoBatchId().startsWith("BATCH-"));
            assertNotNull(token.getCreatedAt());
            assertEquals("unused", token.getStatus());
        }

        // Verify uniqueness of generated tokens within the batch
        long distinctTokens = generatedTokens.stream().map(Token::getTokenId).distinct().count();
        assertEquals(count, distinctTokens);
    }

    @Test
    void testExportBatch() {
        String batchId = "BATCH-12345";
        List<TokenEntity> mockTokenEntities = new ArrayList<>();
        TokenEntity token1 = new TokenEntity();
        token1.setTokenId("RFZJ-ABCD-EFGH-IJKL");
        token1.setPlayerId(1);
        token1.setPoints(100L);
        token1.setPhotoAssetId("asset123");
        token1.setProductSku("sku456");
        token1.setAqisoBatchId(batchId);
        token1.setStatus("unused");
        token1.setCreatedAt(LocalDateTime.now());
        mockTokenEntities.add(token1);
        TokenEntity token2 = new TokenEntity();
        token2.setTokenId("RFZJ-MNOP-QRST-UVWX");
        token2.setPlayerId(1);
        token2.setPoints(100L);
        token2.setPhotoAssetId("asset123");
        token2.setProductSku("sku456");
        token2.setAqisoBatchId(batchId);
        token2.setStatus("unused");
        token2.setCreatedAt(LocalDateTime.now());
        mockTokenEntities.add(token2);

        when(tokenMapper.findByAqisoBatchId(batchId)).thenReturn(mockTokenEntities);

        String exportedContent = tokenGeneratorService.exportBatch(batchId);

        assertNotNull(exportedContent);
        assertTrue(exportedContent.contains("RFZJ-ABCD-EFGH-IJKL"));
        assertTrue(exportedContent.contains("RFZJ-MNOP-QRST-UVWX"));
        assertTrue(exportedContent.endsWith("UVWX\n"));
    }

    @Test
    void testExportBatch_NoTokensFound() {
        String batchId = "NON_EXISTENT_BATCH";
        when(tokenMapper.findByAqisoBatchId(batchId)).thenReturn(new ArrayList<>());

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
