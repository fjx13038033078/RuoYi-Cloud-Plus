package org.dromara.camera.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
     * 从文件路径中提取文件名（兼容 Windows UNC 与 Unix 路径）
     */
    public static String extractFileNameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = normalizePath(path);
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
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
     * 文件名解析结果
     * 文件名格式：{序列号}_{用户编号}_{拍摄时间yyyyMMddHHmmss}_{附加码}
     * 示例：Q541062_63_20250802160820_1000
     */
    @Data
    public static class VideoFileNameInfo {
        /** 视频唯一序列号，如 Q541062 */
        private String serialNumber;
        /** 用户编号，如 63 */
        private String userCode;
        /** 拍摄时间 */
        private Date shootTime;
        /** 原始文件名（不含扩展名） */
        private String rawFileName;
        /** 是否解析成功 */
        private boolean parsed;
    }

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(
        "^([A-Za-z0-9]+)_([A-Za-z0-9]+)_(\\d{14})(?:_([A-Za-z0-9]+))?$"
    );

    private static final String SHOOT_TIME_FORMAT = "yyyyMMddHHmmss";

    /**
     * 解析视频文件名，提取序列号、用户编号、拍摄时间等信息
     *
     * @param fileName 文件名（可带扩展名，如 Q541062_63_20250802160820_1000.mp4）
     * @return 解析结果，解析失败时 parsed 为 false
     */
    public static VideoFileNameInfo parseVideoFileName(String fileName) {
        VideoFileNameInfo info = new VideoFileNameInfo();
        info.setParsed(false);

        if (fileName == null || fileName.isBlank()) {
            return info;
        }

        // 去除扩展名
        String nameWithoutExt = fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
        }
        info.setRawFileName(nameWithoutExt);

        Matcher matcher = FILE_NAME_PATTERN.matcher(nameWithoutExt);
        if (!matcher.matches()) {
            log.warn("文件名不符合解析规则：{}", fileName);
            return info;
        }

        info.setSerialNumber(matcher.group(1));
        info.setUserCode(matcher.group(2));

        String timeStr = matcher.group(3);
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(SHOOT_TIME_FORMAT);
            sdf.setLenient(false);
            info.setShootTime(sdf.parse(timeStr));
        } catch (ParseException e) {
            log.warn("文件名中的拍摄时间解析失败：{}，时间字符串：{}", fileName, timeStr);
            return info;
        }

        info.setParsed(true);
        log.debug("文件名解析成功：{} -> 序列号={}, 用户编号={}, 拍摄时间={}",
            fileName, info.getSerialNumber(), info.getUserCode(), info.getShootTime());

        return info;
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

    // ======================== 视频时长解析 ========================

    private static final Set<String> MP4_MOV_EXTENSIONS = Set.of("mp4", "mov", "m4v", "m4a", "3gp");

    /**
     * 读取视频文件时长（秒），通过解析 MP4/MOV 容器的 moov/mvhd 原子实现，无需外部依赖。
     * 支持 mp4、mov、m4v、3gp 等 ISO Base Media File Format 格式。
     *
     * @param file 视频文件
     * @return 时长秒数，解析失败返回 null
     */
    public static Long getVideoDurationSeconds(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        String ext = getFileExtension(file.getName()).toLowerCase();
        if (!MP4_MOV_EXTENSIONS.contains(ext)) {
            log.debug("非MP4/MOV格式，跳过时长解析：{}", file.getName());
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return findDurationInMp4(raf, 0, raf.length());
        } catch (Exception e) {
            log.warn("读取视频时长失败：{}", file.getName(), e);
            return null;
        }
    }

    /**
     * 递归搜索 MP4 box 树，定位 moov > mvhd 并提取 duration/timescale
     */
    private static Long findDurationInMp4(RandomAccessFile raf, long start, long end) throws IOException {
        long pos = start;
        while (pos < end - 7) {
            raf.seek(pos);
            long size = Integer.toUnsignedLong(raf.readInt());
            byte[] typeBytes = new byte[4];
            raf.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.US_ASCII);

            long headerSize = 8;
            if (size == 1) {
                size = raf.readLong();
                headerSize = 16;
            } else if (size == 0) {
                size = end - pos;
            }

            if (size < headerSize || pos + size > end) {
                break;
            }

            if ("moov".equals(type)) {
                Long result = findDurationInMp4(raf, pos + headerSize, pos + size);
                if (result != null) {
                    return result;
                }
            } else if ("mvhd".equals(type)) {
                return parseMvhdBox(raf, pos + headerSize);
            }
            pos += size;
        }
        return null;
    }

    /**
     * 解析 mvhd box，提取 timescale 和 duration 计算秒数
     */
    private static Long parseMvhdBox(RandomAccessFile raf, long dataStart) throws IOException {
        raf.seek(dataStart);
        int version = raf.readByte() & 0xFF;
        raf.skipBytes(3); // flags

        long timeScale;
        long duration;
        if (version == 0) {
            raf.skipBytes(8); // creation_time(4) + modification_time(4)
            timeScale = Integer.toUnsignedLong(raf.readInt());
            duration = Integer.toUnsignedLong(raf.readInt());
        } else {
            raf.skipBytes(16); // creation_time(8) + modification_time(8)
            timeScale = Integer.toUnsignedLong(raf.readInt());
            duration = raf.readLong();
        }

        if (timeScale > 0) {
            return duration / timeScale;
        }
        return null;
    }

    /**
     * 将秒数格式化为时长显示字符串。
     * 不足 1 小时显示 MM:SS，否则显示 HH:MM:SS
     *
     * @param totalSeconds 总秒数
     * @return 格式化字符串，如 "05:30" 或 "01:23:45"
     */
    public static String formatDuration(long totalSeconds) {
        if (totalSeconds < 0) {
            return "00:00";
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 读取视频文件时长并返回格式化字符串（如 "05:30"）。
     * 组合调用 getVideoDurationSeconds + formatDuration 的便捷方法。
     *
     * @param file 视频文件
     * @return 格式化的时长字符串，解析失败返回 null
     */
    public static String getFormattedDuration(File file) {
        Long seconds = getVideoDurationSeconds(file);
        if (seconds == null) {
            return null;
        }
        return formatDuration(seconds);
    }
}
