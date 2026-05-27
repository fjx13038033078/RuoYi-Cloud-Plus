package org.dromara.camera.domain.bo;

import lombok.Data;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 视频切片查询 Bo
 *
 * @author system
 */
@Data
public class VideoClipQueryBo extends BaseEntity {

    /** 关联视频ID */
    private Long videoId;

    /** 切割任务ID */
    private String taskId;

    /** 切片状态（0:处理中 1:完成 2:失败） */
    private Integer clipStatus;
}
