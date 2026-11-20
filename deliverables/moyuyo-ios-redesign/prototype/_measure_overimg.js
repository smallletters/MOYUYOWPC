/**
 * MOYUYO 原型 · 图上文字对比度实测（像素级）
 * ---------------------------------------------------------------------------
 * 口径：把目标文字设为 transparent（保留背景层），对文字矩形逐像素采样，
 *       用「背景均」与「最暗 10%」两个口径分别实算白字对比度。
 * 用途：为 README §5.1 提供实测数值（非推算），并作为交付前的最后一道对比度闸门。
 *
 * 运行： node _measure_overimg.js
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = __dirname;
const PAGE = 'file://' + path.join(ROOT, 'index.html').replace(/\\/g, '/');
const SCREENS = ['pet', 'home', 'community', 'messages', 'user'];

function lumOf(c) {
  const ch = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
  return 0.2126 * ch(c[0]) + 0.7152 * ch(c[1]) + 0.0722 * ch(c[2]);
}
function ratioOf(a, b) {
  const L1 = lumOf(a), L2 = lumOf(b);
  return (Math.max(L1, L2) + 0.05) / (Math.min(L1, L2) + 0.05);
}

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1180 }, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  await page.goto(PAGE, { waitUntil: 'load' });
  await page.waitForTimeout(700);

  const report = { measuredAt: new Date().toISOString(), screens: {}, worstOverall: null, failCount: 0 };

  for (const sid of SCREENS) {
    await page.click(`.tabbar__item[data-tab="${sid}"]`);
    await page.waitForTimeout(400);

    // 找出「祖先链含 img」且文字自身不在 img 内的元素
    const targets = await page.evaluate((scr) => {
      const scrEl = document.getElementById('scr-' + scr);
      const out = [], seen = new Set();
      const w = document.createTreeWalker(scrEl, NodeFilter.SHOW_TEXT);
      let n;
      while ((n = w.nextNode())) {
        const t = (n.nodeValue || '').trim(); if (!t) continue;
        const el = n.parentElement; if (!el || seen.has(el)) continue; seen.add(el);
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) continue;
        const r = el.getBoundingClientRect();
        if (r.width < 2 || r.height < 2) continue;
        // 祖先链含 img
        let p = el.parentElement, hasImg = false, imgRect = null;
        while (p && p.nodeType === 1) {
          if (p.tagName === 'IMG') { hasImg = true; imgRect = p.getBoundingClientRect(); break; }
          p = p.parentElement;
        }
        if (!hasImg) continue;
        // 文字矩形与图片矩形有实际交叠 → 才是真正的「图上文字」
        const ix = Math.max(0, Math.min(r.right, imgRect.right) - Math.max(r.left, imgRect.left));
        const iy = Math.max(0, Math.min(r.bottom, imgRect.bottom) - Math.max(r.top, imgRect.top));
        if (ix * iy <= 0) continue;
        out.push({ text: t.slice(0, 20), cls: String(el.className).slice(0, 44),
          color: cs.color, rect: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) } });
      }
      return out;
    }, sid);

    // 隐藏文字 → 采样纯背景
    await page.evaluate((sid) => {
      document.querySelectorAll('#scr-' + sid + ' *').forEach((el) => {
        if (el.tagName === 'IMG') return;
        el.style.setProperty('color', 'transparent', 'important');
      });
    }, sid);
    await page.waitForTimeout(150);

    const items = [];
    for (const t of targets) {
      const clip = { x: t.rect.x, y: t.rect.y, width: Math.min(t.rect.w, 300), height: Math.min(t.rect.h, 60) };
      let sm;
      try {
        const buf = await page.screenshot({ clip });
        sm = await page.evaluate(async (data) => {
          const img = new Image();
          await new Promise((res, rej) => { img.onload = res; img.onerror = rej; img.src = 'data:image/png;base64,' + data; });
          const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
          const ctx2 = c.getContext('2d'); ctx2.drawImage(img, 0, 0);
          const d = ctx2.getImageData(0, 0, img.width, img.height).data;
          const px = []; let r = 0, g = 0, b = 0;
          for (let i = 0; i < d.length; i += 4) { r += d[i]; g += d[i + 1]; b += d[i + 2]; px.push([d[i], d[i + 1], d[i + 2]]); }
          px.sort((a, b) => (a[0] + a[1] + a[2]) - (b[0] + b[1] + b[2]));
          const k = Math.max(1, Math.floor(px.length * 0.1));
          let dr = 0, dg = 0, db = 0;
          for (let i = 0; i < k; i++) { dr += px[i][0]; dg += px[i][1]; db += px[i][2]; }
          const n = px.length;
          return { avg: [Math.round(r / n), Math.round(g / n), Math.round(b / n)],
                   p10dark: [Math.round(dr / k), Math.round(dg / k), Math.round(db / k)] };
        }, buf.toString('base64'));
      } catch (e) { continue; }
      const fg = t.color.match(/rgba?\(([^)]+)\)/)[1].split(',').map(Number);
      const cAvg = ratioOf(fg, sm.avg), cDark = ratioOf(fg, sm.p10dark);
      items.push({ ...t, avg: sm.avg, p10dark: sm.p10dark, ratioVsAvg: +cAvg.toFixed(2), ratioVsDarkest: +cDark.toFixed(2) });
    }
    await page.evaluate((sid) => {
      document.querySelectorAll('#scr-' + sid + ' *').forEach((el) => el.style.removeProperty('color'));
    }, sid);

    const bad = items.filter((t) => t.ratioVsAvg < 4.5 || t.ratioVsDarkest < 4.5);
    const worst = items.slice().sort((a, b) => Math.min(a.ratioVsAvg, a.ratioVsDarkest) - Math.min(b.ratioVsAvg, b.ratioVsDarkest))[0] || null;
    report.screens[sid] = { total: items.length, fail: bad.length, worst, bad };
    report.failCount += bad.length;
    if (worst && (!report.worstOverall || Math.min(worst.ratioVsAvg, worst.ratioVsDarkest) < Math.min(report.worstOverall.ratioVsAvg, report.worstOverall.ratioVsDarkest))) {
      report.worstOverall = Object.assign({ screen: sid }, worst);
    }
    console.log(`[${sid}] 图上文字 ${items.length} 处，不达标 ${bad.length} 处` +
      (worst ? `  最差 ${worst.cls} 均${worst.ratioVsAvg}:1 暗${worst.ratioVsDarkest}:1` : ''));
    bad.forEach((b) => console.log(`   ✗ .${b.cls} "${b.text}" 均${b.ratioVsAvg}:1 暗${b.ratioVsDarkest}:1`));
  }

  report.passed = report.failCount === 0;
  fs.writeFileSync(path.join(ROOT, '_shots', 'overimg-contrast.json'), JSON.stringify(report, null, 2), 'utf8');
  console.log('\n=== 图上文字对比度实测 ===');
  console.log(report.passed ? '  ✅ 全部 ≥ 4.5:1' : `  ❌ 仍有 ${report.failCount} 处不达标`);
  await browser.close();
  process.exit(report.passed ? 0 : 1);
})();
