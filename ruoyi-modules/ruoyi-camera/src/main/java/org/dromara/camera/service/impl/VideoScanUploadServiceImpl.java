package org.dromara.camera.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.VideoUploadMessage;
import org.dromara.camera.mapper.CameraManagementMapper;
import org.dromara.camera.service.IVideoMessageService;
import org.dromara.camera.service.IVideoScanUploadService;
import org.dromara.camera.utils.VideoFileUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.web.util.UriUtils.extractFileExtension;

/**
 * 视频扫描上传Service实现
 * 负责文件夹扫描、文件上传到 OSS、数据库记录写入
 *
 * @author LionLi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoScanUploadServiceImpl implements IVideoScanUploadService {

    private final CameraManagementMapper baseMapper;
    private final JdbcTemplate jdbcTemplate;
    private final IVideoMessageService videoMessageService;

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofHours(24);

    private static final Pattern VIDEO_PATTERN = Pattern.compile(
        "\\.(mp4|avi|mov|wmv|flv|mkv|mpeg|mpg|webm|3gp)$", Pattern.CASE_INSENSITIVE);

    @Override
    public List<CameraManagement> scanInsertFromFolder(String folderPath) {
        return scanInsertFromFolder(folderPath, true);
    }

    @Override
    public List<CameraManagement> scanInsertFromFolder(String folderPath, boolean triggerAiAnalysis) {
        Validate.notBlank(folderPath, "文件夹路径不能为空");
        log.info("开始扫描文件夹: {}, triggerAiAnalysis={}", folderPath, triggerAiAnalysis);

        try {
            List<String> allVideoPaths = scanVideoFiles(folderPath);
            if (allVideoPaths.isEmpty()) {
                log.info("未找到视频文件");
                return Collections.emptyList();
            }

            List<String> normalizedPaths = allVideoPaths.stream()
                .map(VideoFileUtils::normalizePath)
                .collect(Collectors.toList());

            Set<String> existingPaths = getExistingStorageLocations();

            List<String> newFilePaths = normalizedPaths.stream()
                .filter(path -> !existingPaths.contains(path))
                .distinct()
                .collect(Collectors.toList());

            if (newFilePaths.isEmpty()) {
                log.info("所有视频文件已在数据库中存在");
                return Collections.emptyList();
            }

            log.info("发现 {} 个新视频文件", newFilePaths.size());

            List<CameraManagement> successEntities = new ArrayList<>();
            for (String filePath : newFilePaths) {
                try {
                    CameraManagement result = processAndUploadSingleVideo(filePath, triggerAiAnalysis);
                    if (result != null) {
                        successEntities.add(result);
                    }
                } catch (Exception e) {
                    log.error("处理单个视频文件失败：{}，错误：{}", filePath, e.getMessage());
                }
            }

            log.info("视频处理完成，成功：{}，总数：{}", successEntities.size(), newFilePaths.size());
            return successEntities;

        } catch (Exception e) {
            log.error("扫描和处理文件夹失败: {}", folderPath, e);
            throw new RuntimeException("处理失败: " + e.getMessage(), e);
        }
    }

    private List<String> scanVideoFiles(String folderPath) {
        try (Stream<Path> pathStream = Files.walk(Paths.get(folderPath))) {
            return pathStream
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .filter(path -> VIDEO_PATTERN.matcher(path).find())
                .distinct()
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描文件夹失败，路径: {}", folderPath, e);
            throw new RuntimeException("扫描文件夹失败: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public CameraManagement processAndUploadSingleVideo(String filePath) {
        return processAndUploadSingleVideo(filePath, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public CameraManagement processAndUploadSingleVideo(String filePath, boolean triggerAiAnalysis) {
        if (isPathExistsInDatabase(filePath)) {
            log.info("文件已存在于数据库中，跳过：{}", filePath);
            return null;
        }

        File sourceFile = new File(filePath);
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            log.warn("视频文件无效或不存在：{}", filePath);
            return null;
        }

        CameraManagement entity = buildCameraManagementEntity(filePath);
        if (entity == null) {
            return null;
        }

        String contentType = VideoFileUtils.detectContentType(sourceFile);
        String suffix = VideoFileUtils.extractFileSuffix(sourceFile);
        int maxRetries = 3;

        for (int retry = 0; retry < maxRetries; retry++) {
            Path tempFile = null;
            try {
                if (retry > 0) {
                    log.info("第 {} 次重试上传文件：{}", retry, sourceFile.getName());
                    Thread.sleep(2000);
                }

                OssClient currentClient = OssFactory.instance();
                if (currentClient == null) {
                    log.error("OssClient获取失败，第 {} 次尝试", retry + 1);
                    continue;
                }

                tempFile = Files.createTempFile("camera_upload_", suffix);
                Files.copy(sourceFile.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);

                UploadResult uploadResult = currentClient.uploadSuffix(tempFile.toFile(), suffix);
                if (uploadResult == null) {
                    log.error("文件上传返回结果为空：{}", sourceFile.getName());
                    continue;
                }

                if (isPathExistsInDatabase(filePath)) {
                    log.info("文件在上传过程中已被其他线程处理，跳过保存：{}", filePath);
                    return null;
                }

                baseMapper.insert(entity);

                Long ossId = saveToSysOss(entity, currentClient, sourceFile, uploadResult, contentType);
                if (ossId != null) {
                    entity.setOssId(ossId);
                    baseMapper.updateById(entity);
                }

                log.info("文件处理成功：{}，OSS ID：{}，文件大小：{} MB",
                    sourceFile.getName(), ossId, String.format("%.2f", sourceFile.length() / (1024.0 * 1024.0)));

                if (triggerAiAnalysis) {
                    sendUploadSuccessMessage(entity, uploadResult, sourceFile, currentClient);
                } else {
                    log.info("已跳过 AI 检测 MQ：videoId={}（triggerAiAnalysis=false）", entity.getVideoId());
                }

                return entity;

            } catch (Exception e) {
                log.error("处理视频文件失败（第 {} 次尝试）：{}，错误：{}",
                    retry + 1, filePath, e.getMessage());
                if (retry == maxRetries - 1) {
                    log.error("处理文件最终失败，已达到最大重试次数：{}", filePath, e);
                }
            } finally {
                if (tempFile != null) {
                    try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                }
            }
        }

        return null;
    }

    // ======================== 私有辅助方法 ========================

    private boolean isPathExistsInDatabase(String filePath) {
        String normalizedPath = VideoFileUtils.normalizePath(filePath);
        LambdaQueryWrapper<CameraManagement> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(CameraManagement::getStorageLocation, normalizedPath);
        return baseMapper.selectCount(wrapper) > 0;
    }

    private Set<String> getExistingStorageLocations() {
        List<CameraManagement> existingList = baseMapper.selectList(
            new QueryWrapper<CameraManagement>().select("storage_location")
        );
        return existingList.stream()
            .map(CameraManagement::getStorageLocation)
            .filter(StringUtils::isNotBlank)
            .map(VideoFileUtils::normalizePath)
            .collect(Collectors.toSet());
    }

    private CameraManagement buildCameraManagementEntity(String filePath) {
        return Optional.of(filePath)
            .filter(path -> Files.exists(Paths.get(path)))
            .map(path -> {
                CameraManagement entity = new CameraManagement();
                entity.setStorageLocation(VideoFileUtils.normalizePath(path));
                entity.setMediaType(extractFileExtension(path));
                entity.setUserName(VideoFileUtils.extractUserNameFromPath(path));
                entity.setUploadTime(new Date());
                entity.setDataSource("scan");

                File file = new File(path);

                // 从文件名解析序列号、用户编号、拍摄时间
                VideoFileUtils.VideoFileNameInfo nameInfo = VideoFileUtils.parseVideoFileName(file.getName());
                if (nameInfo.isParsed()) {
                    entity.setSerialNumber(nameInfo.getSerialNumber());
                    entity.setUserCode(nameInfo.getUserCode());
                    entity.setShootTime(nameInfo.getShootTime());
                    log.info("文件名解析成功：序列号={}, 用户编号={}, 拍摄时间={}",
                        nameInfo.getSerialNumber(), nameInfo.getUserCode(), nameInfo.getShootTime());
                } else {
                    log.warn("文件名解析失败，将使用默认值：{}", file.getName());
                }

                // 读取视频文件时长
                String duration = VideoFileUtils.getFormattedDuration(file);
                if (duration != null) {
                    entity.setDurationDisplay(duration);
                    log.info("视频时长解析成功：{} -> {}", file.getName(), duration);
                } else {
                    log.warn("视频时长解析失败或不支持的格式：{}", file.getName());
                }

                return entity;
            })
            .orElseGet(() -> {
                log.warn("文件不存在，跳过处理: {}", filePath);
                return null;
            });
    }

    private Long saveToSysOss(CameraManagement camera, OssClient ossClient,
                              File file, UploadResult uploadResult, String contentType) {
        try {
            Long ossId = IdWorker.getId();
            Map<String, Object> extMap = Map.of(
                "fileSize", file.length(),
                "contentType", contentType,
                "originalPath", file.getAbsolutePath(),
                "uploadTime", new Date()
            );

            String sql = "INSERT INTO sys_oss (oss_id, tenant_id, file_name, original_name, " +
                "file_suffix, url, ext1, create_dept, create_time, create_by, " +
                "update_time, update_by, service) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            String fileName = uploadResult.getFilename();
            fileName = fileName.contains("/") ? fileName.substring(fileName.lastIndexOf("/") + 1) : fileName;
            Date now = new Date();

            Object[] params = {
                ossId, "000000", fileName, file.getName(),
                VideoFileUtils.extractFileSuffixWithoutDot(file),
                uploadResult.getUrl(),
                JsonUtils.toJsonString(extMap),
                VideoFileUtils.getCurrentDeptId(), now, VideoFileUtils.getCurrentUserId(),
                now, VideoFileUtils.getCurrentUserId(), ossClient.getConfigKey()
            };

            int rows = jdbcTemplate.update(sql, params);
            return rows > 0 ? ossId : null;
        } catch (Exception e) {
            log.error("保存OSS记录失败：{}", file.getName(), e);
            return null;
        }
    }

    private void sendUploadSuccessMessage(CameraManagement camera, UploadResult uploadResult,
                                          File file, OssClient ossClient) {
        String originalUrl = uploadResult.getUrl();
        String objectName = ossClient.removeBaseUrl(originalUrl);
        String bucketName = VideoFileUtils.extractBucketName(originalUrl);

        VideoUploadMessage.Metadata metadata = VideoUploadMessage.Metadata.builder()
            .videoCode(VideoFileUtils.extractVideoCode(file.getName()))
            .userName(camera.getUserName())
            .recordTime(camera.getShootTime() != null ? camera.getShootTime().toString() : null)
            .fileName(file.getName())
            .fileSize(file.length())
            .contentType(VideoFileUtils.detectContentType(file))
            .originalPath(file.getAbsolutePath())
            .build();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSendMessage(camera.getVideoId(), ossClient, objectName, bucketName, originalUrl, metadata);
                }
            });
        } else {
            doSendMessage(camera.getVideoId(), ossClient, objectName, bucketName, originalUrl, metadata);
        }
    }

    private void doSendMessage(Long videoId, OssClient ossClient, String objectName,
                               String bucketName, String originalUrl,
                               VideoUploadMessage.Metadata metadata) {
        try {
            String presignedUrl = ossClient.getPrivateUrl(objectName, PRESIGNED_URL_EXPIRATION);
            videoMessageService.sendVideoDetectionMessage(
                videoId, presignedUrl, bucketName, objectName, originalUrl, metadata);
            log.info("已发送视频检测消息到RabbitMQ: videoId={}, presignedUrl有效期=24小时", videoId);
        } catch (Exception e) {
            log.error("发送RabbitMQ消息失败，但文件上传已成功: videoId={}", videoId, e);
        }
    }
}
