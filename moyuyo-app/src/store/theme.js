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
      // 全平台统一切换深色:
      //   H5 端 .theme-dark 加到 <html>,CSS 变量(.theme-dark 选择器)立即覆盖 :root 内的默认值
      //   APP/MP 端 document.body 是稳定可访问的根节点,加类后同样触发 CSS 变量覆盖,
      //     不依赖 plus.navigator / page-meta 等 nvue 原生 API,各端行为一致
      // #ifdef H5
      const htmlRoot = document.documentElement
      if (this.darkMode) htmlRoot.classList.add('theme-dark')
      else htmlRoot.classList.remove('theme-dark')
      // #endif
      if (typeof document !== 'undefined' && document.body) {
        if (this.darkMode) document.body.classList.add('theme-dark')
        else document.body.classList.remove('theme-dark')
      }
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
