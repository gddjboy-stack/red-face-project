package com.redface.service;

import com.redface.api.ApiException;
import com.redface.config.PhotoStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * C17 本地写真文件存储实现。
 *
 * <p>安全约束：后端生成落盘文件名；扩展名、声明 MIME、真实文件头三重校验；禁止 SVG。
 */
@Service
public class LocalPhotoStorageService implements PhotoStorageService {

    private static final int CODE_UPLOAD_INVALID = 41701;

    private final PhotoStorageProperties properties;

    public LocalPhotoStorageService(PhotoStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredPhotoFile store(String assetId, MultipartFile file) {
        if (!StringUtils.hasText(assetId)) {
            throw new ApiException(CODE_UPLOAD_INVALID, "assetId不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(CODE_UPLOAD_INVALID, "上传文件不能为空");
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new ApiException(CODE_UPLOAD_INVALID, "图片不能超过5MB");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extractExtension(originalName);
        ensureAllowedExtension(extension);
        ensureAllowedDeclaredMime(file.getContentType());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(CODE_UPLOAD_INVALID, "读取上传文件失败");
        }
        DetectedImage detected = detectImage(bytes);
        if (!detected.extension().equals(extension) && !("jpg".equals(extension) && "jpeg".equals(detected.extension()))) {
            throw new ApiException(CODE_UPLOAD_INVALID, "图片扩展名与真实格式不一致");
        }
        String safeExtension = "jpg".equals(extension) ? "jpeg" : extension;
        String storedFileName = sanitizeAssetId(assetId) + "." + safeExtension;
        Path uploadDir = Path.of(properties.getUploadDir()).toAbsolutePath().normalize();
        Path target = uploadDir.resolve(storedFileName).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new ApiException(CODE_UPLOAD_INVALID, "非法上传路径");
        }
        try {
            Files.createDirectories(uploadDir);
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new ApiException(CODE_UPLOAD_INVALID, "保存上传文件失败");
        }
        return new StoredPhotoFile(assetId, buildPublicUrl(storedFileName), originalName, detected.contentType(), bytes.length);
    }

    private String extractExtension(String originalName) {
        String name = originalName == null ? "" : originalName.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new ApiException(CODE_UPLOAD_INVALID, "图片文件必须包含扩展名");
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void ensureAllowedExtension(String extension) {
        if (!("jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension) || "webp".equals(extension))) {
            throw new ApiException(CODE_UPLOAD_INVALID, "仅支持jpg/png/webp图片");
        }
    }

    private void ensureAllowedDeclaredMime(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!("image/jpeg".equals(normalized) || "image/png".equals(normalized) || "image/webp".equals(normalized))) {
            throw new ApiException(CODE_UPLOAD_INVALID, "图片MIME类型不被允许");
        }
    }

    private DetectedImage detectImage(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return new DetectedImage("jpeg", "image/jpeg");
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return new DetectedImage("png", "image/png");
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return new DetectedImage("webp", "image/webp");
        }
        throw new ApiException(CODE_UPLOAD_INVALID, "文件头校验失败：不是真实jpg/png/webp图片");
    }

    private String sanitizeAssetId(String assetId) {
        return assetId.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String buildPublicUrl(String storedFileName) {
        String path = properties.normalizedPublicPath() + storedFileName;
        String base = properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl().trim();
        if (!base.isEmpty() && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private record DetectedImage(String extension, String contentType) {
    }
}
