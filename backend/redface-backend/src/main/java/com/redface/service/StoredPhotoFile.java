package com.redface.service;

/**
 * C17 本地写真文件存储结果。
 */
public class StoredPhotoFile {
    private final String assetId;
    private final String publicUrl;
    private final String fileName;
    private final String contentType;
    private final long fileSize;

    public StoredPhotoFile(String assetId, String publicUrl, String fileName, String contentType, long fileSize) {
        this.assetId = assetId;
        this.publicUrl = publicUrl;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }
}
