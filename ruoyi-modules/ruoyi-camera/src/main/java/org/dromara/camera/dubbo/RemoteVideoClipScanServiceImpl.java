package org.dromara.camera.dubbo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.camera.service.IVideoClipScanDubboService;
import org.dromara.camera.service.IVideoClipScanService;
import org.dromara.common.core.constant.TenantConstants;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;

/**
 * 视频切割扫描 Dubbo 实现
 *
 * @author system
 */
@Slf4j
@RequiredArgsConstructor
@Service
@DubboService(version = "1.0.0")
public class RemoteVideoClipScanServiceImpl implements IVideoClipScanDubboService {

    private final IVideoClipScanService videoClipScanService;

    @Override
    public int scanAndSubmitClipTasks(String folderPath) {
        log.info("Dubbo 调用：视频切割扫描，folder={}", folderPath);
        // SnailJob 后台触发不带租户上下文，统一用默认租户
        return TenantHelper.dynamic(TenantConstants.DEFAULT_TENANT_ID,
            () -> videoClipScanService.scanAndSubmitClipTasks(folderPath));
    }
}
