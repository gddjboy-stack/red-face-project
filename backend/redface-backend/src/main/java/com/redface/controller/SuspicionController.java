package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.auth.CurrentUser;
import com.redface.dto.SuspicionErrorData;
import com.redface.dto.SuspicionStatusResponse;
import com.redface.dto.SuspicionSubmitRequest;
import com.redface.dto.SuspicionSubmitResponse;
import com.redface.service.SuspicionException;
import com.redface.service.SuspicionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C13 真相识破用户侧 API。userId 只能从 Bearer token 登录态注入。
 */
@RestController
@RequestMapping("/api/suspicion")
public class SuspicionController {
    private final SuspicionService suspicionService;

    public SuspicionController(SuspicionService suspicionService) {
        this.suspicionService = suspicionService;
    }

    @GetMapping("/status")
    public ApiResponse<SuspicionStatusResponse> getStatus(@CurrentUser String userId,
                                                          @RequestParam(required = false) Integer roundId) {
        return ApiResponse.success(suspicionService.getStatus(userId, roundId));
    }

    @PostMapping("/submit")
    public ApiResponse<?> submit(@CurrentUser String userId, @RequestBody SuspicionSubmitRequest request) {
        try {
            SuspicionSubmitResponse response = suspicionService.submit(userId, request);
            return ApiResponse.success(response);
        } catch (SuspicionException error) {
            return mapFailure(error);
        }
    }

    private ApiResponse<SuspicionErrorData> mapFailure(SuspicionException error) {
        String businessCode = error.getBusinessCode();
        int apiCode = switch (businessCode) {
            case SuspicionService.CODE_NOT_OPEN -> 41001;
            case SuspicionService.CODE_INVALID_CANDIDATE -> 41002;
            case SuspicionService.CODE_ALREADY_SUBMITTED -> 41003;
            case SuspicionService.CODE_ROUND_MISMATCH -> 41004;
            default -> 41000;
        };
        return ApiResponse.error(apiCode, error.getMessage(), new SuspicionErrorData(businessCode));
    }
}
