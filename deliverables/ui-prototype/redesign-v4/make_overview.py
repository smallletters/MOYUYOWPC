# -*- coding: utf-8 -*-
"""Phase 3 — 五页并排总览拼图 00-overview.png"""
from PIL import Image, ImageDraw, ImageFont
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SCREENS = os.path.join(HERE, 'screens')

PAGES = [
    ('01-pet.png',       'PET · 宠物'),
    ('02-home.png',      'HOME · 商城'),
    ('03-community.png', 'COMMUNITY · 社区'),
    ('04-messages.png',  'MESSAGES · 消息'),
    ('05-user.png',      'USER · 我的'),
]

# target size per phone (half of 786x1704 -> 393x852)
PW, PH = 393, 852
GAP = 18
LABEL_H = 56
HEADER_H = 96
PAD = 24

W = PAD * 2 + len(PAGES) * PW + (len(PAGES) - 1) * GAP
H = HEADER_H + LABEL_H + PH + PAD

canvas = Image.new('RGB', (W, H), '#E9EFEC')
d = ImageDraw.Draw(canvas)

font_title = ImageFont.truetype('C:/Windows/Fonts/msyhbd.ttc', 34)
font_sub = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 18)
font_label = ImageFont.truetype('C:/Windows/Fonts/msyhbd.ttc', 21)

# header band
d.rectangle([0, 0, W, HEADER_H], fill='#0A3830')
d.text((PAD, 20), '墨屿 INK ATELIER  ·  Neo-Editorial', font=font_title, fill='#C6F24E')
d.text((PAD, 62), '五页高保真原型总览   /   393 × 852   /   Phase 3 Frontend Prototype', font=font_sub, fill='#F2F7F5')

for i, (fname, label) in enumerate(PAGES):
    x = PAD + i * (PW + GAP)
    y0 = HEADER_H
    # label band
    d.rectangle([x, y0, x + PW, y0 + LABEL_H], fill='#FFFFFF')
    d.line([x, y0 + LABEL_H - 1, x + PW, y0 + LABEL_H - 1], fill='#CBD9D3', width=1)
    tw = d.textbbox((0, 0), label, font=font_label)[2]
    d.text((x + (PW - tw) // 2, y0 + (LABEL_H - 24) // 2 - 2), label, font=font_label, fill='#125C4E')
    # screenshot
    img = Image.open(os.path.join(SCREENS, fname)).convert('RGB').resize((PW, PH), Image.LANCZOS)
    canvas.paste(img, (x, y0 + LABEL_H))
    # thin frame around phone
    d.rectangle([x, y0 + LABEL_H, x + PW - 1, y0 + LABEL_H + PH - 1], outline='#CBD9D3', width=1)

out = os.path.join(SCREENS, '00-overview.png')
canvas.save(out, optimize=True)
print('WROTE', out, canvas.size, os.path.getsize(out), 'bytes')
