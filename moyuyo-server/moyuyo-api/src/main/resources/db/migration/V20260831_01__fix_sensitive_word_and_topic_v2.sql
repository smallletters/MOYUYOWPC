-- ============================================================
-- V20260831_01__fix_sensitive_word_and_topic_v2.sql
-- 修复新建 docker 数据库后社区发帖失败的 500 错误：
-- 1) mo_sensitive_word 增加 last_hit_time 列（实体已声明但建表脚本遗漏）
-- 2) mo_community_topic_v2 缺失建表（话题广场接口依赖）
-- ============================================================

-- 1) 敏感词表：补齐 last_hit_time
ALTER TABLE `mo_sensitive_word`
  ADD COLUMN `last_hit_time` DATETIME NULL COMMENT '最后命中时间' AFTER `hit_count`;

-- 2) 话题 V2 表：按实体字段建表（id 雪花）
CREATE TABLE IF NOT EXISTS `mo_community_topic_v2` (
  `id`           BIGINT        NOT NULL                COMMENT '雪花ID',
  `name`         VARCHAR(64)   NOT NULL                COMMENT '话题名',
  `description`  VARCHAR(512)  NULL                    COMMENT '话题描述',
  `cover_image`  VARCHAR(256)  NULL                    COMMENT '封面图',
  `post_count`   INT           NOT NULL DEFAULT 0      COMMENT '关联帖子数',
  `follow_count` INT           NOT NULL DEFAULT 0      COMMENT '关注人数',
  `view_count`   INT           NOT NULL DEFAULT 0      COMMENT '浏览量',
  `hot`          INT           NOT NULL DEFAULT 0      COMMENT '热度分',
  `sort_order`   INT           NOT NULL DEFAULT 0      COMMENT '排序',
  `active`       TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '0下线 1启用',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_active_sort` (`active`, `sort_order`),
  KEY `idx_hot` (`hot` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区话题表(v2)';
