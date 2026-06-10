package org.dromara.camera.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.VideoClip;
import org.dromara.camera.domain.VideoClipResult;
import org.dromara.camera.domain.VideoUploadMessage;
import org.dromara.camera.mapper.CameraManagementMapper;
import org.dromara.camera.mapper.VideoClipMapper;
import org.dromara.camera.service.IVideoMessageService;
import org.dromara.camera.utils.VideoFileUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 视频切割结果消费者
 * 监听 video.clip.result.queue，接收 Python 回传的切割结果并落库。
 * 扫描链路（dataSource=scan）下，切片落库后自动逐片发送 AI 检测任务（切分作为 AI 检测预处理）。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoClipResultConsumer {

    /** AI 检测扫描链路的数据来源标签 */
    private static final String DATA_SOURCE_SCAN = "scan";

    private static final Duration PRESIGN_DURATION = Duration.ofDays(7);

    private final VideoClipMapper videoClipMapper;
    private final CameraManagementMapper cameraManagementMapper;
    private final IVideoMessageService videoMessageService;

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
        CameraManagement camera = cameraManagementMapper.selectById(result.getVideoId());
        boolean scanChain = isScanChain(camera);

        List<VideoClipResult.ClipInfo> clips = result.getClips();
        if (clips == null || clips.isEmpty()) {
            log.info("切割结果无切片（视频中未检测到人体）: taskId={}", result.getTaskId());
            if (scanChain) {
                // 无人体片段，无需AI检测，直接置为已完成、无违规
                completeWithoutClips(result.getVideoId());
            }
            return;
        }

        String sourceFileName = resolveSourceFileName(camera, result.getVideoId());
        int sourceClipCount = clips.size();

        List<VideoClip> insertedClips = new ArrayList<>(sourceClipCount);
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
            if (scanChain) {
                // 待事务提交后逐片发送 AI 任务，先标记为检测中
                entity.setAiCheckStatus(1);
            }
            videoClipMapper.insert(entity);
            insertedClips.add(entity);
        }
        log.info("切割结果落库完成: taskId={}, videoId={}, sourceFile={}, clipCount={}, scanChain={}",
            result.getTaskId(), result.getVideoId(), sourceFileName, sourceClipCount, scanChain);

        if (scanChain) {
            dispatchAiTasksAfterCommit(camera, insertedClips, sourceFileName);
        }
    }

    private void handleFailure(VideoClipResult result) {
        CameraManagement camera = cameraManagementMapper.selectById(result.getVideoId());
        VideoClip entity = buildBaseEntity(result, resolveSourceFileName(camera, result.getVideoId()), 0);
        entity.setClipIndex(0);
        entity.setClipStatus(2);
        entity.setErrorMessage(result.getErrorMessage());
        videoClipMapper.insert(entity);
        log.warn("切割任务失败，记录错误: taskId={}, error={}", result.getTaskId(), result.getErrorMessage());

        if (isScanChain(camera)) {
            // 切割预处理失败 → 原视频 AI 检测状态置为失败
            CameraManagement update = new CameraManagement();
            update.setVideoId(result.getVideoId());
            update.setAiCheckStatus(3L);
            update.setAiCheckResult(JsonUtils.toJsonString(
                Map.of("description", "切割预处理失败: " + StringUtils.defaultString(result.getErrorMessage()))));
            update.setCheckTime(new Date());
            cameraManagementMapper.updateById(update);
            log.warn("已将原视频AI检测状态置为失败: videoId={}", result.getVideoId());
        }
    }

    /**
     * 无切片场景：直接将原视频标记为检测完成、无违规
     */
    private void completeWithoutClips(Long videoId) {
        CameraManagement update = new CameraManagement();
        update.setVideoId(videoId);
        update.setAiCheckStatus(2L);
        update.setHasViolation(0);
        update.setAiCheckResult(JsonUtils.toJsonString(
            Map.of("description", "视频中未检测到人体片段，无需AI检测")));
        update.setCheckTime(new Date());
        cameraManagementMapper.updateById(update);
        log.info("无切片，原视频直接标记为检测完成: videoId={}", videoId);
    }

    /**
     * 事务提交后逐片发送 AI 检测任务（避免结果先于切片记录到达）
     */
    private void dispatchAiTasksAfterCommit(CameraManagement camera, List<VideoClip> clips, String sourceFileName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchAiTasks(camera, clips, sourceFileName);
                }
            });
        } else {
            dispatchAiTasks(camera, clips, sourceFileName);
        }
    }

    private void dispatchAiTasks(CameraManagement camera, List<VideoClip> clips, String sourceFileName) {
        OssClient ossClient = null;
        try {
            ossClient = OssFactory.instance();
        } catch (Exception e) {
            log.warn("获取OssClient失败，将直接使用切割结果中的预签名URL: {}", e.getMessage());
        }

        for (VideoClip clip : clips) {
            try {
                String presignedUrl = resolvePresignedUrl(ossClient, clip);
                if (StringUtils.isBlank(presignedUrl)) {
                    log.error("切片预签名URL为空，跳过AI任务: clipId={}, objectName={}",
                        clip.getClipId(), clip.getObjectName());
                    continue;
                }

                VideoUploadMessage.Metadata metadata = VideoUploadMessage.Metadata.builder()
                    .fileName(sourceFileName)
                    .userName(camera != null ? camera.getUserName() : null)
                    .fileSize(clip.getFileSize())
                    .build();

                videoMessageService.sendClipDetectionMessage(
                    clip.getVideoId(), clip.getClipId(), clip.getStartSecond(),
                    presignedUrl, VideoFileUtils.extractBucketName(presignedUrl),
                    clip.getObjectName(), clip.getUrl(), metadata);

                log.info("切片AI检测任务已发送: videoId={}, clipId={}, clipIndex={}, startSecond={}",
                    clip.getVideoId(), clip.getClipId(), clip.getClipIndex(), clip.getStartSecond());
            } catch (Exception e) {
                log.error("发送切片AI检测任务失败: videoId={}, clipId={}", clip.getVideoId(), clip.getClipId(), e);
            }
        }
    }

    /**
     * 优先重新生成 7 天预签名URL；失败则回退使用 Python 回传的 URL
     */
    private String resolvePresignedUrl(OssClient ossClient, VideoClip clip) {
        if (ossClient != null && StringUtils.isNotBlank(clip.getObjectName())) {
            try {
                return ossClient.getPrivateUrl(clip.getObjectName(), PRESIGN_DURATION);
            } catch (Exception e) {
                log.warn("生成切片预签名URL失败，回退使用回传URL: objectName={}, error={}",
                    clip.getObjectName(), e.getMessage());
            }
        }
        return clip.getUrl();
    }

    private boolean isScanChain(CameraManagement camera) {
        return camera != null && DATA_SOURCE_SCAN.equalsIgnoreCase(camera.getDataSource());
    }

    private VideoClip buildBaseEntity(VideoClipResult result, String sourceFileName, int sourceClipCount) {
        VideoClip entity = new VideoClip();
        entity.setTaskId(result.getTaskId());
        entity.setVideoId(result.getVideoId());
        entity.setSourceFileName(sourceFileName);
        entity.setSourceClipCount(sourceClipCount);
        return entity;
    }

    private String resolveSourceFileName(CameraManagement camera, Long videoId) {
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
