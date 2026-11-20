/**
 * Phase 3 原型自检脚本
 * - 逐 tab 截图（浅色 / 深色）
 * - 逐状态态截图
 * - 控制台报错检查
 * - img broken 检查
 * - 颜色/emoji grep 自查
 */
const { chromium } = require('D:/MOYUYOWPC/node_modules/playwright');
const path = require('path');
const fs = require('fs');

const PROTO = 'file:///D:/MOYUYOWPC/.workbuddy/design/prototype/index.html';
const SHOTS = 'D:/MOYUYOWPC/.workbuddy/design/prototype/_shots';

const TABS = ['pet', 'home', 'community', 'messages', 'user'];

const STATES = {
  pet: ['normal'],
  home: ['normal', 'loading', 'empty', 'error', 'fallback'],
  community: ['normal', 'loading', 'empty', 'login', 'error'],
  messages: ['normal', 'loading', 'empty', 'login', 'offline'],
  user: ['normal', 'loading', 'login', 'vipError'],
};

(async () => {
  if (!fs.existsSync(SHOTS)) fs.mkdirSync(SHOTS, { recursive: true });
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 1000 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();

  const consoleErrors = [];
  const pageErrors = [];
  page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') consoleErrors.push(`[${m.type()}] ${m.text()}`); });
  page.on('pageerror', e => pageErrors.push(String(e)));

  await page.goto(PROTO, { waitUntil: 'load' });
  await page.waitForTimeout(600);

  // ---------- 逐 tab × 明暗截图 ----------
  for (const theme of ['light', 'dark']) {
    await page.click(`#pbTheme button[data-theme="${theme}"]`);
    await page.waitForTimeout(350);
    for (const t of TABS) {
      await page.click(`#pbTabs button[data-page="${t}"]`);
      await page.waitForTimeout(450);
      const phone = await page.locator('#phone').boundingBox();
      await page.screenshot({
        path: path.join(SHOTS, `tab-${t}-${theme}.png`),
        clip: { x: phone.x - 14, y: phone.y - 14, width: phone.width + 28, height: phone.height + 28 },
      });
    }
  }

  // ---------- 逐状态态截图 ----------
  await page.click('#pbTheme button[data-theme="light"]');
  await page.waitForTimeout(250);
  for (const t of TABS) {
    await page.click(`#pbTabs button[data-page="${t}"]`);
    await page.waitForTimeout(250);
    for (const st of STATES[t]) {
      if (st === 'normal') continue;
      await page.selectOption('#pbState', st);
      await page.waitForTimeout(400);
      const phone = await page.locator('#phone').boundingBox();
      await page.screenshot({
        path: path.join(SHOTS, `state-${t}-${st}.png`),
        clip: { x: phone.x - 14, y: phone.y - 14, width: phone.width + 28, height: phone.height + 28 },
      });
    }
  }

  // ---------- 交互冒烟 ----------
  const inter = [];
  await page.click('#pbTabs button[data-page="home"]');
  await page.waitForTimeout(250);
  await page.selectOption('#pbState', 'normal');
  await page.waitForTimeout(250);
  // home 3 tab
  await page.click('#page-home .shelf-tab[data-i="1"]');
  await page.waitForTimeout(200);
  inter.push('home shelf tab switch: ' + (await page.locator('#page-home .shelf-tab.on').innerText()));
  // 商品卡 toast
  await page.click('#page-home .prod-card');
  await page.waitForTimeout(300);
  inter.push('prod card toast visible: ' + await page.locator('#toast.show').count());
  await page.waitForTimeout(1700);

  // community
  await page.click('#pbTabs button[data-page="community"]');
  await page.waitForTimeout(250);
  await page.selectOption('#pbState', 'normal');
  await page.waitForTimeout(350);
  const likeBefore = await page.locator('#page-community .lead-post .pa .n').first().innerText();
  await page.click('#page-community .lead-post .pa[data-k="lead"]');
  await page.waitForTimeout(300);
  const likeAfter = await page.locator('#page-community .lead-post .pa .n').first().innerText();
  inter.push(`community like: ${likeBefore} -> ${likeAfter} / on=${await page.locator('#page-community .lead-post .pa.on').count()}`);
  // community tab
  await page.click('#page-community .issue-tab[data-i="2"]');
  await page.waitForTimeout(250);
  inter.push('community issue tab: ' + (await page.locator('#page-community .issue-tab.on').innerText()));
  await page.click('#page-community .issue-tab[data-i="0"]');
  await page.waitForTimeout(250);
  // FAB
  await page.click('#fab');
  await page.waitForTimeout(300);
  inter.push('FAB action sheet open: ' + await page.locator('#overlay.show .asheet').count());
  await page.click('#overlay .asheet .cancel');
  await page.waitForTimeout(250);

  // messages
  await page.click('#pbTabs button[data-page="messages"]');
  await page.waitForTimeout(250);
  await page.selectOption('#pbState', 'normal');
  await page.waitForTimeout(350);
  const row = page.locator('#page-messages .conv-row').first();
  const body = row.locator('.conv-body');
  const bx = await body.boundingBox();
  await page.mouse.move(bx.x + 120, bx.y + bx.height / 2);
  await page.mouse.down();
  await page.mouse.move(bx.x + 30, bx.y + bx.height / 2, { steps: 12 });
  await page.mouse.up();
  await page.waitForTimeout(400);
  const tf = await body.evaluate(el => el.style.transform);
  inter.push('messages swipe transform: ' + tf);
  await page.locator('#page-messages .swipe-del').first().click();
  await page.waitForTimeout(300);
  inter.push('delete confirm modal: ' + await page.locator('#overlay.show .sheet h4').innerText());
  await page.click('#overlay .act-cancel');
  await page.waitForTimeout(200);

  // user
  await page.click('#pbTabs button[data-page="user"]');
  await page.waitForTimeout(250);
  await page.selectOption('#pbState', 'normal');
  await page.waitForTimeout(350);
  await page.click('#page-user .vip-card');
  await page.waitForTimeout(250);
  inter.push('user vip toast: ' + await page.locator('#toast.show').count());
  await page.waitForTimeout(1700);
  await page.click('#page-user .band-item');
  await page.waitForTimeout(250);
  inter.push('user band toast: ' + await page.locator('#toast.show').count());
  await page.waitForTimeout(1700);
  // 未登录 → 功能项弹登录引导
  await page.selectOption('#pbState', 'login');
  await page.waitForTimeout(350);
  await page.click('#page-user .feature-row');
  await page.waitForTimeout(300);
  inter.push('user feature login modal: ' + (await page.locator('#overlay.show .sheet h4').innerText()) +
             ' / ' + (await page.locator('#overlay.show .sheet p').innerText()));
  await page.click('#overlay .act-cancel');
  await page.waitForTimeout(200);
  await page.selectOption('#pbState', 'normal');
  await page.waitForTimeout(300);

  // pet
  await page.click('#pbTabs button[data-page="pet"]');
  await page.waitForTimeout(250);
  await page.selectOption('#pbState', 'normal');
  await page.waitForTimeout(350);
  await page.click('#page-pet .pet-sw[data-i="1"]');
  await page.waitForTimeout(300);
  inter.push('pet switch selected: ' + (await page.locator('#page-pet .pet-sw.on').innerText()).replace(/\n/g, '/'));
  const phone = await page.locator('#phone').boundingBox();
  await page.screenshot({ path: path.join(SHOTS, 'interact-pet-switch.png'), clip: { x: phone.x - 14, y: phone.y - 14, width: phone.width + 28, height: phone.height + 28 } });

  // ---------- 空态引导按钮 ----------
  await page.click('#pbTabs button[data-page="home"]');
  await page.waitForTimeout(200);
  await page.selectOption('#pbState', 'empty');
  await page.waitForTimeout(300);
  await page.click('#page-home .empty .btn');
  await page.waitForTimeout(250);
  inter.push('home empty CTA toast: ' + await page.locator('#toast.show').count());
  await page.waitForTimeout(1700);

  // ---------- 弹层截图 ----------
  await page.click('#pbTabs button[data-page="user"]');
  await page.selectOption('#pbState', 'login');
  await page.waitForTimeout(400);
  await page.click('#page-user .feature-row');
  await page.waitForTimeout(350);
  const p2 = await page.locator('#phone').boundingBox();
  await page.screenshot({ path: path.join(SHOTS, 'overlay-login-modal.png'), clip: { x: p2.x - 14, y: p2.y - 14, width: p2.width + 28, height: p2.height + 28 } });

  // ---------- Spec 面板截图 ----------
  await page.click('#overlay .act-cancel');
  await page.waitForTimeout(200);
  const collapsed = await page.$eval('#spec', el => el.classList.contains('is-collapsed'));
  if (collapsed) await page.click('#pbSpec');
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(SHOTS, 'spec-panel.png'), clip: { x: 0, y: 0, width: 1500, height: 1000 } });
  await page.locator('#spec').screenshot({ path: path.join(SHOTS, 'spec-panel-closeup.png') });
  // 全部页面 scope
  await page.click('#specFilter button[data-scope="all"]');
  await page.waitForTimeout(400);
  await page.locator('#spec').screenshot({ path: path.join(SHOTS, 'spec-panel-all.png') });
  await page.click('#specFilter button[data-scope="page"]');
  await page.waitForTimeout(300);

  // ---------- v1.5 / Phase 2 新增断言 ----------
  const v15 = [];
  // 1) 会员卡：56rpx = 等级名英文 + 中文副标；卡上无积分数字
  await page.click('#pbTabs button[data-page="user"]');
  await page.waitForTimeout(250);
  await page.selectOption('#pbState', 'normal');
  await page.waitForTimeout(350);
  const card = await page.evaluate(() => {
    const c = document.querySelector('#page-user .vip-card');
    return c ? { html: c.innerText, hasPoints: /Points|2,480/.test(c.innerText),
                 hasLevel: /Platinum/.test(c.innerText), hasCn: /铂金/.test(c.innerText),
                 hasNum: /1,240/.test(c.innerText) } : null;
  });
  v15.push('会员卡: ' + JSON.stringify(card));
  // 2) 未登录：全部数据带隐藏 + 会员入口暗示存在 + 8 项加锁 3 项不锁
  await page.selectOption('#pbState', 'login');
  await page.waitForTimeout(350);
  const lg = await page.evaluate(() => ({
    vip: !!document.querySelector('#page-user .vip-card'),
    bands: document.querySelectorAll('#page-user .band').length,
    hint: !!document.querySelector('#page-user .vip-hint'),
    locks: document.querySelectorAll('#page-user .feature-row .lock').length,
    rows: document.querySelectorAll('#page-user .feature-row').length
  }));
  v15.push('未登录隐藏: ' + JSON.stringify(lg) + '  (Phase 2 v1.5 O8: 锁图标应为 0，按需拦截)');
  await page.selectOption('#pbState', 'normal');
  await page.waitForTimeout(300);

  // 3) messages 两段式 Tab：默认私信 → 切通知 → 点 4 宫格切段+筛类型
  await page.click('#pbTabs button[data-page="messages"]');
  await page.waitForTimeout(300);
  v15.push('默认段: ' + (await page.locator('#page-messages .msg-seg-tab.on').innerText()).replace(/\n/g,''));
  await page.click('#page-messages .msg-seg-tab[data-i="1"]');
  await page.waitForTimeout(300);
  v15.push('切到通知段: ' + (await page.locator('#page-messages .msg-seg-tab.on').innerText()) +
           ' / 宫格数=' + await page.locator('#page-messages .nr-item').count() +
           ' / 筛选chip=' + await page.locator('#page-messages .filter-chip').count());
  await page.click('#page-messages .nr-item[data-i="2"]');
  await page.waitForTimeout(300);
  v15.push('点宫格后段=' + (await page.locator('#page-messages .msg-seg-tab.on').innerText()) +
           ' 选中筛选=' + (await page.locator('#page-messages .filter-chip.on').innerText()));
  await page.click('#page-messages .msg-seg-tab[data-i="0"]');
  await page.waitForTimeout(250);

  // 4) tabBar 角标：只有 messages 挂数字
  const badges = await page.evaluate(() => {
    const out = [];
    document.querySelectorAll('.tb-item').forEach(b => out.push([b.getAttribute('data-page'), !!b.querySelector('.tb-badge')]));
    return out;
  });
  v15.push('tabBar 角标: ' + JSON.stringify(badges));

  // 5) 首页产品线索引行 4 chip
  await page.click('#pbTabs button[data-page="home"]');
  await page.waitForTimeout(300);
  v15.push('产品线索引行: ' + await page.locator('#page-home .line-chip').count() +
           ' chips → ' + (await page.locator('#page-home .line-index').innerText()).replace(/\n/g,' / '));
  v15.push('Hero kicker: ' + (await page.locator('#page-home .hero-copy .kicker').innerText()));

  // 6) community：产品线 chip 形态 + 搜索条三 tab 都有 + 话题栏只在话题 tab
  await page.click('#pbTabs button[data-page="community"]');
  await page.waitForTimeout(300);
  const comm = await page.evaluate(() => ({
    searchOnRecommend: !!document.querySelector('#page-community .search-hairline'),
    topicRailOnRecommend: !!document.querySelector('#page-community .topic-rail'),
    entryChips: document.querySelectorAll('#page-community .topic-chip.entry').length,
    linechip: document.querySelector('#page-community .linechip') ? document.querySelector('#page-community .linechip').innerText : null,
    barWidth: document.querySelector('#page-community .linechip .bar') ? getComputedStyle(document.querySelector('#page-community .linechip .bar')).width : null,
    chipBg: document.querySelector('#page-community .linechip') ? getComputedStyle(document.querySelector('#page-community .linechip')).backgroundColor : null,
    chipColor: document.querySelector('#page-community .linechip') ? getComputedStyle(document.querySelector('#page-community .linechip')).color : null
  }));
  v15.push('community 推荐段: ' + JSON.stringify(comm));
  await page.click('#page-community .issue-tab[data-i="2"]');
  await page.waitForTimeout(300);
  v15.push('话题 tab 默认态: 选择器网格=' + (await page.locator('#page-community .topic-grid .topic-chip').count()) +
           ' 搜索条=' + (await page.locator('#page-community .search-hairline').count()) +
           ' 标题=' + (await page.locator('#page-community .topic-sel-head h4').innerText()));
  await page.click('#page-community .topic-grid .topic-chip[data-i="3"]');
  await page.waitForTimeout(300);
  v15.push('选中后: 已选chip=' + (await page.locator('#page-community .topic-selected .topic-chip').count()) +
           ' 选择器收起=' + (await page.locator('#page-community .topic-grid').count()===0) +
           ' 帖子数=' + (await page.locator('#page-community .feed-post').count()));
  await page.click('#page-community .topic-selected .topic-chip');
  await page.waitForTimeout(250);
  v15.push('清除后回到选择器: ' + (await page.locator('#page-community .topic-grid .topic-chip').count()) + ' chips');

  // 7) 展示体使用处（应仅 wordmark / hero title / vip level）
  const displayUse = await page.evaluate(() => {
    const out = [];
    document.querySelectorAll('#viewport *').forEach(el => {
      const cs = getComputedStyle(el);
      if ((cs.fontFamily||'').indexOf('Playfair') >= 0 && el.textContent.trim() && el.children.length === 0) {
        out.push((el.className||el.tagName) + ':' + el.textContent.trim().slice(0,14));
      }
    });
    return out;
  });
  const displayKinds = Array.from(new Set(displayUse.map(x => x.split(':')[0])));
  v15.push('展示体使用处: ' + JSON.stringify(displayUse) +
           ' → 唯一类别=' + JSON.stringify(displayKinds) +
           ' (应仅 wordmark / hero-title / vip-level-en 三类)');
  v15.push('锁图标数(应为0): ' + await page.locator('#viewport .lock').count());
  const bandFF = await page.evaluate(() => {
    const g = s => { const e = document.querySelector(s); return e ? getComputedStyle(e).fontFamily : null; };
    return { walletNum: g('#page-user .wallet-band .num'), socialNum: g('#page-user .social-band .num'),
             segTab: g('#page-messages .msg-seg-tab'),
             walletWeight: getComputedStyle(document.querySelector('#page-user .wallet-band .num')).fontWeight };
  });
  v15.push('字族断言: ' + JSON.stringify(bandFF));

  console.log('=== v1.5 / PHASE2 ASSERTIONS ===');
  console.log(v15.join('\n'));

  // ---------- broken img / console ----------
  await page.click('#pbTabs button[data-page="home"]');
  await page.waitForTimeout(500);
  const imgStats = await page.evaluate(() => {
    const imgs = Array.from(document.querySelectorAll('#viewport img'));
    const broken = imgs.filter(i => !i.complete || i.naturalWidth === 0).length;
    const fallback = document.querySelectorAll('#viewport .ph.is-broken').length;
    return { total: imgs.length, broken, fallback };
  });

  console.log('=== CONSOLE ERRORS/WARNINGS ===');
  console.log(consoleErrors.length ? consoleErrors.join('\n') : '(none)');
  console.log('=== PAGE ERRORS ===');
  console.log(pageErrors.length ? pageErrors.join('\n') : '(none)');
  console.log('=== IMAGES ===');
  console.log(JSON.stringify(imgStats));
  console.log('=== INTERACTIONS ===');
  console.log(inter.join('\n'));

  await browser.close();
})();
