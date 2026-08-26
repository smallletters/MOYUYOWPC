import sys
with open('D:/MOYUYOWPC/backend.log', 'r', encoding='utf-8', errors='replace') as f:
    lines = f.readlines()
# 输出文件写到本地，绕开 PowerShell 截断
with open('D:/MOYUYOWPC/_community_err.txt', 'w', encoding='utf-8') as f:
    for i, ln in enumerate(lines):
        if 'community' in ln.lower() and ('ERROR' in ln or 'Exception' in ln):
            for j in range(max(0, i-2), min(len(lines), i+12)):
                f.write(lines[j])
            f.write('---\n')
print('done')