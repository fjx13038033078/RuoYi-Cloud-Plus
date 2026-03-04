package org.dromara.camera.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;


/**
 * 执法视频信息管理视图对象 camera_management
 *
 * @author LionLi
 * @date 2025-12-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CameraManagement.class)
public class CameraManagementVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 视频ID（唯一标识）
     */
    @ExcelProperty(value = "视频ID")
    private Long videoId;

    /**
     * 视频唯一序列号（从文件名解析，如 Q541062）
     */
    @ExcelProperty(value = "视频序列号")
    private String serialNumber;

    /**
     * 来源设备
     */
    @ExcelProperty(value = "来源设备")
    private String deviceId;

    /**
     * 用户编号执法人员编号（采集者/使用者）
     */
    @ExcelProperty(value = "用户编号")
    private String userCode;

    /**
     * 用户姓名
     */
    @ExcelProperty(value = "用户姓名")
    private String userName;

    /**
     * 上传时间（入库时间戳）
     */
    @ExcelProperty(value = "上传时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;

    /**
     * 单位编号
     */
    @ExcelProperty(value = "单位编号")
    private Long deptId;

    /**
     * 拍摄时间（视频实际拍摄时间）
     */
    @ExcelProperty(value = "拍摄时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date shootTime;

    /**
     * 视频时长显示（如：05:30）
     */
    @ExcelProperty(value = "视频时长")
    private String durationDisplay;

    /**
     * 媒体类型（mp4/mov/avi等）
     */
    @ExcelProperty(value = "媒体类型")
    private String mediaType;

    /**
     * 文件描述（如：巡检焊割作业现场记录）
     */
    @ExcelProperty(value = "文件描述")
    private String fileDescription;

    /**
     * 存储位置（对象存储URL/文件系统路径）
     */
    @ExcelProperty(value = "存储位置")
    private String storageLocation;

    /**
     * 文件标记（预留字段）
     */
    @ExcelProperty(value = "文件标记")
    private String fileMark;

    /**
     * 数据来源（执法记录仪自动上传/手动上传/外部导入）
     */
    @ExcelProperty(value = "数据来源")
    private String dataSource;

    /**
     * AI检测状态（0:未检测,1:检测中,2:检测完成,3:检测失败）
     */
    @ExcelProperty(value = "AI检测状态")
    private Integer aiCheckStatus;  // 改为 Integer

    /**
     * AI检测结果（JSON结构化描述）
     */
    @ExcelProperty(value = "AI检测结果")
    private String aiCheckResult;

    /**
     * 是否有违规行为（0:否,1:是）
     */
    @ExcelProperty(value = "是否违规")
    private Integer hasViolation;

    /**
     * 违规类型
     */
    @ExcelProperty(value = "违规类型")
    private String violationType;

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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date checkTime;

    /**
     * 数据状态（1:正常,0:删除）
     */
    @ExcelProperty(value = "数据状态")
    private Integer dataStatus;  // 改为 Integer

    /**
     * OSS文件ID
     */
    private Long ossId;

    /**
     * MinIO文件URL（通过ossId翻译获取）
     */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "ossId")
    private String url;

    /**
     * 复判状态（0未复判 1已复判）
     */
    @ExcelProperty(value = "复判状态")
    private Integer reviewStatus;

    /**
     * 复判结果（0正常无违规 1确认违规）
     */
    @ExcelProperty(value = "复判结果")
    private Integer reviewResult;

    /**
     * 复判说明
     */
    @ExcelProperty(value = "复判说明")
    private String reviewComment;

    /**
     * 复判人ID
     */
    private Long reviewerId;

    /**
     * 复判人名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "reviewerId")
    private String reviewerName;

    /**
     * 复判时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;
}
