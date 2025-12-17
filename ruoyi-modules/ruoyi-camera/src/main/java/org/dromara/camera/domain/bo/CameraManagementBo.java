package org.dromara.camera.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * 执法视频信息管理业务对象 camera_management
 *
 * @author LionLi
 * @date 2025-12-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = CameraManagement.class, reverseConvertGenerate = false)
public class CameraManagementBo extends BaseEntity {

    /**
     * 视频ID（唯一标识）
     */
    @NotNull(message = "视频ID（唯一标识）不能为空", groups = {EditGroup.class})
    private Long videoId;

    /**
     * 来源设备
     */
    @NotBlank(message = "来源设备不能为空", groups = {AddGroup.class, EditGroup.class})
    private String deviceId;

    /**
     * 用户编号执法人员编号（采集者/使用者）
     */
    @NotBlank(message = "用户编号执法人员编号（采集者/使用者）不能为空", groups = {AddGroup.class, EditGroup.class})
    private String userCode;

    /**
     * 用户姓名
     */
    @NotBlank(message = "用户姓名不能为空", groups = {AddGroup.class, EditGroup.class})
    private String userName;

    /**
     * 上传时间（入库时间戳）
     */
    @NotNull(message = "上传时间（入库时间戳）不能为空", groups = {AddGroup.class, EditGroup.class})
    private Date uploadTime;

    /**
     * 单位编号
     */
    @NotNull(message = "单位编号不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long deptId;

    /**
     * 拍摄时间（视频实际拍摄时间）
     */
    @NotNull(message = "拍摄时间（视频实际拍摄时间）不能为空", groups = {AddGroup.class, EditGroup.class})
    private Date shootTime;

    /**
     * 视频时长显示（如：05:30）
     */
    private String durationDisplay;

    /**
     * 媒体类型（mp4/mov/avi等）
     */
    private String mediaType;

    /**
     * 文件描述（如：巡检焊割作业现场记录）
     */
    private String fileDescription;

    /**
     * 存储位置（对象存储URL/文件系统路径）
     */
    @NotBlank(message = "存储位置（对象存储URL/文件系统路径）不能为空", groups = {AddGroup.class, EditGroup.class})
    private String storageLocation;

    /**
     * 文件标记（预留字段）
     */
    private String fileMark;

    /**
     * 数据来源（执法记录仪自动上传/手动上传/外部导入）
     */
    private String dataSource;

    /**
     * AI检测状态（0:未检测,1:检测中,2:检测完成,3:检测失败）
     */
    private Long aiCheckStatus;

    /**
     * AI检测结果（JSON结构化描述）
     */
    private String aiCheckResult;

    /**
     * 数据状态（1:正常,0:删除）
     */
    private Long dataStatus;


}
