/**
 * MOYUYO ATELIER - uni-app 入口
 */
import { createSSRApp } from 'vue'
import { pinia } from '@/store'
import App from './App.vue'
import { lucClass } from '@/utils/lucide'

export function createApp() {
  const app = createSSRApp(App)
  app.use(pinia)
  // 全局图标映射：模板中可用 $luc(emojiOrName) 得到 'luc luc-xxx' 类名
  app.config.globalProperties.$luc = lucClass
  return {
    app,
  }
}
