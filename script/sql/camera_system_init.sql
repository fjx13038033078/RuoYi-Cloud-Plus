-- ============================================================================
-- 执法视频 AI 检测与视频切割系统 —— 数据库初始化脚本（全新部署一键执行）
-- ----------------------------------------------------------------------------
-- 本脚本整合自历史增量脚本，反映当前最新数据库结构：
--   add_camera_events_json.sql / add_video_clip.sql / add_video_clip_ai_fields.sql
--   update_camera_data_source_dict.sql / update_video_clip_source_info.sql
--
-- 适用范围：在已导入 RuoYi-Cloud-Plus 框架基础库（ry-cloud.sql 等）的 MySQL 上执行。
-- 执行后即可获得：camera_management 主表、video_clip 切片表、camera_data_source 字典、
--               以及"执法视频检测"相关菜单与按钮权限。
-- 可重复执行：表用 IF NOT EXISTS；菜单/字典先删后插，重复执行不会产生脏数据。
-- 注意：camera_management / video_clip 的列顺序与类型依据实体类重建，
--       功能上与线上库完全兼容；如需与线上库二进制级一致，可用线上库
--       SHOW CREATE TABLE 结果替换第 1、2 节。
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. camera_management —— 执法视频信息主表（含 AI 检测结果、人工复判）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `camera_management`
(
    `video_id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '视频ID（主键）',
    `serial_number`          VARCHAR(255)          DEFAULT NULL COMMENT '视频唯一序列号（从文件名解析，如 Q541062）',
    `device_id`              VARCHAR(255)          DEFAULT NULL COMMENT '来源设备',
    `user_code`              VARCHAR(64)           DEFAULT NULL COMMENT '执法人员编号（采集者/使用者）',
    `user_name`              VARCHAR(64)           DEFAULT NULL COMMENT '用户姓名',
    `upload_time`            DATETIME              DEFAULT NULL COMMENT '上传时间（入库时间戳）',
    `dept_id`                BIGINT                DEFAULT NULL COMMENT '部门ID',
    `shoot_time`             DATETIME              DEFAULT NULL COMMENT '拍摄时间（视频实际拍摄时间）',
    `duration_display`       VARCHAR(32)           DEFAULT NULL COMMENT '视频时长显示（如 05:30）',
    `media_type`             VARCHAR(32)           DEFAULT NULL COMMENT '媒体类型（mp4/mov/avi 等）',
    `file_description`       VARCHAR(500)          DEFAULT NULL COMMENT '文件描述',
    `storage_location`       VARCHAR(1024)         DEFAULT NULL COMMENT '存储位置（对象存储URL/文件系统路径）',
    `file_mark`              VARCHAR(255)          DEFAULT NULL COMMENT '文件标记（预留字段）',
    `data_source`            VARCHAR(32)           DEFAULT NULL COMMENT '数据来源（scan=AI检测扫描 / clip=视频切割扫描）',
    `ai_check_status`        BIGINT                DEFAULT 0 COMMENT 'AI检测状态（0:未检测 1:检测中 2:检测完成 3:检测失败）',
    `ai_check_result`        TEXT                  DEFAULT NULL COMMENT 'AI检测结果（JSON结构化描述）',
    `events_json`            TEXT                  DEFAULT NULL COMMENT 'AI检测事件列表JSON（含每个违规事件对应的相关规章制度）',
    `has_violation`          INT                   DEFAULT 0 COMMENT '是否有违规行为（0:否 1:是）',
    `violation_type`         VARCHAR(255)          DEFAULT NULL COMMENT '违规类型（如：未戴安全帽、违规操作等）',
    `violation_start_second` DOUBLE                DEFAULT NULL COMMENT '违规行为起始时间点（秒）',
    `violation_end_second`   DOUBLE                DEFAULT NULL COMMENT '违规行为结束时间点（秒）',
    `screenshot_url`         VARCHAR(2048)         DEFAULT NULL COMMENT '违规截图URL',
    `process_time`           DOUBLE                DEFAULT NULL COMMENT 'AI检测耗时（秒）',
    `check_time`             DATETIME              DEFAULT NULL COMMENT 'AI检测完成时间',
    `data_status`            BIGINT                DEFAULT 1 COMMENT '数据状态（1:正常 0:删除）',
    `oss_id`                 BIGINT                DEFAULT NULL COMMENT 'OSS文件ID（关联 sys_oss）',
    `review_status`          INT                   DEFAULT 0 COMMENT '复判状态（0:未复判 1:已复判）',
    `review_result`          INT                   DEFAULT NULL COMMENT '复判结果（0:正常无违规 1:确认违规）',
    `review_comment`         VARCHAR(500)          DEFAULT NULL COMMENT '复判说明',
    `reviewer_id`            BIGINT                DEFAULT NULL COMMENT '复判人ID',
    `review_time`            DATETIME              DEFAULT NULL COMMENT '复判时间',
    `tenant_id`              VARCHAR(20)           DEFAULT '000000' COMMENT '租户编号',
    `create_dept`            BIGINT                DEFAULT NULL COMMENT '创建部门',
    `create_by`              BIGINT                DEFAULT NULL COMMENT '创建者',
    `create_time`            DATETIME              DEFAULT NULL COMMENT '创建时间',
    `update_by`              BIGINT                DEFAULT NULL COMMENT '更新者',
    `update_time`            DATETIME              DEFAULT NULL COMMENT '更新时间',
    `del_flag`               BIGINT                DEFAULT 0 COMMENT '删除标志（0:存在 2:删除）',
    PRIMARY KEY (`video_id`),
    KEY `idx_data_source` (`data_source`),
    KEY `idx_ai_check_status` (`ai_check_status`),
    KEY `idx_oss_id` (`oss_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '执法视频信息管理表';

-- ============================================================================
-- 2. video_clip —— 视频人体切片记录表（含切片级 AI 检测字段）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `video_clip`
(
    `clip_id`                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '切片ID（主键）',
    `tenant_id`                 VARCHAR(20)   NOT NULL DEFAULT '000000' COMMENT '租户编号',
    `task_id`                   VARCHAR(64)   NOT NULL COMMENT '切割任务ID',
    `video_id`                  BIGINT        NOT NULL COMMENT '关联执法视频ID（camera_management.video_id）',
    `source_file_name`          VARCHAR(512)           DEFAULT NULL COMMENT '原视频文件名',
    `source_clip_count`         INT                    DEFAULT NULL COMMENT '原视频切分出的有效片段总数',
    `clip_index`                INT           NOT NULL DEFAULT 0 COMMENT '切片序号（从0开始）',
    `object_name`               VARCHAR(512)  NOT NULL COMMENT 'MinIO对象名称（完整路径）',
    `url`                       VARCHAR(1024)          DEFAULT NULL COMMENT '预签名访问URL（7天有效，可为空）',
    `start_second`              DOUBLE        NOT NULL COMMENT '片段起始时间（秒）',
    `end_second`                DOUBLE        NOT NULL COMMENT '片段结束时间（秒）',
    `duration_seconds`          DOUBLE        NOT NULL COMMENT '片段时长（秒）',
    `file_size`                 BIGINT                 DEFAULT NULL COMMENT '文件大小（字节）',
    `clip_status`               TINYINT       NOT NULL DEFAULT 0 COMMENT '切片状态（0:处理中 1:完成 2:失败）',
    `ai_check_status`           TINYINT       NOT NULL DEFAULT 0 COMMENT '切片AI检测状态（0:待检测 1:检测中 2:已完成 3:失败）',
    `ai_has_violation`          TINYINT                DEFAULT NULL COMMENT '切片是否违规（0:无 1:有）',
    `ai_violation_type`         VARCHAR(255)           DEFAULT NULL COMMENT '切片违规类型',
    `ai_description`            TEXT                   DEFAULT NULL COMMENT '切片AI分析描述',
    `ai_events_json`            TEXT                   DEFAULT NULL COMMENT '切片事件列表JSON（时间已偏移到原视频时间轴）',
    `ai_screenshot_url`         VARCHAR(2048)          DEFAULT NULL COMMENT '切片违规截图URL',
    `ai_violation_start_second` DOUBLE                 DEFAULT NULL COMMENT '切片违规起始秒（原视频时间轴）',
    `ai_violation_end_second`   DOUBLE                 DEFAULT NULL COMMENT '切片违规结束秒（原视频时间轴）',
    `ai_process_time`           DOUBLE                 DEFAULT NULL COMMENT '切片AI处理耗时（秒）',
    `ai_error_message`          VARCHAR(1024)          DEFAULT NULL COMMENT '切片AI检测失败信息',
    `error_message`             VARCHAR(1024)          DEFAULT NULL COMMENT '切割失败信息',
    `del_flag`                  TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标志（0:存在 1:删除）',
    `create_dept`               BIGINT                 DEFAULT NULL COMMENT '创建部门',
    `create_by`                 BIGINT                 DEFAULT NULL COMMENT '创建者',
    `create_time`               DATETIME               DEFAULT NULL COMMENT '创建时间',
    `update_by`                 BIGINT                 DEFAULT NULL COMMENT '更新者',
    `update_time`               DATETIME               DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`clip_id`),
    KEY `idx_video_id` (`video_id`),
    KEY `idx_task_id` (`task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '视频人体切片记录表';

-- ============================================================================
-- 3. 字典：camera_data_source（数据来源）
--    scan = AI检测扫描（CameraManagementJobExecutor）
--    clip = 视频切割扫描（VideoClipScanJobExecutor）
-- ============================================================================
INSERT INTO `sys_dict_type`
    (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 1881234567892000, '000000', '视频数据来源', 'camera_data_source', 103, 1, NOW(), '执法视频数据来源（AI检测扫描/视频切割扫描）'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'camera_data_source' AND `tenant_id` = '000000'
);

-- 字典数据：先清理 camera_data_source 旧值再写入，保证可重复执行
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'camera_data_source';
INSERT INTO `sys_dict_data`
    (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`,
     `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
VALUES
    (1881234567892004, '000000', 4, 'AI检测扫描',  'scan', 'camera_data_source', '', 'warning', 'N', 103, 1, NOW(), 'SnailJob定时扫描文件夹并触发AI检测'),
    (1881234567892005, '000000', 5, '视频切割扫描', 'clip', 'camera_data_source', '', 'success', 'N', 103, 1, NOW(), 'SnailJob定时扫描文件夹并发起视频切割（不触发AI）');

-- ============================================================================
-- 4. 菜单与按钮权限
--    5000 执法视频检测（目录）
--      ├─ 5010 检测记录 (camera/fileRecord)  +  5011~5017 按钮
--      ├─ 5020 视频切割 (camera/videoClip)   +  5021~5024 按钮
--      └─ 5030 功能演示 (camera/test)
--    如需挂到其他父目录，修改 5000 行的 parent_id 即可。
-- ============================================================================
-- 先清理本系统菜单（5000~5099）再写入，保证可重复执行
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 5000 AND 5099;

-- 4.1 目录
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
                        `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`,
                        `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
                        `update_by`, `update_time`, `remark`)
VALUES (5000, '执法视频检测', 0, 5, 'camera', NULL, NULL, 1, 0, 'M', '0',
        '0', '', 'video', 103, 1, NOW(), 1, NOW(), '执法视频AI检测与切割目录');

-- 4.2 检测记录页（camera_management）
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
                        `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`,
                        `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
                        `update_by`, `update_time`, `remark`)
VALUES (5010, '检测记录', 5000, 1, 'fileRecord', 'camera/fileRecord/index', NULL, 1, 0, 'C', '0',
        '0', 'camera:management:list', 'documentation', 103, 1, NOW(), 1, NOW(), '执法视频检测记录');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
                        `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`,
                        `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
                        `update_by`, `update_time`, `remark`)
