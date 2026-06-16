package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.auth.CurrentUser;
import com.redface.dto.MyPhotosResponse;
import com.redface.query.PhotoQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-4 我的写真接口。userId 只能从 Bearer token 登录态注入。
 */
@RestController
@RequestMapping("/api/me")
public class MeController {
    private final PhotoQueryService photoQueryService;

    public MeController(PhotoQueryService photoQueryService) {
        this.photoQueryService = photoQueryService;
    }

    @GetMapping("/photos")
    public ApiResponse<MyPhotosResponse> photos(@CurrentUser String userId) {
        return ApiResponse.success(photoQueryService.getMyPhotos(userId));
    }
}
