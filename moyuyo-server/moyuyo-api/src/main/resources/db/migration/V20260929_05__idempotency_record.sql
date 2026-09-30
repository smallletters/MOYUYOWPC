-- 幂等记录表：用于防重放（防止前端网络重试 / 并发请求造成重复发货/重复打印）。
-- 场景：批量自动发货时，前端用 (orderId + carrier + trackingNo + 时间桶) 生成幂等键，
--   后端 INSERT 时若幂等键已存在则跳过。
--
-- 唯一索引：scope + key 复合唯一键。
-- 字段：
--   scope  - 业务域（如 "ship_order"、"batch_ship"）
--   biz_id - 业务对象 ID（订单 ID）
--   idem_key - 幂等键（前端生成）
--   created_at - 创建时间，超过保留期可清理

CREATE TABLE IF NOT EXISTS mo_idempotency_record (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  scope         VARCHAR(32)  NOT NULL COMMENT '业务域，如 ship_order / batch_ship',
  biz_id        BIGINT       NOT NULL COMMENT '业务对象 ID，如订单 ID',
  idem_key      VARCHAR(64)  NOT NULL COMMENT '幂等键，由前端生成',
  response_json TEXT         NULL     COMMENT '首次成功响应（用于幂等回放）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scope_biz_key (scope, biz_id, idem_key),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等记录表';

-- 兜底清理：保留最近 30 天的幂等记录
-- 老数据可由定时任务定期 DELETE WHERE created_at < NOW() - INTERVAL 30 DAY