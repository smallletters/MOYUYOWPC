/* 生成放大裁切图，用于人工核对 Hero 浮层 / Banner 压字 */
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1500, height: 1180 }, deviceScaleFactor: 4 });
  await p.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/'));
  await p.waitForTimeout(700);
  const OUT = path.join(__dirname, '_audit_shots');

  await p.evaluate(() => { [...document.querySelectorAll('#screenChips .chip')].find(x => x.getAttribute('data-screen') === 'pet').click(); });
  await p.waitForTimeout(700);
  let r = await p.evaluate(() => {
    const R = el => { const x = el.getBoundingClientRect(); return { x: x.x, y: x.y, w: x.width, h: x.height }; };
    return { panel: R(document.querySelector('#scr-pet .hub-panel')), pills: R(document.querySelector('#scr-pet .hub-pills')) };
  });
  await p.screenshot({ path: path.join(OUT, 'zoom-hero-panel.png'), clip: { x: r.panel.x - 6, y: r.panel.y - 6, width: r.panel.w + 12, height: r.panel.h + 12 } });
  await p.screenshot({ path: path.join(OUT, 'zoom-hero-pills.png'), clip: { x: r.pills.x - 6, y: r.pills.y - 6, width: r.pills.w + 12, height: r.pills.h + 12 } });

  await p.evaluate(() => { [...document.querySelectorAll('#screenChips .chip')].find(x => x.getAttribute('data-screen') === 'home').click(); });
  await p.waitForTimeout(700);
  const b2 = await p.evaluate(() => {
    const R = el => { const x = el.getBoundingClientRect(); return { x: x.x, y: x.y, w: x.width, h: x.height }; };
    const t = R(document.querySelector('#homeBanner .banner__slide.is-on .banner__text'));
    const d = R(document.querySelector('#homeBanner .banner__dots'));
    return { t, d, banner: R(document.querySelector('#homeBanner')) };
  });
  await p.screenshot({ path: path.join(OUT, 'zoom-banner-text.png'), clip: { x: b2.t.x - 4, y: b2.t.y - 10, width: Math.min(330, b2.t.w + 8), height: b2.t.h + 26 } });
  await p.screenshot({ path: path.join(OUT, 'zoom-banner-full.png'), clip: { x: b2.banner.x, y: b2.banner.y + 120, width: b2.banner.w, height: 83 } });

  console.log('done');
  await b.close();
})();
