package org.dromara.camera.service;

import org.dromara.camera.domain.CameraManagement;

import java.util.List;

/**
 * 视频扫描上传Service接口
 * 负责文件夹扫描、文件上传到 OSS、数据库记录写入
 *
 * @author LionLi
 */
public interface IVideoScanUploadService {

    /**
     * 扫描指定文件夹下的视频文件并导入到数据库
     * 采用"先上传后保存"策略，确保只有上传成功的文件才会写入数据库
     *
     * @param folderPath 要扫描的文件夹路径
     * @return 成功导入的 CameraManagement 实体列表
     */
    List<CameraManagement> scanInsertFromFolder(String folderPath);
}
