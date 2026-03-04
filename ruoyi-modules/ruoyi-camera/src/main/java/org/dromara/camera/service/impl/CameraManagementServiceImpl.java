package org.dromara.camera.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.bo.CameraManagementBo;
import org.dromara.camera.domain.bo.ManualReviewBo;
import org.dromara.camera.domain.vo.CameraManagementVo;
import org.dromara.camera.mapper.CameraManagementMapper;
import org.dromara.camera.service.ICameraManagementService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.resource.api.RemoteFileService;
import org.dromara.resource.api.domain.RemoteFile;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 执法视频信息管理Service业务层处理
 * 仅包含CRUD基本操作和视频播放URL查询
 *
 * @author LionLi
 * @date 2025-12-05
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CameraManagementServiceImpl implements ICameraManagementService {

    private final CameraManagementMapper baseMapper;

    @DubboReference
    private RemoteFileService remoteFileService;

    private static final Duration VIDEO_PLAY_URL_EXPIRATION = Duration.ofHours(24);

    @Override
    public CameraManagementVo queryById(Long videoId) {
        return baseMapper.selectVoById(videoId);
    }

    @Override
    public TableDataInfo<CameraManagementVo> queryPageList(CameraManagementBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<CameraManagement> lqw = buildQueryWrapper(bo);
        Page<CameraManagementVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    @Override
    public List<CameraManagementVo> queryList(CameraManagementBo bo) {
        LambdaQueryWrapper<CameraManagement> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<CameraManagement> buildQueryWrapper(CameraManagementBo bo) {
        LambdaQueryWrapper<CameraManagement> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(CameraManagement::getVideoId);
        lqw.like(StringUtils.isNotBlank(bo.getSerialNumber()), CameraManagement::getSerialNumber, bo.getSerialNumber());
        lqw.eq(StringUtils.isNotBlank(bo.getDeviceId()), CameraManagement::getDeviceId, bo.getDeviceId());
        lqw.eq(StringUtils.isNotBlank(bo.getUserCode()), CameraManagement::getUserCode, bo.getUserCode());
        lqw.like(StringUtils.isNotBlank(bo.getUserName()), CameraManagement::getUserName, bo.getUserName());
        lqw.eq(bo.getUploadTime() != null, CameraManagement::getUploadTime, bo.getUploadTime());
        lqw.eq(bo.getShootTime() != null, CameraManagement::getShootTime, bo.getShootTime());
        lqw.eq(StringUtils.isNotBlank(bo.getDurationDisplay()), CameraManagement::getDurationDisplay, bo.getDurationDisplay());
        lqw.eq(StringUtils.isNotBlank(bo.getMediaType()), CameraManagement::getMediaType, bo.getMediaType());
        lqw.eq(StringUtils.isNotBlank(bo.getFileDescription()), CameraManagement::getFileDescription, bo.getFileDescription());
        lqw.eq(StringUtils.isNotBlank(bo.getStorageLocation()), CameraManagement::getStorageLocation, bo.getStorageLocation());
        lqw.eq(StringUtils.isNotBlank(bo.getFileMark()), CameraManagement::getFileMark, bo.getFileMark());
        lqw.eq(StringUtils.isNotBlank(bo.getDataSource()), CameraManagement::getDataSource, bo.getDataSource());
        lqw.eq(bo.getAiCheckStatus() != null, CameraManagement::getAiCheckStatus, bo.getAiCheckStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getAiCheckResult()), CameraManagement::getAiCheckResult, bo.getAiCheckResult());
        lqw.eq(bo.getDataStatus() != null, CameraManagement::getDataStatus, bo.getDataStatus());
        lqw.eq(bo.getCreateBy() != null, CameraManagement::getCreateBy, bo.getCreateBy());
        lqw.eq(bo.getUpdateBy() != null, CameraManagement::getUpdateBy, bo.getUpdateBy());
        return lqw;
    }

    @Override
    public Boolean insertByBo(CameraManagementBo bo) {
        CameraManagement add = MapstructUtils.convert(bo, CameraManagement.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setVideoId(add.getVideoId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(CameraManagementBo bo) {
        CameraManagement update = MapstructUtils.convert(bo, CameraManagement.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    private void validEntityBeforeSave(CameraManagement entity) {
        //TODO 做一些数据校验,如唯一约束
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    @Override
    public String getVideoPlayUrl(Long videoId) {
        log.info("获取视频播放URL，videoId: {}", videoId);

        CameraManagement camera = baseMapper.selectById(videoId);
        if (camera == null) {
            throw new ServiceException("视频不存在");
        }

        Long ossId = camera.getOssId();
        if (ossId == null) {
            log.warn("视频[{}]未上传到OSS，返回本地路径: {}", videoId, camera.getStorageLocation());
            return camera.getStorageLocation();
        }

        List<RemoteFile> files = remoteFileService.selectByIds(String.valueOf(ossId));
        if (CollUtil.isEmpty(files)) {
            throw new ServiceException("OSS文件信息不存在");
        }

        RemoteFile remoteFile = files.get(0);
        String originalUrl = remoteFile.getUrl();

        try {
            OssClient ossClient = OssFactory.instance();
            String objectName = ossClient.removeBaseUrl(originalUrl);
            String presignedUrl = ossClient.getPrivateUrl(objectName, VIDEO_PLAY_URL_EXPIRATION);
            log.info("生成预签名URL成功，videoId: {}", videoId);
            return presignedUrl;
        } catch (Exception e) {
            log.error("生成视频播放URL失败，videoId: {}, url: {}", videoId, originalUrl, e);
            throw new ServiceException("生成视频播放URL失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitManualReview(ManualReviewBo bo) {
        CameraManagement camera = baseMapper.selectById(bo.getVideoId());
        if (camera == null) {
            throw new ServiceException("视频记录不存在");
        }

        CameraManagement update = new CameraManagement();
        update.setVideoId(bo.getVideoId());
        update.setReviewStatus(1);
        update.setReviewResult(bo.getReviewResult());
        update.setReviewComment(bo.getReviewComment());
        update.setReviewerId(LoginHelper.getUserId());
        update.setReviewTime(new Date());

        if (bo.getReviewResult() == 0) {
            update.setHasViolation(0);
            update.setViolationType(null);
        }

        baseMapper.updateById(update);
        log.info("人工复判完成: videoId={}, reviewResult={}, reviewerId={}",
            bo.getVideoId(), bo.getReviewResult(), update.getReviewerId());
    }
}
