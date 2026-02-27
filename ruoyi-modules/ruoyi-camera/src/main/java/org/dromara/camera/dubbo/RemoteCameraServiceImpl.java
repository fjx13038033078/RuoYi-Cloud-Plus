package org.dromara.camera.dubbo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.service.ICameraManagementDubboService;
import org.dromara.camera.service.IVideoScanUploadService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 执法记录仪远程服务实现
 *
 * @author LionLi
 */
@Slf4j
@RequiredArgsConstructor
@Service
@DubboService(version = "1.0.0")
public class RemoteCameraServiceImpl implements ICameraManagementDubboService {

    private final IVideoScanUploadService videoScanUploadService;

    @Override
    public List<CameraManagement> scanInsertFromFolder(String folderPath) {
        log.info("Dubbo服务调用：扫描执法视频文件夹，参数：{}", folderPath);
        return videoScanUploadService.scanInsertFromFolder(folderPath);
    }
}
