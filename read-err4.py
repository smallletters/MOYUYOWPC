import sys
with open('D:/MOYUYOWPC/moyuyo-server/moyuyo-api/target/logs/moyuyo.log', 'r', encoding='utf-8', errors='replace') as f:
    lines = f.readlines()
out=[]
for i, ln in enumerate(lines):
    if 'community' in ln.lower() and ('ERROR' in ln or 'Exception' in ln or 'NoSuch' in ln):
        for j in range(max(0, i-2), min(len(lines), i+15)):
            out.append(lines[j].rstrip())
        out.append('---')
# 写到文件
with open('D:/MOYUYOWPC/_err4.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(out[-300:]))
print('written')