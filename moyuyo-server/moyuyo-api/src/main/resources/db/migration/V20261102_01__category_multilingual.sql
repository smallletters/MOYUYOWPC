-- ============================================================
-- V20261102_01__category_multilingual.sql
-- 商品分类多语言支持：在 mo_category 增加 name_i18n 字段。
--
-- 设计说明：
--   1. name 仍作为默认名称（商品列表、后台表格等场景直接使用），保持向后兼容。
--   2. name_i18n 保存 JSON 字符串，例如：
--        {"zh-CN":"洗护美容","en-US":"Bath & Grooming"}
--      由管理后台「分类设置」维护，APP 分类页按当前语言优先读取该字段，
--      未配置时回落到 name 及前端静态字典。
--   3. 仅新增可空列，不影响存量数据与现有查询。
-- ============================================================

ALTER TABLE mo_category
  ADD COLUMN name_i18n VARCHAR(1024) NULL COMMENT '多语言名称 JSON（locale -> name）' AFTER name;
