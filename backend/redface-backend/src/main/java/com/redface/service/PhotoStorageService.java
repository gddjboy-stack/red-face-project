package com.redface.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * C17 写真文件存储抽象。P0 使用本地实现，后续可迁移对象存储。
 */
public interface PhotoStorageService {
    StoredPhotoFile store(String assetId, MultipartFile file);
}
