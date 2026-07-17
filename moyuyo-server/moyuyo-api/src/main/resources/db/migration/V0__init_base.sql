-- ============================================================
-- V0__init_base.sql
-- 基础用户表（供 V1 的 ALTER TABLE 使用）
-- ============================================================
CREATE TABLE IF NOT EXISTS mo_user (
  id                BIGINT        NOT NULL PRIMARY KEY COMMENT '雪花 ID',
  email             VARCHAR(128)  NULL COMMENT '邮箱（欧美主键）',
  password_hash     VARCHAR(128)  NULL COMMENT '密码 BCrypt 加盐',
  phone             VARCHAR(20)   NULL,
  nickname          VARCHAR(64)   NULL,
  avatar            VARCHAR(255)  NULL,
  birthday          DATE          NULL,
  country           VARCHAR(8)    NULL COMMENT '国家 ISO 3166-1 alpha-2',
  locale            VARCHAR(16)   NULL COMMENT '区域（en_US 等）',
  timezone          VARCHAR(64)   NULL COMMENT '时区（IANA 标识）',
  points            INT           DEFAULT 0,
  two_factor_enabled TINYINT(1)   DEFAULT 0 COMMENT '2FA 是否启用',
  email_verified    TINYINT(1)    DEFAULT 0 COMMENT '邮箱是否验证',
  marketing_opt_in  TINYINT(1)    DEFAULT 0 COMMENT '营销订阅',
  status            TINYINT(1)    DEFAULT 1,
  last_login_time   DATETIME      NULL,
  delete_scheduled_at DATETIME    NULL COMMENT '账号注销计划时间',
  oauth_provider    VARCHAR(16)   NULL,
  oauth_uid         VARCHAR(128)  NULL,
  deleted           TINYINT(1)    DEFAULT 0,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_email (email),
  KEY idx_user_country (country),
  KEY idx_user_delete (status, delete_scheduled_at),
  KEY idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '用户主表';
