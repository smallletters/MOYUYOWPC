# MOYUYO · v5 重设计｜逐页实现规格（MIST SLATE 雾银石墨）

> 产出：提示词工程师 / QA 伊万（image-prompt-engineer）｜阶段：Phase 3
> 事实源：`01-product-brief.md`（视觉定调）+ `02-marketing-copy.md`（冻结文案，唯一文案真源）+ `03-image-set-plan.md`（五页布局锁定）+ `index.html`（结构 / 真实数据 / 图标符号，只读核对）+ `assets/photo/`（32 张真实素材，已逐一核存在）
> 任务映射：本任务为 **UI 布局原型**，不是摄影出图。本文件 = 原「提示词包」转译为**给前端执行的实现规格**；配套 `05-qa-checklist.md` = 原「成片质检清单」转译为**成片视觉 QA 清单**。
> 落地方式：单文件 HTML + 无头浏览器逐页截图。**不修改 `index.html`**，不改写任何冻结文案，不臆造商品 / 帖子 / 会话数据。
> 文案/layout 状态：按主理人转达，用户已明确确认 MIST SLATE 风格 + 五页布局、文案逐字冻结。上游文档仍留「草稿待确认」戳记为流程痕迹，本文件按其**已确认**内容执行。

---

## 第一部分 · 全局设计系统（下游直接复制）

### 1.1 `:root` CSS 变量 —— 浅色（默认，五张图主态）

```css
:root{
  /* ── 品牌 / 文字 ───────────────────────────── */
  --slate:        #3A4B5C;   /* 品牌石墨蓝：品牌文字 / 选中 tab / 图标 */
  --slate-deep:   #2B3A4A;   /* 深填充：主按钮 / VIP / FAB（白字 11.62:1） */
  --ink-1:        #1F2D3D;   /* 正文 / 标题（白卡 13.99:1） */
  --ink-2:        #566573;   /* 次要 / 摘要（白卡 5.99:1） */
  --ink-3:        #8A97A5;   /* 三级：仅时间 / 占位（2.98:1，禁承载关键信息） */
  --accent:       #945048;   /* 干玫瑰文字：话题词 / 强调（白卡 6.00:1） */
  --accent-fill:  #9C564E;   /* 干玫瑰实心按钮底（白字 5.45:1） */
  --signal:       #B04A3F;   /* 信号红：未读 / 点赞 / 删除（白字 5.39:1） */
  --on-slate:     #FFFFFF;   /* 深填充 / 照片遮罩上的白字 */

  /* ── 面 / 填充 ─────────────────────────────── */
  --bg-canvas:    #EEF1F3;   /* 雾银白：全局页面底 */
  --bg-surface:   #FFFFFF;   /* 白卡 / 商品卡 / 帖子卡 / 群卡 */
  --fill-2:       #E4E9ED;   /* 次级填充：搜索胶囊 / chip / 图标底 */
  --fill-3:       #D6DEE4;   /* 三级填充：进度轨 / 占位骨块 */
  --hairline:     #D6DEE4;   /* 1px 冷灰发丝线（主分界语言） */
  --tint:         rgba(58,75,92,.10);  /* 选中 chip / 选中图标底 */
  --veil:         rgba(238,241,243,.82); /* nav / tabBar / sticky tab 雾面玻璃 */
  --veil-blur:    saturate(160%) blur(20px);

  /* ── 照片浮层遮罩（照片上白字必加） ────────── */
  --shade-photo:  linear-gradient(to top, rgba(31,45,61,.62), transparent 55%);

  /* ── 极轻冷灰漫反射（最后手段，禁彩色/金色投影） ── */
  --shadow-1:     0 2px 10px rgba(31,45,61,.06);
  --shadow-2:     0 2px 10px rgba(31,45,61,.10);
  --shadow-float: 0 2px 10px rgba(31,45,61,.12);

  /* ── 圆角 ──────────────────────────────────── */
  --r-card: 16px;  --r-vip: 20px; --r-banner: 18px;
  --r-icon: 14px;  --r-pill: 9999px; --r-avatar: 50%;

  /* ── 字体 / 缓动 ───────────────────────────── */
  --font-sans: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "PingFang SC", "Helvetica Neue", sans-serif;
  --ease-ios: cubic-bezier(.2,.9,.25,1);

  /* ── 视口 / safe 区（数字红线） ─────────────── */
  --vw: 393px; --vh: 852px;
  --safe-top: 59px; --safe-bottom: 34px; --tab-h: 83px;
  --gutter: 16px;              /* 左右安全边距 */
  --card-w: 361px;             /* 393 - 16×2 */
  --prod-w: 174.5px;           /* (361 - 12) / 2 */
  --grid-col: 82.25px;         /* (361 - 16×2) / 4 */
  --pad-bottom: 107px;         /* 末卡底部留白，防 tabBar 遮挡（83+24） */
}
```

### 1.2 `.theme-dark` 深色 token（一键切换态；五张成片默认浅态）

> 说明：标「推导」的 token 方案未单列明码，按同色系明度差补齐，**不写入任何商品/文案事实**。

```css
.theme-dark{
  --bg-canvas:  #151C24;   /* 页面底：冷调深石墨蓝，非纯黑 */
  --bg-surface: #202B36;   /* 卡面 */
  --fill-2:     #2B3845;   /* 次级填充 */
  --fill-3:     #374554;   /* 三级填充（推导：同色系提亮一档） */
  --hairline:   #374554;   /* 深态发丝线（推导） */
  --ink-1:      #E8EDF1;   /* 主文字（深底 14.56:1 / 深卡 12.20:1） */
  --ink-2:      #B8C4CF;   /* 次文字（深卡 8.10:1） */
  --ink-3:      #7E8D9C;   /* 三级文字：仅非关键 */
  --slate:      #9FB4C6;   /* 深态品牌雾蓝（深卡 6.72:1） */
  --slate-deep: #2B3845;   /* 深态深填充（推导，与 fill-2 同） */
  --accent:     #C98078;   /* 深态干玫瑰强调文字 */
  --accent-fill:#9C564E;   /* 实心按钮沿用：白字 5.45:1 与主题无关 */
  --signal:     #C05A50;   /* 深态信号 */
  --tint:       rgba(159,180,198,.14); /* 深态选中染色（推导） */
  --veil:       rgba(21,28,36,.82);     /* 深态毛玻璃（推导，沿用浅态 .82 alpha） */
  --shadow-1:   0 2px 10px rgba(0,0,0,.28); /* 深态投影（推导：冷黑漫反射，仍禁彩色） */
  --shadow-2:   0 2px 10px rgba(0,0,0,.34);
  --shadow-float:0 2px 10px rgba(0,0,0,.40);
}
```

