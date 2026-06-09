package org.dromara.resource.dubbo;

import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.resource.api.RemoteFileService;
import org.dromara.resource.api.domain.RemoteFile;
import org.dromara.resource.domain.SysOssExt;
import org.dromara.resource.domain.bo.SysOssBo;
import org.dromara.resource.domain.vo.SysOssVo;
import org.dromara.resource.service.ISysOssService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文件请求处理
 *
 * @author Lion Li
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DubboService
public class RemoteFileServiceImpl implements RemoteFileService {

    private final ISysOssService sysOssService;

    /**
     * 文件上传请求
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public RemoteFile upload(String name, String originalFilename, String contentType, byte[] file) throws ServiceException {
        try {
            String suffix = StringUtils.substring(originalFilename, originalFilename.lastIndexOf("."), originalFilename.length());
            OssClient storage = OssFactory.instance();
            UploadResult uploadResult = storage.uploadSuffix(file, suffix, contentType);
            // 保存文件信息
            SysOssBo oss = new SysOssBo();
            oss.setUrl(uploadResult.getUrl());
            oss.setFileSuffix(suffix);
            oss.setFileName(uploadResult.getFilename());
            oss.setOriginalName(originalFilename);
            oss.setService(storage.getConfigKey());
            SysOssExt ext1 = new SysOssExt();
            ext1.setFileSize((long) file.length);
            String extStr = JsonUtils.toJsonString(ext1);
            oss.setExt1(extStr);
            sysOssService.insertByBo(oss);
            RemoteFile sysFile = new RemoteFile();
            sysFile.setOssId(oss.getOssId());
            sysFile.setName(uploadResult.getFilename());
            sysFile.setUrl(uploadResult.getUrl());
            sysFile.setOriginalName(originalFilename);
            sysFile.setFileSuffix(suffix);
            sysFile.setExt1(extStr);
            return sysFile;
        } catch (Exception e) {
            log.error("上传文件失败", e);
            throw new ServiceException("上传文件失败");
        }
    }

    /**
     * 通过ossId查询对应的url
     *
     * @param ossIds ossId串逗号分隔
     * @return url串逗号分隔
     */
    @Override
    public String selectUrlByIds(String ossIds) {
        return sysOssService.selectUrlByIds(ossIds);
    }

    /**
     * 保存已上传文件的 OSS 记录（不重新上传，仅写 sys_oss 表）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RemoteFile saveOssRecord(String fileName, String originalName, String fileSuffix,
                                    String url, String service, String ext1) {
        try {
            SysOssBo oss = new SysOssBo();
            oss.setFileName(fileName);
            oss.setOriginalName(originalName);
            oss.setFileSuffix(fileSuffix);
            oss.setUrl(url);
            oss.setService(service);
            oss.setExt1(ext1);
            sysOssService.insertByBo(oss);

            RemoteFile result = new RemoteFile();
            result.setOssId(oss.getOssId());
            result.setName(fileName);
            result.setUrl(url);
            result.setOriginalName(originalName);
            result.setFileSuffix(fileSuffix);
            result.setExt1(ext1);
            return result;
        } catch (Exception e) {
            log.error("保存OSS记录失败: fileName={}", fileName, e);
            throw new ServiceException("保存OSS记录失败: " + e.getMessage());
        }
    }

    /**
     * 通过ossId查询列表
     *
     * @param ossIds ossId串逗号分隔
     * @return 列表
     */
    @Override
    public List<RemoteFile> selectByIds(String ossIds){
        List<SysOssVo> sysOssVos = sysOssService.listByIds(StringUtils.splitTo(ossIds, Convert::toLong));
        return sysOssVos.stream().map(vo -> {
            RemoteFile file = new RemoteFile();
            file.setOssId(vo.getOssId());
            file.setName(vo.getFileName());
            file.setUrl(vo.getUrl());
            file.setOriginalName(vo.getOriginalName());
            file.setFileSuffix(vo.getFileSuffix());
            file.setExt1(vo.getExt1());
            return file;
        }).toList();
    }
}
