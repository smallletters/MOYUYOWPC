/* ===========================================================================
 * MOYUYO iOS 原型 · 独立质量审查脚本 v3（质量审查官 严过审）
 *
 * 与构建师 _selfcheck.js 完全独立编写。v3 修正了前两版的测量假阳性：
 *   · 命中区：用 elementFromPoint 逐像素扫描真实命中带（不靠伪元素推算）
 *   · 图标对比度：图标字形 color vs 其承载底色（不拿图标底自身底色自比）
 *   · 图像上文字：先把文字设为 transparent 再截图，采样「纯背景像素」实算
 *   · 文本对比度：包含元素自身 background-color（角标/标签类）
 *   · §3.8 令牌解析：兼容 CRLF（构建师脚本在此处静默失败）
 *
 * 运行：node _audit.js  →  _audit_shots/*.png + audit.json + summary.txt
 * ========================================================================= */
'use strict';

const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const ROOT = __dirname;
const OUT = path.join(ROOT, '_audit_shots');
const PAGE_URL = 'file:///' + path.join(ROOT, 'index.html').replace(/\\/g, '/');

const SCREENS = [
  { id: 'pet', label: 'PetHub' }, { id: 'home', label: 'Home' },
  { id: 'community', label: 'Community' }, { id: 'messages', label: 'Messages' },
  { id: 'user', label: 'User' }
];
const STATES = ['normal', 'loading', 'empty', 'error', 'guest'];

if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true });
const htmlSrc = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
const cssSrc = (htmlSrc.match(/<style>([\s\S]*?)<\/style>/) || [, ''])[1];
const dsMd = fs.readFileSync(path.join(ROOT, '..', '01-design-system.md'), 'utf8');

const HELPERS = `
window.__AUDIT = (function () {
  function parseColor(c) {
    if (!c) return null;
    c = String(c).trim();
    if (c === 'transparent') return { r: 0, g: 0, b: 0, a: 0 };
    let m = c.match(/^rgba?\\(([^)]+)\\)$/i);
    if (m) { const p = m[1].split(/[,\\s\\/]+/).filter(Boolean).map(Number);
      return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 }; }
    m = c.match(/^#([0-9a-f]{3,8})$/i);
    if (m) { let h = m[1];
      if (h.length === 3) h = h[0]+h[0]+h[1]+h[1]+h[2]+h[2];
      if (h.length === 6) h += 'ff'; if (h.length !== 8) return null;
      return { r: parseInt(h.slice(0,2),16), g: parseInt(h.slice(2,4),16), b: parseInt(h.slice(4,6),16), a: parseInt(h.slice(6,8),16)/255 }; }
    return null;
  }
  function over(fg, bg) { const a = fg.a; return { r: fg.r*a+bg.r*(1-a), g: fg.g*a+bg.g*(1-a), b: fg.b*a+bg.b*(1-a), a: 1 }; }
  function lum(c) { const ch = v => { v/=255; return v<=0.03928 ? v/12.92 : Math.pow((v+0.055)/1.055,2.4); };
    return 0.2126*ch(c.r)+0.7152*ch(c.g)+0.0722*ch(c.b); }
  function ratio(a,b){ const L1=lum(a),L2=lum(b),hi=Math.max(L1,L2),lo=Math.min(L1,L2); return (hi+0.05)/(lo+0.05); }
  function effBg(el, includeSelf) {
    let node = includeSelf ? el : el.parentElement, stack = [], overImage = false;
    while (node && node.nodeType === 1) {
      const cs = getComputedStyle(node);
      if (cs.backgroundImage && cs.backgroundImage !== 'none') overImage = true;
      const bc = parseColor(cs.backgroundColor);
      if (bc && bc.a > 0) { stack.push(bc); if (bc.a >= 0.999) break; }
      node = node.parentElement;
    }
    let base = { r: 255, g: 255, b: 255, a: 1 };
    for (let i = stack.length-1; i>=0; i--) base = over(stack[i], base);
    return { bg: base, overImage: overImage };
  }
  function isVisible(el) {
    const cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) return false;
    const r = el.getBoundingClientRect();
    return r.width >= 1 && r.height >= 1;
  }
  return { parseColor, over, lum, ratio, effBg, isVisible };
})();
`;

function lumOf(c) { const ch = v => { v /= 255; return v <= 0.03928 ? v/12.92 : Math.pow((v+0.055)/1.055, 2.4); };
  return 0.2126*ch(c[0]) + 0.7152*ch(c[1]) + 0.0722*ch(c[2]); }
function ratioOf(a, b) { const L1 = lumOf(a), L2 = lumOf(b); const hi = Math.max(L1,L2), lo = Math.min(L1,L2); return (hi+0.05)/(lo+0.05); }

