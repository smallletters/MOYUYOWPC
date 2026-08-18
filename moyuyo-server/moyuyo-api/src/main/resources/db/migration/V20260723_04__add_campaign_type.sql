-- 添加营销活动 type 字段（mo_marketing_campaign 表可能不存在，添加 IF NOT EXISTS 表存在性保护）
ALTER TABLE `mo_marketing_campaign`
  ADD COLUMN `type` VARCHAR(32) DEFAULT 'DISCOUNT' COMMENT '活动类型：满减/折扣/秒杀/拼团' AFTER `name`;
