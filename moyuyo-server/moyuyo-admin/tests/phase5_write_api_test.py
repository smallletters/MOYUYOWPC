"""阶段4 写接口回归：POST/PUT 抽样验证"""
import json
import urllib.request
import time

BASE = 'http://localhost:8080/api/admin'

def req(method, path, token, body=None):
    r = urllib.request.Request(f'{BASE}{path}', method=method,
                               headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'},
                               data=json.dumps(body).encode() if body else None)
    try:
        with urllib.request.urlopen(r) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, None

def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    r = urllib.request.Request(f'{BASE}/auth/login', data=data, method='POST',
                               headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(r) as resp:
        token = json.loads(resp.read())['data']['token']

    ts = int(time.time() * 1000)
    results = []

    # 1. CMS 创建
    s, b = req('POST', '/cms/create', token, {'title': f'测试banner{ts}', 'type': 'BANNER', 'status': '投放中'})
    results.append(('CMS创建', s, b.get('code') if b else None, b.get('message') if b else None))

    # 2. 营销活动创建
    s, b = req('POST', '/marketing/campaigns', token, {'name': f'测试活动{ts}', 'type': 'FLASH_SALE'})
    results.append(('活动创建', s, b.get('code') if b else None, b.get('message') if b else None))

    # 3. 推送创建
    s, b = req('POST', '/push/create', token, {'title': f'测试推送{ts}', 'channel': 'all', 'content': f'推送内容{ts}'})
    results.append(('推送创建', s, b.get('code') if b else None, b.get('message') if b else None))

    # 4. 黑名单创建
    s, b = req('POST', '/blacklist/create', token, {'value': f'black{ts}', 'type': 'EMAIL'})
    results.append(('黑名单创建', s, b.get('code') if b else None, b.get('message') if b else None))

    # 5. 敏感词创建
    s, b = req('POST', '/sensitive/create', token, {'word': f'w{ts}', 'replacement': '***', 'matchMode': '精确匹配', 'category': '广告'})
    results.append(('敏感词创建', s, b.get('code') if b else None, b.get('message') if b else None))

    # 6. 系统配置保存（List 格式）
    s, b = req('PUT', '/system/config', token, [{'key': 'siteName', 'value': 'MOYUYO', 'type': 'text', 'label': '站点名称'}])
    results.append(('系统配置保存', s, b.get('code') if b else None, b.get('message') if b else None))

    # 7. 知识库创建（content 必填 + 合法分类）
    s, b = req('POST', '/knowledge-base/create', token, {'title': f'测试文章{ts}', 'category': 'FAQ', 'content': f'文章内容{ts}', 'status': 'DRAFT'})
    results.append(('知识库创建', s, b.get('code') if b else None, b.get('message') if b else None))

    # 8. 订单标签创建
    s, b = req('POST', '/order-tags/create', token, {'name': f'标签{ts}', 'color': '#007aff'})
    results.append(('订单标签创建', s, b.get('code') if b else None, b.get('message') if b else None))

    print('=== 写接口回归 ===')
    ok = 0
    for name, s, code, msg in results:
        passed = s == 200 and code == 0
        if passed:
            ok += 1
        print(f'[{"PASS" if passed else "FAIL"}] {name}: http={s} code={code} msg={msg}')
    print(f'汇总: {ok}/{len(results)} 通过')

if __name__ == '__main__':
    main()
