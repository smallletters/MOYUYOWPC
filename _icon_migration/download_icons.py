# -*- coding: utf-8 -*-
"""Download Lucide SVG icons needed for the font. Tries exact name, then fallbacks."""
import urllib.request, os, json, time

BASE = 'https://unpkg.com/lucide-static@1.34.0/icons/{}.svg'
OUT = 'D:/MOYUYOWPC/_icon_migration/svg'

# icon-key -> (primary name, [fallback names])
NEEDED = {
    # common
    'home': ('home', []), 'user': ('user', []), 'search': ('search', []),
    'heart': ('heart', []), 'star': ('star', []), 'x': ('x', []),
    'check': ('check', []), 'plus': ('plus', []), 'minus': ('minus', []),
    'chevron-left': ('chevron-left', []), 'chevron-right': ('chevron-right', []),
    'chevron-down': ('chevron-down', []), 'arrow-left': ('arrow-left', []),
    'arrow-right': ('arrow-right', []), 'arrow-up': ('arrow-up', []),
    'info': ('info', []), 'clock': ('clock', []), 'bell': ('bell', []),
    'settings': ('settings', []), 'sparkles': ('sparkles', []),
    'shield': ('shield', []), 'lock': ('lock', []), 'key': ('key', []),
    'eye': ('eye', []), 'eye-off': ('eye-off', []), 'camera': ('camera', []),
    'image': ('image', []), 'mail': ('mail', []), 'phone': ('phone', []),
    'link': ('link', []), 'globe': ('globe', []), 'moon': ('moon', []),
    'download': ('download', []), 'upload': ('upload', []),
    'refresh-cw': ('refresh-cw', []), 'rotate-ccw': ('rotate-ccw', []),
    'repeat': ('repeat', []), 'shuffle': ('shuffle', []),
    'truck': ('truck', []), 'map-pin': ('map-pin', []), 'flag': ('flag', []),
    'calendar': ('calendar', []), 'trash-2': ('trash-2', ['trash']),
    'pencil': ('pencil', ['edit-3', 'edit']), 'edit-3': ('edit-3', []),
    'tag': ('tag', []), 'trophy': ('trophy', []), 'award': ('award', []),
    'medal': ('medal', []), 'crown': ('crown', []), 'gift': ('gift', []),
    'ticket': ('ticket', []), 'party-popper': ('party-popper', []),
    'wallet': ('wallet', []), 'banknote': ('banknote', []),
    'receipt': ('receipt', []), 'invoice': ('invoice', []),
    'file-text': ('file-text', ['file']), 'book-open': ('book-open', []),
    'book': ('book', []), 'scroll-text': ('scroll-text', []),
    'landmark': ('landmark', []), 'scale': ('scale', []),
    'bar-chart': ('chart-bar', ['bar-chart']),
    'trending-up': ('trending-up', []), 'line-chart': ('chart-line', ['line-chart']),
    'shopping-cart': ('shopping-cart', []), 'shopping-bag': ('shopping-bag', []),
    'package': ('package', []), 'inbox': ('inbox', []),
    'message-circle': ('message-circle', []), 'message-square': ('message-square', []),
    'send': ('send', []), 'share-2': ('share-2', []),
    'users': ('users', []), 'user-plus': ('user-plus', []),
    'smile': ('smile', []), 'thumbs-up': ('thumbs-up', []), 'thumbs-down': ('thumbs-down', []),
    'headphones': ('headphones', []), 'help-circle': ('circle-help', ['help-circle']),
    'alert-triangle': ('triangle-alert', ['alert-triangle']),
    'zap': ('zap', []), 'flame': ('flame', []), 'lightbulb': ('lightbulb', []),
    'check-circle': ('circle-check', ['check-circle']),
    'x-circle': ('circle-x', ['x-circle']), 'close-circle': ('circle-x', ['close-circle']),
    'smartphone': ('smartphone', []), 'monitor': ('monitor', []), 'cpu': ('cpu', []),
    'wifi': ('wifi', []), 'signal': ('signal', []), 'battery-full': ('battery-full', []),
    'droplet': ('droplet', []), 'brain': ('brain', []),
    'instagram': ('instagram', []), 'facebook': ('facebook', []), 'twitter': ('twitter', []),
    'external-link': ('external-link', []), 'building-2': ('building-2', []),
    'coins': ('coins', []), 'cat': ('cat', []), 'dog': ('dog', []),
    'paw-print': ('paw-print', []), 'bone': ('bone', []), 'utensils': ('utensils', []),
    'scissors': ('scissors', []), 'bath': ('bath', []), 'syringe': ('syringe', []),
    'pill': ('pill', []), 'brush': ('brush', []), 'spray-can': ('spray-can', []),
    'footprints': ('footprints', []), 'stethoscope': ('stethoscope', []),
    'recycle': ('recycle', []), 'layout-grid': ('layout-grid', []),
    'list': ('list', []), 'grid': ('grid', []), 'more-horizontal': ('ellipsis', ['more-horizontal']),
    'copy': ('copy', []), 'clipboard-list': ('clipboard-list', []),
    'target': ('target', []), 'gauge': ('gauge', []), 'percent': ('percent', []),
    'badge-check': ('badge-check', []), 'verified': ('badge-check', []),
    'sun': ('sun', []), 'cloud': ('cloud', []), 'video': ('video', []),
    'play': ('play', []), 'pause': ('pause', []), 'mic': ('mic', []),
    'volume-2': ('volume-2', []), 'bookmark': ('bookmark', []),
    'history': ('history', []), 'archive': ('archive', []),
    'credit-card': ('credit-card', []), 'qr-code': ('qr-code', []),
    'bell-off': ('bell-off', []), 'delete': ('delete', []), 'log-out': ('log-out', []),
    'flashlight': ('flashlight', []), 'at-sign': ('at-sign', []),
    'handshake': ('handshake', []), 'scan-line': ('scan-line', []),
}

os.makedirs(OUT, exist_ok=True)
missing, got = [], []
for key, (primary, fallbacks) in NEEDED.items():
    if os.path.exists(os.path.join(OUT, key + '.svg')):
        got.append(key); continue
    ok = False
    for name in [primary] + fallbacks:
        url = BASE.format(name)
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=20) as r:
                if r.status == 200 and not url.endswith('/icons/404.svg'):
                    data = r.read()
                    if b'<svg' in data:
                        open(os.path.join(OUT, key + '.svg'), 'wb').write(data)
                        got.append(key); ok = True
                        break
        except Exception as e:
            pass
    if not ok:
        missing.append((key, primary, fallbacks))
        print('MISSING:', key, primary, fallbacks)

print('got %d icons' % len(got))
if missing:
    print('missing list:')
    for m in missing: print(' ', m)
