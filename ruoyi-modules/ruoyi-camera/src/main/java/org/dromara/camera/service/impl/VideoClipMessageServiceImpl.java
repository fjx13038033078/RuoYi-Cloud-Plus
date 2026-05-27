package org.dromara.camera.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.VideoClipTaskMessage;
import org.dromara.camera.service.IVideoClipMessageService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 视频切割消息服务实现
 *
 * @author system
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoClipMessageServiceImpl implements IVideoClipMessageService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.video-clip.exchange:video.clip.exchange}")
    private String clipExchange;

    @Value("${spring.rabbitmq.video-clip.routing-key:video.clip.task}")
    private String clipRoutingKey;

    @Override
    public String sendClipTask(Long videoId, String presignedUrl) {
        String taskId = "CLIP-" + videoId + "-" + System.currentTimeMillis();

        VideoClipTaskMessage message = VideoClipTaskMessage.builder()
            .taskId(taskId)
            .videoId(videoId)
            .presignedUrl(presignedUrl)
            .minSegmentDuration(3.0)
            .vidStride(3)
            .conf(0.5)
            .build();

        try {
            rabbitTemplate.convertAndSend(clipExchange, clipRoutingKey, message);
            log.info("切割任务消息已发送: taskId={}, videoId={}", taskId, videoId);
            return taskId;
        } catch (Exception e) {
            log.error("切割任务消息发送失败: videoId={}", videoId, e);
            throw new RuntimeException("切割任务消息发送失败: " + e.getMessage(), e);
        }
    }
}
