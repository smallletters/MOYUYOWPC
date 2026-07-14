-- ============================================================
-- V6__init_pet_hub.sql
-- Pet Hub：3D 宠物互动 + 护理追踪
-- 来源：[需求文档.md §3.1.15]
-- ============================================================

-- 1. mo_pet 表新增 Pet Hub 字段
ALTER TABLE mo_pet
  ADD COLUMN last_bath_date     DATE         NULL                  COMMENT '上次洗澡日期',
  ADD COLUMN bath_interval_days INT          NOT NULL DEFAULT 7    COMMENT '洗澡提醒间隔（天）',
  ADD COLUMN last_vaccine_date  DATE         NULL                  COMMENT '上次疫苗日期',
  ADD COLUMN last_deworm_date   DATE         NULL                  COMMENT '上次驱虫日期',
  ADD COLUMN deworm_type        VARCHAR(16)  NULL                  COMMENT '体内/体外/内外同驱',
  ADD COLUMN preferred_scene    VARCHAR(32)  NULL                  COMMENT '偏好3D场景（OUTDOOR/LIVINGROOM/TRAINING/STUDIO）',
  ADD COLUMN interact_affinity  INT          NOT NULL DEFAULT 0    COMMENT '互动亲密度（预留）';

ALTER TABLE mo_pet
  ADD INDEX idx_next_bath (`last_bath_date`),
  ADD INDEX idx_next_vaccine (`last_vaccine_date`),
  ADD INDEX idx_next_deworm (`last_deworm_date`);

-- 2. 成长记录表
CREATE TABLE mo_growth_record (
  id            BIGINT       NOT NULL                  COMMENT '雪花ID',
  pet_id        BIGINT       NOT NULL                  COMMENT '关联宠物ID',
  type          VARCHAR(16)  NOT NULL                  COMMENT 'VACCINE/DEWORM/EXAM/BATH',
  title         VARCHAR(128) NOT NULL                  COMMENT '记录标题',
  record_time   DATE         NOT NULL                  COMMENT '记录日期',
  note          TEXT         NULL                      COMMENT '备注',
  image_url     VARCHAR(512) NULL                      COMMENT '附件图片URL',
  reminder_type VARCHAR(16)  NULL                      COMMENT '关联提醒类型',
  alert_date    DATE         NULL                      COMMENT '设置的下次提醒日期',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_pet_type (`pet_id`, `type`),
  KEY idx_record_time (`record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长记录表（洗澡/疫苗/驱虫/体检）';

-- 3. 护理提醒配置表
CREATE TABLE mo_pet_reminder (
  id                BIGINT       NOT NULL                  COMMENT '雪花ID',
  pet_id            BIGINT       NOT NULL                  COMMENT '关联宠物ID',
  reminder_type     VARCHAR(16)  NOT NULL                  COMMENT 'BATH/VACCINE/DEWORM/EXAM',
  next_date         DATE         NOT NULL                  COMMENT '下次提醒日期',
  interval_days     INT          NOT NULL DEFAULT 7        COMMENT '周期天数',
  advance_days      INT          NOT NULL DEFAULT 3        COMMENT '提前N天提醒',
  enabled           TINYINT(1)   NOT NULL DEFAULT 1        COMMENT '1启用 0禁用',
  last_notified_date DATE        NULL                      COMMENT '上次已推送日期',
  extra             JSON         NULL                      COMMENT '扩展信息（vaccineType/dewormType等）',
  create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pet_reminder (`pet_id`, `reminder_type`),
  KEY idx_next_date (`next_date`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='护理提醒配置表';

-- 4. 成就徽章表
CREATE TABLE mo_pet_achievement (
  id              BIGINT       NOT NULL                  COMMENT '雪花ID',
  pet_id          BIGINT       NOT NULL                  COMMENT '关联宠物ID',
  achievement_code VARCHAR(32) NOT NULL                  COMMENT 'CLEAN_BABY/EPIDEMIC_MASTER/DEWORM_GUARDIAN/ATTENDANCE_FULL/EXPLORER_PIONEER',
  unlocked        TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '0未解锁 1已解锁',
  unlocked_at     DATETIME     NULL                      COMMENT '解锁时间',
  progress        DECIMAL(3,2) NOT NULL DEFAULT 0.00     COMMENT '解锁进度 0.00~1.00',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pet_achievement (`pet_id`, `achievement_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物成就徽章表';

-- 5. 3D 场景解锁表
CREATE TABLE mo_pet_scene (
  id          BIGINT       NOT NULL                  COMMENT '雪花ID',
  pet_id      BIGINT       NOT NULL                  COMMENT '关联宠物ID',
  scene_code  VARCHAR(32)  NOT NULL                  COMMENT 'OUTDOOR/LIVINGROOM/TRAINING/STUDIO',
  unlocked    TINYINT(1)   NOT NULL DEFAULT 1        COMMENT '1已解锁 0未解锁',
  unlocked_at DATETIME     NULL                      COMMENT '解锁时间',
  sort_order  INT          NOT NULL DEFAULT 0        COMMENT '排序',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pet_scene (`pet_id`, `scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物3D场景解锁表';
