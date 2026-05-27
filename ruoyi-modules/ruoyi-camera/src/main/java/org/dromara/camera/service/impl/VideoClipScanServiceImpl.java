package org.dromara.camera.service.impl;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.mapper.CameraManagementMapper;
import org.dromara.camera.service.IVideoClipMessageService;
import org.dromara.camera.service.IVideoClipScanService;
import org.dromara.camera.service.IVideoScanUploadService;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.resource.api.RemoteFileService;
import org.dromara.resource.api.domain.RemoteFile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 视频切割扫描服务实现
 *
 * @author system
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoClipScanServiceImpl implements IVideoClipScanService {

    /** 切割任务定时扫描使用的数据来源标签，对应字典 camera_data_source */
    private static final String DATA_SOURCE_CLIP = "clip";

    private static final Duration PRESIGN_DURATION = Duration.ofHours(24);

    private final IVideoScanUploadService videoScanUploadService;
    private final IVideoClipMessageService videoClipMessageService;
    private final CameraManagementMapper cameraManagementMapper;

    @DubboReference
    private RemoteFileService remoteFileService;

    @Override
    public int scanAndSubmitClipTasks(String folderPath) {
        log.info("[ClipScan] 开始扫描切割目录: {}", folderPath);

        // 1. 扫描入库（关闭 AI MQ 触发）
        List<CameraManagement> inserted = videoScanUploadService.scanInsertFromFolder(folderPath, false);
        if (CollUtil.isEmpty(inserted)) {
            log.info("[ClipScan] 无新增视频，结束");
            return 0;
        }

        int submitted = 0;
        for (CameraManagement camera : inserted) {
            try {
                // 2. 标记 dataSource=clip，便于在检测记录页区分
                CameraManagement update = new CameraManagement();
                update.setVideoId(camera.getVideoId());
                update.setDataSource(DATA_SOURCE_CLIP);
                cameraManagementMapper.updateById(update);

                // 3. 生成预签名 URL 并发起切割 MQ
                String presignedUrl = generatePresignedUrl(camera);
                if (presignedUrl == null) {
                    log.warn("[ClipScan] 跳过切割：无法生成预签名URL, videoId={}", camera.getVideoId());
                    continue;
                }

                String taskId = videoClipMessageService.sendClipTask(camera.getVideoId(), presignedUrl);
                log.info("[ClipScan] 切割任务已提交: videoId={}, taskId={}", camera.getVideoId(), taskId);
                submitted++;
            } catch (Exception e) {
                log.error("[ClipScan] 提交切割任务失败: videoId={}", camera.getVideoId(), e);
            }
        }

        log.info("[ClipScan] 完成: 新增视频={}, 切割任务提交成功={}", inserted.size(), submitted);
        return submitted;
    }

    /**
     * 通过 ossId → sys_oss URL → 预签名 URL
     */
    private String generatePresignedUrl(CameraManagement camera) {
        Long ossId = camera.getOssId();
        if (ossId == null) {
            return null;
        }
        List<RemoteFile> files = remoteFileService.selectByIds(String.valueOf(ossId));
        if (CollUtil.isEmpty(files)) {
            return null;
        }
        String originalUrl = files.get(0).getUrl();
        OssClient ossClient = OssFactory.instance();
        String objectName = ossClient.removeBaseUrl(originalUrl);
        return ossClient.getPrivateUrl(objectName, PRESIGN_DURATION);
    }
}
