package org.dromara.camera.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.VideoUploadMessage;
import org.dromara.camera.service.IVideoMessageService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 视频消息服务实现类
 * 用于发送视频上传消息到RabbitMQ
 *
 * @author LionLi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoMessageServiceImpl implements IVideoMessageService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.video-upload.exchange}")
    private String videoUploadExchange;

    @Value("${spring.rabbitmq.video-upload.routing-key}")
    private String videoUploadRoutingKey;

    @Override
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

    @Override
    public void sendMinioUrlMessage(String minioUrl, Long ossId, Map<String, Object> metadataMap) {
        try {
            // 解析MinIO URL获取bucket和object
            MinioUrlInfo minioInfo = parseMinioUrl(minioUrl);

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
            String taskId = ossId != null
                ? "OSS-" + ossId
                : "UUID-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // 构建消息
            VideoUploadMessage message = VideoUploadMessage.builder()
                .videoId(taskId)
                .bucketName(minioInfo.bucketName())
                .objectName(minioInfo.objectName())
                .metadata(metadata)
                .build();

            sendVideoUploadMessage(message);

        } catch (Exception e) {
            log.error("发送MinIO URL消息失败: minioUrl={}, ossId={}", minioUrl, ossId, e);
            throw new RuntimeException("构建消息失败", e);
        }
    }

    @Override
    public void sendMinioUrlMessage(String minioUrl, Long ossId, String originalPath,
                                    String fileName, String userName) {
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
     * 解析MinIO URL
     *
     * @param minioUrl MinIO URL
     * @return MinIO信息
     */
    private MinioUrlInfo parseMinioUrl(String minioUrl) {
        try {
            // 示例URL: http://127.0.0.1:9000/zhifajiluyi/2026/01/16/xxx.mp4
            String path = minioUrl.replaceFirst("https?://[^/]+/", "");
            int slashIndex = path.indexOf("/");

            if (slashIndex > 0) {
                String bucketName = path.substring(0, slashIndex);
                String objectName = path.substring(slashIndex + 1);
                return new MinioUrlInfo(bucketName, objectName);
            } else {
                return new MinioUrlInfo("default", path);
            }
        } catch (Exception e) {
            log.warn("解析MinIO URL失败: {}, 使用默认值", minioUrl, e);
            return new MinioUrlInfo("unknown", "unknown");
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
     * MinIO URL信息记录类
     */
    private record MinioUrlInfo(String bucketName, String objectName) {
    }
}
