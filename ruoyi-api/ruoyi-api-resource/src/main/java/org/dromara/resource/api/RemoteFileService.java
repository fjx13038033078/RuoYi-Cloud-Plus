package org.dromara.resource.api;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.resource.api.domain.RemoteFile;

import java.util.List;

/**
 * 文件服务
 *
 * @author Lion Li
 */
public interface RemoteFileService {

    /**
     * 上传文件
     *
     * @param file 文件信息
     * @return 结果
     */
    RemoteFile upload(String name, String originalFilename, String contentType, byte[] file) throws ServiceException;

    /**
     * 通过ossId查询对应的url
     *
     * @param ossIds ossId串逗号分隔
     * @return url串逗号分隔
     */
    String selectUrlByIds(String ossIds);

    /**
     * 通过ossId查询列表
     *
     * @param ossIds ossId串逗号分隔
     * @return 列表
     */
    List<RemoteFile> selectByIds(String ossIds);

    /**
     * 保存已上传文件的 OSS 记录（不重新上传文件，仅写 sys_oss 表）
     * <p>适用于调用方自行上传到 OSS 后，需要在 sys_oss 登记记录的场景，
     * 避免将大文件字节数组通过 Dubbo 传输。</p>
     *
     * @param fileName     存储文件名（OSS 路径中的文件名）
     * @param originalName 原始文件名
     * @param fileSuffix   文件后缀（不含点，如 mp4）
     * @param url          文件访问 URL
     * @param service      OSS 服务商 configKey（如 minio）
     * @param ext1         扩展信息 JSON（可为 null）
     * @return 保存后的文件信息（含 ossId）
     */
    RemoteFile saveOssRecord(String fileName, String originalName, String fileSuffix,
                             String url, String service, String ext1) throws ServiceException;
}
