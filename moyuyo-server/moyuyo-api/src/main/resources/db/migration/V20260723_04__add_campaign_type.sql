-- 添加营销活动 type 字段
ALTER TABLE `mo_marketing_campaign`
  ADD COLUMN IF NOT EXISTS `type` VARCHAR(32) COMMENT '活动类型：满减/折扣/秒杀/拼团' AFTER `name`;
