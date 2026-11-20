/* 遮罩层渲染根因定位 */
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1500, height: 1180 }, deviceScaleFactor: 1 });
  await p.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/'));
  await p.waitForTimeout(500);
  const r = await p.evaluate(async () => {
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    const sb = [...document.querySelectorAll('#screenChips .chip')].find(x => x.getAttribute('data-screen') === 'home');
    sb.click(); await sleep(400);
    const out = {};
    const scrim = document.querySelector('#homeBanner .banner__scrim');
    const cs = getComputedStyle(scrim);
    out.bannerScrim = {
      exists: !!scrim, backgroundImage: cs.backgroundImage, backgroundColor: cs.backgroundColor,
      position: cs.position, zIndex: cs.zIndex, opacity: cs.opacity,
      rect: (r => ({ x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) }))(scrim.getBoundingClientRect()),
      parentClass: scrim.parentElement.className,
      parentOpacity: getComputedStyle(scrim.parentElement).opacity
    };
    out.rootScrimVar = getComputedStyle(document.documentElement).getPropertyValue('--scrim-banner');
    const slide = document.querySelector('#homeBanner .banner__slide.is-on');
    out.slide = { opacity: getComputedStyle(slide).opacity, bgImage: getComputedStyle(slide).backgroundImage };
    out.bannerEl = (r => ({ w: Math.round(r.width), h: Math.round(r.height) }))(document.querySelector('#homeBanner').getBoundingClientRect());
    // hero scrim
    const sb2 = [...document.querySelectorAll('#screenChips .chip')].find(x => x.getAttribute('data-screen') === 'pet');
    sb2.click(); await sleep(400);
    const hs = document.querySelector('#scr-pet .hub-hero__scrim-top');
    out.heroScrimTop = hs ? { backgroundImage: getComputedStyle(hs).backgroundImage, rect: (r => ({ x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) }))(hs.getBoundingClientRect()) } : 'NOT FOUND';
    const hsc = document.querySelector('#scr-pet .hub-hero__scrim');
    out.heroScrimWrap = hsc ? { backgroundImage: getComputedStyle(hsc).backgroundImage, pointerEvents: getComputedStyle(hsc).pointerEvents } : 'NOT FOUND';
    const glass = document.querySelector('#scr-pet .hub-panel');
    out.glass = { background: getComputedStyle(glass).background, backdropFilter: getComputedStyle(glass).backdropFilter, rect: (r => ({ x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) }))(glass.getBoundingClientRect()) };
    out.heroImg = { src: document.getElementById('petHero').getAttribute('src'), objectPosition: getComputedStyle(document.getElementById('petHero')).objectPosition };
    out.islandImmersive = document.getElementById('deviceScreen').className;
    out.statusbarColor = getComputedStyle(document.querySelector('.device__statusbar')).color;
    return out;
  });
  console.log(JSON.stringify(r, null, 1));
  await b.close();
})();
