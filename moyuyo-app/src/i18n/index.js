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
    this._locale = getStorage(STORAGE_KEYS.LOCALE, DEFAULT_LOCALE)
    if (!MESSAGES[this._locale]) this._locale = DEFAULT_LOCALE
    // 订阅列表:locale 变化时通知页面 re-render
    this._subscribers = new Set()
  }

  get locale() {
    return this._locale
  }

  set locale(v) {
    if (!MESSAGES[v]) return
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
    const messages = MESSAGES[this._locale] || MESSAGES[DEFAULT_LOCALE]
    let value = getByPath(messages, key)
    // 兜底:若当前语言缺失,尝试英文
    if (value === key && this._locale !== DEFAULT_LOCALE) {
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
   * 获取当前 locale 的"自身显示名称"
   * 例:locale='zh-CN' 时返回 '简体中文'
   *    locale='en-US' 时返回 'English'
   * 用于设置页面"语言"项右侧的"当前语言"展示
   */
  get currentLanguageName() {
    return MESSAGES[this._locale]?.languages?.[this._locale] || this._locale
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
