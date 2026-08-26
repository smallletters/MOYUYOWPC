# -*- coding: utf-8 -*-
"""Pass 2: dynamic icon migration (MOYUYO app).

1) Data pass  : emoji-ONLY string literals inside <script> -> lucide class name
                (comments protected; mixed strings like '🔄 重新连接' untouched)
2) Template A : sole-content bindings
                   {{ X.icon }} / {{ X.emoji }} / {{ X.icon || 'emoji' }}
                -> <text class="... luc" :class="$luc(X.icon)"></text>
3) Template B : pure-emoji ternary expressions
                   {{ cond ? '⚡' : '🔦' }}
                -> <text class="... luc" :class="$luc(cond ? 'zap' : 'flashlight')"></text>

Strategy for template: locate each {{ }} binding, back-track to the nearest
opening tag, forward-match the closing tag (skipping nested same-tag opens),
then verify the WHOLE element content is exactly that binding (sole-content)
before rewriting. This avoids the old ELEM_RE pitfall where the outermost
element swallowed inner icon elements and the '<' guard blocked conversion.
"""
import os, re, glob, ast, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else r'D:/MOYUYOWPC/moyuyo-app/src/pages'
VS16, ZWJ = '\ufe0f', '\u200d'

# ---------- canonical emoji -> lucide name map ----------
tree = ast.parse(open(r'D:/MOYUYOWPC/_icon_migration/replace_icons.py', encoding='utf-8').read())
MAP = {}
for node in tree.body:
    if isinstance(node, ast.Assign):
        for t in node.targets:
            if isinstance(t, ast.Name) and t.id in ('EMOJI_MAP', 'GLYPH_MAP'):
                for k, v in ast.literal_eval(node.value):
                    MAP[k] = v
MAP.update({
    '⚖': 'scale', '✂': 'scissors', '✉': 'mail', '🍎': 'apple',
    '🏥': 'cross', '👗': 'shirt', '🗺': 'map', '🧠': 'brain',
    '⌕': 'search', '♂': 'user', '♀': 'user', '♡': 'heart',
    '👁🗨': 'eye-off', '🤍': 'heart',
    '↑': 'arrow-up', '↓': 'arrow-down', '⏸': 'pause', '▶': 'play',
})

def emoji_name(s):
    if not s:
        return None
    c = s.replace(VS16, '').replace(ZWJ, '')
    return MAP.get(s) or MAP.get(c)

STR_RE = re.compile(r"""(['"])((?:\\.|(?!\1)[^\\])*)\1""")

# ---------- data pass ----------
def data_pass(scr):
    count = [0]
    masks = {}

    def mask(kind):
        def f(m2):
            k = '@@C%d@@' % len(masks)
            masks[k] = m2.group(0)
            return k
        return f

    s2 = re.sub(r'//[^\n]*', mask('line'), scr)
    s2 = re.sub(r'/\*.*?\*/', mask('block'), s2, flags=re.S)

    def sub(m):
        if m.group(1) == '`':
            return m.group(0)
        name = emoji_name(m.group(2))
        if name is None:
            return m.group(0)
        count[0] += 1
        return m.group(1) + name + m.group(1)

    s3 = STR_RE.sub(sub, s2)
    for k, v in reversed(list(masks.items())):
        s3 = s3.replace(k, v)
    return s3, count[0]

# ---------- template passes ----------
def add_luc_class(attrs):
    m = re.search(r'\bclass=([\'"])(.*?)\1', attrs, re.S)
    if m:
        cur = m.group(2).strip()
        new_cls = (cur + ' luc') if cur else 'luc'
        return attrs[:m.start(2)] + new_cls + attrs[m.end(2):]
    return ' class="luc"' + attrs

ICON_BIND_RE = re.compile(
    r'^([A-Za-z_$][\w$]*(?:\[[^\]]*\])?(?:\.[A-Za-z_$][\w$]*)*)'
    r'\.(icon|emoji)'
    r'(?:\s*\|\|\s*([\'"])(.*?)\3)?$', re.S)