### 1.3 基础重置 / 视口 / safe 区

```css
*,*::before,*::after{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html,body{margin:0;padding:0}
body{
  font-family:var(--font-sans);
  color:var(--ink-1);
  background:#C9D0D6;              /* 舞台底色（非页面底，仅截图画布外圈） */
  font-size:16px; line-height:1.35;
  -webkit-font-smoothing:antialiased;
}
.num,strong.num,.price{font-variant-numeric:tabular-nums}

/* 手机画布：393×852 固定，截图时不要圆角外框参与裁切 */
.screen{
  position:relative; width:var(--vw); height:var(--vh);
  overflow:hidden; background:var(--bg-canvas);
  margin:0 auto;
}
.statusbar{position:absolute;top:0;left:0;right:0;height:var(--safe-top);z-index:40;pointer-events:none;color:var(--ink-1)}
.statusbar .sb-time{position:absolute;left:32px;top:15px;font:600 15px/20px var(--font-sans);letter-spacing:.2px;font-variant-numeric:tabular-nums}
.statusbar .sb-right{position:absolute;right:30px;top:17px;display:flex;align-items:center;gap:6px}
.statusbar .sb-right svg{width:18px;height:13px;fill:currentColor}
/* 灵动岛：系统级黑，不重绘（保持画布顶部中段留空即可） */

.app-body{position:absolute;inset:0;overflow:hidden}
.page{position:absolute;inset:0;display:none}
.page.active{display:block}
.scroll{position:absolute;left:0;right:0;top:0;bottom:0;overflow-y:auto;overflow-x:hidden;-webkit-overflow-scrolling:touch;scrollbar-width:none}
.scroll::-webkit-scrollbar{display:none}
.scroll.home,.scroll.community,.scroll.messages{padding-top:var(--safe-top)}
.scroll.user{padding-top:0}          /* user 透明 nav 覆盖 header，内容从 0 起 */
```

> 底部滚动容器内容需留 `--pad-bottom:107px`，确保末行不被 tabBar（83）+ Home Indicator（34 区）遮挡。

### 1.4 可复用组件 CSS（直接复制）

