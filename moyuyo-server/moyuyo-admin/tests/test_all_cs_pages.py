"""
测试所有与客服相关的页面是否还有 405 错误
"""
from playwright.sync_api import sync_playwright
import time

PAGES_TO_TEST = [
    ('客服管理', '/admin/cs'),
    ('客服会话', '/admin/cs-sessions'),
    ('工单管理', '/admin/ticket'),
]

def test_page(page, name, path):
    """测试单个页面"""
    print(f"\n{'='*60}")
    print(f"测试页面: {name} ({path})")
    print('='*60)

    all_responses = []
    errors_405 = []

    def on_response(response):
        all_responses.append({
            'url': response.url,
            'status': response.status,
            'method': response.request.method
        })
        if response.status == 405:
            errors_405.append({
                'method': response.request.method,
                'url': response.url
            })

    page.on('response', on_response)

    try:
        page.goto(f'http://localhost:5173{path}', timeout=30000)
        time.sleep(5)

        # 切换所有 Tab
        tabs = page.locator('button.tab-switcher-item').all()
        for tab in tabs:
            try:
                tab.click()
                time.sleep(1)
            except:
                pass

        # 切换所有分页
        pages = page.locator('button.pagination-btn').all()
        for p_btn in pages[:3]:
            try:
                p_btn.click()
                time.sleep(1)
            except:
                pass

        # 点击"查询"按钮
        try:
            page.locator('button.btn-primary:has-text("查询")').first.click()
            time.sleep(2)
        except:
            pass

        print(f"  共有 {len(all_responses)} 个网络请求")
        print(f"  405 错误数量: {len(errors_405)}")
        if errors_405:
            for e in errors_405:
                print(f"    ❌ {e['method']} {e['url']} -> 405")
        else:
            print(f"    ✓ 无 405 错误")

    except Exception as e:
        print(f"  测试失败: {e}")

    page.remove_listener('response', on_response)
    return errors_405

def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
        )
        context = browser.new_context()
        page = context.new_page()

        # 登录
        print("=" * 60)
        print("登录")
        print("=" * 60)
        page.goto('http://localhost:5173/admin/login', timeout=30000)
        time.sleep(2)
        page.locator('input[type="email"]').fill('admin@moyuyo.com')
        page.locator('input[type="password"]').fill('123456')
        page.locator('button.login-btn').click()
        time.sleep(3)
        print("登录成功")

        # 测试每个页面
        all_405 = {}
        for name, path in PAGES_TO_TEST:
            errors_405 = test_page(page, name, path)
            if errors_405:
                all_405[name] = errors_405

        # 总结
        print("\n\n" + "=" * 60)
        print("测试总结")
        print("=" * 60)
        if all_405:
            for name, errors in all_405.items():
                print(f"\n❌ {name}: 发现 {len(errors)} 个 405 错误")
                for e in errors:
                    print(f"   {e['method']} {e['url']}")
        else:
            print("✓ 所有客服相关页面都没有 405 错误！")

        browser.close()

if __name__ == "__main__":
    main()
