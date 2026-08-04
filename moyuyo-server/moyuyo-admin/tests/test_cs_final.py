"""
完整测试客服管理页面 - 包括路由跳转和直接访问
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
                'method': response.request.method,
            })

        page.on('response', on_response)
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

            # 不清空记录，直接访问客服管理页面
            print("\n" + "=" * 60)
            print("步骤 2: 直接访问 /admin/cs")
            print("=" * 60)
            # 记录开始位置
            start_index = len(all_responses)
            page.goto('http://localhost:5173/admin/cs', timeout=30000)
            time.sleep(8)  # 等待所有 API 请求完成

            # 输出从 start_index 开始的响应
            print("\n" + "=" * 60)
            print("步骤 3: /admin/cs 页面的所有 API 请求")
            print("=" * 60)
            for i, resp in enumerate(all_responses[start_index:], 1):
                marker = " ❌" if resp['status'] >= 400 else ""
                if '/api/admin' in resp['url']:
                    print(f"  {i:3d}. {resp['method']:6s} {resp['status']} {resp['url']}{marker}")

            # 检查 405
            print("\n" + "=" * 60)
            print("步骤 4: 405 错误检查")
            print("=" * 60)
            errors_405 = [r for r in all_responses[start_index:] if r['status'] == 405 and '/api/admin' in r['url']]
            if errors_405:
                print(f"  ❌ 发现 {len(errors_405)} 个 405 错误:")
                for r in errors_405:
                    print(f"     {r['method']} {r['url']}")
            else:
                print("  ✓ 无 405 错误")

            # 检查所有 4xx 和 5xx
            print("\n" + "=" * 60)
            print("步骤 5: 所有 4xx/5xx 错误")
            print("=" * 60)
            errors_4xx_5xx = [r for r in all_responses[start_index:] if r['status'] >= 400 and '/api/admin' in r['url']]
            if errors_4xx_5xx:
                print(f"  发现 {len(errors_4xx_5xx)} 个 4xx/5xx 错误:")
                for r in errors_4xx_5xx:
                    print(f"     {r['status']} {r['method']} {r['url']}")
            else:
                print("  ✓ 无 4xx/5xx 错误")

            # 截图
            page.screenshot(path='cs_final.png', full_page=True)
            print("\n页面截图已保存到 cs_final.png")

        except Exception as e:
            print(f"发生错误: {e}")
            import traceback
            traceback.print_exc()
        finally:
            browser.close()

if __name__ == "__main__":
    main()
