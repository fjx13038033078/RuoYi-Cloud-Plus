package org.dromara.camera.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 视频切割结果消息（Python 回传）
 *
 * @author system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoClipResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;

    /** 视频ID */
    private Long videoId;

    /** 状态：SUCCESS / FAILED */
    private String status;

    /** 切片列表 */
    private List<ClipInfo> clips;

    /** 失败信息（status=FAILED 时） */
    private String errorMessage;

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    /**
     * 单个切片信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClipInfo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Integer clipIndex;
        private String objectName;
        private String url;
        private Double startSecond;
        private Double endSecond;
        private Double durationSeconds;
        private Long fileSize;
    }
}
