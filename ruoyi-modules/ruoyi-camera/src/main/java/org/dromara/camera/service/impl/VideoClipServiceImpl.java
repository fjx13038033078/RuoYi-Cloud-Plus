package org.dromara.camera.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.VideoClip;
import org.dromara.camera.domain.bo.VideoClipQueryBo;
import org.dromara.camera.domain.vo.VideoClipVo;
import org.dromara.camera.mapper.VideoClipMapper;
import org.dromara.camera.service.IVideoClipService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 视频切片服务实现
 *
 * @author system
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoClipServiceImpl implements IVideoClipService {

    private final VideoClipMapper baseMapper;

    private static final Duration CLIP_URL_EXPIRATION = Duration.ofHours(24);

    @Override
    public TableDataInfo<VideoClipVo> queryPageList(VideoClipQueryBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<VideoClip> lqw = buildQueryWrapper(bo);
        Page<VideoClipVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    @Override
    public List<VideoClipVo> queryList(VideoClipQueryBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public VideoClipVo queryById(Long clipId) {
        return baseMapper.selectVoById(clipId);
    }

    @Override
    public List<VideoClipVo> queryByTaskId(String taskId) {
        LambdaQueryWrapper<VideoClip> lqw = Wrappers.lambdaQuery();
        lqw.eq(VideoClip::getTaskId, taskId);
        lqw.orderByAsc(VideoClip::getClipIndex);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public List<VideoClipVo> queryByVideoId(Long videoId) {
        LambdaQueryWrapper<VideoClip> lqw = Wrappers.lambdaQuery();
        lqw.eq(VideoClip::getVideoId, videoId);
        lqw.orderByAsc(VideoClip::getClipIndex);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public Boolean deleteByIds(List<Long> clipIds) {
        return baseMapper.deleteByIds(clipIds) > 0;
    }

    @Override
    public VideoClipVo refreshUrl(Long clipId) {
        VideoClip clip = baseMapper.selectById(clipId);
        if (clip == null) {
            throw new ServiceException("切片不存在");
        }
        if (StringUtils.isBlank(clip.getObjectName())) {
            throw new ServiceException("切片对象名称为空，无法刷新URL");
        }
        try {
            OssClient ossClient = OssFactory.instance();
            String url = ossClient.getPrivateUrl(clip.getObjectName(), CLIP_URL_EXPIRATION);
            clip.setUrl(url);
            baseMapper.updateById(clip);
            return baseMapper.selectVoById(clipId);
        } catch (Exception e) {
            log.error("刷新切片URL失败: clipId={}, objectName={}", clipId, clip.getObjectName(), e);
            throw new ServiceException("刷新URL失败: " + e.getMessage());
        }
    }

    private LambdaQueryWrapper<VideoClip> buildQueryWrapper(VideoClipQueryBo bo) {
        LambdaQueryWrapper<VideoClip> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getVideoId() != null, VideoClip::getVideoId, bo.getVideoId());
        lqw.eq(StringUtils.isNotBlank(bo.getTaskId()), VideoClip::getTaskId, bo.getTaskId());
        lqw.eq(bo.getClipStatus() != null, VideoClip::getClipStatus, bo.getClipStatus());
        lqw.orderByAsc(VideoClip::getVideoId).orderByAsc(VideoClip::getClipIndex);
        return lqw;
    }
}
