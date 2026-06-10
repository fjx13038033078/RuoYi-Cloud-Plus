package org.dromara.camera.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 视频人体切片记录 video_clip
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_clip")
public class VideoClip extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 切片ID */
    @TableId(value = "clip_id")
    private Long clipId;

    /** 切割任务ID */
    private String taskId;

    /** 关联执法视频ID */
    private Long videoId;

    /** 原视频文件名 */
    private String sourceFileName;

    /** 原视频切分出的有效片段总数 */
    private Integer sourceClipCount;

    /** 切片序号（从0开始） */
    private Integer clipIndex;

    /** MinIO 对象名称（完整路径） */
    private String objectName;

    /** 预签名访问URL */
    private String url;

    /** 片段起始时间（秒） */
    private Double startSecond;

    /** 片段结束时间（秒） */
    private Double endSecond;

    /** 片段时长（秒） */
    private Double durationSeconds;

    /** 文件大小（字节） */
    private Long fileSize;

    /**
     * 切片状态：0-处理中 1-完成 2-失败
     */
    private Integer clipStatus;

    /** 失败信息 */
    private String errorMessage;

    /**
     * 切片AI检测状态：0-待检测 1-检测中 2-已完成 3-失败
     */
    private Integer aiCheckStatus;

    /** 切片是否违规（0:无 1:有） */
    private Integer aiHasViolation;

    /** 切片违规类型 */
    private String aiViolationType;

    /** 切片AI分析描述 */
    private String aiDescription;

    /** 切片事件列表JSON（时间已偏移到原视频时间轴） */
    private String aiEventsJson;

    /** 切片违规截图URL */
    private String aiScreenshotUrl;

    /** 切片违规起始秒（原视频时间轴） */
    private Double aiViolationStartSecond;

    /** 切片违规结束秒（原视频时间轴） */
    private Double aiViolationEndSecond;

    /** 切片AI处理耗时（秒） */
    private Double aiProcessTime;

    /** 切片AI检测失败信息 */
    private String aiErrorMessage;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private Integer delFlag;
}
