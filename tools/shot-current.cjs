const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = 'http://192.168.64.103:5174';
const OUT = path.resolve(__dirname, 'shots');
const PAGES = [
  ['home', '/pages/tabbar/home'],
  ['community', '/pages/tabbar/community'],
  ['messages', '/pages/tabbar/messages'],
  ['user', '/pages/tabbar/user'],
];

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const ctx = await browser.newContext({
    viewport: { width: 393, height: 852 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
  });
  const page = await ctx.newPage();
  for (const [name, route] of PAGES) {
    try {
      await page.goto(BASE + route, { waitUntil: 'networkidle', timeout: 30000 });
      await page.waitForTimeout(2500);
      await page.screenshot({ path: path.join(OUT, name + '.png'), fullPage: false });
      await page.screenshot({ path: path.join(OUT, name + '-full.png'), fullPage: true });
      console.log('OK', name);
    } catch (e) {
      console.log('FAIL', name, e.message);
    }
  }
  await browser.close();
})();
