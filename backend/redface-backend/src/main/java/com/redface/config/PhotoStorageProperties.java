package com.redface.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * C17 写真上传本地存储配置。
 *
 * <p>P0 使用本地持久化目录 + 静态资源映射；后续可替换为对象存储实现。
 */
@ConfigurationProperties(prefix = "redface.photo-storage")
public class PhotoStorageProperties {

    private String uploadDir = System.getProperty("user.home") + "/redface-uploads/photos";
    private String publicPath = "/uploads/photos/";
    private String publicBaseUrl = "";
    private long maxSizeBytes = 5L * 1024L * 1024L;

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getPublicPath() {
        return publicPath;
    }

    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public String normalizedPublicPath() {
        String value = publicPath == null || publicPath.isBlank() ? "/uploads/photos/" : publicPath.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value;
    }

    public String normalizedResourcePattern() {
        return normalizedPublicPath() + "**";
    }
}
