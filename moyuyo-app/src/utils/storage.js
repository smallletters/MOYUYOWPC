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
  LOCALE: `${STORAGE_PREFIX}locale`,
  ONBOARDING_DONE: `${STORAGE_PREFIX}onboarding_done`,
  SEARCH_HISTORY: `${STORAGE_PREFIX}search_history`,
  ADDRESS_LIST: `${STORAGE_PREFIX}address_list`,
  DEVICE_LIST: `${STORAGE_PREFIX}device_list`,
  // 未支付订单本地缓存:下单成功但未完成支付时本地留存,便于待付款列表展示
  PENDING_ORDERS: `${STORAGE_PREFIX}pending_orders`,
  // 设置页通知开关持久化:与系统/原生推送通道分离,本地开关默认开
  NOTIFICATION_ENABLED: `${STORAGE_PREFIX}notification_enabled`,
  // 立即购买临时单品:商品详情页直达结算用,不写入购物车
  BUYNOW_ITEM: `${STORAGE_PREFIX}buynow_item`,
  // 社区帖子草稿列表:支持多份草稿(数组)
  COMMUNITY_POST_DRAFTS: `${STORAGE_PREFIX}community_post_drafts`,
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

/** 读取本地未支付订单列表 */
export function getPendingOrders() {
  const list = getStorage(STORAGE_KEYS.PENDING_ORDERS, [])
  return Array.isArray(list) ? list : []
}

/** 保存本地未支付订单(去重 by id) */
export function savePendingOrder(order) {
  const list = getPendingOrders()
  const idx = list.findIndex((o) => o.id === order.id)
  if (idx >= 0) list[idx] = order
  else list.unshift(order)
  setStorage(STORAGE_KEYS.PENDING_ORDERS, list)
}

/** 按订单 id 移除本地未支付订单 */
export function removePendingOrder(orderId) {
  const list = getPendingOrders().filter((o) => o.id !== orderId)
  setStorage(STORAGE_KEYS.PENDING_ORDERS, list)
}
