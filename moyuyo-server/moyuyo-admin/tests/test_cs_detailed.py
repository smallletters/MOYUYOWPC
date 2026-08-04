"""
详细测试客服管理页面 - 进入页面立即的所有 API 请求
"""
from playwright.sync_api import sync_playwright
import time
import json

def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
        )
        context = browser.new_context()
        page = context.new_page()

        all_responses = []
        console_messages = []

        def on_response(response):
            all_responses.append({
                'url': response.url,
                'status': response.status,
                'method': response.request.method,
            })

        def on_console(msg):
            console_messages.append({
                'type': msg.type,
                'text': msg.text
            })

        page.on('response', on_response)
        page.on('console', on_console)
        page.on('pageerror', lambda err: print(f"  [pageerror] {err.message}"))

        try:
            # 登录
            print("=" * 60)
            print("步骤 1: 登录")
            print("=" * 60)
            page.goto('http://localhost:5173/admin/login', timeout=30000)
            time.sleep(2)
            page.locator('input[type="email"]').fill('admin@moyuyo.com')
            page.locator('input[type="password"]').fill('123456')
            page.locator('button.login-btn').click()
            time.sleep(3)
            print("登录成功")

            # 清空记录
            all_responses.clear()
            console_messages.clear()

            # 直接访问客服管理页面
            print("\n" + "=" * 60)
            print("步骤 2: 直接访问 /admin/cs")
            print("=" * 60)
            page.goto('http://localhost:5173/admin/cs', timeout=30000)
            time.sleep(8)  # 等待所有 API 请求完成

            # 输出所有响应
            print("\n" + "=" * 60)
            print("步骤 3: 所有 /api/admin 请求")
            print("=" * 60)
            for i, resp in enumerate(all_responses, 1):
                marker = " ❌" if resp['status'] >= 400 else ""
                if '/api/admin' in resp['url']:
                    print(f"  {i:3d}. {resp['method']:6s} {resp['status']} {resp['url']}{marker}")

            # 输出所有 console 消息
            print("\n" + "=" * 60)
            print("步骤 4: console 消息")
            print("=" * 60)
            for msg in console_messages:
                if msg['type'] in ('error', 'warning'):
                    print(f"  [{msg['type']}] {msg['text'][:300]}")

            # 检查 405
            print("\n" + "=" * 60)
            print("步骤 5: 405 错误检查")
            print("=" * 60)
            errors_405 = [r for r in all_responses if r['status'] == 405 and '/api/admin' in r['url']]
            if errors_405:
                print(f"  ❌ 发现 {len(errors_405)} 个 405 错误:")
                for r in errors_405:
                    print(f"     {r['method']} {r['url']}")
            else:
                print("  ✓ 无 405 错误")

            # 检查所有 4xx 和 5xx
            print("\n" + "=" * 60)
            print("步骤 6: 所有 4xx/5xx 错误")
            print("=" * 60)
            errors_4xx_5xx = [r for r in all_responses if r['status'] >= 400 and '/api/admin' in r['url']]
            if errors_4xx_5xx:
                print(f"  发现 {len(errors_4xx_5xx)} 个 4xx/5xx 错误:")
                for r in errors_4xx_5xx:
                    print(f"     {r['status']} {r['method']} {r['url']}")
            else:
                print("  ✓ 无 4xx/5xx 错误")

            # 截图
            page.screenshot(path='cs_page_detailed.png', full_page=True)
            print("\n页面截图已保存到 cs_page_detailed.png")

        except Exception as e:
            print(f"发生错误: {e}")
            import traceback
            traceback.print_exc()
        finally:
            browser.close()

if __name__ == "__main__":
    main()
