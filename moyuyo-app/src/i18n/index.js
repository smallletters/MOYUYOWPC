/**
 * 轻量级 i18n 模块(自实现,不依赖 vue-i18n 运行时)
 *
 * 设计目标:
 * 1. 模板中可用 $t('wallet.title') / $t('withdraw.hint', { minAmount: 10 })
 * 2. 脚本中可用 i18n.t(key, params) / i18n.locale
 * 3. 持久化语言选择(存 STORAGE_KEYS.LOCALE)
 * 4. 支持运行时切换语言(响应式:Pinia store 触发 re-render)
 *
 * 用法:
 *   import { i18n } from '@/i18n'
 *   i18n.t('wallet.title')              // '我的钱包'
 *   i18n.t('withdraw.hint', { minAmount: 10 }) // '最低 ¥10 · ...'
 *   i18n.locale = 'en-US'
 */
import { getStorage, setStorage, STORAGE_KEYS } from '@/utils/storage'
import { ref } from 'vue'
import zhCN from './zh-CN'
import enUS from './en-US'

const MESSAGES = {
  'zh-CN': zhCN,
  'en-US': enUS,
}

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US']
export const DEFAULT_LOCALE = 'en-US' // 目标用户改成美国后默认英文

/**
 * 通过点分路径取值,如 'wallet.features.points'
 */
function getByPath(obj, path) {
  if (!obj || !path) return path
  const keys = String(path).split('.')
  let cur = obj
  for (const k of keys) {
    if (cur && typeof cur === 'object' && k in cur) {
      cur = cur[k]
    } else {
      // 路径不存在时返回原始 key,方便排查缺失文案
      return path
    }
  }
  return cur
}

/**
 * 模板插值:把 {name} 替换成 params.name
 */
function interpolate(template, params) {
  if (!params || typeof template !== 'string') return template
  return template.replace(/\{(\w+)\}/g, (_, k) => {
    const v = params[k]
    return v === undefined || v === null ? `{${k}}` : String(v)
  })
}

class I18n {
  constructor() {
    // 启动时从 storage 读,默认英文(目标用户美国)
    const initial = getStorage(STORAGE_KEYS.LOCALE, DEFAULT_LOCALE)
    // 用 ref 包裹 locale:模板里访问 i18n.locale / $t() 会自动建立响应式依赖
    // 切换 locale 时所有渲染 {{ $t('xxx') }} 的组件会立刻重渲,无需刷页
    this._localeRef = ref(MESSAGES[initial] ? initial : DEFAULT_LOCALE)
    // 兼容旧代码里的 _locale 字段
    this._locale = this._localeRef.value
    // 订阅列表:locale 变化时通知非 Vue 上下文(已不必要,保留以防旧代码依赖)
    this._subscribers = new Set()
  }

  get locale() {
    return this._localeRef.value
  }

  set locale(v) {
    if (!MESSAGES[v]) return
    this._localeRef.value = v
    this._locale = v
    setStorage(STORAGE_KEYS.LOCALE, v)
    // 通知订阅者(用于非 props 场景;Vue 模板一般自动响应)
    this._subscribers.forEach((fn) => fn(v))
  }

  /**
   * 翻译:支持 fallback(英文兜底)
   *   t('wallet.title')              -> 'My Wallet'
   *   t('withdraw.hint', { minAmount: 10 }) -> 'Minimum $10 · ...'
   */
  t(key, params) {
    const locale = this._localeRef.value
    const messages = MESSAGES[locale] || MESSAGES[DEFAULT_LOCALE]
    let value = getByPath(messages, key)
    // 兜底:若当前语言缺失,尝试英文
    if (value === key && locale !== DEFAULT_LOCALE) {
      value = getByPath(MESSAGES[DEFAULT_LOCALE], key)
    }
    return interpolate(value, params)
  }

  /**
   * 当前语言的货币符号
   */
  get currencySymbol() {
    return this.t('currency.symbol')
  }

  /**
   * 订阅 locale 变化
   */
  subscribe(fn) {
    this._subscribers.add(fn)
    return () => this._subscribers.delete(fn)
  }

