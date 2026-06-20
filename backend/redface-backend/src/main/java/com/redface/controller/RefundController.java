package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.AdminOperationResult;
import com.redface.dto.RefundErrorData;
import com.redface.dto.RefundRequest;
import com.redface.dto.RefundResult;
import com.redface.service.RefundException;
import com.redface.service.RefundService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C14 退款后台 API。仅供场控后台调用，挂在 /api/admin/** 下，
 * 复用现有 AdminAuthInterceptor 的 X-Admin-Token 鉴权（不新增、不改鉴权）。
 *
 * <p>Controller 只做 HTTP 适配与错误码映射，退款业务全部委托 RefundService。
 */
@RestController
@RequestMapping("/api/admin")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/refund")
    public ApiResponse<?> refund(@RequestBody RefundRequest request) {
        try {
            RefundResult result = refundService.refund(
                    request == null ? null : request.getToken(),
                    request == null ? null : request.getOperatorId(),
                    request == null ? null : request.getReason());
            return ApiResponse.success(AdminOperationResult.of("refund", "退款成功", result));
        } catch (RefundException error) {
            return mapFailure(error);
        }
    }

    private ApiResponse<RefundErrorData> mapFailure(RefundException error) {
        String businessCode = error.getBusinessCode();
        int apiCode = switch (businessCode) {
            case RefundService.CODE_INVALID_TOKEN -> 42001;
            case RefundService.CODE_NOT_REFUNDABLE -> 42002;
            default -> 42000;
        };
        return ApiResponse.error(apiCode, error.getMessage(), new RefundErrorData(businessCode));
    }
}
