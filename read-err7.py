import sys
with open('D:/MOYUYOWPC/moyuyo-server/moyuyo-api/target/logs/moyuyo.log', 'r', encoding='utf-8', errors='replace') as f:
    lines = f.readlines()
out=[]
for i, ln in enumerate(lines):
    if 'e5522e3cc5c747b68d7d71f6ce61aa54' in ln or 'community/posts' in ln.lower() and ('409' in ln or 'INSERT' in ln or 'Caused' in ln or 'Duplicate' in ln or 'Constraint' in ln):
        for j in range(max(0, i-2), min(len(lines), i+20)):
            out.append(lines[j].rstrip())
        out.append('---')
with open('D:/MOYUYOWPC/_err7.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(out[-400:]))
print('written')