-- ============================================================
-- V20260804_03__add_operation_log_table.sql
-- 操作审计日志表（合规与追踪）
-- 用于记录关键业务操作：用户、IP、操作类型、详情、状态、耗时
-- 配合 OperationLogAspect 异步批量写入
-- ============================================================

CREATE TABLE IF NOT EXISTS mo_operation_log (
  id              BIGINT       NOT NULL COMMENT '主键',
  type            VARCHAR(64)  NOT NULL COMMENT '操作类型',
  user_id         BIGINT       NULL COMMENT '操作人ID',
  username        VARCHAR(128) NULL COMMENT '操作人用户名/邮箱',
  ip              VARCHAR(64)  NULL COMMENT '客户端 IP',
  detail          TEXT         NULL COMMENT '操作详情（SpEL 渲染后）',
  success         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功 1-成功 0-失败',
  error_message   VARCHAR(512) NULL COMMENT '失败时的错误信息',
  cost_millis     BIGINT       NOT NULL DEFAULT 0 COMMENT '操作耗时 ms',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_oplog_user_time (user_id, create_time),
  KEY idx_oplog_type_time (type, create_time),
  KEY idx_oplog_ip_time (ip, create_time),
  KEY idx_oplog_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- 索引说明：
-- idx_oplog_user_time: 查询某用户最近操作（合规审计最常用）
-- idx_oplog_type_time: 按操作类型聚合统计（管理员自助审计）
-- idx_oplog_ip_time: 按 IP 查询异常操作（风控）
-- idx_oplog_create_time: 按时段清理过期数据（保留 90 天）