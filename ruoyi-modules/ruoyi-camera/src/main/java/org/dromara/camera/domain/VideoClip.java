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

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private Integer delFlag;
}
