package org.dromara.job.snailjob;

import cn.hutool.core.convert.Convert;
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.client.job.core.executor.AbstractJobExecutor;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.dromara.camera.service.ICameraManagementDubboService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 检测扫描定时任务
 * <p>
 * 扫描指定文件夹（递归子目录）→ 原视频上传 MinIO + 入 camera_management（dataSource=scan）→ 发起 AI 检测 MQ。
 * <p>
 * SnailJob 后台配置：
 *   executor_info = cameraManagementJobExecutor
 *   job_params    = 可选，指定要扫描的根目录；不填则使用 nacos / 默认值。
 *                   只需填写顶层目录（如 D:\执法记录仪），其下多级子目录会被递归扫描。
 *
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

    /** 未在任务参数中指定时使用的默认扫描根目录（可在 Nacos 用 camera.scan.target-folder 覆盖） */
    @Value("${camera.scan.target-folder:D:\\执法记录仪}")
    private String defaultFolder;

    @Override
    protected ExecuteResult doJobExecute(JobArgs jobArgs) {
        // 优先使用 SnailJob「任务参数」，未填写则回退到默认目录
        String folder = Convert.toStr(jobArgs.getJobParams(), defaultFolder);
        try {
            log.info("定时任务: 开始扫描 {}（递归子目录）", folder);

            // 直接调用 Dubbo 服务（内部使用 Files.walk 递归扫描所有子目录）
            cameraManagementDubboService.scanInsertFromFolder(folder);

            log.info("定时任务: 扫描完成，目录={}", folder);
            return ExecuteResult.success("扫描任务执行成功: " + folder);

        } catch (Exception e) {
            log.error("定时任务执行失败，目录={}", folder, e);
            return ExecuteResult.failure("扫描失败: " + e.getMessage());
        }
    }
}
