-- ============================================================
-- V1__init_user_and_oauth.sql
-- 初始化：用户表改造 + 7 张 GDPR / OAuth / 2FA / 登录日志表
-- ============================================================

-- 1. 改造 mo_user 表
ALTER TABLE mo_user
  ADD COLUMN email             VARCHAR(128)  COMMENT '邮箱（欧美主键）'        AFTER id,
  ADD COLUMN password_hash     VARCHAR(128)  COMMENT '密码 BCrypt 加盐'           AFTER email,
  ADD COLUMN country           VARCHAR(8)    COMMENT '国家 ISO 3166-1 alpha-2'    AFTER birthday,
  ADD COLUMN locale            VARCHAR(16)   COMMENT '区域（en_US 等）'          AFTER country,
  ADD COLUMN timezone          VARCHAR(64)   COMMENT '时区（IANA 标识）'         AFTER locale,
  ADD COLUMN two_factor_enabled TINYINT(1)   DEFAULT 0 COMMENT '2FA 是否启用'     AFTER points,
  ADD COLUMN email_verified    TINYINT(1)    DEFAULT 0 COMMENT '邮箱是否验证'         AFTER two_factor_enabled,
  ADD COLUMN marketing_opt_in  TINYINT(1)    DEFAULT 0 COMMENT '营销订阅'             AFTER email_verified,
  ADD COLUMN delete_scheduled_at DATETIME    DEFAULT NULL COMMENT '账号注销计划时间' AFTER last_login_time;

-- 2. 邮箱唯一约束（先清理重复数据）
UPDATE mo_user SET email = CONCAT('legacy_', id, '@moyuyo.local') WHERE email IS NULL;
ALTER TABLE mo_user
  ADD UNIQUE KEY uk_email (email);

-- 3. 索引
CREATE INDEX idx_user_country   ON mo_user(country);
CREATE INDEX idx_user_delete    ON mo_user(status, delete_scheduled_at);
CREATE INDEX idx_phone          ON mo_user(phone);

