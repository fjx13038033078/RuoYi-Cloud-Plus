ALTER TABLE `camera_management`
    ADD COLUMN `oss_id` bigint DEFAULT NULL COMMENT 'OSS文件ID';

-- ==================== 1. 添加唯一索引（防止重复导入）====================
ALTER TABLE camera_management
    ADD UNIQUE INDEX uk_storage_location (storage_location(500));

-- ==================== 2. 修改字段类型（符合若依规范）====================
ALTER TABLE camera_management
    MODIFY COLUMN ai_check_status VARCHAR(10) DEFAULT '0' COMMENT 'AI检测状态（0:未检测 1:检测中 2:检测完成 3:检测失败）',
    MODIFY COLUMN data_status VARCHAR(10) DEFAULT '1' COMMENT '数据状态（0:删除 1:正常）';

-- ==================== 3. 添加查询索引（提高查询性能）====================
ALTER TABLE camera_management
    ADD INDEX idx_user_code (user_code),
    ADD INDEX idx_device_id (device_id),
    ADD INDEX idx_upload_time (upload_time),
    ADD INDEX idx_dept_id (dept_id);

-- ==================== 4. 添加数据字典类型 ====================
-- 使用雪花算法生成的ID，你也可以改成自己的ID
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by,
                           update_time, remark)
VALUES (1881234567890001, '000000', 'AI检测状态', 'camera_ai_status', 100, 1, NOW(), 1, NOW(), '执法记录仪AI检测状态'),
       (1881234567890002, '000000', '数据来源', 'camera_data_source', 100, 1, NOW(), 1, NOW(), '执法记录仪数据来源');

-- ==================== 5. 添加数据字典数据 ====================
-- AI检测状态
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
                           is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (1881234567891001, '000000', 1, '未检测', '0', 'camera_ai_status', '', 'default', 'Y', 100, 1, NOW(), 1, NOW(),
        ''),
       (1881234567891002, '000000', 2, '检测中', '1', 'camera_ai_status', '', 'primary', 'N', 100, 1, NOW(), 1, NOW(),
        ''),
       (1881234567891003, '000000', 3, '检测完成', '2', 'camera_ai_status', '', 'success', 'N', 100, 1, NOW(), 1, NOW(),
        ''),
       (1881234567891004, '000000', 4, '检测失败', '3', 'camera_ai_status', '', 'danger', 'N', 100, 1, NOW(), 1, NOW(),
        '');

-- 数据来源
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
                           is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (1881234567892001, '000000', 1, '执法记录仪自动上传', 'auto', 'camera_data_source', '', 'default', 'N', 100, 1,
        NOW(), 1, NOW(), ''),
       (1881234567892002, '000000', 2, '手动上传', 'manual', 'camera_data_source', '', 'primary', 'N', 100, 1, NOW(), 1,
        NOW(), ''),
       (1881234567892003, '000000', 3, '外部导入', 'import', 'camera_data_source', '', 'info', 'N', 100, 1, NOW(), 1,
        NOW(), ''),
       (1881234567892004, '000000', 4, '文件夹扫描', 'scan', 'camera_data_source', '', 'warning', 'Y', 100, 1, NOW(), 1,
        NOW(), '');
