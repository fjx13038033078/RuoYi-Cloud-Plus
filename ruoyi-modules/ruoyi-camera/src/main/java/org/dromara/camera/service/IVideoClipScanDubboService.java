package org.dromara.camera.service;

/**
 * 视频切割扫描 Dubbo 服务
 *
 * @author system
 */
public interface IVideoClipScanDubboService {

    /**
     * 扫描指定文件夹下的视频，入库（不触发 AI 识别）后逐个发起切割任务。
     *
     * @param folderPath 文件夹路径
     * @return 成功提交的切割任务数量
     */
    int scanAndSubmitClipTasks(String folderPath);
}
