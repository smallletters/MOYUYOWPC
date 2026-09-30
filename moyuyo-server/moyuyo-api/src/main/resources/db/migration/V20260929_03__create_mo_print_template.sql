-- 订单打印模板表
-- 用于支撑 AdminOrderOpsController /print/template CRUD 接口
-- 解决 OrderPrint.vue 编辑模板"刷新即丢失"问题
-- 表名约定：mo_ 前缀，与现有 Flyway 迁移保持一致
CREATE TABLE IF NOT EXISTS `mo_print_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` VARCHAR(64) NOT NULL COMMENT '模板编码:PICK(拣货单)/PACK(打包单)/SHIP(发货单)/LABEL(配货标签)/SHIPPING_LABEL(快递面单)',
  `name` VARCHAR(100) NOT NULL COMMENT '模板名称',
  `paper_size` VARCHAR(20) NOT NULL DEFAULT 'A4' COMMENT '默认纸张规格',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '模板说明',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认模板: 0否 1是',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `creator` VARCHAR(100) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单打印模板';

-- 初始化 5 个打印模板，与 OrderPrint.vue 中的 printTemplates 初始示例保持一致
INSERT IGNORE INTO `mo_print_template`
  (`code`, `name`, `paper_size`, `description`, `is_default`, `sort_order`, `creator`)
VALUES
  ('PICK',           '拣货单',     'A4',           '按商品汇总，含货位 / SKU / 数量',             1, 10, '系统'),
  ('PACK',           '打包单',     'A5',           '按订单展示商品明细，放入包裹',                 0, 20, '系统'),
  ('SHIP',           '发货单',     'A4',           '含收件人信息 / 订单号 / 商品清单',           0, 30, '系统'),
  ('LABEL',          '配货标签',   'thermal-100',  '地址标签，可粘贴至包裹',                       0, 40, '系统'),
  ('SHIPPING_LABEL', '快递面单',   'thermal-100',  '燕文等承运商电子面单 PDF，启用 SDK 后可直接调取并打印', 0, 50, '系统');