```css
/* ── 卡片 / 分组 ─────────────────────────────── */
.group-card{
  margin:0 var(--gutter) 16px;
  background:var(--bg-surface);
  border:1px solid var(--hairline);        /* 1px 冷灰发丝，替代旧 0.5px 暖线 */
  border-radius:var(--r-card);
  box-shadow:var(--shadow-1);
  overflow:hidden;
}
.section-head{padding:20px var(--gutter) 8px;font-size:13px;color:var(--ink-2);font-weight:400}

/* ── 行（功能列表 / 通用） ───────────────────── */
.row{min-height:52px;padding:12px var(--gutter);display:flex;align-items:center;gap:12px;position:relative;border:0;background:transparent;color:var(--ink-1);text-align:left;font:inherit}
.row + .row::before{content:"";position:absolute;left:56px;right:0;top:0;height:1px;background:var(--hairline)}
.row .row-label{font-size:15px;font-weight:400;flex:1}
.row .chevron{width:13px;height:13px;color:var(--ink-3);flex:none}

/* ── 图标底 / 图标 ───────────────────────────── */
.ic{width:24px;height:24px;display:block;flex:none;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.ic-28{width:28px;height:28px} .ic-20{width:20px;height:20px} .ic-18{width:18px;height:18px} .ic-13{width:13px;height:13px} .ic-64{width:64px;height:64px}
.tile{width:52px;height:52px;border-radius:var(--r-icon);background:var(--fill-2);color:var(--slate);display:flex;align-items:center;justify-content:center}
.func-icon{width:28px;height:28px;border-radius:8px;background:var(--fill-2);color:var(--slate);display:flex;align-items:center;justify-content:center;flex:none}
.ring{width:40px;height:40px;border-radius:var(--r-avatar);background:var(--fill-2);color:var(--slate);display:flex;align-items:center;justify-content:center;position:relative}

/* ── 角标（信号红单点编码） ──────────────────── */
.badge-num{min-width:16px;height:16px;padding:0 4px;border-radius:var(--r-pill);background:var(--signal);color:#fff;font:600 11px/16px var(--font-sans);text-align:center;font-variant-numeric:tabular-nums}
.badge-dot{width:8px;height:8px;border-radius:var(--r-avatar);background:var(--signal);box-shadow:0 0 0 1.5px var(--veil)}
.pill{height:18px;min-width:18px;padding:0 5px;border-radius:var(--r-pill);background:var(--signal);color:#fff;font:600 11px/18px var(--font-sans);text-align:center;font-variant-numeric:tabular-nums}
.odot{position:absolute;top:-4px;right:-6px;min-width:16px;height:16px;line-height:16px;padding:0 4px;border-radius:var(--r-pill);background:var(--signal);color:#fff;font:600 11px/16px var(--font-sans);text-align:center}

/* ── 胶囊 / chip ─────────────────────────────── */
.search-pill{height:36px;flex:1;display:flex;align-items:center;gap:6px;padding:0 12px;border:0;border-radius:var(--r-pill);background:var(--fill-2);color:var(--ink-2);font-size:13px}
.search-pill .ic{width:16px;height:16px}
.chip{flex:none;height:32px;padding:0 14px;border-radius:var(--r-pill);background:var(--fill-2);color:var(--ink-2);font-size:13px;border:1px solid transparent;white-space:nowrap}
.chip.on{background:var(--tint);color:var(--slate);border-color:rgba(58,75,92,.18)}
.chip .topic{color:var(--accent)}      /* 话题词干玫瑰点缀 */

/* ── 分段 tab（吸顶） ────────────────────────── */
.seg-tabs{position:sticky;top:var(--safe-top);z-index:15;height:44px;display:flex;align-items:stretch;background:var(--veil);backdrop-filter:var(--veil-blur);-webkit-backdrop-filter:var(--veil-blur);border-bottom:1px solid var(--hairline)}
.seg-tab{flex:1;border:0;background:transparent;color:var(--ink-2);font-size:15px;font-weight:400;cursor:pointer}
.seg-tab.on{color:var(--slate);font-weight:600}
.seg-indicator{position:absolute;bottom:0;left:0;width:20px;height:3px;border-radius:2px;background:var(--slate);transition:transform 220ms var(--ease-ios)}
.seg-indicator.underline{width:24px;height:2px}

/* ── nav ─────────────────────────────────────── */
.nav{position:absolute;left:0;right:0;top:0;z-index:20}
.nav-compact{height:44px;top:var(--safe-top);background:var(--veil);backdrop-filter:var(--veil-blur);-webkit-backdrop-filter:var(--veil-blur);border-bottom:1px solid var(--hairline)}
.nav-large{height:96px;top:var(--safe-top);background:var(--bg-canvas)}  /* Large Title 区，透明底 */
.nav-large .large-title{position:absolute;left:var(--gutter);bottom:12px;font-size:34px;font-weight:700;letter-spacing:-.5px;color:var(--ink-1)}
.nav-user{height:44px;top:var(--safe-top);background:transparent;border-bottom:0}
.nav-user.scrolled{background:var(--veil);backdrop-filter:var(--veil-blur);-webkit-backdrop-filter:var(--veil-blur);border-bottom:1px solid var(--hairline)}
.nav-user .user-title{position:absolute;left:0;right:0;text-align:center;line-height:44px;font-size:17px;font-weight:600;color:var(--ink-1);opacity:0}
.nav-user.scrolled .user-title{opacity:1}
.toolbar{height:44px;display:flex;align-items:center;gap:8px;padding:0 var(--gutter)}
.nav-btn{position:relative;width:44px;height:44px;flex:none;display:flex;align-items:center;justify-content:center;border:0;background:transparent;color:var(--slate)}
.nav-right{justify-content:flex-end}

/* ── tabBar（五页统一，选中态） ───────────────── */
.tabbar{position:absolute;left:0;right:0;bottom:0;height:var(--tab-h);z-index:25;display:flex;align-items:flex-start;background:var(--veil);backdrop-filter:var(--veil-blur);-webkit-backdrop-filter:var(--veil-blur);border-top:1px solid var(--hairline)}
.tab{position:relative;flex:1;min-width:0;height:56px;border:0;background:transparent;color:var(--ink-3);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;font:inherit;cursor:pointer}
.tab .tab-label{font-size:11px;font-weight:400;white-space:nowrap}
.tab.active{color:var(--slate)}                 /* 选中：石墨蓝 + 图标填色变体 */
.tab.active .tab-label{font-weight:600}
.tab.active .ic{fill:rgba(58,75,92,.16);stroke:var(--slate)}
.tab .tbadge{position:absolute;top:5px;left:calc(50% + 6px);min-width:16px;height:16px;line-height:16px;padding:0 4px;border-radius:var(--r-pill);background:var(--signal);color:#fff;font:600 11px/16px var(--font-sans);text-align:center;font-variant-numeric:tabular-nums}

/* ── 商品卡（商城 2 列网格） ─────────────────── */
.product-grid{display:grid;grid-template-columns:repeat(2,var(--prod-w));gap:12px;padding:12px var(--gutter) 0}
.product-panel{display:none} .product-panel.on{display:block}
.product-card{width:var(--prod-w);background:var(--bg-surface);border:1px solid var(--hairline);border-radius:var(--r-card);overflow:hidden;box-shadow:var(--shadow-1)}
.pthumb{position:relative;width:100%;aspect-ratio:1;background:var(--fill-2);overflow:hidden}
.pthumb img{width:100%;height:100%;object-fit:cover;display:block}
.pinfo{padding:8px 10px 10px}
.pname{font-size:13px;font-weight:600;line-height:17px;color:var(--ink-1);height:34px;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}
.pdesc{font-size:11px;font-weight:400;line-height:15px;color:var(--ink-2);margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.pprice-row{display:flex;align-items:baseline;gap:6px;margin-top:6px}
.price-now{font-size:17px;font-weight:700;color:var(--accent);font-variant-numeric:tabular-nums}
.price-old{font-size:11px;font-weight:400;color:var(--ink-3);text-decoration:line-through;font-variant-numeric:tabular-nums}
.prate{display:flex;align-items:center;gap:3px;margin-top:4px;font-size:11px;color:var(--ink-2);font-variant-numeric:tabular-nums}
.prate .star{color:var(--accent)}

/* ── Banner（商城，真实照片 + 石墨底遮罩） ───── */
.banner{position:relative;margin:0 var(--gutter) 16px;width:var(--card-w);height:203px;border-radius:var(--r-banner);overflow:hidden;box-shadow:var(--shadow-1);background:var(--fill-2)}
.banner-slide{position:absolute;inset:0;opacity:0;transition:opacity 500ms ease-in-out}
.banner-slide.on{opacity:1}
.banner-slide img{width:100%;height:100%;object-fit:cover;display:block}
.banner-shade{position:absolute;inset:0;background:var(--shade-photo);pointer-events:none}
.banner-copy{position:absolute;left:16px;bottom:24px;right:16px}
.banner-tag{display:inline-block;height:20px;line-height:20px;padding:0 8px;border-radius:var(--r-pill);background:var(--accent-fill);color:#fff;font-size:11px;font-weight:600;margin-bottom:6px}
.banner-title{font-size:22px;line-height:27px;font-weight:700;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.banner-dots{position:absolute;left:0;right:0;bottom:12px;display:flex;justify-content:center;gap:6px;align-items:center}
.banner-dots button{border:0;padding:0;height:4px;width:4px;border-radius:var(--r-pill);background:rgba(255,255,255,.45)}
.banner-dots button.on{width:12px;background:var(--slate)}

/* ── 帖子卡（社区） ──────────────────────────── */
.post-card{margin:12px var(--gutter) 16px;background:var(--bg-surface);border:1px solid var(--hairline);border-radius:var(--r-card);box-shadow:var(--shadow-1);overflow:hidden}
.post-head{display:flex;align-items:center;gap:10px;padding:12px}
.avatar{width:36px;height:36px;border-radius:var(--r-avatar);overflow:hidden;flex:none;background:var(--fill-2)}
.avatar img{width:100%;height:100%;object-fit:cover;display:block}
.post-author{font-size:15px;font-weight:600;color:var(--ink-1)}
.post-time{font-size:11px;font-weight:400;color:var(--ink-3)}
.post-topic{font-size:11px;color:var(--accent)}
.post-text{padding:0 12px 10px;font-size:15px;font-weight:400;line-height:21px;color:var(--ink-1);display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}
.post-media{padding:0 12px}
.post-media img,.media-cell img{width:100%;height:100%;object-fit:cover;display:block}
.post-media.single{height:176px;border-radius:12px;overflow:hidden}
.media-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:3px}
.media-grid.two{grid-template-columns:repeat(2,1fr)}
.media-cell{aspect-ratio:1;border-radius:6px;overflow:hidden;background:var(--fill-2)}
.post-actions{display:flex;align-items:center;gap:20px;padding:10px 12px;font-size:13px;color:var(--ink-2);font-variant-numeric:tabular-nums}
.post-actions .act{display:flex;align-items:center;gap:5px}
.post-actions .act.liked{color:var(--signal)}
.post-actions .act.liked .ic{fill:var(--signal)}

/* ── 会话行（消息） ──────────────────────────── */
.conv-list{background:var(--bg-surface);border-top:1px solid var(--hairline);border-bottom:1px solid var(--hairline)}
.conv-row{position:relative;height:76px;display:flex;align-items:center;gap:12px;padding:0 var(--gutter);background:var(--bg-surface)}
.conv-row:not(:last-child)::after{content:"";position:absolute;left:76px;right:0;bottom:0;height:1px;background:var(--hairline);z-index:2}
.conv-avatar{width:48px;height:48px;border-radius:var(--r-avatar);overflow:hidden;flex:none;background:var(--fill-2)}
.conv-avatar img{width:100%;height:100%;object-fit:cover;display:block}
.conv-main{flex:1;min-width:0}
.conv-line1{display:flex;align-items:baseline;justify-content:space-between;gap:8px}
.conv-name{font-size:15px;font-weight:600;color:var(--ink-1)}
.conv-time{font-size:11px;font-weight:400;color:var(--ink-3);font-variant-numeric:tabular-nums;flex:none}
.conv-sum{font-size:13px;font-weight:400;color:var(--ink-2);margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.conv-unread{flex:none}
.section-bar{height:36px;margin:24px var(--gutter) 8px;display:flex;align-items:center;gap:8px}
.section-bar h3{font-size:17px;font-weight:600;margin:0;color:var(--ink-1)}
.section-bar .link{margin-left:auto;font-size:15px;color:var(--slate)}
.notice-grid{display:grid;grid-template-columns:repeat(4,var(--grid-col));padding:16px;height:88px;align-items:center}
.notice-item{display:flex;flex-direction:column;align-items:center;gap:6px;border:0;background:transparent;font-size:13px;color:var(--ink-1)}

/* ── VIP 卡（我的，题眼） ────────────────────── */
.vip-card{position:relative;margin:0 var(--gutter) 16px;height:132px;border-radius:var(--r-vip);overflow:hidden;color:#fff;
  background:linear-gradient(135deg,var(--slate) 0%,var(--slate-deep) 100%);
  box-shadow:var(--shadow-2)}
/* 极细砂质噪点：无高光、无彩色/金色投影；用内联 SVG feTurbulence 数据 URI 平铺，低透明度 */
.vip-card::before{content:"";position:absolute;inset:0;opacity:.06;pointer-events:none;
  background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='3'/%3E%3C/filter%3E%3Crect width='120' height='120' filter='url(%23n)'/%3E%3C/svg%3E")}
.vip-in{position:relative;z-index:1;padding:18px 20px;height:100%;display:flex;flex-direction:column;justify-content:center}
.vip-title{font-size:20px;font-weight:700}
.vip-points{font-size:28px;line-height:34px;font-weight:700;margin-top:6px;font-variant-numeric:tabular-nums}
.vip-tip{font-size:12px;color:rgba(255,255,255,.80);margin-top:2px}
.vip-chev{position:absolute;right:18px;top:50%;transform:translateY(-50%);width:16px;height:16px;color:rgba(255,255,255,.9)}

/* ── 宫格 / 订单（我的） ─────────────────────── */
.grid-card{display:grid;grid-template-columns:repeat(4,1fr);margin:0 var(--gutter) 16px}
.grid-cell{border:0;background:transparent;display:flex;flex-direction:column;align-items:center;gap:4px;min-height:76px;justify-content:center}
.grid-cell.social{min-height:92px}
.grid-cell .gv{font-size:17px;font-weight:600;color:var(--ink-1);font-variant-numeric:tabular-nums}
.grid-cell .gl{font-size:11px;color:var(--ink-2)}
.grid-cell .gi{color:var(--slate)}
.order-grid{display:grid;grid-template-columns:repeat(4,1fr);padding:16px 8px 18px}
.order-cell{position:relative;display:flex;flex-direction:column;align-items:center;gap:6px;border:0;background:transparent;font-size:11px;color:var(--ink-1)}

/* ── FAB / 页脚 ─────────────────────────────── */
.fab{position:absolute;right:16px;bottom:calc(var(--tab-h) + 16px);width:56px;height:56px;border-radius:var(--r-avatar);border:0;background:var(--slate-deep);color:#fff;display:flex;align-items:center;justify-content:center;box-shadow:var(--shadow-float);z-index:24}
.footer{padding:20px 0 var(--pad-bottom);text-align:center;color:var(--ink-2)}
.footer .brand{font-size:15px;font-weight:700;color:var(--slate);font-family:Georgia,"Songti SC",serif} /* 仅 Logo 衬线，唯一例外 */
.footer .slogan{font-size:12px;margin-top:4px}

/* ── Home Indicator（系统级横条，不重绘细节） ── */
.home-indicator{position:absolute;left:0;right:0;bottom:0;height:var(--safe-bottom);z-index:30;display:flex;align-items:flex-end;justify-content:center;padding-bottom:8px;pointer-events:none}
.home-indicator i{display:block;width:134px;height:5px;border-radius:var(--r-pill);background:var(--ink-1);opacity:.85}
```

