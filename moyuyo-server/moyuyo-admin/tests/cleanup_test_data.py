# -*- coding: utf-8 -*-
"""清理自动化测试产生的数据（e2e_/riskfix_/shipfix_/fs_/api_test_/valid_/textprio_/nomethod_ 前缀）"""
import json, urllib.request, urllib.error

BASE = 'http://localhost:8080/api/admin'
PREFIXES = ('e2e_', 'riskfix_', 'shipfix_', 'fs_', 'api_test_', 'valid_', 'textprio_', 'nomethod_')

# (模块名, 列表接口, 删除接口模板, 名称字段)
MODULES = [
    ('敏感词', '/sensitive/list', '/sensitive/{id}', 'word'),
    ('订单标签', '/order-tags/list', '/order-tags/{id}', 'name'),
    ('风控告警', '/risk-alert/configs', '/risk-alert/configs/{id}', 'name'),
    ('发货策略', '/logistics/shipping-strategies', '/logistics/shipping-strategies/{id}', 'strategyName'),
    ('秒杀', '/flash-sales/list', '/flash-sales/{id}', 'name'),
    ('知识库', '/knowledge-base/list', '/knowledge-base/{id}', 'title'),
    ('黑名单', '/blacklist/list', '/blacklist/{id}', 'value'),
    ('优惠券', '/coupons/list', '/coupons/{id}', 'name'),
    ('关税', '/tariff/configs', '/tariff/configs/{id}', 'name'),
]

def req(method, url, token, body=None):
    r = urllib.request.Request(url, method=method, headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'})
    if body is not None:
        r.data = json.dumps(body).encode()
    try:
        with urllib.request.urlopen(r, timeout=8) as resp:
            return resp.status, json.loads(resp.read() or b'{}')
    except urllib.error.HTTPError as e:
        return e.code, None

def main():
    login_data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    with urllib.request.urlopen(urllib.request.Request(f'{BASE}/auth/login', data=login_data, method='POST', headers={'Content-Type': 'application/json'}), timeout=8) as r:
        token = json.loads(r.read())['data']['token']
    total_deleted = 0
    for name, list_url, del_url, field in MODULES:
        try:
            st, resp = req('GET', f'{BASE}{list_url}', token)
            if st != 200 or not resp or 'data' not in resp:
                # 尝试直接列表
                records = resp.get('data', []) if isinstance(resp, dict) else []
                if isinstance(records, dict):
                    records = records.get('records', [])
                if not isinstance(records, list):
                    print(f'{name}: 跳过（结构未知 {str(resp)[:60]}）')
                    continue
            else:
                records = resp['data']
                if isinstance(records, dict):
                    records = records.get('records', [])
            targets = []
            for rec in records:
                if not isinstance(rec, dict):
                    continue
                v = str(rec.get(field, '') or '')
                # 兼容别名
                if not v:
                    for alt in ('word', 'name', 'strategyName', 'title', 'value', 'alertName'):
                        if rec.get(alt):
                            v = str(rec[alt])
                            break
                if any(v.startswith(p) for p in PREFIXES):
                    targets.append(rec.get('id'))
            for tid in targets:
                st2, _ = req('DELETE', f'{BASE}{del_url.format(id=tid)}', token)
                if st2 in (200, 204):
                    total_deleted += 1
            print(f'{name}: 匹配{len(targets)}条，已删除 {len(targets)} 条')
        except Exception as e:
            print(f'{name}: 异常 {str(e)[:80]}')
    print(f'\n总计删除: {total_deleted} 条测试数据')

if __name__ == '__main__':
    main()
