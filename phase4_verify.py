"""
阶段4 修复验证脚本
- 验证 AdminProductController.create 的 name 为空 → 业务码 400
- 验证 AdminProductController.create 的 IllegalArgumentException → 业务码 400
- 验证 AdminProductController.delete 的商品不存在 → 业务码 404
- 验证 AdminOrderController.{detail,updateAddress,ship,cancel,delete} 的订单不存在 → 业务码 404
- 验证 AdminPriceController.{history,create} 的商品不存在 → 业务码 404
注：项目统一返回 HTTP 200，错误通过业务码 code 字段表达（Result 包装）
"""
import requests
import sys
import time

BASE = "http://localhost:8080"
LOGIN_URL = f"{BASE}/api/admin/auth/login"

# 1) 登录
login_body = {"email": "zhang@moyuyo.com", "password": "123456"}
r = requests.post(LOGIN_URL, json=login_body, timeout=10)
data = r.json()
assert data.get("code") == 0, f"登录失败: {data}"
TOKEN = data["data"]["token"]
H = {"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"}
print(f"[OK] 登录成功 token={TOKEN[:30]}...")

# 用例定义：(名称, 方法, 路径, 请求体, 期望业务码)
CASES = []

# 商品创建 - name 为空 → 期望 biz_code 400
CASES.append(("商品创建-name为空", "POST", "/api/admin/products/create",
              {"categoryId": 1, "price": 99.0}, 400))

# 商品创建 - name 为空白字符串 → 期望 biz_code 400
CASES.append(("商品创建-name空白", "POST", "/api/admin/products/create",
              {"name": "   ", "categoryId": 1, "price": 99.0}, 400))

# 商品创建 - 缺 categoryId 等必填 → IllegalArgumentException → 期望 biz_code 400
CASES.append(("商品创建-缺必填", "POST", "/api/admin/products/create",
              {"name": "测试商品"}, 400))

# 商品创建 - 完整数据 → 期望成功 biz_code 0
CASES.append(("商品创建-正常", "POST", "/api/admin/products/create",
              {"name": f"测试商品-阶段4验证-{int(time.time())}", "price": 99.0, "categoryId": 1, "stock": 10}, 0))

# 商品删除 - 不存在的ID → 期望 biz_code 404
CASES.append(("商品删除-不存在", "DELETE", "/api/admin/products/999999999", None, 404))

# 订单详情 - 不存在的ID → 期望 biz_code 404
CASES.append(("订单详情-不存在", "GET", "/api/admin/orders/999999999", None, 404))

# 订单修改地址 - 不存在的ID → 期望 biz_code 404
CASES.append(("订单地址修改-不存在", "PUT", "/api/admin/orders/999999999/address",
              {"receiverName": "测试", "receiverPhone": "13800000000", "address": "测试"}, 404))

# 订单发货 - 不存在的ID → 期望 biz_code 404
CASES.append(("订单发货-不存在", "PUT", "/api/admin/orders/999999999/ship",
              {"carrier": "顺丰", "trackingNo": "SF123"}, 404))

# 订单取消 - 不存在的ID → 期望 biz_code 404
CASES.append(("订单取消-不存在", "PUT", "/api/admin/orders/999999999/cancel",
              {"reason": "测试取消"}, 404))

# 订单删除 - 不存在的ID → 期望 biz_code 404
CASES.append(("订单删除-不存在", "DELETE", "/api/admin/orders/999999999", None, 404))

# 价格历史 - 不存在的商品ID → 期望 biz_code 404
CASES.append(("价格历史-商品不存在", "GET", "/api/admin/price/history/999999999", None, 404))

# 价格调整 - 不存在的商品 → 期望 biz_code 404
CASES.append(("价格调整-商品不存在", "POST", "/api/admin/price/create",
              {"productName": f"不存在的商品XYZ_阶段4_{int(time.time())}", "sellingPrice": 99.0, "reason": "测试"}, 404))

# 重复登录 - 同邮箱再次登录 → 期望 biz_code 0
CASES.append(("重复登录", "POST", "/api/admin/auth/login", login_body, 0))

# 错误密码登录 → 期望 biz_code 401
CASES.append(("错误密码登录", "POST", "/api/admin/auth/login",
              {"email": "zhang@moyuyo.com", "password": "wrong_pwd"}, 401))

# 商品列表-参数正常 → biz_code 0
CASES.append(("商品列表-正常", "GET", "/api/admin/products/list?page=1&size=5", None, 0))

# 订单列表-日期格式错误 → biz_code 400
CASES.append(("订单列表-日期错", "GET", "/api/admin/orders/list?startDate=bad-date", None, 400))

# 订单列表-结束日期格式错误 → biz_code 400
CASES.append(("订单列表-结束日期错", "GET", "/api/admin/orders/list?endDate=xxx", None, 400))

# 价格列表-正常 → biz_code 0
CASES.append(("价格列表-正常", "GET", "/api/admin/price/list?page=1&size=5", None, 0))


def run_case(name, method, path, body, expected_code):
    url = BASE + path
    try:
        if method == "GET":
            r = requests.get(url, headers=H, timeout=10)
        elif method == "POST":
            r = requests.post(url, headers=H, json=body, timeout=10)
        elif method == "PUT":
            r = requests.put(url, headers=H, json=body, timeout=10)
        elif method == "DELETE":
            r = requests.delete(url, headers=H, timeout=10)
        else:
            return ("ERR", -1, "未知 method")
    except Exception as e:
        return ("ERR", -1, str(e))

    actual_http = r.status_code
    body_json = {}
    try:
        body_json = r.json()
    except Exception:
        pass

    biz_code = body_json.get("code")
    biz_msg = body_json.get("message", "")

    # 项目统一 HTTP 200，业务码 code 表达错误
    if actual_http >= 500:
        return ("FAIL", actual_http, f"HTTP 5xx: biz_code={biz_code}, msg={biz_msg}")
    if biz_code == expected_code:
        return ("PASS", actual_http, f"biz_code={biz_code}, msg={biz_msg}")
    return ("FAIL", actual_http, f"biz_code={biz_code} (期望 {expected_code}), msg={biz_msg}")


results = []
print("\n" + "=" * 80)
print(f"{'用例名称':<28} {'方法':<8} {'期望':<8} {'HTTP':<6} {'业务码':<8} {'结果':<6} 详情")
print("=" * 80)
for c in CASES:
    name, method, path, body, expected = c
    verdict, http_code, detail = run_case(name, method, path, body, expected)
    expected_str = str(expected)
    biz = detail.split(",")[0].replace("biz_code=", "")
    print(f"{name:<28} {method:<8} {expected_str:<8} {http_code:<6} {biz:<8} {verdict:<6} {detail}")
    results.append((name, verdict, http_code, detail))

# 汇总
total = len(results)
passed = sum(1 for r in results if r[1] == "PASS")
failed = [r for r in results if r[1] != "PASS"]
print("=" * 80)
print(f"汇总: {passed}/{total} 通过, {len(failed)} 失败")

if failed:
    print("\n失败用例详情:")
    for n, v, h, d in failed:
        print(f"  - {n}: HTTP={h} {d}")
    sys.exit(1)
else:
    print("\n所有边界用例通过！阶段4 修复验证成功。")
