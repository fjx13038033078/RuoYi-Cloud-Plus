ALTER TABLE `camera_management`
    ADD COLUMN `oss_id` bigint DEFAULT NULL COMMENT 'OSS文件ID',
    ADD COLUMN `minio_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'OSS访问URL'
