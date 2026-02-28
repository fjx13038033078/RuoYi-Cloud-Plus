package org.dromara.camera.service;

import org.dromara.camera.domain.vo.DashboardStatsVo;

/**
 * 数据大屏统计Service接口
 *
 * @author LionLi
 */
public interface IDashboardService {

    /**
     * 获取数据大屏统计数据
     *
     * @return 大屏统计数据
     */
    DashboardStatsVo getStats();
}
