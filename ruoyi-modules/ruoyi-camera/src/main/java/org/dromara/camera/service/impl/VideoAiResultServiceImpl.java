package org.dromara.camera.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.VideoAnalysisResult;
import org.dromara.camera.mapper.CameraManagementMapper;
import org.dromara.camera.service.IVideoAiResultService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * AI检测结果管理Service实现
 * 负责接收Python端回传的AI检测结果并更新数据库记录
 *
 * @author LionLi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoAiResultServiceImpl implements IVideoAiResultService {

    private final CameraManagementMapper baseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnalysisResult(VideoAnalysisResult result) {
        Long videoId = result.getVideoId();
        log.info("开始更新AI分析结果: videoId={}, taskId={}, status={}",
            videoId, result.getTaskId(), result.getStatus());

        CameraManagement camera = baseMapper.selectById(videoId);
        if (camera == null) {
            log.error("视频记录不存在: videoId={}", videoId);
            throw new ServiceException("视频记录不存在: " + videoId);
        }

        CameraManagement update = new CameraManagement();
        update.setVideoId(videoId);

        if (result.isSuccess()) {
            update.setAiCheckStatus(2L);
            update.setAiCheckResult(wrapAsJson(result.getAiDescription()));
            update.setHasViolation(result.hasViolationBehavior() ? 1 : 0);
            update.setViolationType(result.getViolationType());
            update.setViolationStartSecond(result.getViolationStartSecond());
            update.setViolationEndSecond(result.getViolationEndSecond());
            update.setScreenshotUrl(result.getScreenshotUrl());
            update.setProcessTime(result.getProcessTime());
            update.setCheckTime(new Date());

            log.info("AI检测完成: videoId={}, hasViolation={}, violationType={}, violationTime={}-{}s",
                videoId, result.getHasViolation(), result.getViolationType(),
                result.getViolationStartSecond(), result.getViolationEndSecond());
        } else {
            update.setAiCheckStatus(3L);
            update.setAiCheckResult(wrapAsJson("检测失败: " + result.getErrorMessage()));
            update.setCheckTime(new Date());

            log.warn("AI检测失败: videoId={}, error={}", videoId, result.getErrorMessage());
        }

        baseMapper.updateById(update);

        log.info("AI分析结果更新成功: videoId={}, aiCheckStatus={}", videoId, update.getAiCheckStatus());
    }

    @Override
    public void updateAiCheckStatus(Long videoId, Integer status) {
        CameraManagement update = new CameraManagement();
        update.setVideoId(videoId);
        update.setAiCheckStatus(Long.valueOf(status));

        if (status == 1) {
            log.info("更新AI检测状态为检测中: videoId={}", videoId);
        }

        baseMapper.updateById(update);
    }

    /**
     * 将文本包装成JSON格式，兼容数据库JSON类型字段
     */
    private String wrapAsJson(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
            || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return text;
        }
        Map<String, String> wrapper = new HashMap<>();
        wrapper.put("description", text);
        return JsonUtils.toJsonString(wrapper);
    }
}
