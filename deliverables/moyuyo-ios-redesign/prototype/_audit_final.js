/* 收尾核查：折叠细节 / 图片几何 / 切态位移 / 会员卡对比度 / 降级 */
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1500, height: 1180 }, deviceScaleFactor: 1 });
  await p.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/'));
  await p.waitForTimeout(600);
  const out = {};
  const go = async (id) => { await p.evaluate(s => { [...document.querySelectorAll('#screenChips .chip')].find(x => x.getAttribute('data-screen') === s).click(); }, id); await p.waitForTimeout(450); };

  // 1) 首页 Compact 折叠
  await go('home');
  const homeFold = async (y) => {
    await p.evaluate(v => { document.querySelector('#scr-home [data-scroll]').scrollTop = v; }, y);
    await p.waitForTimeout(450);
    return p.evaluate(() => {
      const scr = document.getElementById('scr-home');
      const s = scr.querySelector('.nav__search .search');
      const t = scr.querySelector('.nav__title-mid');
      const cs = getComputedStyle(s);
      return { scrollTop: scr.querySelector('[data-scroll]').scrollTop,
        searchH: Math.round(s.getBoundingClientRect().height), searchOpacity: cs.opacity,
        titleMidOpacity: getComputedStyle(t).opacity, titleMidText: t.textContent,
        vars: { p: scr.style.getPropertyValue('--p'), searchH: scr.style.getPropertyValue('--search-h'), searchO: scr.style.getPropertyValue('--search-o'), ctO: scr.style.getPropertyValue('--ct-o') } };
    });
  };
  out.homeFold = { at0: await homeFold(0), at30: await homeFold(30), at120: await homeFold(120), at400: await homeFold(400) };

  // 2) 社区图片几何 + 分隔线
  await go('community');
  out.communityMedia = await p.evaluate(() => Array.prototype.map.call(document.querySelectorAll('#scr-community .post'), post => {
    const m = post.querySelector('.post__media');
    const img = m.querySelector('img');
    const r = img.getBoundingClientRect();
    const cs = getComputedStyle(img);
    const acts = post.querySelector('.post__acts').getBoundingClientRect();
    const body = post.querySelector('.post__body').getBoundingClientRect();
    const head = post.querySelector('.post__head').getBoundingClientRect();
    return { n: m.querySelectorAll('img').length, w: Math.round(r.width), h: Math.round(r.height),
      radius: cs.borderRadius, objectPosition: cs.objectPosition,
      图到正文: Math.round(body.top - r.bottom), 正文到话题: null,
      作者行到图: Math.round(r.top - head.bottom), 图到互动行: Math.round(acts.top - r.bottom),
      正文行高: getComputedStyle(post.querySelector('.post__body')).lineHeight,
      正文字号: getComputedStyle(post.querySelector('.post__body')).fontSize,
      正文字重: getComputedStyle(post.querySelector('.post__body')).fontWeight };
  }));

  // 3) 切态首屏纵向位移
  const shift = {};
  for (const s of ['pet','home','community','messages','user']) {
    await go(s);
    const vals = [];
    for (const st of ['normal','loading','empty','error','guest']) {
      await p.evaluate(x => { [...document.querySelectorAll('#stateChips .chip')].find(c => c.getAttribute('data-state') === x).click(); }, st);
      await p.waitForTimeout(220);
      vals.push(await p.evaluate(sid => {
        const scr = document.getElementById('scr-' + sid);
        const nav = scr.querySelector('.nav');
        const sc = scr.querySelector('[data-scroll]');
        const st = scr.querySelector('.st--' + scr.getAttribute('data-state'));
        const first = st ? st.firstElementChild : null;
        return { navH: nav ? Math.round(nav.getBoundingClientRect().height) : null,
          firstTop: first ? Math.round(first.getBoundingClientRect().top) : null,
          scrollTop: Math.round(sc.scrollTop) };
      }, s));
    }
    shift[s] = vals;
    await p.evaluate(() => { [...document.querySelectorAll('#stateChips .chip')].find(c => c.getAttribute('data-state') === 'normal').click(); });
  }
  out.stateShift = shift;

  // 4) 会员卡：炭字 on 金渐变 最差对比度
  await go('user');
  await p.addStyleTag({ content: '.member { color: transparent !important; } .member * { color: transparent !important; }' });
  await p.waitForTimeout(200);
  const mrect = await p.evaluate(() => { const r = document.querySelector('#scr-user .member').getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height }; });
  const buf = await p.screenshot({ clip: { x: mrect.x, y: mrect.y, width: mrect.w, height: mrect.h } });
  out.memberCard = await p.evaluate(async d => {
    const img = new Image(); await new Promise((r, j) => { img.onload = r; img.onerror = j; img.src = 'data:image/png;base64,' + d; });
    const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
    const x = c.getContext('2d'); x.drawImage(img, 0, 0);
    const px = x.getImageData(0, 0, c.width, c.height).data; const arr = [];
    for (let i = 0; i < px.length; i += 4) arr.push([px[i], px[i+1], px[i+2]]);
    return { w: c.width, h: c.height, n: arr.length };
  }, buf.toString('base64'));
  // 会员卡文字位置取样（直接读渲染像素）
  const memTexts = await p.evaluate(() => Array.prototype.map.call(document.querySelectorAll('#scr-user .member__title, #scr-user .member__points, #scr-user .member__tip'), el => {
    const r = el.getBoundingClientRect(); const cs = getComputedStyle(el);
    return { cls: el.className, text: el.textContent.trim(), x: r.x, y: r.y, w: r.width, h: r.height, fs: parseFloat(cs.fontSize), fw: cs.fontWeight };
  }));
  const lum = c => { const ch=v=>{v/=255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);};return 0.2126*ch(c[0])+0.7152*ch(c[1])+0.0722*ch(c[2]); };
  const ratio=(a,c)=>{const L1=lum(a),L2=lum(c),hi=Math.max(L1,L2),lo=Math.min(L1,L2);return +((hi+0.05)/(lo+0.05)).toFixed(2);};
  out.memberTextContrast = [];
  for (const t of memTexts) {
    const s = await p.screenshot({ clip: { x: t.x, y: t.y, width: Math.min(t.w, 260), height: t.h } });
    const px = await p.evaluate(async d => {
      const img=new Image(); await new Promise((r,j)=>{img.onload=r;img.onerror=j;img.src='data:image/png;base64,'+d;});
      const c=document.createElement('canvas');c.width=img.width;c.height=img.height;const x=c.getContext('2d');x.drawImage(img,0,0);
      const px=x.getImageData(0,0,c.width,c.height).data;const a=[];for(let i=0;i<px.length;i+=4)a.push([px[i],px[i+1],px[i+2]]);return a;
    }, s.toString('base64'));
    // 取最亮（对炭字最差）
    let worst = null, wr = 99;
    px.forEach(c => { const v = ratio([46,43,41], c); if (v < wr) { wr = v; worst = c; } });
    let best = null, br = 0;
    px.forEach(c => { const v = ratio([46,43,41], c); if (v > br) { br = v; best = c; } });
    out.memberTextContrast.push({ cls: t.cls, text: t.text, fs: t.fs, fw: t.fw, 最差: wr, 最差底色: worst, 最佳: br });
  }

  // 5) 降级 / 动效
  out.reducedMotion = await p.evaluate(() => {
    const rules = [];
    Array.prototype.forEach.call(document.styleSheets[0].cssRules, r => {
      if (r.conditionText && /prefers-reduced-motion/.test(r.conditionText)) rules.push(r.cssText.slice(0, 200));
      if (r.media) rules.push('MEDIA ' + r.conditionText);
    });
    return rules;
  });
  out.pressRules = await p.evaluate(() => {
    let n = 0, samples = [];
    Array.prototype.forEach.call(document.styleSheets[0].cssRules, r => {
      if (r.selectorText && r.selectorText.indexOf(':active') >= 0) { n++; if (samples.length < 8) samples.push(r.selectorText + ' { ' + r.style.cssText.slice(0, 110) + ' }'); }
    });
    return { activeRuleCount: n, samples: samples };
  });
  out.shadowCount = await p.evaluate(() => {
    const c = {};
    Array.prototype.forEach.call(document.styleSheets[0].cssRules, r => {
      if (!r.style || !r.style.boxShadow) return;
      const v = r.style.boxShadow;
      c[v] = (c[v] || 0) + 1;
    });
    return c;
  });
  console.log(JSON.stringify(out, null, 1));
  await b.close();
})();
