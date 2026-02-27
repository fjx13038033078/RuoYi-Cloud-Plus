package org.dromara.camera.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 视频分析Service接口
 * 负责上传视频文件并调用外部AI服务进行分析
 *
 * @author LionLi
 */
public interface IVideoAnalysisService {

    /**
     * 分析视频文件
     *
     * @param file 上传的视频文件
     * @return 分析结果
     */
    Map<String, Object> analyzeVideo(MultipartFile file);
}
