# -*- coding: utf-8 -*-
"""Scan moyuyo-app .vue templates and inventory every icon usage."""
import re, glob, json, collections, os

ROOT = 'D:/MOYUYOWPC/moyuyo-app/src'
EMOJI_RE = re.compile(
    '[\U0001F300-\U0001FAFF\U00002600-\U000027BF\U0001F1E6-\U0001F1FF]'
    '(?:\uFE0F)?'
)
GLYPH_RE = re.compile('[\u2039\u203A\u2190\u2192\u2605\u2606\u2661\u2665\u2713\u2715\u00D7\u2726\u2B50\u2764\uFE0F]')
ICONFONT_RE = re.compile(r'iconfont icon-([a-z0-9-]+)')

emoji_count = collections.Counter()
emoji_ctx = collections.defaultdict(list)
glyph_count = collections.Counter()
glyph_ctx = collections.defaultdict(list)
iconfont_count = collections.Counter()
iconfont_ctx = collections.defaultdict(list)
uicon_names = collections.Counter()

files = glob.glob(ROOT + '/**/*.vue', recursive=True)
for f in files:
    s = open(f, encoding='utf-8').read()
    tpl = s.split('<script')[0]
    for m in re.finditer(r'<u-icon\b([^>]*)>', tpl):
        nm = re.search(r'name="([^"]+)"', m.group(1))
        sz = re.search(r'size="([^"]+)"', m.group(1))
        name = nm.group(1) if nm else '?'
        uicon_names[name] += 1
        key = 'u-icon:' + name
        if len(emoji_ctx[key]) < 3:
            ctx = tpl[max(0, m.start()-30):m.end()+30].replace('\n', ' ')
            emoji_ctx[key].append(os.path.relpath(f, ROOT) + '  >>' + ctx)
    for m in EMOJI_RE.finditer(tpl):
        e = m.group(0)
        key = 'EMOJI:' + e
        emoji_count[key] += 1
        if len(emoji_ctx[key]) < 3:
            ctx = tpl[max(0, m.start()-45):m.end()+45].replace('\n', ' ')
            emoji_ctx[key].append(os.path.relpath(f, ROOT) + '  >>' + ctx)
    for m in GLYPH_RE.finditer(tpl):
        g = m.group(0)
        key = 'GLYPH:' + g
        glyph_count[key] += 1
        if len(glyph_ctx[key]) < 3:
            ctx = tpl[max(0, m.start()-45):m.end()+45].replace('\n', ' ')
            glyph_ctx[key].append(os.path.relpath(f, ROOT) + '  >>' + ctx)
    for m in ICONFONT_RE.finditer(tpl):
        c = m.group(1)
        iconfont_count[c] += 1
        if len(iconfont_ctx[c]) < 3:
            ctx = tpl[max(0, m.start()-45):m.end()+45].replace('\n', ' ')
            iconfont_ctx[c].append(os.path.relpath(f, ROOT) + '  >>' + ctx)

lines = []
def dump(title, counter, ctx):
    lines.append('===== %s =====' % title)
    for k, v in counter.most_common():
        lines.append('%s  x%d' % (k, v))
        for c in ctx[k]:
            lines.append('    ' + c)
dump('EMOJI', emoji_count, emoji_ctx)
dump('GLYPHS', glyph_count, glyph_ctx)
dump('ICONFONT CLASSES', iconfont_count, iconfont_ctx)
lines.append('===== U-ICON =====')
for k, v in uicon_names.most_common():
    lines.append('u-icon name=%s x%d' % (k, v))

open('D:/MOYUYOWPC/_icon_migration/inventory.txt', 'w', encoding='utf-8').write('\n'.join(lines))
print('total lines:', len(lines))
print(open('D:/MOYUYOWPC/_icon_migration/inventory.txt', encoding='utf-8').read()[:6000])
