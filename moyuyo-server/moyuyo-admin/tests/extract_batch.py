#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""找所有 batch-import 相关行"""
import re

LOG_FILE = r'D:\MOYUYOWPC\moyuyo-server\api.log.out'

with open(LOG_FILE, 'r', encoding='utf-8', errors='ignore') as f:
    content = f.read()

# 找含 batch-import 的所有行
for i, line in enumerate(content.split('\n')):
    if 'batch-import' in line:
        print(f"L{i}: {line[:250]}")
        if i > 0:
            # 找下一行的 Caused by
            for j in range(i+1, min(i+30, len(content.split('\n')))):
                l = content.split('\n')[j]
                if 'Caused by' in l or 'Field' in l or 'SQL' in l:
                    print(f"   L{j}: {l[:300]}")
                    break
print()
print("---查找最后一次 Caused by + sku_id---")
# 找所有 Caused by 中包含 sku_id 的
caused_blocks = re.split(r'(?=Caused by:)', content)
for blk in caused_blocks:
    if 'sku_id' in blk:
        idx = blk.find('Caused by')
        print(blk[idx:idx+1500])
        print('=====')
        break
