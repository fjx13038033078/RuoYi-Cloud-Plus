package org.dromara.camera.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.dromara.camera.domain.CameraManagement;
import org.dromara.camera.domain.bo.CameraManagementBo;
import org.dromara.camera.domain.vo.CameraManagementVo;
import org.dromara.camera.mapper.CameraManagementMapper;
import org.dromara.camera.service.ICameraManagementService;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch;
import static org.springframework.web.util.UriUtils.extractFileExtension;

/**
 * 执法视频信息管理Service业务层处理
 *
 * @author LionLi
 * @date 2025-12-05
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CameraManagementServiceImpl implements ICameraManagementService {

    private final CameraManagementMapper baseMapper;

    private final ExternalAnalysisClient externalAnalysisClient;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("mp4", "avi", "mov", "wmv", "flv", "mkv");

    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024;

    /**
     * 查询执法视频信息管理
     *
     * @param videoId 主键
     * @return 执法视频信息管理
     */
    @Override
    public CameraManagementVo queryById(Long videoId) {
        return baseMapper.selectVoById(videoId);
    }

    /**
     * 分页查询执法视频信息管理列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 执法视频信息管理分页列表
     */
    @Override
    public TableDataInfo<CameraManagementVo> queryPageList(CameraManagementBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<CameraManagement> lqw = buildQueryWrapper(bo);
        Page<CameraManagementVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的执法视频信息管理列表
     *
     * @param bo 查询条件
     * @return 执法视频信息管理列表
     */
    @Override
    public List<CameraManagementVo> queryList(CameraManagementBo bo) {
        LambdaQueryWrapper<CameraManagement> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<CameraManagement> buildQueryWrapper(CameraManagementBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<CameraManagement> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(CameraManagement::getVideoId);
        lqw.eq(StringUtils.isNotBlank(bo.getDeviceId()), CameraManagement::getDeviceId, bo.getDeviceId());
        lqw.eq(StringUtils.isNotBlank(bo.getUserCode()), CameraManagement::getUserCode, bo.getUserCode());
        lqw.like(StringUtils.isNotBlank(bo.getUserName()), CameraManagement::getUserName, bo.getUserName());
        lqw.eq(bo.getUploadTime() != null, CameraManagement::getUploadTime, bo.getUploadTime());
        lqw.eq(bo.getShootTime() != null, CameraManagement::getShootTime, bo.getShootTime());
        lqw.eq(StringUtils.isNotBlank(bo.getDurationDisplay()), CameraManagement::getDurationDisplay, bo.getDurationDisplay());
        lqw.eq(StringUtils.isNotBlank(bo.getMediaType()), CameraManagement::getMediaType, bo.getMediaType());
        lqw.eq(StringUtils.isNotBlank(bo.getFileDescription()), CameraManagement::getFileDescription, bo.getFileDescription());
        lqw.eq(StringUtils.isNotBlank(bo.getStorageLocation()), CameraManagement::getStorageLocation, bo.getStorageLocation());
        lqw.eq(StringUtils.isNotBlank(bo.getFileMark()), CameraManagement::getFileMark, bo.getFileMark());
        lqw.eq(StringUtils.isNotBlank(bo.getDataSource()), CameraManagement::getDataSource, bo.getDataSource());
        lqw.eq(bo.getAiCheckStatus() != null, CameraManagement::getAiCheckStatus, bo.getAiCheckStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getAiCheckResult()), CameraManagement::getAiCheckResult, bo.getAiCheckResult());
        lqw.eq(bo.getDataStatus() != null, CameraManagement::getDataStatus, bo.getDataStatus());
        lqw.eq(bo.getCreateBy() != null, CameraManagement::getCreateBy, bo.getCreateBy());
        lqw.eq(bo.getUpdateBy() != null, CameraManagement::getUpdateBy, bo.getUpdateBy());
        return lqw;
    }

    /**
     * 新增执法视频信息管理
     *
     * @param bo 执法视频信息管理
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(CameraManagementBo bo) {
        CameraManagement add = MapstructUtils.convert(bo, CameraManagement.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setVideoId(add.getVideoId());
        }
        return flag;
    }

    /**
     * 修改执法视频信息管理
     *
     * @param bo 执法视频信息管理
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(CameraManagementBo bo) {
        CameraManagement update = MapstructUtils.convert(bo, CameraManagement.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(CameraManagement entity) {
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除执法视频信息管理信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    //    @Override
//    public List<CameraManagement> importFromExcel(MultipartFile file) {
//        if (file.isEmpty()) {
//            throw new RuntimeException("导入文件为空");
//        }
//        try {
//            // 1. 读取Excel数据（只包含存储位置）
//            List<CameraImportExcelVo> importDataList = readExcelFile(file);
//
//            // 2. 验证并处理数据
//            List<CameraManagement> resultList = new ArrayList<>();
//            for (CameraImportExcelVo vo : importDataList) {
//                try {
//                    CameraManagement entity = convertAndValidate(vo);
//                    resultList.add(entity);
//                } catch (Exception e) {
//                    log.error("处理数据失败: {}", vo.getStorageLocation(), e);
//                    // 可以记录失败行，但继续处理其他行
//                }
//            }
//
//            // 3. 保存到数据库
//            saveBatch(resultList);
//            return resultList;
//
//        } catch (Exception e) {
//            throw new RuntimeException("Excel导入失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 转换并验证数据
//     */
//    private CameraManagement convertAndValidate(CameraImportExcelVo vo) {
//        String filePath = vo.getStorageLocation();
//
//        // 验证文件是否存在
//        File file = new File(filePath);
//        if (!file.exists()) {
//            throw new RuntimeException("文件不存在: " + filePath);
//        }
//
//        // 验证是否是视频文件
//        String mediaType = filePathParser.extractMediaTypeFromPath(filePath);
//        if (!isVideoFile(mediaType)) {
//            throw new RuntimeException("不支持的文件类型: " + mediaType);
//        }
//
//        // 从路径提取信息
//        String userName = filePathParser.extractUserNameFromPath(filePath);
//        String fileName = filePathParser.extractFileNameFromPath(filePath);
//
//        // 获取视频时长
//        String durationDisplay;
//        try {
//            durationDisplay = durationExtractor.getVideoDuration(filePath);
//
//            // 确保时长格式正确（转换为 mm:ss 格式）
//            durationDisplay = normalizeDurationFormat(durationDisplay);
//
//        } catch (Exception e) {
//            // 如果无法获取准确时长，使用简单方法
//            durationDisplay = durationExtractor.getSimpleDuration(filePath);
//            durationDisplay = normalizeDurationFormat(durationDisplay);
//        }
//
//        // 创建实体对象
//        CameraManagement entity = new CameraManagement();
//        entity.setStorageLocation(filePath);
//        entity.setUserName(userName);
//        entity.setDurationDisplay(durationDisplay);  // 现在应该是 mm:ss 格式
//        entity.setMediaType(mediaType);
//        // entity.setFileName(fileName);  // 如果数据库有该字段，取消注释
//
//        // 设置其他默认值
//        entity.setCreateTime(new Date());
//        entity.setUpdateTime(new Date());
//
//        return entity;
//    }
//
//    /**
//     * 检查是否是视频文件
//     */
//    private boolean isVideoFile(String mediaType) {
//        if (StringUtils.isBlank(mediaType)) {
//            return false;
//        }
//
//        Set<String> videoTypes = new HashSet<>(Arrays.asList(
//            "mp4", "avi", "mov", "wmv", "flv", "mkv", "mpeg", "mpg"
//        ));
//
//        return videoTypes.contains(mediaType.toLowerCase());
//    }
//
//    /**
//     * 读取Excel文件
//     */
//    private List<CameraImportExcelVo> readExcelFile(MultipartFile file) throws IOException {
//        // 使用EasyExcel读取
//        return EasyExcel.read(file.getInputStream())
//            .head(CameraImportExcelVo.class)
//            .sheet()
//            .doReadSync();
//    }
//
//    /**
//     * 标准化时长格式，确保为 mm:ss 或 HH:mm:ss
//     */
//    private String normalizeDurationFormat(String duration) {
//        if (StringUtils.isBlank(duration)) {
//            return "00:00";
//        }
//
//        // 如果包含"秒"字，转换为 mm:ss 格式
//        if (duration.contains("秒")) {
//            try {
//                // 提取数字部分
//                String numStr = duration.replaceAll("[^0-9]", "");
//                if (!StringUtils.isBlank(numStr)) {
//                    int totalSeconds = Integer.parseInt(numStr);
//                    int minutes = totalSeconds / 60;
//                    int seconds = totalSeconds % 60;
//                    return String.format("%02d:%02d", minutes, seconds);
//                }
//            } catch (Exception e) {
//                log.error("转换时长格式失败: {}", duration, e);
//            }
//            return "00:00";
//        }
//
//        // 如果已经是 mm:ss 或 HH:mm:ss 格式，直接返回
//        if (duration.matches("\\d{1,2}:\\d{2}") || duration.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
//            return duration;
//        }
//
//        return "00:00";
//    }

    /**
     * 扫描指定文件夹下的视频文件并导入到数据库
     * 方法具有事务性，异常时回滚
     *
     * @param folderPath 要扫描的文件夹路径（Windows路径，如：D:\执法记录仪）
     * @return 成功导入的CameraManagement实体列表
     * @throws IllegalArgumentException 当文件夹路径为空时抛出
     * @throws RuntimeException         当扫描或导入过程发生错误时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CameraManagement> scanInsertFromFolder(String folderPath) {
        // 参数校验：确保文件夹路径不为空
        Validate.notBlank(folderPath, "文件夹路径不能为空");

        log.info("开始扫描文件夹: {}", folderPath);

        // 使用Optional进行链式操作，避免空指针异常
        return Optional.of(folderPath)
            // 步骤1：扫描文件夹获取所有视频文件
            .map(this::scanVideoFiles)
            // 如果扫描结果为空，返回空列表
            .orElseGet(Collections::emptyList)
            .stream()
            // 步骤2：获取每个文件的绝对路径
            .map(File::getAbsolutePath)
            // 步骤3：去重，避免重复处理相同文件
            .distinct()
            // 步骤4：收集所有路径并处理保存
            .collect(Collectors.collectingAndThen(
                Collectors.toList(),
                this::processAndSaveVideos
            ));
    }

    /**
     * 扫描指定文件夹及其子文件夹，获取所有视频文件
     * 支持多种视频格式：mp4, avi, mov, wmv, flv, mkv, mpeg, mpg, webm, 3gp
     * 不区分大小写匹配文件扩展名
     *
     * @param folderPath 要扫描的文件夹路径
     * @return 视频文件列表，按扫描顺序排列
     * @throws RuntimeException 当文件夹访问失败时抛出
     */
    private List<File> scanVideoFiles(String folderPath) {
        // 视频文件扩展名的正则表达式模式
        // 匹配以指定扩展名结尾的文件，不区分大小写
        final Pattern VIDEO_PATTERN = Pattern.compile("\\.(mp4|avi|mov|wmv|flv|mkv|mpeg|mpg|webm|3gp)$",
            Pattern.CASE_INSENSITIVE);

        // 使用try-with-resources确保流资源被正确关闭
        try (Stream<Path> pathStream = Files.walk(Paths.get(folderPath))) {
            return pathStream
                // 过滤：只保留常规文件（排除目录、符号链接等）
                .filter(Files::isRegularFile)
                // 过滤：只保留视频文件（通过扩展名匹配）
                .filter(path -> VIDEO_PATTERN.matcher(path.toString()).find())
                // 转换：Path对象转换为File对象
                .map(Path::toFile)
                // 收集：转换为List集合
                .collect(Collectors.toList());
        } catch (IOException e) {
            // 记录详细错误日志，便于问题排查
            log.error("扫描文件夹失败，路径: {}", folderPath, e);
            throw new RuntimeException("扫描文件夹失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理视频文件路径列表，过滤已存在记录，构建实体并批量保存
     * 此方法会检查数据库中是否已存在相同路径的记录，避免重复导入
     *
     * @param videoPaths 视频文件的完整路径列表
     * @return 成功保存到数据库的实体列表
     */
    private List<CameraManagement> processAndSaveVideos(List<String> videoPaths) {
        // 边界检查：如果路径列表为空，直接返回空列表
        if (videoPaths.isEmpty()) {
            log.info("没有找到新的视频文件");
            return Collections.emptyList();
        }

        // 性能优化：一次性获取数据库中所有已存在的路径
        // 使用Set集合提供O(1)时间复杂度的查找
        Set<String> existingPaths = getExistingStorageLocations();

        // 使用流处理过滤和转换
        List<CameraManagement> resultList = videoPaths.stream()
            // 过滤1：排除空路径
            .filter(StringUtils::isNotBlank)
            // 过滤2：排除数据库中已存在的路径
            .filter(path -> !existingPaths.contains(normalizePath(path)))
            // 转换：将文件路径转换为实体对象
            .map(this::buildCameraManagementEntity)
            // 过滤3：排除转换失败的实体（如文件不存在）
            .filter(Objects::nonNull)
            // 收集：转换为List集合
            .collect(Collectors.toList());

        // 使用Optional处理结果，提供更清晰的逻辑分支
        return Optional.of(resultList)
            // 如果结果列表不为空，执行保存操作
            .filter(list -> !list.isEmpty())
            .map(list -> {
                // 批量保存到数据库
                saveBatch(list);
                log.info("成功导入 {} 个视频文件到数据库", list.size());
                return list;
            })
            // 如果结果列表为空，记录提示信息并返回空列表
            .orElseGet(() -> {
                log.info("所有视频文件已在数据库中存在，无需重复导入");
                return Collections.emptyList();
            });
    }

    /**
     * 从数据库查询所有已存在的视频文件存储路径
     * 此方法用于检查重复，避免导入已存在的文件
     * <p>
     * 注意：如果数据量非常大（如百万级），可能需要分批查询或使用分页
     *
     * @return 数据库中所有存储路径的集合（已规范化处理）
     */
    private Set<String> getExistingStorageLocations() {
        return Optional.ofNullable(baseMapper.selectList(null))
            // 如果查询结果为null，使用空列表替代
            .orElseGet(Collections::emptyList)
            .stream()
            // 提取每个实体的存储路径
            .map(CameraManagement::getStorageLocation)
            // 过滤：排除空路径
            .filter(StringUtils::isNotBlank)
            // 规范化：统一路径格式
            .map(this::normalizePath)
            // 收集：使用LinkedHashSet保持顺序且去重
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 根据文件路径构建CameraManagement实体对象
     * 此方法会检查文件是否存在，并对路径进行规范化处理
     *
     * @param filePath 视频文件的完整路径
     * @return CameraManagement实体对象，如果文件不存在则返回null
     */
    private CameraManagement buildCameraManagementEntity(String filePath) {
        return Optional.of(filePath)
            // 检查文件是否存在
            .filter(path -> Files.exists(Paths.get(path)))
            // 如果文件存在，构建实体
            .map(path -> {
                CameraManagement entity = new CameraManagement();

                // 存储规范化后的文件路径
                String normalizedPath = normalizePath(path);
                entity.setStorageLocation(normalizedPath);

                // 从文件路径中提取文件后缀，并存储到media_type字段
                String fileExtension = extractFileExtension(path);
                entity.setMediaType(fileExtension);

                // 从文件路径中提取用户名（如路径：D:/执法记录仪/出矿1队/张三11.1/xxx.mp4 -> 提取"张三"）
                String userName = extractUserNameFromPath(path);
                entity.setUserName(userName);

                // 设置创建时间为当前时间
                entity.setUploadTime(new Date());

                // 可根据需要添加其他字段：
                // entity.setFileName(extractFileName(path));
                // entity.setFileSize(getFileSize(path));
                // entity.setFileType(extractFileExtension(path));

                return entity;
            })
            // 如果文件不存在，记录警告并返回null
            .orElseGet(() -> {
                log.warn("文件不存在，跳过处理: {}", filePath);
                return null;
            });
    }

    /**
     * 增强版用户名提取 - 支持多种路径模式
     *
     * @param filePath 文件路径
     * @return 提取的用户名
     */
    private String extractUserNameFromPath(String filePath) {
        String normalizedPath = normalizePath(filePath);

        // 定义多种可能的路径模式
        String[] patterns = {
            "执法记录仪/[^/]+/([^/0-9.]+)[0-9.]*/",  // 模式1：执法记录仪/队名/用户名11.1/
            "/([^/0-9._-]+)[0-9._-]*/[^/]+\\.[a-zA-Z0-9]+$", // 模式2：/用户名11.1/文件名.mp4
            "/([^/]+)/[^/]+\\.[a-zA-Z0-9]+$" // 模式3：/用户名/文件名.mp4
        };

        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(normalizedPath);
            if (m.find() && m.groupCount() > 0) {
                return m.group(1);
            }
        }

        return "userName"; // 默认值
    }

    /**
     * 规范化文件路径，统一格式
     * 主要解决Windows和Unix路径分隔符不一致的问题
     * 将反斜杠(\)统一转换为正斜杠(/)，并去除首尾空格
     * 例如：D:\执法记录仪\test.mp4 → D:/执法记录仪/test.mp4
     *
     * @param path 原始文件路径
     * @return 规范化后的文件路径
     */
    private String normalizePath(String path) {
        return Optional.ofNullable(path)
            // 替换所有反斜杠为正斜杠，并去除首尾空格
            .map(p -> p.replace('\\', '/').trim())
            // 如果原始路径为null，返回null（保持一致性）
            .orElse(path);
    }

//     ==================== 可选的扩展方法 ====================

    /**
     * 从文件路径中提取文件名（不包含路径）
     *
     * @param filePath 完整文件路径
     * @return 文件名，如"test.mp4"
     */
    private String extractFileName(String filePath) {
        return Optional.ofNullable(filePath)
            .map(File::new)
            .map(File::getName)
            .orElse("");
    }

    /**
     * 获取文件大小（字节）
     *
     * @param filePath 文件路径
     * @return 文件大小，如果文件不存在返回0
     */
    private Long getFileSize(String filePath) {
        return Optional.of(filePath)
            .map(File::new)
            .filter(File::exists)
            .map(File::length)
            .orElse(0L);
    }


    /**
     * 分析视频文件
     * 该方法会对视频文件进行验证，并根据文件大小动态设置超时和重试策略，调用外部服务进行分析
     *
     * @param file 上传的视频文件（MultipartFile格式）
     * @return Map<String, Object> 包含分析结果和文件信息的映射
     * @throws RuntimeException 当文件验证失败或分析过程中发生错误时抛出
     */
    @Override
    public Map<String, Object> analyzeVideo(MultipartFile file) {
        // 1. 参数校验 - 验证视频文件的有效性
        validateVideoFile(file);

        // 根据文件大小动态设置超时和重试策略
        int maxRetries = calculateRetryCount(file.getSize()); // 计算最大重试次数
        long timeout = calculateTimeout(file.getSize());      // 计算超时时间

        // 记录分析开始日志，包含文件信息和计算出的策略参数
        log.info("开始分析视频: {}, 大小: {} MB, 最大重试次数: {}, 超时时间: {}秒",
            file.getOriginalFilename(),
            String.format("%.2f", file.getSize() / (1024.0 * 1024.0)),
            maxRetries,
            timeout / 1000);

        Exception lastException = null; // 用于记录最后一次异常，用于最终的错误报告

        // 带重试机制的调用 - 循环尝试分析，最多重试maxRetries次
        for (int retry = 0; retry <= maxRetries; retry++) {
            try {
                // 如果是重试（不是第一次尝试），先等待2秒再执行
                if (retry > 0) {
                    log.info("第 {} 次重试分析视频", retry);
                    Thread.sleep(2000); // 重试前等待2秒，避免频繁调用
                }

                // 2. 直接调用外部服务并返回原始结果
                String jsonResponse = externalAnalysisClient.analyzeVideoRaw(file);

                // 3. 解析JSON响应为Map对象
                ObjectMapper objectMapper = new ObjectMapper();
                // 配置ObjectMapper忽略未知属性，避免解析失败
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                // 将JSON字符串转换为Map对象
                Map<String, Object> result = objectMapper.readValue(jsonResponse, Map.class);

                // 4. 添加文件信息到结果中
                addFileInfo(result, file);

                log.info("视频分析成功: {}", file.getOriginalFilename());
                return result; // 分析成功，返回结果

            } catch (SocketTimeoutException e) {
                // 处理超时异常 - 记录日志并决定是否重试
                lastException = e;
                log.warn("第 {} 次尝试超时，视频大小: {} MB",
                    retry + 1,
                    String.format("%.2f", file.getSize() / (1024.0 * 1024.0)));
                // 如果已达到最大重试次数，抛出运行时异常
                if (retry == maxRetries) {
                    log.error("视频分析超时，已达到最大重试次数", e);
                    throw new RuntimeException("视频分析超时，请尝试上传较小文件或稍后重试");
                }
            } catch (IOException e) {
                // 处理IO异常 - 记录日志并决定是否重试
                lastException = e;
                // 如果已达到最大重试次数，抛出运行时异常
                if (retry == maxRetries) {
                    log.error("视频分析失败", e);
                    throw new RuntimeException("视频分析失败: " + e.getMessage());
                }
            } catch (Exception e) {
                // 处理其他类型的异常 - 直接抛出，不重试
                log.error("视频分析异常", e);
                throw new RuntimeException("视频分析失败: " + e.getMessage());
            }
        }

        // 所有重试都失败后，抛出运行时异常
        throw new RuntimeException("视频分析失败: " + (lastException != null ? lastException.getMessage() : "未知错误"));
    }

    /**
     * 根据文件大小计算重试次数
     * 文件越大，需要的重试次数越多，因为大文件上传和分析更容易失败
     *
     * @param fileSize 文件大小（字节）
     * @return 重试次数
     */
    private int calculateRetryCount(long fileSize) {
        if (fileSize > 100 * 1024 * 1024) { // 大于100MB
            return 3; // 大文件，允许最多3次重试
        } else if (fileSize > 50 * 1024 * 1024) { // 50-100MB
            return 2; // 中等文件，允许最多2次重试
        } else {
            return 1; // 小文件，允许最多1次重试
        }
    }

    /**
     * 根据文件大小计算超时时间
     * 文件越大，需要的超时时间越长
     *
     * @param fileSize 文件大小（字节）
     * @return 超时时间（毫秒）
     */
    private long calculateTimeout(long fileSize) {
        // 基本时间 + 根据文件大小增加的额外时间
        long baseTimeout = 60000; // 60秒基础时间
        long additionalTimeout = fileSize / (1024 * 1024) * 1000; // 每MB增加1秒

        // 返回计算出的超时时间，但最长不超过10分钟（600000毫秒）
        return Math.min(baseTimeout + additionalTimeout, 600000);
    }

    /**
     * 添加文件信息到分析结果中
     *
     * @param result 分析结果Map
     * @param file   上传的视频文件
     */
    private void addFileInfo(Map<String, Object> result, MultipartFile file) {
        result.put("upload_file_name", file.getOriginalFilename()); // 原始文件名
        result.put("upload_file_size", formatFileSize(file.getSize())); // 格式化后的文件大小
        result.put("upload_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); // 上传时间
        result.put("file_format", getFileExtension(file.getOriginalFilename())); // 文件格式（扩展名）
        result.put("file_size_bytes", file.getSize()); // 文件大小（字节）
    }

    /**
     * 验证视频文件的有效性
     * 检查文件是否为空、文件名是否有效、文件格式是否支持、文件大小是否超限
     *
     * @param file 上传的视频文件
     * @throws IllegalArgumentException 当文件不符合要求时抛出
     */
    private void validateVideoFile(MultipartFile file) {
        // 检查文件是否为空
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的视频文件");
        }

        // 检查文件名是否有效
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 检查文件格式是否支持
        String extension = getFileExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件格式: " + extension);
        }

        // 检查文件大小是否超限
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过500MB");
        }

        // 记录验证通过日志（实际检查视频时长的逻辑可根据需要添加）
        log.info("视频文件验证通过: {}, 大小: {} MB",
            filename,
            String.format("%.2f", file.getSize() / (1024.0 * 1024.0)));
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 文件扩展名（不含点号），如果无扩展名则返回空字符串
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        // 确保点号不在开头或结尾
        return lastDotIndex > 0 && lastDotIndex < filename.length() - 1 ?
            filename.substring(lastDotIndex + 1) : "";
    }

    /**
     * 格式化文件大小，将字节数转换为易读的格式（B/KB/MB/GB）
     *
     * @param size 文件大小（字节）
     * @return 格式化后的文件大小字符串
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B"; // 小于1KB，显示字节
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0); // 小于1MB，显示KB
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0)); // 小于1GB，显示MB
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0)); // 大于等于1GB，显示GB
    }
}
