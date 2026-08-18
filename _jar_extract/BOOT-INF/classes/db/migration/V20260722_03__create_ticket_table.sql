-- ============================================================
-- V20260722_03__create_ticket_table.sql
-- 创建工单管理表（实体已存在但缺少建表脚本）
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_ticket` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `ticket_no` VARCHAR(64) NOT NULL COMMENT '工单编号',
    `type` VARCHAR(32) DEFAULT '咨询' COMMENT '工单类型：退款/物流/咨询/投诉',
    `priority` VARCHAR(16) DEFAULT '中' COMMENT '优先级：高/中/低',
    `title` VARCHAR(255) NOT NULL COMMENT '工单标题',
    `content` TEXT COMMENT '工单内容',
    `user_id` BIGINT COMMENT '用户ID',
    `user_name` VARCHAR(64) COMMENT '用户名',
    `agent_name` VARCHAR(64) COMMENT '客服名称',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/CLOSED',
    `response_time` VARCHAR(255) COMMENT '回复内容/响应时间',
    `timeout` TINYINT(1) DEFAULT 0 COMMENT '是否超时',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`),
    INDEX `idx_type` (`type`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单管理表';

-- 插入示例工单数据
INSERT INTO `mo_ticket` (`id`, `ticket_no`, `type`, `priority`, `title`, `content`, `user_id`, `user_name`, `status`, `create_time`) VALUES
(1, 'TKT20260701001', '退款', '高', '订单退款申请', '收到的商品与描述不符，申请退款', 1, '张三', 'PENDING', '2026-07-01 10:00:00'),
(2, 'TKT20260701002', '物流', '中', '物流信息未更新', '包裹已3天没有物流更新', 2, '李四', 'PROCESSING', '2026-07-01 14:30:00'),
(3, 'TKT20260702001', '咨询', '低', '商品使用疑问', '请问这款宠物粮适合几个月大的猫咪', 3, '王五', 'CLOSED', '2026-07-02 09:15:00'),
(4, 'TKT20260703001', '投诉', '高', '客服态度恶劣', '客服人员态度非常不好', 1, '张三', 'PENDING', '2026-07-03 16:00:00'),
(5, 'TKT20260703002', '退款', '中', '重复支付请求退款', '同一订单支付了两次', 4, '赵六', 'PROCESSING', '2026-07-03 11:20:00')
ON DUPLICATE KEY UPDATE `title` = `title`;
