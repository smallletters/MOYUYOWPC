/* 修复验证：运行时注入候选修复，实测修复后的对比度（不改源码） */
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1500, height: 1180 }, deviceScaleFactor: 1 });
  await p.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/'));
  await p.waitForTimeout(700);
  const lum = c => { const ch=v=>{v/=255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);};return 0.2126*ch(c[0])+0.7152*ch(c[1])+0.0722*ch(c[2]); };
  const ratio=(a,c)=>{const L1=lum(a),L2=lum(c),hi=Math.max(L1,L2),lo=Math.min(L1,L2);return +((hi+0.05)/(lo+0.05)).toFixed(2);};

  async function worst(clip, fg, hideSel) {
    if (hideSel) { await p.addStyleTag({ content: hideSel }); await p.waitForTimeout(150); }
    const buf = await p.screenshot({ clip });
    const r = await p.evaluate(async d => {
      const img=new Image(); await new Promise((r,j)=>{img.onload=r;img.onerror=j;img.src='data:image/png;base64,'+d;});
      const c=document.createElement('canvas');c.width=img.width;c.height=img.height;const x=c.getContext('2d');x.drawImage(img,0,0);
      const px=x.getImageData(0,0,c.width,c.height).data;const a=[];for(let i=0;i<px.length;i+=4)a.push([px[i],px[i+1],px[i+2]]);return a;
    }, buf.toString('base64'));
    const rs = r.map(c => ratio(fg, c)).sort((a,b)=>a-b);
    return { n: r.length, min: rs[0], p5: rs[Math.floor(rs.length*0.05)], median: rs[Math.floor(rs.length*0.5)] };
  }
  const out = {};

  // ---------- HERO：修复前 ----------
  await p.evaluate(()=>{[...document.querySelectorAll('#screenChips .chip')].find(x=>x.getAttribute('data-screen')==='pet').click();});
  await p.waitForTimeout(600);
  const heroRects = await p.evaluate(()=>{
    const R=el=>{const r=el.getBoundingClientRect();return{x:r.x,y:r.y,w:r.width,h:r.height};};
    return { panel:R(document.querySelector('#scr-pet .st--normal .hub-panel')),
             kicker:R(document.querySelector('#scr-pet .st--normal .hub-panel__kicker')),
             title:R(document.querySelector('#scr-pet .st--normal .hub-panel__title')),
             pills:[...document.querySelectorAll('#scr-pet .st--normal .hub-pill')].map(R) };
  });
  out.HERO_修复前 = {
    kicker: await worst({x:heroRects.kicker.x,y:heroRects.kicker.y,width:heroRects.kicker.w,height:heroRects.kicker.h},[255,255,255], '.hub-panel,.hub-panel *,.hub-pills,.hub-pills *{color:transparent!important;text-shadow:none!important;}'),
    title:  await worst({x:heroRects.title.x,y:heroRects.title.y,width:heroRects.title.w,height:heroRects.title.h},[255,255,255]),
    pill1:  await worst({x:heroRects.pills[0].x,y:heroRects.pills[0].y,width:heroRects.pills[0].w,height:heroRects.pills[0].h},[255,255,255]),
    pill2:  await worst({x:heroRects.pills[1].x,y:heroRects.pills[1].y,width:heroRects.pills[1].w,height:heroRects.pills[1].h},[255,255,255])
  };

  // ---------- HERO：注入修复（正常态补 .hub-hero__scrim-local） ----------
  await p.evaluate(()=>{
    const hero=document.querySelector('#scr-pet .st--normal .hub-hero');
    const wrap=hero.querySelector('.hub-hero__scrim');
    const d=document.createElement('div'); d.className='hub-hero__scrim-local';
    wrap.insertBefore(d, wrap.querySelector('.hub-hero__scrim-bottom'));
  });
  await p.waitForTimeout(250);
  out.HERO_注入局部压暗后 = {
    kicker: await worst({x:heroRects.kicker.x,y:heroRects.kicker.y,width:heroRects.kicker.w,height:heroRects.kicker.h},[255,255,255]),
    title:  await worst({x:heroRects.title.x,y:heroRects.title.y,width:heroRects.title.w,height:heroRects.title.h},[255,255,255]),
    pill1:  await worst({x:heroRects.pills[0].x,y:heroRects.pills[0].y,width:heroRects.pills[0].w,height:heroRects.pills[0].h},[255,255,255]),
    pill2:  await worst({x:heroRects.pills[1].x,y:heroRects.pills[1].y,width:heroRects.pills[1].w,height:heroRects.pills[1].h},[255,255,255])
  };

  // ---------- HERO：注入修复 2（局部压暗 + 玻璃加深） ----------
  await p.addStyleTag({ content: ':root{ --glass-dark: rgba(46,43,41,.55); }' });
  await p.waitForTimeout(250);
  out.HERO_局部压暗加深玻璃后 = {
    kicker: await worst({x:heroRects.kicker.x,y:heroRects.kicker.y,width:heroRects.kicker.w,height:heroRects.kicker.h},[255,255,255]),
    title:  await worst({x:heroRects.title.x,y:heroRects.title.y,width:heroRects.title.w,height:heroRects.title.h},[255,255,255]),
    pill1:  await worst({x:heroRects.pills[0].x,y:heroRects.pills[0].y,width:heroRects.pills[0].w,height:heroRects.pills[0].h},[255,255,255]),
    pill2:  await worst({x:heroRects.pills[1].x,y:heroRects.pills[1].y,width:heroRects.pills[1].w,height:heroRects.pills[1].h},[255,255,255])
  };

  // ---------- BANNER ----------
  await p.evaluate(()=>{[...document.querySelectorAll('#screenChips .chip')].find(x=>x.getAttribute('data-screen')==='home').click();});
  await p.waitForTimeout(700);
  const bR = await p.evaluate(()=>{
    const R=el=>{const r=el.getBoundingClientRect();return{x:r.x,y:r.y,w:r.width,h:r.height,right:r.right,bottom:r.bottom};};
    const t=R(document.querySelector('#homeBanner .banner__slide.is-on .banner__title'));
    const d=R(document.querySelector('#homeBanner .banner__dots'));
    const dot=R(document.querySelector('#homeBanner .banner__dot.is-on'));
    return {title:t,dots:d,dot};
  });
  out.BANNER_几何 = { 标题: bR.title, 指示点组: bR.dots, 选中点: bR.dot,
    垂直间隙_点顶减标题底: +(bR.dot.y - bR.title.bottom).toFixed(1),
    水平是否重叠: (bR.dot.right > bR.title.x && bR.dot.x < bR.title.right),
    标题右边界: bR.title.right, 指示点左边界: bR.dot.x };
  const banClip = { x: bR.title.x, y: bR.title.y, width: bR.title.w, height: Math.max(4, bR.title.h - 3) };
  out.BANNER_修复前 = await worst(banClip, [255,255,255], '.banner__text,.banner__text *,.banner__dots{color:transparent!important;background:none!important;}');

  // 候选修复：更陡的压暗渐变
  await p.addStyleTag({ content: ':root{ --scrim-banner: linear-gradient(to top, rgba(46,43,41,.80) 0%, rgba(46,43,41,.42) 42%, rgba(46,43,41,0) 78%); }' });
  await p.waitForTimeout(250);
  out.BANNER_加深压暗后 = await worst(banClip, [255,255,255]);

  // 候选修复 2：再叠加一层左下局部压暗
  await p.addStyleTag({ content: '.banner__scrim{ background-image: linear-gradient(to top, rgba(46,43,41,.80) 0%, rgba(46,43,41,.42) 42%, rgba(46,43,41,0) 78%), radial-gradient(120% 90% at 12% 100%, rgba(46,43,41,.55) 0%, rgba(46,43,41,0) 60%); }' });
  await p.waitForTimeout(250);
  out['BANNER_加深+左下局部后'] = await worst(banClip, [255,255,255]);

  console.log(JSON.stringify(out, null, 1));
  await b.close();
})();
