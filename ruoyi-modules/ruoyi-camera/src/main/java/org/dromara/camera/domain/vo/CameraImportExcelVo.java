package org.dromara.camera.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * Excel数据导入实体类
 */
@Data
public class CameraImportExcelVo {

    @ExcelProperty("存储位置")
    private String storageLocation;

    // 这些字段将通过解析文件路径自动生成，不需要在Excel中填写
    private String userName;       // 从路径提取
    private String durationDisplay; // 从视频文件提取
    private String mediaType;      // 从文件后缀提取
    private String fileName;       // 文件名（可选）
}
