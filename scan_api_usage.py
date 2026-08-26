"""扫描所有 vue 页面，列出哪些调用了哪些 API。"""
import os
import re

PAGES = r"D:\MOYUYOWPC\moyuyo-app\src\pages"
result = []

for root, _, files in os.walk(PAGES):
    for f in files:
        if not f.endswith(".vue"):
            continue
        path = os.path.join(root, f)
        rel = path.replace(PAGES + "\\", "")
        with open(path, "r", encoding="utf-8") as fh:
            content = fh.read()
        has_api = "@/api" in content
        if not has_api:
            result.append((rel, "❌ 无 API 调用"))
            continue
        # 收集 Api.X 调用
        apis = set()
        for m in re.finditer(r"(?:Api|api)\.([a-zA-Z]+)", content):
            apis.add(m.group(1))
        result.append((rel, "✅ " + ", ".join(sorted(apis))))

# 按字母排序输出
result.sort()
for page, status in result:
    print(f"{page:55s}  {status}")