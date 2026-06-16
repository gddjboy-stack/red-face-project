package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.LiveHomeResponse;
import com.redface.query.LiveHomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-1 首页直播状态接口。
 */
@RestController
@RequestMapping("/api/live")
public class LiveController {
    private final LiveHomeService liveHomeService;

    public LiveController(LiveHomeService liveHomeService) {
        this.liveHomeService = liveHomeService;
    }

    @GetMapping("/home")
    public ApiResponse<LiveHomeResponse> home() {
        return ApiResponse.success(liveHomeService.getHome());
    }
}
