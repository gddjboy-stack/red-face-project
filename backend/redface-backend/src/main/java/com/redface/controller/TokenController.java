package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.auth.CurrentUser;
import com.redface.dto.RedeemErrorData;
import com.redface.dto.RedeemRequest;
import com.redface.dto.RedeemResponse;
import com.redface.dto.RedeemResult;
import com.redface.query.RedeemViewService;
import com.redface.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-3 卡密核销接口。userId 只能从 Bearer token 登录态注入，不接受前端传参。
 */
@RestController
@RequestMapping("/api/tokens")
public class TokenController {
    private final TokenService tokenService;
    private final RedeemViewService redeemViewService;

    public TokenController(TokenService tokenService, RedeemViewService redeemViewService) {
        this.tokenService = tokenService;
        this.redeemViewService = redeemViewService;
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<?>> redeem(@CurrentUser String userId, @RequestBody RedeemRequest request) {
        RedeemResult result = tokenService.redeem(request == null ? null : request.getToken(), userId, "miniapp");
        if (result.isSuccess()) {
            RedeemResponse response = redeemViewService.getRedeemResponse(result.getTokenId(), userId, result.getMembership());
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        return ResponseEntity.ok(mapRedeemFailure(result));
    }

    private ApiResponse<RedeemErrorData> mapRedeemFailure(RedeemResult result) {
        String businessCode = result.getCode();
        int apiCode = switch (businessCode) {
            case "invalid_format" -> 40001;
            case "not_found" -> 40002;
            case "already_used" -> 40003;
            case "locked" -> 40004;
            case "round_not_available" -> 40005;
            default -> 40000;
        };
        Long remainingSeconds = "locked".equals(businessCode) ? result.getRemainingSeconds() : null;
        return ApiResponse.error(apiCode, result.getMessage(), new RedeemErrorData(businessCode, remainingSeconds));
    }
}
