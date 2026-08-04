"""
使用 Playwright 重现客服管理页面 405 错误 - 全面测试
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
        # 也监听 console
        page.on('console', lambda msg: print(f"  [console.{msg.type}] {msg.text[:200]}") if msg.type in ('error', 'warning') else None)
        # 监听 dialog
        page.on('dialog', lambda dialog: (print(f"  [dialog] {dialog.message}"), dialog.accept()))

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

            # 切换到"全部"标签
            print("\n切换到'全部'标签")
            try:
                page.locator('button.tab-switcher-item:has-text("全部")').first.click()
                time.sleep(2)
            except Exception as e:
                print(f"  点击'全部'标签失败: {e}")

            # 切换到"待处理"标签
            print("切换到'待处理'标签")
            try:
                page.locator('button.tab-switcher-item:has-text("待处理")').first.click()
                time.sleep(2)
            except Exception as e:
                print(f"  点击'待处理'标签失败: {e}")

            # 切换到"进行中"标签
            print("切换到'进行中'标签")
            try:
                page.locator('button.tab-switcher-item:has-text("进行中")').first.click()
                time.sleep(2)
            except Exception as e:
                print(f"  点击'进行中'标签失败: {e}")

            # 切换到"已关闭"标签
            print("切换到'已关闭'标签")
            try:
                page.locator('button.tab-switcher-item:has-text("已关闭")').first.click()
                time.sleep(2)
            except Exception as e:
                print(f"  点击'已关闭'标签失败: {e}")

            # 切换到"超时"标签
            print("切换到'超时'标签")
            try:
                page.locator('button.tab-switcher-item:has-text("超时")').first.click()
                time.sleep(2)
            except Exception as e:
                print(f"  点击'超时'标签失败: {e}")

            # 切换回"全部"标签
            print("切换回'全部'标签")
            try:
                page.locator('button.tab-switcher-item:has-text("全部")').first.click()
                time.sleep(2)
            except Exception as e:
                print(f"  点击'全部'标签失败: {e}")

            # 切换工单类型
            print("\n切换工单类型为'退款'")
            try:
                page.locator('select').first.select_option('refund')
                time.sleep(1)
            except Exception as e:
                print(f"  选择工单类型失败: {e}")

            # 点击查询按钮
            print("点击查询按钮")
            try:
                page.locator('button.btn-primary:has-text("查询")').first.click()
                time.sleep(2)
            except Exception as e:
                print(f"  点击查询按钮失败: {e}")

            # 点击重置按钮
            print("点击重置按钮")
            try:
                page.locator('button.btn-outline:has-text("重置")').first.click()
                time.sleep(2)
            except Exception as e:
                print(f"  点击重置按钮失败: {e}")

            # 点击"处理"按钮
            print("\n点击'处理'按钮")
            try:
                page.locator('span.table-link:has-text("处理")').first.click()
                time.sleep(2)
                # 截图查看是否跳转
                page.screenshot(path='after_handle.png', full_page=True)
                # 返回
                page.go_back()
                time.sleep(2)
            except Exception as e:
                print(f"  点击'处理'按钮失败: {e}")

            # 点击"转交"按钮
            print("\n点击'转交'按钮")
            try:
                page.locator('span.table-link:has-text("转交")').first.click()
                time.sleep(2)
                # 截图查看 prompt
                page.screenshot(path='after_transfer.png', full_page=True)
                # 取消 prompt
                try:
                    page.keyboard.press('Escape')
                    time.sleep(1)
                except:
                    pass
            except Exception as e:
                print(f"  点击'转交'按钮失败: {e}")

            # 打印所有 API 请求
            print("\n" + "=" * 60)
            print("所有 /api/admin 请求 (按时间顺序)")
            print("=" * 60)
            for i, req in enumerate(api_requests, 1):
                print(f"{i}. {req['method']} {req['url']}")

            # 打印所有 API 错误
            print("\n" + "=" * 60)
            print("所有 /api/admin 响应错误 (非 2xx)")
            print("=" * 60)
            has_error = False
            for err in api_errors:
                if err['status'] >= 400:
                    has_error = True
                    print(f"  {err['method']} {err['url']} -> {err['status']}")
            if not has_error:
                print("  无错误！")

        except Exception as e:
            print(f"发生错误: {e}")
            import traceback
            traceback.print_exc()
        finally:
            browser.close()

if __name__ == "__main__":
    main()