### 1.5 SVG 图标符号（复用 `index.html` 内联 `<symbol>`，24×24 / stroke 1.8 / round）

可用 id 清单（原原型已定义，逐字复用，**不改形只换色**）：
`i-home` `i-users` `i-paw` `i-chat` `i-user` `i-search` `i-scan` `i-bell` `i-chevron` `i-grid` `i-crown` `i-ticket` `i-zap` `i-star` `i-heart` `i-comment` `i-share` `i-more` `i-plus` `i-camera` `i-headset` `i-pin` `i-moon` `i-sun` `i-calendar` `i-list` `i-gift` `i-mappin` `i-foot` `i-settings` `i-help` `i-info` `i-card` `i-cart` `i-truck` `i-package` `i-userplus` `i-eye` `i-bath` `i-syringe` `i-pill` `i-shirt` `i-sparkles` `i-book` `i-scale` `i-bag`

> 爪印 `#i-paw` 为品牌超级符号：宠物占位页做大号"矿物浮雕"、图片兜底用低透明装饰，**不得用彩色空态插画**。

### 1.6 素材路径约定（截图 HTML 位置决定基准）

- 建议单文件截图 HTML 放 `D:/MOYUYOWPC/deliverables/ui-prototype/redesign-v5/` 下，素材统一写 `const ASSET = '../assets/photo/';`。
- 若放 `redesign-v5/screens/` 下，则 `const ASSET = '../../assets/photo/';`。
- 32 张素材已核实存在；**逐张按文件名引用，不臆造文件名**。照片多为暖色真实摄影，是画面唯一暖色焦点：**禁叠暖金遮罩**；凡照片上压白字，必加 `--shade-photo` 底部深石墨遮罩。

