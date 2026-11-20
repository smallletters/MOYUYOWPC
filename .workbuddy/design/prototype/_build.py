"""
Phase 3 原型构建脚本
  1) 把 assets/lucide-inline.css（lucide.ttf 的 base64 内联版）注入 _template.html 的
     /*LUCIDE_INLINE*/ 占位符 → 生成 index.html（单文件、可双击、file:// 可用）
  2) 重新生成 assets/lucide-inline.css（从 moyuyo-app 真实的 lucide.css + lucide.ttf）
用法：node _verify.js / node _selfcheck.js 前先跑本脚本
"""
import base64
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
APP = r'D:/MOYUYOWPC/moyuyo-app/src'
PHOTO_SRC = r'D:/MOYUYOWPC/deliverables/moyuyo-redesign/assets/photo'


def build_lucide_inline():
    ttf = open(os.path.join(HERE, 'assets', 'lucide.ttf'), 'rb').read()
    b64 = base64.b64encode(ttf).decode()
    css = open(os.path.join(APP, 'styles/lucide.css'), encoding='utf-8').read()
    # 去掉头部块注释：其中含 "<style>" 字样，会让 HTML 解析器提前进入 RAWTEXT 状态
    css = re.sub(r'^\s*/\*\*.*?\*/\s*', '', css, count=1, flags=re.S)
    assert '<style>' not in css
    css = re.sub(
        r"@font-face\s*\{[^}]*\}",
        "@font-face {\n  font-family: 'LucideIcons';\n"
        "  src: url(\"data:font/ttf;base64,%s\") format('truetype');\n"
        "  font-weight: normal;\n  font-style: normal;\n  font-display: swap;\n}" % b64,
        css, count=1)
    out = os.path.join(HERE, 'assets', 'lucide-inline.css')
    open(out, 'w', encoding='utf-8', newline='\n').write(css)
    print('  lucide-inline.css  %d bytes' % len(css))


def copy_assets():
    dst = os.path.join(HERE, 'assets', 'photo')
    os.makedirs(dst, exist_ok=True)
    n = 0
    for f in os.listdir(PHOTO_SRC):
        open(os.path.join(dst, f), 'wb').write(open(os.path.join(PHOTO_SRC, f), 'rb').read())
        n += 1
    print('  photo assets        %d files' % n)


def build_index():
    tpl = open(os.path.join(HERE, '_template.html'), encoding='utf-8').read()
    luc = open(os.path.join(HERE, 'assets', 'lucide-inline.css'), encoding='utf-8').read()
    assert '/*LUCIDE_INLINE*/' in tpl
    # 只替换第一个占位符（Spec 面板文案里也会提到它，不能被展开）
    html = tpl.replace('/*LUCIDE_INLINE*/', luc, 1)
    open(os.path.join(HERE, 'index.html'), 'w', encoding='utf-8', newline='\n').write(html)
    print('  index.html          %d bytes' % len(html.encode('utf-8')))


if __name__ == '__main__':
    print('building prototype...')
    build_lucide_inline()
    copy_assets()
    build_index()
    print('done.')