  /**
   * 把当前 locale 同步到浏览器 DOM(H5 专用):
   *   - <html lang="zh-CN|en-US">  (影响浏览器拼写检查、屏幕阅读器、CSS :lang 选择器)
   *   - document.title              (切换语言后浏览器标签页标题跟随)
   * 在小程序/APP 端 document 不存在,需调用方自行用条件编译规避;
   * 这里做了 typeof window 守卫,内部 try/catch,保证任意平台调用都安全。
   *
   * 用法:在 main.js 里 createSSRApp 之前调用一次,之后每次切换 locale 自动同步。
   *   i18n.applyToDocument()
   */
  applyToDocument() {
    const apply = () => {
      try {
        if (typeof document === 'undefined') return
        const lang = this.locale
        document.documentElement.setAttribute('lang', lang)
        // 标题走 app.title namespace;若字典缺失则回落到静态 MOYUYO ATELIER
        const title = this.t('app.title') || 'MOYUYO ATELIER'
        if (document.title !== title) document.title = title
      } catch (e) {
        /* DOM 同步失败不影响业务,忽略 */
      }
    }
    // 立即执行一次(初始 locale)
    apply()
    // 订阅后续切换
    this.subscribe(apply)
  }

  /**
   * 获取当前 locale 的"自身显示名称"
   * 例:locale='zh-CN' 时返回 '简体中文'
   *    locale='en-US' 时返回 'English'
   * 用于设置页面"语言"项右侧的"当前语言"展示
   */
  get currentLanguageName() {
    const locale = this._localeRef.value
    return MESSAGES[locale]?.languages?.[locale] || locale
  }
}

/**
 * 导出所有支持的语言(供设置页面下拉/弹窗渲染)
 * 格式:[
 *   { code: 'zh-CN', name: '简体中文' },
 *   { code: 'en-US', name: 'English' },
 * ]
 */
export function getSupportedLanguages() {
  // 合并所有 locale 的 languages 字典,得到"code -> {zh-CN:简体中文, en-US:English, ...}"
  // 每个 code 用英文 (en-US) 作为默认显示名
  const merged = {}
  Object.keys(MESSAGES).forEach((locale) => {
    const langs = MESSAGES[locale]?.languages
    if (langs && typeof langs === 'object') {
      Object.keys(langs).forEach((code) => {
        if (!merged[code]) merged[code] = langs[code]
      })
    }
  })
  return SUPPORTED_LOCALES.map((code) => ({ code, name: merged[code] || code }))
}

export const i18n = new I18n()

/**
   * 便捷函数:在 Vue 选项式 API / setup 中调用
   */
  export function t(key, params) {
    return i18n.t(key, params)
  }

  /**
   * 后端分类名本地化:输入后端 name,返回当前 locale 下的展示文本。
   * 字典查不到时回落到后端原值(运营新增分类不会因为前端没翻译而消失)。
   *
   * @param {string} name - 后端返回的分类名(精确匹配)
   * @param {boolean} [isSub] - 是否二级分类;默认 false(一级)
   * @returns {string}
   */
  export function tCategoryName(name, isSub) {
    if (!name) return ''
    const key = isSub ? 'category.subNames' : 'category.names'
    const map = i18n.t(key) || {}
    // i18n.t 返回的是字符串(如果 key 找不到)或对象(找到了);
    // 我们这里访问的就是字典里的对象,如果取到的是字符串说明字典里没这个对象(不太可能)
    if (typeof map === 'object' && map !== null) {
      return Object.prototype.hasOwnProperty.call(map, name) ? map[name] : name
    }
    return name
  }

  /**
   * 把当前 i18n.locale 同步到 uni-app 内置 i18n 系统。
   *
   * 背景:uni-h5 内置了 tabBar / navigationBar 的 i18n 机制——读取
   * pages.json 里 %key% 形式的占位符,然后从 __uniConfig.locales 字典
   * 里取当前 locale 对应的文本。这个机制依赖 uni.getLocale() 返回的值。
   *
   * 我们自己实现的 i18n 模块(i18n.locale)与 uni 内置 locale 是两份独立状态,
   * 必须在每次切换时同步,否则切换语言后:
   *   - 自己页面模板的 $t() 会更新(响应式)
   *   - 但 tabBar 底栏、navigationBar 标题仍然是 pages.json 初始 locale 的文字
   *
   * 用法:在 createApp() 启动时调一次 + 订阅 i18n.locale 变化时再调
   */
  export function syncUniLocale() {
    const apply = () => {
      if (typeof uni === 'undefined' || typeof uni.setLocale !== 'function') return
      try {
        uni.setLocale(i18n.locale)
      } catch (e) {
        // uni-h5 内部 setLocale 失败通常是因为 __uniConfig.locales 没配
        // 此场景下 tabBar 不会自动翻译,只能靠 setTabBarItem 兜底(已废弃)
      }
    }
    apply()
    i18n.subscribe(apply)
  }
