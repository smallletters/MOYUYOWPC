/* ==========================================================================
   MOYUYO ATELIER · 五 Tab 可交互原型
   js/app.js  —— 传统脚本，file:// 直接运行，无依赖
   --------------------------------------------------------------------------
   结构：
     §1 工具        §2 状态       §3 外壳（面板 / 尺寸 / 主题 / 状态）
     §4 渲染总控    §5 顶栏与通用  §6 五个页面
     §7 浮层        §8 手势       §9 事件分发   §10 启动
   ========================================================================== */
(function () {
  'use strict';

  var D = window.MOYUYO_DATA;

  /* ==========================================================================
     §1 工具
     ========================================================================== */
  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function ico(name, extra) {
    return '<span class="luc luc-' + name + (extra ? ' ' + extra : '') + '"></span>';
  }

  var rpxUnit = 0.52;
  function px(n) { return n * rpxUnit; }

  function reducedMotion() {
    return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function nextFrame(fn) { requestAnimationFrame(function () { requestAnimationFrame(fn); }); }

  /* ==========================================================================
     §2 状态
     ========================================================================== */
  var TABS = [
    { key: 'pet', label: '宠物', icon: 'paw-print' },
    { key: 'home', label: '首页', icon: 'home' },
    { key: 'community', label: '社区', icon: 'message-square' },
    { key: 'messages', label: '消息', icon: 'mail' },
    { key: 'user', label: '我的', icon: 'user' }
  ];

  var TAB_NAME = { pet: 'Pet', home: 'Home', community: 'Community', messages: 'Messages', user: 'User' };

  function defaults() {
    return {
      theme: 'light',
      size: '390',
      status: 'normal',
      hotspots: false,
      tab: 'pet',
      homeTab: 0,
      cmTab: 0,
      petIndex: 0,
      bannerIndex: 0,
      loadedHome: [true, false, false],
      loadedCm: [true, false, false],
      homePage: 1,
      cmPage: 1,
      cmLoading: false,
      liked: {},
      convs: D.conversations.map(function (c) { return { id: c.id, name: c.name, letter: c.letter, preview: c.preview, time: c.time, unread: c.unread, cs: !!c.cs, ava: c.ava || '' }; }),
      convPage: 1,
      openConv: null,
      lastSwipe: 0,
      stack: []
    };
  }

  var S = defaults();

  function unreadTotal() {
    return S.convs.reduce(function (a, c) { return a + (c.unread || 0); }, 0);
  }

  function findPost(id) {
    var keys = Object.keys(D.posts);
    for (var i = 0; i < keys.length; i++) {
      var arr = D.posts[keys[i]];
      for (var j = 0; j < arr.length; j++) if (arr[j].id === id) return arr[j];
    }
    return null;
  }

  /* ==========================================================================
     §3 外壳
     ========================================================================== */
  var SIZE_MAP = {
    '390': { w: 390, h: 844, sb: 47, sab: 34 },
    '430': { w: 430, h: 932, sb: 47, sab: 34 },
    '375': { w: 375, h: 667, sb: 20, sab: 0 }
  };

  function applySize() {
    var m = SIZE_MAP[S.size];
    var phone = $('#phone');
    phone.setAttribute('data-size', S.size);
    rpxUnit = m.w / 750;
    phone.style.setProperty('--rpx', rpxUnit + 'px');
    phone.style.setProperty('--sb-h', m.sb + 'px');
    phone.style.setProperty('--sab', m.sab + 'px');
    phone.style.setProperty('--vsh', (m.h - m.sb - 50 - m.sab) + 'px');
  }

  function applyTheme() {
    var root = document.documentElement;
    if (S.theme === 'auto') {
      var dark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      root.className = dark ? 'theme-dark' : 'theme-light';
    } else {
      root.className = 'theme-' + S.theme;
    }
  }

  if (window.matchMedia) {
    var mq = window.matchMedia('(prefers-color-scheme: dark)');
    var onMq = function () { if (S.theme === 'auto') { applyTheme(); paint({ keepScroll: true }); } };
    if (mq.addEventListener) mq.addEventListener('change', onMq);
    else if (mq.addListener) mq.addListener(onMq);
  }

  function markSeg(group, value) {
    $$('[data-act="' + group + '"]').forEach(function (b) {
      b.classList.toggle('is-on', b.getAttribute('data-arg') === String(value));
    });
  }

  function syncPanel() {
    markSeg('theme', S.theme);
    markSeg('size', S.size);
    markSeg('status', S.status);
    $('#nowTab').textContent = TAB_NAME[S.tab];
    var stName = { normal: '正常', loading: '加载', empty: '空', error: '错误', guest: '未登录', stale: '弱网' }[S.status];
    $('#nowMeta').textContent = stName + ' · ' + SIZE_MAP[S.size].w + ' × ' + SIZE_MAP[S.size].h;
    var sw = $('[data-act="hotspot"]');
    sw.setAttribute('aria-checked', S.hotspots ? 'true' : 'false');
  }

  /* ==========================================================================
     §4 渲染总控
     ========================================================================== */
  function paint(opts) {
    opts = opts || {};
    var screen = $('#screen');
    var keep = opts.keepScroll ? screen.scrollTop : 0;
    $('#phone').setAttribute('data-page', S.tab);
    screen.innerHTML = pageHTML();
    screen.scrollTop = keep;
    afterPaint();
    renderTabbar();
    renderFab();
    syncPanel();
  }

  function afterPaint() {
    // 下划线位置
    $$('.tabs').forEach(function (tabs) {
      var idx = parseInt(tabs.getAttribute('data-active'), 10) || 0;
      placeUnderline(tabs, idx, false);
    });
    // Banner
    var track = $('.banner__track');
    if (track) track.style.transform = 'translateX(' + (-S.bannerIndex * 100) + '%)';
    // 自动播放
    stopBanner();
    if ($('.banner__track') && !reducedMotion()) startBanner();
  }

  function placeUnderline(tabs, idx, animate) {
    var bar = $('.tabs__bar', tabs);
    var items = $$('.tabs__item', tabs);
    var it = items[idx];
    if (!bar || !it) return;
    var r = it.getBoundingClientRect();
    var tr = tabs.getBoundingClientRect();
    var x = (r.left - tr.left) + r.width / 2 - bar.offsetWidth / 2;
    if (!animate) {
      bar.style.transition = 'none';
      bar.style.transform = 'translateX(' + x + 'px)';
      // 强制回流后恢复过渡
      void bar.offsetWidth;
      bar.style.transition = '';
    } else {
      bar.style.transform = 'translateX(' + x + 'px)';
    }
  }

  /* ---------- FAB（贴在手机视口内，不随内容滚动） ---------- */
  function fabHost() {
    var h = $('#fabHost');
    if (!h) {
      h = document.createElement('div');
      h.className = 'fab-host';
      h.id = 'fabHost';
      $('#phone').appendChild(h);
    }
    return h;
  }

  function renderFab() {
    var host = fabHost();
    var show = (S.tab === 'community');
    host.innerHTML = show
      ? '<button class="fab" data-hot data-act="cm-publish" aria-label="发布新帖子">' + ico('camera') + '</button>'
      : '';
  }

  /* ---------- tabBar ---------- */
  function renderTabbar() {
    var total = S.status === 'guest' ? 0 : unreadTotal();
    var html = TABS.map(function (t) {
      var on = t.key === S.tab;
      var dot = (t.key === 'messages' && total > 0) ? '<i class="tab__dot" data-role="msgdot"></i>' : '';
      return '<button class="tab' + (on ? ' is-on' : '') + '" role="tab" aria-selected="' + on + '"'
        + ' data-act="tab" data-arg="' + t.key + '" aria-label="' + t.label + '">'
        + '<span class="tab__ico">' + ico(t.icon) + dot + '</span>'
        + '<span class="tab__label">' + t.label + '</span>'
        + '</button>';
    }).join('');
    $('#tabbar').innerHTML = html;
  }

  /* ---------- 页面分发 ---------- */
  function pageHTML() {
    switch (S.tab) {
      case 'pet': return pagePet();
      case 'home': return pageHome();
      case 'community': return pageCommunity();
      case 'messages': return pageMessages();
      case 'user': return pageUser();
    }
    return '';
  }

  /* ==========================================================================
     §5 共用片段
     ========================================================================== */
  function art(item, ratio, size, iconPx) {
    /* 有真实图时用 art--photo 隐藏图标/角标，色块保留为加载兜底：
       img 加载失败（onerror）就移除 art--photo 并自删，占位色块自然露出 */
    var img = item.img
      ? '<img class="art__img" src="assets/photo/' + item.img + '" alt="" loading="lazy"'
        + ' onerror="this.parentNode.classList.remove(\'art--photo\');this.remove()">'
      : '';
    return '<div class="art ' + ratio + (size ? ' ' + size : '') + (item.img ? ' art--photo' : '') + '" style="--c1:' + item.c1 + ';--c2:' + item.c2 + '">'
      + '<span class="luc luc-' + item.icon + ' art__ico"' + (iconPx ? ' style="font-size:calc(' + iconPx + ' * var(--rpx))"' : '') + '></span>'
      + '<span class="art__tag">' + esc(item.tag || item.line) + '</span>'
      + img
      + '</div>';
  }

  function staleBar(text) {
    return '<div class="stale-bar" role="status">' + ico('signal') + '<span>' + esc(text) + '</span></div>';
  }

  function emptyState(o) {
    // 兜底：任何分支漏传字段都不能渲染出空标题 / 空气按钮
    o = o || {};
    return '<div class="empty">'
      + '<span class="luc luc-' + (o.icon || 'inbox') + ' empty__ico"></span>'
      + '<h3 class="empty__title">' + esc(o.title || '暂时没有内容') + '</h3>'
      + (o.desc ? '<p class="empty__desc">' + esc(o.desc) + '</p>' : '')
      + (o.btn
        ? '<div class="empty__act">'
          + '<button class="pbtn ' + (o.primary ? 'pbtn--primary' : 'pbtn--secondary') + '" data-act="' + (o.act || 'toast') + '"'
          + (o.arg ? ' data-arg="' + esc(o.arg) + '"' : '') + ' data-hot>' + esc(o.btn) + '</button>'
          + '</div>'
        : '')
      + '</div>';
  }

  function skeletonProduct() {
    // 定值 584rpx，照 01 §4.2.2.2 的 10 项清单排：
    // 图 331 + 上内距 20 + 标题 78 + 12 + 描述 39 + 12 + 价格 36 + 12 + 评分 24 + 下内距 20
    // 标题的 78 槽位内放两条 33 + 12 间距（看起来是两行字，几何仍等于 78）
    var bar = function (h, w) {
      return '<div class="sk" style="height:calc(' + h + ' * var(--rpx));width:' + w + ';border-radius:calc(8 * var(--rpx))"></div>';
    };
    var gap = '<div style="height:calc(12 * var(--rpx))"></div>';
    return '<div class="sk-card" style="background:var(--color-surface);box-shadow:var(--shadow-card);overflow:hidden;height:calc(584 * var(--rpx))">'
      + '<div class="sk" style="height:0;padding-top:100%;border-radius:0"></div>'
      + '<div style="height:calc(253 * var(--rpx));padding:calc(20 * var(--rpx));display:flex;flex-direction:column">'
        + '<div style="height:calc(78 * var(--rpx));display:flex;flex-direction:column;justify-content:space-between">'
          + bar(33, '94%') + bar(33, '62%')
        + '</div>'
        + gap + bar(39, '86%')
        + gap + bar(36, '46%')
        + gap + bar(24, '52%')
      + '</div></div>';
  }

  /* 骨架卡按「3 图 + 4 行正文 + 互动行」的真实卡几何排：
     112(头行) + 207(3 图) + 205(正文块) + 88(互动行) ≈ 612rpx，与真实 3 图卡 613 基本等高，
     切态时首卡顶边不跳、滚动位置不变（02 §3.4.1 B 类） */
  function skeletonPost() {
    var bar = function (w, top) {
      return '<div class="sk" style="height:calc(32 * var(--rpx));width:' + w + ';'
        + (top ? 'margin-top:calc(15 * var(--rpx));' : '') + 'border-radius:calc(8 * var(--rpx))"></div>';
    };
    var cell = '<div class="sk" style="height:0;padding-top:100%;border-radius:calc(16 * var(--rpx))"></div>';
    var pill = '<div class="sk" style="width:calc(64 * var(--rpx));height:calc(32 * var(--rpx));border-radius:999px"></div>';
    return '<div class="sk-card" style="background:var(--color-surface);box-shadow:var(--shadow-card);margin:0 calc(32 * var(--rpx));height:calc(613 * var(--rpx))">'
      + '<div style="display:flex;gap:calc(16 * var(--rpx));align-items:center;height:calc(72 * var(--rpx));margin:calc(24 * var(--rpx)) calc(24 * var(--rpx)) 0">'
        + '<div class="sk sk-circle" style="width:calc(72 * var(--rpx));height:calc(72 * var(--rpx));flex:0 0 auto"></div>'
        + '<div style="flex:1">'
          + '<div class="sk" style="height:calc(28 * var(--rpx));width:42%"></div>'
          + '<div class="sk" style="height:calc(20 * var(--rpx));width:26%;margin-top:calc(8 * var(--rpx))"></div>'
        + '</div>'
      + '</div>'
      + '<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:calc(8 * var(--rpx));margin:calc(16 * var(--rpx)) calc(24 * var(--rpx)) 0">' + cell + cell + cell + '</div>'
      + '<div style="padding:calc(16 * var(--rpx)) calc(24 * var(--rpx))">'
        + bar('96%') + bar('88%', 1) + bar('92%', 1) + bar('58%', 1)
      + '</div>'
      + '<div style="display:flex;align-items:center;gap:calc(24 * var(--rpx));height:calc(88 * var(--rpx));padding:0 calc(24 * var(--rpx))">'
        + pill + pill + pill
      + '</div>'
      + '</div>';
  }

  function skeletonRow() {
    return '<div style="display:flex;gap:calc(20 * var(--rpx));align-items:center;height:calc(104 * var(--rpx));padding:0 calc(24 * var(--rpx))">'
      + '<div class="sk sk-circle" style="width:calc(88 * var(--rpx));height:calc(88 * var(--rpx));flex:0 0 auto"></div>'
      + '<div style="flex:1;min-width:0">'
      + '<div class="sk" style="height:calc(28 * var(--rpx));width:60%"></div>'
      + '<div class="sk" style="height:calc(22 * var(--rpx));width:40%;margin-top:calc(10 * var(--rpx))"></div>'
      + '</div></div>';
  }

  /* ==========================================================================
     §6 页面
     ========================================================================== */

  /* ---------------------------------------------------------------- 6.1 Pet */
  function petSceneView(pet, extra) {
    extra = extra || {};
    return '<div class="scene' + (extra.stale ? ' has-stale' : '') + '">'
      + '<img class="scene__img" src="assets/pet-hub-scene.jpg" alt="宠物空间场景">'
      + (pet ? '<div class="scene__tint" style="background:' + pet.tint + '"></div>' : '')
      + '<div class="scene__mask"></div>'
      + (extra.stale ? '<div class="scene__stale glass" role="status">' + ico('signal') + '<span>显示上次记录 · 2 小时前</span></div>' : '')
      + petStrip(pet)
      + (pet
        ? '<div class="scene__info glass">'
            + '<div class="kicker">' + esc(pet.breed) + '</div>'
            + '<div class="title">' + esc(pet.name) + '，' + esc(pet.age) + '</div>'
          + '</div>'
        : '')
      + '<div class="scene__actions">'
        + '<button class="gpill" data-hot data-act="pet-dressup" aria-label="装扮宠物">'
          + '<span class="gpill__c glass">' + ico('sparkles') + '<span>装扮</span>' + ico('chevron-down') + '</span>'
        + '</button>'
        + '<button class="gpill" data-hot data-act="pet-3d" aria-label="进入 3D 空间">'
          + '<span class="gpill__c glass">' + ico('grid') + '<span>3D 空间</span></span>'
        + '</button>'
      + '</div>'
      + (pet ? '<div class="scene__figure"></div>' : '')
      + (extra.hint || '')
      + '</div>';
  }

  function petStrip(active) {
    var items = active ? D.pets.map(function (p, i) {
      var on = active && active.id === p.id;
      /* 头像位：有实拍照片显示照片，否则回落品种图标 */
      var face = p.img
        ? '<img class="pet-ava__img" src="assets/photo/' + p.img + '" alt="" onerror="this.remove()">'
        : ico(p.icon);
      return '<button class="pet-ava' + (on ? ' is-on' : '') + '" data-hot data-act="pet-ava" data-i="' + i + '"'
        + ' aria-label="切换到 ' + esc(p.name) + '" aria-pressed="' + (on ? 'true' : 'false') + '">'
        + '<span class="pet-ava__ring">' + face + '</span></button>';
    }).join('') : '';
    var add = '<button class="pet-ava pet-ava--add" data-hot data-act="pet-profile" aria-label="添加宠物档案">'
      + '<span class="pet-ava__ring">' + ico('plus') + '</span></button>';
    return '<div class="pet-strip">' + items + add + '</div>';
  }

  function careDeck(pet, opts) {
    // opts.muted：错误态保留几何、只把数值位换成「—」并压暗。
    // 读不到 ≠ 是零 ≠ 删掉模块 —— 删掉会让整页从 1462 塌到 752rpx。
    var muted = !!(opts && opts.muted);
    var cols = pet.care.map(function (c) {
      var tone = muted ? 'ok' : c.tone;
      return '<div class="care__col"' + (muted ? '' : ' data-hot data-act="care-col" data-arg="pet-care-' + c.key + '" tabindex="0" role="button"') + ''
        + (muted ? '' : ' aria-label="' + esc(c.label) + '，' + esc(c.last) + '，' + esc(c.status) + '"') + '>'
        + '<div class="care__bar"><i style="background:' + (muted ? 'var(--color-fill-dim)' : 'var(--ink-' + (c.tone === 'ok' ? 'ok' : c.tone === 'warn' ? 'warn' : 'err') + ')') + '"></i></div>'
        + '<span class="ibox ibox--64 ibox--' + tone + ' care__icon">' + ico(c.icon) + '</span>'
        + '<span class="care__label">' + esc(c.label) + '</span>'
        + '<span class="care__value">' + (muted ? '—' : esc(c.last)) + '</span>'
        + '<span class="care__status' + (muted ? '' : ' care__status--' + c.tone) + '">' + (muted ? '读不到' : esc(c.status)) + '</span>'
        + '</div>';
    }).join('');

    return '<div class="care">'
      + '<div class="care__grid">' + cols + '</div>'
      + '<div class="care__divider"></div>'
      + '<div class="care__row"' + (muted ? '' : ' data-hot data-act="care-row" data-arg="pet-care-' + pet.next.key + '" tabindex="0" role="button"') + '>'
        + '<span class="lbl">' + esc(pet.next.label) + '</span>'
        + '<span class="val">' + (muted ? '—' : esc(pet.next.days)) + '</span>'
        + '<button class="care__more"' + (muted ? ' disabled' : ' data-hot data-act="care-row" data-arg="pet-care-' + pet.next.key + '"') + ' aria-label="查看护理记录">'
          + ico('more-horizontal') + '</button>'
      + '</div>'
      + '</div>';
  }

  function quickGrid() {
    return '<div class="quick">' + D.petQuick.map(function (q) {
      return '<button class="quick__item" data-hot data-act="pet-quick" data-arg="' + q.key + '">'
        + '<span class="ibox ibox--80">' + ico(q.icon) + '</span>'
        + '<span class="quick__label">' + esc(q.label) + '</span>'
        + '</button>';
    }).join('') + '</div>';
  }

  function pagePet() {
    var st = S.status;
    var html = '<div class="page page--pet" id="petPage">';

    if (st === 'loading') {
      html += '<div class="scene"><div class="scene__mask"></div>'
        + '<div class="pet-strip">' + '<div class="sk sk-circle" style="width:calc(80 * var(--rpx));height:calc(80 * var(--rpx));flex:0 0 auto"></div>'.repeat(4) + '</div>'
        + '<div class="scene__ph">' + ico('paw-print') + '</div>'
        + '</div>'
        + '<div class="care">'
        + '<div style="display:flex;gap:calc(16 * var(--rpx))">'
        + '<div class="sk" style="flex:1;height:calc(150 * var(--rpx))"></div>'
        + '<div class="sk" style="flex:1;height:calc(150 * var(--rpx))"></div>'
        + '<div class="sk" style="flex:1;height:calc(150 * var(--rpx))"></div>'
        + '</div>'
        + '<div class="sk" style="height:calc(88 * var(--rpx));margin-top:calc(24 * var(--rpx))"></div>'
        + '</div>'
        + '<div class="gap-48"></div>'
        + '<div class="pad"><div class="sk" style="height:calc(32 * var(--rpx));width:40%"></div></div>'
        + '<div class="gap-24"></div>'
        + '<div class="quick">' + '<div class="sk" style="flex:1;height:calc(126 * var(--rpx));border-radius:calc(16 * var(--rpx))"></div>'.repeat(5) + '</div>'
        + '<div class="page__bottom"></div>'
        + '</div>';
      return html;
    }

    if (st === 'empty' || st === 'guest') {
      var isGuest = st === 'guest';
      var hint = '<div style="position:absolute;left:0;right:0;bottom:calc(52 * var(--rpx));display:flex;justify-content:center">'
        + '<button class="scene__hint glass" data-hot data-act="' + (isGuest ? 'guest-login' : 'pet-profile') + '">'
        + ico('plus') + '<span>' + (isGuest ? '登录后可创建宠物档案' : '添加宠物档案') + '</span>'
        + '</button></div>';
      html += petSceneView(null, { hint: hint });
      html += '<div class="care">'
        + '<div class="empty" style="padding:calc(40 * var(--rpx)) calc(16 * var(--rpx))">'
        + '<span class="luc luc-paw-print empty__ico" style="font-size:calc(72 * var(--rpx))"></span>'
        + '<h3 class="empty__title">' + (isGuest ? '登录后即可管理宠物' : '还没有宠物档案') + '</h3>'
        + '<p class="empty__desc">' + (isGuest ? '登录后可以记录护理、疫苗与体重，并同步到所有设备。' : '创建档案后，我们会按品种与年龄给出护理提醒。') + '</p>'
        + '<div class="empty__act"><button class="pbtn pbtn--primary" data-hot data-act="' + (isGuest ? 'guest-login' : 'pet-profile') + '">'
        + (isGuest ? '去登录' : '创建宠物档案') + '</button></div>'
        + '</div></div>';
      html += '<div class="gap-48"></div>' + quickGrid() + '<div class="page__bottom"></div></div>';
      return html;
    }

    var pet = D.pets[S.petIndex];

    if (st === 'error') {
      var errHint = '<div style="position:absolute;left:0;right:0;bottom:calc(52 * var(--rpx));display:flex;justify-content:center">'
        + '<button class="scene__hint glass" data-hot data-act="retry"><span class="luc luc-refresh-cw"></span>'
        + '<span>宠物数据加载失败 · 重试</span></button></div>';
      html += petSceneView(null, { hint: errHint });
      // 错误态保留护理卡与快捷区的几何（内容灰化、数值位「—」），
      // 不让页面从 1462rpx 塌到 752rpx —— 切态不得改变几何。
      html += '<div style="opacity:.55">' + careDeck(pet, { muted: true }) + '</div>';
      html += '<div class="gap-48"></div>';
      html += '<div class="sect-head"><h2 class="sect-head__title">快捷操作</h2></div>';
      html += '<div class="gap-24"></div>';
      html += '<div style="opacity:.55">' + quickGrid() + '</div>';
      html += '<div class="page__bottom"></div></div>';
      return html;
    }

    // normal / stale
    html += petSceneView(pet, { stale: st === 'stale' });
    html += careDeck(pet);
    html += '<div class="gap-48"></div>';
    html += '<div class="sect-head"><h2 class="sect-head__title">快捷操作</h2></div>';
    html += '<div class="gap-24"></div>';
    html += quickGrid();
    html += '<div class="page__bottom"></div>';
    html += '</div>';
    return html;
  }

  /* --------------------------------------------------------------- 6.2 Home */
  function homeNav() {
    return '<div class="nav" data-role="nav">'
      + '<button class="searchbar" data-hot data-act="home-search" aria-label="搜索商品" role="searchbox">'
        + '<span class="searchbar__c">' + ico('search') + '<span class="searchbar__ph">搜索想要的…</span></span>'
      + '</button>'
      + '<div class="nav__actions">'
        + '<button class="icon-btn" data-hot data-act="toast" data-arg="请使用 APP 扫码" aria-label="扫一扫">' + ico('scan-line') + '</button>'
        + '<button class="icon-btn" data-hot data-act="home-notify" aria-label="通知，未读 5 条">'
          + ico('bell') + '<span class="badge">5</span>'
        + '</button>'
      + '</div></div>';
  }

  function kingkong() {
    return '<div class="kingkong">' + D.homeQuick.map(function (q) {
      return '<button class="kk" data-hot data-act="kk" data-arg="' + q.key + '">'
        + '<span class="ibox ibox--96">' + ico(q.icon) + '</span>'
        + '<span class="kk__label">' + q.label + '</span>'
        + '</button>';
    }).join('') + '</div>';
  }

  function bannerBlock() {
    var slides = D.banners.map(function (b, i) {
      return '<div class="banner__slide" data-act="banner-tap" data-i="' + i + '" role="button" tabindex="0"'
        + ' aria-label="第 ' + (i + 1) + ' 张，共 ' + D.banners.length + ' 张：' + esc(b.title) + '">'
        + '<div class="art' + (b.img ? ' art--photo' : '') + '" style="--c1:' + b.c1 + ';--c2:' + b.c2 + '">'
          + '<span class="luc luc-' + b.icon + ' art__ico"></span>'
          + '<span class="art__tag">' + b.line + '</span>'
          + (b.img ? '<img class="art__img" src="assets/photo/' + b.img + '" alt="" onerror="this.parentNode.classList.remove(\'art--photo\');this.remove()">' : '')
        + '</div>'
        + '<div class="banner__veil"></div>'
        + '<div class="banner__cap">'
          + '<span class="banner__tag">' + esc(b.tag) + '</span>'
          + '<span class="banner__title">' + esc(b.title) + '</span>'
        + '</div>'
        + '</div>';
    }).join('');

    var dots = D.banners.map(function (b, i) {
      return '<span class="banner__dot' + (i === S.bannerIndex ? ' is-on' : '') + '"></span>';
    }).join('');

    return '<div class="banner" data-role="banner">'
      + '<div class="banner__track">' + slides + '</div>'
      + '<div class="banner__dots">' + dots + '</div>'
      + '</div>';
  }

  function tabsBlock(id, items, active, cls) {
    return '<div class="tabs ' + (cls || '') + '" data-role="' + id + '" data-active="' + active + '" role="tablist">'
      + items.map(function (t, i) {
        return '<button class="tabs__item' + (i === active ? ' is-on' : '') + '" role="tab"'
          + ' aria-selected="' + (i === active) + '" data-hot data-act="' + id + '" data-i="' + i + '">' + esc(t) + '</button>';
      }).join('')
      + '<span class="tabs__bar"></span>'
      + '</div>';
  }

  function productCard(p, ti, pi) {
    return '<article class="pcard" data-hot data-act="goods" data-t="' + ti + '" data-i="' + pi + '"'
      + ' data-long="goods" tabindex="0" role="button"'
      + ' aria-label="' + esc(p.name) + '，' + p.price + ' 元，评分 ' + p.rating + '">'
      + art(p, 'ratio-1x1', '', 96)
      + '<div class="pcard__body">'
        + '<h3 class="pcard__name">' + esc(p.name) + '</h3>'
        + '<p class="pcard__desc">' + esc(p.desc) + '</p>'
        + '<div class="pcard__price">'
          + '<span class="pcard__now">' + p.price + '</span>'
          + '<span class="pcard__was">' + p.orig + '</span>'
        + '</div>'
        + '<div class="pcard__meta">' + ico('star') + '<span>' + p.rating + '</span>'
          + '<span class="sep">·</span><span>' + esc(p.sold) + ' 人已买</span></div>'
      + '</div></article>';
  }

  function homeListHTML() {
    var st = S.status;
    var ti = S.homeTab;

    if (st === 'loading') {
      return '<div class="grid2">' + new Array(4).fill(0).map(skeletonProduct).join('') + '</div>';
    }
    if (st === 'error') {
      return emptyState({
        icon: 'cloud', title: '加载失败，请检查网络',
        desc: '网络恢复后点重试即可。',
        btn: '重试', act: 'retry', primary: true
      });
    }
    if (st === 'empty') {
      return emptyState({
        icon: 'package', title: '暂时没有推荐',
        desc: '换个分类看看，或到领券中心逛逛。',
        btn: '去分类看看', act: 'home-category'
      });
    }

    var list = D.products[ti].slice(0, S.homePage * 8);
    var html = '<div class="grid2">' + list.map(function (p, i) { return productCard(p, ti, i); }).join('') + '</div>';
    if (S.homePage >= 2) {
      html += '<div class="loadmore loadmore--end">— 没有更多了 —</div>';
    } else {
      html += '<div class="loadmore" data-hot data-act="home-more" id="homeMore">' + ico('chevron-down') + '<span>加载更多</span></div>';
    }
    return html;
  }

  function pageHome() {
    var html = '<div class="page" id="homePage">';
    html += homeNav();
    if (S.status === 'stale') html += staleBar('离线内容 · 10 分钟前');
    html += '<div class="gap-24"></div>';
    html += kingkong();
    html += '<div class="gap-24"></div>';
    if (S.status === 'loading') {
      html += '<div class="pad"><div class="sk" style="height:0;padding-top:56.25%;border-radius:calc(32 * var(--rpx))"></div></div>';
    } else {
      html += bannerBlock();
    }
    html += '<div class="gap-32"></div>';
    if (S.status === 'loading') {
      html += '<div class="tabs"><div class="sk" style="height:calc(28 * var(--rpx));width:52%;margin:auto"></div></div>';
    } else {
      html += tabsBlock('home-tab', D.homeTabs, S.homeTab, 'tabs--sticky');
    }
    html += '<div class="gap-24"></div>';
    html += '<div id="homeList">' + homeListHTML() + '</div>';
    html += '<div class="page__bottom"></div>';
    html += '</div>';
    return html;
  }

  /* ---------------------------------------------------------- 6.3 Community */
  function cmNav() {
    return '<div class="nav nav--serif" data-role="nav">'
      + '<h1 class="nav__title">社区</h1>'
      + '<div class="nav__actions">'
        + '<button class="icon-btn" data-hot data-act="cm-search" aria-label="搜索帖子">' + ico('search') + '</button>'
        + '<button class="icon-btn" data-hot data-act="cm-notify" aria-label="通知，未读 3 条">'
          + ico('bell') + '<span class="badge">3</span>'
        + '</button>'
      + '</div></div>';
  }

  function cmFilters() {
    return '<div id="cmFilters">'
      + '<button class="cm-search" data-hot data-act="cm-search" aria-label="搜索帖子">'
        + '<span class="cm-search__c">' + ico('search') + '<span class="searchbar__ph">搜帖子 / 话题</span></span>'
      + '</button>'
      + '<div class="chipbar">'
        + D.topics.map(function (t) {
            return '<button class="chip" data-hot data-act="cm-topic" data-arg="' + esc(t) + '">'
              + '<span class="chip__c">' + esc(t) + '</span></button>';
          }).join('')
      + '</div></div>';
  }

  function postCard(p) {
    var liked = !!S.liked[p.id];
    var imgs = p.images;
    var imgHTML = '';
    if (imgs.length === 1) {
      // 单图跟随原图比例（widthFix 语义，01 v1.8）：原型用 mock 里每张图的原始比例模拟
      imgHTML = '<div class="post__imgs"><div class="post__img1">'
        + art(imgs[0], 'ratio-' + (p.ratio || '4x3'), '', 120) + '</div></div>';
    } else if (imgs.length > 1) {
      // 宫格列数三分支（主理人裁决 A）：2 或 4 图用 2 列，其余多图用 3 列。
      // 一律 3 列会让 2 图剩一个孤格、4 图第二行缺角。
      var gridCls = (imgs.length === 2 || imgs.length === 4) ? 'grid-imgs grid-imgs--2' : 'grid-imgs';
      imgHTML = '<div class="post__imgs"><div class="' + gridCls + '">'
        + imgs.map(function (im) { return art(im, 'ratio-1x1', 'art--sm'); }).join('') + '</div></div>';
    }

    return '<article class="post" data-act="post-open" data-id="' + p.id + '" tabindex="0" role="button"'
      + ' aria-label="' + esc(p.user) + '的帖子">'
      + '<div class="post__head">'
        + '<div class="avatar avatar--72" data-act="cm-author" data-arg="' + esc(p.user) + '">'
          + (p.img
            ? '<img class="avatar__img" src="assets/photo/' + p.img + '" alt="" loading="lazy" onerror="this.remove()">'
            : esc(p.avatar))
        + '</div>'
        + '<div class="post__who" data-act="cm-author" data-arg="' + esc(p.user) + '">'
          + '<span class="post__name">' + esc(p.user) + '</span>'
          + '<span class="post__time">' + esc(p.time) + '</span>'
        + '</div>'
        + '<button class="icon-btn icon-btn--quiet" data-hot data-act="post-more" data-id="' + p.id + '" aria-label="更多操作">'
          + ico('more-horizontal') + '</button>'
      + '</div>'
      + imgHTML
      + '<p class="post__text">' + esc(p.text) + '</p>'
      + '<div class="post__acts">'
        + '<button class="act' + (liked ? ' is-liked' : '') + '" data-hot data-act="like" data-id="' + p.id + '"'
          + ' aria-pressed="' + liked + '" aria-label="' + (liked ? '取消点赞' : '点赞') + '，当前 ' + p.likes + ' 次">'
          + ico('heart') + '<span class="act__count">' + p.likes + '</span>'
        + '</button>'
        + '<button class="act" data-hot data-act="comment" data-id="' + p.id + '" aria-label="评论，当前 ' + p.comments + ' 条">'
          + ico('message-circle') + '<span class="act__count">' + p.comments + '</span>'
        + '</button>'
        + '<button class="act" data-hot data-act="share" aria-label="分享这条帖子">'
          + ico('share-2') + '<span>分享</span>'
        + '</button>'
      + '</div></article>';
  }

  function cmListHTML() {
    var st = S.status, ti = S.cmTab;

    if (st === 'loading' || S.cmLoading) {
      return '<div style="display:flex;flex-direction:column;gap:calc(24 * var(--rpx))">'
        + skeletonPost() + skeletonPost() + skeletonPost() + '</div>';
    }

    if (st === 'guest') {
      if (ti === 1) {
        return '<div class="card card--shadow">' + emptyState({
          icon: 'lock', title: '关注流需要登录',
          desc: '登录后查看你关注的人的动态。',
          btn: '去登录', act: 'guest-login', primary: true
        }) + '</div>';
      }
      if (ti === 2) {
        return emptyState({
          icon: 'tag', title: '选择上方话题标签查看帖子',
          desc: '话题是导航入口，点进去看这个话题下的全部内容。', btn: '搜索帖子', act: 'cm-search'
        });
      }
    }

    if (st === 'error') {
      return emptyState({
        icon: 'cloud', title: '加载失败，请重试',
        desc: '内容还在，点击重试即可重新拉取。', btn: '重试', act: 'retry', primary: true
      });
    }

    if (st === 'empty') {
      // 三个 Tab 各自 return 完整对象；键名必须与 emptyState() 读取的一致
      // （title / desc / btn / act / primary）。曾经这里写成 t/d/b/a/p，
      // 导致空态渲染出空标题 + 一个 44×46px 的"空气按钮"。
      var map = [
        { icon: 'message-square', title: '还没有帖子，发一个吧～', desc: '第一条帖子的赞，总是来得最慢也最真。', btn: '发布帖子', act: 'cm-publish', primary: true },
        { icon: 'users', title: '还没有关注的人，去发现感兴趣的用户吧～', desc: '在推荐流里逛逛，看到喜欢的就关注。', btn: '去推荐看看', act: 'cm-tab-rec' },
        { icon: 'tag', title: '选择上方话题标签查看帖子', desc: '话题是导航入口，点进去看这个话题下的全部内容。', btn: '搜索帖子', act: 'cm-search' }
      ][ti] || { icon: 'message-square', title: '暂时没有内容', desc: '稍后再来看看。', btn: '刷新看看', act: 'retry' };
      return emptyState(map);
    }

    var arr = D.posts[String(ti)] || [];
    var html = arr.map(postCard).join('<div class="gap-24"></div>');
    if (ti === 0 && S.cmPage >= 2) {
      html += '<div class="loadmore loadmore--end">— 没有更多了 —</div>';
    } else if (ti === 0) {
      html += '<div class="loadmore" data-hot data-act="cm-more" id="cmMore">' + ico('chevron-down') + '<span>加载更多</span></div>';
    }
    return html;
  }

  function pageCommunity() {
    var st = S.status;
    var html = '<div class="page" id="cmPage">';
    html += cmNav();
    if (st === 'stale') html += staleBar('离线内容 · 上次更新 8 分钟前');
    html += tabsBlock('cm-tab', D.communityTabs, S.cmTab, 'tabs--flat tabs--sticky');
    // 搜索条 + 话题 chip 不依赖数据，loading 态也要保留（否则首卡上移 160rpx，
    // 骨架出现时会整屏跳一次）
    html += (S.cmTab === 0) ? cmFilters() : '';
    // 不再补 gap：chrome 与列表都保留后，任何额外间距都会让首卡顶边在切态时跳动
    html += '<div id="cmList">' + cmListHTML() + '</div>';
    html += '<div class="page__bottom page__bottom--fab"></div>';
    html += '</div>';
    return html;
  }

  /* ----------------------------------------------------------- 6.4 Messages */
  function msgNav() {
    return '<div class="nav" data-role="nav">'
      + '<h1 class="nav__title">消息</h1>'
      + '<div class="nav__actions">'
        + '<button class="icon-btn" data-hot data-act="msg-notify" aria-label="通知">'
          + ico('bell') + (S.status === 'guest' ? '' : '<span class="badge">5</span>')
        + '</button>'
        + '<button class="icon-btn" data-hot data-act="msg-group" aria-label="发起群聊">' + ico('users') + '</button>'
        + '<button class="icon-btn" data-hot data-act="msg-dm" aria-label="发起私信">' + ico('user-plus') + '</button>'
      + '</div></div>';
  }

  function notifyCard() {
    return '<div class="notify-card">' + D.notifyCells.map(function (c) {
      return '<button class="notify-cell" data-hot data-act="msg-cell" data-arg="' + c.key + '"'
        + ' aria-label="' + c.label + '通知' + (c.unread ? '，有未读' : '') + '">'
        + '<span class="ibox ibox--72">' + ico(c.icon)
          + (c.unread ? '<i class="dot" style="top:calc(-2 * var(--rpx));right:calc(-2 * var(--rpx))"></i>' : '')
        + '</span>'
        + '<span class="notify-cell__label">' + c.label + '</span>'
        + '</button>';
    }).join('') + '</div>';
  }

  function convRow(c) {
    var unread = c.unread > 0;
    /* 头像优先级：实拍图 > 客服耳机图标 > 首字占位 */
    var ava = c.ava
      ? '<img class="avatar__img" src="assets/photo/' + c.ava + '" alt="" loading="lazy" onerror="this.remove()">'
      : (c.cs ? ico('headphones') : esc(c.letter));
    return '<div class="conv" data-conv-id="' + c.id + '">'
      + '<button class="conv__del" data-act="conv-del" data-id="' + c.id + '" aria-label="删除会话">删除</button>'
      + '<div class="conv__slider' + (unread ? ' is-unread' : '') + '" data-swipe data-act="conv-open" data-id="' + c.id + '"'
        + ' tabindex="0" role="button" aria-label="' + esc(c.name) + (unread ? '，' + c.unread + ' 条未读' : '') + '">'
        + '<span class="avatar avatar--88">' + ava + '</span>'
        + '<div class="conv__body">'
          + '<span class="conv__name">' + esc(c.name) + '</span>'
          + '<span class="conv__preview">' + esc(c.preview) + '</span>'
        + '</div>'
        + '<div class="conv__side">'
          + '<span class="conv__time">' + esc(c.time) + '</span>'
          + '<span class="conv__pill' + (unread ? '' : ' is-out') + '">' + (unread ? c.unread : '') + '</span>'
        + '</div>'
        + (unread ? '<span class="sr-only">未读</span>' : '')
      + '</div>'
      + '</div>';
  }

  function convListHTML() {
    var html = '';
    S.convs.forEach(function (c, i) {
      html += convRow(c);
      if (i < S.convs.length - 1) html += '<div class="hr hr--inset"></div>';
    });
    return html;
  }

  function pageMessages() {
    var st = S.status;
    var html = '<div class="page" id="msgPage">';
    html += msgNav();

    if (st === 'stale') html += staleBar('离线消息 · 3 分钟前');
    html += '<div class="gap-24"></div>';

    if (st === 'guest') {
      html += '<div class="card card--shadow" style="padding:calc(48 * var(--rpx)) calc(32 * var(--rpx))">'
        + '<h2 style="font-size:calc(28 * var(--rpx));font-weight:600;text-align:center">登录后查看消息</h2>'
        + '<p style="font-size:calc(26 * var(--rpx));color:var(--color-text-label);text-align:center;margin-top:calc(12 * var(--rpx));line-height:1.5">'
        + '登录后可以看到私信、互动与订单通知。</p>'
        + '<div style="display:flex;justify-content:center;margin-top:calc(32 * var(--rpx))">'
        + '<button class="pbtn pbtn--primary" data-hot data-act="guest-login">去登录</button>'
        + '</div></div>';
      html += '<div class="foot foot--bottom"><span class="foot__brand">MOYUYO ATELIER</span>'
        + '<span class="foot__line">与爱宠共度的每一天</span></div>';
      html += '<div class="page__bottom"></div></div>';
      return html;
    }

    if (st === 'loading') {
      html += '<div class="notify-card">' + new Array(4).fill(0).map(function () {
        return '<div class="notify-cell"><div class="sk sk-circle" style="width:calc(72 * var(--rpx));height:calc(72 * var(--rpx))"></div>'
          + '<div class="sk" style="width:calc(48 * var(--rpx));height:calc(20 * var(--rpx))"></div></div>';
      }).join('') + '</div>';
      html += '<div class="gap-32"></div>';
      // 区块头不依赖数据，loading 也要在（否则首卡上移约 30px，骨架出现时整屏跳一次）
      html += '<div class="sect-head">'
        + '<h2 class="sect-head__title">私信</h2>'
        + '<button class="tbtn" data-hot data-act="msg-dm">发起私信' + ico('chevron-right') + '</button>'
        + '</div>';
      html += '<div class="card card--list card--shadow">' + skeletonRow() + skeletonRow() + skeletonRow() + '</div>';
      html += '<div class="page__bottom"></div></div>';
      return html;
    }

    html += notifyCard();
    html += '<div class="gap-32"></div>';

    var ut = unreadTotal();
    html += '<div class="sect-head">'
      + '<h2 class="sect-head__title">私信'
        + '<span class="badge badge--inline" data-role="dmBadge"' + (ut ? '' : ' style="display:none"') + '>' + ut + '</span>'
      + '</h2>'
      + '<button class="tbtn" data-hot data-act="msg-dm">发起私信' + ico('chevron-right') + '</button>'
      + '</div>';

    if (st === 'error') {
      html += '<div class="card card--list card--shadow">' + emptyState({
        icon: 'cloud', title: '加载失败，请重试',
        desc: '通知卡仍然可用，仅会话列表没读到。', btn: '重试', act: 'retry', primary: true
      }) + '</div>';
      html += '<div class="page__bottom"></div></div>';
      return html;
    }

    if (st === 'empty') {
      html += '<div class="card card--list card--shadow">' + emptyState({
        icon: 'message-square', title: '还没有私信，找感兴趣的人聊聊吧',
        desc: '关注喜欢的用户后，就可以直接发起私信。', btn: '发起私信', act: 'msg-dm'
      }) + '</div>';
      html += '<div class="page__bottom"></div></div>';
      return html;
    }

    html += '<div class="card card--list card--shadow" id="convList">' + convListHTML() + '</div>';
    if (S.convPage >= 2) {
      html += '<div class="loadmore loadmore--end">— 没有更多了 —</div>';
    } else {
      html += '<div class="loadmore" data-hot data-act="conv-more" id="convMore">' + ico('chevron-down') + '<span>加载更多</span></div>';
    }
    html += '<div class="page__bottom"></div>';
    html += '</div>';
    return html;
  }

  /* --------------------------------------------------------------- 6.5 User */
  function userHeader() {
    var u = D.user;
    if (S.status === 'guest') {
      return '<div class="uhead" data-hot data-act="us-profile" tabindex="0" role="button" aria-label="登录或注册">'
        + '<div class="uhead__ava">' + ico('user') + '</div>'
        + '<div class="uhead__main">'
          + '<span class="uhead__name">登录 / 注册</span>'
          + '<span class="uhead__mail">登录后同步你的宠物档案与订单</span>'
        + '</div>'
        + '<button class="pbtn pbtn--primary pbtn--s hit88" data-hot data-act="guest-login">去登录</button>'
        + '</div>';
    }
    return '<div class="uhead" data-hot data-act="us-profile" tabindex="0" role="button" aria-label="个人资料">'
      + '<div class="uhead__ava">' + esc(u.name.charAt(0)) + '</div>'
      + '<div class="uhead__main">'
        + '<span class="uhead__name">' + esc(u.name) + '</span>'
        + '<span class="uhead__mail">' + esc(u.email) + '</span>'
        + '<span class="uhead__level">' + ico('award') + esc(u.level) + '</span>'
      + '</div>'
      + '<span class="uhead__chev">' + ico('chevron-right') + '</span>'
      + '</div>';
  }

  function memberCard() {
    var u = D.user;
    var dash = S.status === 'error' || S.status === 'loading';
    return '<div class="member" data-hot data-act="us-member" tabindex="0" role="button"'
      + ' aria-label="会员中心，当前 ' + u.points + ' 积分">'
      + '<span class="luc luc-trophy member__mark"></span>'
      + '<div>'
        + '<div class="member__kicker">ATELIER 会员</div>'
        + '<div class="member__score">'
          + '<span class="member__num num">' + (dash ? '—' : esc(u.points)) + '</span>'
          + '<span class="member__unit">积分</span>'
        + '</div>'
      + '</div>'
      + '<div class="member__hint">'
        + '<span class="grow">' + (dash ? '—' : esc(u.pointsNext)) + '</span>'
        + ico('chevron-right')
      + '</div>'
      + '</div>';
  }

  function walletCard() {
    var err = S.status === 'error' || S.status === 'loading';
    return '<div class="stat4">' + D.user.wallet.map(function (w) {
      return '<button class="stat" data-hot data-act="us-wallet" data-arg="' + w.key + '" aria-label="' + w.label + '">'
        + '<span class="ibox ibox--56' + (w.key === 'collect' ? ' ibox--gold' : '') + '">' + ico(w.icon) + '</span>'
        + '<span class="stat__num num" data-role="wnum-' + w.key + '">' + (err ? '—' : esc(w.value)) + '</span>'
        + '<span class="stat__label">' + w.label + '</span>'
        + '</button>';
    }).join('') + '</div>';
  }

  function socialCard() {
    var dash = S.status === 'error' || S.status === 'loading';
    return '<div class="social">' + D.user.social.map(function (s) {
      return '<button class="social__cell" data-hot data-act="us-social" data-arg="' + s.key + '" aria-label="' + s.label + ' ' + s.value + '">'
        + '<span class="social__num num">' + (dash ? '—' : esc(s.value)) + '</span>'
        + '<span class="social__label">' + s.label + '</span>'
        + '</button>';
    }).join('') + '</div>';
  }

  function orderCard() {
    return '<div class="orders">'
      + '<div class="orders__head">'
        + '<h2 class="orders__title">我的订单</h2>'
        + '<button class="tbtn" data-hot data-act="us-order-all">全部' + ico('chevron-right') + '</button>'
      + '</div>'
      + '<div class="orders__grid">' + D.user.orders.map(function (o) {
        return '<button class="order-cell" data-hot data-act="us-order" data-arg="' + o.key + '"'
          + ' aria-label="' + o.label + '订单' + (o.badge ? '，' + o.badge + ' 个' : '') + '">'
          + '<span class="order-cell__ico">' + ico(o.icon)
            + (o.badge ? '<span class="order-cell__badge">' + o.badge + '</span>' : '')
          + '</span>'
          + '<span class="order-cell__label">' + o.label + '</span>'
          + '</button>';
      }).join('') + '</div>'
      + '</div>';
  }

  function fnList() {
    return '<div class="list list--plain" role="list">' + D.user.functions.map(function (f) {
      return '<button class="lrow lrow--inset" role="listitem" data-hot data-act="us-fn" data-arg="' + f.key + '"'
        + ' aria-label="' + f.label + '">'
        + '<span class="lrow__ico">' + ico(f.icon) + '</span>'
        + '<span class="lrow__text">' + f.label + '</span>'
        + '<span class="lrow__chev">' + ico('chevron-right') + '</span>'
        + '</button>';
    }).join('') + '</div>';
  }

  function pageUser() {
    var st = S.status;
    var guest = st === 'guest';
    var html = '<div class="page" id="userPage">';
    html += userHeader();

    if (st === 'stale') html += staleBar('数据可能不是最新 · 12 分钟前');
    else if (st === 'error') html += staleBar('数据加载失败 · 点此重试');

    html += '<div class="gap-24"></div>';

    if (guest) {
      html += '<div class="card card--shadow" style="padding:calc(40 * var(--rpx)) calc(32 * var(--rpx))">'
        + '<p style="font-size:calc(26 * var(--rpx));color:var(--color-text-label);text-align:center;line-height:1.6">'
        + '登录后可查看积分、优惠券与订单状态。</p>'
        + '<div style="display:flex;justify-content:center;margin-top:calc(24 * var(--rpx))">'
        + '<button class="pbtn pbtn--primary" data-hot data-act="guest-login">立即登录</button>'
        + '</div></div>';
      html += '<div class="gap-24"></div>';
      html += fnList();
      html += '<div class="gap-48"></div>';
      html += '<div class="foot"><span class="foot__brand">MOYUYO ATELIER</span>'
        + '<span class="foot__line">与爱宠共度的每一天</span></div>';
      html += '<div class="page__bottom"></div></div>';
      return html;
    }

    if (st === 'loading') {
      /* loading 只作用于数值位（— + tabular-nums），会员卡/钱包/社交/订单/11 项功能/页脚
         全部常驻，保证切态几何不变（04 走查 P1-7） */
      html += memberCard();
      html += '<div class="gap-24"></div>';
      html += walletCard();
      html += '<div class="gap-24"></div>';
      html += socialCard();
      html += '<div class="gap-24"></div>';
      html += orderCard();
      html += '<div class="gap-24"></div>';
      html += fnList();
      html += '<div class="gap-48"></div>';
      html += '<div class="foot"><span class="foot__brand">MOYUYO ATELIER</span>'
        + '<span class="foot__line">与爱宠共度的每一天</span></div>';
      html += '<div class="page__bottom"></div></div>';
      return html;
    }

    html += memberCard();
    html += '<div class="gap-24"></div>';
    html += walletCard();
    html += '<div class="gap-24"></div>';
    html += socialCard();
    html += '<div class="gap-24"></div>';
    html += orderCard();
    html += '<div class="gap-24"></div>';
    html += fnList();
    html += '<div class="gap-48"></div>';
    html += '<div class="foot">'
      + '<span class="foot__brand">MOYUYO ATELIER</span>'
      + '<span class="foot__line">与爱宠共度的每一天</span>'
      + '</div>';
    html += '<div class="page__bottom"></div>';
    html += '</div>';
    return html;
  }

  /* ==========================================================================
     §7 浮层
     ========================================================================== */
  function layer() { return $('#layers'); }

  var toastTimer = null;
  function toast(msg) {
    var host = layer();
    var old = $('.toast', host);
    if (old) old.parentNode.removeChild(old);
    var el = document.createElement('div');
    el.className = 'toast';
    el.setAttribute('role', 'status');
    el.textContent = msg;
    host.appendChild(el);
    nextFrame(function () { el.classList.add('is-in'); });
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      el.classList.remove('is-in');
      setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 220);
    }, 1200);
  }

  var sheetEl = null;
  function closeSheet() {
    if (!sheetEl) return;
    var node = sheetEl;
    sheetEl = null;
    $('.mask', node).classList.remove('is-in');
    $('.sheet', node).classList.remove('is-in');
    setTimeout(function () { if (node.parentNode) node.parentNode.removeChild(node); }, 300);
  }

  function openSheet(title, items) {
    closeSheet();
    var wrap = document.createElement('div');
    wrap.innerHTML = '<div class="mask"></div>'
      + '<div class="sheet" role="dialog" aria-modal="true" aria-label="' + esc(title) + '">'
      + '<div class="sheet__title">' + esc(title) + '</div>'
      + items.map(function (it) {
          return '<button class="sheet__item' + (it.danger ? ' sheet__item--danger' : '') + '"'
            + ' data-act="sheet-item" data-arg="' + it.act + '">'
            + (it.icon ? ico(it.icon) : '') + '<span>' + esc(it.label) + '</span></button>';
        }).join('')
      + '<div class="sheet__sep"></div>'
      + '<button class="sheet__cancel" data-act="sheet-close">取消</button>'
      + '</div>';
    layer().appendChild(wrap);
    sheetEl = wrap;
    nextFrame(function () {
      $('.mask', wrap).classList.add('is-in');
      $('.sheet', wrap).classList.add('is-in');
    });
  }

  function openDressup() {
    closeSheet();
    var wrap = document.createElement('div');
    wrap.innerHTML = '<div class="mask"></div>'
      + '<div class="sheet" role="dialog" aria-modal="true" aria-label="装扮宠物">'
      + '<div class="sheet__title">装扮 · ' + esc(D.pets[S.petIndex].name) + '</div>'
      + '<div class="dressup">'
      + '<div class="dressup__sect"><div class="dressup__label">形象</div><div class="dressup__row">'
        + '<button class="dressup__opt is-on" data-hot>' + ico('dog') + '日常</button>'
        + '<button class="dressup__opt" data-hot>' + ico('crown') + '礼装</button>'
        + '<button class="dressup__opt" data-hot>' + ico('shirt') + '雨衣</button>'
      + '</div></div>'
      + '<div class="dressup__sect"><div class="dressup__label">背景</div><div class="dressup__row">'
        + '<button class="dressup__opt is-on" data-hot>' + ico('home') + '客厅</button>'
        + '<button class="dressup__opt" data-hot>' + ico('cloud') + '公园</button>'
        + '<button class="dressup__opt" data-hot>' + ico('sun') + '海边</button>'
      + '</div></div>'
      + '<div class="dressup__sect"><div class="dressup__label">大小</div><div class="dressup__row">'
        + '<button class="dressup__opt" data-hot>' + ico('minus') + '小</button>'
        + '<button class="dressup__opt is-on" data-hot>' + ico('minus') + '中</button>'
        + '<button class="dressup__opt" data-hot>' + ico('plus') + '大</button>'
      + '</div></div>'
      + '</div>'
      + '<div class="sheet__sep"></div>'
      + '<button class="sheet__cancel" data-act="sheet-close">关闭</button>'
      + '</div>';
    layer().appendChild(wrap);
    sheetEl = wrap;
    nextFrame(function () {
      $('.mask', wrap).classList.add('is-in');
      $('.sheet', wrap).classList.add('is-in');
    });
  }

  var modalEl = null;
  function closeModal() {
    if (!modalEl) return;
    var node = modalEl;
    modalEl = null;
    $('.mask', node).classList.remove('is-in');
    $('.modal', node).classList.remove('is-in');
    setTimeout(function () { if (node.parentNode) node.parentNode.removeChild(node); }, 300);
  }

  function openModal(o) {
    closeModal();
    var wrap = document.createElement('div');
    wrap.innerHTML = '<div class="mask"></div>'
      + '<div class="modal" role="dialog" aria-modal="true" aria-label="' + esc(o.title) + '">'
      + (o.icon ? '<div class="modal__ico">' + ico(o.icon) + '</div>' : '')
      + '<h3 class="modal__title">' + esc(o.title) + '</h3>'
      + (o.desc ? '<p class="modal__desc">' + esc(o.desc) + '</p>' : '')
      + '<div class="modal__acts">'
      + '<button class="pbtn ' + (o.cancelType || 'pbtn--secondary') + '" data-act="modal-cancel">' + esc(o.cancel || '再看看') + '</button>'
      + '<button class="pbtn ' + (o.okType || 'pbtn--primary') + '" data-act="modal-ok" data-arg="' + (o.okAct || '') + '">' + esc(o.ok || '去登录') + '</button>'
      + '</div></div>';
    layer().appendChild(wrap);
    modalEl = wrap;
    nextFrame(function () {
      $('.mask', wrap).classList.add('is-in');
      $('.modal', wrap).classList.add('is-in');
    });
  }

  function promptLogin() {
    openModal({
      icon: 'lock',
      title: '请先登录',
      desc: '登录后即可使用该功能。',
      cancel: '再看看',
      ok: '去登录',
      okAct: 'go-login'
    });
  }

  /* ---------- 占位页（模拟） ---------- */
  function pushSub(title, route, note) {
    var el = document.createElement('div');
    el.className = 'subpage';
    el.innerHTML = '<div class="subpage__nav">'
      + '<button class="subpage__back" data-act="back" aria-label="返回">' + ico('chevron-left') + '</button>'
      + '<h1 class="subpage__title">' + esc(title) + '</h1>'
      + '</div>'
      + '<div class="subpage__body">'
      + '<div class="subpage__ico">' + ico('file-text') + '</div>'
      + '<h2 class="subpage__name">' + esc(title) + '</h2>'
      + '<p class="subpage__note">' + esc(note || ('原型占位，真实产品为「' + title + '」页')) + '</p>'
      + (route ? '<p class="subpage__route">' + esc(route) + '</p>' : '')
      + '<button class="pbtn pbtn--secondary" data-hot data-act="back" style="margin-top:calc(24 * var(--rpx))">返回</button>'
      + '</div>';
    layer().appendChild(el);
    S.stack.push(el);
    nextFrame(function () { el.classList.add('is-in'); });
  }

  function go(destKey, note) {
    var d = D.DEST[destKey];
    if (!d) { toast('敬请期待'); return; }
    pushSub(d[0], d[1], note);
  }

  function popSub() {
    var el = S.stack.pop();
    if (!el) return;
    el.classList.remove('is-in');
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 300);
  }

  /* ==========================================================================
     §8 手势：左滑 / 下拉刷新 / 长按 / Banner 拖动
     ========================================================================== */
  var G = {
    owner: null,          // 'swipe' | 'ptr' | 'banner' | 'none'
    sx: 0, sy: 0, dx: 0, dy: 0,
    convEl: null,
    openEl: null,
    max: 0,
    startOffset: 0,
    lpTimer: null,
    lpEl: null,
    bannerStart: 0,
    bannerDx: 0,
    down: false
  };

  var ptr = { el: null, dy: 0, on: false };

  function screenEl() { return $('#screen'); }

  function closeConv() {
    if (!G.openEl) return;
    var s = $('.conv__slider', G.openEl);
    if (s) { s.classList.remove('is-drag'); s.style.transform = ''; }
    G.openEl = null;
    S.openConv = null;
  }

  function openConvRow(row) {
    if (G.openEl && G.openEl !== row) closeConv();
    var s = $('.conv__slider', row);
    if (!s) return;
    s.style.transform = 'translateX(' + (-px(140)) + 'px)';
    G.openEl = row;
    S.openConv = row.getAttribute('data-conv-id');
  }

  function ensurePtr() {
    var page = $('.page', screenEl());
    if (!page) return null;
    var node = $('.ptr', page);
    if (!node) {
      node = document.createElement('div');
      node.className = 'ptr';
      node.innerHTML = ico('arrow-down') + '<span>下拉刷新</span>';
      page.appendChild(node);
    }
    return node;
  }

  function onDown(e) {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    var t = e.target;
    if (t.closest && t.closest('.sheet, .modal')) return;

    G.down = true;
    G.owner = null;
    G.sx = e.clientX; G.sy = e.clientY;
    G.dx = 0; G.dy = 0;
    // 注意：data-swipe 在 .conv__slider 上，滑动时按 .conv 包装层取子节点
    var sw = (t.closest && t.closest('[data-swipe]')) || null;
    G.convEl = sw ? sw.closest('.conv') : null;
    G.startOffset = G.convEl && G.openEl && G.openEl === G.convEl ? -px(140) : 0;
    G.max = px(140);

    // 长按（600ms）
    clearTimeout(G.lpTimer);
    var lp = t.closest && t.closest('[data-long]');
    G.lpEl = lp || null;
    if (lp) {
      G.lpTimer = setTimeout(function () {
        if (!G.down) return;
        G.owner = 'none';
        var id = lp.getAttribute('data-act');
        if (id === 'goods') {
          openSheet('商品操作', [
            { label: '收藏', icon: 'bookmark', act: 'toast-收藏成功' },
            { label: '不感兴趣', icon: 'thumbs-down', act: 'toast-已减少此类推荐' }
          ]);
        }
      }, 600);
    }

    // Banner 拖动
    var bn = t.closest && t.closest('[data-role="banner"]');
    if (bn) { G.owner = 'banner'; G.bannerStart = e.clientX; G.bannerDx = 0; stopBanner(); }
  }

  function onMove(e) {
    if (!G.down) return;
    var dx = e.clientX - G.sx;
    var dy = e.clientY - G.sy;
    G.dx = dx; G.dy = dy;

    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) clearTimeout(G.lpTimer);

    if (G.owner === 'banner') {
      G.bannerDx = dx;
      var track = $('.banner__track');
      if (track) {
        var base = -S.bannerIndex * 100;
        track.style.transition = 'none';
        track.style.transform = 'translateX(calc(' + base + '% + ' + dx + 'px))';
      }
      return;
    }

    if (G.owner === null) {
      // 判定阈值内先不决定方向，等后续 move 累积位移（否则一次 8px 的微动会废掉整个手势）
      var adx = Math.abs(dx), ady = Math.abs(dy);
      if (adx < 10 && ady < 10) return;
      if (adx > ady) {
        if (G.convEl && adx > ady * 1.5 && dx < 0) {
          G.owner = 'swipe';
          var sl0 = $('.conv__slider', G.convEl);
          if (sl0) sl0.classList.add('is-drag');
        } else {
          G.owner = 'none';
        }
      } else if (dy > 10 && screenEl().scrollTop <= 0 && S.tab !== 'pet') {
        G.owner = 'ptr';
        ptr.on = true;
        ptr.dy = 0;
        ptr.el = ensurePtr();
      } else {
        G.owner = 'none';
      }
    }

    if (G.owner === 'swipe') {
      if (e.cancelable) e.preventDefault();
      var off = G.startOffset + dx;
      if (off > 0) off = 0;
      if (off < -G.max) off = -G.max;
      var s = $('.conv__slider', G.convEl);
      if (s) s.style.transform = 'translateX(' + off + 'px)';
      return;
    }

    if (G.owner === 'ptr') {
      if (e.cancelable) e.preventDefault();
      ptr.dy = Math.min(dy * 0.4, 120);
      var page = $('.page', screenEl());
      if (page) {
        page.style.transition = 'none';
        page.style.transform = 'translateY(' + ptr.dy + 'px)';
      }
      if (ptr.el) {
        ptr.el.classList.add('is-live');
        ptr.el.style.opacity = Math.min(1, ptr.dy / 40);
        var txt = $('span:not(.luc)', ptr.el);
        if (txt) txt.textContent = ptr.dy > 60 ? '松开刷新' : '下拉刷新';
        ptr.el.classList.toggle('is-spin', false);
      }
    }
  }

  function onUp(e) {
    clearTimeout(G.lpTimer);
    if (!G.down) return;
    G.down = false;

    if (G.owner === 'banner') {
      var dx = G.bannerDx;
      var track = $('.banner__track');
      if (track) track.style.transition = '';
      if (Math.abs(dx) > 40) goBanner(S.bannerIndex + (dx < 0 ? 1 : -1));
      else goBanner(S.bannerIndex);
      startBanner();
      G.owner = null;
      return;
    }

    if (G.owner === 'swipe') {
      var off = G.startOffset + G.dx;
      if (off < -G.max) off = -G.max;
      var row = G.convEl;
      var s = row && $('.conv__slider', row);
      if (s) s.classList.remove('is-drag');
      if (Math.abs(off) > G.max / 2) openConvRow(row);
      else {
        if (s) s.style.transform = '';
        if (G.openEl === row) { G.openEl = null; S.openConv = null; }
      }
      S.lastSwipe = Date.now();
      G.owner = null;
      G.convEl = null;
      return;
    }

    if (G.owner === 'ptr') {
      var page = $('.page', screenEl());
      var ind = ptr.el;
      if (ptr.dy > 60) {
        if (ind) {
          ind.classList.add('is-spin');
          var t = $('span:not(.luc)', ind);
          if (t) t.textContent = '刷新中…';
        }
        setTimeout(function () {
          // 600ms 假请求
          if (S.tab === 'home') { S.homePage = 1; }
          if (S.tab === 'community') { S.cmPage = 1; }
          paint({ keepScroll: true });
          var p2 = $('.page', screenEl());
          if (p2) {
            p2.style.transition = 'transform var(--dur-state) var(--ease-emphasized)';
            p2.style.transform = 'translateY(0)';
          }
          if (S.tab === 'user') {
            flashNumbers();
          }
        }, 600);
      } else {
        if (page) {
          page.style.transition = 'transform var(--dur-state) var(--ease-emphasized)';
          page.style.transform = 'translateY(0)';
        }
        setTimeout(function () {
          var p3 = $('.page', screenEl());
          if (p3) { p3.style.transition = ''; p3.style.transform = ''; }
          if (ind && ind.parentNode) ind.parentNode.removeChild(ind);
          ptr.el = null;
        }, 220);
      }
      ptr.on = false;
      G.owner = null;
      return;
    }

    G.owner = null;
  }

  function onCancel() {
    G.down = false;
    clearTimeout(G.lpTimer);
    if (G.owner === 'ptr') {
      var page = $('.page', screenEl());
      if (page) { page.style.transition = 'transform var(--dur-state) var(--ease-emphasized)'; page.style.transform = 'translateY(0)'; }
      setTimeout(function () {
        var p = $('.page', screenEl());
        if (p) { p.style.transition = ''; p.style.transform = ''; }
        if (ptr.el && ptr.el.parentNode) ptr.el.parentNode.removeChild(ptr.el);
        ptr.el = null;
      }, 220);
    }
    G.owner = null;
  }

  /* ---------- User 下拉：数字原地淡出淡入（US-11） ---------- */
  function flashNumbers() {
    var nums = $$('[data-role^="wnum-"]');
    nums.forEach(function (n) {
      n.classList.add('is-fading');
    });
    setTimeout(function () {
      nums.forEach(function (n) { n.classList.remove('is-fading'); });
    }, 200);
  }

  /* ---------- Banner 自动播放 ---------- */
  var bannerTimer = null;
  function startBanner() {
    stopBanner();
    if (reducedMotion()) return;
    bannerTimer = setInterval(function () { goBanner(S.bannerIndex + 1); }, 3000);
  }
  function stopBanner() {
    if (bannerTimer) { clearInterval(bannerTimer); bannerTimer = null; }
  }

  function goBanner(i) {
    var n = D.banners.length;
    S.bannerIndex = ((i % n) + n) % n;
    var track = $('.banner__track');
    if (track) track.style.transform = 'translateX(' + (-S.bannerIndex * 100) + '%)';
    $$('.banner__dot').forEach(function (d, k) {
      d.classList.toggle('is-on', k === S.bannerIndex);
    });
  }

  /* ---------- 滚动：吸顶 + 触底加载 ---------- */
  function onScroll() {
    var sc = screenEl();
    var nav = $('[data-role="nav"]', sc);
    if (nav) nav.classList.toggle('is-scrolled', sc.scrollTop > 8);

    var page = $('.page', sc);
    if (!page) return;
    var nearBottom = sc.scrollTop + sc.clientHeight > sc.scrollHeight - 200;
    if (!nearBottom) return;

    if (S.tab === 'home' && S.homePage < 2 && S.status === 'normal') {
      S.homePage = 2;
      var hl = $('#homeList');
      if (hl) hl.innerHTML = homeListHTML();
    }
    if (S.tab === 'community' && S.cmPage < 2 && S.status === 'normal' && S.cmTab === 0) {
      S.cmPage = 2;
      var cl = $('#cmList');
      if (cl) cl.innerHTML = cmListHTML();
    }
  }

  /* ==========================================================================
     §9 事件分发
     ========================================================================== */
  function syncUnreadUI() {
    var ut = unreadTotal();
    var badge = $('[data-role="dmBadge"]');
    if (badge) {
      badge.textContent = ut;
      badge.style.display = ut ? '' : 'none';
    }
    renderTabbar();
  }

  function doAct(act, el) {
    var arg = el.getAttribute('data-arg');
    var id = el.getAttribute('data-id');

    switch (act) {
      /* ---------- 外壳 ---------- */
      case 'panel-fold':
        $('#panel').classList.toggle('is-open');
        return;

      case 'theme':
        S.theme = arg; applyTheme(); syncPanel();
        return;

      case 'size':
        S.size = arg; applySize(); paint({ keepScroll: true }); syncPanel();
        return;

      case 'status':
        S.status = arg;
        S.cmPage = 1; S.homePage = 1;
        paint();
        return;

      case 'hotspot':
        S.hotspots = !S.hotspots;
        document.body.classList.toggle('show-hot', S.hotspots);
        syncPanel();
        return;

      case 'reset':
        closeSheet(); closeModal();
        while (S.stack.length) popSub();
        closeConv();
        S = defaults();
        document.body.classList.remove('show-hot');
        applyTheme(); applySize(); paint(); syncPanel();
        toast('已重置到初始状态');
        return;

      /* ---------- Tab ---------- */
      case 'tab':
        if (S.tab === arg) return;
        closeConv();
        S.tab = arg;
        paint();
        return;

      /* ---------- Pet ---------- */
      case 'pet-ava':
        var i = parseInt(el.getAttribute('data-i'), 10);
        if (i === S.petIndex) return;
        S.petIndex = i;
        petSwitchPaint();
        return;

      case 'pet-quick':
        if (arg === 'share') {
          if (S.status === 'empty' || S.status === 'guest') { S.tab = 'community'; paint(); return; }
          go('pet-share', '真实产品为带 petId 的发布页（switchTab 后由 tabIntent 消费）');
          return;
        }
        if (arg === 'mall') { go('pet-mall'); return; }
        go('pet-' + arg);
        return;

      case 'care-col':
      case 'care-row':
        go(arg);
        return;

      case 'pet-dressup':
        openDressup();
        return;

      case 'pet-3d':
        go('pet-3d');
        return;

      case 'pet-profile':
        go('pet-profile');
        return;

      /* ---------- Home ---------- */
      case 'home-search': go('home-search'); return;
      case 'home-notify': go('home-notify'); return;

      case 'kk':
        var kkMap = { category: 'home-category', member: 'home-member', coupon: 'home-coupon', flash: 'home-flash' };
        go(kkMap[arg]);
        return;

      case 'banner-tap':
        var bi = parseInt(el.getAttribute('data-i'), 10);
        var b = D.banners[bi];
        if (b && b.link) pushSub('活动详情', '/pages/marketing/campaign?id=' + (bi + 1), '原型占位，真实产品为「活动落地页」· ' + b.title);
        else toast('该活动页面暂不可用');
        return;

      case 'home-tab':
        switchHomeTab(parseInt(el.getAttribute('data-i'), 10));
        return;

      case 'home-more':
        S.homePage = 2;
        var hl = $('#homeList');
        if (hl) hl.innerHTML = homeListHTML();
        return;

      case 'goods':
        var gi = parseInt(el.getAttribute('data-i'), 10);
        var p = D.products[S.homeTab][gi];
        pushSub('商品详情', '/pages/goods/detail?id=100' + (gi + 1),
          '原型占位，真实产品为「商品详情」页 · ' + (p ? p.name : ''));
        return;

      /* ---------- Community ---------- */
      case 'cm-search': go('cm-search'); return;
      case 'cm-notify': go('cm-notify'); return;
      case 'cm-topic': go('cm-topic', '真实产品为「' + arg + '」话题详情页'); return;
      case 'cm-author': go('cm-author', '真实产品为「' + arg + '」的个人主页'); return;
      case 'cm-publish':
        if (S.status === 'guest') { promptLogin(); return; }
        go('cm-publish');
        return;

      case 'cm-tab':
        switchCmTab(parseInt(el.getAttribute('data-i'), 10));
        return;

      case 'cm-tab-rec':
        switchCmTab(0);
        return;

      case 'cm-more':
        S.cmPage = 2;
        var cl = $('#cmList');
        if (cl) cl.innerHTML = cmListHTML();
        return;

      case 'post-open':
        go('cm-post');
        return;

      case 'comment':
        go('cm-post', '真实产品为帖子详情并自动聚焦评论输入框');
        return;

      case 'share':
        toast('链接已复制');
        return;

      case 'post-more':
        openPostMore(id);
        return;

      case 'like':
        toggleLike(el);
        return;

      /* ---------- Messages ---------- */
      case 'msg-notify': go('msg-notify'); return;
      case 'msg-group': go('msg-group'); return;
      case 'msg-dm': go('msg-dm'); return;

      case 'msg-cell':
        go('msg-notify-' + arg, '真实产品为通知中心「' + el.textContent.trim() + '」分组');
        return;

      case 'conv-open':
        // 先判「刚滑动过」：左滑松手会合成一次 click，不能让它把刚展开的行又收起来
        if (Date.now() - S.lastSwipe < 300) return;
        if (G.openEl) { closeConv(); return; }
        readConv(id);
        go('msg-conv', '真实产品为「' + (findConv(id) || {}).name + '」的私信会话');
        return;

      case 'conv-del':
        confirmDeleteConv(id);
        return;

      case 'conv-more':
        S.convPage = 2;
        S.convs = S.convs.concat(D.conversationsPage2.map(function (c) {
          return { id: c.id, name: c.name, letter: c.letter, preview: c.preview, time: c.time, unread: c.unread, cs: !!c.cs, ava: c.ava || '' };
        }));
        paint({ keepScroll: true });
        return;

      /* ---------- User ---------- */
      case 'us-profile': go('us-profile'); return;
      case 'us-member': go('us-member'); return;
      case 'us-wallet': go('us-wallet-' + arg); return;
      case 'us-order-all': go('us-order-all'); return;
      case 'us-order': go('us-order-' + arg); return;

      case 'us-social':
        if ((arg === 'follow' || arg === 'fans') && S.status === 'guest') { promptLogin(); return; }
        go('us-social-' + arg);
        return;

      case 'us-fn':
        var f = D.user.functions.filter(function (x) { return x.key === arg; })[0];
        if (f && f.auth && S.status === 'guest') { promptLogin(); return; }
        go('us-fn-' + arg);
        return;

      /* ---------- 通用 ---------- */
      case 'retry':
        S.status = 'normal';
        S.homePage = 1; S.cmPage = 1;
        paint();
        toast('已重新加载');
        return;

      case 'guest-login':
        go('go-login');
        return;

      case 'toast':
        toast(arg || '敬请期待');
        return;

      case 'back':
        popSub();
        return;

      case 'sheet-close':
        closeSheet();
        return;

      case 'sheet-item':
        closeSheet();
        if (arg.indexOf('toast-') === 0) { toast(arg.slice(6)); return; }
        if (arg.indexOf('del:') === 0) { doDeleteConv(arg.slice(4)); return; }
        if (arg.indexOf('top:') === 0) { toast('已置顶会话'); return; }
        if (arg.indexOf('report:') === 0) { openReport(arg.slice(7)); return; }
        if (arg.indexOf('remove:') === 0) { removePost(arg.slice(7)); return; }
        if (arg === 'ask-delete') { confirmPostDelete(el.getAttribute('data-pid')); }
        return;

      case 'modal-cancel':
        closeModal();
        return;

      case 'modal-ok':
        closeModal();
        if (arg === 'go-login') go('go-login');
        return;
    }
  }

  /* ---------- Pet 切换联动（T7：数据一致性） ---------- */
  var petSwitchToken = 0;
  function petSwitchPaint() {
    var token = ++petSwitchToken;
    var pet = D.pets[S.petIndex];
    // 乐观：先切换头像选中环（同步反馈）
    $$('.pet-ava').forEach(function (a, i) {
      a.classList.toggle('is-on', i === S.petIndex);
      a.setAttribute('aria-pressed', String(i === S.petIndex));
    });
    // 200ms 淡出 → 重绘 → 淡入；用 token 丢弃过期响应
    setTimeout(function () {
      if (token !== petSwitchToken) return; // 快速连点时丢弃过期结果
      var info = $('.scene__info');
      if (info) info.style.opacity = '0';
      var fig = $('.scene__figure');
      if (fig) fig.style.opacity = '0';
      $$('.care__col').forEach(function (c) { c.style.opacity = '0'; });
      setTimeout(function () {
        if (token !== petSwitchToken) return;
        paint({ keepScroll: true });
      }, 200);
    }, 0);
  }

  /* ---------- 页内 Tab ---------- */
  function switchHomeTab(i) {
    if (i === S.homeTab) return;
    var list = $('#homeList');
    var tabs = $('[data-role="home-tab"]');
    S.homeTab = i;
    if (tabs) {
      $$('.tabs__item', tabs).forEach(function (b, k) {
        b.classList.toggle('is-on', k === i);
        b.setAttribute('aria-selected', String(k === i));
      });
      tabs.setAttribute('data-active', i);
      placeUnderline(tabs, i, true);
    }
    if (S.loadedHome[i]) {
      if (list) list.innerHTML = homeListHTML();
      return;
    }
    // 首进：骨架 → 内容（列表区单独替换，不整页空）
    if (list) {
      list.style.transition = 'opacity var(--dur-state) var(--ease-standard)';
      list.style.opacity = '.5';
      list.innerHTML = '<div class="grid2">' + new Array(4).fill(0).map(skeletonProduct).join('') + '</div>';
    }
    setTimeout(function () {
      if (S.homeTab !== i) return; // 已切走，丢弃
      S.loadedHome[i] = true;
      var l2 = $('#homeList');
      if (l2) {
        l2.innerHTML = homeListHTML();
        l2.style.opacity = '1';
      }
    }, 700);
  }

  function switchCmTab(i) {
    if (i === S.cmTab) return;
    S.cmTab = i;
    var tabs = $('[data-role="cm-tab"]');
    if (tabs) {
      $$('.tabs__item', tabs).forEach(function (b, k) {
        b.classList.toggle('is-on', k === i);
        b.setAttribute('aria-selected', String(k === i));
      });
      tabs.setAttribute('data-active', i);
      placeUnderline(tabs, i, true);
    }
    // 搜索条 + 话题 chip 整条隐藏（display 切换，不做高度动画）
    var filters = $('#cmFilters');
    if (filters && i !== 0) {
      filters.parentNode.removeChild(filters);
    } else if (!filters && i === 0) {
      var page = $('#cmPage');
      var list = $('#cmList');
      if (page && list) {
        var tmp = document.createElement('div');
        tmp.innerHTML = cmFilters();
        page.insertBefore(tmp.firstChild, list);
      }
    }

    var cmList = $('#cmList');
    if (S.loadedCm[i]) {
      if (cmList) cmList.innerHTML = cmListHTML();
      return;
    }
    S.cmLoading = true;
    if (cmList) cmList.innerHTML = cmListHTML();
    setTimeout(function () {
      if (S.cmTab !== i) return;
      S.cmLoading = false;
      S.loadedCm[i] = true;
      var l2 = $('#cmList');
      if (l2) l2.innerHTML = cmListHTML();
    }, 700);
  }

  /* ---------- 点赞（T3） ---------- */
  var likeLock = {};
  function toggleLike(el) {
    if (S.status === 'guest') { promptLogin(); return; }
    var id = el.getAttribute('data-id');
    var p = findPost(id);
    if (!p) return;
    if (likeLock[id]) return;           // 600ms 防抖：忽略重复点击，不排队
    likeLock[id] = true;
    setTimeout(function () { likeLock[id] = false; }, 600);

    var liked = !S.liked[id];
    S.liked[id] = liked;
    p.likes += liked ? 1 : -1;

    el.classList.toggle('is-liked', liked);
    el.setAttribute('aria-pressed', String(liked));
    el.setAttribute('aria-label', (liked ? '取消点赞' : '点赞') + '，当前 ' + p.likes + ' 次');
    var c = $('.act__count', el);
    if (c) c.textContent = p.likes;

    if (!reducedMotion()) {
      el.classList.remove('is-bump');
      void el.offsetWidth;
      el.classList.add('is-bump');
      setTimeout(function () { el.classList.remove('is-bump'); }, 240);
    }
  }

  /* ---------- 帖子更多动作表（CM-16/17/18/19/20） ---------- */
  function openPostMore(id) {
    var p = findPost(id);
    if (!p) return;
    var items = [];
    if (p.own) items.push({ label: '删除', icon: 'trash-2', danger: true, act: 'ask-delete' });
    items.push({ label: '不感兴趣', icon: 'thumbs-down', act: 'remove:' + id });
    items.push({ label: '举报', icon: 'flag', act: 'report:' + id });
    openSheet('帖子操作', items);
    sheetEl.setAttribute('data-pid', id);
  }

  function confirmPostDelete(id) {
    openModal({
      icon: 'trash-2',
      title: '确认删除这条帖子？',
      desc: '删除后不可恢复，评论与点赞也会一并移除。',
      cancel: '取消',
      ok: '删除',
      okType: 'pbtn--danger',
      okAct: 'modal-close-only'
    });
    var okBtn = $('[data-act="modal-ok"]', modalEl);
    if (okBtn) {
      okBtn.onclick = function (ev) {
        ev.stopPropagation();
        closeModal();
        removePost(id);
        toast('已删除');
      };
    }
  }

  function removePost(id) {
    var el = document.querySelector('.post[data-id="' + id + '"]');
    if (!el) return;
    el.style.height = el.offsetHeight + 'px';
    void el.offsetHeight;
    el.classList.add('is-gone');
    setTimeout(function () {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, 220);
  }

  function openReport(id) {
    openSheet('举报原因', [
      { label: '骚扰', icon: 'flag', act: 'toast-举报已提交' },
      { label: '垃圾广告', icon: 'flag', act: 'toast-举报已提交' },
      { label: '色情低俗', icon: 'flag', act: 'toast-举报已提交' },
      { label: '诈骗', icon: 'flag', act: 'toast-举报已提交' },
      { label: '其他', icon: 'flag', act: 'toast-举报已提交' }
    ]);
  }

  /* ---------- 消息未读链路（T8） ---------- */
  function findConv(id) {
    return S.convs.filter(function (c) { return c.id === id; })[0];
  }

  function readConv(id) {
    var c = findConv(id);
    if (!c || !c.unread) return;
    c.unread = 0;
    var row = document.querySelector('.conv[data-conv-id="' + id + '"]');
    if (row) {
      var sl = $('.conv__slider', row);
      if (sl) {
        sl.classList.remove('is-unread');
        sl.setAttribute('aria-label', c.name);
      }
      var pill = $('.conv__pill', row);
      if (pill) {
        pill.classList.add('is-out');
        pill.textContent = '';
      }
      var sr = $('.sr-only', row);
      if (sr) sr.parentNode.removeChild(sr);
    }
    syncUnreadUI();
  }

  function confirmDeleteConv(id) {
    var c = findConv(id);
    if (!c) return;
    var isCs = c.cs;
    openModal({
      icon: 'trash-2',
      title: '删除会话',
      desc: isCs ? '将删除与「' + c.name + '」的客服会话记录。' : '将删除与「' + c.name + '」的聊天记录，对方不会收到通知。',
      cancel: '取消',
      ok: '删除',
      okType: 'pbtn--danger',
      okAct: 'modal-close-only'
    });
    var okBtn = $('[data-act="modal-ok"]', modalEl);
    if (okBtn) {
      okBtn.onclick = function (ev) {
        ev.stopPropagation();
        closeModal();
        doDeleteConv(id);
      };
    }
  }

  function doDeleteConv(id) {
    var c = findConv(id);
    var row = document.querySelector('.conv[data-conv-id="' + id + '"]');
    var hadUnread = c && c.unread;
    S.convs = S.convs.filter(function (x) { return x.id !== id; });
    if (G.openEl === row) { G.openEl = null; S.openConv = null; }
    if (row) {
      row.style.height = row.offsetHeight + 'px';
      void row.offsetHeight;
      row.classList.add('is-removing');
      setTimeout(function () {
        if (row.parentNode) row.parentNode.removeChild(row);
        // 重画分隔线
        var listEl = $('#convList');
        if (listEl) {
          if (!S.convs.length) {
            listEl.innerHTML = emptyState({
              icon: 'message-square', title: '还没有私信，找感兴趣的人聊聊吧',
              desc: '关注喜欢的用户后，就可以直接发起私信。', btn: '发起私信', act: 'msg-dm'
            });
          } else {
            listEl.innerHTML = convListHTML();
          }
        }
      }, 220);
    }
    if (hadUnread) syncUnreadUI();
    toast('已删除');
  }

  /* ==========================================================================
     §10 启动
     ========================================================================== */
  document.addEventListener('click', function (e) {
    var el = e.target.closest ? e.target.closest('[data-act]') : null;
    if (!el) {
      // 点遮罩关闭
      if (e.target.classList && e.target.classList.contains('mask')) {
        closeSheet(); closeModal();
      }
      return;
    }
    // 弹层内的 data-act 交给统一分发；modal-ok 的重写由 onclick 处理
    doAct(el.getAttribute('data-act'), el);
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      if (modalEl) { closeModal(); return; }
      if (sheetEl) { closeSheet(); return; }
      if (S.stack.length) { popSub(); return; }
    }
    // 让 role=button 的元素可键盘激活
    if ((e.key === 'Enter' || e.key === ' ') && e.target.getAttribute) {
      var act = e.target.getAttribute('data-act');
      if (act && e.target.getAttribute('role') === 'button') {
        e.preventDefault();
        doAct(act, e.target);
      }
    }
  });

  // 手势
  var sc = $('#screen');
  sc.addEventListener('pointerdown', onDown, { passive: true });
  sc.addEventListener('pointermove', onMove, { passive: false });
  sc.addEventListener('pointerup', onUp, { passive: true });
  sc.addEventListener('pointercancel', onCancel, { passive: true });
  sc.addEventListener('scroll', onScroll, { passive: true });

  applyTheme();
  applySize();
  paint();
  syncPanel();

  // 暴露少量调试钩子（便于验收时手测，不影响运行）
  window.__MOYUYO_PROTO = { state: S, paint: paint, doAct: doAct, toast: toast };
})();
