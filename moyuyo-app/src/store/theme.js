/**
 * 主题状态管理：light / dark / system
 */
import { defineStore } from 'pinia'
import { setStorage, getStorage, STORAGE_KEYS } from '@/utils/storage'

export const useThemeStore = defineStore('theme', {
  state: () => ({
    mode: getStorage(STORAGE_KEYS.THEME, 'system') // light | dark | system
  }),

  actions: {
    setMode(mode) {
      this.mode = mode
      setStorage(STORAGE_KEYS.THEME, mode)
      this.applyTheme()
    },

    applyTheme() {
      // #ifdef H5
      const root = document.documentElement
      if (this.mode === 'dark') {
        root.classList.add('theme-dark')
      } else if (this.mode === 'light') {
        root.classList.remove('theme-dark')
      } else {
        // system - 跟随系统
        const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches
        root.classList.toggle('theme-dark', isDark)
      }
      // #endif
      // #ifdef MP-WEIXIN / APP-PLUS
      // 小程序和 APP 通过 navigationBarTextStyle / page meta 实现
      // 这里简化处理
      // #endif
    }
  }
})
