package org.dromara.system.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.system.domain.CameraManagement;

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
     * 数据状态（1:正常,0:删除）
     */
    @ExcelProperty(value = "数据状态")
    private Integer dataStatus;  // 改为 Integer

}
