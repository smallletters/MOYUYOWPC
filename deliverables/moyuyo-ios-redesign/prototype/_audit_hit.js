/* 精确命中边界扫描：逐像素探测 elementFromPoint 命中扩展区 */
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1500, height: 1180 }, deviceScaleFactor: 1 });
  await p.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/'));
  await p.waitForTimeout(500);

  const res = await p.evaluate(async () => {
    const out = {};
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    async function scan(label, selector, screen) {
      const sb = [...document.querySelectorAll('#screenChips .chip')].find(x => x.getAttribute('data-screen') === screen);
      sb.click(); await sleep(320);
      const el = document.querySelector(selector);
      if (!el) { out[label] = 'NOT FOUND'; return; }
      const r = el.getBoundingClientRect();
      const cx = r.left + r.width / 2;
      let firstHit = null, lastHit = null; const missing = []; const raw = [];
      for (let d = -40; d <= 40; d++) {
        const y = r.top + r.height / 2 + d;
        const stack = document.elementsFromPoint(cx, y) || [];
        const hit = stack[0];
        const ok = hit && (hit === el || el.contains(hit));
        raw.push(d + ': ' + (hit ? hit.tagName + '.' + String(hit.className).slice(0, 26) : 'null') + (ok ? ' OK' : ' MISS'));
        if (ok) { if (firstHit === null) firstHit = d; lastHit = d; }
        else missing.push({ d, got: hit ? hit.tagName + '.' + String(hit.className).slice(0, 30) : 'null' });
      }
      out[label] = {
        visualH: Math.round(r.height), visualW: Math.round(r.width),
        hitTopOffset: firstHit, hitBottomOffset: lastHit,
        effectiveH: (lastHit - firstHit + 1),
        gaps: missing.filter(m => m.d > firstHit && m.d < lastHit).map(m => m.d),
        edgeMiss: missing.filter(m => m.d <= firstHit || m.d >= lastHit).map(m => m.d), raw: raw.filter(r => !r.endsWith('OK'))
      };
    }
    await scan('communityChip', '#scr-community .chip-pill', 'community');
    await scan('homeSearch', '#scr-home .nav__search .search', 'home');
    await scan('hubPill', '#scr-pet .hub-pill', 'pet');
    await scan('quickItem', '#scr-pet .quick__item', 'pet');
    await scan('rowIconUser', '#scr-user .row--icon', 'user');
    await scan('postAct', '#scr-community .post__act', 'community');
    await scan('postTopic', '#scr-community .post__topic', 'community');
    await scan('tabbarItem', '#tabbar .tabbar__item', 'pet');
    await scan('convAct', '#scr-messages .conv__act', 'messages');
    await scan('reminderMore', '#scr-pet .reminder__more', 'pet');
    await scan('lineChipHome', '#scr-home #lineChips .chip-pill', 'home');
    await scan('segmentItem', '#scr-home .segment__item', 'home');
    await scan('postMore', '#scr-community .post__more', 'community');
    return out;
  });
  console.log(JSON.stringify(res, null, 1));
  await b.close();
})();
