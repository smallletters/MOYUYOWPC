#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""检查关键表的列结构"""
import pymysql
import os, subprocess

# 尝试从 .env 读取
env_path = r"D:\MOYUYOWPC\moyuyo-server\.env"
password = ""
if os.path.exists(env_path):
    with open(env_path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("MYSQL_PASSWORD="):
                password = line.split("=", 1)[1].strip()
                break

# 也尝试从环境变量
if not password:
    password = os.environ.get("MYSQL_PASSWORD", "")

print(f"使用密码长度: {len(password)}")

try:
    conn = pymysql.connect(
        host="localhost", port=3306, user="root",
        password=password, database="moyuyo_dev",
        charset="utf8mb4", connect_timeout=5,
    )
except Exception as e:
    print(f"无法连接: {e}")
    # 也试 user='moyuyo'
    try:
        conn = pymysql.connect(
            host="localhost", port=3306, user="moyuyo",
            password=password, database="moyuyo_dev",
            charset="utf8mb4", connect_timeout=5,
        )
    except Exception as e2:
        print(f"也失败: {e2}")
        raise

cur = conn.cursor()
for tbl in ["mo_cs_session", "mo_points_log", "mo_admin_permission", "mo_admin_role",
            "mo_risk_alert_config", "mo_order_item"]:
    cur.execute(f"DESCRIBE {tbl}")
    print(f"\n=== {tbl} ===")
    for row in cur.fetchall():
        print(" ", row)
conn.close()

