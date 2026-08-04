# -*- coding: utf-8 -*-
"""阶段1盘点：解析设计稿标题/区块 vs 现有Vue页面标题，生成映射表"""
import os, re, json, glob

DESIGN_DIR = r'D:\MOYUYOWPC\APPdocs\admin'
VIEW_DIR = r'D:\MOYUYOWPC\moyuyo-server\moyuyo-admin\src\views'

def extract_blocks(path):
    """提取HTML设计稿中的页面标题、h1/h2/h3文本、卡片标题"""
    with open(path, encoding='utf-8', errors='ignore') as f:
        html = f.read()
    title_m = re.search(r'<title>(.*?)</title>', html, re.S)
    title = title_m.group(1).strip() if title_m else ''
    # 提取 h1/h2 及带 class 的 section/panel 标题
    heads = []
    for m in re.finditer(r'<h([1-4])[^>]*>(.*?)</h\1>', html, re.S):
        t = re.sub(r'<[^>]+>', '', m.group(2)).strip()
        if t and len(t) < 40:
            heads.append(t)
    # 提取面板/区块标题（page-title / panel-title / section-title 类）
    blocks = []
    for m in re.finditer(r'class="[^"]*(?:panel-title|section-title|block-title|card-title|module-title)[^"]*"[^>]*>\s*([^<]{2,40})', html):
        blocks.append(m.group(1).strip())
    # 统计主要组件出现次数
    comps = {
        'table': len(re.findall(r'<table', html)),
        'dialog/modal': len(re.findall(r'class="[^"]*(?:el-dialog|modal|dialog)[^"]*"', html)) + len(re.findall(r'<dialog', html)),
        'form': len(re.findall(r'<form|el-form', html)),
        'button': len(re.findall(r'<button', html)),
        'tabs': len(re.findall(r'el-tabs|class="tabs"', html)),
        'chart': len(re.findall(r'chart|echarts|canvas', html, re.I)),
    }
    return {'file': os.path.basename(path), 'title': title, 'heads': heads[:12], 'blocks': blocks[:8], 'comps': comps}

def extract_vue(path):
    """提取Vue页面的标题区域文本与组件结构"""
    with open(path, encoding='utf-8', errors='ignore') as f:
        src = f.read()
    # 提取页面标题 h1 / page-title-area
    title_m = re.search(r'<h1[^>]*>(.*?)</h1>', src, re.S)
    title = re.sub(r'[{{}}<>=/ ]', '', title_m.group(1)).strip() if title_m else ''
    title2_m = re.search(r'page-title-area[^>]*>.*?<p[^>]*>(.*?)</p>', src, re.S)
    subtitle = title2_m.group(1).strip() if title2_m else ''
    comps = {
        'table': len(re.findall(r'<el-table|data-table|<table', src)),
        'dialog': len(re.findall(r'<el-dialog|el-drawer', src)),
        'form': len(re.findall(r'<el-form', src)),
        'button': len(re.findall(r'<el-button|<button', src)),
        'tabs': len(re.findall(r'<el-tabs|el-tab-pane', src)),
        'api_calls': len(re.findall(r'from .\./api/', src)),
    }
    return {'file': os.path.basename(path), 'title': title, 'subtitle': subtitle, 'comps': comps}

def main():
    designs = []
    for p in sorted(glob.glob(os.path.join(DESIGN_DIR, 'admin-*.html'))):
        designs.append(extract_blocks(p))
    views = []
    for p in sorted(glob.glob(os.path.join(VIEW_DIR, '*.vue'))):
        if p.endswith('.bak') or 'backup' in p:
            continue
        views.append(extract_vue(p))
    with open('mapping_report.json', 'w', encoding='utf-8') as f:
        json.dump({'designs': designs, 'views': views}, f, ensure_ascii=False, indent=1)
    # 打印设计稿清单
    print(f'设计稿数量: {len(designs)} | Vue页面数量: {len(views)}')
    print('\n===== 设计稿清单 =====')
    for d in designs:
        print(f"[{d['file']}] {d['title']} | h2/h3: {d['heads'][:5]} | 表格:{d['comps']['table']} 弹窗:{d['comps']['dialog/modal']} 表单:{d['comps']['form']} 按钮:{d['comps']['button']}")
    print('\n===== Vue页面清单 =====')
    for v in views:
        print(f"[{v['file']}] {v['title']} | 表格:{v['comps']['table']} 弹窗:{v['comps']['dialog']} 表单:{v['comps']['form']} 按钮:{v['comps']['button']} API:{v['comps']['api_calls']}")

if __name__ == '__main__':
    main()
