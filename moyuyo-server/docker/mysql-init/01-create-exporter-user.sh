#!/bin/bash
# 首次初始化 MySQL 数据卷时创建监控专用账号（mysqld-exporter 使用，禁止 root）
# 由官方 mysql 镜像 entrypoint 自动执行 /docker-entrypoint-initdb.d/*.sh
# 依赖环境变量：MYSQL_ROOT_PASSWORD、MYSQL_EXPORTER_PASSWORD
# 注意：仅在 mysql-data 数据卷为空、首次初始化时执行一次

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY '${MYSQL_EXPORTER_PASSWORD}';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
FLUSH PRIVILEGES;
SQL
