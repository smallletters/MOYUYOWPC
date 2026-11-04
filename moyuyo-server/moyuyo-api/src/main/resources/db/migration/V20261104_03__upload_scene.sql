-- 图片空间增加上传场景与文件大小元数据
-- scene: ADMIN 管理端素材 / AVATAR 头像 / COMMUNITY 社区 / DM 私信聊天 / USER 其他用户上传
ALTER TABLE `mo_upload_file`
    ADD COLUMN `scene` VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '上传场景：ADMIN/AVATAR/COMMUNITY/DM/USER' AFTER `url`,
    ADD COLUMN `size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）' AFTER `scene`,
    ADD KEY `idx_upload_file_scene` (`scene`);
