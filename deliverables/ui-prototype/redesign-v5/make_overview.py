# -*- coding: utf-8 -*-
"""v5 MIST SLATE — 五页并排总览图 00-overview.png"""
from PIL import Image, ImageDraw, ImageFont
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SCREENS = os.path.join(HERE, 'screens')

PAGES = [
    ('01-pet.png',       '宠物  PET'),
    ('02-home.png',      '商城  HOME'),
    ('03-community.png', '社区  COMMUNITY'),
    ('04-messages.png',  '消息  MESSAGES'),
    ('05-user.png',      '我的  USER'),
]

PW, PH = 393, 852          # 单页逻辑尺寸（截图为 2x，等比缩回）
GAP = 24
LABEL_H = 44
HEADER_H = 104
PAD = 24

W = PAD * 2 + len(PAGES) * PW + (len(PAGES) - 1) * GAP
H = HEADER_H + LABEL_H + PH + PAD

CANVAS = '#EEF1F3'
SLATE = '#3A4B5C'
INK = '#1F2D3D'
INK2 = '#566573'
ACCENT = '#945048'
HAIRLINE = '#D6DEE4'

def font(path, size):
    return ImageFont.truetype(path, size)

fb = font('C:/Windows/Fonts/msyhbd.ttc', 34)   # 标题粗
fm = font('C:/Windows/Fonts/msyhbd.ttc', 20)   # 页名
fs = font('C:/Windows/Fonts/msyh.ttc', 17)     # 副标题
fl = font('C:/Windows/Fonts/msyh.ttc', 15)     # 标签小字

canvas = Image.new('RGB', (W, H), CANVAS)
d = ImageDraw.Draw(canvas)

# header：品牌字 + 标题 + 副标题 + 干玫瑰细线
d.text((PAD, 20), 'MOYUYO', font=font('C:/Windows/Fonts/georgia.ttf', 30), fill=SLATE)
d.text((PAD + 150, 26), '五页高保真重设计  ·  MIST SLATE 雾银石墨', font=fb, fill=INK)
d.text((PAD, 66), '393 × 852  /  v5 全新风格  /  冷调静奢 · 无衬线 · 雾面磨砂', font=fs, fill=INK2)
d.line([(PAD, 96), (W - PAD, 96)], fill=HAIRLINE, width=1)

for i, (fname, label) in enumerate(PAGES):
    x = PAD + i * (PW + GAP)
    y0 = HEADER_H
    # 页名标签带
    d.rectangle([x, y0, x + PW, y0 + LABEL_H], fill='#FFFFFF', outline=HAIRLINE, width=1)
    d.rectangle([x + 12, y0 + 14, x + 16, y0 + 30], fill=ACCENT)  # 干玫瑰色条
    d.text((x + 24, y0 + 11), label, font=fm, fill=SLATE)
    # 截图等比缩回并贴入
    img = Image.open(os.path.join(SCREENS, fname)).convert('RGB').resize((PW, PH), Image.LANCZOS)
    canvas.paste(img, (x, y0 + LABEL_H))
    # 1px 冷灰外框（无拼贴阴影）
    d.rectangle([x, y0 + LABEL_H, x + PW - 1, y0 + LABEL_H + PH - 1], outline=HAIRLINE, width=1)

out = os.path.join(SCREENS, '00-overview.png')
canvas.save(out, optimize=True)
print('WROTE', out, canvas.size, os.path.getsize(out), 'bytes')
