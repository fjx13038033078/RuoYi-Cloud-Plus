package org.dromara.camera.service;

/**
 * 视频切割扫描服务
 * 定时任务专用：扫描文件夹 → 入库（不触发 AI 识别）→ 逐条发起切割任务
 *
 * @author system
 */
public interface IVideoClipScanService {

    /**
     * 扫描文件夹中的视频，原视频上传到 MinIO 并入 camera_management（dataSource=clip），
     * 然后为每个视频发起一个切割 MQ 任务。
     *
     * @param folderPath 文件夹路径
     * @return 成功提交的切割任务数量
     */
    int scanAndSubmitClipTasks(String folderPath);
}