VALUES
    (5011, '记录查询', 5010, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:management:query',         '#', 103, 1, NOW(), 1, NOW(), ''),
    (5012, '记录新增', 5010, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:management:add',           '#', 103, 1, NOW(), 1, NOW(), ''),
    (5013, '记录修改', 5010, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:management:edit',          '#', 103, 1, NOW(), 1, NOW(), ''),
    (5014, '记录删除', 5010, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:management:remove',        '#', 103, 1, NOW(), 1, NOW(), ''),
    (5015, '记录导出', 5010, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:management:export',        '#', 103, 1, NOW(), 1, NOW(), ''),
    (5016, '扫描导入', 5010, 6, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:management:scanInsert',    '#', 103, 1, NOW(), 1, NOW(), ''),
    (5017, '视频上传', 5010, 7, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:management:upload',        '#', 103, 1, NOW(), 1, NOW(), '');

-- 4.3 视频切割页（video_clip）
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
                        `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`,
                        `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
                        `update_by`, `update_time`, `remark`)
VALUES (5020, '视频切割', 5000, 2, 'videoClip', 'camera/videoClip/index', NULL, 1, 0, 'C', '0',
        '0', 'camera:videoClip:list', 'scissor', 103, 1, NOW(), 1, NOW(), '执法视频人体切片管理');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
                        `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`,
                        `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
                        `update_by`, `update_time`, `remark`)
VALUES
    (5021, '切割查询', 5020, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:videoClip:query',  '#', 103, 1, NOW(), 1, NOW(), ''),
    (5022, '发起切割', 5020, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:videoClip:submit', '#', 103, 1, NOW(), 1, NOW(), ''),
    (5023, '删除切片', 5020, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:videoClip:remove', '#', 103, 1, NOW(), 1, NOW(), ''),
    (5024, '导出切片', 5020, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:videoClip:export', '#', 103, 1, NOW(), 1, NOW(), '');

-- 4.4 功能演示页（同步上传分析演示）
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
                        `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`,
                        `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
                        `update_by`, `update_time`, `remark`)
VALUES (5030, '功能演示', 5000, 3, 'cameraTest', 'camera/test/index', NULL, 1, 0, 'C', '0',
        '0', 'camera:management:upload', 'eye-open', 103, 1, NOW(), 1, NOW(), 'AI检测功能演示页');

-- ============================================================================
-- 5. SnailJob 定时任务（建议在 SnailJob 管理后台手动创建；以下仅作参考）
--    cameraManagementJobExecutor —— AI检测扫描（扫描→切分→逐片AI→聚合，主任务）
--    videoClipScanJobExecutor    —— 纯视频切割扫描（不触发AI，可选）
-- ============================================================================
