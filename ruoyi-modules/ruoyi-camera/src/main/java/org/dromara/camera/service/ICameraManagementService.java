package org.dromara.camera.service;

import org.dromara.camera.domain.bo.CameraManagementBo;
import org.dromara.camera.domain.vo.CameraManagementVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 执法视频信息管理Service接口
 * 仅包含CRUD基本操作和视频播放URL查询
 *
 * @author LionLi
 * @date 2025-12-05
 */
public interface ICameraManagementService {

    /**
     * 查询执法视频信息管理
     *
     * @param videoId 主键
     * @return 执法视频信息管理
     */
    CameraManagementVo queryById(Long videoId);

    /**
     * 分页查询执法视频信息管理列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 执法视频信息管理分页列表
     */
    TableDataInfo<CameraManagementVo> queryPageList(CameraManagementBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的执法视频信息管理列表
     *
     * @param bo 查询条件
     * @return 执法视频信息管理列表
     */
    List<CameraManagementVo> queryList(CameraManagementBo bo);

    /**
     * 新增执法视频信息管理
     *
     * @param bo 执法视频信息管理
     * @return 是否新增成功
     */
    Boolean insertByBo(CameraManagementBo bo);

    /**
     * 修改执法视频信息管理
     *
     * @param bo 执法视频信息管理
     * @return 是否修改成功
     */
    Boolean updateByBo(CameraManagementBo bo);

    /**
     * 校验并批量删除执法视频信息管理信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 获取视频播放URL（预签名URL，用于私有bucket访问）
     *
     * @param videoId 视频ID
     * @return 预签名播放URL
     */
    String getVideoPlayUrl(Long videoId);
}