### 1.7 逐页截图激活方式（无头浏览器稳定截图）

单文件内五页 DOM 同时存在，用 `.page.active` 切换；暴露全局函数 + hash 双通道：

```js
const PAGES = ['pet','home','community','messages','user'];
window.showPage = function(id){
  if(!PAGES.includes(id)) id = 'home';
  document.querySelectorAll('.page').forEach(p=>p.classList.toggle('active', p.dataset.page===id));
  document.querySelectorAll('.tab').forEach(t=>{
    const on = t.dataset.tab===id;
    t.classList.toggle('active',on);
    t.setAttribute('aria-selected', on?'true':'false');
  });
  window.__currentPage = id;
  return id;
};
// hash 激活：#pet / #home / #community / #messages / #user
function applyHash(){ window.showPage(location.hash.replace('#','') || 'pet'); }
window.addEventListener('hashchange', applyHash); applyHash();
```

**截图注意**：
1. 每页截图前 `window.showPage('home')`（或改 hash）后等待 ≥1 帧（指示条/布局稳定）。
2. **冻结 Banner 自动轮播**：截图时清掉 `setInterval`，固定 `activeBanner=0`（品牌日），避免 3000ms 跳动导致同页两次截图不一致。
3. **禁动画**：截图注入 `* { animation:none !important; transition:none !important; }`，确保指示条/淡入终态确定。
4. 保持浅色态：截图前 `document.documentElement.classList.remove('theme-dark')`；五张图默认浅态。
5. 视口设为 `393×852`，`deviceScaleFactor=1`（或 2 但五页一致），只截 `.screen` 元素，画布外圈不进图。
6. 消息页默认**已登录态**（`messagesLoggedIn=true`）以展示 5 条真实会话；`a-cs.jpg` 客服行带"禁删"语义（不显示删除入口）。
7. 我的页默认滚动到顶部（透明 nav）；如需滚动态另出图时再单独处理，五张标准图取顶部态。

---

## 第二部分 · 逐页实现规格

### PAGE 1 · 宠物占位页 PET（`#pet`）

**文案映射**：PET-01 `宠物 · 敬请期待`｜PET-02 `宠物`（compact nav 标题）｜G-03~G-08（tabBar + `9:41`）。**禁止引入 Luna/布丁等任何宠物 Hub 真实数据**。

**DOM 结构树**
```
.screen
├─ .statusbar > .sb-time("9:41") + .sb-right(信号/wifi/电池 svg)
├─ .page[data-page="pet"].active
│  └─ .nav.nav-compact > .compact-title("宠物")
└─ .tabbar > .tab[data-tab="pet"].active(★) + .tab[home/community/messages/user]
   └─ .home-indicator
```

**精确 CSS / 参数**
| 区块 | 参数 |
|---|---|
| 状态栏 | h59，透明；`.sb-time` left32/top15，600 15/20 `--ink-1` |
| compact nav | h44，top59；居中标题 `宠物` 17/600 `--ink-1`；底部无描边（占位页极简） |
| 占位主区 | flex 居中，`background:var(--bg-canvas)`；爪印母题 `#i-paw` 大号（约 96px）用 `--fill-3` + 极细砂噪点（同 VIP 噪点 URI）做"石刻"质感，透明度约 .55，位于文案上方约 28px |
| 占位文案 | PET-01 `宠物 · 敬请期待`，17/600 `--slate`，水平居中 |
| 留白 | 文案上下各留 ≥64px 雾银负空间；主区不出现任何卡片/列表 |
| tabBar | h83，`.tab[data-tab="pet"]` active（`★`）；其余未选 `--ink-3` |

**素材**：无照片（纯图形占位）；噪点用内联 SVG 数据 URI。
**状态栏 / tabBar**：亮色态深石墨字；灵动岛不重绘；tabBar 消息 tab 保留角标 `3`（G-06）。

---

### PAGE 2 · 商城页 HOME（`#home`，主锚页）

**文案映射**：HOME-01~HOME-34（全部）；G-01/G-03~G-08。**Banner 截图固定第 1 张**（HOME-08/09）；商品网格截图展示"猜你喜欢"6 卡（HOME-17~22）。

**DOM 结构树**
```
.screen
├─ .statusbar
├─ .page[data-page="home"]
│  ├─ .nav.nav-compact#nav-home
│  │  └─ .toolbar: .search-pill(🔍 + HOME-02) + .nav-btn(扫码 i-scan) + .nav-btn(🔔 i-bell + .badge-num "3")
│  │     .compact-title("商城" HOME-01)
│  └─ .scroll.home#scroll-home
│     ├─ .quick-grid (4× .quick-cell: i-grid/crown/ticket/zap + HOME-04~07)
│     ├─ .banner#banner
│     │  ├─ .banner-slide.on > img[s-golden.jpg]      ← 第1张
│     │  ├─ .banner-slide.s2 > img[s-walk.jpg]
│     │  ├─ .banner-slide.s3 > img[s-bath.jpg]
│     │  ├─ .banner-shade (--shade-photo)
│     │  ├─ .banner-copy: .banner-tag(HOME-08) + .banner-title(HOME-09)
│     │  └─ .banner-dots (3 点，第1个 .on)
│     ├─ .seg-tabs#homeTabs (HOME-14/15/16 + .seg-indicator 20×3，第1个选中)
│     └─ .product-panel.on > .product-grid > 6× .product-card
└─ .tabbar (.tab[data-tab="home"] active ★ + 消息角标3)
```

