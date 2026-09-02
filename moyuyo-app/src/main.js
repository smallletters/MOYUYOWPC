/**
 * MOYUYO ATELIER - uni-app 入口
 */
import { createSSRApp } from 'vue'
import { pinia } from '@/store'
import App from './App.vue'
import { lucClass } from '@/utils/lucide'
import { i18n } from '@/i18n'
import { i18nPageMixin } from '@/utils/i18nPageMixin'
import { syncUniLocale } from '@/i18n'

// uview-plus 3.x 的 install() 依赖 `import.meta.glob('./components/u-*/*.vue')` 拉组件对象,
// 在 vite-plugin-uni 处理 node_modules 时 glob 返回空数组 → 一个组件都没注册上。
// 绕开:不再用 app.use(uviewPlus),改为手动静态 import 项目里用到的组件再 Vue.component() 全局注册。
// 新增 u- 组件时,只需追加一行 import + 一行 app.component()。
import UIcon from 'uview-plus/components/u-icon/u-icon.vue'
import UPopup from 'uview-plus/components/u-popup/u-popup.vue'
import UCoupon from 'uview-plus/components/u-coupon/u-coupon.vue'
import UNavbar from 'uview-plus/components/u-navbar/u-navbar.vue'
import UButton from 'uview-plus/components/u-button/u-button.vue'
// uview-plus 源组件叫 u-textarea,目录里也只有 u-textarea/u-textarea.vue,
// 但项目里 post-create.vue 模板写的标签是 <u--textarea>。这里把 UTextarea 同时注册成两个名字,两边都能用。
import UTextarea from 'uview-plus/components/u-textarea/u-textarea.vue'
import UUpload from 'uview-plus/components/u-upload/u-upload.vue'
import UCellGroup from 'uview-plus/components/u-cell-group/u-cell-group.vue'
import UCell from 'uview-plus/components/u-cell/u-cell.vue'
import UView from 'uview-plus/components/u-view/u-view.vue'

export function createApp() {
  // H5 启动时立即把当前 locale 同步到 <html lang> 与 document.title,
  // 之后设置页切换 locale 时会自动同步(由 i18n.subscribe 注册)。
  // 非 H5 端 document 不存在,applyToDocument 内部有 typeof document 守卫,不会抛错。
  i18n.applyToDocument()
  // 同步 uni 内置 i18n:tabBar 的 %tabBar.xxx% 占位符、navigationBar 等都依赖它
  syncUniLocale()

  const app = createSSRApp(App)
  app.use(pinia)
  // 全局 mixin:每页声明 pageTitleKey 即可在 onLoad/onUnload 时自动同步原生 navbar 标题,
  // locale 切换时也会实时更新(设置页改了语言,所有打开的页面立即反映)
  app.mixin(i18nPageMixin)
  // 全局注册 uview-plus 组件(以及 uView 根组件,用于 toast/notify 等全局 API)
  app.component('u-icon', UIcon)
  app.component('u-popup', UPopup)
  app.component('u-coupon', UCoupon)
  app.component('u-navbar', UNavbar)
  app.component('u-button', UButton)
  // 同名注册:兼容 post-create.vue 用的 <u--textarea> 与 uview-plus 默认名 u-textarea
  app.component('u-textarea', UTextarea)
  app.component('u--textarea', UTextarea)
  app.component('u-upload', UUpload)
  app.component('u-cell-group', UCellGroup)
  app.component('u-cell', UCell)
  app.component('u-view', UView)
  // 全局图标映射:模板中可用 $luc(emojiOrName) 得到 'luc luc-xxx' 类名
  app.config.globalProperties.$luc = lucClass
  // 全局 i18n:模板中可用 $t('key', params),$i18n.locale 读取当前语言
  app.config.globalProperties.$t = (key, params) => i18n.t(key, params)
  app.config.globalProperties.$i18n = i18n
  return {
    app,
  }
}
