-- ============================================================
-- V20261103_01__gdpr_policy_i18n.sql
-- 隐私政策中英双语支持：在 mo_gdpr_policy 增加英文标题/正文。
--
-- 设计说明：
--   1. 保留原有 title / content 作为中文（默认语言）正文，保持向后兼容；
--   2. 新增 title_en / content_en 保存英文版本，管理后台「隐私政策全文」页维护；
--   3. APP 端调用 /api/v1/policies/active?lang=xx 按语言读取，
--      对应语言未配置时回退到另一种语言；
--   4. 仅新增可空列，不影响存量数据与现有查询。
-- ============================================================

ALTER TABLE mo_gdpr_policy
  ADD COLUMN title_en VARCHAR(200) NULL COMMENT '英文标题' AFTER title,
  ADD COLUMN content_en MEDIUMTEXT NULL COMMENT '英文正文' AFTER content;
