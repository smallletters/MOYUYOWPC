#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""查找所有 Caused by 块和上下文"""
import re

LOG_FILE = r'D:\MOYUYOWPC\moyuyo-server\api.log.out'

with open(LOG_FILE, 'r', encoding='utf-8', errors='ignore') as f:
    content = f.read()

# 找所有 "Field ... doesn't have a default value" 错误
errors = re.findall(r"Field '([^']+)' doesn't have a default value", content)
print(f"=== 所有 Field 错误 (去重) ===")
for f in sorted(set(errors)):
    print(f"  - {f}")

# 找所有 Duplicate entry
dups = re.findall(r"Duplicate entry '([^']+)' for key '([^']+)'", content)
print(f"\n=== 所有 Duplicate entry (去重) ===")
seen = set()
for v, k in dups:
    if (v, k) not in seen:
        seen.add((v, k))
        print(f"  - value='{v}' key='{k}'")
