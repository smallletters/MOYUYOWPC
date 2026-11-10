-- ============================================================
-- V20261109_06__sms_template.sql
-- 短信模板管理（管理后台「短信管理 → 短信模板」）
--
-- 说明：
--   1. 状态：APPROVED=已通过 / PENDING=待审核 / REJECTED=已驳回
--   2. 类型：CODE=验证码 / NOTICE=通知 / MARKETING=营销
--   3. enabled 控制模板是否可用于发送
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_sms_template` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(100) NOT NULL COMMENT '模板名称',
    `type`          VARCHAR(30)  NOT NULL DEFAULT 'NOTICE' COMMENT '模板类型：CODE/NOTICE/MARKETING',
    `template_id`   VARCHAR(60)  DEFAULT NULL COMMENT '服务商模板ID',
    `content`       VARCHAR(1000) NOT NULL COMMENT '模板内容',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：APPROVED/PENDING/REJECTED',
    `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT '驳回原因',
    `enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用 0=禁用',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_sms_tpl_type` (`type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '短信模板';
