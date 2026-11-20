/* Phase 5 交付截图脚本 — MOYUYO v6
   大视口(1400x1000)使 fitDevice() scale=1，.screen 原生渲染为 393x852 CSS px。
   用 clip 精确截取 .screen 的 boundingBox（不含手机外壳/展示台/stage-note），
   deviceScaleFactor=1 直接输出精确 393x852 PNG。
   浅色主题：显式移除 .theme-dark。
*/
const { chromium } = require('D:/MOYUYOWPC/deliverables/ui-prototype/redesign-v5/node_modules/playwright-core');
const path = require('path');
const fs = require('fs');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const DIR = __dirname;
const OUT = path.join(DIR, 'exports');
const PAGES = [
  { tab: 'pet',       file: '01-宠物Hub.png' },
  { tab: 'home',      file: '02-商城.png' },
  { tab: 'community', file: '03-社区.png' },
  { tab: 'messages',  file: '04-消息.png' },
  { tab: 'user',      file: '05-我的.png' },
];

function pngSize(buf) {
  return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({
    executablePath: EDGE, headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  });
  const context = await browser.newContext({
    viewport: { width: 1400, height: 1000 },
    deviceScaleFactor: 1
  });
  const page = await context.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('PAGEERROR: ' + e.message));

  // 捕获所有图片加载失败（含 onerror 移除前的捕获阶段）
  await page.addInitScript(() => {
    window.__broken = [];
    document.addEventListener('error', e => {
      if (e.target && e.target.tagName === 'IMG') window.__broken.push(e.target.getAttribute('src'));
    }, true);
  });

  const url = 'file:///' + path.join(DIR, 'index.html').replace(/\\/g, '/');
  await page.goto(url, { waitUntil: 'load' });

  // 禁用动画/过渡，避免 180ms 淡入造成半透明截图；关闭任意可能的深色主题
  await page.addStyleTag({ content: '*{animation:none !important;transition:none !important}' });
  await page.evaluate(() => document.documentElement.classList.remove('theme-dark'));

  // 等图片加载，但设 3s 上限防止懒加载图永不触发 load/error 造成挂起
  await page.evaluate(() => Promise.race([
    Promise.all(Array.from(document.images).map(img => {
      if (img.complete) return Promise.resolve();
      return new Promise(res => { img.onload = img.onerror = res; });
    })),
    new Promise(res => setTimeout(res, 3000))
  ]));
  await page.waitForTimeout(400);

  // 验证 scale=1 与 .screen 原生尺寸
  const diag = await page.evaluate(() => {
    const el = document.getElementById('screen');
    const r = el.getBoundingClientRect();
    const wrap = document.getElementById('deviceWrap');
    return {
      transform: getComputedStyle(wrap).transform,
      innerWidth: window.innerWidth, innerHeight: window.innerHeight,
      screenW: r.width, screenH: r.height
    };
  });
  console.log('DIAG', JSON.stringify(diag));

  const results = [];
  for (let i = 0; i < PAGES.length; i++) {
    const name = PAGES[i].tab;
    await page.click('.tab[data-tab="' + name + '"]');
    // 所有 scroll 容器归顶
    await page.evaluate(n => {
      document.querySelectorAll('.scroll').forEach(s => { s.scrollTop = 0; });
      const el = document.getElementById('scroll-' + n);
      if (el) el.scrollTop = 0;
    }, name);
    await page.waitForTimeout(300); // 等淡入/重排稳定

    const box = await page.locator('.screen').boundingBox();
    const out = path.join(OUT, PAGES[i].file);
    // dSF=1：clip 使用 CSS 像素坐标，输出精确等于 box 宽高的 PNG
    await page.screenshot({ path: out, clip: {
      x: Math.round(box.x), y: Math.round(box.y),
      width: Math.round(box.width), height: Math.round(box.height)
    }});
    const sz = pngSize(fs.readFileSync(out));
    results.push({ file: PAGES[i].file, w: sz.w, h: sz.h, bytes: fs.statSync(out).size });
    console.log('WROTE', PAGES[i].file, sz.w + 'x' + sz.h, fs.statSync(out).size + ' bytes');
  }

  const broken = await page.evaluate(() => window.__broken || []);
  const brokenNow = await page.evaluate(() =>
    Array.from(document.images).filter(i => i.complete && i.naturalWidth === 0).map(i => i.getAttribute('src'))
  );
  console.log('BROKEN (captured):', broken.length ? [...new Set(broken)].join(', ') : 'none');
  console.log('BROKEN (in DOM):', brokenNow.length ? brokenNow.join(', ') : 'none');
  console.log('CONSOLE ERRORS:', errors.length ? [...new Set(errors)].slice(0, 10).join(' | ') : 'none');
  console.log('RESULT_JSON=' + JSON.stringify(results));
  console.log('DONE');
  await browser.close();
})().catch(e => { console.error('FATAL', e); process.exit(1); });