/** 采样纯背景像素：调用前先把目标区域内的文字设为 transparent */
async function sampleBg(page, clip) {
  const buf = await page.screenshot({ clip: clip });
  const b64 = buf.toString('base64');
  return await page.evaluate(async (data) => {
    const img = new Image();
    await new Promise((res, rej) => { img.onload = res; img.onerror = rej; img.src = 'data:image/png;base64,' + data; });
    const c = document.createElement('canvas');
    c.width = img.width; c.height = img.height;
    const ctx = c.getContext('2d'); ctx.drawImage(img, 0, 0);
    const d = ctx.getImageData(0, 0, c.width, c.height).data;
    const n = d.length / 4;
    const px = []; let r = 0, g = 0, b = 0;
    for (let i = 0; i < d.length; i += 4) { r += d[i]; g += d[i+1]; b += d[i+2]; px.push([d[i], d[i+1], d[i+2]]); }
    px.sort((a, b) => (a[0]+a[1]+a[2]) - (b[0]+b[1]+b[2]));
    const k = Math.max(1, Math.floor(n * 0.1));
    let dr = 0, dg = 0, db = 0;
    for (let i = 0; i < k; i++) { dr += px[i][0]; dg += px[i][1]; db += px[i][2]; }
    return { avg: [Math.round(r/n), Math.round(g/n), Math.round(b/n)],
             p10dark: [Math.round(dr/k), Math.round(dg/k), Math.round(db/k)] };
  }, b64);
}

async function gotoScreen(page, id) {
  await page.evaluate((sid) => {
    const b = Array.prototype.slice.call(document.querySelectorAll('#screenChips .chip')).find(x => x.getAttribute('data-screen') === sid);
    if (b) b.click();
  }, id);
  await page.waitForTimeout(420);
}
async function gotoState(page, st) {
  await page.evaluate((s) => {
    const b = Array.prototype.slice.call(document.querySelectorAll('#stateChips .chip')).find(x => x.getAttribute('data-state') === s);
    if (b) b.click();
  }, st);
  await page.waitForTimeout(160);
}