**精确 CSS / 参数**（复用 §1.4，仅列本页专属数值）
| 区块 | 参数 |
|---|---|
| nav | h44 top59 毛玻璃；搜索胶囊 h36 `--fill-2`，占位 HOME-02 `--ink-2` 13；铃铛角标 `3` `--signal` 白字 |
| 金刚区 | `.quick-grid` grid 4 等分，`padding:8px 16px 12px`（方案"底部留12"）；cell 高 84；tile 52×52 r14 `--fill-2`，图标 `--slate`（产品线仅换描边色，不换面）；标签 12 `--ink-1` |
| Banner | 361×203 r18，组距16；照片 `object-fit:cover`；`.banner-shade` 用 `--shade-photo`；tag 干玫瑰实心白字 11/600；title 白字 22/700；说明点未选4px/选中12×4 `--slate` |
| 推荐 tab | sticky h44（top59）；3 等分；选中 `--slate` 600 + 下划线 20×3 `--slate` |
| 商品网格 | 2 列，卡宽 174.5，卡间12，`padding:12px 16px 0`；卡 r16 + 1px hairline + 轻阴影；图 1:1；名 13/600 两行 clamp；描述 11 `--ink-2`；现价 17/700 `--accent`；原价 11 删除线 `--ink-3`；★评分+评价数 11 tabular-nums |
| 底部 | `.product-grid` 末卡后追加 `padding-bottom:var(--pad-bottom)`（107px） |
| tabBar | 商城选中 ★；消息角标 3 |

**18 款 SKU → 真实素材映射（逐字文案 + 真实文件名，不得替换/臆造）**

猜你喜欢（截图展示此组）：
| 编号 | 名称 | 描述 | 现价 | 原价 | 评分 | 评价 | 素材 |
|---|---|---|---|---|---|---|---|
| HOME-17 | 云端羊绒宠物窝 | 柔软保暖 · 可拆洗 | ¥89.00 | ¥129.00 | ★4.9 | 1.2k | `p-home-bed.jpg` |
| HOME-18 | 复古皮革牵引绳 | 耐磨手柄 · 复古质感 | ¥39.00 | ¥59.00 | ★4.8 | 862 | `p-gear-leash.jpg` |
| HOME-19 | 陶瓷慢食碗 | 防打翻 · 健康进食 | ¥29.00 | ¥45.00 | ★4.9 | 2.1k | `p-home-bowl.png` |
| HOME-20 | 亚麻猫抓板窝 | 磨爪休息两用 | ¥49.00 | ¥69.00 | ★4.7 | 534 | `p-play-scratch.png` |
| HOME-21 | 静音逗猫棒套装 | 互动解闷 · 猫咪最爱 | ¥19.00 | ¥32.00 | ★4.8 | 1.6k | `p-play-toy.jpg` |
| HOME-22 | 恒温宠物饮水机 | 循环过滤 · 静音省心 | ¥119.00 | ¥169.00 | ★4.9 | 978 | `p-gear-fountain.png` |

今日爆款（切换 tab2 时用，数据须齐）：
| 编号 | 名称 | 描述 | 现价 | 原价 | 评分 | 评价 | 素材 |
|---|---|---|---|---|---|---|---|
| HOME-23 | 云朵乳胶睡垫 | 整晚好眠 · 限时特惠 | ¥79.00 | ¥139.00 | ★4.9 | 3.4k | `s-cat-sofa.jpg` |
| HOME-24 | 轻便可折叠航空箱 | 出行无忧 · 大空间 | ¥159.00 | ¥229.00 | ★4.8 | 1.1k | `p-gear-carrier.jpg` |
| HOME-25 | 冻干混合猫粮 1.5kg | 高蛋白 · 无谷物 | ¥99.00 | ¥149.00 | ★4.9 | 5.2k | `p-care-treats.jpg` |
| HOME-26 | 实木猫爬架 | 剑麻柱 · 稳固不晃 | ¥199.00 | ¥299.00 | ★4.7 | 689 | `s-cat3.jpg` |
| HOME-27 | 硅胶折叠旅行碗 | 随身便携 · 外出必备 | ¥25.00 | ¥39.00 | ★4.8 | 2.8k | `p-gear-bowl.png` |
| HOME-28 | 除毛梳毛刷 | 一梳即净 · 不伤皮肤 | ¥35.00 | ¥55.00 | ★4.9 | 1.9k | `s-bath.jpg` |

口碑好评（切换 tab3 时用，数据须齐）：
| 编号 | 名称 | 描述 | 现价 | 原价 | 评分 | 评价 | 素材 |
|---|---|---|---|---|---|---|---|
| HOME-29 | 北欧风实木宠物餐桌 | 高颜值 · 护颈设计 | ¥139.00 | ¥189.00 | ★5.0 | 421 | `s-dog.jpg` |
| HOME-30 | 天然棉绳玩具套装 | 耐咬磨牙 · 安全无毒 | ¥29.00 | ¥49.00 | ★4.9 | 1.3k | `p-play-jacket.jpg` |
| HOME-31 | 智能称重喂食器 | 定时定量 · 远程投喂 | ¥229.00 | ¥329.00 | ★4.8 | 756 | `p-gear-fountain.png` |
| HOME-32 | 羊羔绒保暖毯 | 亲肤柔软 · 四季可用 | ¥59.00 | ¥89.00 | ★4.9 | 2.4k | `s-cat2.jpg` |
| HOME-33 | 宠物专用吹水机 | 低噪速干 · 居家洗护 | ¥269.00 | ¥369.00 | ★4.7 | 318 | `p-care-shampoo.jpg` |
| HOME-34 | 陶瓷浮雕慢食盘 | 美观实用 · 防滑底座 | ¥45.00 | ¥69.00 | ★4.9 | 903 | `p-care-biscuits.jpg` |

**状态栏 / tabBar**：亮色态深石墨字；商城选中 ★；铃铛角标 `3`（HOME-03）与 tabBar 消息角标 `3`（G-06）并存且数值一致。

---

### PAGE 3 · 社区页 COMMUNITY（`#community`）

**文案映射**：COMM-01~COMM-17；G-03~G-08。截图取"推荐"tab + "全部"chip，展示 5 条真实帖子。

**DOM 结构树**
```
.screen
├─ .statusbar
├─ .page[data-page="community"]
│  ├─ .nav.nav-large#nav-community
│  │  └─ .toolbar.nav-right: .nav-btn(i-search) + .nav-btn(i-bell + .badge-dot)
│  │     .large-title("社区" COMM-01)
│  └─ .scroll.community#scroll-community
│     ├─ .seg-tabs#communityTabs (COMM-02/03/04 + .seg-indicator.underline 24×2，推荐选中)
│     ├─ .search-row(🔍 + COMM-05 "搜索话题或用户")
│     ├─ .chip-scroll#topicChips (COMM-06~10，全部选中)
│     └─ #postList > 5× .post-card
│        (作者行 + 图片 + 正文 + 互动行)
├─ .fab (i-plus / i-camera 白，右下悬浮)
└─ .tabbar (.tab[data-tab="community"] active ★)
```

