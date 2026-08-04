import json
data = json.load(open(r"D:\MOYUYOWPC\phase3_results.json", encoding="utf-8"))
for r in data["results"]:
    if r["status"] != "OK":
        print(f"{r['name']:25} | {','.join(r['issues']):30}")
        for e in r.get("js_errors", []):
            print(f"    JS: {e[:200]}")
