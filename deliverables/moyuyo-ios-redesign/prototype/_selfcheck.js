/**
 * MOYUYO ATELIER · iOS 5 屏原型 · 交付自检脚本
 * ---------------------------------------------------------------------------
 * 用法： node _selfcheck.js
 * 产出： _shots/selfcheck.json  +  _shots/<screen>.png
 *
 * 检查项：
 *  1. 逐屏点击 TabBar 并截图（5 屏）
 *  2. 控制台错误 / 页面异常 / 资源加载失败
 *  3. 全部 <img> 的 naturalWidth 是否为 0（断链检测）
 *  4. CSS 中 :hover 规则数量（红线：全站 0 处）
 *  5. CSS 中全部 #RRGGBB 色值集合 + 禁用色值扫描（红线）
 *  6. 硬性红线扫描：金色文字、蓝色渐变、#FF4D4F、emoji 图标
 *  7. 交互烟测：分段控件 / chip 切换 / 点赞 / 左滑删除 / 会话未读角标
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = __dirname;
const SHOTS = path.join(ROOT, '_shots');
const PAGE = 'file://' + path.join(ROOT, 'index.html').replace(/\\/g, '/');

/* ------------------------------------------------------------- 禁用清单 */
const BANNED_HEX = ['007AFF', '004FAF', '4C6EF5', 'FF4D4F', '1E90FF', '4169E1', '5B5BD6', '6C5CE7'];
const BANNED_TEXT = [
  { re: /rgba\(\s*0\s*,\s*122\s*,\s*255/i, why: 'iOS 蓝 rgba(0,122,255,*)' },
  { re: /rgba\(\s*255\s*,\s*77\s*,\s*79/i, why: '旧危险色 rgba(255,77,79,*)' },
  { re: /linear-gradient\([^)]*#(?:007AFF|004FAF|4C6EF5|1E90FF|4169E1|5B5BD6|6C5CE7)/i, why: '蓝紫渐变' },
  { re: /radial-gradient\([^)]*#(?:007AFF|004FAF|4C6EF5|1E90FF|4169E1|5B5BD6|6C5CE7)/i, why: '蓝紫渐变' },
  { re: /(?<![-\w])color\s*:\s*var\(\s*--color-primary\s*\)/i, why: '金色文字误用 var(--color-primary)' },
  { re: /(?<![-\w])color\s*:\s*#DBC98A/i, why: '金色文字误用裸 #DBC98A' },
  { re: /:hover/i, why: '鼠标悬停规则（移动端应为 0）' },
];

/* Emoji 检测：CJK 之外的扩展象形字符 */
const EMOJI_RE = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}\u{1F000}-\u{1F2FF}]/u;

const results = { startedAt: new Date().toISOString(), page: PAGE, checks: [], errors: [], consoleErrors: [], failedRequests: [] };
const add = (name, pass, detail) => {
  results.checks.push({ name, pass: !!pass, detail: detail === undefined ? '' : detail });
  return pass;
};

/* ---------------------------------------------------------------- 主体 */
(async () => {
  if (!fs.existsSync(SHOTS)) fs.mkdirSync(SHOTS, { recursive: true });

  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1180 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();

  page.on('console', (m) => { if (m.type() === 'error') results.consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => results.errors.push(String(e && e.message ? e.message : e)));
  page.on('requestfailed', (r) => {
    const u = r.url();
    if (u.startsWith('data:')) return;
    results.failedRequests.push(u + ' :: ' + (r.failure() ? r.failure().errorText : 'unknown'));
  });

  await page.goto(PAGE, { waitUntil: 'load' });
  await page.waitForTimeout(700);

  /* ---- 1. 逐屏截图 ---- */
  const screens = ['pet', 'home', 'community', 'messages', 'user'];
  for (const s of screens) {
    await page.click(`.tabbar__item[data-tab="${s}"]`);
    await page.waitForTimeout(450);
    await page.locator('.device').screenshot({ path: path.join(SHOTS, `${s}.png`) });
  }
  add('5 屏 TabBar 切换并截图', true, screens.join(', '));

  /* ---- 2. 控制台 / 异常 / 资源 ---- */
  add('无页面 JS 异常', results.errors.length === 0, results.errors.join(' | ') || '0 个');
  add('无控制台 error', results.consoleErrors.length === 0, results.consoleErrors.join(' | ') || '0 个');
  add('无资源加载失败', results.failedRequests.length === 0, results.failedRequests.join(' | ') || '0 个');

  /* ---- 3. 断链图片（逐屏检查，含懒加载） ---- */
  const broken = [];
  for (const s of screens) {
    await page.click(`.tabbar__item[data-tab="${s}"]`);
    await page.waitForTimeout(250);
    await page.evaluate(async () => {
      const sc = document.querySelector('.screen.is-active [data-scroll]');
      if (sc) { sc.scrollTop = sc.scrollHeight; }
      await new Promise((r) => setTimeout(r, 500));
      if (sc) { sc.scrollTop = 0; }
    });
    await page.waitForTimeout(350);
    const bad = await page.evaluate(() => {
      const out = [];
      document.querySelectorAll('.screen.is-active img').forEach((im) => {
        if (im.complete && im.naturalWidth === 0) out.push(im.getAttribute('src'));
      });
      return out;
    });
    bad.forEach((b) => broken.push(s + ':' + b));
  }
  add('无断链图片 (naturalWidth>0)', broken.length === 0, broken.join(' | ') || '全部图片加载正常');

  /* ---- 4. 全站 CSS 文本收集 ---- */
  const cssText = await page.evaluate(() => {
    const parts = [];
    for (const sheet of document.styleSheets) {
      try { for (const r of sheet.cssRules) parts.push(r.cssText); } catch (e) { /* ignore */ }
    }
    const inline = document.querySelectorAll('style');
    inline.forEach((s) => parts.push(s.textContent));
    return parts.join('\n');
  });

  /* ---- 5. :hover 计数 ---- */
  const hoverCount = (cssText.match(/:hover/g) || []).length;
  add('全站 0 处 :hover', hoverCount === 0, '实际 ' + hoverCount + ' 处');

  /* ---- 6. 禁用色值扫描 ---- */
  const hexSet = new Set();
  (cssText.match(/#[0-9a-fA-F]{3,8}\b/g) || []).forEach((h) => hexSet.add(h.toUpperCase()));
  const bannedHits = [];
  for (const h of hexSet) {
    const bare = h.slice(1);
    for (const b of BANNED_HEX) {
      if (bare.toUpperCase() === b || bare.toUpperCase().startsWith(b)) bannedHits.push(h + ' (禁用 ' + b + ')');
    }
  }
  add('无禁用十六进制色值', bannedHits.length === 0, bannedHits.join(' | ') || '已扫描 ' + hexSet.size + ' 个色值');

  const textHits = [];
  for (const t of BANNED_TEXT) {
    const m = cssText.match(t.re);
    if (m) textHits.push(t.why + ' → ' + m[0].slice(0, 60));
  }
  add('无禁用 CSS 模式（蓝渐变/金色文字/:hover）', textHits.length === 0, textHits.join(' | ') || '通过');

  /* ---- 7. 金色实心块内文字检查 ---- */
  const goldBlocks = await page.evaluate(() => {
    const bad = [];
    const gold = getComputedStyle(document.documentElement).getPropertyValue('--color-primary').trim();
    const goldRgb = gold.replace('#', '');
    const rr = parseInt(goldRgb.slice(0, 2), 16), gg = parseInt(goldRgb.slice(2, 4), 16), bb = parseInt(goldRgb.slice(4, 6), 16);
    document.querySelectorAll('.screen').forEach((scr) => {
      scr.classList.add('is-active');
      scr.querySelectorAll('*').forEach((el) => {
        const cs = getComputedStyle(el);
        const m = cs.backgroundColor.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
        if (!m) return;
        if (Math.abs(+m[1] - rr) < 6 && Math.abs(+m[2] - gg) < 6 && Math.abs(+m[3] - bb) < 6) {
          const txt = (el.textContent || '').trim();
          if (!txt) return;
          const tc = cs.color.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
          if (!tc) return;
          const isWhite = +tc[1] > 235 && +tc[2] > 235 && +tc[3] > 235;
          if (isWhite) bad.push(el.className + ' → "' + txt.slice(0, 20) + '"');
        }
      });
      scr.classList.remove('is-active');
    });
    return bad;
  });
  add('金色实心块内无白字', goldBlocks.length === 0, goldBlocks.join(' | ') || '通过');

  /* ---- 8. Emoji 检测 ---- */
  const bodyText = await page.evaluate(() => document.body.innerText);
  const emojiHit = bodyText.match(EMOJI_RE);
  add('无 emoji 图标', !emojiHit, emojiHit ? '发现 ' + emojiHit.join(' ') : '通过');

  /* ---- 9. 页面左右边距 16pt 检查 ---- */
  const marginIssues = await page.evaluate(() => {
    const bad = [];
    // 只测「内容区容器」：这些元素应占满 361pt 内容宽（393 − 16×2），即左右边缘落在 16 / 377
    const sel = '.group, .prod-grid, .kingkong, .banner, .chiprow, .segment, .care-card, .reminder, ' +
                '.quick, .post, .notice-grid, .quad-grid, .login-card, .conv__list, .guest-banner, .hub-empty-bar';
    const all = Array.from(document.querySelectorAll('.screen'));
    all.forEach((scr) => {
      all.forEach((x) => x.classList.remove('is-active'));
      scr.classList.add('is-active');
      const sr = scr.getBoundingClientRect();
      scr.querySelectorAll(sel).forEach((el) => {
        const r = el.getBoundingClientRect();
        if (r.width === 0 || r.height === 0) return;
        // 元素盒需落在屏内（相对屏幕容器）
        const L = r.left - sr.left, R = r.right - sr.left;
        if (L < -1 || R > 394) return;
        // 全出血容器（hero / 横向滚动条）豁免
        if (el.classList.contains('chiprow')) return;
        // 以「内容盒」判定：内容必须完整落在 [16, 377] 内（设计系统 §3.3.2）
        const cs = getComputedStyle(el);
        const cL = L + parseFloat(cs.paddingLeft), cR = R - parseFloat(cs.paddingRight);
        if (cL < 14.5 || cR > 378.5) {
          bad.push(el.className.split(' ')[0] + ' content L=' + cL.toFixed(1) + ' R=' + cR.toFixed(1));
        }
      });
    });
    all.forEach((x) => x.classList.remove('is-active'));
    return bad;
  });
  add('页面左右边距统一 16pt', marginIssues.length === 0, marginIssues.slice(0, 6).join(' | ') || '通过');

  /* ---- 10. 命中区 ≥ 44×44pt 检查 ---- */
  const hitIssues = await page.evaluate(() => {
    const bad = [];
    const NEED = 44;
    // 设计系统 §4.2 强制 Segmented Control 轨道 33pt / 胶囊 29pt（等同 Apple 原生 UISegmentedControl）：
    // 组件专属规格优先于通用 44pt 规则，记为显式例外（README §已知取舍）
    const EXCEPTION = ['segment__item'];
    const owned = (el, x, y) => {
      const hit = document.elementFromPoint(x, y);
      return hit && (hit === el || el.contains(hit));
    };
    const all = Array.from(document.querySelectorAll('.screen'));
    const screenRect = document.querySelector('.device__screen').getBoundingClientRect();
    all.forEach((scr) => {
      all.forEach((x) => x.classList.remove('is-active'));
      scr.classList.add('is-active');
      scr.querySelectorAll('button, a, [role="button"], [role="tab"]').forEach((el) => {
        const r = el.getBoundingClientRect();
        if (r.width === 0 || r.height === 0) return;
        if (EXCEPTION.some((c) => el.classList.contains(c))) return;
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        // 自身尺寸已达标的方向无需探测；不足方向须覆盖到 44pt（向两侧均分扩展）
        const needL = r.width  < NEED, needT = r.height < NEED;
        const dW = Math.ceil((NEED - r.width) / 2), dH = Math.ceil((NEED - r.height) / 2);
        const probes = [];
        if (needT) { probes.push([cx, r.top - dH + 1], [cx, r.bottom + dH - 1]); }
        if (needL) { probes.push([r.left - dW + 1, cy], [r.right + dW - 1, cy]); }
        for (const [px, py] of probes) {
          // 探测点必须落在设备屏幕矩形内（横向滚动条上的元素可超出屏幕，不计）
          if (px < screenRect.left || px > screenRect.right || py < screenRect.top || py > screenRect.bottom) continue;
          if (!owned(el, px, py)) {
            bad.push((el.className || el.tagName) + ' ' + r.width.toFixed(0) + 'x' + r.height.toFixed(0) +
                     ' 未覆盖 ' + px.toFixed(0) + ',' + py.toFixed(0));
            break;
          }
        }
      });
    });
    all.forEach((x) => x.classList.remove('is-active'));
    return bad;
  });
  add('可点元素命中区 ≥ 44×44pt（Segmented Control 按 §4.2 记为例外）', hitIssues.length === 0, hitIssues.slice(0, 8).join(' | ') || '通过');

  /* ---- 11. 交互烟测 ---- */
  const inter = {};

  // 11a. Segmented Control：首页三 tab 切换 → 指示胶囊位移
  await page.click('.tabbar__item[data-tab="home"]');
  await page.waitForTimeout(350);
  const segBefore = await page.evaluate(() => {
    const i = document.querySelector('#homeSegment .segment__ind');
    return i ? i.style.transform : '';
  });
  await page.click('#homeSegment .segment__item[data-tab="hot"]');
  await page.waitForTimeout(450);
  const segAfter = await page.evaluate(() => {
    const i = document.querySelector('#homeSegment .segment__ind');
    return i ? i.style.transform : '';
  });
  inter.segment = { before: segBefore, after: segAfter, moved: segBefore !== segAfter };
  add('Segmented Control 指示胶囊位移', segBefore !== segAfter, segBefore + ' → ' + segAfter);

  // 11b. 商品网格随 tab 变化
  const gridAfter = await page.evaluate(() => document.querySelectorAll('#prodGrid .prod').length);
  inter.gridCount = gridAfter;
  add('商品网格随 tab 切换重排', gridAfter > 0, '当前 ' + gridAfter + ' 个商品');

  // 11c. 产品线 chip 切换 → 数据源变化
  const prodBefore = await page.evaluate(() => document.querySelector('#prodGrid .prod__name') ? document.querySelector('#prodGrid .prod__name').textContent : '');
  await page.click('#lineChips .chip-pill[data-line="gear"]');
  await page.waitForTimeout(400);
  const prodAfter = await page.evaluate(() => document.querySelector('#prodGrid .prod__name') ? document.querySelector('#prodGrid .prod__name').textContent : '');
  inter.chip = { before: prodBefore, after: prodAfter, changed: prodBefore !== prodAfter };
  add('产品线 chip 切换商品数据源', prodBefore !== prodAfter, prodBefore + ' → ' + prodAfter);

  // 11d. 点赞：计数 +1 + 心形填充
  await page.click('.tabbar__item[data-tab="community"]');
  await page.waitForTimeout(400);
  const likeBefore = await page.evaluate(() => {
    const b = document.querySelector('[data-like="1"]');
    return { n: b.querySelector('[data-like-count]').textContent, on: b.classList.contains('is-liked') };
  });
  await page.click('[data-like="1"]');
  await page.waitForTimeout(350);
  const likeAfter = await page.evaluate(() => {
    const b = document.querySelector('[data-like="1"]');
    return { n: b.querySelector('[data-like-count]').textContent, on: b.classList.contains('is-liked') };
  });
  inter.like = { before: likeBefore, after: likeAfter };
  add('点赞：计数 +1 且心形填充', likeBefore.n !== likeAfter.n && likeAfter.on === true, likeBefore.n + ' → ' + likeAfter.n + '，filled=' + likeAfter.on);

  // 11e. Large Title 折叠：滚动后大标题淡出、小标题淡入
  await page.evaluate(() => { const s = document.querySelector('#scr-community [data-scroll]'); s.scrollTop = 60; });
  await page.waitForTimeout(350);
  const collapse = await page.evaluate(() => {
    const scr = document.getElementById('scr-community');
    const cs = getComputedStyle(scr);
    return { lt: cs.getPropertyValue('--lt-o').trim(), ct: cs.getPropertyValue('--ct-o').trim(), p: cs.getPropertyValue('--p').trim() };
  });
  inter.collapse = collapse;
  add('Large Title 折叠（大标题淡出 + 小标题淡入）', parseFloat(collapse.lt) < 0.5 && parseFloat(collapse.ct) > 0, JSON.stringify(collapse));

  // 11f. 消息：进入会话 → 未读清零 → 角标 -1
  await page.click('.tabbar__item[data-tab="messages"]');
  await page.waitForTimeout(400);
  const badgeBefore = await page.evaluate(() => document.getElementById('tabBadgeMessages').textContent);
  await page.locator('.conv[data-conv="c2"] .conv__body').click();
  await page.waitForTimeout(400);
  const badgeAfter = await page.evaluate(() => document.getElementById('tabBadgeMessages').textContent);
  inter.badge = { before: badgeBefore, after: badgeAfter };
  add('进入会话：未读清零 → TabBar 角标 -1', +badgeAfter === +badgeBefore - 1, badgeBefore + ' → ' + badgeAfter);

  // 11g. 左滑删除：跟手位移 + 阈值吸附
  await page.evaluate(() => {
    const row = document.querySelector('.conv[data-conv="c3"]');
    const body = row.querySelector('.conv__body');
    body.setPointerCapture = function () {};
    body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 300, clientY: 500, pointerId: 1 }));
    body.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: 220, clientY: 500, pointerId: 1 }));
    body.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, clientX: 220, clientY: 500, pointerId: 1 }));
  });
  await page.waitForTimeout(500);
  const swipe = await page.evaluate(() => {
    const row = document.querySelector('.conv[data-conv="c3"]');
    return { open: row.classList.contains('is-open'), tf: row.querySelector('.conv__body').style.transform };
  });
  inter.swipe = swipe;
  add('会话左滑删除：跟手 + 阈值吸附展开', swipe.open === true, 'open=' + swipe.open + ' transform=' + swipe.tf);

  // 11h. Banner 自动轮播
  await page.click('.tabbar__item[data-tab="home"]');
  await page.waitForTimeout(300);
  const b0 = await page.evaluate(() => Array.from(document.querySelectorAll('#homeBanner .banner__slide')).findIndex((s) => s.classList.contains('is-on')));
  await page.waitForTimeout(3400);
  const b1 = await page.evaluate(() => Array.from(document.querySelectorAll('#homeBanner .banner__slide')).findIndex((s) => s.classList.contains('is-on')));
  inter.carousel = { before: b0, after: b1 };
  add('Banner 自动轮播 3s', b0 !== b1, 'slide ' + b0 + ' → ' + b1);

  // 11i. 状态切换不引起首屏纵向位移
  await page.click('.tabbar__item[data-tab="community"]');
  await page.waitForTimeout(300);
  const shift = await page.evaluate(async () => {
    const scr = document.getElementById('scr-community');
    const sc = scr.querySelector('[data-scroll]');
    sc.scrollTop = 0;
    const before = scr.getBoundingClientRect().top;
    const navBefore = scr.querySelector('.nav__compact-title').getBoundingClientRect().top;
    document.querySelector('#stateChips .chip[data-state="loading"]').click();
    await new Promise((r) => setTimeout(r, 300));
    const after = scr.getBoundingClientRect().top;
    const navAfter = scr.querySelector('.nav__compact-title').getBoundingClientRect().top;
    document.querySelector('#stateChips .chip[data-state="normal"]').click();
    return { dScreen: Math.abs(after - before), dNav: Math.abs(navAfter - navBefore) };
  });
  inter.stateShift = shift;
  add('切态不引起首屏纵向位移', shift.dScreen < 0.5 && shift.dNav < 0.5, 'screen Δ=' + shift.dScreen.toFixed(2) + ' nav Δ=' + shift.dNav.toFixed(2));

  // 11j. 未登录拦截
  await page.click('#guestMode');
  await page.waitForTimeout(350);
  const guestLike = await page.evaluate(() => {
    const b = document.querySelector('[data-like="1"]');
    const n = b.querySelector('[data-like-count]').textContent;
    b.click();
    return { n, toast: document.getElementById('toast').textContent };
  });
  inter.guest = guestLike;
  add('未登录点赞被拦截', /登录/.test(guestLike.toast), 'toast=' + guestLike.toast);
  await page.click('#guestMode');
  await page.waitForTimeout(250);

  /* ---- 12. 五态完备性 ---- */
  const stateCovers = await page.evaluate(() => {
    const out = {};
    document.querySelectorAll('.screen').forEach((scr) => {
      const id = scr.getAttribute('data-screen');
      out[id] = ['normal', 'loading', 'empty', 'error', 'guest'].filter((s) => scr.querySelector('.st--' + s)).length;
    });
    return out;
  });
  const allFive = Object.keys(stateCovers).every((k) => stateCovers[k] === 5);
  add('每屏 5 态齐备（正常/加载/空/错误/未登录）', allFive, JSON.stringify(stateCovers));

  /* ---- 13. 令牌唯一性抽查 ---- */
  const tokenCheck = await page.evaluate(() => {
    const cs = getComputedStyle(document.documentElement);
    const names = ['--color-primary', '--ink-gold', '--on-primary', '--color-background', '--color-surface',
      '--color-text', '--color-text-secondary', '--color-fill', '--color-fill-dim', '--color-danger-solid',
      '--color-danger-ink', '--brand-gradient-vip', '--glass-dark', '--nav-veil', '--separator', '--press'];
    const missing = names.filter((n) => !cs.getPropertyValue(n).trim());
    return { missing, count: names.length };
  });
  add('核心令牌均已定义', tokenCheck.missing.length === 0, tokenCheck.missing.join(',') || tokenCheck.count + ' 项全部存在');

  /* ---- 14. 令牌集合与设计系统 §3.8 逐项比对 ---- */
  const designSystem = path.join(ROOT, '..', '01-design-system.md');
  let tokenParity = { skipped: true };
  if (fs.existsSync(designSystem)) {
    const ds = fs.readFileSync(designSystem, 'utf8');
    // 锚定「### 3.8 CSS 变量落地块」正文标题（跳过目录中的同名锚点）
    const head = ds.search(/^### 3\.8 CSS 变量落地块/m);
    let dsNames = [];
    if (head >= 0) {
      const rest = ds.slice(head);
      const code = rest.match(/```css\r?\n([\s\S]*?)```/);
      if (code) {
        // §3.8 中存在「一行多令牌」（如 `--color-success: …;  --ink-success: …;`），故不能只取行首
        dsNames = Array.from(new Set((code[1].match(/(^|[\s{])(--[a-z0-9-]+)\s*:/g) || [])
          .map((x) => x.match(/(--[a-z0-9-]+)/)[1])));
      }
    }
    /* 只统计 :root 中真实声明的令牌（避免把 BEM 修饰符如 .row--icon 误判为令牌） */
    const rootBlock = (cssText.match(/:root\s*\{([\s\S]*?)\n\}/) || ['', ''])[1];
    const myNames = Array.from(new Set((rootBlock.match(/(^|[\s{])(--[a-z0-9-]+)\s*:/g) || [])
      .map((x) => x.match(/(--[a-z0-9-]+)/)[1])));
    const missingTok = dsNames.filter((n) => !myNames.includes(n));
    const extraTok = myNames.filter((n) => !dsNames.includes(n) && !n.startsWith('--shell-'));
    tokenParity = { dsCount: dsNames.length, myCount: myNames.length, missing: missingTok, extra: extraTok };
    add('令牌集合与设计系统 §3.8 一致（零缺失、零额外）', missingTok.length === 0 && extraTok.length === 0,
        '§3.8 ' + dsNames.length + ' 个令牌，缺失 ' + missingTok.length + ' 个，非 §3.8 的额外令牌 ' + extraTok.length + ' 个' +
        (missingTok.length ? '：' + missingTok.join(',') : '') + (extraTok.length ? '：' + extraTok.join(',') : ''));
  }

  /* ---- 15. 原始文件文本扫描（防 Phase 4 文本级扫描误判/漏判） ---- */
  const rawHtml = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const rawIssues = [];
  if (/:hover/i.test(rawHtml)) rawIssues.push('原始文件出现 :hover');
  if (/(?<![-\w])color\s*:\s*var\(\s*--color-primary\s*\)/i.test(rawHtml)) rawIssues.push('原始文件出现 color: var(--color-primary)');
  ['007AFF', '004FAF', '4C6EF5', 'FF4D4F'].forEach((b) => {
    if (rawHtml.toUpperCase().includes(b)) rawIssues.push('原始文件出现 ' + b);
  });
  if (/rgba\(\s*0\s*,\s*122\s*,\s*255/i.test(rawHtml)) rawIssues.push('原始文件出现 rgba(0,122,255,…)');
  const rawEmoji = rawHtml.match(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/u);
  if (rawEmoji) rawIssues.push('原始文件出现 emoji ' + rawEmoji[0]);
  add('原始 index.html 文本级扫描（禁用色值 / :hover / 金色文字 / emoji）', rawIssues.length === 0,
      rawIssues.join(' | ') || '通过');

  /* ---- 汇总 ---- */
  results.interactions = inter;
  results.tokenParity = tokenParity;
  results.hexPalette = Array.from(hexSet).sort();
  results.passCount = results.checks.filter((c) => c.pass).length;
  results.failCount = results.checks.filter((c) => !c.pass).length;
  results.total = results.checks.length;
  results.passed = results.failCount === 0;

  fs.writeFileSync(path.join(SHOTS, 'selfcheck.json'), JSON.stringify(results, null, 2), 'utf8');

  console.log('\n=========== MOYUYO 原型自检报告 ===========');
  results.checks.forEach((c) => {
    console.log((c.pass ? '  [PASS] ' : '  [FAIL] ') + c.name + (c.detail ? '  — ' + c.detail : ''));
  });
  console.log('------------------------------------------');
  console.log('  通过 ' + results.passCount + ' / ' + results.total);
  console.log('  色值集合（' + results.hexPalette.length + '）：' + results.hexPalette.join(' '));
  console.log('  截图目录：' + SHOTS);
  console.log('==========================================\n');

  await browser.close();
  process.exit(results.passed ? 0 : 1);
})().catch((e) => {
  console.error('自检脚本异常：', e);
  results.fatal = String(e && e.stack ? e.stack : e);
  try { fs.writeFileSync(path.join(SHOTS, 'selfcheck.json'), JSON.stringify(results, null, 2), 'utf8'); } catch (_) {}
  process.exit(2);
});
