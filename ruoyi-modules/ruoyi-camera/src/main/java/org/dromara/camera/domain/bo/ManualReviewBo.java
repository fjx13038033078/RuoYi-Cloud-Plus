package org.dromara.camera.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人工复判请求对象
 *
 * @author LionLi
 */
@Data
public class ManualReviewBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 视频ID
     */
    @NotNull(message = "视频ID不能为空")
    private Long videoId;

    /**
     * 复判结果（0正常无违规 1确认违规）
     */
    @NotNull(message = "复判结果不能为空")
    private Integer reviewResult;

    /**
     * 复判说明
     */
    private String reviewComment;
}
