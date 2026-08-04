"""
重置测试 - 只访问客服管理页面并查看所有网络请求
"""
from playwright.sync_api import sync_playwright
import time

def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
        )
        context = browser.new_context()
        page = context.new_page()

        all_responses = []

        def on_response(response):
            all_responses.append({
                'url': response.url,
                'status': response.status,
                'method': response.request.method
            })

        page.on('response', on_response)
        # 监听 console
        page.on('console', lambda msg: print(f"  [console.{msg.type}] {msg.text[:300]}"))

        try:
            # 打开登录页
            print("=" * 60)
            print("步骤 1: 打开登录页")
            print("=" * 60)
            page.goto('http://localhost:5173/admin/login', timeout=30000)
            time.sleep(2)

            # 登录
            print("\n步骤 2: 登录")
            page.locator('input[type="email"]').fill('admin@moyuyo.com')
            page.locator('input[type="password"]').fill('123456')
            page.locator('button.login-btn').click()
            time.sleep(3)

            # 清空记录
            all_responses.clear()

            # 访问客服管理页面
            print("\n步骤 3: 访问客服管理页面 (/admin/cs)")
            page.goto('http://localhost:5173/admin/cs', timeout=30000)
            time.sleep(5)

            # 打印所有响应
            print("\n" + "=" * 60)
            print("所有网络请求响应")
            print("=" * 60)
            for i, resp in enumerate(all_responses, 1):
                marker = " ❌" if resp['status'] >= 400 else ""
                print(f"  {i:3d}. {resp['method']:6s} {resp['status']} {resp['url']}{marker}")

            # 检查 405
            print("\n" + "=" * 60)
            print("405 错误列表")
            print("=" * 60)
            errors_405 = [r for r in all_responses if r['status'] == 405]
            if errors_405:
                for r in errors_405:
                    print(f"  ❌ {r['method']} {r['url']} -> 405")
            else:
                print("  无 405 错误")

            # 截屏
            page.screenshot(path='cs_only.png', full_page=True)
            print("\n页面截图已保存到 cs_only.png")

        except Exception as e:
            print(f"发生错误: {e}")
            import traceback
            traceback.print_exc()
        finally:
            browser.close()

if __name__ == "__main__":
    main()
