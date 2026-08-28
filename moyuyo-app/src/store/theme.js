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

// 把存储的 mode 映射到"当前是否启用深色"(system 时跟随系统)
function resolveDarkMode(mode) {
  if (mode === 'dark') return true
  if (mode === 'light') return false
  // system: 仅 H5 能查询;其他端按 light 处理,后续 onShow 监听会刷新
  // #ifdef H5
  if (typeof window !== 'undefined' && window.matchMedia) {
    return !!window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  // #endif
  return false
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
      // #ifdef H5
      const root = document.documentElement
      if (this.darkMode) {
        root.classList.add('theme-dark')
      } else {
        root.classList.remove('theme-dark')
      }
      // #endif
      // #ifdef MP-WEIXIN / APP-PLUS
      // 小程序和 APP 通过 navigationBarTextStyle / page meta 实现
      // 这里简化处理
      // #endif
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
  },
})

// 注意:首次进入应用时由 App.vue onLaunch 主动调用 applyTheme(),
// 这里不在模块顶层 useThemeStore(),避免 pinia 未安装时崩溃。
