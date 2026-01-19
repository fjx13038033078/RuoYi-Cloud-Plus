package org.dromara.camera.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 视频上传消息DTO
 * 用于RabbitMQ消息传输，供下游Python(FastAPI)消费者解析
 *
 * @author LionLi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoUploadMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID（业务追踪ID，用于日志追踪和幂等处理）
     */
    private String taskId;

    /**
     * 视频ID（数据库主键）
     */
    private Long videoId;

    /**
     * 预签名URL（核心字段：算法端下载视频的凭证，24小时有效）
     */
    private String presignedUrl;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 对象名称（MinIO中的文件路径/key）
     */
    private String objectName;

    /**
     * 原始文件URL（非预签名，用于记录）
     */
    private String originalUrl;

    /**
     * 消息创建时间
     */
    private LocalDateTime createTime;

    /**
     * 元数据
     */
    private Metadata metadata;

    /**
     * 元数据内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 视频编码/设备号
         */
        private String videoCode;

        /**
         * 用户名称
         */
        private String userName;

        /**
         * 录制时间
         */
        private String recordTime;

        /**
         * 文件名
         */
        private String fileName;

        /**
         * 文件大小（字节）
         */
        private Long fileSize;

        /**
         * 内容类型
         */
        private String contentType;

        /**
         * 原始文件路径
         */
        private String originalPath;

        /**
         * 扩展字段（供业务扩展使用）
         */
        private Map<String, Object> extFields;
    }
}
