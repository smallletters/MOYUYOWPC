-- ============================================================
-- V20260907_01__align_pet_growth_record_schema.sql
-- 对齐 mo_growth_record 与 GrowthRecordEntity 字段结构
-- 背景：V6__init_pet_hub.sql 的历史建表列为
--   type/title/record_time/note/image_url/reminder_type/alert_date
-- 而 GrowthRecordEntity 运行时读写的是
--   user_id/record_type/content/media_url/record_date + update_time
-- 二者完全错位导致成长记录读写直接报错（表结构历史遗留不一致）。
-- 本迁移同时修正 V6 建表源头，并用存储过程做幂等修复，
-- 兼容"已按旧 V6 建表"的存量库与"按新 V6 建表"的全新库。
-- 注意：禁止手工反复重跑本迁移，正常由应用启动时 Flyway 自动执行。
-- ============================================================

-- 幂等修复：仅当旧列 `type` 存在（说明是旧 V6 建出来的表）时才执行改造
DROP PROCEDURE IF EXISTS _moyuyo_align_pet_growth_record;
DELIMITER //
CREATE PROCEDURE _moyuyo_align_pet_growth_record()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'mo_growth_record'
          AND COLUMN_NAME = 'type'
    ) THEN
        -- 1. 先移除旧索引，避免后续重建同名索引冲突
        ALTER TABLE mo_growth_record
          DROP INDEX idx_pet_type,
          DROP INDEX idx_record_time;

        -- 2. 补齐实体所需的新列
        ALTER TABLE mo_growth_record
          ADD COLUMN user_id     BIGINT       NULL COMMENT '记录人ID',
          ADD COLUMN record_type VARCHAR(16)  NULL COMMENT 'VACCINE/DEWORM/EXAM/BATH',
          ADD COLUMN content     VARCHAR(512) NULL COMMENT '记录内容（如疫苗名称/驱虫药/护理描述）',
          ADD COLUMN media_url   VARCHAR(512) NULL COMMENT '附件图片URL',
          ADD COLUMN record_date DATE         NULL COMMENT '护理发生日期',
          ADD COLUMN update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

        -- 3. 旧数据搬迁：type→record_type，record_time→record_date，
        --    image_url→media_url，title/note 合并进 content
        UPDATE mo_growth_record
           SET record_type = type,
               record_date = record_time,
               media_url   = image_url,
               content     = CONCAT_WS('，', NULLIF(title, ''), NULLIF(note, ''));

        -- 4. user_id 从 mo_pet 回填；孤儿记录（宠物已被删除）先行兜底清理
        DELETE gr
          FROM mo_growth_record gr
          LEFT JOIN mo_pet p ON gr.pet_id = p.id
         WHERE p.id IS NULL;

        UPDATE mo_growth_record gr
          JOIN mo_pet p ON gr.pet_id = p.id
           SET gr.user_id = p.user_id;

        -- 5. 收紧约束为 NOT NULL
        ALTER TABLE mo_growth_record
          MODIFY COLUMN user_id     BIGINT      NOT NULL COMMENT '记录人ID',
          MODIFY COLUMN record_type VARCHAR(16) NOT NULL COMMENT 'VACCINE/DEWORM/EXAM/BATH',
          MODIFY COLUMN record_date DATE        NOT NULL COMMENT '护理发生日期';

        -- 6. 清理旧列（旧索引已在第 1 步移除，删列不受索引限制）
        ALTER TABLE mo_growth_record
          DROP COLUMN type,
          DROP COLUMN title,
          DROP COLUMN record_time,
          DROP COLUMN note,
          DROP COLUMN image_url,
          DROP COLUMN reminder_type,
          DROP COLUMN alert_date;

        -- 7. 按新结构重建索引
        ALTER TABLE mo_growth_record
          ADD KEY idx_pet_type (`pet_id`, `record_type`),
          ADD KEY idx_pet_date (`pet_id`, `record_date`);
    END IF;
END //
DELIMITER ;

CALL _moyuyo_align_pet_growth_record();
DROP PROCEDURE IF EXISTS _moyuyo_align_pet_growth_record;
