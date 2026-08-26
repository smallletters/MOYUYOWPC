import sys
with open('D:/MOYUYOWPC/moyuyo-server/moyuyo-api/target/logs/moyuyo.log', 'r', encoding='utf-8', errors='replace') as f:
    content = f.read()
# 找包含 'community' 的行
import re
out=[]
for i, line in enumerate(content.split('\n')):
    if 'community' in line.lower() and ('ERROR' in line or 'Exception' in line):
        out.append(line[:400])
# 输出
with open('D:/MOYUYOWPC/_err3.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(out[-200:]))
print('written', len(out), 'lines')