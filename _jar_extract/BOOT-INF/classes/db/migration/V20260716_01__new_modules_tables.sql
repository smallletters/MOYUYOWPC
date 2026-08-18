-- ============================================================
-- MOYUYO V20260716_01 - 新模块数据库表创建
-- 包含：通知/反馈/任务/邀请/浏览/售后/回收/发票/宠物/礼品卡/砍价/拼团/套餐/直播/订阅/抽奖
-- ============================================================

-- 1. 通知
CREATE TABLE IF NOT EXISTS `mo_notification` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `type` VARCHAR(20) DEFAULT 'SYSTEM' COMMENT 'ORDER/ACTIVITY/SYSTEM',
    `title` VARCHAR(200),
    `content` TEXT,
    `related_id` BIGINT,
    `read` BIT DEFAULT 0,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_read` (`user_id`, `read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知';

-- 2. 意见反馈
CREATE TABLE IF NOT EXISTS `mo_feedback` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `type` VARCHAR(20) COMMENT 'SUGGESTION/BUG/CONTENT/OTHER',
    `content` TEXT,
    `images` TEXT COMMENT 'JSON array',
    `contact` VARCHAR(100),
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/REPLIED/CLOSED',
    `reply_content` TEXT,
    `reply_time` DATETIME,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈';

