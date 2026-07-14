/**
 * 本地存储工具：统一管理用户 Token、用户信息、购物车、主题等本地数据
 * 避免散落的 uni.setStorageSync 难以维护
 */

const STORAGE_PREFIX = 'moyuyo_'

export const STORAGE_KEYS = {
  TOKEN: `${STORAGE_PREFIX}token`,
  USER_INFO: `${STORAGE_PREFIX}user_info`,
  CART: `${STORAGE_PREFIX}cart`,
  THEME: `${STORAGE_PREFIX}theme`,
  ONBOARDING_DONE: `${STORAGE_PREFIX}onboarding_done`,
  SEARCH_HISTORY: `${STORAGE_PREFIX}search_history`,
  ADDRESS_LIST: `${STORAGE_PREFIX}address_list`,
  DEVICE_LIST: `${STORAGE_PREFIX}device_list`
}

export function setStorage(key, value) {
  try {
    uni.setStorageSync(key, value)
    return true
  } catch (e) {
    console.error('[storage] set error', e)
    return false
  }
}

export function getStorage(key, defaultValue = null) {
  try {
    const value = uni.getStorageSync(key)
    return value === '' || value === undefined || value === null ? defaultValue : value
  } catch (e) {
    console.error('[storage] get error', e)
    return defaultValue
  }
}

export function removeStorage(key) {
  try {
    uni.removeStorageSync(key)
    return true
  } catch (e) {
    console.error('[storage] remove error', e)
    return false
  }
}

export function clearStorage() {
  try {
    uni.clearStorageSync()
    return true
  } catch (e) {
    console.error('[storage] clear error', e)
    return false
  }
}
