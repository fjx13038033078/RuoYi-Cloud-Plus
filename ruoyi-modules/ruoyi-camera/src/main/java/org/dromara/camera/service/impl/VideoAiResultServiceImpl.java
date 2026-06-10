package org.dromara.camera.service.impl;

import cn.hutool.core.lang.Dict;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.VideoAnalysisResult;
import org.dromara.camera.domain.VideoClip;
import org.dromara.camera.mapper.CameraManagementMapper;
import org.dromara.camera.mapper.VideoClipMapper;
import org.dromara.camera.service.IVideoAiResultService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI检测结果管理Service实现
 * 负责接收Python端回传的AI检测结果并更新数据库记录。
 * 切分预处理链路（clipId 非空）：结果先落 video_clip 切片级字段，
 * 该视频全部切片检测完成后聚合写回 camera_management。
 *
 * @author LionLi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoAiResultServiceImpl implements IVideoAiResultService {

    private final CameraManagementMapper baseMapper;
    private final VideoClipMapper videoClipMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnalysisResult(VideoAnalysisResult result) {
        if (result.getClipId() != null) {
            updateClipAnalysisResult(result);
            return;
        }
        updateWholeVideoResult(result);
    }

    /**
     * 整段视频直发链路（演示页/历史消息），保留原逻辑
     */
    private void updateWholeVideoResult(VideoAnalysisResult result) {
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
            update.setEventsJson(result.getEventsJson());
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

    /**
     * 切片级结果落库，全部切片完成后聚合写回原视频记录
     */
    private void updateClipAnalysisResult(VideoAnalysisResult result) {
        Long clipId = result.getClipId();
        Long videoId = result.getVideoId();
        log.info("开始更新切片AI分析结果: videoId={}, clipId={}, taskId={}, status={}",
            videoId, clipId, result.getTaskId(), result.getStatus());

        VideoClip clip = videoClipMapper.selectById(clipId);
        if (clip == null) {
            log.error("切片记录不存在: clipId={}", clipId);
            throw new ServiceException("切片记录不存在: " + clipId);
        }

        VideoClip update = new VideoClip();
        update.setClipId(clipId);
        if (result.isSuccess()) {
            update.setAiCheckStatus(2);
            update.setAiHasViolation(result.hasViolationBehavior() ? 1 : 0);
            update.setAiViolationType(result.getViolationType());
            update.setAiDescription(result.getAiDescription());
            update.setAiEventsJson(result.getEventsJson());
            update.setAiScreenshotUrl(result.getScreenshotUrl());
            update.setAiViolationStartSecond(result.getViolationStartSecond());
            update.setAiViolationEndSecond(result.getViolationEndSecond());
            update.setAiProcessTime(result.getProcessTime());
        } else {
            update.setAiCheckStatus(3);
            update.setAiErrorMessage(result.getErrorMessage());
            update.setAiProcessTime(result.getProcessTime());
        }
        videoClipMapper.updateById(update);
        log.info("切片AI结果落库完成: clipId={}, aiCheckStatus={}", clipId, update.getAiCheckStatus());

        aggregateIfAllClipsDone(videoId);
    }

    /**
     * 检查该视频全部有效切片是否均已检测完成（成功/失败），是则聚合写回 camera_management
     */
    private void aggregateIfAllClipsDone(Long videoId) {
        LambdaQueryWrapper<VideoClip> wrapper = Wrappers.<VideoClip>lambdaQuery()
            .eq(VideoClip::getVideoId, videoId)
            .eq(VideoClip::getClipStatus, 1)
            .orderByAsc(VideoClip::getClipIndex);
        List<VideoClip> clips = videoClipMapper.selectList(wrapper);
        if (clips.isEmpty()) {
            log.warn("聚合检查：未查询到有效切片, videoId={}", videoId);
            return;
        }

        long pending = clips.stream()
            .filter(c -> c.getAiCheckStatus() == null || c.getAiCheckStatus() < 2)
            .count();
        if (pending > 0) {
            log.info("聚合检查：仍有 {} 个切片未完成检测, videoId={}", pending, videoId);
            return;
        }

        List<VideoClip> succeeded = clips.stream()
            .filter(c -> Integer.valueOf(2).equals(c.getAiCheckStatus()))
            .toList();

        CameraManagement update = new CameraManagement();
        update.setVideoId(videoId);
        update.setCheckTime(new Date());

        if (succeeded.isEmpty()) {
            // 全部切片检测失败
            update.setAiCheckStatus(3L);
            String errors = clips.stream()
                .map(VideoClip::getAiErrorMessage)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse("未知错误");
            update.setAiCheckResult(wrapAsJson("检测失败: 全部切片检测失败（" + errors + "）"));
            baseMapper.updateById(update);
            log.warn("聚合完成（全部失败）: videoId={}, clipCount={}", videoId, clips.size());
            return;
        }

        update.setAiCheckStatus(2L);
        update.setEventsJson(mergeEventsJson(succeeded));

        // 违规信息：取时间轴上首个违规切片
        VideoClip firstViolation = succeeded.stream()
            .filter(c -> Integer.valueOf(1).equals(c.getAiHasViolation()))
            .min(Comparator.comparing(VideoClip::getStartSecond,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .orElse(null);

        update.setHasViolation(firstViolation != null ? 1 : 0);
        if (firstViolation != null) {
            update.setViolationType(firstViolation.getAiViolationType());
            update.setViolationStartSecond(firstViolation.getAiViolationStartSecond());
            update.setViolationEndSecond(firstViolation.getAiViolationEndSecond());
            update.setScreenshotUrl(firstViolation.getAiScreenshotUrl());
        }

        // 处理耗时：各切片耗时累加
        double totalProcessTime = succeeded.stream()
            .map(VideoClip::getAiProcessTime)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .sum();
        update.setProcessTime(totalProcessTime > 0 ? totalProcessTime : null);

        update.setAiCheckResult(wrapAsJson(mergeDescriptions(succeeded, clips.size())));

        baseMapper.updateById(update);
        log.info("聚合完成: videoId={}, 切片总数={}, 成功={}, hasViolation={}",
            videoId, clips.size(), succeeded.size(), update.getHasViolation());
    }

    /**
     * 合并各切片事件JSON为单一数组，按事件起始秒排序（时间已由Python偏移到原视频时间轴）
     */
    private String mergeEventsJson(List<VideoClip> clips) {
        List<Dict> allEvents = new ArrayList<>();
        for (VideoClip clip : clips) {
            if (StringUtils.isBlank(clip.getAiEventsJson())) {
                continue;
            }
            try {
                List<Dict> events = JsonUtils.parseArrayMap(clip.getAiEventsJson());
                if (events != null) {
                    allEvents.addAll(events);
                }
            } catch (Exception e) {
                log.warn("解析切片事件JSON失败: clipId={}, error={}", clip.getClipId(), e.getMessage());
            }
        }
        if (allEvents.isEmpty()) {
            return null;
        }
        allEvents.sort(Comparator.comparingDouble(this::extractStartSecond));
        return JsonUtils.toJsonString(allEvents);
    }

    private double extractStartSecond(Dict event) {
        Object value = event.get("start_second");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.MAX_VALUE;
    }

    /**
     * 合并各切片AI描述（带切片时间区间标注）
     */
    private String mergeDescriptions(List<VideoClip> succeeded, int totalClips) {
        List<String> parts = new ArrayList<>();
        for (VideoClip clip : succeeded) {
            if (StringUtils.isBlank(clip.getAiDescription())) {
                continue;
            }
            String range = String.format("【切片%d %s-%s】",
                clip.getClipIndex() == null ? 0 : clip.getClipIndex(),
                formatSecond(clip.getStartSecond()), formatSecond(clip.getEndSecond()));
            parts.add(range + "\n" + clip.getAiDescription());
        }
        if (parts.isEmpty()) {
            return String.format("共 %d 个切片完成AI检测，未发现违规事件", totalClips);
        }
        return String.join("\n\n", parts);
    }

    private String formatSecond(Double second) {
        if (second == null) {
            return "?";
        }
        long total = Math.round(second);
        return String.format("%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60);
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
