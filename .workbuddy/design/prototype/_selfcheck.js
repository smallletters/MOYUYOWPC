/**
 * 自查红线（§6.13.7 / §7 X1-X11）自动校验
 * 判定规则按 Phase 1 规范收紧（v1.8）：
 *  R1 低对比：只判「关键信息」；tertiary + 非关键元信息（kicker/时间/计数/未选中 tab/placeholder）豁免
 *  R2 金色：按 token 判定（--color-primary / --color-primary-light / --color-primary-dark / --brand-gradient-*），
 *           不按 RGB 色相猜；--color-danger / --color-danger-strong 不算金色；产品线语义色（.linechip/.line-chip）单列
 *  R3 粗体正文：仅「非数字 且 字号 ≥13px 且 weight ≥600」且属正文类才算问题（排除 tab/chip/徽标/标题/价格）
 *  R4 视觉主角：同页 score3 元素按相对面积比较，2nd ≥ max/1.5 才算「多主角」；嵌套子元素不重复计
 *  X9 危险面：--color-danger-strong 承载文字时，前景色必须是 --on-danger 且对比度 ≥4.5
 */
const { chromium } = require('D:/MOYUYOWPC/node_modules/playwright');

const PROTO = 'file:///D:/MOYUYOWPC/.workbuddy/design/prototype/index.html';
const TABS = ['pet', 'home', 'community', 'messages', 'user'];