-- 4. 第三方登录绑定
CREATE TABLE mo_oauth_binding (
  id                BIGINT       NOT NULL                COMMENT '雪花 ID',
  user_id           BIGINT       NOT NULL                COMMENT '关联 mo_user',
  provider          VARCHAR(16)  NOT NULL                COMMENT 'APPLE/GOOGLE/FACEBOOK',
  provider_uid      VARCHAR(128) NOT NULL                COMMENT '第三方平台唯一标识',
  provider_email    VARCHAR(128)                        COMMENT '第三方邮箱',
  raw_profile       JSON                                COMMENT '原始用户信息（脱敏）',
  bind_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_time   DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_uid (provider, provider_uid),
  KEY idx_user_provider (user_id, provider),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '第三方登录绑定表';

-- 5. 2FA 配置
CREATE TABLE mo_two_factor (
  id              BIGINT       NOT NULL                  COMMENT '雪花 ID',
  user_id         BIGINT       NOT NULL                  COMMENT '用户 ID（一对一）',
  secret          VARCHAR(256) NOT NULL                  COMMENT 'TOTP 密钥（AES 加密）',
  backup_codes    JSON                                  COMMENT '10 组备用码（BCrypt 哈希）',
  enabled_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '2FA 配置表';

-- 6. 同意日志（GDPR）
CREATE TABLE mo_consent_log (
  id              BIGINT       NOT NULL                  COMMENT '雪花 ID',
  user_id         BIGINT       NOT NULL                  COMMENT '用户 ID（0 表示游客）',
  consent_type    VARCHAR(32)  NOT NULL                  COMMENT 'PRIVACY_POLICY/TERMS/MARKETING_EMAIL/ANALYTICS_COOKIE/ADVERTISING_COOKIE',
  granted         TINYINT(1)   NOT NULL                  COMMENT '0 拒绝 1 同意',
  version         VARCHAR(16)  NOT NULL                  COMMENT '协议版本号',
  source          VARCHAR(16)  NOT NULL                  COMMENT 'REGISTER/SETTINGS/FOOTER',
  grant_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoke_time     DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_user_type (user_id, consent_type),
  KEY idx_grant_time (grant_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '同意日志表（GDPR 审计）';

-- 7. 数据导出请求
CREATE TABLE mo_data_export_request (
  id                  BIGINT       NOT NULL                COMMENT '雪花 ID',
  user_id             BIGINT       NOT NULL                COMMENT '用户 ID',
  export_id           VARCHAR(32)  NOT NULL                COMMENT '业务 ID',
  status              VARCHAR(16)  NOT NULL                COMMENT 'PROCESSING/READY/EXPIRED/FAILED',
  download_url        VARCHAR(512) NULL                    COMMENT 'OSS 临时签名 URL',
  download_expire_at  DATETIME     NULL,
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  complete_time       DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_export_id (export_id),
  KEY idx_user_id (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '数据导出请求表';

-- 8. 账号注销
CREATE TABLE mo_account_deletion (
  id                  BIGINT       NOT NULL                COMMENT '雪花 ID',
  user_id             BIGINT       NOT NULL                COMMENT '用户 ID（一对一）',
  status              VARCHAR(16)  NOT NULL                COMMENT 'PENDING/RECOVERED/EXECUTED',
  reason              VARCHAR(32)  NULL                    COMMENT '注销原因',
  scheduled_at        DATETIME     NULL                    COMMENT '计划执行时间',
  recover_deadline    DATETIME     NULL                    COMMENT '可恢复截止时间',
  recover_token       VARCHAR(64)  NULL                    COMMENT '恢复凭证',
  recovered_at        DATETIME     NULL,
  executed_at         DATETIME     NULL,
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id),
  KEY uk_recover_token (recover_token),
  KEY idx_scheduled (status, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '账号注销表（GDPR 30 天宽限期）';

-- 9. 登录日志
CREATE TABLE mo_login_log (
  id              BIGINT       NOT NULL                  COMMENT '雪花 ID',
  user_id         BIGINT       NULL                      COMMENT '用户 ID（失败时为 NULL）',
  login_email     VARCHAR(128) NULL                      COMMENT '登录邮箱（用于失败定位）',
  login_method    VARCHAR(16)  NOT NULL                  COMMENT 'EMAIL/APPLE/GOOGLE/FACEBOOK/MAGIC_LINK',
  provider        VARCHAR(16)  NULL                      COMMENT '第三方 provider',
  device_id       VARCHAR(64)  NULL,
  ip              VARCHAR(45)  NULL                      COMMENT 'IP 支持 IPv6',
  country         VARCHAR(8)   NULL                      COMMENT 'IP 解析国家',
  user_agent      TEXT         NULL,
  success         TINYINT(1)   NOT NULL                  COMMENT '0 失败 1 成功',
  fail_reason     VARCHAR(64)  NULL,
  login_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, login_time),
  KEY idx_ip_time (ip, login_time),
  KEY idx_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '登录日志表';

-- 10. 账号设备表（升级为多端登录管理）
CREATE TABLE mo_device (
  id              BIGINT       NOT NULL                  COMMENT '雪花 ID',
  user_id         BIGINT       NOT NULL,
  device_id       VARCHAR(64)  NOT NULL,
  platform        VARCHAR(16)  NOT NULL                  COMMENT 'iOS/Android/Web',
  model           VARCHAR(64)  NULL,
  os_version      VARCHAR(32)  NULL,
  app_version     VARCHAR(32)  NULL,
  push_token      VARCHAR(255) NULL,
  trusted         TINYINT(1)   DEFAULT 0                 COMMENT '是否 2FA 可信设备',
  last_active     DATETIME     NULL,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_device (user_id, device_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '设备管理表';
