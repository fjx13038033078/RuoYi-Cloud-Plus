package org.dromara.camera.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.camera.domain.vo.DashboardStatsVo;
import org.dromara.camera.service.IDashboardService;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据大屏统计
 *
 * @author LionLi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final IDashboardService dashboardService;

    /**
     * 获取数据大屏统计数据
     */
    @GetMapping("/stats")
    public R<DashboardStatsVo> getStats() {
        return R.ok(dashboardService.getStats());
    }
}
