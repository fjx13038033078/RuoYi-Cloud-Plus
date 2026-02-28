package org.dromara.camera.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 数据大屏统计 VO
 *
 * @author LionLi
 */
@Data
public class DashboardStatsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 视频总量 */
    private Long totalVideos;
    /** 已检测数（ai_check_status=2） */
    private Long checkedCount;
    /** 违规视频数（has_violation=1） */
    private Long violationCount;
    /** 检测通过率（如 "92.9%"） */
    private String passRate;
    /** 今日新增 */
    private Long todayNew;
    /** 昨日新增（用于趋势对比） */
    private Long yesterdayNew;
    /** 平均处理时长（如 "2m36s"） */
    private String avgProcessTime;

    /** 近7天检测趋势 */
    private List<TrendItem> trendData;
    /** 违规类型分布 */
    private List<TypeItem> violationTypes;
    /** 部门检测统计 */
    private List<DeptItem> deptStats;
    /** 最新检测记录 */
    private List<RecordItem> recentRecords;

    @Data
    public static class TrendItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String date;
        private Long total;
        private Long violations;
    }

    @Data
    public static class TypeItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String name;
        private Long count;
    }

    @Data
    public static class DeptItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long deptId;
        private String deptName;
        private Long checked;
        private Long violations;
    }

    @Data
    public static class RecordItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String time;
        private Integer status;
        private String statusText;
        private String fileName;
        private String userName;
        private Integer hasViolation;
        private String resultText;
    }
}
