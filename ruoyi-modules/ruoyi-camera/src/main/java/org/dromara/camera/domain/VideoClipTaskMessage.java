package org.dromara.camera.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 视频切割任务消息（发给 Python）
 *
 * @author system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoClipTaskMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;

    /** 视频ID */
    private Long videoId;

    /** 预签名下载URL（7天有效） */
    private String presignedUrl;

    /** 最短有效片段时长（秒），默认 3.0 */
    private Double minSegmentDuration;

    /** YOLO 检测步长（每N帧取1帧），默认 3 */
    private Integer vidStride;

    /** 检测置信度阈值，默认 0.5 */
    private Double conf;
}
