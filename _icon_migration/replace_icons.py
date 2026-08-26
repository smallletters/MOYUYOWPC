# -*- coding: utf-8 -*-
"""Batch replace emoji / unicode glyphs / iconfont classes with Lucide icon spans.
Pass 1: iconfont classes + static template emoji/glyphs. Dynamic {{ }} handled by replace_dynamic.py.
"""
import os, re, sys, glob, json

ROOT = r'D:/MOYUYOWPC/moyuyo-app/src/pages'

# ---------- iconfont class mapping ----------
ICONFONT_MAP = {
    'arrow-left': 'arrow-left', 'clock': 'clock', 'share': 'share-2',
    'heart': 'heart', 'info': 'info', 'chevron-down': 'chevron-down',
    'bag': 'shopping-bag', 'users': 'users', 'battery': 'battery-full',
    'shield': 'shield', 'plus': 'plus', 'rotate': 'rotate-ccw',
    'chevron-right': 'chevron-right', 'instagram': 'camera',
    'facebook': 'users', 'twitter': 'at-sign', 'link': 'link',
    'arrow-up': 'arrow-up', 'coins': 'coins', 'pencil': 'pencil',
    'repeat': 'repeat', 'external-link': 'external-link', 'award': 'award',
    'flame': 'flame', 'droplet': 'droplet', 'brain': 'brain', 'zap': 'zap',
    'package': 'package', 'crown': 'crown', 'cpu': 'cpu', 'chart': 'bar-chart',
    'phone': 'phone', 'alert': 'alert-triangle', 'signal': 'signal',
    'wifi': 'wifi', 'trending': 'trending-up', 'cart': 'shopping-cart',
    'check': 'check', 'close': 'x', 'tag': 'tag', 'user': 'user',
    'send': 'send', 'building': 'building-2', 'edit': 'pencil',
    'file': 'file-text', 'mail': 'mail', 'eye': 'eye', 'download': 'download',
    'close-circle': 'x-circle', 'headphones': 'headphones',
}

# ---------- emoji / glyph -> lucide name ----------
# NOTE: order matters - longer sequences (with VS16/ZWJ) first.
EMOJI_MAP = [
    ('🛍️', 'shopping-bag'), ('👁‍🗨', 'message-circle'), ('👁\u200d🗨', 'message-circle'),
    ('🛡️', 'shield'), ('❤️', 'heart'), ('⚠️', 'alert-triangle'),
    ('✏️', 'pencil'), ('✂️', 'scissors'), ('🖼️', 'image'), ('🎟️', 'ticket'),
    ('🏷️', 'tag'), ('✉️', 'mail'), ('⚖️', 'scale'), ('✨', 'sparkles'),
    ('🎁', 'gift'), ('💬', 'message-circle'), ('🔍', 'search'),
    ('📦', 'package'), ('🔔', 'bell'), ('🏆', 'trophy'),
    ('🔗', 'link'), ('📱', 'smartphone'), ('⚡', 'zap'),
    ('👤', 'user'), ('📍', 'map-pin'), ('🕐', 'clock'),
    ('🔒', 'lock'), ('📷', 'camera'), ('👁', 'eye'),
    ('♥', 'heart'), ('🔥', 'flame'), ('🔄', 'refresh-cw'),
    ('🐕', 'paw-print'), ('🐈', 'paw-print'), ('🐱', 'paw-print'),
    ('✅', 'check-circle'), ('📭', 'inbox'), ('🎟', 'ticket'),
    ('🛡', 'shield'), ('❓', 'help-circle'), ('🛒', 'shopping-cart'),
    ('🎫', 'ticket'), ('📤', 'upload'), ('🛍', 'shopping-bag'),
    ('👍', 'thumbs-up'), ('🚚', 'truck'), ('📄', 'file-text'),
    ('⚠', 'alert-triangle'), ('➖', 'minus'), ('🧾', 'receipt'),
    ('📈', 'trending-up'), ('➕', 'plus'), ('📋', 'clipboard-list'),
    ('📅', 'calendar'), ('🖼', 'image'), ('🏷', 'tag'),
    ('🏠', 'home'), ('🧴', 'spray-can'), ('➤', 'send'),
    ('🔦', 'flashlight'), ('👥', 'users'), ('🤍', 'heart'),
    ('💡', 'lightbulb'), ('📜', 'scroll-text'), ('🎧', 'headphones'),
    ('👩', 'user'), ('♻', 'recycle'), ('💰', 'banknote'),
    ('📊', 'bar-chart'), ('📖', 'book'), ('🏦', 'building-2'),
    ('📔', 'book'), ('🍽', 'utensils'), ('⚙', 'settings'),
    ('🛁', 'bath'), ('🦷', 'brush'), ('❤', 'heart'),
    ('♡', 'heart'), ('🙈', 'eye-off'), ('🎉', 'party-popper'),
    ('👎', 'thumbs-down'), ('📘', 'book'), ('📞', 'phone'),
    ('☆', 'star'), ('🔑', 'key'), ('💻', 'laptop'),
    ('🔀', 'shuffle'), ('🌙', 'moon'), ('🌐', 'globe'),
    ('👑', 'crown'), ('🗨', 'message-circle'), ('⭐', 'star'),
    ('🐈‍⬛', 'paw-print'),
    # extra uncovered pass-2 set
    ('💳', 'credit-card'), ('💰', 'banknote'), ('📶', 'signal'),
    ('🖥', 'monitor'), ('😊', 'smile'), ('😌', 'smile'),
    ('💊', 'pill'), ('💉', 'syringe'), ('🩺', 'stethoscope'),
    ('📝', 'edit-3'), ('📌', 'map-pin'), ('🎾', 'paw-print'),
    ('🍗', 'utensils'), ('🐾', 'paw-print'), ('♀', 'user'),
    ('♂', 'user'), ('🎒', 'shopping-bag'), ('📸', 'camera'),
    ('🎯', 'target'), ('🤝', 'handshake'), ('👣', 'footprints'),
    ('🐧', 'paw-print'), ('💚', 'heart'), ('💙', 'heart'),
    ('🤖', 'cpu'), ('📹', 'video'),
]

