package com.redface.service;

import com.redface.api.ApiException;
import com.redface.dto.AdminPhotoStatusRequest;
import com.redface.dto.AdminPhotoUpdateRequest;
import com.redface.dto.AdminPhotoView;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.PhotoAssetMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * C17 后台写真资产管理服务。
 */
@Service
public class PhotoAdminService {

    private static final int CODE_NOT_FOUND = 41704;
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PhotoAssetMapper photoAssetMapper;
    private final PhotoStorageService photoStorageService;
    private final OperationsLogMapper operationsLogMapper;

    public PhotoAdminService(PhotoAssetMapper photoAssetMapper,
                             PhotoStorageService photoStorageService,
                             OperationsLogMapper operationsLogMapper) {
        this.photoAssetMapper = photoAssetMapper;
        this.photoStorageService = photoStorageService;
        this.operationsLogMapper = operationsLogMapper;
    }

    public List<AdminPhotoView> listPhotos(Integer playerId, String status) {
        return photoAssetMapper.findPhotos(playerId, normalizeOptionalStatus(status));
    }

    @Transactional
    public AdminPhotoView upload(String operatorId, int playerId, boolean isCover, int sortOrder, MultipartFile file) {
        validateOperator(operatorId);
        validatePlayer(playerId);
        String assetId = generateAssetId(playerId);
        StoredPhotoFile stored = photoStorageService.store(assetId, file);
        if (isCover) {
            photoAssetMapper.clearOtherCovers(playerId, assetId);
        }
        photoAssetMapper.insertPhoto(assetId, playerId, stored.getPublicUrl(), null, "active", isCover, safeSort(sortOrder),
                stored.getFileName(), stored.getContentType(), stored.getFileSize());
        writeLog(operatorId, "photo_upload", "photo:" + assetId,
                "{\"playerId\":" + playerId + ",\"isCover\":" + isCover + "}", "上传写真");
        return requirePhoto(assetId);
    }

    @Transactional
    public AdminPhotoView update(String assetId, AdminPhotoUpdateRequest request) {
        validateOperator(request.getOperatorId());
        AdminPhotoView existing = requirePhoto(assetId);
        int playerId = request.getPlayerId() == null ? existing.getPlayerId() : request.getPlayerId();
        validatePlayer(playerId);
        String status = normalizeStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        boolean isCover = Boolean.TRUE.equals(request.getIsCover());
        if (request.getIsCover() == null) {
            isCover = Boolean.TRUE.equals(existing.getIsCover());
        }
        if ("inactive".equals(status)) {
            isCover = false;
        }
        if (isCover) {
            photoAssetMapper.clearOtherCovers(playerId, assetId);
        }
        int sortOrder = request.getSortOrder() == null ? safeSort(existing.getSortOrder()) : safeSort(request.getSortOrder());
        String downloadUrl = request.getDownloadUrl() == null ? existing.getDownloadUrl() : normalizeNullableText(request.getDownloadUrl());
        photoAssetMapper.updateMetadata(assetId, playerId, status, isCover, sortOrder, downloadUrl);
        writeLog(request.getOperatorId(), "photo_update", "photo:" + assetId,
                "{\"playerId\":" + playerId + ",\"status\":\"" + status + "\",\"isCover\":" + isCover + "}", "更新写真元数据");
        return requirePhoto(assetId);
    }

    @Transactional
    public AdminPhotoView replaceFile(String assetId, String operatorId, MultipartFile file) {
        validateOperator(operatorId);
        AdminPhotoView existing = requirePhoto(assetId);
        StoredPhotoFile stored = photoStorageService.store(assetId, file);
        photoAssetMapper.updateFile(assetId, stored.getPublicUrl(), stored.getFileName(), stored.getContentType(), stored.getFileSize());
        writeLog(operatorId, "photo_replace", "photo:" + assetId,
                "{\"playerId\":" + existing.getPlayerId() + "}", "替换写真文件");
        return requirePhoto(assetId);
    }

    @Transactional
    public AdminPhotoView updateStatus(String assetId, AdminPhotoStatusRequest request) {
        validateOperator(request.getOperatorId());
        requirePhoto(assetId);
        String status = normalizeStatus(request.getStatus());
        photoAssetMapper.updateStatus(assetId, status);
        writeLog(request.getOperatorId(), "photo_status", "photo:" + assetId,
                "{\"status\":\"" + status + "\"}", "更新写真状态");
        return requirePhoto(assetId);
    }

    @Transactional
    public AdminPhotoView setCover(String assetId, String operatorId) {
        validateOperator(operatorId);
        AdminPhotoView existing = requirePhoto(assetId);
        photoAssetMapper.clearOtherCovers(existing.getPlayerId(), assetId);
        photoAssetMapper.setCover(assetId);
        writeLog(operatorId, "photo_set_cover", "photo:" + assetId,
                "{\"playerId\":" + existing.getPlayerId() + "}", "设为写真封面");
        return requirePhoto(assetId);
    }

    private AdminPhotoView requirePhoto(String assetId) {
        if (!StringUtils.hasText(assetId)) {
            throw new ApiException(CODE_NOT_FOUND, "assetId不能为空");
        }
        AdminPhotoView view = photoAssetMapper.findByAssetId(assetId);
        if (view == null) {
            throw new ApiException(CODE_NOT_FOUND, "写真资产不存在");
        }
        return view;
    }

    private void validateOperator(String operatorId) {
        if (!StringUtils.hasText(operatorId)) {
            throw new IllegalArgumentException("operatorId不能为空");
        }
    }

    private void validatePlayer(int playerId) {
        if (playerId <= 0 || !StringUtils.hasText(photoAssetMapper.findActivePlayerName(playerId))) {
            throw new ApiException(CODE_NOT_FOUND, "选手不存在或已停用");
        }
    }

    private String normalizeOptionalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return normalizeStatus(status);
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (!"active".equals(value) && !"inactive".equals(value)) {
            throw new IllegalArgumentException("写真状态必须为active或inactive");
        }
        return value;
    }

    private int safeSort(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String generateAssetId(int playerId) {
        return "photo_" + playerId + "_" + LocalDateTime.now().format(ID_TIME) + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void writeLog(String operatorId, String actionType, String target, String detail, String reason) {
        operationsLogMapper.insert(operatorId, actionType, target, detail, reason);
    }
}
