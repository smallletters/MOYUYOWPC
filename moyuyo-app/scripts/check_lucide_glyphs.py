from fontTools.ttLib import TTFont
f = TTFont(r'd:\MOYUYOWPC\moyuyo-app\src\static\fonts\lucide.ttf')
cmap = f.getBestCmap()
targets = {
    'alert-triangle \\e900': 0xe900,
    'bath \\e90a': 0xe90a,
    'brush \\e913': 0xe913,
    'check \\e918': 0xe918,
    'droplet \\e929': 0xe929,
    'pill \\e95a': 0xe95a,
    'stethoscope \\e975': 0xe975,
    'syringe \\e977': 0xe977,
}
print('TTF PUA mapping (None=glyph 缺失):')
for name, cp in targets.items():
    g = cmap.get(cp)
    print('  {:35s} -> {}'.format(name, g))
print()
print('Total glyph count:', f['maxp']['nGlyphs'])
print('Total mapped codepoints:', len(cmap))