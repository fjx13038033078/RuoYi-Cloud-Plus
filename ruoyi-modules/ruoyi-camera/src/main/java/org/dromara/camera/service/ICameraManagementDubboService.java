package org.dromara.camera.service;

import org.dromara.camera.domain.CameraManagement;

import java.util.List;

public interface ICameraManagementDubboService {

    List<CameraManagement> scanInsertFromFolder(String folderPath);

}