# unicode glyphs used as icons (replace in template text)
GLYPH_MAP = [
    ('‹', 'arrow-left'),
    ('›', 'chevron-right'),
    ('✓', 'check'),
    ('✕', 'x'),
    ('×', 'x'),
    ('★', 'star'),
    ('←', 'arrow-left'),
    ('→', 'arrow-right'),
    ('✦', 'sparkles'),
    ('✗', 'x'),
    ('✧', 'sparkles'),
]

def icon_span(name):
    return '<text class="luc luc-%s"></text>' % name

def load_file(p):
    with open(p, encoding='utf-8') as f:
        return f.read()

def save_file(p, s):
    with open(p, 'w', encoding='utf-8', newline='') as f:
        f.write(s)

def replace_iconfont(s):
    def sub(m):
        cls = m.group(1)
        target = ICONFONT_MAP.get(cls, cls)
        return 'class="luc luc-%s"' % target
    return re.sub(r'class="iconfont\s+icon-([a-z0-9-]+)"', sub, s)

def replace_static(s):
    """Replace emoji/glyph sequences appearing in static template text
    (not inside {{ }} expressions, not inside attribute values, not in <style>/<script>)."""
    # work only on the <template> section
    m = re.search(r'<template>', s)
    if not m:
        return s
    head, tail = s[:m.start()], s[m.end():]
    tpl_end = tail.find('</template>')
    if tpl_end < 0:
        return s
    tpl, rest = tail[:tpl_end], tail[tpl_end:]

    # protect {{ }} expressions and attributes: mask them
    expr_mask = {}
    def mask_expr(m2):
        k = '@@E%d@@' % len(expr_mask)
        expr_mask[k] = m2.group(0)
        return k
    tpl2 = re.sub(r'\{\{.*?\}\}', mask_expr, tpl, flags=re.S)
    attr_mask = {}
    def mask_attr(m2):
        k = '@@A%d@@' % len(attr_mask)
        attr_mask[k] = m2.group(0)
        return k
    tpl3 = re.sub(r'\s[a-zA-Z:@-]+="[^"]*"', mask_attr, tpl2)

    # also protect element tags themselves
    tag_mask = {}
    def mask_tag(m2):
        k = '@@T%d@@' % len(tag_mask)
        tag_mask[k] = m2.group(0)
        return k
    tpl4 = re.sub(r'</?[a-zA-Z][^<>]*>', mask_tag, tpl3)

    # now replace remaining raw sequences in text content
    for seq, name in EMOJI_MAP + GLYPH_MAP:
        if seq in tpl4:
            tpl4 = tpl4.replace(seq, icon_span(name))

    # restore
    tpl5 = tpl4
    for k, v in reversed(list(tag_mask.items())):
        tpl5 = tpl5.replace(k, v)
    for k, v in reversed(list(attr_mask.items())):
        tpl5 = tpl5.replace(k, v)
    for k, v in reversed(list(expr_mask.items())):
        tpl5 = tpl5.replace(k, v)

    return head + '<template>' + tpl5 + rest

def cleanup_vs(s):
    # remove stray variation selectors / ZWJ left over
    return s.replace('\ufe0f', '').replace('\u200d', '')

def main():
    files = sorted(glob.glob(os.path.join(ROOT, '**', '*.vue'), recursive=True))
    changes = []
    for fp in files:
        orig = load_file(fp)
        s = replace_iconfont(orig)
        s = replace_static(s)
        s = cleanup_vs(s)
        if s != orig:
            save_file(fp, s)
            # count changed lines approx
            changes.append(os.path.relpath(fp, ROOT))
    print('changed %d files' % len(changes))
    for c in changes:
        print(' ', c)
    # summary of remaining emoji
    emoji_re = re.compile('[\U0001F300-\U0001FAFF\u2600-\u27BF\uFE0F]')
    remain = 0
    for fp in files:
        s = load_file(fp)
        t = s.split('<style')[0]
        found = emoji_re.findall(t)
        if found:
            remain += len(found)
            print('REMAIN', len(found), os.path.relpath(fp, ROOT), ''.join(set(found))[:20])
    print('remaining emoji chars in templates:', remain)

if __name__ == '__main__':
    main()