const AUDIT = `
function lum(rgb){
  const f = rgb.map(v => { v/=255; return v<=0.03928 ? v/12.92 : Math.pow((v+0.055)/1.055, 2.4); });
  return 0.2126*f[0] + 0.7152*f[1] + 0.0722*f[2];
}
function parse(c){
  if(!c) return null;
  const m = c.match(/rgba?\\(([^)]+)\\)/); if(!m) return null;
  const p = m[1].split(',').map(s=>parseFloat(s));
  return {r:p[0],g:p[1],b:p[2],a:p.length>3?p[3]:1};
}
function hex2rgb(h){
  h = (h||'').trim();
  if(h.charAt(0)==='#') h = h.slice(1);
  if(h.length===3) h = h[0]+h[0]+h[1]+h[1]+h[2]+h[2];
  if(!/^[0-9a-fA-F]{6}$/.test(h)) return null;
  return {r:parseInt(h.slice(0,2),16), g:parseInt(h.slice(2,4),16), b:parseInt(h.slice(4,6),16)};
}
function blend(fg,bg){ const a=fg.a; return {r:fg.r*a+bg.r*(1-a), g:fg.g*a+bg.g*(1-a), b:fg.b*a+bg.b*(1-a), a:1}; }
function effBg(el){
  let n=el, stack=[];
  while(n && n.nodeType===1){
    const c = parse(getComputedStyle(n).backgroundColor);
    if(c && c.a>0){ stack.push(c); if(c.a>=1) break; }
    n=n.parentElement;
  }
  let base={r:255,g:255,b:255,a:1};
  for(let i=stack.length-1;i>=0;i--) base=blend(stack[i],base);
  return base;
}
function onVisual(el){
  // 只向上看 2 层：避免「整个 section 含图」导致正文被误判为压在图上
  let depth=0;
  for(let n=el; n && n.nodeType===1 && depth<3; n=n.parentElement, depth++){
    const cs=getComputedStyle(n);
    if((cs.backgroundImage||'').includes('gradient')) return true;
    if(n.querySelector('img')) return true;
  }
  return false;
}
function tok(name){
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return hex2rgb(v);
}
function same(a,b){ return !!(a && b) && Math.abs(a.r-b.r)<2 && Math.abs(a.g-b.g)<2 && Math.abs(a.b-b.b)<2; }

/* R1：非关键元信息 */
const META_CLS = /(kicker|\\btm\\b|time|cnt|ph-text|load-more|copy|prod-desc|ni-time|\\bsmall\\b|cc-t|\\bnum\\b|\\bn\\b|end-note)/;
function isMeta(cls, tag){
  if(tag==='S') return true;            // 原价删除线（§5.1.2⑤ 规定 tertiary）
  if(META_CLS.test(cls)) return true;
  if(/(shelf-tab|issue-tab|msg-seg-tab|topic-chip|filter-chip|linechip|line-chip)/.test(cls)){
    if(/\\bon\\b/.test(cls)) return false;
    return true;
  }
  return false;
}
/* R3：正文类才做粗体判定 */
const NON_BODY = /(tab|chip|badge|mchip|kicker|title|nm|name|nick|price|num|level|label|brand|meta|icon|dot|mark|avatar|pet-sw|wordmark|count|badge)/;
function isBody(cls, tag){
  if(NON_BODY.test(cls)) return false;
  if(tag==='B'||tag==='H1'||tag==='H2'||tag==='H3'||tag==='H4') return false;
  return true;
}

window.__audit = function(pageId){
  const root = document.getElementById(pageId);
  const out = { lowContrast:[], goldText:[], goldEls:[], boldBody:[], dangerFace:[], multiProtagonist:[] };
  const GOLD = ['--color-primary','--color-primary-light','--color-primary-dark'].map(tok).filter(Boolean);
  const DANGER_STRONG = tok('--color-danger-strong');
  const TERTIARY = tok('--color-text-tertiary');
  const ON_DANGER = tok('--on-danger');
  const els = root.querySelectorAll('*');

  for(const el of els){
    const cs = getComputedStyle(el);
    if(cs.display==='none'||cs.visibility==='hidden'||parseFloat(cs.opacity)===0) continue;
    const r = el.getBoundingClientRect();
    if(r.width<2||r.height<2) continue;
    const own = Array.from(el.childNodes).filter(n=>n.nodeType===3&&n.textContent.trim().length>0).map(n=>n.textContent.trim()).join(' ');
    const cls = (el.className&&el.className.toString())||'';
    const fg = parse(cs.color); if(!fg) continue;
    const bg = effBg(el);
    const L1 = lum([fg.r,fg.g,fg.b]), L2 = lum([bg.r,bg.g,bg.b]);
    const ratio = (Math.max(L1,L2)+0.05)/(Math.min(L1,L2)+0.05);
    const fs = parseFloat(cs.fontSize), fw = parseInt(cs.fontWeight)||400;
    const large = fs>=24||(fs>=18.66&&fw>=700);
    const need = large?3:4.5;
    const visual = onVisual(el);
    const isText = own.length>0;

    /* R1 低对比（只判关键信息） */
    if(isText && !visual){
      const meta = isMeta(cls, el.tagName);
      const tertiary = same(fg, TERTIARY);
      if(!(tertiary && meta) && ratio < need){
        out.lowContrast.push({ cls: cls.slice(0,38)||el.tagName, text: own.slice(0,16), ratio:+ratio.toFixed(2), need, color: cs.color, fs, fw });
      }
    }

    /* R2 金色（按 token，不按色相） */
    if(isText && !visual && GOLD.some(g => same(fg,g))){
      out.goldText.push({ cls: cls.slice(0,32), text: own.slice(0,14), ratio:+ratio.toFixed(2), ok: ratio>=4.5 });
    }
    const bgc = parse(cs.backgroundColor);
    const bc  = parse(cs.borderTopColor);
    const isLineChip = /linechip|line-chip/.test(cls);
    let gold=false, kind='';
    if(bgc && bgc.a>0 && GOLD.some(g=>same(bgc,g)) && r.width*r.height>200){ gold=true; kind='背景'; }
    if(!gold && bc && GOLD.some(g=>same(bc,g)) && parseFloat(cs.borderTopWidth)>0){ gold=true; kind='描边'; }
    if(gold && !isLineChip) out.goldEls.push({ cls: cls.slice(0,34)||el.tagName, kind, area: Math.round(r.width*r.height) });
    if((cs.backgroundImage||'').includes('gradient') && /vip-card/.test(cls)) out.goldEls.push({ cls: cls.slice(0,34), kind:'渐变(会员卡)', area: Math.round(r.width*r.height) });

    /* X9 危险面文字 */
    if(bgc && bgc.a>=0.9 && DANGER_STRONG && same(bgc, DANGER_STRONG) && isText){
      const good = ON_DANGER && same(fg, ON_DANGER) && ratio>=4.5;
      if(!good) out.dangerFace.push({ cls: cls.slice(0,34), text: own.slice(0,12), ratio:+ratio.toFixed(2), color: cs.color, expect:'var(--on-danger)' });
    }

    /* R3 粗体正文（排除数字 + 非正文角色） */
    if(isText && fw>=600 && fs>=13 && isBody(cls, el.tagName) && !/[0-9]/.test(own)){
      out.boldBody.push({ cls: cls.slice(0,38)||el.tagName, text: own.slice(0,18), fw, fs });
    }
  }

  /* R4 视觉主角：只取顶层 score3 元素，按相对面积 */
  const cand=[];
  root.querySelectorAll('*').forEach(el=>{
    const cs=getComputedStyle(el), r=el.getBoundingClientRect();
    if(r.width<8||r.height<8) return;
    const bgc=parse(cs.backgroundColor);
    const hasImg=el.querySelector(':scope > img');
    const hasGrad=(cs.backgroundImage||'').includes('gradient');
    const cls0=(el.className||'').toString();
    if(/ink|veil|scrim|mask|overlay|ph-fallback/.test(cls0)) return;
    let score=0;
    if((hasGrad||hasImg) && r.width*r.height>4000) score=3;
    else if(bgc && bgc.a>0 && r.width*r.height>4000) score=2;
    if(score>=3) cand.push({el, area:Math.round(r.width*r.height)});
  });
  const top = cand.filter(c => !cand.some(o => o.el!==c.el && o.el.contains(c.el)));
  top.sort((a,b)=>b.area-a.area);
  if(top.length>1 && top[1].area >= top[0].area/1.5){
    out.multiProtagonist.push({ a1:(top[0].el.className||'').toString().slice(0,28), area1:top[0].area,
                                 a2:(top[1].el.className||'').toString().slice(0,28), area2:top[1].area });
  }
  out.protagonist = top.slice(0,2).map(t=>({cls:(t.el.className||'').toString().slice(0,28), area:t.area}));
  out.goldCount = out.goldEls.length;
  delete out.goldEls;
  return out;
};
`;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1400, height: 1000 } });
  await page.goto(PROTO, { waitUntil: 'load' });
  await page.waitForTimeout(600);
  await page.addScriptTag({ content: AUDIT });

  let total = 0;
  const summary = [];
  for (const theme of ['light', 'dark']) {
    await page.click(`#pbTheme button[data-theme="${theme}"]`);
    await page.waitForTimeout(350);
    console.log(`\n######## THEME = ${theme} ########`);
    for (const t of TABS) {
      await page.click(`#pbTabs button[data-page="${t}"]`);
      await page.waitForTimeout(250);
      await page.selectOption('#pbState', 'normal');
      await page.waitForTimeout(250);
      const a = await page.evaluate(p => window.__audit(p), 'page-' + t);
      const n = a.lowContrast.length + a.goldText.filter(g => !g.ok).length + a.boldBody.length + a.dangerFace.length + a.multiProtagonist.length;
      total += n;
      console.log(`--- ${t} ---`);
      console.log(`  低对比(关键信息): ${a.lowContrast.length} ${JSON.stringify(a.lowContrast)}`);
      console.log(`  金色文字: ${a.goldText.length} ${JSON.stringify(a.goldText)}`);
      console.log(`  危险面文字: ${a.dangerFace.length} ${JSON.stringify(a.dangerFace)}`);
      console.log(`  粗体正文: ${a.boldBody.length} ${JSON.stringify(a.boldBody)}`);
      console.log(`  多主角: ${a.multiProtagonist.length} ${JSON.stringify(a.multiProtagonist)}`);
      console.log(`  金色元素数(页内,不含 tabBar/产品线语义色): ${a.goldCount}  主角: ${JSON.stringify(a.protagonist)}`);
      summary.push({ theme, page: t, issues: n });
    }
  }
  console.log('\n######## 合计问题数 ########');
  console.log(total);
  console.log(JSON.stringify(summary));
  await browser.close();
})();
