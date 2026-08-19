-- 退款管理：补齐 reject_reason / transaction_id / reject_operator_id 字段
-- 解决 #10：拒绝原因仅写日志无法查询申诉，#1：完成退款时第三方流水号无法追溯
-- V20260818_01 迁移脚本

ALTER TABLE `mo_refund`
  ADD COLUMN `reject_reason` VARCHAR(255) NULL COMMENT '拒绝退款原因（必填）' AFTER `description`,
  ADD COLUMN `reject_operator_id` BIGINT NULL COMMENT '拒绝操作人 ID' AFTER `reject_reason`,
  ADD COLUMN `reject_time` DATETIME NULL COMMENT '拒绝时间' AFTER `reject_operator_id`,
  ADD COLUMN `complete_operator_id` BIGINT NULL COMMENT '完成退款操作人（财务）ID' AFTER `reject_time`,
  ADD COLUMN `transaction_id` VARCHAR(64) NULL COMMENT '第三方退款流水号' AFTER `complete_operator_id`;
