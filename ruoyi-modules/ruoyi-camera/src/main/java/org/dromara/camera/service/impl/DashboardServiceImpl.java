package org.dromara.camera.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.vo.DashboardStatsVo;
import org.dromara.camera.mapper.CameraManagementMapper;
import org.dromara.camera.service.IDashboardService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据大屏统计Service实现
 *
 * @author LionLi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DashboardServiceImpl implements IDashboardService {

    private final CameraManagementMapper baseMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final Map<Integer, String> STATUS_TEXT_MAP = Map.of(
        0, "未检测", 1, "检测中", 2, "检测完成", 3, "检测失败"
    );

    @Override
    public DashboardStatsVo getStats() {
        DashboardStatsVo vo = new DashboardStatsVo();

        // 核心指标
        buildCoreStats(vo);
        // 近7天趋势
        vo.setTrendData(buildTrendData());
        // 违规类型分布
        vo.setViolationTypes(buildViolationTypes());
        // 部门统计
        vo.setDeptStats(buildDeptStats());
        // 最新记录
        vo.setRecentRecords(buildRecentRecords());

        return vo;
    }

    private void buildCoreStats(DashboardStatsVo vo) {
        long total = baseMapper.selectCount(Wrappers.lambdaQuery());

        long checked = baseMapper.selectCount(Wrappers.<CameraManagement>lambdaQuery()
            .eq(CameraManagement::getAiCheckStatus, 2));

        long violation = baseMapper.selectCount(Wrappers.<CameraManagement>lambdaQuery()
            .eq(CameraManagement::getHasViolation, 1));

        vo.setTotalVideos(total);
        vo.setCheckedCount(checked);
        vo.setViolationCount(violation);

        // 通过率：(已检测 - 违规) / 已检测
        if (checked > 0) {
            double rate = (double) (checked - violation) / checked * 100;
            vo.setPassRate(String.format("%.1f%%", rate));
        } else {
            vo.setPassRate("--");
        }

        // 今日 / 昨日新增
        LocalDate today = LocalDate.now();
        vo.setTodayNew(countByDate(today));
        vo.setYesterdayNew(countByDate(today.minusDays(1)));

        // 平均处理时长
        List<CameraManagement> withTime = baseMapper.selectList(
            Wrappers.<CameraManagement>lambdaQuery()
                .isNotNull(CameraManagement::getProcessTime)
                .gt(CameraManagement::getProcessTime, 0));
        if (!withTime.isEmpty()) {
            double avgSeconds = withTime.stream()
                .mapToDouble(CameraManagement::getProcessTime)
                .average().orElse(0);
            vo.setAvgProcessTime(formatDuration(avgSeconds));
        } else {
            vo.setAvgProcessTime("--");
        }
    }

    private long countByDate(LocalDate date) {
        Date dayStart = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dayEnd = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        return baseMapper.selectCount(Wrappers.<CameraManagement>lambdaQuery()
            .ge(CameraManagement::getUploadTime, dayStart)
            .lt(CameraManagement::getUploadTime, dayEnd));
    }

    private List<DashboardStatsVo.TrendItem> buildTrendData() {
        LocalDate today = LocalDate.now();
        Date sevenDaysAgo = Date.from(today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<CameraManagement> list = baseMapper.selectList(
            Wrappers.<CameraManagement>lambdaQuery()
                .ge(CameraManagement::getUploadTime, sevenDaysAgo)
                .select(CameraManagement::getUploadTime, CameraManagement::getHasViolation));

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd");
        Map<String, List<CameraManagement>> grouped = list.stream()
            .filter(c -> c.getUploadTime() != null)
            .collect(Collectors.groupingBy(c -> sdf.format(c.getUploadTime())));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<DashboardStatsVo.TrendItem> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String dateKey = today.minusDays(i).format(fmt);
            DashboardStatsVo.TrendItem item = new DashboardStatsVo.TrendItem();
            item.setDate(dateKey);

            List<CameraManagement> dayList = grouped.getOrDefault(dateKey, Collections.emptyList());
            item.setTotal((long) dayList.size());
            item.setViolations(dayList.stream()
                .filter(c -> c.getHasViolation() != null && c.getHasViolation() == 1)
                .count());
            result.add(item);
        }
        return result;
    }

    private List<DashboardStatsVo.TypeItem> buildViolationTypes() {
        List<CameraManagement> violations = baseMapper.selectList(
            Wrappers.<CameraManagement>lambdaQuery()
                .eq(CameraManagement::getHasViolation, 1)
                .isNotNull(CameraManagement::getViolationType)
                .select(CameraManagement::getViolationType));

        return violations.stream()
            .filter(c -> StringUtils.isNotBlank(c.getViolationType()))
            .collect(Collectors.groupingBy(CameraManagement::getViolationType, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(e -> {
                DashboardStatsVo.TypeItem item = new DashboardStatsVo.TypeItem();
                item.setName(e.getKey());
                item.setCount(e.getValue());
                return item;
            })
            .collect(Collectors.toList());
    }

    private List<DashboardStatsVo.DeptItem> buildDeptStats() {
        List<CameraManagement> all = baseMapper.selectList(
            Wrappers.<CameraManagement>lambdaQuery()
                .isNotNull(CameraManagement::getDeptId)
                .select(CameraManagement::getDeptId,
                    CameraManagement::getAiCheckStatus,
                    CameraManagement::getHasViolation));

        Map<Long, List<CameraManagement>> grouped = all.stream()
            .filter(c -> c.getDeptId() != null)
            .collect(Collectors.groupingBy(CameraManagement::getDeptId));

        // 查询部门名称
        Map<Long, String> deptNames = queryDeptNames(grouped.keySet());

        return grouped.entrySet().stream()
            .map(e -> {
                DashboardStatsVo.DeptItem item = new DashboardStatsVo.DeptItem();
                item.setDeptId(e.getKey());
                item.setDeptName(deptNames.getOrDefault(e.getKey(), "部门" + e.getKey()));
                item.setChecked(e.getValue().stream()
                    .filter(c -> c.getAiCheckStatus() != null && c.getAiCheckStatus() == 2L)
                    .count());
                item.setViolations(e.getValue().stream()
                    .filter(c -> c.getHasViolation() != null && c.getHasViolation() == 1)
                    .count());
                return item;
            })
            .sorted(Comparator.comparingLong(DashboardStatsVo.DeptItem::getChecked).reversed())
            .collect(Collectors.toList());
    }

    private Map<Long, String> queryDeptNames(Set<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            String ids = deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String sql = "SELECT dept_id, dept_name FROM sys_dept WHERE dept_id IN (" + ids + ")";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r.get("dept_id")).longValue(),
                r -> (String) r.get("dept_name"),
                (a, b) -> a));
        } catch (Exception e) {
            log.warn("查询部门名称失败，将使用部门ID代替: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<DashboardStatsVo.RecordItem> buildRecentRecords() {
        List<CameraManagement> recent = baseMapper.selectList(
            Wrappers.<CameraManagement>lambdaQuery()
                .orderByDesc(CameraManagement::getUploadTime)
                .last("LIMIT 10"));

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");
        return recent.stream().map(c -> {
            DashboardStatsVo.RecordItem item = new DashboardStatsVo.RecordItem();
            item.setTime(c.getUploadTime() != null ? timeFmt.format(c.getUploadTime()) : "--");
            int status = c.getAiCheckStatus() != null ? c.getAiCheckStatus().intValue() : 0;
            item.setStatus(status);
            item.setStatusText(STATUS_TEXT_MAP.getOrDefault(status, "未知"));

            String path = c.getStorageLocation();
            item.setFileName(path != null && path.contains("/")
                ? path.substring(path.lastIndexOf("/") + 1) : path);

            item.setUserName(c.getUserName() != null ? c.getUserName() : "--");
            item.setHasViolation(c.getHasViolation() != null ? c.getHasViolation() : 0);

            if (status == 2) {
                item.setResultText(item.getHasViolation() == 1 ? "发现违规" : "未发现违规");
            } else if (status == 1) {
                item.setResultText("处理中...");
            } else if (status == 3) {
                item.setResultText("检测失败");
            } else {
                item.setResultText("待检测");
            }
            return item;
        }).collect(Collectors.toList());
    }

    private String formatDuration(double totalSeconds) {
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) (totalSeconds % 60);
        if (minutes > 0) {
            return minutes + "m" + seconds + "s";
        }
        return seconds + "s";
    }
}