/* =========================================================================== */
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1500, height: 1180 }, deviceScaleFactor: 2 });
  const consoleErrors = [], pageErrors = [], failedRequests = [];
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', e => pageErrors.push(String(e)));
  page.on('requestfailed', r => failedRequests.push(r.url() + ' :: ' + (r.failure() && r.failure().errorText)));

  await page.goto(PAGE_URL, { waitUntil: 'load' });
  await page.addScriptTag({ content: HELPERS });
  await page.waitForTimeout(400);

  const report = { startedAt: new Date().toISOString(), page: PAGE_URL, screens: {}, globals: {}, suspects: {} };
  const screenBox = await page.evaluate(() => {
    const r = document.getElementById('deviceScreen').getBoundingClientRect();
    return { x: r.x, y: r.y, width: r.width, height: r.height };
  });
  report.globals.deviceScreenBox = screenBox;

  /* ============================== 静态扫描 ============================== */
  const declaredVars = new Set((cssSrc.match(/(--[a-z0-9-]+)\s*:/gi) || []).map(s => s.replace(/\s*:$/, '').trim()));
  const usedVars = new Set((cssSrc.match(/var\(\s*(--[a-z0-9-]+)/gi) || []).map(s => s.replace(/var\(\s*/i, '').trim()));
  // 关键修复：兼容 CRLF —— 构建师脚本在此处静默失败（dsCount=0）
  const dsBlock = (dsMd.match(/### 3\.8[\s\S]*?```css\r?\n([\s\S]*?)\r?\n```/) || [, ''])[1];
  const dsVars = new Set((dsBlock.match(/(--[a-z0-9-]+)\s*:/gi) || []).map(s => s.replace(/\s*:$/, '').trim()));
  const shellVars = new Set(['--shell-bg','--shell-panel','--shell-line','--shell-ink','--shell-ink-2','--shell-device-scale']);
  const jsVars = new Set(['--icon','--avatar','--text','--ok','--warn','--late']);

  report.globals.static = {
    hoverRuleCount: (cssSrc.match(/:hover/g) || []).length,
    forbiddenHex: {
      '#007AFF': (cssSrc.match(/#007AFF/gi) || []).length,
      '#004FAF': (cssSrc.match(/#004FAF/gi) || []).length,
      '#4C6EF5': (cssSrc.match(/#4C6EF5/gi) || []).length,
      'rgba(0,122,255': (cssSrc.match(/rgba\(\s*0\s*,\s*122\s*,\s*255/gi) || []).length,
      '#FF4D4F': (cssSrc.match(/#FF4D4F/gi) || []).length
    },
    colorPrimaryAsTextProp: (cssSrc.match(/(^|[;{\s])color\s*:\s*var\(\s*--color-primary\s*\)/gi) || []).length,
    undefinedVars: [...usedVars].filter(v => !declaredVars.has(v)),
    dsTokenCount: dsVars.size,
    protoTokenCount: declaredVars.size,
    dsTokensMissingInProto: [...dsVars].filter(v => !declaredVars.has(v)),
    protoTokensNotInDS: [...declaredVars].filter(v => !dsVars.has(v) && !shellVars.has(v) && !jsVars.has(v)),
    emojiInHtml: (htmlSrc.match(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/gu) || []),
    buildSelfcheckVerdict: (() => { try { return JSON.parse(fs.readFileSync(path.join(ROOT, '_shots', 'selfcheck.json'), 'utf8')).passed; } catch (e) { return null; } })(),
    buildSelfcheckCounts: (() => { try { const j = JSON.parse(fs.readFileSync(path.join(ROOT, '_shots', 'selfcheck.json'), 'utf8'));
      return { pass: j.passCount, fail: j.failCount, total: j.total, passed: j.passed,
               failedChecks: (j.checks || []).filter(c => !c.pass).map(c => c.name) }; } catch (e) { return null; } })()
  };

  /* ============================== 逐屏 ============================== */
  for (const s of SCREENS) {
    await gotoScreen(page, s.id);
    await page.screenshot({ path: path.join(OUT, `screen-${s.id}.png`),
      clip: { x: screenBox.x, y: screenBox.y, width: screenBox.width, height: screenBox.height } });

    const entry = { label: s.label, text: [], icons: [], overImageText: [], hit: { fail: [], total: 0, exempt: [] }, focus: {}, cards: {} };

    /* 文本对比度（含元素自身底色） */
    entry.text = await page.evaluate((scr) => {
      const A = window.__AUDIT; const scrEl = document.getElementById('scr-' + scr);
      const out = [], seen = new Set();
      const w = document.createTreeWalker(scrEl, NodeFilter.SHOW_TEXT); let n;
      while ((n = w.nextNode())) {
        const t = (n.nodeValue || '').trim(); if (!t) continue;
        const el = n.parentElement; if (!el || seen.has(el)) continue; seen.add(el);
        if (!A.isVisible(el)) continue;
        const cs = getComputedStyle(el); const fg = A.parseColor(cs.color); if (!fg) continue;
        const { bg, overImage } = A.effBg(el, true);
        const r = el.getBoundingClientRect();
        const rec = { text: t.slice(0, 26), cls: String(el.className).slice(0, 44), tag: el.tagName,
          fs: parseFloat(cs.fontSize), fw: cs.fontWeight, color: cs.color,
          bg: 'rgb(' + Math.round(bg.r) + ',' + Math.round(bg.g) + ',' + Math.round(bg.b) + ')',
          ratio: Math.round(A.ratio(fg, bg)*100)/100,
          rect: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) } };
        if (overImage) out.push(Object.assign({ overImage: true }, rec)); else out.push(rec);
      }
      return out;
    }, s.id);
    entry.text = entry.text.filter(t => !t.overImage);

    /* 图像上的文字：单独收集，稍后用「文字 transparent + 截图采样」实算 */
    const overTxt = await page.evaluate((scr) => {
      const A = window.__AUDIT; const scrEl = document.getElementById('scr-' + scr);
      const out = [], seen = new Set();
      const w = document.createTreeWalker(scrEl, NodeFilter.SHOW_TEXT); let n;
      while ((n = w.nextNode())) {
        const t = (n.nodeValue || '').trim(); if (!t) continue;
        const el = n.parentElement; if (!el || seen.has(el)) continue; seen.add(el);
        if (!A.isVisible(el)) continue;
        const cs = getComputedStyle(el); const fg = A.parseColor(cs.color); if (!fg) continue;
        // 该元素所在「图片层容器」内是否含 img（仅当自身祖先链无图片容器时）
        let p = el.parentElement, hasImgAncestor = false;
        while (p && p.nodeType === 1) { if (p.tagName === 'IMG') { hasImgAncestor = true; break; } p = p.parentElement; }
        if (hasImgAncestor) continue;
        // 找最近含 img 的祖先
        let q = el.parentElement, imgBox = null;
        while (q && q.nodeType === 1) { if (q.querySelector && q.querySelector('img')) { imgBox = q; break; } q = q.parentElement; }
        if (!imgBox) continue;
        // 仅当该容器在视口内且有可见 img
        const im = imgBox.querySelector('img'); if (!im || getComputedStyle(im).display === 'none') continue;
        const r = el.getBoundingClientRect();
        out.push({ text: t.slice(0, 26), cls: String(el.className).slice(0, 44), container: String(imgBox.className).slice(0, 40),
          fs: parseFloat(cs.fontSize), fw: cs.fontWeight, color: cs.color,
          rect: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) } });
      }
      return out;
    }, s.id);

    /* 图标 / 图形对比度 */
    entry.icons = await page.evaluate((scr) => {
      const A = window.__AUDIT; const scrEl = document.getElementById('scr-' + scr);
      const out = [];
      const sel = 'svg, i.luc, .row__chev, .conv__pin';
      Array.prototype.forEach.call(scrEl.querySelectorAll(sel), (el) => {
        if (!A.isVisible(el)) return;
        const cs = getComputedStyle(el); const fg = A.parseColor(cs.color);
        if (!fg || fg.a < 0.05) return;
        const { bg } = A.effBg(el, false);
        const r = el.getBoundingClientRect();
        out.push({ tag: el.tagName, cls: String(el.className).slice(0, 46), color: cs.color,
          bg: 'rgb(' + Math.round(bg.r) + ',' + Math.round(bg.g) + ',' + Math.round(bg.b) + ')',
          ratio: Math.round(A.ratio(A.over(fg, bg), bg)*100)/100,
          size: Math.round(Math.max(r.width, r.height)*10)/10 });
      });
      return out;
    }, s.id);

    /* 命中区：elementFromPoint 逐像素扫描真实命中带 */
    entry.hit = await page.evaluate(async (scr) => {
      const scrEl = document.getElementById('scr-' + scr);
      const dev = document.getElementById('deviceScreen').getBoundingClientRect();
      const out = { fail: [], total: 0, exempt: [], measured: [] };
      const cands = Array.prototype.slice.call(scrEl.querySelectorAll('button, a[href], [role="tab"]'));
      const seen = new Set();
      for (const el of cands) {
        if (seen.has(el)) continue; seen.add(el);
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden') continue;
        const r0 = el.getBoundingClientRect();
        if (r0.width < 1 || r0.height < 1) continue;
        if (el.closest('.conv__del')) continue;
        out.total++;
        const sc = el.closest('[data-scroll]');
        if (sc) {
          const scR = sc.getBoundingClientRect(); const r = el.getBoundingClientRect();
          if (r.bottom > scR.bottom - 2 || r.top < scR.top + 2) {
            sc.scrollTop += (r.top - scR.top) - scR.height * 0.4;
            await new Promise(res => requestAnimationFrame(() => requestAnimationFrame(res)));
          }
        }
        const r = el.getBoundingClientRect();
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        let top = null, bot = null, gaps = [];
        for (let d = -32; d <= 32; d++) {
          const y = cy + d;
          if (y < dev.top || y > dev.bottom) { continue; }
          const st = document.elementsFromPoint(cx, y) || [];
          const h = st[0];
          const ok = h && (h === el || el.contains(h));
          if (ok) { if (top === null) top = d; bot = d; }
          else if (top !== null && bot !== null && d > bot) break;
        }
        const eff = (top === null) ? 0 : (bot - top + 1);
        const rec = { cls: String(el.className).slice(0, 52), tag: el.tagName,
          visualW: Math.round(r.width), visualH: Math.round(r.height),
          effH: eff, top: top, bot: bot };
        out.measured.push(rec);
        if (el.closest('.segment')) { out.exempt.push(rec); continue; }
        if (eff < 44) out.fail.push(Object.assign({}, rec, { why: '有效命中高 ' + eff + 'pt < 44pt' }));
      }
      return out;
    }, s.id);

    /* 焦点唯一性 / 顶层白卡 */
    entry.focus = await page.evaluate((scr) => {
      const scrEl = document.getElementById('scr-' + scr); const big = [];
      Array.prototype.forEach.call(scrEl.querySelectorAll('img'), im => {
        const cs = getComputedStyle(im); const r = im.getBoundingClientRect();
        if (cs.display === 'none' || r.width < 1) return;
        if (r.width >= 190 && r.height >= 90) big.push({ src: im.getAttribute('src'), w: Math.round(r.width), h: Math.round(r.height) });
      });
      return { bigImgs: big };
    }, s.id);
    entry.cards = await page.evaluate((scr) => {
      const scrEl = document.getElementById('scr-' + scr); const res = [];
      Array.prototype.forEach.call(scrEl.querySelectorAll('*'), el => {
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden') return;
        if (cs.backgroundColor !== 'rgb(255, 255, 255)') return;
        const r = el.getBoundingClientRect();
        if (r.width < 100 || r.height < 36) return;
        const pc = el.parentElement ? getComputedStyle(el.parentElement).backgroundColor : '';
        if (pc === 'rgb(255, 255, 255)') return;
        res.push({ cls: String(el.className).slice(0, 46), w: Math.round(r.width), h: Math.round(r.height) });
      });
      return res;
    }, s.id);

    /* 图像上文字：文字 transparent → 采样纯背景 → 实算对比度 */
    const pix = [];
    if (overTxt.length) {
      // 隐藏文字（保留背景层）
      await page.addStyleTag({ content: '.__auditHideText, .__auditHideText * { color: transparent !important; }' });
      const groups = {};
      overTxt.forEach(t => { (groups[t.container] = groups[t.container] || []).push(t); });
      for (const cname of Object.keys(groups)) {
        await page.evaluate((cn) => {
          Array.prototype.forEach.call(document.querySelectorAll('#viewport .' + cn.split(' ').join('.')), el => { el.classList.add('__auditHideText'); });
        }, cname);
      }
      await page.waitForTimeout(120);
      for (const t of overTxt) {
        if (t.rect.w < 2 || t.rect.h < 2) continue;
        const clip = { x: t.rect.x, y: t.rect.y, width: Math.min(t.rect.w, 280), height: Math.min(t.rect.h, 60) };
        let sm; try { sm = await sampleBg(page, clip); } catch (e) { continue; }
        const fg = t.color.match(/rgba?\(([^)]+)\)/)[1].split(',').map(Number);
        pix.push({ text: t.text, cls: t.cls, fs: t.fs, color: t.color,
          bgAvg: sm.avg, bgP10dark: sm.p10dark,
          ratioVsAvg: Math.round(ratioOf(fg, sm.avg)*100)/100,
          ratioVsDarkest: Math.round(ratioOf(fg, sm.p10dark)*100)/100 });
      }
      // 还原
      await page.evaluate(() => { Array.prototype.forEach.call(document.querySelectorAll('.__auditHideText'), el => el.classList.remove('__auditHideText')); });
    }
    entry.overImageText = pix;
    report.screens[s.id] = entry;
  }

  /* ============================== 图片 / 状态 ============================== */
  report.globals.images = await page.evaluate(() => {
    const broken = []; const all = document.querySelectorAll('img');
    Array.prototype.forEach.call(all, im => { if (im.naturalWidth === 0) broken.push(im.getAttribute('src')); });
    return { total: all.length, broken: broken };
  });
  const stateBroken = [];
  for (const s of SCREENS) for (const st of STATES) {
    await gotoState(page, st); await gotoScreen(page, s.id);
    const b = await page.evaluate(() => { const a = [];
      Array.prototype.forEach.call(document.querySelectorAll('img'), im => { if (im.naturalWidth === 0) a.push(im.getAttribute('src')); }); return a; });
    if (b.length) stateBroken.push({ screen: s.id, state: st, broken: b });
  }
  report.globals.stateBrokenImages = stateBroken;
  await gotoState(page, 'normal');

  /* ============================== 折叠 ============================== */
  const fold = {};
  for (const id of ['community', 'messages', 'user', 'home']) {
    await gotoScreen(page, id);
    const snap = (sid) => page.evaluate((i) => {
      const scr = document.getElementById('scr-' + i);
      const nav = scr.querySelector('.nav'); const lt = scr.querySelector('.largetitle');
      const ct = scr.querySelector('.nav__compact-title'); const vl = scr.querySelector('.nav__veil');
      const sc = scr.querySelector('[data-scroll]');
      return { navH: nav ? Math.round(nav.getBoundingClientRect().height) : null,
        navTop: nav ? Math.round(nav.getBoundingClientRect().top) : null,
        ltO: lt ? getComputedStyle(lt).opacity : null, ctO: ct ? getComputedStyle(ct).opacity : null,
        veilO: vl ? getComputedStyle(vl).opacity : null, hairO: scr.querySelector('.nav__hairline') ? getComputedStyle(scr.querySelector('.nav__hairline')).opacity : null,
        scrollTop: sc ? Math.round(sc.scrollTop) : 0,
        vars: { p: scr.style.getPropertyValue('--p'), ltO: scr.style.getPropertyValue('--lt-o'), ctO: scr.style.getPropertyValue('--ct-o') } };
    }, id);
    const before = await snap(id);
    await page.screenshot({ path: path.join(OUT, `fold-${id}-before.png`), clip: { x: screenBox.x, y: screenBox.y, width: screenBox.width, height: screenBox.height } });
    await page.evaluate((sid) => { const sc = document.getElementById('scr-' + sid).querySelector('[data-scroll]'); if (sc) sc.scrollTop = 200; }, id);
    await page.waitForTimeout(480);
    const after = await snap(id);
    await page.screenshot({ path: path.join(OUT, `fold-${id}-after.png`), clip: { x: screenBox.x, y: screenBox.y, width: screenBox.width, height: screenBox.height } });
    fold[id] = { before, after, navJump: (before.navH !== after.navH) ? (after.navH - before.navH) : 0,
                 navTopJump: (before.navTop !== after.navTop) ? (after.navTop - before.navTop) : 0 };
    await page.evaluate((sid) => { const sc = document.getElementById('scr-' + sid).querySelector('[data-scroll]'); if (sc) sc.scrollTop = 0; }, id);
  }
  report.globals.fold = fold;

  /* ============================== 疑点专项 ============================== */
  await gotoScreen(page, 'home');
  report.suspects.banner = await page.evaluate(() => {
    const b = document.querySelector('#homeBanner');
    const R = el => { const r = el.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), right: Math.round(r.right), bottom: Math.round(r.bottom) }; };
    const t = R(b.querySelector('.banner__title')), g = R(b.querySelector('.banner__tag'));
    const d = R(b.querySelector('.banner__dots')), dr = R(b.querySelector('.banner__dot.is-on')), br = R(b);
    return { banner: br, tag: g, title: t, dots: d, activeDot: dr,
      verticalGapTitleBottomToDotTop: dr.y - t.bottom,
      horizontalOverlapTitleVsDot: (dr.right > t.x && dr.x < t.right),
      titleLeftOffset: t.x - br.x, titleBottomOffset: br.bottom - t.bottom,
      dotBottomOffset: br.bottom - dr.bottom,
      spec: '§4.8 标题底 14pt / 指示点距底 12pt / 点间距 6pt' };
  });

  report.suspects.outsideTokens = await page.evaluate(() => {
    const targets = ['#0A0908', '#14110F', '#EDE7DE', '#FBF9F6'];
    const res = {};
    Array.prototype.forEach.call(document.styleSheets[0].cssRules, rule => {
      if (!rule.style || !rule.selectorText) return;
      const txt = rule.cssText.toUpperCase();
      targets.forEach(t => { if (txt.indexOf(t) >= 0) (res[t] = res[t] || []).push(rule.selectorText + ' { ' + rule.style.cssText.slice(0, 110) + ' }'); });
    });
    return res;
  });

  await gotoScreen(page, 'community');
  report.suspects.postBody = await page.evaluate(() => Array.prototype.map.call(
    document.querySelectorAll('#scr-community .post__body'), el => {
      const cs = getComputedStyle(el); const r = el.getBoundingClientRect();
      return { text: el.textContent.trim().slice(0, 22), fs: parseFloat(cs.fontSize), fw: cs.fontWeight,
        lh: cs.lineHeight, lineHeightRatio: Math.round(parseFloat(cs.lineHeight)/parseFloat(cs.fontSize)*100)/100, w: Math.round(r.width) };
    }));

  await gotoScreen(page, 'pet');
  report.suspects.petSceneUsage = await page.evaluate(() => {
    const res = [];
    Array.prototype.forEach.call(document.querySelectorAll('img'), im => {
      const s = im.getAttribute('src') || '';
      if (s.indexOf('pet-hub-scene') >= 0 || s.indexOf('hero-pet') >= 0) {
        const cs = getComputedStyle(im); const r = im.getBoundingClientRect();
        const st = im.closest('.st');
        res.push({ src: s, state: st ? st.className.replace('st st--','') : 'n/a', display: cs.display,
          w: Math.round(r.width), h: Math.round(r.height), parent: String(im.parentElement.className).slice(0, 34) });
      }
    });
    return res;
  });

  const petSwitch = [];
  for (const key of ['buddy', 'abu', 'niangao', 'xue']) {
    const before = await page.evaluate(() => ({ hero: document.getElementById('petHero').getAttribute('src'),
      title: document.querySelector('[data-pet="title"]').textContent,
      on: document.querySelector('.hub-pet.is-on') ? document.querySelector('.hub-pet.is-on').getAttribute('data-pet') : null }));
    await page.evaluate(k => { const b = document.querySelector('.hub-pet[data-pet="' + k + '"]'); if (b) b.click(); }, key);
    await page.waitForTimeout(260);
    const after = await page.evaluate(() => ({ hero: document.getElementById('petHero').getAttribute('src'),
      title: document.querySelector('[data-pet="title"]').textContent,
      on: document.querySelector('.hub-pet.is-on') ? document.querySelector('.hub-pet.is-on').getAttribute('data-pet') : null,
      toast: document.getElementById('toast').textContent,
      careGroom: document.querySelector('[data-care-value="groom"]').textContent }));
    petSwitch.push({ key, before, after, changed: before.title !== after.title || before.hero !== after.hero });
  }
  report.suspects.petSwitch = petSwitch;

  await gotoScreen(page, 'user');
  report.suspects.userPlaceholders = await page.evaluate(() => {
    const p = document.querySelector('#scr-user .member__points'); const cs = p ? getComputedStyle(p) : null;
    return { memberPointsText: p ? p.textContent.trim() : null, fs: cs ? parseFloat(cs.fontSize) : null,
      fw: cs ? cs.fontWeight : null, color: cs ? cs.color : null,
      quadNums: Array.prototype.map.call(document.querySelectorAll('#scr-user .quad-item__num'), e => e.textContent.trim()) };
  });

  report.suspects.cssGaps = await page.evaluate(() => {
    const res = {};
    const k = document.querySelector('.hub-panel__kicker');
    if (k) { const cs = getComputedStyle(k); res.kicker = { color: cs.color, fs: parseFloat(cs.fontSize), text: k.textContent.trim(), spec: '§4.10.2 规定 rgba(255,255,255,.85)' }; }
    const n = document.querySelector('.prod__num');
    if (n) { const cs = getComputedStyle(n); res.prodNum = { fs: parseFloat(cs.fontSize), fw: cs.fontWeight, color: cs.color, text: n.textContent, spec: '§4.7 评分值 12pt/500' }; }
    const d = document.querySelector('.prod__dot');
    if (d) { const cs = getComputedStyle(d); res.prodDot = { fs: parseFloat(cs.fontSize), color: cs.color, spec: '§4.7 分隔点 12pt tertiary' }; }
    const st = document.querySelector('.prod__stars');
    if (st) { const cs = getComputedStyle(st); res.prodStars = { color: cs.color, fs: parseFloat(cs.fontSize), spec: '§4.7 星 12pt --color-primary 填充（自述替代方案 --ink-gold）' }; }
    res.scrimLocalExists = !!document.querySelector('.hub-hero__scrim-local');
    res.scrimLocalHasRule = (() => { try { return Array.prototype.some.call(document.styleSheets[0].cssRules, r => r.selectorText === '.hub-hero__scrim-local'); } catch (e) { return null; } })();
    res.deadOrUnstyled = ['prod__none','prod__num','prod__dot','hub-hero__scrim-local'].map(c => {
      const rule = (() => { try { return Array.prototype.some.call(document.styleSheets[0].cssRules, r => r.selectorText && r.selectorText.indexOf(c) >= 0); } catch (e) { return null; } })();
      const used = Array.prototype.some.call(document.querySelectorAll('.' + c), e => e.getClientRects().length > 0);
      return { cls: c, hasCssRule: rule, renderedInUI: used };
    });
    return res;
  });

  /* 字号阶梯 / 圆角 / 页边距 */
  report.globals.typography = await page.evaluate((screens) => {
    const ladder = [34,28,22,20,17,16,15,13,12,11]; const bad = [], seen = new Set();
    screens.forEach(sid => {
      const scr = document.getElementById('scr-' + sid); if (!scr) return;
      Array.prototype.forEach.call(scr.querySelectorAll('*'), el => {
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden') return;
        const hasText = Array.prototype.some.call(el.childNodes, n => n.nodeType === 3 && n.nodeValue.trim());
        if (!hasText) return;
        const fs = Math.round(parseFloat(cs.fontSize)*100)/100;
        const k = sid+'|'+fs+'|'+el.className;
        if (!seen.has(k)) { seen.add(k);
          if (!ladder.includes(fs)) bad.push({ screen: sid, fs, cls: String(el.className).slice(0,44), text: el.textContent.trim().slice(0,18) });
          if (fs < 11) bad.push({ screen: sid, fs, min: true, cls: String(el.className).slice(0,44) });
        }
      });
    });
    return { offLadder: bad };
  }, SCREENS.map(s => s.id));

  report.globals.radii = await page.evaluate(() => {
    const vals = {};
    Array.prototype.forEach.call(document.styleSheets[0].cssRules, r => {
      if (!r.style || !r.style.borderRadius) return;
      r.style.borderRadius.split(' ').filter(Boolean).forEach(x => { vals[x] = (vals[x]||0)+1; });
    });
    return vals;
  });

  report.globals.pageMargin = await page.evaluate((screens) => {
    const res = {};
    screens.forEach(sid => {
      const scr = document.getElementById('scr-' + sid);
      const dev = document.getElementById('deviceScreen').getBoundingClientRect(); const bad = [];
      Array.prototype.forEach.call(scr.querySelectorAll('.screen__content > *'), el => {
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden') return;
        if (el.classList.contains('st')) return;               // 状态容器本身非内容块
        const r = el.getBoundingClientRect();
        if (r.width < 40) return;
        const ml = parseFloat(cs.marginLeft), mr = parseFloat(cs.marginRight);
        const dl = Math.round(r.left - dev.left), dr = Math.round(dev.right - r.right);
        if (Math.abs(ml-16) > 0.6 || Math.abs(mr-16) > 0.6 || dl < 15.5 || dr < 15.5) {
          bad.push({ cls: String(el.className).slice(0,44), ml, mr, leftOffset: dl, rightOffset: dr });
        }
      });
      res[sid] = bad;
    });
    return res;
  }, SCREENS.map(s => s.id));

  report.globals.goldText = await page.evaluate(() => {
    const hits = [];
    Array.prototype.forEach.call(document.querySelectorAll('*'), el => {
      const cs = getComputedStyle(el);
      if (cs.color === 'rgb(219, 201, 138)') hits.push({ tag: el.tagName, cls: String(el.className).slice(0,44), text: (el.textContent||'').trim().slice(0,18) });
    });
    return hits;
  });

  /* 危险色 / 蓝色光晕 残留 */
  report.globals.residual = await page.evaluate(() => {
    const out = { blueShadow: [], blueGradient: [], redFF4D4F: [], heavyShadow: [] };
    Array.prototype.forEach.call(document.querySelectorAll('*'), el => {
      const cs = getComputedStyle(el);
      const sh = cs.boxShadow || '';
      if (/rgba?\(\s*0\s*,\s*1[0-9]{2}\s*,\s*2[0-9]{2}/.test(sh) || /rgba?\(\s*0\s*,\s*122\s*,\s*255/.test(sh)) out.blueShadow.push({ cls: String(el.className).slice(0,40), sh: sh.slice(0,70) });
      const bg = cs.backgroundImage || '';
      if (/#0(07aff|04faf)/i.test(bg) || /rgba?\(\s*0\s*,\s*122\s*,\s*255/.test(bg)) out.blueGradient.push({ cls: String(el.className).slice(0,40) });
      if (/#ff4d4f/i.test(bg + sh + cs.color)) out.redFF4D4F.push({ cls: String(el.className).slice(0,40) });
      const m = sh.match(/rgba?\(([^)]+)\)/g) || [];
      if (m.length >= 3) out.heavyShadow.push({ cls: String(el.className).slice(0,40), sh: sh.slice(0,90) });
    });
    return out;
  });

  report.globals.consoleErrors = consoleErrors;
  report.globals.pageErrors = pageErrors;
  report.globals.failedRequests = failedRequests;

  await browser.close();
  fs.writeFileSync(path.join(OUT, 'audit.json'), JSON.stringify(report, null, 2), 'utf8');

  /* ------------------------------- 摘要 ------------------------------- */
  const L = [];
  L.push('=== MOYUYO 原型独立审查 v3 · 实测摘要 ===');
  L.push('[静态] :hover=' + report.globals.static.hoverRuleCount +
    ' | 禁用色值=' + JSON.stringify(report.globals.static.forbiddenHex) +
    ' | emoji=' + report.globals.static.emojiInHtml.length +
    ' | 真正的 color:var(--color-primary)=' + report.globals.static.colorPrimaryAsTextProp);
  L.push('[令牌] §3.8 解析出 ' + report.globals.static.dsTokenCount + ' 个 / 原型声明 ' + report.globals.static.protoTokenCount +
    ' 个 | §3.8 缺失于原型: ' + JSON.stringify(report.globals.static.dsTokensMissingInProto) +
    ' | 原型额外(非评审面板/非JS): ' + JSON.stringify(report.globals.static.protoTokensNotInDS));
  L.push('[未定义 var] ' + JSON.stringify(report.globals.static.undefinedVars));
  L.push('[构建师自检 JSON] ' + JSON.stringify(report.globals.static.buildSelfcheckCounts));
  L.push('[图片] 总数 ' + report.globals.images.total + ' 断链 ' + JSON.stringify(report.globals.images.broken) + ' | 各态断链 ' + JSON.stringify(report.globals.stateBrokenImages));
  L.push('[运行时] 控制台错误 ' + consoleErrors.length + ' | 页面异常 ' + pageErrors.length + ' | 失败请求 ' + failedRequests.length);
  L.push('[字号阶梯越界] ' + JSON.stringify(report.globals.typography.offLadder));
  L.push('[圆角] ' + JSON.stringify(report.globals.radii));
  L.push('[页边距≠16 的内容块] ' + JSON.stringify(report.globals.pageMargin));
  L.push('[金色文字实况] ' + JSON.stringify(report.globals.goldText));
  L.push('[残留扫描] ' + JSON.stringify(report.globals.residual));
  L.push('');
  for (const s of SCREENS) {
    const e = report.screens[s.id];
    const badT = e.text.filter(t => t.ratio < 4.5);
    const badI = e.icons.filter(t => t.ratio < 3);
    const badP = e.overImageText.filter(t => t.ratioVsAvg < 4.5 || t.ratioVsDarkest < 4.5);
    L.push('[' + s.label + '] 文本<4.5: ' + badT.length + '/' + e.text.length +
      ' | 图标<3: ' + badI.length + '/' + e.icons.length +
      ' | 图上文字: ' + e.overImageText.length + ' (不达标 ' + badP.length + ')' +
      ' | 命中区失败: ' + e.hit.fail.length + '/' + e.hit.total +
      ' | 顶层白卡: ' + e.cards.length +
      ' | 大图块: ' + e.focus.bigImgs.length + ' ' + JSON.stringify(e.focus.bigImgs.map(b => b.src)));
    badT.forEach(t => L.push('    TXT ' + t.ratio + ':1  ' + t.fs + 'px/' + t.fw + '  "' + t.text + '"  .' + t.cls + '  fg=' + t.color + ' bg=' + t.bg));
    badI.forEach(t => L.push('    ICO ' + t.ratio + ':1  ' + t.size + 'px  .' + t.cls + '  fg=' + t.color + ' bg=' + t.bg));
    e.overImageText.forEach(t => L.push('    PIX "' + t.text + '" ' + t.fs + 'px fg=' + t.color +
      ' | 背景均 ' + JSON.stringify(t.bgAvg) + ' → ' + t.ratioVsAvg + ':1 | 背景最暗10% ' + JSON.stringify(t.bgP10dark) + ' → ' + t.ratioVsDarkest + ':1  .' + t.cls));
    e.hit.fail.forEach(h => L.push('    HIT ' + h.why + '  visual ' + h.visualW + 'x' + h.visualH + '  .' + h.cls));
  }
  L.push('');
  L.push('[折叠] ' + JSON.stringify(Object.fromEntries(Object.entries(report.globals.fold).map(([k, v]) =>
    [k, 'navΔh' + v.navJump + ' navΔtop' + v.navTopJump + ' | lt ' + v.before.ltO + '→' + v.after.ltO +
     ' | ct ' + v.before.ctO + '→' + v.after.ctO + ' | veil ' + v.before.veilO + '→' + v.after.veilO +
     ' | p=' + v.after.vars.p + ' ltO=' + v.after.vars.ltO + ' ctO=' + v.after.vars.ctO]))));
  L.push('[Banner] ' + JSON.stringify(report.suspects.banner));
  L.push('[非§3.8色值] ' + JSON.stringify(report.suspects.outsideTokens));
  L.push('[帖子正文] ' + JSON.stringify(report.suspects.postBody));
  L.push('[宠物切换] ' + JSON.stringify(report.suspects.petSwitch.map(p => p.key + ' changed=' + p.changed + ' hero=' + p.after.hero + ' title=' + p.after.title + ' toast="' + p.after.toast + '"')));
  L.push('[pet-hub-scene/hero-pet 使用] ' + JSON.stringify(report.suspects.petSceneUsage));
  L.push('[我的占位] ' + JSON.stringify(report.suspects.userPlaceholders));
  L.push('[CSS 缺口] ' + JSON.stringify(report.suspects.cssGaps));

  const out = L.join('\n');
  console.log(out);
  fs.writeFileSync(path.join(OUT, 'summary.txt'), out, 'utf8');
})().catch(e => { console.error('AUDIT FAILED:', e); process.exit(1); });
