package org.dromara.camera.service;

import org.dromara.camera.domain.VideoAnalysisResult;

/**
 * AI检测结果管理Service接口
 * 负责接收Python端回传结果并更新数据库
 *
 * @author LionLi
 */
public interface IVideoAiResultService {

    /**
     * 更新AI分析结果
     *
     * @param result AI分析结果
     */
    void updateAnalysisResult(VideoAnalysisResult result);

    /**
     * 更新AI检测状态
     *
     * @param videoId 视频ID
     * @param status  状态（0:未检测,1:检测中,2:检测完成,3:检测失败）
     */
    void updateAiCheckStatus(Long videoId, Integer status);
}
