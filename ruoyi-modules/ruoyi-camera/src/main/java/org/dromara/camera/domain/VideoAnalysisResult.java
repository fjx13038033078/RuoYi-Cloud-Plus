package org.dromara.camera.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 视频分析结果DTO
 * 用于接收Python端回传的AI检测结果
 *
 * @author LionLi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoAnalysisResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID（与请求一致，用于追踪）
     */
    private String taskId;

    /**
     * 视频ID（数据库主键）
     */
    private Long videoId;

    /**
     * 状态: SUCCESS/FAILED
     */
    private String status;

    /**
     * 是否有违规
     */
    private Boolean hasViolation;

    /**
     * 违规类型（如：未戴安全帽、违规操作等）
     */
    private String violationType;

    /**
     * AI分析描述（Qwen-VL生成的详细描述）
     */
    private String aiDescription;

    /**
     * 违规截图URL（MinIO预签名URL）
     */
    private String screenshotUrl;

    /**
     * 事件列表JSON
     */
    private String eventsJson;

    /**
     * 处理耗时（秒）
     */
    private Double processTime;

    /**
     * 错误信息（当status=FAILED时）
     */
    private String errorMessage;

    /**
     * 判断是否处理成功
     */
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    /**
     * 判断是否有违规行为
     */
    public boolean hasViolationBehavior() {
        return Boolean.TRUE.equals(hasViolation);
    }
}
