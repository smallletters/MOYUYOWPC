/**
 * 主题状态管理：light / dark / system
 *
 * 暴露两层语义:
 * - mode: 'light' | 'dark' | 'system'  (三态,细粒度控制)
 * - darkMode: 当前是否实际启用深色色板 (布尔,简化设置页开关语义)
 *
 * 设置页只关心 darkMode (bool) 即可,无需感知 system 跟随。
 */
import { defineStore } from 'pinia'
import { setStorage, getStorage, STORAGE_KEYS } from '@/utils/storage'

const VALID_MODES = ['light', 'dark', 'system']

// 系统主题监听是否已注册（模块级幂等标记，避免 onLaunch 重复注册）
let _systemWatcherStarted = false

/**
 * 原生 tabBar 深/浅配色表。
 *
 * 为什么要单独维护一份：原生 tabBar 是客户端「原生」视图（H5 端则被 uni-app 渲染成
 * 带行内样式的 <uni-tabbar>），**不参与 CSS 变量 / .theme-dark 的计算**。
 * 所以深色模式下页面主体会自动变暗，底部导航栏却仍是白底——必须额外通过
 * uni.setTabBarStyle / uni.setTabBarItem 在运行时改色。
 *
 * 取色对齐 src/styles/variables.scss 的 design token：
 *
 *          未选中(文字/图标)              选中                背景              上边框
 * light   --color-text-tertiary      --color-primary    --color-surface
 *         #9a948c                    #dbc98a            #ffffff           black
 * dark    #807a72                    #c9b47a            #2a2724           white
 *
 * 注：borderStyle 官方仅支持 black / white，分别对应 rgba(0,0,0,.33) / rgba(255,255,255,.33)。
 */
const TAB_BAR_PALETTE = {
  light: {
    color: '#9a948c',
    selectedColor: '#dbc98a',
    backgroundColor: '#ffffff',
    borderStyle: 'black',
  },
  dark: {
    color: '#807a72',
    selectedColor: '#c9b47a',
    backgroundColor: '#2a2724',
    borderStyle: 'white',
  },
}

/**
 * 原生导航栏（navbar）深浅配色表。
 *
 * 与 tabBar 同理：使用 pages.json 默认导航栏（未设 navigationStyle:custom）的页面，
 * 其顶部标题栏由客户端原生视图渲染，**不参与 CSS 变量计算**，
 * 深色模式下会一直是白底（pages.json globalStyle 写死 #FFFFFF）。
 * 必须通过 uni.setNavigationBarColor 在运行时改色。
 *
 * 注意：frontColor 官方仅支持 '#ffffff' / '#000000' 两个值。
 */
const NAV_BAR_PALETTE = {
  light: { frontColor: '#000000', backgroundColor: '#ffffff' },
  dark: { frontColor: '#ffffff', backgroundColor: '#1a1816' },
}

/**
 * tabBar iconfont 字形兜底表（顺序必须与 pages.json tabBar.list 一致）。
 *
 * 为什么需要兜底：H5 端 uni-app 会把整份 pages.json 暴露成 window.__uniConfig，
 * 运行时可读；但 APP 端 __uniConfig 由 app-config-service.js 以局部 const 形式
 * 注入（实测产物里是 IIFE 内的 `const __uniConfig = {...}`），**不保证挂在
 * globalThis 上**。一旦读不到，setTabBarItem 的图标改色就会整段被跳过，
 * 深色模式下会出现"底栏背景变了、图标还是浅色"的半成品状态。
 * 所以这里保留一份字形常量：优先取运行时配置，取不到再退回常量。
 */
const TAB_BAR_ICON_FALLBACK = [
  { iconfont: { text: '\ue93c', fontSize: '20px' } }, // home
  { iconfont: { text: '\ue936', fontSize: '20px' } }, // category
  { iconfont: { text: '\ue956', fontSize: '20px' } }, // pet
  { iconfont: { text: '\ue94c', fontSize: '20px' } }, // community
  { iconfont: { text: '\ue982', fontSize: '20px' } }, // user
]

/**
 * 读取 tabBar 各项的 iconfont 字形 / 字号。
 * 优先读 pages.json 的编译产物 __uniConfig.tabBar.list（H5 实测可读、
 * APP 端视注入方式而定）；读不到时退回 TAB_BAR_ICON_FALLBACK。
 */
function readTabBarList() {
  try {
    const cfg = typeof globalThis !== 'undefined' ? globalThis.__uniConfig : null
    const list = cfg && cfg.tabBar && cfg.tabBar.list
    if (Array.isArray(list) && list.length && list.some((it) => it && it.iconfont)) {
      return list
    }
  } catch (e) {
    /* 读取失败按兜底处理 */
  }
  return TAB_BAR_ICON_FALLBACK
}

