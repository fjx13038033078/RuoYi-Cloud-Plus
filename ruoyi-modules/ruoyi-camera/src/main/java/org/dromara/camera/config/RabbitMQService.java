package org.dromara.camera.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.VideoUploadMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class RabbitMQService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.video-upload.exchange}")
    private String videoUploadExchange;

    @Value("${spring.rabbitmq.video-upload.routing-key}")
    private String videoUploadRoutingKey;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发送视频上传消息到RabbitMQ（新格式）
     */
    public void sendVideoUploadMessage(VideoUploadMessage message) {
        try {
            // 生成任务ID（如果未提供）
            if (message.getVideoId() == null || message.getVideoId().isEmpty()) {
                message.setVideoId(UUID.randomUUID().toString());
            }

            log.info("发送视频上传消息到RabbitMQ: taskId={}, bucket={}, object={}",
                message.getVideoId(), message.getBucketName(), message.getObjectName());

            rabbitTemplate.convertAndSend(
                videoUploadExchange,
                videoUploadRoutingKey,
                message
            );

            log.info("消息发送成功: taskId={}", message.getVideoId());

        } catch (Exception e) {
            log.error("发送RabbitMQ消息失败: {}", message, e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 发送MinIO URL消息 - 新格式
     * @param minioUrl MinIO地址
     * @param ossId 数据库主键ID
     * @param metadataMap 元数据（应包含videoCode, userId, recordTime等）
     */
    public void sendMinioUrlMessage(String minioUrl, Long ossId, Map<String, Object> metadataMap) {
        try {
            // 解析MinIO URL获取bucket和object
            MinioInfo minioInfo = parseMinioUrl(minioUrl);

            // 提取关键元数据
            String videoCode = extractValue(metadataMap, "videoCode", "UNKNOWN");
            String userId = extractValue(metadataMap, "userId", "0");
            String recordTime = extractValue(metadataMap, "recordTime",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

            // 构建元数据对象
            Map<String, Object> extFields = new HashMap<>(metadataMap);
            extFields.put("minioUrl", minioUrl);
            extFields.put("ossId", ossId);
            extFields.put("sendTime", LocalDateTime.now().toString());

            VideoUploadMessage.Metadata metadata = VideoUploadMessage.Metadata.builder()
                .videoCode(videoCode)
                .userId(userId)
                .recordTime(recordTime)
                .extFields(extFields)
                .build();

            // 生成任务ID
            String taskId = ossId != null ? "OSS-" + ossId : "UUID-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // 构建消息
            VideoUploadMessage message = VideoUploadMessage.builder()
                .videoId(taskId)
                .bucketName(minioInfo.getBucketName())
                .objectName(minioInfo.getObjectName())
                .metadata(metadata)
                .build();

            sendVideoUploadMessage(message);

        } catch (Exception e) {
            log.error("发送MinIO URL消息失败: minioUrl={}, ossId={}", minioUrl, ossId, e);
            throw new RuntimeException("构建消息失败", e);
        }
    }

    /**
     * 发送MinIO URL消息 - 重载方法（兼容旧调用）
     */
    public void sendMinioUrlMessage(String minioUrl, Long ossId, String originalPath,
                                    String fileName, String userName) {
        // 从参数中构建metadataMap
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("originalPath", originalPath);
        metadataMap.put("fileName", fileName);
        metadataMap.put("userName", userName);

        // 尝试从fileName提取videoCode
        if (fileName != null && fileName.contains("_")) {
            String[] parts = fileName.split("_");
            if (parts.length > 0) {
                metadataMap.put("videoCode", parts[0]);
            }
        }

        sendMinioUrlMessage(minioUrl, ossId, metadataMap);
    }

    /**
     * 从Map发送消息（兼容现有代码）
     */
    public void sendMinioUrlFromMap(Map<String, Object> uploadInfo) {
        try {
            String minioUrl = (String) uploadInfo.get("minioUrl");
            Long ossId = null;
            if (uploadInfo.get("ossId") != null) {
                if (uploadInfo.get("ossId") instanceof Number) {
                    ossId = ((Number) uploadInfo.get("ossId")).longValue();
                } else if (uploadInfo.get("ossId") instanceof String) {
                    ossId = Long.parseLong((String) uploadInfo.get("ossId"));
                }
            }

            if (minioUrl != null) {
                // 直接传递整个map作为metadata
                sendMinioUrlMessage(minioUrl, ossId, uploadInfo);
            } else {
                log.warn("minioUrl为空，跳过消息发送");
            }
        } catch (Exception e) {
            log.error("从Map发送消息失败", e);
        }
    }

    /**
     * 解析MinIO URL
     */
    private MinioInfo parseMinioUrl(String minioUrl) {
        try {
            // 示例URL: http://127.0.0.1:9000/zhifajiluyi/2026/01/16/e3626345c5294724ad9666b98ae231f7.mp4
            String path = minioUrl.replaceFirst("http://[^/]+/", "");
            int slashIndex = path.indexOf("/");

            if (slashIndex > 0) {
                String bucketName = path.substring(0, slashIndex);
                String objectName = path.substring(slashIndex + 1);
                return new MinioInfo(bucketName, objectName);
            } else {
                // 如果没有斜杠，整个路径作为objectName
                return new MinioInfo("default", path);
            }
        } catch (Exception e) {
            log.warn("解析MinIO URL失败: {}, 使用默认值", minioUrl, e);
            return new MinioInfo("unknown", "unknown");
        }
    }

    /**
     * 从Map中提取值
     */
    private String extractValue(Map<String, Object> map, String key, String defaultValue) {
        if (map != null && map.containsKey(key) && map.get(key) != null) {
            return map.get(key).toString();
        }
        return defaultValue;
    }

    /**
     * 内部类：MinIO信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class MinioInfo {
        private String bucketName;
        private String objectName;
    }
}
