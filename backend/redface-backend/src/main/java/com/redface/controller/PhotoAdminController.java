package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.AdminPhotoCoverRequest;
import com.redface.dto.AdminPhotoStatusRequest;
import com.redface.dto.AdminPhotoUpdateRequest;
import com.redface.dto.AdminPhotoView;
import com.redface.service.PhotoAdminService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * C17 后台写真资产管理接口。
 */
@RestController
@RequestMapping("/api/admin/photos")
public class PhotoAdminController {

    private final PhotoAdminService photoAdminService;

    public PhotoAdminController(PhotoAdminService photoAdminService) {
        this.photoAdminService = photoAdminService;
    }

    @GetMapping
    public ApiResponse<List<AdminPhotoView>> listPhotos(@RequestParam(required = false) Integer playerId,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.success(photoAdminService.listPhotos(playerId, status));
    }

    @PostMapping("/upload")
    public ApiResponse<AdminPhotoView> upload(@RequestParam String operatorId,
                                              @RequestParam int playerId,
                                              @RequestParam(defaultValue = "false") boolean isCover,
                                              @RequestParam(defaultValue = "0") int sortOrder,
                                              @RequestParam MultipartFile file) {
        return ApiResponse.success(photoAdminService.upload(operatorId, playerId, isCover, sortOrder, file));
    }

    @PutMapping("/{assetId}")
    public ApiResponse<AdminPhotoView> update(@PathVariable String assetId,
                                              @RequestBody AdminPhotoUpdateRequest request) {
        return ApiResponse.success(photoAdminService.update(assetId, request));
    }

    @PostMapping("/{assetId}/replace")
    public ApiResponse<AdminPhotoView> replace(@PathVariable String assetId,
                                               @RequestParam String operatorId,
                                               @RequestParam MultipartFile file) {
        return ApiResponse.success(photoAdminService.replaceFile(assetId, operatorId, file));
    }

    @PutMapping("/{assetId}/status")
    public ApiResponse<AdminPhotoView> updateStatus(@PathVariable String assetId,
                                                    @RequestBody AdminPhotoStatusRequest request) {
        return ApiResponse.success(photoAdminService.updateStatus(assetId, request));
    }

    @PostMapping("/{assetId}/cover")
    public ApiResponse<AdminPhotoView> setCover(@PathVariable String assetId,
                                                @RequestBody AdminPhotoCoverRequest request) {
        return ApiResponse.success(photoAdminService.setCover(assetId, request.getOperatorId()));
    }
}
