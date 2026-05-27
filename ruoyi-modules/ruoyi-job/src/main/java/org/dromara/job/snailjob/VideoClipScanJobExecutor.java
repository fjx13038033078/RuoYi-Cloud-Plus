package org.dromara.job.snailjob;

import cn.hutool.core.convert.Convert;
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.client.job.core.executor.AbstractJobExecutor;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.dromara.camera.service.IVideoClipScanDubboService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 视频切割扫描定时任务
 * <p>
 * 流程：扫描指定文件夹 → 原视频上传 MinIO + 入 camera_management（dataSource=clip，不触发 AI 识别）
 * → 逐条发起切割 MQ 任务
 * <p>
 * SnailJob 后台配置：
 *   executor_info = videoClipScanJobExecutor
 *   job_params    = 可选，指定要扫描的文件夹路径；不填则使用 nacos 默认值
 *   executor_timeout = 建议 3600 秒以上（视频较多时）
 *
 * @author system
 */
@Slf4j
@Component
@JobExecutor(name = "videoClipScanJobExecutor")
public class VideoClipScanJobExecutor extends AbstractJobExecutor {

    @DubboReference(version = "1.0.0", check = false, timeout = 600_000)
    private IVideoClipScanDubboService videoClipScanDubboService;

    @Value("${camera.clip-scan.target-folder:\\\\\\\\192.168.124.52\\\\e\\\\20260314\\\\MP4}")
    private String defaultFolder;

    @Override
    protected ExecuteResult doJobExecute(JobArgs jobArgs) {
        String folder = Convert.toStr(jobArgs.getJobParams(), defaultFolder);
        log.info("[VideoClipScanJob] 开始执行，扫描目录: {}", folder);

        try {
            int submitted = videoClipScanDubboService.scanAndSubmitClipTasks(folder);
            String msg = "成功提交切割任务数: " + submitted;
            log.info("[VideoClipScanJob] {}", msg);
            return ExecuteResult.success(msg);
        } catch (Exception e) {
            log.error("[VideoClipScanJob] 执行失败", e);
            return ExecuteResult.failure("切割扫描失败: " + e.getMessage());
        }
    }
}
