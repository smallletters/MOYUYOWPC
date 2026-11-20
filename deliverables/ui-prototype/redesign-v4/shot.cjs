/* Phase 3 screenshot script — playwright-core + local Edge
   Usage: node shot.cjs  */
const { chromium } = require('playwright-core');
const path = require('path');
const fs = require('fs');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const DIR = __dirname;
const SCREENS = path.join(DIR, 'screens');
const PAGES = ['pet','home','community','messages','user'];

(async () => {
  fs.mkdirSync(SCREENS, { recursive: true });
  const browser = await chromium.launch({
    executablePath: EDGE,
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  });
  const context = await browser.newContext({
    viewport: { width: 393, height: 852 },
    deviceScaleFactor: 2
  });
  const page = await context.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });

  const url = 'file:///' + path.join(DIR, 'index.html').replace(/\\/g, '/');
  await page.goto(url, { waitUntil: 'load' });

  // wait for all images (hidden pages included) to settle
  await page.evaluate(async () => {
    await Promise.all(Array.from(document.images).map(img => {
      if (img.complete) return Promise.resolve();
      return new Promise(res => { img.onload = img.onerror = res; });
    }));
  });

  // report broken images
  const broken = await page.evaluate(() =>
    Array.from(document.images).filter(i => !i.complete || i.naturalWidth === 0).map(i => i.getAttribute('src'))
  );
  console.log('BROKEN IMAGES:', broken.length ? broken.join(', ') : 'none');

  for (let i = 0; i < PAGES.length; i++) {
    const name = PAGES[i];
    await page.evaluate(n => window.switchTab(n), name);
    await page.waitForTimeout(450);
    const out = path.join(SCREENS, String(i + 1).padStart(2,'0') + '-' + name + '.png');
    await page.locator('.screen').screenshot({ path: out });
    const size = fs.statSync(out).size;
    console.log('WROTE', out, size + ' bytes');
  }

  await browser.close();
  if (errors.length) console.log('CONSOLE ERRORS:', errors.slice(0,10).join(' | '));
  console.log('DONE');
})().catch(e => { console.error('FATAL', e); process.exit(1); });
