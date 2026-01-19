package org.dromara.camera.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.VideoUploadMessage;
import org.dromara.camera.service.IVideoMessageService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
            if (message.getTaskId() == null || message.getTaskId().isEmpty()) {
                message.setTaskId(generateTaskId(message.getVideoId()));
            }

            // 设置创建时间
            if (message.getCreateTime() == null) {
                message.setCreateTime(LocalDateTime.now());
            }

            log.info("发送视频检测消息到RabbitMQ: taskId={}, videoId={}, bucket={}, object={}",
                message.getTaskId(), message.getVideoId(),
                message.getBucketName(), message.getObjectName());

            rabbitTemplate.convertAndSend(
                videoUploadExchange,
                videoUploadRoutingKey,
                message
            );

            log.info("消息发送成功: taskId={}, presignedUrl长度={}",
                message.getTaskId(),
                message.getPresignedUrl() != null ? message.getPresignedUrl().length() : 0);

        } catch (Exception e) {
            log.error("发送RabbitMQ消息失败: taskId={}, videoId={}",
                message.getTaskId(), message.getVideoId(), e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    @Override
    public void sendVideoDetectionMessage(Long videoId, String presignedUrl, String bucketName,
                                          String objectName, String originalUrl,
                                          VideoUploadMessage.Metadata metadata) {
        // 生成任务ID
        String taskId = generateTaskId(videoId);

        // 构建消息
        VideoUploadMessage message = VideoUploadMessage.builder()
            .taskId(taskId)
            .videoId(videoId)
            .presignedUrl(presignedUrl)
            .bucketName(bucketName)
            .objectName(objectName)
            .originalUrl(originalUrl)
            .createTime(LocalDateTime.now())
            .metadata(metadata)
            .build();

        sendVideoUploadMessage(message);
    }

    /**
     * 生成任务ID
     *
     * @param videoId 视频ID
     * @return 任务ID
     */
    private String generateTaskId(Long videoId) {
        if (videoId != null) {
            return "VID-" + videoId + "-" + System.currentTimeMillis();
        }
        return "TASK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
