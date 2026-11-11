-- ============================================================
-- V20261111_02__cs_ai_usage.sql
-- AI 智能客服调用用量记录（P1：成本核算与故障排查）
--
-- 记录维度：
--   - 谁（user_id）/ 哪个会话（session_id）
--   - 调了谁（provider）/ 第三方对话ID（request_id、conversation_id）
--   - 花了多少（input/output/total tokens、耗时）
--   - 是否成功、失败原因
-- 保留策略：按 create_time 建索引，后续可用定时任务清理 90 天前数据
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_cs_ai_usage` (
  `id` BIGINT NOT NULL COMMENT '主键（雪花算法）',
  `session_id` BIGINT NOT NULL COMMENT '客服会话ID（mo_cs_session.id）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `provider` VARCHAR(32) NOT NULL COMMENT '提供方：coze / none',
  `request_id` VARCHAR(64) NULL COMMENT '第三方对话ID（扣子 chat_id）',
  `conversation_id` VARCHAR(64) NULL COMMENT '第三方会话ID（扣子 conversation_id）',
  `input_tokens` INT NOT NULL DEFAULT 0 COMMENT '输入 token 数（含知识库片段与历史上下文）',
  `output_tokens` INT NOT NULL DEFAULT 0 COMMENT '输出 token 数',
  `total_tokens` INT NOT NULL DEFAULT 0 COMMENT '总 token 数',
  `duration_ms` INT NOT NULL DEFAULT 0 COMMENT '本次调用耗时（毫秒）',
  `success` TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功：1成功 0失败',
  `error_msg` VARCHAR(500) NULL COMMENT '失败原因（截断至 500 字符）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_usage_create_time` (`create_time`),
  KEY `idx_ai_usage_session` (`session_id`),
  KEY `idx_ai_usage_user` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 智能客服调用用量记录';
