"""
使用 Playwright 重现客服管理页面 405 错误
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

        # 收集网络请求
        api_requests = []
        api_errors = []

        def on_request(request):
            if '/api/admin' in request.url:
                api_requests.append({
                    'method': request.method,
                    'url': request.url,
                    'headers': dict(request.headers)
                })

        def on_response(response):
            if '/api/admin' in response.url:
                api_errors.append({
                    'url': response.url,
                    'status': response.status,
                    'method': response.request.method
                })

        page.on('request', on_request)
        page.on('response', on_response)

        try:
            # 打开登录页
            print("=" * 60)
            print("打开登录页")
            print("=" * 60)
            page.goto('http://localhost:5173/admin/login', timeout=30000)
            time.sleep(2)

            # 登录
            print("登录中...")
            page.locator('input[type="email"]').fill('admin@moyuyo.com')
            page.locator('input[type="password"]').fill('123456')
            page.locator('button.login-btn').click()
            time.sleep(3)

            # 访问客服管理页面
            print("\n" + "=" * 60)
            print("访问客服管理页面 (/admin/cs)")
            print("=" * 60)
            page.goto('http://localhost:5173/admin/cs', timeout=30000)
            time.sleep(5)

            # 点击"全部"标签
            print("\n点击'全部'标签")
            page.locator('button.tab-switcher-item:has-text("全部")').first.click()
            time.sleep(2)

            # 点击"待处理"标签
            print("点击'待处理'标签")
            page.locator('button.tab-switcher-item:has-text("待处理")').first.click()
            time.sleep(2)

            # 点击查询按钮
            print("点击查询按钮")
            page.locator('button.btn-primary:has-text("查询")').first.click()
            time.sleep(2)

            # 打印所有 API 请求
            print("\n" + "=" * 60)
            print("所有 /api/admin 请求")
            print("=" * 60)
            for i, req in enumerate(api_requests, 1):
                print(f"{i}. {req['method']} {req['url']}")

            # 打印所有 API 错误
            print("\n" + "=" * 60)
            print("所有 /api/admin 响应错误 (非 2xx)")
            print("=" * 60)
            for err in api_errors:
                if err['status'] >= 400:
                    print(f"  {err['method']} {err['url']} -> {err['status']}")

            # 截屏
            page.screenshot(path='cs_page.png', full_page=True)
            print("\n页面截图已保存到 cs_page.png")

        except Exception as e:
            print(f"发生错误: {e}")
            import traceback
            traceback.print_exc()
        finally:
            browser.close()

if __name__ == "__main__":
    main()
