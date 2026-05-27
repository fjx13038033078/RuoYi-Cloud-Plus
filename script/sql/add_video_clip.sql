-- 视频切片功能：新增 video_clip 表及菜单权限

-- ======================================================
-- 1. video_clip 表（切片记录）
-- ======================================================
CREATE TABLE IF NOT EXISTS `video_clip`
(
    `clip_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '切片ID（主键）',
    `tenant_id`        VARCHAR(20)  NOT NULL DEFAULT '000000' COMMENT '租户编号',
    `task_id`          VARCHAR(64)  NOT NULL COMMENT '切割任务ID',
    `video_id`         BIGINT       NOT NULL COMMENT '关联执法视频ID（camera_management.video_id）',
    `clip_index`       INT          NOT NULL DEFAULT 0 COMMENT '切片序号（从0开始）',
    `object_name`      VARCHAR(512) NOT NULL COMMENT 'MinIO对象名称（完整路径）',
    `url`              VARCHAR(1024)         COMMENT '预签名访问URL（24h有效，可为空）',
    `start_second`     DOUBLE       NOT NULL COMMENT '片段起始时间（秒）',
    `end_second`       DOUBLE       NOT NULL COMMENT '片段结束时间（秒）',
    `duration_seconds` DOUBLE       NOT NULL COMMENT '片段时长（秒）',
    `file_size`        BIGINT                COMMENT '文件大小（字节）',
    `clip_status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '切片状态（0:处理中 1:完成 2:失败）',
    `error_message`    VARCHAR(1024)         COMMENT '失败信息',
    `del_flag`         TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0:存在 1:删除）',
    `create_dept`      BIGINT                COMMENT '创建部门',
    `create_by`        BIGINT                COMMENT '创建者',
    `create_time`      DATETIME              COMMENT '创建时间',
    `update_by`        BIGINT                COMMENT '更新者',
    `update_time`      DATETIME              COMMENT '更新时间',
    PRIMARY KEY (`clip_id`),
    KEY `idx_video_id` (`video_id`),
    KEY `idx_task_id` (`task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '视频人体切片记录表';

-- ======================================================
-- 2. 视频切割功能菜单（挂在 camera 目录下）
-- ======================================================
-- 注：请先在数据库中查询 camera 父菜单的 menu_id，根据实际值替换下方 parent_id
-- 假设 camera 父目录菜单 menu_id = 5000（请根据实际替换）

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
                        `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`,
                        `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
                        `update_by`, `update_time`, `remark`)
VALUES (5020, '视频切割', 5000, 6, 'videoClip', 'camera/videoClip/index', NULL, 1, 0, 'C', '0',
        '0', 'camera:videoClip:list', 'scissor', 103, 1, NOW(), 1, NOW(), '执法视频人体切片管理');

-- 按钮权限
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
                        `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`,
                        `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
                        `update_by`, `update_time`, `remark`)
VALUES
    (5021, '切割查询', 5020, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:videoClip:query',   '#', 103, 1, NOW(), 1, NOW(), ''),
    (5022, '发起切割', 5020, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:videoClip:submit',  '#', 103, 1, NOW(), 1, NOW(), ''),
    (5023, '删除切片', 5020, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:videoClip:remove',  '#', 103, 1, NOW(), 1, NOW(), ''),
    (5024, '导出切片', 5020, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'camera:videoClip:export',  '#', 103, 1, NOW(), 1, NOW(), '');

-- ======================================================
-- 3. 字典：camera_data_source 增加 "视频切割扫描" 值
-- ======================================================
INSERT INTO `sys_dict_data`
    (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`,
     `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`,
     `update_by`, `update_time`, `remark`)
VALUES
    (1881234567892005, '000000', 5, '视频切割扫描', 'clip', 'camera_data_source', '', 'success', 'N', 100, 1, NOW(), 1, NOW(), '视频切割定时任务导入');

-- ======================================================
-- 4. SnailJob：视频切割扫描定时任务（建议手动在管理后台创建；以下 SQL 仅作示例）
--    executor_info = videoClipScanJobExecutor
--    executor_timeout = 3600 秒
-- ======================================================
-- INSERT INTO `sj_job` (`namespace_id`, `group_name`, `job_name`, `args_str`, `args_type`,
--     `next_trigger_at`, `job_status`, `task_type`, `route_key`, `executor_type`, `executor_info`,
--     `trigger_type`, `trigger_interval`, `block_strategy`, `executor_timeout`, `max_retry_times`,
--     `parallel_num`, `retry_interval`, `bucket_index`, `resident`, `description`, `ext_attrs`)
-- VALUES ('dev', 'ruoyi_group', '视频切割扫描', NULL, 1, UNIX_TIMESTAMP() * 1000, 1, 1, 4, 1, 'videoClipScanJobExecutor',
--     2, '3600', 1, 3600, 0, 1, 1, 100, 0, '视频切割定时任务（先扫描入库，再发起切割）', '');
