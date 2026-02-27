package org.dromara.camera.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * 视频文件相关工具类
 *
 * @author LionLi
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VideoFileUtils {

    private static final Map<String, String> VIDEO_CONTENT_TYPES = Map.ofEntries(
        entry(".mp4", "video/mp4"),
        entry(".avi", "video/x-msvideo"),
        entry(".mov", "video/quicktime"),
        entry(".mkv", "video/x-matroska"),
        entry(".wmv", "video/x-ms-wmv"),
        entry(".flv", "video/x-flv"),
        entry(".mpeg", "video/mpeg"),
        entry(".mpg", "video/mpeg"),
        entry(".webm", "video/webm"),
        entry(".3gp", "video/3gpp")
    );

    /**
     * 规范化文件路径，将反斜杠统一为正斜杠并去除首尾空格
     */
    public static String normalizePath(String path) {
        return Optional.ofNullable(path)
            .map(p -> p.replace('\\', '/').trim())
            .orElse(path);
    }

    /**
     * 探测文件 Content-Type，优先按扩展名匹配，否则使用系统探测
     */
    public static String detectContentType(File file) {
        String fileName = file.getName().toLowerCase();

        return VIDEO_CONTENT_TYPES.entrySet().stream()
            .filter(e -> fileName.endsWith(e.getKey()))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElseGet(() -> probeFileContentType(file));
    }

    private static String probeFileContentType(File file) {
        try {
            String contentType = Files.probeContentType(file.toPath());
            return (contentType != null && contentType.startsWith("video/")) ?
                contentType : "video/*";
        } catch (IOException e) {
            log.warn("文件类型探测失败：{}", file.getName(), e);
            return "video/*";
        }
    }

    /**
     * 提取文件后缀（带点号），如 ".mp4"
     */
    public static String extractFileSuffix(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex) : "";
    }

    /**
     * 提取文件后缀（不带点号），如 "mp4"，最大长度 10
     */
    public static String extractFileSuffixWithoutDot(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex <= 0) return "";
        String suffix = name.substring(dotIndex + 1);
        return suffix.length() > 10 ? suffix.substring(0, 10) : suffix;
    }

    /**
     * 获取文件扩展名（不含点号）
     */
    public static String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastDotIndex = filename.lastIndexOf(".");
        return lastDotIndex > 0 && lastDotIndex < filename.length() - 1 ?
            filename.substring(lastDotIndex + 1) : "";
    }

    /**
     * 格式化文件大小为可读字符串
     */
    public static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * 从 URL 中提取存储桶名称
     */
    public static String extractBucketName(String url) {
        try {
            String path = url.replaceFirst("https?://[^/]+/", "");
            int slashIndex = path.indexOf("/");
            return slashIndex > 0 ? path.substring(0, slashIndex) : path;
        } catch (Exception e) {
            log.warn("提取存储桶名称失败: {}", url, e);
            return "unknown";
        }
    }

    /**
     * 从文件名中提取视频编码（取第一个下划线之前的部分）
     */
    public static String extractVideoCode(String fileName) {
        if (fileName != null && fileName.contains("_")) {
            return fileName.split("_")[0];
        }
        return fileName;
    }

    /**
     * 从文件路径中提取用户名，支持多种目录模式
     */
    public static String extractUserNameFromPath(String filePath) {
        String normalizedPath = normalizePath(filePath);

        String[] patterns = {
            "执法记录仪/[^/]+/([^/0-9.]+)[0-9.]*/",
            "/([^/0-9._-]+)[0-9._-]*/[^/]+\\.[a-zA-Z0-9]+$",
            "/([^/]+)/[^/]+\\.[a-zA-Z0-9]+$"
        };

        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(normalizedPath);
            if (m.find() && m.groupCount() > 0) {
                return m.group(1);
            }
        }

        return "userName";
    }

    /**
     * 获取当前用户ID（占位实现）
     */
    public static Long getCurrentUserId() {
        try {
            return 1L;
        } catch (Exception e) {
            return 1L;
        }
    }

    /**
     * 获取当前部门ID（占位实现）
     */
    public static Long getCurrentDeptId() {
        try {
            return 100L;
        } catch (Exception e) {
            return 100L;
        }
    }
}
