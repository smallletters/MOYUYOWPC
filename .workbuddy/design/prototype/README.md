# MOYUYO TabBar · Phase 3 可交互原型

**Linen & Brass（亚麻与黄铜）· 5 tab 单文件可交互 HTML 原型**
视觉真值源：`../../phase1-visual-direction.md`（Phase 1 v1.8）+ `../../phase2-ux-spec.md`（Phase 2 v1.4）

---

## 打开方式

双击 `index.html` 即可（`file://` 协议可用）。**不依赖任何构建工具 / 网络 / 服务端**：
无 `fetch`、无 ES module、无外部 CSS/JS 引用；lucide 图标字体以 base64 内联（规避 `file://` 跨源字体被拦）。

---

## ⚠️ 构建关系（改代码前必读）

| 文件 | 角色 |
|---|---|
| **`_template.html`** | **源文件**。改这个 |
| **`index.html`** | **产物**。由 `_build.py` 生成，**不要直接改**（会被覆盖） |

```bash
cd .workbuddy/design/prototype
python _build.py          # 源 → 产物（同时把项目真实 lucide.css + lucide.ttf 转成 base64 内联注入）
node _verify.js           # Playwright：截图 + 交互断言 + 控制台/broken img 检查
node _selfcheck.js        # 自查红线审计（低对比 / 金色 / 粗体正文 / 多主角 / 危险面文字）
node _shots_extra.js      # 补充截图（会员卡特写、消息通知段、话题 tab 选择器态…）
```

`_template.html` 里 `/*LUCIDE_INLINE*/` 是 lucide CSS 的注入占位符（**只能有 1 处**，
`_build.py` 用 `replace(..., 1)` 只替换第一处）。

---

## 目录

```
index.html            ← 交付物（单文件，202KB）
_template.html        ← 源文件
_build.py             ← 构建脚本
_verify.js            ← 交互 + 截图自检
_selfcheck.js         ← 红线审计
_shots_extra.js       ← 补充截图
assets/
  lucide.ttf          ← 项目真实图标字体（base64 内联进 index.html）
  lucide.css          ← 项目真实图标类名表（参考）
  lucide-inline.css   ← 生成物：lucide.css + base64 字体
  photo/              ← 31 张项目实拍图
_shots/               ← 截图产物
```

---

## 展示体只有 3 处（Phase 1 §3 v1.3/v1.4 收敛）

`--font-display` 只用于 **Latin 短文本**：① 品牌 wordmark `MOYUYO` ② Hero 标题（44rpx）③ 会员卡等级名·英文（56rpx）。
**所有中文标题 / 数据带数字 / Tab 文字一律 `--font-sans` + 500/600** —— Playfair Display 不含中文字形，中文用展示体必然 fallback 宋体。
（数据带数字靠**字重 600 + 32/36rpx + `--color-text`** 承重，不靠字族，也避免与会员卡等级名抢视觉语汇。）

---

## 原型边界（Phase 3 不还原的 uni-app 能力）

原型只负责**视觉与交互形态**，以下在真实实现里由 uni-app 承担，详见 Spec 面板「真实实现」列：

- 原生 tabBar（不吃 CSS 变量，需 `theme.js` 的 `TAB_BAR_PALETTE` + `uni.setTabBarStyle()` 手动同步）
- `uni.showModal / showActionSheet`（`confirmColor` 只能写字面量；破坏性用 `#C96E5F` / `#D9836F`）
- 原生下拉刷新（原型不设计自定义刷新头）
- `switchTab` **路径后不能带参数** → tabBar 页的登录回跳必须走 **storage + action**
- `uni.reLaunch` 跳登录是**红线条目**（销毁 tabBar 栈 + 丢社区草稿）→ 必须改 `navigateTo`
- 3D 场景（pet 页只还原视觉层次，不还原交互）
- `env(safe-area-inset-*)`（原型写死 44px / 34px）
