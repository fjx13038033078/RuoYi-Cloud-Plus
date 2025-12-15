package org.dromara.system.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.system.domain.CameraManagement;
import org.dromara.system.service.ICameraManagementDubboService;
import org.dromara.system.service.ICameraManagementService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@DubboService(version = "1.0.0")
@Service
public class CameraManagementDubboServiceImpl implements ICameraManagementDubboService {

    @Resource
    private ICameraManagementService cameraManagementService;

    @Override
    public List<CameraManagement> scanInsertFromFolder(String folderPath) {
        log.info("Dubbo服务调用：查询执法视频列表，参数：{}", folderPath);
        return cameraManagementService.scanInsertFromFolder(folderPath);
    }
}
