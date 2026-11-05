-- ============================================================
-- V20261105_03__invite_reward_guard.sql
-- 邀请奖励风控与月度排行奖励：
--   1) mo_invite 记录触发奖励的订单与奖励时间
--      - trigger_order_id 用于退款时精确扣回该笔邀请奖励
--      - reward_time     用于统计单个邀请人的月度计奖数量（上限 20 条）
--   2) mo_invite_month_bonus 记录月度 TOP10 阶梯奖励发放，unique(month,user_id) 保证幂等
-- ============================================================

ALTER TABLE `mo_invite`
    ADD COLUMN `trigger_order_id` BIGINT NULL COMMENT '触发邀请奖励的订单ID（退款扣回依据）' AFTER `points_awarded`,
    ADD COLUMN `reward_time` DATETIME NULL COMMENT '邀请奖励发放时间（月度计奖上限统计）' AFTER `trigger_order_id`,
    ADD INDEX `idx_user_reward_time` (`user_id`, `reward_time`);

CREATE TABLE IF NOT EXISTS `mo_invite_month_bonus` (
    `id` BIGINT NOT NULL,
    `month` VARCHAR(7) NOT NULL COMMENT '归属月份 yyyy-MM（美国东部时间）',
    `user_id` BIGINT NOT NULL,
    `rank_no` INT NOT NULL COMMENT '名次 1-10',
    `points` INT NOT NULL COMMENT '发放积分',
    `create_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_month_user` (`month`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度邀请排行奖励发放记录';
