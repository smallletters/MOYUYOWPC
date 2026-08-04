# -*- coding: utf-8 -*-
"""验证提交类按钮完整流程：打开新增弹窗->填写->保存->成功反馈->弹窗关闭
目标页面：优惠券(CouponManage)、短信(SmsManage)、CMS(CmsManage)
"""
import time
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173/admin"
PAGES = [
    {"route": "coupon-manage", "open_btn": "新建", "save_btn": "保存", "fields": ["名称", "面值"], "name_ph": "input[placeholder*='名称'], input[placeholder*='优惠券']"},
    {"route": "sms", "open_btn": "发送", "save_btn": "发送", "fields": [], "name_ph": ""},
    {"route": "cms", "open_btn": "新建", "save_btn": "保存", "fields": [], "name_ph": "input[placeholder*='标题'], input[placeholder*='Banner']"},
]


def main():
    results = []
    with sync_playwright() as p:
        b = p.chromium.launch()
        ctx = b.new_context(viewport={"width": 1600, "height": 900})
        pg = ctx.new_page()
        pg.goto(f"{BASE}/login", wait_until="networkidle")
        pg.wait_for_timeout(500)
        try:
            pg.fill("input[placeholder*='邮箱'], input[placeholder*='账号'], input[type='text']", "admin@moyuyo.com", timeout=4000)
        except Exception:
            pass
        pg.fill("input[type='password']", "123456", timeout=4000)
        pg.click("button[type='submit'], button:has-text('登录')", timeout=4000)
        pg.wait_for_timeout(1500)

        for spec in PAGES:
            route = spec["route"]
            pg.goto(f"{BASE}/{route}", wait_until="networkidle")
            pg.wait_for_timeout(1200)
            rec = {"page": route, "steps": []}
            # 打开弹窗
            open_btn = pg.locator(f"button:has-text('{spec['open_btn']}')").first
            if open_btn.count() == 0:
                rec["steps"].append("未找到打开按钮")
                results.append(rec)
                print(f"[FAIL] {route}: 未找到打开按钮")
                continue
            open_btn.click(timeout=3000)
            pg.wait_for_timeout(600)
            dialog = pg.locator(".el-dialog:visible, .el-overlay:visible")
            rec["steps"].append(f"打开弹窗: {dialog.count() > 0}")
            if dialog.count() == 0:
                results.append(rec)
                print(f"[WARN] {route}: 弹窗未打开")
                continue
            # 填写必填字段
            filled = False
            if spec["name_ph"]:
                inp = pg.locator(spec["name_ph"]).first
                if inp.count() > 0:
                    inp.fill(f"E2E测试_{int(time.time())}")
                    filled = True
            # 优惠券面值
            if route == "coupon-manage":
                try:
                    pg.locator(".el-dialog:visible input[placeholder*='面值']").first.fill("9")
                except Exception:
                    pass
            rec["steps"].append(f"填写表单: {filled}")
            # 点击保存/发送
            save_btn = pg.locator(f".el-dialog:visible button:has-text('{spec['save_btn']}')").first
            if save_btn.count() == 0:
                save_btn = pg.locator(f"button:has-text('{spec['save_btn']}')").first
            save_btn.click(timeout=3000)
            pg.wait_for_timeout(1200)
            # 检查成功提示
            toast = pg.locator(".el-message--success, .el-message--success .el-message__content")
            toast_text = ""
            if toast.count() > 0:
                toast_text = toast.first.inner_text().strip()
            rec["steps"].append(f"成功提示: '{toast_text}'")
            # 检查弹窗是否关闭
            dlg_after = pg.locator(".el-dialog:visible").count()
            rec["steps"].append(f"保存后弹窗数: {dlg_after}")
            ok = bool(toast_text) and dlg_after == 0
            rec["result"] = "OK" if ok else "检查"
            results.append(rec)
            print(f"[{rec['result']}] {route}: 提示='{toast_text}' 弹窗={dlg_after}")
        b.close()

    with open(r"D:\MOYUYOWPC\phase3_submit_result.json", "w", encoding="utf-8") as f:
        import json
        json.dump(results, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
