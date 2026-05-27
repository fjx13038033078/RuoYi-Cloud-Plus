package org.dromara.camera.service;

import org.dromara.camera.domain.bo.VideoClipQueryBo;
import org.dromara.camera.domain.vo.VideoClipVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * 视频切片服务接口
 *
 * @author system
 */
public interface IVideoClipService {

    /** 分页查询切片列表 */
    TableDataInfo<VideoClipVo> queryPageList(VideoClipQueryBo bo, PageQuery pageQuery);

    /** 查询全部（导出用） */
    List<VideoClipVo> queryList(VideoClipQueryBo bo);

    /** 查询详情 */
    VideoClipVo queryById(Long clipId);

    /** 根据任务ID查询切片列表 */
    List<VideoClipVo> queryByTaskId(String taskId);

    /** 根据视频ID查询切片列表 */
    List<VideoClipVo> queryByVideoId(Long videoId);

    /** 批量删除切片（逻辑删除） */
    Boolean deleteByIds(List<Long> clipIds);

    /** 刷新切片的预签名URL */
    VideoClipVo refreshUrl(Long clipId);
}
