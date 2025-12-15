package org.dromara.system.service;

import org.dromara.system.domain.CameraManagement;

import java.util.List;

public interface ICameraManagementDubboService {

    List<CameraManagement> scanInsertFromFolder(String folderPath);

}
