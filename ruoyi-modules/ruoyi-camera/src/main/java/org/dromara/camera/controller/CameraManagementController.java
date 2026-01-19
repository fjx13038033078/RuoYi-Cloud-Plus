package org.dromara.camera.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.bo.CameraManagementBo;
import org.dromara.camera.domain.vo.CameraImportExcelVo;
import org.dromara.camera.domain.vo.CameraManagementVo;
import org.dromara.camera.service.ICameraManagementService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 执法视频信息管理
 *
 * @author LionLi
 * @date 2025-12-05
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/camera/management")
public class CameraManagementController extends BaseController {

    private final ICameraManagementService cameraManagementService;

    /**
     * 查询执法视频信息管理列表
     */
    @SaCheckPermission("camera:management:list")
    @GetMapping("/list")
    public TableDataInfo<CameraManagementVo> list(CameraManagementBo bo, PageQuery pageQuery) {
        return cameraManagementService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出执法视频信息管理列表
     */
    @SaCheckPermission("camera:management:export")
    @Log(title = "执法视频信息管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(CameraManagementBo bo, HttpServletResponse response) {
        List<CameraManagementVo> list = cameraManagementService.queryList(bo);
        ExcelUtil.exportExcel(list, "执法视频信息管理", CameraManagementVo.class, response);
    }

    /**
     * 扫描文件夹导入视频
     */
    @SaCheckPermission("camera:management:scanInsert")
    @Log(title = "执法视频信息管理", businessType = BusinessType.IMPORT)
    @PostMapping("/scanInsert")
    public R<List<CameraManagement>> scanInsert(@RequestParam("folderPath") String folderPath) {
        try {
            List<CameraManagement> results = cameraManagementService.scanInsertFromFolder(folderPath);
            return R.ok(results);
        } catch (Exception e) {
            return R.fail("扫描导入失败: " + e.getMessage());
        }
    }

    /**
     * 下载导入模板
     */
    @SaCheckPermission("camera:management:downloadTemplate")
    @GetMapping("/downloadTemplate")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("执法视频导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 创建空数据列表，只生成表头
        List<CameraImportExcelVo> list = new ArrayList<>();

        EasyExcel.write(response.getOutputStream(), CameraImportExcelVo.class)
            .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
            .sheet("模板")
            .doWrite(list);
    }

    /**
     * 获取执法视频信息管理详细信息
     *
     * @param videoId 主键
     */
    @SaCheckPermission("camera:management:query")
    @GetMapping("/{videoId}")
    public R<CameraManagementVo> getInfo(@NotNull(message = "主键不能为空")
                                         @PathVariable("videoId") Long videoId) {
        return R.ok(cameraManagementService.queryById(videoId));
    }

    /**
     * 新增执法视频信息管理
     */
    @SaCheckPermission("camera:management:add")
    @Log(title = "执法视频信息管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody CameraManagementBo bo) {
        return toAjax(cameraManagementService.insertByBo(bo));
    }

    /**
     * 修改执法视频信息管理
     */
    @SaCheckPermission("camera:management:edit")
    @Log(title = "执法视频信息管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/edit")
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody CameraManagementBo bo) {
        return toAjax(cameraManagementService.updateByBo(bo));
    }

    /**
     * 删除执法视频信息管理
     *
     * @param videoIds 主键串
     */
    @SaCheckPermission("camera:management:remove")
    @Log(title = "执法视频信息管理", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @RequestBody Long[] videoIds) {
        return toAjax(cameraManagementService.deleteWithValidByIds(List.of(videoIds), true));
    }

    /**
     * 上传视频并调用AI分析
     */
    @SaCheckPermission("camera:management:upload")
    @PostMapping("/upload")
    public R<Map<String, Object>> uploadVideo(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = cameraManagementService.analyzeVideo(file);
            return R.ok("视频分析成功", result);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            return R.fail("视频分析失败: " + e.getMessage());
        }
    }
}
