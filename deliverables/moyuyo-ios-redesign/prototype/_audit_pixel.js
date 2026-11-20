/* 严格最差情况像素对比度：对压在图上的白字，按文字包围盒网格采样，取最小值 */
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1500, height: 1180 }, deviceScaleFactor: 1 });
  await p.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/'));
  await p.waitForTimeout(600);

  const lum = c => { const ch = v => { v /= 255; return v <= 0.03928 ? v/12.92 : Math.pow((v+0.055)/1.055,2.4); };
    return 0.2126*ch(c[0]) + 0.7152*ch(c[1]) + 0.0722*ch(c[2]); };
  const ratio = (a, c) => { const L1 = lum(a), L2 = lum(c); const hi = Math.max(L1,L2), lo = Math.min(L1,L2); return +((hi+0.05)/(lo+0.05)).toFixed(2); };

  // 隐藏文字后，对指定区域网格采样
  async function worstCase(clip, grid, fg) {
    const buf = await p.screenshot({ clip });
    return p.evaluate(async (a) => {
      const img = new Image();
      await new Promise((res, rej) => { img.onload = res; img.onerror = rej; img.src = 'data:image/png;base64,' + a.data; });
      const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
      const ctx = c.getContext('2d'); ctx.drawImage(img, 0, 0);
      const d = ctx.getImageData(0, 0, c.width, c.height).data;
      const px = [];
      const step = a.grid.step;
      for (let y = 0; y < c.height; y += step) for (let x = 0; x < c.width; x += step) {
        const i = (y * c.width + x) * 4; px.push([d[i], d[i+1], d[i+2]]);
      }
      return { w: c.width, h: c.height, px: px };
    }, { data: buf.toString('base64'), grid: { step: grid.step } }).then(r => {
      let min = 99, max = 0, minPx = null, maxPx = null;
      r.px.forEach(c2 => { const v = ratio(fg, c2); if (v < min) { min = v; minPx = c2; } if (v > max) { max = v; maxPx = c2; } });
      return { samples: r.px.length, minRatio: min, maxRatio: max, brightestBg: minPx, darkestBg: maxPx };
    });
  }

  const out = {};

  // ============ BANNER ============
  await p.evaluate(() => { [...document.querySelectorAll('#screenChips .chip')].find(x => x.getAttribute('data-screen') === 'home').click(); });
  await p.waitForTimeout(600);
  const bInfo = await p.evaluate(() => {
    const R = el => { const r = el.getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height, right: r.right, bottom: r.bottom }; };
    const ban = R(document.querySelector('#homeBanner'));
    const title = R(document.querySelector('#homeBanner .banner__slide.is-on .banner__title'));
    const tag = R(document.querySelector('#homeBanner .banner__slide.is-on .banner__tag'));
    return { ban, title, tag };
  });
  await p.addStyleTag({ content: '.banner__text, .banner__text * { color: transparent !important; text-shadow: none !important; }' });
  await p.waitForTimeout(200);
  out.bannerTitle = await worstCase(
    { x: bInfo.title.x, y: bInfo.title.y, width: bInfo.title.w, height: bInfo.title.h },
    { step: 2 }, [255,255,255]);
  out.bannerTitle.caption = 'Banner 主标题（17pt 白字）压图区最差对比度';
  out.bannerTag = await worstCase(
    { x: bInfo.tag.x, y: bInfo.tag.y, width: bInfo.tag.w, height: bInfo.tag.h },
    { step: 2 }, [46,43,41]);
  out.bannerTag.caption = 'Banner Tag（炭字 on 金底）最差对比度';

  // ============ HERO ============
  await p.evaluate(() => { [...document.querySelectorAll('#screenChips .chip')].find(x => x.getAttribute('data-screen') === 'pet').click(); });
  await p.waitForTimeout(600);
  const hInfo = await p.evaluate(() => {
    const R = el => { const r = el.getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height, right: r.right, bottom: r.bottom }; };
    const panel = R(document.querySelector('#scr-pet .hub-panel'));
    const kicker = R(document.querySelector('#scr-pet .hub-panel__kicker'));
    const title = R(document.querySelector('#scr-pet .hub-panel__title'));
    const pills = Array.prototype.map.call(document.querySelectorAll('#scr-pet .hub-pill'), R);
    return { panel, kicker, title, pills };
  });
  await p.addStyleTag({ content: '.hub-panel, .hub-panel *, .hub-pills, .hub-pills * { color: transparent !important; text-shadow: none !important; }' });
  await p.waitForTimeout(200);
  out.heroKicker = await worstCase({ x: hInfo.kicker.x, y: hInfo.kicker.y, width: hInfo.kicker.w, height: hInfo.kicker.h }, { step: 1 }, [255,255,255]);
  out.heroKicker.caption = 'Hero 左上 kicker（12pt 白字）';
  out.heroTitle = await worstCase({ x: hInfo.title.x, y: hInfo.title.y, width: hInfo.title.w, height: hInfo.title.h }, { step: 1 }, [255,255,255]);
  out.heroTitle.caption = 'Hero 左上 宠物名（17pt 白字）';
  out.heroPill1 = await worstCase({ x: hInfo.pills[0].x, y: hInfo.pills[0].y, width: hInfo.pills[0].w, height: hInfo.pills[0].h }, { step: 1 }, [255,255,255]);
  out.heroPill1.caption = 'Hero 右上「装扮」胶囊（13pt 白字）';
  out.heroPill2 = await worstCase({ x: hInfo.pills[1].x, y: hInfo.pills[1].y, width: hInfo.pills[1].w, height: hInfo.pills[1].h }, { step: 1 }, [255,255,255]);
  out.heroPill2.caption = 'Hero 右上「3D 空间」胶囊（13pt 白字）';
  out.heroGeom = hInfo;

  // ============ 其他图上文字：社区帖子图无文字；检查 rail 头像环 & 宠物 rail ============
  console.log(JSON.stringify(out, null, 1));
  await b.close();
})();