**精确 CSS / 参数**
| 区块 | 参数 |
|---|---|
| Large Title 区 | nav h96（含 safe-top），`background:var(--bg-canvas)`；`.large-title` left16/bottom12，34/700 `--ink-1`，letter-spacing:-.5px；右侧搜索 + 铃铛红点 |
| 分段 tab | sticky h44 top59 毛玻璃，3 等分；选中 `--slate` 600 + 下划线 24×2 `--slate` |
| 搜索条 | h36，组距12，`--fill-2` 胶囊；占位 COMM-05 `--ink-2` 13；放大镜 i-search 16 |
| 话题 chip 横滚 | h32，组距12；chip `--fill-2`，选中 `--tint` 底 + `--slate` 字；`#` 话题词用 `--accent`（COMM-07~10），`全部`（COMM-06）无强调色 |
| 帖子卡 | 白卡 r16 + 1px hairline + 轻阴影，卡间16，`margin:12px 16px 16px`；作者行头像36 + 昵称15/600 + 时间11 `--ink-3` + 话题11 `--accent` + 更多 i-more；图片圆角12（单图高176，九宫格3列 gap3，2图2列）；正文15/21 三行 clamp；互动行 点赞/评论/分享 13 `--ink-2`，点赞态实心 `--signal` 且计数递增（双重编码） |
| FAB | 56 圆 `--slate-deep` 白图标，右下距16，bottom = tabBar83+16 |
| 底部 | 末卡留 `--pad-bottom` 107px |
| tabBar | 社区选中 ★ |

**5 条真实帖子（作者/时间/话题/正文/赞/评/头像/配图，逐字）**
| 编号 | 作者 | 时间 | 话题 | 正文（逐字，含 emoji/标点） | 赞/评 | 头像 | 配图（真实文件） |
|---|---|---|---|---|---|---|---|
| COMM-11 | 布丁妈妈 | 2小时前 | #萌宠日常 | 周末带布丁去公园，第一次踩草地，小心翼翼又兴奋得不行，记录一下这个可爱瞬间～ | 128/24 | `s-dog.jpg` | 1图 `s-walk.jpg` |
| COMM-12 | Luna 的窝 | 5小时前 | #新家开箱 | 新买的云端羊绒宠物窝到了，质感真的很好，和家里的装修也超搭！开箱分享给各位家长。 | 86/12 | `s-cat.jpg` | 4图 `p-home-bed.jpg` `s-cat-sofa.jpg` `p-home-bowl.png` `s-cat3.jpg` |
| COMM-13 | 阿柴的日常 | 昨天 21:30 | #喂养日记 | 换粮第三周，肠胃适应得不错，便便也很健康。慢食碗果然有用，吃饭速度降下来了。 | 203/37 | `s-golden.jpg` | 2图 `p-care-treats.jpg` `p-home-bowl.png` |
| COMM-14 | 小满同学 | 昨天 15:12 | #萌宠日常 | 今天给主子梳毛，掉毛季真的是一场战斗，好在有除毛梳，沙发终于能看了😂 推荐给同样头疼的家长。 | 64/9 | `s-cat2.jpg` | 1图 `s-bath.jpg` |
| COMM-15 | Momo 的家长 | 2天前 | #好物测评 | 恒温饮水机用了半个月，滤芯更换很方便，猫咪喝水量明显变多了，静音效果也符合预期。 | 152/28 | `s-cat3.jpg` | 3图 `p-gear-fountain.png` `s-cat.jpg` `p-gear-bowl.png` |

> 空态（非本图默认，切换无内容话题时）：爪印母题 + COMM-16 `这里还没有内容` + COMM-17 `换个话题看看吧`。文案本身不改写、不截断改写，仅界面 3 行 clamp 呈现。

**状态栏 / tabBar**：亮色态深石墨字；社区选中 ★；铃铛用红点（非数字）。

---

### PAGE 4 · 消息页 MESSAGES（`#messages`）

**文案映射**：MSG-01~MSG-14；G-03~G-08。截图取**已登录态**，展示通知四宫格 + 5 条真实会话；未读合计 pill = 3（MSG-12）。

**DOM 结构树**
```
.screen
├─ .statusbar
├─ .page[data-page="messages"]
│  ├─ .nav.nav-large#nav-messages
│  │  └─ .toolbar.nav-right: .nav-btn(i-bell + 红点) + .nav-btn(i-userplus 群聊) + .nav-btn(i-chat 私信)
│  │     .large-title("消息" MSG-01)
│  └─ .scroll.messages#scroll-messages
│     ├─ .group-card > .notice-grid (MSG-02~05)
│     ├─ .section-bar: h3("私信" MSG-11) + .pill("3" MSG-12) + .link("发起私信" MSG-13)
│     └─ .conv-list > 5× .conv-row
└─ .tabbar (.tab[data-tab="messages"] active ★ + .tbadge "3")
```

**精确 CSS / 参数**
| 区块 | 参数 |
|---|---|
| Large Title 区 | 同社区：nav h96，`消息` 34/700 `--ink-1`；右侧铃铛红点 + 发起群聊 + 发起私信（i-userplus / i-chat） |
| 通知四宫格 | 白卡 361×88 r16，组距16；`.notice-grid` 4×82.25，padding16；圆形图标底 40 `--fill-2`；图标 `全部 i-grid / 互动 i-heart / 订单 i-package / 系统 i-bell`；互动、系统带信号红点 |
| 私信 section bar | h36，组距8；标题 `私信` 17/600 `--ink-1`；未读 pill `3` `--signal` 白字；右 `发起私信` 15 `--slate` |
| 会话列表 | 行高76；头像48 圆；名称15/600 `--ink-1`；时间11 `--ink-3` tabular-nums；摘要13 `--ink-2` 单行省略；未读角标 `--signal` 白字；行间 1px hairline（left76 起）；客服中心用 `a-cs.jpg` 且**不显示删除** |
| 底部 | 末行留 `--pad-bottom` 107px |
| tabBar | 消息选中 ★ + 角标 `3` |

