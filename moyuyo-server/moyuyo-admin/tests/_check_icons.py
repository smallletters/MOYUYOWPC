"""校验所有 .vue 文件从 @element-plus/icons-vue 导入的图标名是否真实存在"""
import io
import re
import os

# 直接从 node_modules 读取图标导出（避免编码问题）
try:
    import subprocess
    out = subprocess.run(
        ['node', '-e', "const i=require('@element-plus/icons-vue');process.stdout.write(Object.keys(i).join('\\n'))"],
        capture_output=True, text=True, cwd=os.getcwd()
    )
    ICONS = set(out.stdout.split())
except Exception as e:
    print('node 调用失败:', e)
    ICONS = set()

VIEWS = 'src/views'
issues = []
for fn in sorted(os.listdir(VIEWS)):
    if not fn.endswith('.vue'):
        continue
    path = os.path.join(VIEWS, fn)
    src = io.open(path, encoding='utf-8').read()
    for m in re.finditer(r'import\s*\{([^}]+)\}\s*from\s*[\'"]@element-plus/icons-vue[\'"]', src):
        for name in m.group(1).split(','):
            name = name.strip()
            if not name:
                continue
            if name not in ICONS:
                issues.append((fn, name))

if issues:
    print('发现不存在的图标导入：')
    for fn, name in issues:
        print(f'  {fn}: {name}')
else:
    print('全部图标导入校验通过')
