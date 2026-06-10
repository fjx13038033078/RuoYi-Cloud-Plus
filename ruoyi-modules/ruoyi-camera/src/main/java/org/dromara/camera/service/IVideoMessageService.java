package org.dromara.camera.service;

import org.dromara.camera.domain.VideoUploadMessage;

/**
 * 视频消息服务接口
 * 用于发送视频上传消息到消息队列
 *
 * @author LionLi
 */
public interface IVideoMessageService {

    /**
     * 发送视频上传消息（包含预签名URL）
     *
     * @param message 视频上传消息
     */
    void sendVideoUploadMessage(VideoUploadMessage message);

    /**
     * 构建并发送视频检测消息
     *
     * @param videoId       数据库主键ID
     * @param presignedUrl  预签名URL（7天有效）
     * @param bucketName    存储桶名称
     * @param objectName    对象名称
     * @param originalUrl   原始URL
     * @param metadata      元数据
     */
    void sendVideoDetectionMessage(Long videoId, String presignedUrl, String bucketName,
                                   String objectName, String originalUrl,
                                   VideoUploadMessage.Metadata metadata);

    /**
     * 构建并发送切片级视频检测消息（切分预处理链路）
     *
     * @param videoId         原视频数据库主键ID
     * @param clipId          切片ID（video_clip.clip_id）
     * @param clipStartSecond 切片在原视频中的起始秒
     * @param presignedUrl    切片预签名URL（7天有效）
     * @param bucketName      存储桶名称
     * @param objectName      切片对象名称
     * @param originalUrl     原始URL
     * @param metadata        元数据
     */
    void sendClipDetectionMessage(Long videoId, Long clipId, Double clipStartSecond,
                                  String presignedUrl, String bucketName,
                                  String objectName, String originalUrl,
                                  VideoUploadMessage.Metadata metadata);
}
