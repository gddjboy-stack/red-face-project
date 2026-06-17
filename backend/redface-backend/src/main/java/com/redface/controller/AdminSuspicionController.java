package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.SuspicionStatusResponse;
import com.redface.service.SuspicionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C13 后台识破监控 API。只展示候选分布，不暴露真实卧底身份。
 */
@RestController
@RequestMapping("/api/admin/suspicion")
public class AdminSuspicionController {
    private final SuspicionService suspicionService;

    public AdminSuspicionController(SuspicionService suspicionService) {
        this.suspicionService = suspicionService;
    }

    @GetMapping("/status")
    public ApiResponse<SuspicionStatusResponse> getStatus(@RequestParam(required = false) Integer roundId) {
        return ApiResponse.success(suspicionService.getAdminStatus(roundId));
    }
}
