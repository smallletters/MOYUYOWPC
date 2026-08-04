"""
统计 moyuyo-server 后端 Admin 控制器接口分布
"""
import re
from pathlib import Path

CONTROLLER_DIR = Path(r"D:\MOYUYOWPC\moyuyo-server\moyuyo-api\src\main\java\com\moyuyo\api\controller\admin")


def count_methods(text):
    """统计每个 HTTP 方法的数量"""
    counts = {"GET": 0, "POST": 0, "PUT": 0, "DELETE": 0}
    for m in re.finditer(r'@(Get|Post|Put|Delete)Mapping\b', text):
        counts[m.group(1).upper()] += 1
    return counts


def main():
    total = {"GET": 0, "POST": 0, "PUT": 0, "DELETE": 0}
    rows = []
    for f in sorted(CONTROLLER_DIR.glob("*.java")):
        text = f.read_text(encoding="utf-8")
        counts = count_methods(text)
        if sum(counts.values()) == 0:
            continue
        rows.append((f.stem, counts))
        for k, v in counts.items():
            total[k] += v

    print(f"{'控制器':<40} {'GET':>5} {'POST':>5} {'PUT':>5} {'DEL':>5} {'合计':>5}")
    print("-" * 70)
    for name, c in rows:
        s = sum(c.values())
        print(f"{name:<40} {c['GET']:>5} {c['POST']:>5} {c['PUT']:>5} {c['DELETE']:>5} {s:>5}")
    print("-" * 70)
    s = sum(total.values())
    print(f"{'合计':<40} {total['GET']:>5} {total['POST']:>5} {total['PUT']:>5} {total['DELETE']:>5} {s:>5}")


if __name__ == "__main__":
    main()
