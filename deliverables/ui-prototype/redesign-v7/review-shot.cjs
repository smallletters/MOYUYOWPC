/* Phase 4 审查用截图脚本（审查官临时文件，非交付物）
   MOYUYO v6 — 逐页截图 + 破图/控制台错误检测 */
const { chromium } = require('D:/MOYUYOWPC/deliverables/ui-prototype/redesign-v5/node_modules/playwright-core');
const path = require('path');
const fs = require('fs');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const DIR = __dirname;
const OUT = path.join(DIR, 'review-shots');
const PAGES = ['pet','home','community','messages','user'];

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: EDGE, headless: true, args: ['--no-sandbox','--disable-dev-shm-usage'] });
  // 大视口使 fitDevice() scale=1，保证 .screen 截图为原生 393x852 CSS px
  const context = await browser.newContext({ viewport: { width: 1400, height: 1000 }, deviceScaleFactor: 2 });
  const page = await context.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('PAGEERROR: ' + e.message));

  // 捕获所有图片加载失败（含被 onerror 移除前的捕获阶段）
  await page.addInitScript(() => {
    window.__broken = [];
    document.addEventListener('error', e => {
      if (e.target && e.target.tagName === 'IMG') window.__broken.push(e.target.getAttribute('src'));
    }, true);
  });

  const url = 'file:///' + path.join(DIR, 'index.html').replace(/\\/g, '/');
  await page.goto(url, { waitUntil: 'load' });
  await page.addStyleTag({ content: '*{animation:none !important;transition:none !important}' });
  await page.evaluate(() => document.documentElement.classList.remove('theme-dark'));

  // 等隐藏页懒加载图尽量完成
  await page.waitForTimeout(600);

  for (let i = 0; i < PAGES.length; i++) {
    const name = PAGES[i];
    await page.click('.tab[data-tab="' + name + '"]');
    await page.evaluate(n => { const s = document.getElementById('scroll-' + n); if (s) s.scrollTop = 0; }, name);
    await page.waitForTimeout(180);
    const out = path.join(OUT, String(i+1).padStart(2,'0') + '-' + name + '-light.png');
    await page.locator('.screen').screenshot({ path: out });
    console.log('WROTE', path.basename(out), fs.statSync(out).size + ' bytes');
  }

  // 深色模式：我的页 + 宠物Hub + 商城（验证换肤覆盖）
  await page.click('.tab[data-tab="user"]');
  await page.click('#themeBtn');
  await page.waitForTimeout(200);
  let out = path.join(OUT, '06-user-dark.png');
  await page.locator('.screen').screenshot({ path: out });
  console.log('WROTE', path.basename(out), fs.statSync(out).size + ' bytes');

  await page.click('.tab[data-tab="pet"]');
  await page.waitForTimeout(200);
  out = path.join(OUT, '07-pet-dark.png');
  await page.locator('.screen').screenshot({ path: out });
  console.log('WROTE', path.basename(out), fs.statSync(out).size + ' bytes');

  await page.click('.tab[data-tab="home"]');
  await page.waitForTimeout(200);
  out = path.join(OUT, '08-home-dark.png');
  await page.locator('.screen').screenshot({ path: out });
  console.log('WROTE', path.basename(out), fs.statSync(out).size + ' bytes');

  const broken = await page.evaluate(() => window.__broken || []);
  const brokenNow = await page.evaluate(() =>
    Array.from(document.images).filter(i => i.complete && i.naturalWidth === 0).map(i => i.getAttribute('src'))
  );
  console.log('BROKEN IMAGES (captured):', broken.length ? [...new Set(broken)].join(', ') : 'none');
  console.log('BROKEN IMAGES (in DOM):', brokenNow.length ? brokenNow.join(', ') : 'none');
  console.log('CONSOLE ERRORS:', errors.length ? [...new Set(errors)].slice(0,10).join(' | ') : 'none');
  console.log('DONE');
  await browser.close();
})().catch(e => { console.error('FATAL', e); process.exit(1); });