BIND_RE = re.compile(r'\{\{\s*(.*?)\s*\}\}', re.S)
OPEN_RE = re.compile(r'<(\w+)((?:[^>"\']|"[^"]*"|\'[^\']*\')*)>')
SKIP_TAGS = ('template', 'slot', 'component', 'block', 'suspense')

def split_ternary(expr):
    """Split 'cond ? then : else' (no nested ternary) -> (cond, then, else), else None.

    String literals are masked first so ?/: inside strings don't confuse the scan;
    optional chaining (?.) and nullish (??) are not treated as ternary starts.
    """
    lits = {}

    def mask_str(m2):
        k = '@@S%d@@' % len(lits)
        lits[k] = m2.group(0)
        return k

    masked = STR_RE.sub(mask_str, expr)
    depth = qpos = 0
    for i, ch in enumerate(masked):
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        elif ch == '?' and depth == 0:
            nxt = masked[i + 1] if i + 1 < len(masked) else ''
            if nxt in '.?':
                continue
            qpos = i
            break
    if not qpos:
        return None
    cpos = depth = 0
    for i in range(qpos + 1, len(masked)):
        ch = masked[i]
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        elif ch == '?' and depth == 0:
            return None  # nested ternary
        elif ch == ':' and depth == 0:
            cpos = i
            break
    if not cpos:
        return None

    def unm(s):
        for k, v in lits.items():
            s = s.replace(k, v)
        return s

    return unm(masked[:qpos]), unm(masked[qpos + 1:cpos]), unm(masked[cpos + 1:])

def lit_state(part):
    """Classify string literals inside a ternary result branch.

    ('ok', n)  : every literal is a mappable emoji (n > 0 mapped)
    ('var', 0) : no string literals at all (pure expression)
    ('empty',0): only empty/blank literals
    (None,  0) : at least one non-empty literal is not a mappable emoji
    """
    lits = [m2.group(2) for m2 in STR_RE.finditer(part)]
    if not lits:
        return ('var', 0)
    cnt = 0
    for lit in lits:
        if lit.strip() == '':
            continue
        if emoji_name(lit) is None:
            return (None, 0)
        cnt += 1
    return ('ok', cnt) if cnt else ('empty', 0)

def template_pass(tpl, file_key, emoji_files):
    """Returns (new_tpl, changes, skipped)."""
    changes = 0
    skipped = []

    def find_container(m):
        """Element whose sole content is the binding match m, or None."""
        bstart, bend = m.start(), m.end()
        opens = list(OPEN_RE.finditer(tpl, 0, bstart))
        if not opens:
            return None
        mo = opens[-1]
        tag, attrs = mo.group(1), mo.group(2)
        if attrs.rstrip().endswith('/'):      # self-closing
            return None
        if tag in SKIP_TAGS:
            return None
        depth = 0
        pat = re.compile(r'<%s\b[^>]*>|</%s\s*>' % (tag, tag))
        close = None
        for cm in pat.finditer(tpl, bend):
            if cm.group(0).startswith('</'):
                if depth == 0:
                    close = cm
                    break
                depth -= 1
            elif not cm.group(0)[:-1].rstrip().endswith('/'):
                depth += 1
        if close is None:
            return None
        # sole-content check: nothing before/after the binding inside the element
        if tpl[mo.end():close.start()].strip() != m.group(0).strip():
            return None
        return mo, close, tag, attrs

    repls = []  # (open_start, close_end, new_element)
    for m in BIND_RE.finditer(tpl):
        expr = m.group(1).strip()
        if not expr or '$luc' in expr:
            continue
        c = find_container(m)
        if c is None:
            continue
        mo, close, tag, attrs = c
        if re.search(r':class\s*=', attrs) or 'luc' in attrs:
            continue

        new_el = None
        bm = ICON_BIND_RE.match(expr)
        if bm:
            base, field, q, fall = bm.group(1), bm.group(2), bm.group(3), bm.group(4)
            if field == 'emoji' and file_key not in emoji_files:
                continue
            base_expr = base + '.' + field
            if bm.group(3):
                fname = emoji_name(fall)
                if fname is None:
                    skipped.append(('fallback-unmapped', expr[:50]))
                    continue
                cls = "$luc(%s) || 'luc luc-%s'" % (base_expr, fname)
            else:
                cls = '$luc(%s)' % base_expr
            new_el = '<%s%s :class="%s"></%s>' % (tag, add_luc_class(attrs), cls, tag)
        else:
            tri = split_ternary(expr)
            if tri is not None:
                cond, then_p, else_p = tri
                t_res = lit_state(then_p)
                e_res = lit_state(else_p)
                if t_res[0] == 'ok' and e_res[0] == 'ok':
                    def rep_part(part):
                        def rp(m2):
                            name = emoji_name(m2.group(2))
                            if name is None:
                                return m2.group(0)
                            return m2.group(1) + name + m2.group(1)
                        return STR_RE.sub(rp, part)
                    expr2 = '%s ? %s : %s' % (cond, rep_part(then_p), rep_part(else_p))
                    new_el = '<%s%s :class="$luc(%s)"></%s>' % (tag, add_luc_class(attrs), expr2, tag)
                else:
                    skipped.append(('ternary-mixed', '%s [%s|%s]' % (expr[:44], t_res[0], e_res[0])))
        if new_el is None:
            continue

        # overlap guard: valid containers are disjoint
        if repls and mo.start() < repls[-1][1]:
            continue
        repls.append((mo.start(), close.end(), new_el))
        changes += 1

    new_tpl = tpl
    for start, end, el in sorted(repls, key=lambda r: r[0], reverse=True):
        new_tpl = new_tpl[:start] + el + new_tpl[end:]
    return new_tpl, changes, skipped

