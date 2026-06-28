package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.TokenGenerateRequest;
import com.redface.dto.TokenGenerateResponse;
import com.redface.service.TokenAdminService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C18 后台卡密生成与导出接口。
 */
@RestController
@RequestMapping("/api/admin/tokens")
public class TokenAdminController {

    private final TokenAdminService tokenAdminService;

    public TokenAdminController(TokenAdminService tokenAdminService) {
        this.tokenAdminService = tokenAdminService;
    }

    @PostMapping("/generate")
    public ApiResponse<TokenGenerateResponse> generate(@RequestBody TokenGenerateRequest request) {
        return ApiResponse.success(tokenAdminService.generate(request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String batchId) {
        String csvContent = tokenAdminService.exportBatch(batchId);
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", "tokens_" + batchId + ".txt");
        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }
}
