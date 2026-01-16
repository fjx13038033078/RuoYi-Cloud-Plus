package org.dromara.camera.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoUploadMessage implements Serializable {

    /**
     * 任务ID（UUID或数据库主键）
     */
    private String videoId;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 对象名称（文件路径）
     */
    private String objectName;

    /**
     * 元数据
     */
    private Metadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        /**
         * 视频编码/设备号
         */
        private String videoCode;

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 录制时间
         */
        private String recordTime;

        /**
         * 扩展字段
         */
        private Map<String, Object> extFields;
    }
}
