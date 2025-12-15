package org.dromara.job.snailjob;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.client.job.core.executor.AbstractJobExecutor;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.dromara.system.service.ICameraManagementDubboService;
import org.springframework.stereotype.Component;

/**
 * @author opensnail
 * @date 2024-05-17
 */

@Slf4j
@Component
@JobExecutor(name = "cameraManagementJobExecutor")
public class CameraManagementJobExecutor extends AbstractJobExecutor {

    // 注入 Dubbo 服务
    @DubboReference(version = "1.0.0", check = false)
    private ICameraManagementDubboService cameraManagementDubboService;

    // 单个固定路径
    private static final String TARGET_FOLDER = "D:\\执法记录仪";

    @Override
    protected ExecuteResult doJobExecute(JobArgs jobArgs) {
        try {
            log.info("定时任务: 开始扫描 {}", TARGET_FOLDER);

            // 直接调用 Dubbo 服务
            cameraManagementDubboService.scanInsertFromFolder(TARGET_FOLDER);

            log.info("定时任务: 扫描完成");
            return ExecuteResult.success("扫描任务执行成功");

        } catch (Exception e) {
            log.error("定时任务执行失败", e);
            return ExecuteResult.failure("扫描失败: " + e.getMessage());
        }
    }
}
