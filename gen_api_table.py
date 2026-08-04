# -*- coding: utf-8 -*-
"""解析 admin.js：模块名 + 函数名 -> 接口，合并测试结果生成《后端接口修复清单》"""
import json
import re

ADMIN_JS = r"d:\MOYUYOWPC\moyuyo-server\moyuyo-admin\src\api\admin.js"
AUTH_JS = r"d:\MOYUYOWPC\moyuyo-server\moyuyo-admin\src\api\auth.js"
INVENTORY = r"D:\MOYUYOWPC\api-inventory.json"


def parse_js(path):
    """提取 (模块, 函数名, method, path) 列表"""
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    lines = content.split("\n")
    module = "通用"
    items = []
    # 模式1：return api.xxx('path') 上一行是 export function xxx
    for m in re.finditer(
        r"export function\s+(\w+)\s*\([^)]*\)\s*\{\s*\n\s*return\s+api\.(get|post|put|delete)\(\s*['\"]([^'\"]+)['\"]",
        content,
    ):
        fn_name = m.group(1)
        method = m.group(2).upper()
        path = m.group(3)
        # 定位该函数所在模块：往前找最近的 // ==== 分隔线
        pos = m.start()
        mod = module
        # 扫描之前的行找模块分隔
        for line in lines:
            pass
        prefix = content[:pos]
        mods = re.findall(r"//\s*={4,}\s*(.+?)\s*={4,}", prefix)
        if mods:
            mod = mods[-1].strip()
        items.append((mod, fn_name, method, path))
    return items


def main():
    items = parse_js(ADMIN_JS) + parse_js(AUTH_JS)
    print(f"解析出 {len(items)} 个接口")

    with open(INVENTORY, encoding="utf-8") as f:
        inv_list = json.load(f)
    inv = {(r["method"], r["path"]): r for r in inv_list}

    rows = []
    for module, fn_name, method, path in items:
        inv_key = None
        if (method, path) in inv:
            inv_key = (method, path)
        else:
            full = "/api/admin" + path
            if (method, full) in inv:
                inv_key = (method, full)
        if inv_key:
            r = inv[inv_key]
            if r["http"] == 200 and r["biz"] in (0, "0"):
                status = "正常"
            else:
                status = f"异常(http={r['http']},biz={r['biz']})"
        else:
            status = "未扫描到"
        rows.append((module, path, fn_name, status))

    # 汇总状态统计
    from collections import Counter
    print(Counter(r[3] for r in rows))

    # 写 Markdown
    out = ["| 功能模块 | 接口路径 | 功能描述(函数) | 状态 |", "|---|---|---|---|"]
    for module, path, fn, status in rows:
        module = module.replace("|", "\\|")
        fn = fn.replace("|", "\\|")
        out.append(f"| {module} | `{method_and_path(path)}` | {fn} | {status} |")
    text = "\n".join(out)
    with open(r"D:\MOYUYOWPC\api-inventory-table.md", "w", encoding="utf-8") as f:
        f.write(text)
    print("表格已写入 api-inventory-table.md，共", len(rows), "行")
    print("\n".join(out[:15]))


def method_and_path(p):
    return p


if __name__ == "__main__":
    main()
