-- ============================================================
-- MOYUYO V20260827_02 - 手机短信验证码登录
-- 表：mo_sms_code
-- 用途：存储手机号 + 验证码 + 用途 + 过期时间，支持登录/重置密码
-- 安全：验证码使用后立即标记 used，避免重放
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_sms_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号（带国家区号，如 +8613800000000）',
    `code` VARCHAR(6) NOT NULL COMMENT '验证码',
    `purpose` VARCHAR(20) NOT NULL DEFAULT 'LOGIN' COMMENT 'LOGIN / RESET_PASSWORD / REGISTER',
    `used` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已使用 0=否 1=是',
    `fail_count` INT NOT NULL DEFAULT 0 COMMENT '失败次数，超 5 次自动失效',
    `expire_at` DATETIME NOT NULL COMMENT '过期时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_phone_purpose` (`phone`, `purpose`),
    KEY `idx_expire` (`expire_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手机短信验证码';

-- 给 mo_user.phone 加唯一索引（手机号登录唯一标识）
-- 若历史已存在重复手机号则跳过（生产用脚本去重）
-- 注意：仅在 phone 列存在且未建唯一索引时执行
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'mo_user'
      AND index_name = 'uk_user_phone'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `mo_user` ADD UNIQUE KEY `uk_user_phone` (`phone`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;