package org.dromara.camera.service;

/**
 * 视频切割消息服务接口（发送切割任务到 Python）
 *
 * @author system
 */
public interface IVideoClipMessageService {

    /**
     * 发送切割任务消息
     *
     * @param videoId      视频ID
     * @param presignedUrl 预签名下载URL
     * @return 任务ID
     */
    String sendClipTask(Long videoId, String presignedUrl);
}
