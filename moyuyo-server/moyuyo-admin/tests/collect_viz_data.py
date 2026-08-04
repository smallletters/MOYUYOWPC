"""
收集修复报告与可视化所需数据
- 接口数量分布
- 修复记录统计
- 测试通过率
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime
from pathlib import Path

BASE = "http://localhost:8080"
ROOT = Path(r"D:\MOYUYOWPC\moyuyo-server")
CONTROLLER_DIR = ROOT / "moyuyo-api" / "src" / "main" / "java" / "com" / "moyuyo" / "com" / "moyuyo" / "api" / "controller" / "admin"
# 修正路径
CONTROLLER_DIR = ROOT / "moyuyo-api" / "src" / "main" / "java" / "com" / "moyuyo" / "api" / "controller" / "admin"

OUT_DIR = ROOT / "moyuyo-admin" / "tests"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def count_methods(text):
    counts = {"GET": 0, "POST": 0, "PUT": 0, "DELETE": 0}
    for m in re.finditer(r'@(Get|Post|Put|Delete)Mapping\b', text):
        counts[m.group(1).upper()] += 1
    return counts


def stat_endpoints():
    rows = []
    total = {"GET": 0, "POST": 0, "PUT": 0, "DELETE": 0}
    for f in sorted(CONTROLLER_DIR.glob("*.java")):
        text = f.read_text(encoding="utf-8")
        counts = count_methods(text)
        if sum(counts.values()) == 0:
            continue
        rows.append({"controller": f.stem, **counts, "total": sum(counts.values())})
        for k, v in counts.items():
            total[k] += v
    total_row = {"controller": "合计", **total, "total": sum(total.values())}
    rows.append(total_row)
    return rows


def stat_migrations():
    """统计 Flyway 迁移脚本数量"""
    mig_dir = ROOT / "moyuyo-api" / "src" / "main" / "resources" / "db" / "migration"
    migrations = sorted(mig_dir.glob("V*.sql"))
    return [
        {"name": m.name, "size": m.stat().st_size, "mtime": m.stat().st_mtime}
        for m in migrations
    ]


def stat_admin_views():
    """统计前端管理页面数"""
    views_dir = ROOT / "moyuyo-admin" / "src" / "views"
    return sorted([f.stem for f in views_dir.glob("*.vue")])


def login():
    data = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE}/api/admin/auth/login", data=data,
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as r:
        body = json.loads(r.read().decode("utf-8"))
        return (body.get("data") or {}).get("token")


def call(token, method, path, body=None):
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    data = json.dumps(body).encode("utf-8") if body else None
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, r.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return 0, str(e)


def live_healthcheck():
    """实时健康检查"""
    token = login()
    if not token:
        return {"passed": 0, "failed": 0, "total": 0, "endpoints": [], "failures": []}

    # 提取控制器中所有接口路径
    endpoints = []
    for java_file in CONTROLLER_DIR.glob("*.java"):
        text = java_file.read_text(encoding="utf-8")
        cls_match = re.search(r'@RequestMapping\(\s*"([^"]+)"\s*\)', text)
        if not cls_match:
            continue
        prefix = cls_match.group(1)
        for m in re.finditer(r'@(Get|Post|Put|Delete)Mapping\((?:value\s*=\s*)?"([^"]*)"\)', text):
            method_type = m.group(1).upper()
            sub = re.sub(r"\{[^}]+\}", "1", m.group(2))
            full = prefix.rstrip("/") + "/" + sub.lstrip("/") if sub else prefix
            endpoints.append((method_type, full, java_file.stem))

    passed = 0
    failed = 0
    rows = []
    failures = []
    for method, path, cls in endpoints:
        body = None
        if method in ("POST", "PUT"):
            body = {"id": 1, "name": "test"}
        code, resp = call(token, method, path, body)
        # 5xx 视为失败, 4xx 视为正常(业务校验/资源不存在/权限)
        ok = code < 500
        if ok:
            passed += 1
        else:
            failed += 1
            failures.append({"method": method, "path": path, "controller": cls, "status": code, "response": resp[:200]})
        rows.append({"method": method, "path": path, "controller": cls, "status": code, "ok": ok})
    return {"passed": passed, "failed": failed, "total": len(endpoints), "endpoints": rows, "failures": failures}


def main():
    print("[1/4] 统计控制器接口分布...")
    endpoints_stats = stat_endpoints()

    print("[2/4] 统计迁移脚本...")
    migrations = stat_migrations()

    print("[3/4] 统计前端管理页面...")
    views = stat_admin_views()

    print("[4/4] 实时健康检查...")
    health = live_healthcheck()

    summary = {
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "endpoints_stats": endpoints_stats,
        "migrations": {"count": len(migrations), "files": [m["name"] for m in migrations]},
        "admin_views": {"count": len(views), "names": views},
        "health": {
            "passed": health["passed"],
            "failed": health["failed"],
            "total": health["total"],
            "failures": health.get("failures", []),
        },
    }

    out_file = OUT_DIR / "viz_data.json"
    out_file.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"已写入: {out_file}")
    print(f"\n汇总:")
    print(f"  - 后端控制器: {len(endpoints_stats) - 1} 个, 接口 {endpoints_stats[-1]['total']} 个")
    print(f"  - 迁移脚本: {len(migrations)} 个")
    print(f"  - 前端管理页面: {len(views)} 个")
    print(f"  - 健康检查: {health['passed']}/{health['total']} 通过 ({(health['passed']/max(health['total'],1)*100):.1f}%)")


if __name__ == "__main__":
    main()
