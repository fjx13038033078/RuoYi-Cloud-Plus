package org.dromara.camera.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.VideoAnalysisResult;
import org.dromara.camera.service.IVideoAiResultService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 视频检测结果消费者
 * 监听 video.result.queue 队列，接收Python端回传的AI检测结果
 *
 * @author LionLi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoResultConsumer {

    private final IVideoAiResultService videoAiResultService;

    @RabbitListener(queues = "${spring.rabbitmq.video-result.queue:video.result.queue}")
    public void handleVideoResult(VideoAnalysisResult result) {
        String taskId = result.getTaskId();
        Long videoId = result.getVideoId();

        log.info("收到AI检测结果: taskId={}, videoId={}, status={}, hasViolation={}",
            taskId, videoId, result.getStatus(), result.getHasViolation());

        try {
            videoAiResultService.updateAnalysisResult(result);
            log.info("AI检测结果处理成功: taskId={}, videoId={}", taskId, videoId);
        } catch (Exception e) {
            log.error("处理AI检测结果失败: taskId={}, videoId={}, error={}",
                taskId, videoId, e.getMessage(), e);
        }
    }
}
