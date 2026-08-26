import sys
with open('D:/MOYUYOWPC/moyuyo-server/moyuyo-api/target/logs/moyuyo.log', 'r', encoding='utf-8', errors='replace') as f:
    lines = f.readlines()
out=[]
for i, ln in enumerate(lines):
    if '798e1ae318b441fab3a4e7dec273fd17' in ln or ('community/posts' in ln.lower() and ('409' in ln or 'Caused' in ln or 'INSERT' in ln or 'Duplicate' in ln)):
        for j in range(max(0, i-2), min(len(lines), i+20)):
            out.append(lines[j].rstrip())
        out.append('---')
with open('D:/MOYUYOWPC/_err8.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(out[-400:]))
print('written')