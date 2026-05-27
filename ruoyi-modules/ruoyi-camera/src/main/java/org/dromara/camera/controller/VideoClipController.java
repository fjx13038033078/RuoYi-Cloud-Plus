package org.dromara.camera.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.dromara.camera.domain.bo.VideoClipQueryBo;
import org.dromara.camera.domain.vo.VideoClipVo;
import org.dromara.camera.service.ICameraManagementService;
import org.dromara.camera.service.IVideoClipMessageService;
import org.dromara.camera.service.IVideoClipService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.web.core.BaseController;
import org.dromara.resource.api.RemoteFileService;
import org.dromara.resource.api.domain.RemoteFile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 视频切割管理
 *
 * @author system
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/clip")
public class VideoClipController extends BaseController {

    private final IVideoClipService videoClipService;
    private final IVideoClipMessageService videoClipMessageService;
    private final ICameraManagementService cameraManagementService;

    @DubboReference
    private RemoteFileService remoteFileService;

    private static final Duration PRESIGN_DURATION = Duration.ofHours(24);

    /**
     * 分页查询切片列表
     */
    @SaCheckPermission("camera:videoClip:query")
    @GetMapping("/list")
    public TableDataInfo<VideoClipVo> list(VideoClipQueryBo bo, PageQuery pageQuery) {
        return videoClipService.queryPageList(bo, pageQuery);
    }

    /**
     * 根据视频ID查询其所有切片
     */
    @SaCheckPermission("camera:videoClip:query")
    @GetMapping("/byVideo/{videoId}")
    public R<List<VideoClipVo>> listByVideoId(
        @NotNull(message = "视频ID不能为空") @PathVariable Long videoId) {
        return R.ok(videoClipService.queryByVideoId(videoId));
    }

    /**
     * 发起视频切割任务
     *
     * @param videoId 视频ID
     * @return 任务ID
     */
    @SaCheckPermission("camera:videoClip:submit")
    @Log(title = "视频切割", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/submit/{videoId}")
    public R<Map<String, String>> submitClipTask(
        @NotNull(message = "视频ID不能为空") @PathVariable Long videoId) {

        // 生成预签名URL
        String presignedUrl;
        try {
            presignedUrl = generatePresignedUrl(videoId);
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }

        String taskId = videoClipMessageService.sendClipTask(videoId, presignedUrl);
        return R.ok("切割任务已提交", Map.of("taskId", taskId));
    }

    /**
     * 刷新切片的预签名 URL（URL 过期后使用）
     */
    @SaCheckPermission("camera:videoClip:query")
    @GetMapping("/refreshUrl/{clipId}")
    public R<VideoClipVo> refreshUrl(
        @NotNull(message = "切片ID不能为空") @PathVariable Long clipId) {
        return R.ok(videoClipService.refreshUrl(clipId));
    }

    /**
     * 删除切片
     */
    @SaCheckPermission("camera:videoClip:remove")
    @Log(title = "视频切割", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    public R<Void> remove(
        @NotEmpty(message = "主键不能为空") @RequestBody Long[] clipIds) {
        return toAjax(videoClipService.deleteByIds(List.of(clipIds)));
    }

    /**
     * 根据任务ID查询切片列表
     */
    @SaCheckPermission("camera:videoClip:query")
    @GetMapping("/byTask/{taskId}")
    public R<List<VideoClipVo>> listByTaskId(@PathVariable String taskId) {
        return R.ok(videoClipService.queryByTaskId(taskId));
    }

    // ---------- 私有辅助 ----------

    private String generatePresignedUrl(Long videoId) {
        var camera = cameraManagementService.queryById(videoId);
        if (camera == null) {
            throw new ServiceException("视频不存在: videoId=" + videoId);
        }
        Long ossId = camera.getOssId();
        if (ossId == null) {
            throw new ServiceException("视频尚未上传到 OSS");
        }
        List<RemoteFile> files = remoteFileService.selectByIds(String.valueOf(ossId));
        if (files == null || files.isEmpty()) {
            throw new ServiceException("OSS 文件信息不存在");
        }
        String originalUrl = files.get(0).getUrl();
        OssClient ossClient = OssFactory.instance();
        String objectName = ossClient.removeBaseUrl(originalUrl);
        return ossClient.getPrivateUrl(objectName, PRESIGN_DURATION);
    }
}
