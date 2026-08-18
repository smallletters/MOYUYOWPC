# MOYUYO 运维脚本

本目录提供生产环境运维所需的工具脚本，与 README.md 与 docs/RUNBOOK.md 中的操作流程配套使用。

## 脚本清单

| 脚本 | 平台 | 用途 |
| --- | --- | --- |
| `init-mysql-truststore.sh` | Linux / macOS / WSL | 从 MySQL 服务端拉取 CA 证书，导出 PKCS12 truststore，供应用启用 TLS 服务端证书校验 |
| `backup-mysql.ps1` | Windows / PowerShell | 通过 docker exec 调用 mysqldump 备份 MySQL，自动压缩归档 + 清理过期备份 |
| `restore-mysql.ps1` | Windows / PowerShell | 从 .sql.gz 恢复到目标 MySQL 容器（二次确认后执行，避免误覆盖） |
| `backup-elasticsearch.ps1` | Windows / PowerShell | 通过 ES Snapshot API 创建索引快照，清理过期 snapshot |

## 推荐调度策略

### Windows 任务计划程序

| 任务 | 触发器 | 命令 |
| --- | --- | --- |
| MySQL 每日全量备份 | 每天 03:00 | `powershell -File D:\MOYUYOWPC\moyuyo-server\scripts\backup-mysql.ps1 -ContainerName moyuyo-mysql -DbName moyuyo_prod -BackupDir D:\backups\moyuyo\mysql` |
| ES 每周快照 | 每周日 04:00 | `powershell -File D:\MOYUYOWPC\moyuyo-server\scripts\backup-elasticsearch.ps1 -ContainerName moyuyo-elasticsearch -RepositoryName moyuyo_backup -BackupDir D:\backups\moyuyo\es` |

### Linux crontab（如果迁移到 Linux）

```bash
# MySQL 每日全量备份（凌晨 3 点）
0 3 * * * bash /opt/moyuyo/moyuyo-server/scripts/init-mysql-truststore.sh  # 仅首次需要执行
0 3 * * * /usr/local/bin/docker-exec-backup-mysql.sh

# ES 每周快照（周日凌晨 4 点）
0 4 * * 0 /usr/local/bin/docker-exec-backup-es.sh
```

## 安全要求

1. 所有脚本通过 `.env` 文件读取密码，**严禁**将密码硬编码到脚本或命令行参数中
2. 备份目录应仅允许运维账号读写：`chmod 700 /backups/moyuyo`
3. 备份归档应加密传输到异地（OSS / S3 等），避免单点物理故障
4. 每季度至少执行一次恢复演练（使用 `restore-mysql.ps1` 在预发环境验证备份可恢复）

## 故障排查

| 症状 | 排查方向 |
| --- | --- |
| `mysqldump: Got error` | 容器内 MySQL root 权限不足；检查 `.env` 中 `MYSQL_ROOT_PASSWORD` 与 `docker-compose.yml` 中 `MYSQL_ROOT_PASSWORD` 一致 |
| `tar: command not found` | Windows PowerShell 默认无 tar；Win10 1803+ 已自带，旧版需安装 Git for Windows 或 7-Zip |
| ES snapshot 卡在 STARTED | 检查 ES 集群磁盘空间（snapshot 需要共享卷剩余容量 ≥ 索引总大小） |
| truststore 路径找不到 | 容器启动前必须先运行 `init-mysql-truststore.sh` 生成 `.p12` 文件 |