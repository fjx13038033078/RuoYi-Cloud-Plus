package org.dromara.camera.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.camera.domain.VideoClip;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 视频切片视图对象
 *
 * @author system
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = VideoClip.class)
public class VideoClipVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 切片ID */
    @ExcelProperty("切片ID")
    private Long clipId;

    /** 切割任务ID */
    @ExcelProperty("任务ID")
    private String taskId;

    /** 关联视频ID */
    @ExcelProperty("视频ID")
    private Long videoId;

    /** 切片序号 */
    @ExcelProperty("序号")
    private Integer clipIndex;

    /** MinIO对象名称 */
    private String objectName;

    /** 访问URL */
    private String url;

    /** 起始时间（秒） */
    @ExcelProperty("起始秒")
    private Double startSecond;

    /** 结束时间（秒） */
    @ExcelProperty("结束秒")
    private Double endSecond;

    /** 时长（秒） */
    @ExcelProperty("时长(秒)")
    private Double durationSeconds;

    /** 文件大小（字节） */
    @ExcelProperty("文件大小(字节)")
    private Long fileSize;

    /** 切片状态（0:处理中 1:完成 2:失败） */
    @ExcelProperty("状态")
    private Integer clipStatus;

    /** 失败信息 */
    private String errorMessage;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty("创建时间")
    private Date createTime;
}
