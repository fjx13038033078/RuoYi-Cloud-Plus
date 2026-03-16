package org.dromara.camera.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 执法视频信息管理对象 camera_management
 *
 * @author LionLi
 * @date 2025-12-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("camera_management")
public class CameraManagement extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 视频ID（唯一标识）
     */
    @TableId(value = "video_id")
    private Long videoId;

    /**
     * 视频唯一序列号（从文件名解析，如 Q541062）
     */
    private String serialNumber;

    /**
     * 来源设备
     */
    private String deviceId;

    /**
     * 用户编号执法人员编号（采集者/使用者）
     */
    private String userCode;

    /**
     * 用户姓名
     */
    private String userName;

    /**
     * 单位编号（数据库暂无此列，标记为非数据库字段）
     */
    @TableField(exist = false)
    private String unitNumber;

    /**
     * 上传时间（入库时间戳）
     */
    private Date uploadTime;

    /**
     * 部门id
     */
    private Long deptId;

    /**
     * 拍摄时间（视频实际拍摄时间）
     */
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
     * 是否有违规行为（0:否,1:是）
     */
    private Integer hasViolation;

    /**
     * 违规类型（如：未戴安全帽、违规操作等）
     */
    private String violationType;

    /**
     * 违规行为起始时间点（秒）
     */
    private Double violationStartSecond;

    /**
     * 违规行为结束时间点（秒）
     */
    private Double violationEndSecond;

    /**
     * 违规截图URL
     */
    private String screenshotUrl;

    /**
     * AI检测耗时（秒）
     */
    private Double processTime;

    /**
     * AI检测完成时间
     */
    private java.util.Date checkTime;

    /**
     * 数据状态（1:正常,0:删除）
     */
    private Long dataStatus;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    @TableLogic
    private Long delFlag;

    /**
     * OSS文件ID（关联sys_oss表）
     */
    private Long ossId;

    /**
     * 复判状态（0未复判 1已复判）
     */
    private Integer reviewStatus;

    /**
     * 复判结果（0正常无违规 1确认违规）
     */
    private Integer reviewResult;

    /**
     * 复判说明
     */
    private String reviewComment;

    /**
     * 复判人ID
     */
    private Long reviewerId;

    /**
     * 复判时间
     */
    private Date reviewTime;
}
