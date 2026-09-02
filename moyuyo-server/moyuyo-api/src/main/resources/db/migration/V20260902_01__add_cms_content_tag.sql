-- ============================================================
-- V20260902_01__add_cms_content_tag.sql
-- 补齐 mo_cms_content.tag 字段
-- 背景：CmsContentEntity 与前端 CMS 管理页（Banner/推荐位/专题页）均
--       提交了 tag，但 V20260722_02__create_cms_content.sql 建表时漏建，
--       导致 POST /api/admin/cms/create 报 Unknown column 'tag' in 'field list'。
-- ============================================================

ALTER TABLE `mo_cms_content`
    ADD COLUMN `tag` VARCHAR(32) DEFAULT NULL
        COMMENT 'Banner 标签（如 HOT/NEW/限时/新品上架）'
        AFTER `content`;
