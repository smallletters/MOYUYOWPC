import requests
BASE = "http://localhost:8080/api/v1"
# 测试 parentCategoryId=1（Bath & Grooming）应查到全部其下二级分类（101,102,103,104）的商品
r = requests.get(f"{BASE}/products", params={"parentCategoryId": 1, "size": 10})
print("parentCategoryId=1:", r.status_code, "records:", len(r.json().get("data", {}).get("records", [])))

r = requests.get(f"{BASE}/products", params={"categoryId": 101, "size": 10})
print("categoryId=101:", r.status_code, "records:", len(r.json().get("data", {}).get("records", [])))

r = requests.get(f"{BASE}/products", params={"parentCategoryId": 3, "size": 10})
print("parentCategoryId=3 (no children):", r.status_code, "records:", len(r.json().get("data", {}).get("records", [])))
print("DONE")