-- 将风控规则条件列从 JSON 改为 TEXT
-- 原因：前端条件为自然语言文本（如"单日下单次数 > 10"），MySQL JSON 列会拒绝非法 JSON 导致 409
-- 与测试环境 schema-test.sql 中的 TEXT 定义保持一致
ALTER TABLE `mo_risk_rule` MODIFY COLUMN `condition_json` TEXT NOT NULL COMMENT '触发条件';
