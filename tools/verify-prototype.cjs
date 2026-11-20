/**
 * 原型验证脚本 —— 供 Phase 4 质量审查（critique-reviewer）使用
 * 用法：node tools/verify-prototype.cjs <原型index.html路径> <输出目录>
 * 输出：每屏截图 + 断链图片检查 + 控制台错误 + 关键 DOM 结构断言 + 文字色/对比度粗扫
 */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const FILE = path.resolve(process.argv[2] || 'deliverables/moyuyo-ios-redesign/prototype/index.html');
const OUT = path.resolve(process.argv[3] || 'tools/verify-out');
const URL = 'file:///' + FILE.replace(/\\/g, '/');

const TABS = [
  ['pet', '宠物'],
  ['home', '商城'],
  ['community', '社区'],
  ['messages', '消息'],
  ['user', '我的'],
];

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const ctx = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    deviceScaleFactor: 1,
  });
  const page = await ctx.newPage();

  const consoleErrors = [];
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', e => consoleErrors.push('PAGEERROR: ' + e.message));

  const failedReqs = [];
  page.on('requestfailed', r => failedReqs.push(r.url() + ' :: ' + (r.failure() || {}).errorText));
  page.on('response', r => { if (r.status() >= 400) failedReqs.push(r.status() + ' ' + r.url()); });

  await page.goto(URL, { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(OUT, '00-full-page.png'), fullPage: false });

  const results = { screens: {}, consoleErrors, failedReqs, assertions: {} };

  for (const [slug, label] of TABS) {
    const loc = page.locator(`text="${label}"`).last();
    try {
      await loc.click({ timeout: 5000 });
      await page.waitForTimeout(1000);
      await page.screenshot({ path: path.join(OUT, `screen-${slug}.png`) });
      // 设备框内单独截图
      const frame = page.locator('.phone-frame, .device, .iphone, [class*="device"]').first();
      if (await frame.count()) {
        await frame.screenshot({ path: path.join(OUT, `frame-${slug}.png`) }).catch(() => {});
      }
      results.screens[slug] = 'OK';
    } catch (e) {
      results.screens[slug] = 'FAIL: ' + e.message.split('\n')[0];
    }
  }

  // 断链图片
  results.assertions.brokenImages = await page.evaluate(() => {
    const bad = [];
    document.querySelectorAll('img').forEach(im => {
      if (!im.complete || im.naturalWidth === 0) bad.push(im.getAttribute('src'));
    });
    return bad;
  });

  // 结构断言
  results.assertions.counts = await page.evaluate(() => ({
    total: document.querySelectorAll('*').length,
    svgIcons: document.querySelectorAll('svg').length,
    emoji: (document.body.innerText.match(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/gu) || []).slice(0, 10),
    hoverRules: (() => {
      let n = 0;
      for (const ss of document.styleSheets) {
        try { for (const r of ss.cssRules) if (r.selectorText && r.selectorText.includes(':hover')) n++; } catch (e) {}
      }
      return n;
    })(),
    hexColors: (() => {
      const set = new Set();
      const walk = (rules) => { for (const r of rules) {
        if (r.cssRules) { try { walk(r.cssRules); } catch (e) {} continue; }
        if (!r.style) continue;
        for (const p of r.style) {
          const m = String(r.style.getPropertyValue(p)).match(/#[0-9a-fA-F]{6}\b/g);
          if (m) m.forEach(x => set.add(x.toUpperCase()));
        }
      }};
      for (const ss of document.styleSheets) { try { walk(ss.cssRules); } catch (e) {} }
      return [...set].sort();
    })(),
  }));

  // iOS 蓝 / 紫色 / emoji 残留扫描
  results.assertions.brandViolations = await page.evaluate(() => {
    const banned = ['#007AFF', '#007aff', '#4C6EF5', '#4c6ef5', '#5856D6', '#5B5BD6', '#6366F1', '#6366f1'];
    const hits = [];
    for (const ss of document.styleSheets) {
      try {
        for (const r of ss.cssRules) {
          const t = r.cssText || '';
          for (const b of banned) if (t.toUpperCase().includes(b)) hits.push(b + ' @ ' + (r.selectorText || '').slice(0, 60));
        }
      } catch (e) {}
    }
    return hits;
  });

  fs.writeFileSync(path.join(OUT, 'verify-report.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify(results, null, 2));
  await browser.close();
})();