# ---------- main ----------
def main():
    files = sorted(glob.glob(os.path.join(ROOT, '**', '*.vue'), recursive=True))

    # files that define emoji-only `emoji:` literals (allow .emoji binding conversion)
    emoji_files = set()
    for fp in files:
        s = open(fp, encoding='utf-8').read()
        m = re.search(r'<script[^>]*>(.*?)</script>', s, re.S)
        if not m:
            continue
        scr = m.group(1)
        if re.search(r'\bemoji\s*:\s*[\'"]', scr) and \
           any(emoji_name(x.group(2)) for x in STR_RE.finditer(scr)):
            emoji_files.add(os.path.basename(fp))

    total_d = total_t = 0
    skipped_all = []

    for fp in files:
        s = open(fp, encoding='utf-8').read()
        m_tpl = re.search(r'<template>', s)
        m_scr = re.search(r'<script[^>]*>', s)
        if not m_tpl and not m_scr:
            continue

        tpl = scr = ''
        if m_tpl:
            t_end = s.find('</template>', m_tpl.end())
            tpl = s[m_tpl.end():t_end]
        if m_scr:
            s_end = s.find('</script>', m_scr.end())
            scr = s[m_scr.end():s_end]

        new_scr, dc = (scr, 0) if not scr else data_pass(scr)
        new_tpl, tc, sk = (tpl, 0, []) if not tpl else template_pass(tpl, os.path.basename(fp), emoji_files)
        skipped_all += [(os.path.relpath(fp, ROOT),) + x for x in sk]

        if new_scr != scr or new_tpl != tpl:
            head = s[:m_tpl.start()] if m_tpl else ''
            rest = ''
            if m_tpl:
                tail = s[m_tpl.end():]
                rest = tail[tail.find('</template>') + len('</template>'):]
            else:
                rest = s
            body = ('<template>' + new_tpl + '</template>') if m_tpl else ''
            m2 = re.search(r'<script[^>]*>', rest)
            if m2:
                tag = m2.group(0)
                s_end = rest.find('</script>', m2.end())
                body += rest[:m2.start()] + tag + new_scr + '</script>' + rest[s_end + len('</script>'):]
            else:
                body += rest
            open(fp, 'w', encoding='utf-8', newline='').write(head + body)
            total_d += dc
            total_t += tc
            print('changed %-45s data:%2d tpl:%2d' % (os.path.relpath(fp, ROOT), dc, tc))

    print('\n== total data:%d template:%d' % (total_d, total_t))
    print('== skipped %d:' % len(skipped_all))
    for p, k, d in skipped_all:
        print('   %s [%s] %s' % (p, k, d))

if __name__ == '__main__':
    main()