**5 条真实会话（逐字）**
| 编号 | 名称 | 时间 | 摘要 | 未读 | 头像 | 备注 |
|---|---|---|---|---|---|---|
| MSG-06 | 客服中心 | 09:41 | 您好，有什么可以帮您？ | 0 | `a-cs.jpg` | service，禁删 |
| MSG-07 | Luna 妈妈 | 09:28 | 昨天寄的宠物窝收到了吗？ | 2 | `s-cat.jpg` | 未读 2 |
| MSG-08 | 宠物友好酒店 | 昨天 | 您的预订已确认，期待入住 | 0 | `s-hero.jpg` | — |
| MSG-09 | 布丁爸爸 | 星期三 | 周末一起带狗狗去遛弯呀 | 1 | `s-dog2.jpg` | 未读 1 |
| MSG-10 | 领券小助手 | 周二 | 您有一张 20 元优惠券待领取 | 0 | `a-brand.jpg` | 头像含暖金衬线字，仅作小圆头像，不整图铺底 |

> 未登录态（非本图默认）：MSG-14 标题 `登录后查看消息` / 副文 `与宠友私信、互动通知一站式查看`。

**状态栏 / tabBar**：亮色态深石墨字；消息选中 ★ + 角标3；全页唯一高饱和点为信号红，克制不刺眼。

---

### PAGE 5 · 我的页 USER（`#user`）

**文案映射**：USER-01~USER-37；G-01/G-02/G-03~G-08。截图取顶部态（透明 nav），VIP 卡为视觉锚。

**DOM 结构树**
```
.screen
├─ .statusbar
├─ .page[data-page="user"]
│  ├─ .nav.nav-user#nav-user
│  │  ├─ .nav-btn#themeBtn(i-moon)             ← 右上主题切换
│  │  └─ .user-title("我的" USER-04)           ← 滚动后浮出
│  └─ .scroll.user#scroll-user
│     ├─ .user-header (头像 + 昵称 + 邮箱 + 会员 pill)
│     ├─ .vip-card (VIP-05/06/07 + chevron)
│     ├─ .group-card > .grid-card#walletGrid  (USER-08~11)
│     ├─ .group-card > .grid-card#socialGrid  (USER-12~15)
│     ├─ .group-card > .order-grid#orderGrid  (USER-16~21)
│     ├─ #funcList > 3×( .section-head + .group-card > .row×N )  (USER-22~35)
│     └─ .footer (USER-36/37)
└─ .tabbar (.tab[data-tab="user"] active ★)
```

**精确 CSS / 参数**
| 区块 | 参数 |
|---|---|
| 头像 header | 高约200，居中；头像72 圆 `s-cat2.jpg`；昵称 `Momo 的家长` 22/700 `--ink-1`（USER-01）；邮箱 13 `--ink-2`（USER-02）；会员 pill `♛ 黄金会员` `--accent` 文字（USER-03）；右上主题切换 i-moon |
| VIP 卡 | 361×132 r20，组距16；`linear-gradient(135deg, #3A4B5C→#2B3A4A)` 明度渐层 + 极细砂噪点（`.vip-card::before`）；**禁高光/彩色投影**；标题 `MOYUYO 会员` 20/700（USER-05）；Points `2,480 Points` 28/700 tabular-nums（USER-06）；提示 `再消费 ¥320 可升级铂金会员` 12 白80%（USER-07）；右侧 chevron |
| 钱包宫格 | 白卡 r16 组距16；4 列；数值 17/600 tabular-nums `--ink-1`，标签 11 `--ink-2` |
| 社交宫格 | 同上；第 4 项为 `个人中心` 纯文字（i-user，无数值） |
| 订单宫格 | 白卡 r16 组距16；标题 `我的订单` + `查看全部 ›`（`--slate`）；4 等分，待付款角标2、待收货角标1（`--signal` 白字） |
| 功能列表 | 3 组 section head（13 `--ink-2`）+ 白卡 r16，组距16；行 h52，图标底 14（28×28 r8 `--fill-2`），文案15，chevron；行间 1px hairline |
| 页脚 | 约60；`MOYUYO`（Logo 衬线，唯一例外）+ `与爱同行 · 精致养宠日常` 12 `--ink-2`；底部留 `--pad-bottom` |
| 透明 nav | 顶部透明；滚动后 `.nav-user.scrolled` 浮出毛玻璃 + 居中 `我的` 17/600（USER-04） |
| tabBar | 我的选中 ★ |

**宫格 / 订单 / 功能项数据（逐字）**
- 钱包（USER-08~11）：浏览足迹 `128`｜积分 `2,480`｜优惠券 `6`｜收藏 `23`
- 社交（USER-12~15）：关注 `36`｜粉丝 `1.2k`｜收藏 `23`｜`个人中心`（i-user，无数值）
- 订单（USER-18~21）：`待付款`(角标2, i-card)｜`购物车`(i-cart)｜`待收货`(角标1, i-truck)｜`待评价`(i-star)
- 常用（USER-22~26）：`签到` i-calendar｜`任务` i-list｜`邀请` i-gift｜`会员` i-crown
- 账户与服务（USER-27~32）：`地址` i-mappin｜`宠物` i-paw｜`收藏` i-heart｜`足迹` i-foot｜`设置` i-settings
- 关于（USER-33~35）：`帮助` i-help｜`关于` i-info

**素材**：头像 `s-cat2.jpg`；VIP 噪点内联 SVG。**状态栏 / tabBar**：亮色态深石墨字；我的选中 ★。

---

## 第三部分 · 五页并排总览图（第 6 张）

**规格**（沿用方案第二部分）
- 五张页面图按 tabBar 顺序并排：宠物 / 商城 / 社区 / 消息 / 我的（左→右）。
- 1:1 等比缩放（不拉伸），页间距 24px；统一雾银底 `--bg-canvas`。
- 每页顶部加页名标签：宠物/商城/社区/消息/我的，11px `--slate`。
- 整体加极轻冷灰描边圆角外框（1px `--hairline`，r16~20），**不做拼贴阴影**。
- 建议画布：宽 ≈ 5×393 + 4×24 + 2×24 = **2109px**，高 ≈ 852 + 标签区约 40 + 2×24 = **940px**（数值供参考，以实际排版留白为准，五页须等高对齐）。
- 五页内部状态与本页截图一致（Banner 均取第 1 张、消息均登录态、我的均顶部态），确保总览与单图逐页可对。

---

## 第四部分 · 交付门禁（本文件边界）

- 本文件只给**实现规格与截图激活方式**，不写最终 HTML、不截图、不改 `index.html`、不改上游三份文档。
- 所有文案按 `02-marketing-copy.md` 编号逐字取用；发现疑似错别字也**不自行改**，退回主理人请用户确认。
- 所有商品/帖子/会话数据来自原原型实证，无臆造；素材只用 `assets/photo/` 已存在文件。
- 成片验收按配套 `05-qa-checklist.md` 执行。
