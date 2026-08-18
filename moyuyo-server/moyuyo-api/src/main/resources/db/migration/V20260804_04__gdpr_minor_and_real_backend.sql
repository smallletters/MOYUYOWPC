-- ============================================================
-- MOYUYO V20260804_04 - GDPR 未成年人保护 + 快速操作 + 真实后端支撑
-- 包含：未成年人年龄验证 / 家长同意凭证 / 通知中心 / 活动草稿 / 直播监控
--       / 拆包裹策略版本 / 仓库智能分配 / 用户画像聚合源
-- 表名约定：mo_ 前缀，Flyway 与现有迁移保持一致
-- ============================================================

-- 1. GDPR 未成年人年龄验证记录
CREATE TABLE IF NOT EXISTS `mo_minor_verification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `age` INT DEFAULT NULL COMMENT '声明年龄',
    `birth_date` DATE DEFAULT NULL COMMENT '出生日期',
    `verify_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'VERIFIED/PENDING/FAILED/EXEMPTED',
    `guardian_consent` VARCHAR(16) DEFAULT NULL COMMENT 'GRANTED/PENDING/REVOKED/EXEMPTED',
    `risk_level` VARCHAR(8) DEFAULT 'LOW' COMMENT 'LOW/MIDDLE/HIGH',
    `verify_channel` VARCHAR(32) DEFAULT 'BIRTHDATE' COMMENT 'BIRTHDATE/IDCARD/FACE/THIRD_PARTY',
    `verified_by` VARCHAR(64) DEFAULT NULL COMMENT '验证来源（系统/人工）',
    `verified_time` DATETIME DEFAULT NULL COMMENT '最近一次验证时间',
    `next_check_time` DATETIME DEFAULT NULL COMMENT '下一次复检时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_verify_status` (`verify_status`),
    KEY `idx_risk_level` (`risk_level`),
    KEY `idx_birth_date` (`birth_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GDPR 未成年人年龄验证记录';

-- 2. GDPR 家长同意凭证存档
CREATE TABLE IF NOT EXISTS `mo_minor_consent_proof` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '未成年人用户ID',
    `guardian_name` VARCHAR(64) DEFAULT NULL COMMENT '监护人姓名',
    `guardian_id_no` VARCHAR(64) DEFAULT NULL COMMENT '监护人身份证号（脱敏存储）',
    `guardian_phone` VARCHAR(20) DEFAULT NULL COMMENT '监护人联系电话',
    `relationship` VARCHAR(32) DEFAULT NULL COMMENT '关系：父母/法定监护人',
    `proof_type` VARCHAR(32) DEFAULT 'ELECTRONIC' COMMENT '凭证类型：ELECTRONIC/PAPER/THIRD_PARTY',
    `proof_url` VARCHAR(500) DEFAULT NULL COMMENT '凭证附件地址',
    `signed_at` DATETIME DEFAULT NULL COMMENT '签署时间',
    `expire_at` DATETIME DEFAULT NULL COMMENT '到期时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED/EXPIRED',
    `remark` VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_signed_at` (`signed_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GDPR 家长同意凭证存档';

-- 3. GDPR 快速操作执行流水（导出包 / 账号删除宽限期 / 撤销同意）
CREATE TABLE IF NOT EXISTS `mo_gdpr_quick_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `action_type` VARCHAR(32) NOT NULL COMMENT 'EXPORT/DELETE/CONSENT_EXPORT/REVOKE/COMPLAINT',
    `target_user_id` BIGINT DEFAULT NULL COMMENT '目标用户ID',
    `format` VARCHAR(16) DEFAULT NULL COMMENT '导出格式 JSON/CSV',
    `download_url` VARCHAR(500) DEFAULT NULL COMMENT '导出包下载地址',
    `grace_period_end` DATETIME DEFAULT NULL COMMENT '账号删除宽限期截止时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/REVOKED/REJECTED',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作管理员ID',
    `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作管理员',
    `note` VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_action_type` (`action_type`),
    KEY `idx_target_user_id` (`target_user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GDPR 快速操作执行流水';

-- 4. 管理端通知中心
CREATE TABLE IF NOT EXISTS `mo_admin_notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `type` VARCHAR(32) NOT NULL COMMENT 'REFUND/ORDER/RISK/COMPLAINT/INVENTORY/SYSTEM',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` VARCHAR(1000) DEFAULT NULL COMMENT '通知内容',
    `target_path` VARCHAR(200) DEFAULT NULL COMMENT '点击后跳转路径',
    `ref_id` VARCHAR(64) DEFAULT NULL COMMENT '关联业务ID',
    `read_status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    `priority` VARCHAR(8) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/HIGH/URGENT',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_read_status` (`read_status`),
    KEY `idx_type` (`type`),
    KEY `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端通知中心';

-- 5. 营销活动草稿状态由 Service 层通过 status='DRAFT' 表达，无需新增字段

-- 6. 直播监控规则与告警
CREATE TABLE IF NOT EXISTS `mo_live_monitor_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `rule_name` VARCHAR(100) NOT NULL COMMENT '规则名称',
    `rule_type` VARCHAR(32) NOT NULL COMMENT 'PRODUCT/CONTENT/COMPLIANCE',
    `keyword` VARCHAR(200) DEFAULT NULL COMMENT '命中关键词（逗号分隔）',
    `action` VARCHAR(32) NOT NULL DEFAULT 'WARN' COMMENT 'WARN/MUTE/STOP',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `creator` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_rule_type` (`rule_type`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播合规监控规则';

CREATE TABLE IF NOT EXISTS `mo_live_violation_alert` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_id` BIGINT DEFAULT NULL COMMENT '直播间ID',
    `room_title` VARCHAR(200) DEFAULT NULL,
    `host_name` VARCHAR(64) DEFAULT NULL,
    `rule_id` BIGINT DEFAULT NULL COMMENT '命中规则ID',
    `rule_name` VARCHAR(100) DEFAULT NULL,
    `content` VARCHAR(500) DEFAULT NULL COMMENT '违规内容',
    `severity` VARCHAR(8) NOT NULL DEFAULT 'LOW' COMMENT 'LOW/MIDDLE/HIGH',
    `handled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0未处理 1已处理',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_room_id` (`room_id`),
    KEY `idx_handled` (`handled`),
    KEY `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播违规告警';

-- 7. 拆包裹策略版本
CREATE TABLE IF NOT EXISTS `mo_split_package_rule_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `version_code` VARCHAR(32) NOT NULL COMMENT '规则版本号，如 v2026.08.04',
    `description` VARCHAR(500) DEFAULT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `creator` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version_code` (`version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拆包裹策略版本';

CREATE TABLE IF NOT EXISTS `mo_split_package_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `version_id` BIGINT NOT NULL COMMENT '所属规则版本ID',
    `rule_name` VARCHAR(100) NOT NULL,
    `trigger_type` VARCHAR(32) NOT NULL COMMENT 'WEIGHT/VOLUME/CATEGORY/COUNTRY',
    `trigger_value` VARCHAR(200) DEFAULT NULL COMMENT '触发值',
    `action` VARCHAR(32) NOT NULL DEFAULT 'SPLIT' COMMENT 'SPLIT/MERGE/KEEP',
    `priority` INT NOT NULL DEFAULT 100,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_version_id` (`version_id`),
    KEY `idx_trigger_type` (`trigger_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拆包裹规则明细';

-- 8. 用户行为事件源（用于用户画像聚合）
CREATE TABLE IF NOT EXISTS `mo_user_behavior_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `event_type` VARCHAR(32) NOT NULL COMMENT 'VIEW/CART/ORDER/PAY/SEARCH/SHARE',
    `ref_id` VARCHAR(64) DEFAULT NULL COMMENT '商品ID/订单ID/搜索词',
    `category` VARCHAR(64) DEFAULT NULL COMMENT '一级类目',
    `amount` DECIMAL(12,2) DEFAULT NULL COMMENT '金额（订单/支付）',
    `device` VARCHAR(32) DEFAULT NULL COMMENT 'IOS/ANDROID/WEB',
    `channel` VARCHAR(32) DEFAULT NULL COMMENT '渠道',
    `hour` INT DEFAULT NULL COMMENT '小时 0-23',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time` DESC),
    KEY `idx_event_type` (`event_type`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为事件源（画像聚合）';

-- 9. 扩展 mo_clearance：支撑异常告警 / 时效 / 目的国对比
-- 注意：使用存储过程 + information_schema 实现幂等列扩展，
-- 避免在已有字段的目标库上重复执行时因 Duplicate column 报错（MySQL 8.0.29+ 才支持 ADD COLUMN，
-- 为兼容更低版本与历史实例，此处使用程序化方式）
DROP PROCEDURE IF EXISTS _moyuyo_add_column_if_missing;
DELIMITER //
CREATE PROCEDURE _moyuyo_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL _moyuyo_add_column_if_missing('mo_clearance', 'country',          'VARCHAR(32) DEFAULT NULL COMMENT \'目的国\'');
CALL _moyuyo_add_column_if_missing('mo_clearance', 'exception_reason', 'VARCHAR(200) DEFAULT NULL COMMENT \'异常原因\'');
CALL _moyuyo_add_column_if_missing('mo_clearance', 'handler',          'VARCHAR(64) DEFAULT NULL COMMENT \'处理人\'');
CALL _moyuyo_add_column_if_missing('mo_clearance', 'days_cost',        'INT DEFAULT NULL COMMENT \'清关用时（天）\'');

-- 10. 仓库 KPI 扩展：容量 / 利用率 / 智能分配
CALL _moyuyo_add_column_if_missing('mo_warehouse', 'capacity',        'DECIMAL(12,2) DEFAULT 0 COMMENT \'总容量（立方米）\'');
CALL _moyuyo_add_column_if_missing('mo_warehouse', 'used_capacity',   'DECIMAL(12,2) DEFAULT 0 COMMENT \'已用容量\'');
CALL _moyuyo_add_column_if_missing('mo_warehouse', 'manager_phone',   'VARCHAR(20) DEFAULT NULL');
CALL _moyuyo_add_column_if_missing('mo_warehouse', 'in_transit_qty',  'INT DEFAULT 0 COMMENT \'在途包裹数\'');

-- 清理临时过程
DROP PROCEDURE IF EXISTS _moyuyo_add_column_if_missing;

-- 11. 仓库分配建议表
CREATE TABLE IF NOT EXISTS `mo_warehouse_allocation_suggest` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `product_id` BIGINT DEFAULT NULL COMMENT '商品ID',
    `product_name` VARCHAR(200) DEFAULT NULL,
    `from_warehouse` VARCHAR(64) DEFAULT NULL COMMENT '源仓库',
    `to_warehouse` VARCHAR(64) DEFAULT NULL COMMENT '目标仓库',
    `qty` INT DEFAULT 0 COMMENT '建议调拨量',
    `reason` VARCHAR(500) DEFAULT NULL COMMENT '建议理由',
    `priority` INT DEFAULT 100,
    `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/APPLIED/IGNORED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库智能分配建议';

-- ============================================================
-- 初始化示例数据：保证前端首屏有内容
-- ============================================================

-- 未成年人验证记录（覆盖 VERIFIED/PENDING/FAILED 三种状态 + 13/16 以下各 2 条）
INSERT IGNORE INTO `mo_minor_verification`
    (`id`, `user_id`, `age`, `birth_date`, `verify_status`, `guardian_consent`, `risk_level`, `verify_channel`, `verified_by`, `verified_time`, `next_check_time`)
VALUES
    (1, 100238, 9,  '2016-08-12', 'VERIFIED', 'GRANTED',  'LOW',    'BIRTHDATE',  '系统', NOW(), DATE_ADD(NOW(), INTERVAL 180 DAY)),
    (2, 100415, 14, '2011-09-04', 'PENDING',  'PENDING',  'HIGH',   'BIRTHDATE',  '系统', NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY)),
    (3, 100667, 11, '2014-12-21', 'VERIFIED', 'GRANTED',  'LOW',    'IDCARD',     '系统', NOW(), DATE_ADD(NOW(), INTERVAL 365 DAY)),
    (4, 100892, 8,  '2017-05-18', 'FAILED',   'REVOKED',  'HIGH',   'THIRD_PARTY','系统', NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
    (5, 101024, 15, '2011-03-09', 'PENDING',  'EXEMPTED', 'MIDDLE', 'BIRTHDATE',  '系统', NOW(), DATE_ADD(NOW(), INTERVAL 14 DAY)),
    (6, 101188, 12, '2014-02-26', 'VERIFIED', 'GRANTED',  'LOW',    'BIRTHDATE',  '系统', NOW(), DATE_ADD(NOW(), INTERVAL 365 DAY));

-- 家长同意凭证
INSERT IGNORE INTO `mo_minor_consent_proof`
    (`id`, `user_id`, `guardian_name`, `guardian_id_no`, `guardian_phone`, `relationship`, `proof_type`, `proof_url`, `signed_at`, `expire_at`, `status`)
VALUES
    (1, 100238, '张某某', '410**********1234', '138****1234', '父母',        'ELECTRONIC', '/storage/gdpr/proof_100238.pdf', NOW(), DATE_ADD(NOW(), INTERVAL 365 DAY), 'ACTIVE'),
    (2, 100415, '李某某', '410**********5678', '139****5678', '父母',        'ELECTRONIC', '/storage/gdpr/proof_100415.pdf', NOW(), DATE_ADD(NOW(), INTERVAL 365 DAY), 'ACTIVE'),
    (3, 100667, '王某某', '420**********9012', '137****9012', '法定监护人',  'PAPER',      '/storage/gdpr/proof_100667.pdf', NOW(), DATE_ADD(NOW(), INTERVAL 365 DAY), 'ACTIVE');

-- 通知中心（与前端 AdminLayout 通知 mock 字段保持一致）
INSERT IGNORE INTO `mo_admin_notification`
    (`id`, `type`, `title`, `content`, `target_path`, `priority`, `read_status`)
VALUES
    (1, 'REFUND',    '您有 3 笔退款待审批',     '近期订单退款请求等待处理',         '/refund',  'HIGH',    0),
    (2, 'ORDER',     '近 24 小时有 12 笔订单需处理','订单运营看板提示',              '/orders',  'NORMAL',  0),
    (3, 'RISK',      '风控告警 1 条：异常登录',   '同一账号在多个省份连续登录',       '/risk-alert', 'URGENT', 0);

-- 直播监控规则示例
INSERT IGNORE INTO `mo_live_monitor_rule`
    (`id`, `rule_name`, `rule_type`, `keyword`, `action`, `enabled`, `creator`)
VALUES
    (1, '违禁词监控',     'CONTENT', '违禁,虚假,绝对,百分百', 'WARN', 1, '系统'),
    (2, '禁售商品监控',   'PRODUCT', '药品,医疗器械,烟酒',     'STOP', 1, '系统'),
    (3, '未成年人保护监控', 'CONTENT', '儿童,未成年人',          'WARN', 1, '系统');

-- 直播违规告警示例
INSERT IGNORE INTO `mo_live_violation_alert`
    (`id`, `room_id`, `room_title`, `host_name`, `rule_id`, `rule_name`, `content`, `severity`, `handled`, `create_time`)
VALUES
    (1, 8001, '夏日宠粮大促', '主播小汪', 1, '违禁词监控', '直播间口播「百分百中奖」',     'MIDDLE', 0, NOW()),
    (2, 8002, '猫咪零食专场', '主播阿喵', 2, '禁售商品监控', '上架了 SKU 未通过审核的商品', 'HIGH',   0, NOW()),
    (3, 8003, '萌宠日常',   '主播团团', 3, '未成年人保护监控', '评论出现未成年人引流',     'LOW',    1, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 拆包裹规则版本
INSERT IGNORE INTO `mo_split_package_rule_version`
    (`id`, `version_code`, `description`, `enabled`, `creator`)
VALUES
    (1, 'v2026.08.01', '默认拆包裹规则集：按重量/品类/目的地拆分', 1, '系统');

INSERT IGNORE INTO `mo_split_package_rule`
    (`id`, `version_id`, `rule_name`, `trigger_type`, `trigger_value`, `action`, `priority`, `enabled`)
VALUES
    (1, 1, '超重自动拆包',  'WEIGHT',   '>10kg',         'SPLIT', 10, 1),
    (2, 1, '多品类拆包',    'CATEGORY', '跨一级类目',     'SPLIT', 20, 1),
    (3, 1, '小包合并',      'WEIGHT',   '<0.5kg',         'MERGE', 30, 1),
    (4, 1, '敏感品类直发',  'CATEGORY', '食品/液体',      'KEEP',  40, 1);

-- 清关示例数据（覆盖 PENDING/INSPECTING/CLEARED/REJECTED + 异常 + 不同时长）
INSERT IGNORE INTO `mo_clearance`
    (`id`, `declaration_no`, `order_no`, `product_name`, `hs_code`, `tax_rate`, `status`,
     `declare_time`, `clearance_time`, `country`, `exception_reason`, `handler`, `days_cost`)
VALUES
    (1, 'DC20260800001', 'O202608010001', '宠物零食罐头',   '23091000', 8.0,  'CLEARED',    DATE_SUB(NOW(), INTERVAL 7 DAY),  DATE_SUB(NOW(), INTERVAL 5 DAY), '美国',    NULL,                              '清关员A', 2),
    (2, 'DC20260800002', 'O202608010002', '猫爬架',         '44219990', 5.0,  'CLEARED',    DATE_SUB(NOW(), INTERVAL 6 DAY),  DATE_SUB(NOW(), INTERVAL 4 DAY), '德国',    NULL,                              '清关员B', 2),
    (3, 'DC20260800003', 'O202608010003', '宠物饮水机',     '85167990', 7.5,  'INSPECTING', DATE_SUB(NOW(), INTERVAL 3 DAY),  NULL,                            '加拿大',  NULL,                              '清关员A', NULL),
    (4, 'DC20260800004', 'O202608010004', '狗用磨牙棒',     '23091000', 8.0,  'PENDING',    DATE_SUB(NOW(), INTERVAL 1 DAY),  NULL,                            '英国',    NULL,                              NULL,       NULL),
    (5, 'DC20260800005', 'O202608010005', '宠物衣服',       '62044300', 12.0, 'REJECTED',   DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), '澳大利亚','品名申报与实际不符','清关员C', 2),
    (6, 'DC20260800006', 'O202608010006', '宠物玩具球',     '95030089', 6.0,  'CLEARED',    DATE_SUB(NOW(), INTERVAL 4 DAY),  DATE_SUB(NOW(), INTERVAL 3 DAY), '美国',    NULL,                              '清关员A', 1),
    (7, 'DC20260800007', 'O202608010007', '猫砂',           '25010000', 4.0,  'CLEARED',    DATE_SUB(NOW(), INTERVAL 9 DAY),  DATE_SUB(NOW(), INTERVAL 7 DAY), '日本',    NULL,                              '清关员B', 2),
    (8, 'DC20260800008', 'O202608010008', '鱼缸加热棒',     '85167990', 7.5,  'PENDING',    DATE_SUB(NOW(), INTERVAL 2 DAY),  NULL,                            '法国',    NULL,                              NULL,       NULL);

-- 仓库 KPI / 智能分配示例
UPDATE `mo_warehouse` SET `capacity`=5000, `used_capacity`=3200, `manager_phone`='138****0001', `in_transit_qty`=18 WHERE `id`=1;
UPDATE `mo_warehouse` SET `capacity`=3000, `used_capacity`=2100, `manager_phone`='138****0002', `in_transit_qty`=12 WHERE `id`=2;
UPDATE `mo_warehouse` SET `capacity`=4000, `used_capacity`=3800, `manager_phone`='138****0003', `in_transit_qty`=8  WHERE `id`=3;

INSERT IGNORE INTO `mo_warehouse_allocation_suggest`
    (`id`, `product_id`, `product_name`, `from_warehouse`, `to_warehouse`, `qty`, `reason`, `priority`, `status`)
VALUES
    (1, 1001, '宠物零食罐头', '美西仓', '美东仓', 200, '美西仓库存接近上限，且美东仓近 7 日销量提升 30%', 10, 'PENDING'),
    (2, 1002, '猫爬架',       '美东仓', '美西仓', 80,  '美西仓该 SKU 库存低于安全水位',                  20, 'PENDING'),
    (3, 1003, '宠物饮水机',   '美西仓', '欧洲仓', 50,  '欧洲仓夏季需求激增',                            30, 'APPLIED');

-- 用户行为事件示例数据（用于画像聚合）
INSERT IGNORE INTO `mo_user_behavior_event`
    (`id`, `user_id`, `event_type`, `ref_id`, `category`, `amount`, `device`, `channel`, `hour`, `create_time`)
VALUES
    (1, 100238, 'VIEW',  'p1001', '宠物零食',     NULL,    'IOS',     'APP',       10, DATE_SUB(NOW(), INTERVAL 30 DAY)),
    (2, 100238, 'VIEW',  'p1002', '宠物零食',     NULL,    'IOS',     'APP',       11, DATE_SUB(NOW(), INTERVAL 28 DAY)),
    (3, 100238, 'CART',  'p1001', '宠物零食',     NULL,    'IOS',     'APP',       12, DATE_SUB(NOW(), INTERVAL 27 DAY)),
    (4, 100238, 'ORDER', 'O2026X1', '宠物零食',   128.50,  'IOS',     'APP',       12, DATE_SUB(NOW(), INTERVAL 27 DAY)),
    (5, 100238, 'PAY',   'O2026X1', '宠物零食',   128.50,  'IOS',     'APP',       12, DATE_SUB(NOW(), INTERVAL 27 DAY)),
    (6, 100238, 'VIEW',  'p2001', '宠物服饰',     NULL,    'ANDROID', 'H5',        14, DATE_SUB(NOW(), INTERVAL 20 DAY)),
    (7, 100238, 'ORDER', 'O2026X2', '宠物服饰',   258.00,  'ANDROID', 'H5',        15, DATE_SUB(NOW(), INTERVAL 19 DAY)),
    (8, 100238, 'PAY',   'O2026X2', '宠物服饰',   258.00,  'ANDROID', 'H5',        15, DATE_SUB(NOW(), INTERVAL 19 DAY)),
    (9, 100238, 'VIEW',  'p3001', '宠物玩具',     NULL,    'IOS',     'APP',       20, DATE_SUB(NOW(), INTERVAL 15 DAY)),
    (10, 100238, 'ORDER', 'O2026X3', '宠物玩具',  68.00,  'IOS',     'APP',       21, DATE_SUB(NOW(), INTERVAL 14 DAY)),
    (11, 100238, 'PAY',   'O2026X3', '宠物玩具',  68.00,  'IOS',     'APP',       21, DATE_SUB(NOW(), INTERVAL 14 DAY)),
    (12, 100238, 'VIEW',  'p4001', '宠物医疗',     NULL,    'WEB',     'WEB',       22, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (13, 100238, 'ORDER', 'O2026X4', '宠物医疗',  198.00, 'WEB',     'WEB',       22, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (14, 100238, 'PAY',   'O2026X4', '宠物医疗',  198.00, 'WEB',     'WEB',       23, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (15, 100238, 'VIEW',  'p1001', '宠物零食',     NULL,    'IOS',     'APP',       9, DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (16, 100238, 'SEARCH','猫砂',     NULL,        NULL,    'IOS',     'APP',       9, DATE_SUB(NOW(), INTERVAL 2 DAY));
