import json
r = json.load(open(r'D:\MOYUYOWPC\moyuyo-server\moyuyo-admin\tests\button_audit_v2.json', encoding='utf-8'))
print('Pages:', r['total_pages'])
print('Total buttons:', r['total_buttons'])
print('Tested:', r['tested_buttons'])
print('Skipped:', r['skipped_buttons'])
print('Failed:', r['failed_interactions'])
print()
print('=== Pages with failures ===')
for p in r['page_results']:
    if p['failed'] > 0:
        print('  [' + p['name'] + '] fail=' + str(p['failed']))
print()
print('=== Top failures ===')
for f in r['all_failures'][:30]:
    print('  [' + f['page'] + '] ' + f['button'] + ': ' + f['msg'])
print()
print('=== Page summary (first 20) ===')
for p in r['page_results'][:20]:
    print('  ' + p['name'] + ': btns=' + str(p['total_buttons']) + ' tested=' + str(p['tested']) + ' skip=' + str(p['skipped']) + ' fail=' + str(p['failed']))
