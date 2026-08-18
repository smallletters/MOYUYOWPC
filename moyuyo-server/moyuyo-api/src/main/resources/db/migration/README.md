# Flyway 迁移脚本规范

本目录存放 Flyway 数据库迁移脚本（按版本顺序执行，**已应用的迁移不可修改**）。

## 命名规范

### 1. 版本号格式

```
V<YYYYMMDD>_<NN>__<description>.sql
```

示例：
- ✅ `V20260815_01__add_user_email_index.sql`
- ✅ `V20260815_02__create_payment_log_table.sql`
- ❌ `V1__init.sql`（仅初始化期使用）
- ❌ `V0__base.sql`（语义不清）
- ❌ `V20260815_add_index.sql`（缺序号）

### 2. 序号约定
- `01` 起编号
- 同一天可连续编号 `01`、`02`...
- 不要使用 `V1`、`V2` 等简略版本号（不利于查找与回滚）

### 3. 描述约定
- 简短英文，动词开头（add/create/modify/drop/fix）
- 多表用 `add_multi_*` 或多个独立迁移

## DDL 编写规范

### ✅ 安全做法（必须遵守）

```sql
-- 表已存在则跳过（防止重复执行失败）
CREATE TABLE IF NOT EXISTS `mo_xxx` (...);

-- 列已存在则跳过（MySQL 8.0.29+ 支持）
ALTER TABLE `mo_xxx` ADD COLUMN IF NOT EXISTS `col` VARCHAR(50);

-- 索引已存在则跳过
ALTER TABLE `mo_xxx` ADD INDEX IF NOT EXISTS `idx_xxx` (`col`);

-- 数据修复幂等
INSERT INTO ... ON DUPLICATE KEY UPDATE ...;
```

### ⚠️ 不幂等的操作（必须先检查）

```sql
-- 删除列（无法 IF EXISTS 旧版）：拆分为可重入
-- 步骤 1：标记删除 ALTER TABLE ... DROP COLUMN_IF_EXISTS（旧版本不支持时用存储过程）
-- 步骤 2：在新版本中执行

-- 修改默认值
ALTER TABLE `mo_xxx` ALTER COLUMN `col` SET DEFAULT 'xxx';
```

## 约束规范

| 场景 | 要求 |
|------|------|
| 主键 | `BIGINT AUTO_INCREMENT PRIMARY KEY`（雪花算法由应用生成） |
| 业务唯一字段 | `UNIQUE KEY uk_xxx (xxx)` |
| 外键 | **慎用**！外键约束在分库分表后失效，建议应用层维护 |
| 索引 | 命名 `idx_<table>_<col>` 或 `uk_<table>_<col>`（唯一） |
| 字符集 | `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` |
| 时间字段 | `create_time DATETIME DEFAULT CURRENT_TIMESTAMP`<br>`update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` |
| 软删除 | `delete_status TINYINT DEFAULT 0`（0=正常，1=已删除） |

## 分区表规范

`mo_order` 等大表必须分区：

```sql
CREATE TABLE IF NOT EXISTS `mo_order` (
    ...
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
PARTITION BY RANGE (TO_DAYS(create_time)) (
    PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
    PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
    ...
    PARTITION p_max  VALUES LESS THAN MAXVALUE
);
```

每月新增分区由维护脚本（`scripts/maintenance/order-partition-rollover.ps1`）执行。

## 测试与验证

### CI 中真实执行 Flyway 迁移
参见 `.github/workflows/backend-ci.yml`：
```yaml
- name: Flyway 迁移校验
  run: |
    mvn -pl moyuyo-api flyway:migrate \
      -Dflyway.url=jdbc:mysql://localhost:3306/moyuyo_test \
      -Dflyway.user=root -Dflyway.password=test \
      -Dflyway.locations=classpath:db/migration \
      -Dflyway.baselineOnMigrate=true -B
```

### 本地测试
```bash
mvn -pl moyuyo-api flyway:migrate \
  -Dflyway.url=jdbc:mysql://localhost:3306/moyuyo_dev \
  -Dflyway.locations=classpath:db/migration,classpath:db/seed \
  -Dflyway.baselineOnMigrate=true -B

mvn -pl moyuyo-api flyway:info \
  -Dflyway.url=jdbc:mysql://localhost:3306/moyuyo_dev
```

## 禁忌

- ❌ **不要修改已应用的迁移**（即使只是改注释）—— Flyway checksum 校验会失败
- ❌ 不要在迁移中执行长时间运行的 DML（影响生产可用性）
- ❌ 不要删除其他迁移还在引用的表 / 列
- ❌ 不要在迁移中插入测试数据（应放入 `db/seed/` 目录）
- ❌ 不要使用 `DROP DATABASE` 等危险操作