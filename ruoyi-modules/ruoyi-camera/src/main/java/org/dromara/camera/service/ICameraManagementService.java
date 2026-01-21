package org.dromara.camera.service;

import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.VideoAnalysisResult;
import org.dromara.camera.domain.bo.CameraManagementBo;
import org.dromara.camera.domain.vo.CameraManagementVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 执法视频信息管理Service接口
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

//    /**
//     * 从Excel导入数据
//     * @param file Excel文件
//     * @return 导入结果
//     */
//    List<CameraManagement> importFromExcel(MultipartFile file);

    /**
     * 扫描文件夹并插入数据
     *
     * @param folderPath 文件夹路径
     * @return 插入的数据列表
     */
    List<CameraManagement> scanInsertFromFolder(String folderPath);

    /**
     * 视频分析
     *
     * @param file 视频文件
     * @return 分析结果
     */
    Map<String, Object> analyzeVideo(MultipartFile file);

    /**
     * 获取视频播放URL（预签名URL，用于私有bucket访问）
     *
     * @param videoId 视频ID
     * @return 预签名播放URL
     */
    String getVideoPlayUrl(Long videoId);

    /**
     * 更新AI分析结果
     * 接收Python端回传的检测结果，更新数据库记录
     *
     * @param result AI分析结果
     */
    void updateAnalysisResult(VideoAnalysisResult result);

    /**
     * 更新AI检测状态
     *
     * @param videoId 视频ID
     * @param status  状态（0:未检测,1:检测中,2:检测完成,3:检测失败）
     */
    void updateAiCheckStatus(Long videoId, Integer status);
}
