const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1500, height: 1180 }, deviceScaleFactor: 1 });
  await p.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/'));
  await p.waitForTimeout(600);
  const lum = c => { const ch=v=>{v/=255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);};return 0.2126*ch(c[0])+0.7152*ch(c[1])+0.0722*ch(c[2]); };
  const ratio = (a,c)=>{const L1=lum(a),L2=lum(c),hi=Math.max(L1,L2),lo=Math.min(L1,L2);return +((hi+0.05)/(lo+0.05)).toFixed(2);};
  await p.evaluate(()=>{[...document.querySelectorAll('#screenChips .chip')].find(x=>x.getAttribute('data-screen')==='home').click();});
  await p.waitForTimeout(700);
  const info = await p.evaluate(()=>{const R=el=>{const r=el.getBoundingClientRect();return{x:r.x,y:r.y,w:r.width,h:r.height,right:r.right,bottom:r.bottom};};
    return {title:R(document.querySelector('#homeBanner .banner__slide.is-on .banner__title')), dots:R(document.querySelector('#homeBanner .banner__dots'))};});
  // 隐藏文字与指示点
  await p.addStyleTag({content:'.banner__text, .banner__text *, .banner__dots { color: transparent !important; background: none !important; }'});
  await p.waitForTimeout(200);
  const clip = { x: info.title.x, y: info.title.y, width: info.title.w, height: info.title.h - 3 }; // 排除指示点带
  const buf = await p.screenshot({clip});
  const res = await p.evaluate(async d=>{
    const img=new Image(); await new Promise((r,j)=>{img.onload=r;img.onerror=j;img.src='data:image/png;base64,'+d;});
    const c=document.createElement('canvas');c.width=img.width;c.height=img.height;const x=c.getContext('2d');x.drawImage(img,0,0);
    const px=x.getImageData(0,0,c.width,c.height).data; const out=[];
    for(let i=0;i<px.length;i+=4) out.push([px[i],px[i+1],px[i+2]]);
    return {w:c.width,h:c.height,px:out};
  }, buf.toString('base64'));
  const rs = res.px.map(c=>ratio([255,255,255],c)).sort((a,b)=>a-b);
  const lumSorted = res.px.map(c=>lum(c)).sort((a,b)=>a-b);
  const pick = arr => arr[Math.floor(arr.length*0.95)];
  const bgOf = (L) => { // 反查亮度对应的代表色
    const idx = lumSorted.findIndex(v=>v>=L); const p = res.px.find(c=>Math.abs(lum(c)-L)<0.002); return p||null; };
  console.log(JSON.stringify({
    采样点数: res.px.length,
    '白字最差对比度': rs[0], '白字P5': rs[Math.floor(rs.length*0.05)], '白字中位': rs[Math.floor(rs.length*0.5)], '白字P95': rs[Math.floor(rs.length*0.95)], '白字最佳': rs[rs.length-1],
    '最亮背景': bgOf(lumSorted[lumSorted.length-1]), '最暗背景': bgOf(lumSorted[0]),
    说明: '§4.8 要求 Banner 压字区白字 ≥ 4.5:1（设计系统称 7.83:1）'
  }, null, 1));
  await b.close();
})();
