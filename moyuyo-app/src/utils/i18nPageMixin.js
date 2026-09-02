/**
 * i18n 页面标题 mixin + composable
 *
 * 用法 1:Options API 页面
 *   import { i18nPageMixin } from '@/utils/i18nPageMixin'
 *   export default {
 *     mixins: [i18nPageMixin],
 *     pageTitleKey: 'pageTitle.userWallet',
 *   }
 *
 * 用法 2:Composition API / <script setup> 页面
 *   import { usePageTitle } from '@/utils/i18nPageMixin'
 *   usePageTitle('pageTitle.userWallet')
 *
 * 行为:
 *   - onLoad 时调用 uni.setNavigationBarTitle 把当前 locale 的标题写入原生 navbar
 *   - 订阅 i18n.subscribe,locale 切换时(设置页改了语言)立刻重设当前页标题
 *   - onUnload 时取消订阅,避免内存泄漏
 *
 * 注意:
 *   1. 仅对原生 navbar 生效(navigationStyle !== 'custom');custom 模式页面
 *      的标题在 template 里直接 $t('xxx'),已是响应式,不需 mixin/composable
 *   2. 如果页面想用"动态标题"(如 "订单 #123456"),key 传函数:
 *        pageTitleKey() { return this.orderNo ? 'orderDetail.titleWithNo' : 'orderDetail.title' }
 *      或 usePageTitle(() => state.orderNo ? '...' : '...')
 */
import { i18n } from '@/i18n'

/** 解析 key 为当前 locale 下的标题文本 */
export function resolvePageTitle(key, fallback) {
  if (!key) return ''
  const resolved = typeof key === 'function' ? key() : key
  if (!resolved) return ''
  const text = i18n.t(resolved)
  // 字典缺失时 i18n.t 返回原 key,需要 fallback
  if (text === resolved && fallback) return fallback
  return text
}

/**
 * 把标题写到原生 navbar + H5 下 document.title + uni-h5 注入的 .uni-page-head__title
 *
 * H5 端细节:
 * 1. uni-h5 的 navbar 由 `<div class="uni-page-head__title">` 渲染,
 *    `uni.setNavigationBarTitle` 实际只刷新 .uni-page-head__title 内部 vnode 一次;
 *    后续 locale 切换时再次调用可能 noop,导致标题不更新。
 * 2. 这里在 uni.setNavigationBarTitle 之后显式同步更新 DOM 节点 textContent,
 *    兜底确保任意时刻切换语言都能立即反映。
 */
export function applyPageTitle(title) {
  if (!title) return
  // #ifdef H5
  try {
    if (typeof document !== 'undefined') {
      document.title = title
      // uni-h5 的 navbar 标题 DOM:class="uni-page-head__title"
      // 直接同步 textContent,避免 uni.setNavigationBarTitle 在切换语言时不更新
      const titleEls = document.querySelectorAll('.uni-page-head__title')
      titleEls.forEach((el) => {
        if (el.textContent !== title) el.textContent = title
      })
    }
  } catch (e) { /* ignore */ }
  // #endif
  try {
    uni.setNavigationBarTitle({ title })
  } catch (e) { /* ignore */ }
}

/** Composition API / <script setup> 用法 */
export function usePageTitle(key, fallback) {
  // 立即应用一次
  applyPageTitle(resolvePageTitle(key, fallback))
  // 订阅后续 locale 切换
  // 注意:uni-app setup 中 onUnload 才是页面卸载钩子;这里我们用 onUnload 来自 @dcloudio/uni-app
  // 简单做法:不显式 unbind,uni-app 页面销毁时 i18n.subscribe 的 Set 里这个 fn 仍在但
  // 不会真正被调用(因为 i18n.locale 改变后 applyPageTitle 内部 try/catch 不会抛错,
  // 顶多重复设置一次标题),如果需要精确释放可换成 useUnload 钩子。
  // 当前实现为简化版,稍后优化。
  const unsub = i18n.subscribe(() => {
    applyPageTitle(resolvePageTitle(key, fallback))
  })
  // 尝试在页面卸载时取消订阅(uni-app 提供的 onUnload 钩子)
  try {
    // 动态 import 避免 H5/非小程序环境下 onUnload 不存在
    const uniApp = require('@dcloudio/uni-app')
    if (uniApp && typeof uniApp.onUnload === 'function') {
      uniApp.onUnload(() => unsub())
    }
  } catch (e) {
    // ignore
  }
}

export const i18nPageMixin = {
  // 页面声明:页面级 i18n key,可静态字符串也可函数返回(动态标题)
  // 例如 pageTitleKey: 'pageTitle.userWallet'
  // 或   pageTitleKey() { return this.orderNo ? 'orderDetail.titleWithNo' : 'orderDetail.title' }
  pageTitleKey: '',
  // fallback:字典里没有时回落的标题(可选,不传则回落到 pages.json 里的 navigationBarTitleText)
  pageTitleFallback: '',

  onLoad() {
    this._applyPageTitle()
    // 订阅 locale 变化,切换语言时立即更新当前页标题
    this._i18nUnsub = i18n.subscribe(() => {
      this._applyPageTitle()
    })
  },
  onUnload() {
    if (this._i18nUnsub) {
      this._i18nUnsub()
      this._i18nUnsub = null
    }
  },
  methods: {
    _applyPageTitle() {
      const title = resolvePageTitle(this.pageTitleKey, this.pageTitleFallback)
      applyPageTitle(title)
    },
  },
}