/**
 * 读取"系统当前是否为深色"。
 * - H5：matchMedia 天然跟随系统
 * - App / 小程序：必须在 manifest.json 对应平台开启 darkmode:true，
 *   uni.getSystemInfoSync().theme 才会返回 'light' | 'dark'；
 *   未开启时 theme 为 undefined，这里按浅色兜底。
 */
function readSystemDark() {
  // #ifdef H5
  if (typeof window !== 'undefined' && window.matchMedia) {
    return !!window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  // #endif
  try {
    const info = uni.getSystemInfoSync ? uni.getSystemInfoSync() : null
    return !!info && info.theme === 'dark'
  } catch (e) {
    console.warn('[theme] readSystemDark failed', e)
    return false
  }
}

// 把存储的 mode 映射到"当前是否启用深色"(system 时跟随系统)
function resolveDarkMode(mode) {
  if (mode === 'dark') return true
  if (mode === 'light') return false
  return readSystemDark()
}

export const useThemeStore = defineStore('theme', {
  state: () => {
    const mode = getStorage(STORAGE_KEYS.THEME, 'system')
    return {
      mode: VALID_MODES.includes(mode) ? mode : 'system',
      // 派生布尔字段,初始化时按 mode 解析一次
      darkMode: resolveDarkMode(mode),
    }
  },

  actions: {
    /** 三态设置: 'light' | 'dark' | 'system' */
    setMode(mode) {
      if (!VALID_MODES.includes(mode)) return
      this.mode = mode
      this.darkMode = resolveDarkMode(mode)
      setStorage(STORAGE_KEYS.THEME, mode)
      this.applyTheme()
    },

    /**
     * 简化接口:只关心开关语义(供设置页 Switch 使用)
     * true  -> 强制 dark
     * false -> 强制 light
     */
    setDarkMode(enabled) {
      this.setMode(enabled ? 'dark' : 'light')
    },

    applyTheme() {
      // 全平台统一切换主题（html + body 两端成对管理）：
      //   theme-dark ：启用深色色板（.theme-dark 选择器覆盖 :root 默认值）
      //   theme-light：**压制** @media (prefers-color-scheme: dark)
      //      —— 深色媒体查询的选择器已改为 :root:not(.theme-light)。
      //         系统深色 + App 内「强制浅色」时，仅靠「不加 theme-dark」压不住系统深色，
      //         必须显式补 theme-light，强制浅色才会生效（本轮修复目标）。
      //
      // 类名依据「用户三态 mode」而非派生布尔 darkMode，保证两端互斥、无残留：
      //   mode==='light'        → 加 theme-light（强制浅色）
      //   其余（dark / system）  → 移除 theme-light（system 下绝不能残留，
      //                            否则系统深色会被自己压成浅色）
      //   实际深色 darkMode 为真（dark 或 system 跟随到深色）→ 加 theme-dark
      //
      // 注：原先 html 的类操作被 H5 条件编译包裹，APP 端拿不到 theme-light；
      // 而 APP 端 @media 同样作用于 :root，故抽出为跨端执行 + guard + try/catch。
      if (typeof document !== 'undefined') {
        try {
          const isForcedLight = this.mode === 'light'
          const useDark = !!this.darkMode
          const rootNodes = [document.documentElement, document.body].filter(Boolean)
          rootNodes.forEach((el) => {
            if (el && el.classList) {
              el.classList.toggle('theme-light', isForcedLight)
              el.classList.toggle('theme-dark', useDark)
            }
          })
        } catch (e) {
          console.warn('[theme] applyTheme class toggle failed', e)
        }
      }
      // 原生 tabBar 不读 CSS 变量，必须额外走运行时 API 改色
      this.syncTabBar()
      // 原生导航栏同理（仅对使用 pages.json 默认导航栏的页面有意义）
      this.syncNavigationBar()
    },

    /**
     * 同步原生导航栏配色。
     *
     * ⚠️ 与 tabBar 一样的时机约束：setNavigationBarColor 只作用于「当前页」，
     * 且 H5 端页面的 head 是每次进入页面时按 pages.json 重新创建的，
     * 所以「导航到新页面」的场景必须由页面自己的生命周期再调一次
     * （见 utils/themePageMixin.js 的全局 mixin）。
     * 这里负责「在当前页切换主题」的场景。
     * 页面为 navigationStyle:custom 时没有原生 head，调用会 fail，静默忽略即可。
     */
    syncNavigationBar(attempt = 0) {
      const p = NAV_BAR_PALETTE[this.darkMode ? 'dark' : 'light']
      try {
        uni.setNavigationBarColor({
          frontColor: p.frontColor,
          backgroundColor: p.backgroundColor,
          fail: () => {},
        })
      } catch (e) {
        /* custom 导航栏页面无原生 head，忽略 */
      }
      // ⚠️ 时机：页面组件的 mounted 早于 uni-h5 把 pages.json 默认色刷到
      // `div.uni-page-head` 的行内 style 上，首帧设置会被随后的默认色覆盖。
      // 延后重试两次兜底（幂等，重复设置无副作用）。
      if (attempt < 2) {
        setTimeout(() => this.syncNavigationBar(attempt + 1), 120 * (attempt + 1))
      }
    },

    /**
     * 同步原生 tabBar 配色（深色模式的关键：CSS 变量管不到原生 tabBar）。
     *
     * ⚠️ 调用时机有硬约束：uni-app 的 setTabBarStyle / setTabBarItem 在 beforeInvoke 里会
     * 校验「当前页必须是 tabBar 页面」(getCurrentPageMeta().isTabBar)，不是就直接 reject；
     * tabBar 尚未创建时 getTabBar() 为 null 同样 reject。
     * 所以本方法只是「尽力而为」的一次尝试（在 tabBar 页面上切主题时立即生效），
     * 冷启动、以及从设置页（非 tabBar 页）切完主题返回的场景，
     * 由各 tabBar 页面在 onShow 里再调一次兜底，保证任何入口进来配色都是对的。
     */
    syncTabBar(attempt = 0) {
      const p = TAB_BAR_PALETTE[this.darkMode ? 'dark' : 'light']
      // tabBar 尚未创建（冷启动首帧）或当前不在 tabBar 页时，API 会 reject；
      // 冷启动场景延迟重试两次即可覆盖；仍失败说明确实不在 tabBar 页，放弃即可
      //（该场景由 tabBar 页自己的 onShow 负责）。
      const onFail = () => {
        if (attempt >= 2) return
        setTimeout(() => this.syncTabBar(attempt + 1), 300 * (attempt + 1))
      }

      // 1) 文字色 / 背景色 / 上边框（H5 + App 均支持）
      try {
        uni.setTabBarStyle({
          color: p.color,
          selectedColor: p.selectedColor,
          backgroundColor: p.backgroundColor,
          borderStyle: p.borderStyle,
          fail: onFail,
        })
      } catch (e) {
        onFail()
      }

      // 2) 字体图标颜色。pages.json 里每个 item 单独配了 iconfont.color/selectedColor，
      //    只调 setTabBarStyle 不会改图标（已实测：H5 端文字/背景变了、图标没变），
      //    必须逐项 setTabBarItem。iconfont 字段 App 3.4.4+ 支持，实测 H5 也生效。
      //    注意 text/selectedText/fontSize 必须原样带上，否则会把字形冲掉。
      readTabBarList().forEach((item, index) => {
        const ic = item && item.iconfont
        if (!ic || !ic.text) return
        try {
          uni.setTabBarItem({
            index,
            iconfont: {
              text: ic.text,
              selectedText: ic.selectedText || ic.text,
              fontSize: ic.fontSize || '20px',
              color: p.color,
              selectedColor: p.selectedColor,
            },
            fail: onFail,
          })
        } catch (e) {
          onFail()
        }
      })
    },

    /** 跟随系统主题变化(system 模式下用户切换系统深色时调用) */
    refreshFromSystem() {
      if (this.mode !== 'system') return
      const next = resolveDarkMode('system')
      if (next !== this.darkMode) {
        this.darkMode = next
        this.applyTheme()
      }
    },

    /**
     * 注册系统主题变化监听，实现 system 模式的实时跟随。
     * 在 App.vue onLaunch 调用一次即可（模块级幂等，重复调用直接返回）。
     */
    startSystemWatcher() {
      if (_systemWatcherStarted) return

      // App 端必须先开启"跟随系统主题"，否则收不到主题切换事件
      // #ifdef APP-PLUS
      try {
        if (typeof plus !== 'undefined' && plus.nativeUI && plus.nativeUI.setUIStyle) {
          plus.nativeUI.setUIStyle('auto')
        }
      } catch (e) {
        console.warn('[theme] setUIStyle(auto) failed', e)
      }
      // #endif

      // onThemeChange 仅 H5 / App / 微信小程序等部分平台支持，其他端静默跳过
      if (typeof uni === 'undefined' || typeof uni.onThemeChange !== 'function') return
      _systemWatcherStarted = true

      uni.onThemeChange((res) => {
        // 非 system 模式（强制浅色/深色）不跟随系统
        if (this.mode !== 'system') return
        const next = !!(res && res.theme === 'dark')
        if (next === this.darkMode) return
        this.darkMode = next
        this.applyTheme()
      })
    },
  },
})

// 注意:首次进入应用时由 App.vue onLaunch 主动调用 applyTheme(),
// 这里不在模块顶层 useThemeStore(),避免 pinia 未安装时崩溃。
