# -*- coding: utf-8 -*-
"""汇总阶段3结果"""
import json
d = json.load(open(r'D:\MOYUYOWPC\moyuyo-server\moyuyo-admin\tests\phase3_output.json', encoding='utf-8'))
total = len(d)
ok = sum(1 for r in d if r['status'] == 'OK')
warn = sum(1 for r in d if r['status'] == 'WARN')
err = sum(1 for r in d if r['status'] == 'ERR')
ce = sum(len(r.get('console_errors', [])) for r in d)
pe = sum(len(r.get('page_errors', [])) for r in d)
print('页面统计: total={}, ok={}, warn={}, err={}'.format(total, ok, warn, err))
print('累计 console_errors={}, page_errors={}'.format(ce, pe))
for r in d:
    if r['status'] != 'OK':
        print('  [' + r['status'] + '] ' + r['path'] + ' ce=' + str(r.get('console_errors', [])))
