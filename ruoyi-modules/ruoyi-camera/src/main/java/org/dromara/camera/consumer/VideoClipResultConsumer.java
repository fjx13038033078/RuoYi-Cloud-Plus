package org.dromara.camera.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.VideoClip;
import org.dromara.camera.domain.VideoClipResult;
import org.dromara.camera.mapper.CameraManagementMapper;
import org.dromara.camera.mapper.VideoClipMapper;
import org.dromara.camera.utils.VideoFileUtils;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 视频切割结果消费者
 * 监听 video.clip.result.queue，接收 Python 回传的切割结果并落库
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoClipResultConsumer {

    private final VideoClipMapper videoClipMapper;
    private final CameraManagementMapper cameraManagementMapper;

    @RabbitListener(queues = "${spring.rabbitmq.video-clip-result.queue:video.clip.result.queue}")
    @Transactional(rollbackFor = Exception.class)
    public void handleClipResult(VideoClipResult result) {
        String taskId = result.getTaskId();
        Long videoId = result.getVideoId();

        log.info("收到切割结果: taskId={}, videoId={}, status={}, clips={}",
            taskId, videoId, result.getStatus(),
            result.getClips() == null ? 0 : result.getClips().size());

        try {
            if (result.isSuccess()) {
                handleSuccess(result);
            } else {
                handleFailure(result);
            }
        } catch (Exception e) {
            log.error("处理切割结果失败: taskId={}, videoId={}", taskId, videoId, e);
            throw e;
        }
    }

    private void handleSuccess(VideoClipResult result) {
        List<VideoClipResult.ClipInfo> clips = result.getClips();
        if (clips == null || clips.isEmpty()) {
            log.info("切割结果无切片（视频中未检测到人体）: taskId={}", result.getTaskId());
            return;
        }

        String sourceFileName = resolveSourceFileName(result.getVideoId());
        int sourceClipCount = clips.size();

        for (VideoClipResult.ClipInfo info : clips) {
            VideoClip entity = buildBaseEntity(result, sourceFileName, sourceClipCount);
            entity.setClipIndex(info.getClipIndex());
            entity.setObjectName(info.getObjectName());
            entity.setUrl(info.getUrl());
            entity.setStartSecond(info.getStartSecond());
            entity.setEndSecond(info.getEndSecond());
            entity.setDurationSeconds(info.getDurationSeconds());
            entity.setFileSize(info.getFileSize());
            entity.setClipStatus(1);
            videoClipMapper.insert(entity);
        }
        log.info("切割结果落库完成: taskId={}, videoId={}, sourceFile={}, clipCount={}",
            result.getTaskId(), result.getVideoId(), sourceFileName, sourceClipCount);
    }

    private void handleFailure(VideoClipResult result) {
        VideoClip entity = buildBaseEntity(result, resolveSourceFileName(result.getVideoId()), 0);
        entity.setClipIndex(0);
        entity.setClipStatus(2);
        entity.setErrorMessage(result.getErrorMessage());
        videoClipMapper.insert(entity);
        log.warn("切割任务失败，记录错误: taskId={}, error={}", result.getTaskId(), result.getErrorMessage());
    }

    private VideoClip buildBaseEntity(VideoClipResult result, String sourceFileName, int sourceClipCount) {
        VideoClip entity = new VideoClip();
        entity.setTaskId(result.getTaskId());
        entity.setVideoId(result.getVideoId());
        entity.setSourceFileName(sourceFileName);
        entity.setSourceClipCount(sourceClipCount);
        return entity;
    }

    private String resolveSourceFileName(Long videoId) {
        CameraManagement camera = cameraManagementMapper.selectById(videoId);
        if (camera == null) {
            return "";
        }
        String fileName = VideoFileUtils.extractFileNameFromPath(camera.getStorageLocation());
        if (StringUtils.isNotBlank(fileName)) {
            return fileName;
        }
        if (StringUtils.isNotBlank(camera.getSerialNumber())) {
            return camera.getSerialNumber();
        }
        return "video-" + videoId;
    }
}
