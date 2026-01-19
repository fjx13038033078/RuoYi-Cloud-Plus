package org.dromara.camera.service;

import org.dromara.camera.domain.VideoUploadMessage;

import java.util.Map;

/**
 * 视频消息服务接口
 * 用于发送视频上传消息到消息队列
 *
 * @author LionLi
 */
public interface IVideoMessageService {

    /**
     * 发送视频上传消息
     *
     * @param message 视频上传消息
     */
    void sendVideoUploadMessage(VideoUploadMessage message);

    /**
     * 发送MinIO URL消息
     *
     * @param minioUrl    MinIO地址
     * @param ossId       数据库主键ID
     * @param metadataMap 元数据
     */
    void sendMinioUrlMessage(String minioUrl, Long ossId, Map<String, Object> metadataMap);

    /**
     * 发送MinIO URL消息（兼容旧调用）
     *
     * @param minioUrl     MinIO地址
     * @param ossId        数据库主键ID
     * @param originalPath 原始路径
     * @param fileName     文件名
     * @param userName     用户名
     */
    void sendMinioUrlMessage(String minioUrl, Long ossId, String originalPath, String fileName, String userName);
}
