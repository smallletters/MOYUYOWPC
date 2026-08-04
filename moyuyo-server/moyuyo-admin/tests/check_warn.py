import json

with open(r'D:\MOYUYOWPC\moyuyo-server\moyuyo-admin\tests\phase3_output.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# 查找 orders 和 products/edit 相关
print("=== orders 相关 ===")
for d in data:
    if 'orders' in str(d.get('path', '')).lower():
        print(d.get('path'), d.get('status'), d.get('buttons'))

print("\n=== products/edit 相关 ===")
for d in data:
    if 'products/edit' in str(d.get('path', '')).lower() or 'products_add' in str(d.get('path', '')).lower():
        print(d.get('path'), d.get('status'), d.get('buttons'))

print("\n=== 状态分布 ===")
from collections import Counter
counter = Counter(d.get('status') for d in data)
print(counter)

print("\n=== 总数 ===")
print(f"Total: {len(data)}")
