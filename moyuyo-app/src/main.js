/**
 * MOYUYO ATELIER - uni-app 入口
 */
import { createSSRApp } from 'vue'
import { pinia } from '@/store'
import App from './App.vue'
import { lucClass } from '@/utils/lucide'
import { i18n } from '@/i18n'

export function createApp() {
  const app = createSSRApp(App)
  app.use(pinia)
  // 全局图标映射:模板中可用 $luc(emojiOrName) 得到 'luc luc-xxx' 类名
  app.config.globalProperties.$luc = lucClass
  // 全局 i18n:模板中可用 $t('key', params),$i18n.locale 读取当前语言
  app.config.globalProperties.$t = (key, params) => i18n.t(key, params)
  app.config.globalProperties.$i18n = i18n
  return {
    app,
  }
}