-- 3. 任务
CREATE TABLE IF NOT EXISTS `mo_mission` (
    `id` BIGINT PRIMARY KEY,
    `type` VARCHAR(20) COMMENT 'DAILY/WEEKLY/ACHIEVEMENT',
    `name` VARCHAR(100),
    `description` VARCHAR(500),
    `icon` VARCHAR(100),
    `points` INT DEFAULT 0,
    `target` INT DEFAULT 1,
    `sort_order` INT DEFAULT 0,
    `active` BIT DEFAULT 1,
    `create_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务定义';

CREATE TABLE IF NOT EXISTS `mo_user_mission` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `mission_id` BIGINT NOT NULL,
    `progress` INT DEFAULT 0,
    `completed` BIT DEFAULT 0,
    `claimed` BIT DEFAULT 0,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_user_mission` (`user_id`, `mission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户任务进度';

-- 4. 邀请
CREATE TABLE IF NOT EXISTS `mo_invite` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `invite_code` VARCHAR(20) NOT NULL UNIQUE,
    `invited_user_id` BIGINT,
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/REGISTERED/ORDERED',
    `points_awarded` INT DEFAULT 0,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请记录';

-- 5. 浏览记录
CREATE TABLE IF NOT EXISTS `mo_browsing_history` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_user_time` (`user_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览记录';

-- 6. 售后管理
CREATE TABLE IF NOT EXISTS `mo_after_sales` (
    `id` BIGINT PRIMARY KEY,
    `order_id` BIGINT NOT NULL,
    `order_item_id` BIGINT,
    `user_id` BIGINT NOT NULL,
    `type` VARCHAR(20) COMMENT 'REFUND/RETURN/EXCHANGE',
    `reason` VARCHAR(500),
    `amount` DECIMAL(10,2) DEFAULT 0,
    `description` TEXT,
    `images` TEXT COMMENT 'JSON array',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/COMPLETED',
    `create_time` DATETIME NOT NULL,
    `update_time` DATETIME,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后';

-- 7. 发票
CREATE TABLE IF NOT EXISTS `mo_invoice` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `title` VARCHAR(200),
    `tax_id` VARCHAR(50),
    `email` VARCHAR(100),
    `type` VARCHAR(20) COMMENT 'COMPANY/INDIVIDUAL',
    `amount` DECIMAL(10,2),
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/ISSUED/REJECTED',
    `file_url` VARCHAR(500),
    `create_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票';

-- 8. 宠物相册
CREATE TABLE IF NOT EXISTS `mo_pet_album` (
    `id` BIGINT PRIMARY KEY,
    `pet_id` BIGINT NOT NULL,
    `image_url` VARCHAR(500) NOT NULL,
    `description` VARCHAR(500),
    `create_time` DATETIME NOT NULL,
    INDEX `idx_pet_id` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物相册';

-- 9. 宠物日记
CREATE TABLE IF NOT EXISTS `mo_pet_diary` (
    `id` BIGINT PRIMARY KEY,
    `pet_id` BIGINT NOT NULL,
    `title` VARCHAR(200),
    `content` TEXT,
    `images` TEXT COMMENT 'JSON array',
    `mood` VARCHAR(20),
    `tags` VARCHAR(200),
    `weight` DECIMAL(8,2),
    `likes` INT DEFAULT 0,
    `comments` INT DEFAULT 0,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_pet_id` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长日记';

-- 10. 宠物体重
CREATE TABLE IF NOT EXISTS `mo_pet_weight` (
    `id` BIGINT PRIMARY KEY,
    `pet_id` BIGINT NOT NULL,
    `weight` DECIMAL(8,2) NOT NULL,
    `unit` VARCHAR(10) DEFAULT 'kg',
    `note` VARCHAR(500),
    `measured_at` DATETIME,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_pet_id` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体重记录';

-- 11. 宠物装扮
CREATE TABLE IF NOT EXISTS `mo_pet_outfit` (
    `id` BIGINT PRIMARY KEY,
    `pet_id` BIGINT NOT NULL,
    `category` VARCHAR(50),
    `name` VARCHAR(100),
    `image_url` VARCHAR(500),
    `owned` BIT DEFAULT 0,
    `equipped` BIT DEFAULT 0,
    `price` DECIMAL(10,2),
    `create_time` DATETIME NOT NULL,
    INDEX `idx_pet_id` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物装扮';

-- 12. 礼品卡
CREATE TABLE IF NOT EXISTS `mo_gift_card` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `card_no` VARCHAR(50),
    `balance` DECIMAL(10,2) DEFAULT 0,
    `total_amount` DECIMAL(10,2) DEFAULT 0,
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/USED/EXPIRED',
    `expiry_date` DATE,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼品卡';

CREATE TABLE IF NOT EXISTS `mo_gift_card_transaction` (
    `id` BIGINT PRIMARY KEY,
    `card_id` BIGINT NOT NULL,
    `type` VARCHAR(20) COMMENT 'RECHARGE/SPEND/REFUND',
    `amount` DECIMAL(10,2),
    `order_id` BIGINT,
    `remark` VARCHAR(500),
    `create_time` DATETIME NOT NULL,
    INDEX `idx_card_id` (`card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼品卡交易';

-- 13. 砍价
CREATE TABLE IF NOT EXISTS `mo_bargain` (
    `id` BIGINT PRIMARY KEY,
    `product_id` BIGINT,
    `sku_id` BIGINT,
    `original_price` DECIMAL(10,2),
    `bargain_price` DECIMAL(10,2),
    `total_stock` INT DEFAULT 0,
    `remaining_stock` INT DEFAULT 0,
    `end_time` DATETIME,
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ENDED',
    `create_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='砍价活动';

CREATE TABLE IF NOT EXISTS `mo_bargain_help` (
    `id` BIGINT PRIMARY KEY,
    `bargain_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `help_amount` DECIMAL(10,2),
    `create_time` DATETIME NOT NULL,
    INDEX `idx_bargain_id` (`bargain_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='砍价助力';

-- 14. 拼团
CREATE TABLE IF NOT EXISTS `mo_group_buy` (
    `id` BIGINT PRIMARY KEY,
    `product_id` BIGINT,
    `sku_id` BIGINT,
    `group_price` DECIMAL(10,2),
    `original_price` DECIMAL(10,2),
    `min_members` INT DEFAULT 2,
    `current_members` INT DEFAULT 0,
    `end_time` DATETIME,
    `status` VARCHAR(20) DEFAULT 'ACTIVE',
    `create_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团';

CREATE TABLE IF NOT EXISTS `mo_group_buy_member` (
    `id` BIGINT PRIMARY KEY,
    `group_buy_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `is_leader` BIT DEFAULT 0,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_group_buy_id` (`group_buy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团成员';

-- 15. 套餐
CREATE TABLE IF NOT EXISTS `mo_bundle_deal` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(200),
    `description` TEXT,
    `main_product_id` BIGINT,
    `bundle_price` DECIMAL(10,2),
    `original_total` DECIMAL(10,2),
    `discount_percent` INT,
    `active` BIT DEFAULT 1,
    `start_time` DATETIME,
    `end_time` DATETIME,
    `create_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐';

CREATE TABLE IF NOT EXISTS `mo_bundle_deal_item` (
    `id` BIGINT PRIMARY KEY,
    `bundle_id` BIGINT NOT NULL,
    `product_id` BIGINT,
    `sku_id` BIGINT,
    `is_main` BIT DEFAULT 0,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_bundle_id` (`bundle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐商品';

-- 16. 直播间
CREATE TABLE IF NOT EXISTS `mo_live_room` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(200),
    `cover_url` VARCHAR(500),
    `host_name` VARCHAR(100),
    `host_avatar` VARCHAR(500),
    `status` VARCHAR(20) DEFAULT 'OFFLINE' COMMENT 'OFFLINE/LIVE/ENDED',
    `viewer_count` INT DEFAULT 0,
    `product_count` INT DEFAULT 0,
    `start_time` DATETIME,
    `end_time` DATETIME,
    `create_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间';

CREATE TABLE IF NOT EXISTS `mo_live_room_product` (
    `id` BIGINT PRIMARY KEY,
    `room_id` BIGINT NOT NULL,
    `product_id` BIGINT,
    `sort_order` INT DEFAULT 0,
    `status` VARCHAR(20) DEFAULT 'WAITING' COMMENT 'EXPLAINING/WAITING/DONE',
    `create_time` DATETIME NOT NULL,
    INDEX `idx_room_id` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播商品';

-- 17. 订阅
CREATE TABLE IF NOT EXISTS `mo_subscribe_plan` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(100),
    `price` DECIMAL(10,2),
    `duration_months` INT,
    `description` TEXT,
    `features` TEXT COMMENT 'JSON array',
    `sort_order` INT DEFAULT 0,
    `active` BIT DEFAULT 1,
    `create_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅方案';

CREATE TABLE IF NOT EXISTS `mo_user_subscription` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `plan_id` BIGINT NOT NULL,
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CANCELLED/EXPIRED',
    `start_time` DATETIME,
    `end_time` DATETIME,
    `auto_renew` BIT DEFAULT 1,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅';

CREATE TABLE IF NOT EXISTS `mo_subscription_history` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `plan_id` BIGINT NOT NULL,
    `action` VARCHAR(20) COMMENT 'SUBSCRIBE/CANCEL/RENEW/UPGRADE',
    `amount` DECIMAL(10,2),
    `create_time` DATETIME NOT NULL,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅历史';

-- 18. 抽奖
CREATE TABLE IF NOT EXISTS `mo_lottery` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(100),
    `description` VARCHAR(500),
    `prize_image` VARCHAR(500),
    `prize_name` VARCHAR(100),
    `probability` DECIMAL(5,4) DEFAULT 0,
    `daily_free` INT DEFAULT 1,
    `points_cost` INT DEFAULT 0,
    `active` BIT DEFAULT 1,
    `start_time` DATETIME,
    `end_time` DATETIME,
    `create_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽奖';

CREATE TABLE IF NOT EXISTS `mo_lottery_record` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `lottery_id` BIGINT NOT NULL,
    `prize_name` VARCHAR(100),
    `won` BIT DEFAULT 0,
    `used_free_spin` BIT DEFAULT 0,
    `points_spent` INT DEFAULT 0,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽奖记录';

-- 19. 管理后台 - 管理员用户表
CREATE TABLE IF NOT EXISTS `mo_admin_user` (
    `id` BIGINT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `name` VARCHAR(100),
    `email` VARCHAR(255),
    `role` VARCHAR(50) DEFAULT 'OPERATOR' COMMENT 'SUPER_ADMIN/OPERATOR',
    `status` VARCHAR(20) DEFAULT 'ACTIVE',
    `last_login_time` DATETIME,
    `create_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理后台用户';
