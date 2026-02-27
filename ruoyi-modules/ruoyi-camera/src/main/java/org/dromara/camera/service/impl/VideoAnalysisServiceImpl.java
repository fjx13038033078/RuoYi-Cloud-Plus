package org.dromara.camera.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.client.VideoAnalysisClient;
import org.dromara.camera.service.IVideoAnalysisService;
import org.dromara.camera.utils.VideoFileUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 视频分析Service实现
 * 负责上传视频文件并调用外部AI服务进行分析，含重试机制
 *
 * @author LionLi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoAnalysisServiceImpl implements IVideoAnalysisService {

    private final VideoAnalysisClient videoAnalysisClient;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("mp4", "avi", "mov", "wmv", "flv", "mkv");
    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> analyzeVideo(MultipartFile file) {
        validateVideoFile(file);

        int maxRetries = calculateRetryCount(file.getSize());
        long timeout = calculateTimeout(file.getSize());

        log.info("开始分析视频: {}, 大小: {} MB, 最大重试次数: {}, 超时时间: {}秒",
            file.getOriginalFilename(),
            String.format("%.2f", file.getSize() / (1024.0 * 1024.0)),
            maxRetries, timeout / 1000);

        Exception lastException = null;

        for (int retry = 0; retry <= maxRetries; retry++) {
            try {
                if (retry > 0) {
                    log.info("第 {} 次重试分析视频", retry);
                    Thread.sleep(2000);
                }

                String jsonResponse = videoAnalysisClient.analyzeVideoRaw(file);

                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(jsonResponse, Map.class);

                addFileInfo(result, file);

                log.info("视频分析成功: {}", file.getOriginalFilename());
                return result;

            } catch (SocketTimeoutException e) {
                lastException = e;
                log.warn("第 {} 次尝试超时，视频大小: {} MB",
                    retry + 1, String.format("%.2f", file.getSize() / (1024.0 * 1024.0)));
                if (retry == maxRetries) {
                    throw new RuntimeException("视频分析超时，请尝试上传较小文件或稍后重试");
                }
            } catch (IOException e) {
                lastException = e;
                if (retry == maxRetries) {
                    throw new RuntimeException("视频分析失败: " + e.getMessage());
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("视频分析失败: " + e.getMessage());
            }
        }

        throw new RuntimeException("视频分析失败: " + (lastException != null ? lastException.getMessage() : "未知错误"));
    }

    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的视频文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String extension = VideoFileUtils.getFileExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件格式: " + extension);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过500MB");
        }
        log.info("视频文件验证通过: {}, 大小: {} MB",
            filename, String.format("%.2f", file.getSize() / (1024.0 * 1024.0)));
    }

    private int calculateRetryCount(long fileSize) {
        if (fileSize > 100 * 1024 * 1024) return 3;
        if (fileSize > 50 * 1024 * 1024) return 2;
        return 1;
    }

    private long calculateTimeout(long fileSize) {
        long baseTimeout = 60000;
        long additionalTimeout = fileSize / (1024 * 1024) * 1000;
        return Math.min(baseTimeout + additionalTimeout, 600000);
    }

    private void addFileInfo(Map<String, Object> result, MultipartFile file) {
        result.put("upload_file_name", file.getOriginalFilename());
        result.put("upload_file_size", VideoFileUtils.formatFileSize(file.getSize()));
        result.put("upload_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("file_format", VideoFileUtils.getFileExtension(file.getOriginalFilename()));
        result.put("file_size_bytes", file.getSize());
    }
}
