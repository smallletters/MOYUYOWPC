-- ============================================================
-- V20261104_08__community_not_interested.sql
-- 社区「不感兴趣」：记录用户对单条帖子的负反馈，
-- 推荐流（GET /api/v1/community/posts）按当前登录用户过滤这些帖子。
--
-- 设计：
--   1. 同一用户对同一帖子只保留一条记录（唯一索引保证幂等）
--   2. 仅影响该用户自己的推荐流，不影响帖子本身与其他用户
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_community_not_interested` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `post_id` BIGINT NOT NULL COMMENT '帖子ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_not_interested_user_post` (`user_id`, `post_id`),
    KEY `idx_not_interested_user` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子不感兴趣记录';
