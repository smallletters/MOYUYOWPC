import { get, post, put, del } from '@/utils/request'

export function getCart() {
  return get('/api/v1/cart')
}

export function addItem(skuId, quantity = 1) {
  return post('/api/v1/cart', { skuId, quantity })
}

export function updateQuantity(skuId, quantity) {
  return put(`/api/v1/cart/${skuId}`, { quantity })
}

export function removeItem(skuId) {
  return del(`/api/v1/cart/${skuId}`)
}

export function toggleCheck(skuId, selected) {
  return put(`/api/v1/cart/check/${skuId}`, { selected })
}

export function toggleCheckAll(selected) {
  return put('/api/v1/cart/check-all', { selected })
}

export function clearCart() {
  return del('/api/v1/cart')
}

export function checkout(addressId, remark, couponId) {
  return post('/api/v1/cart/checkout', { addressId, remark, couponId })
}

export function getFavorites() {
  return get('/api/v1/favorites')
}

export function addFavorite(productId, skuId, groupId) {
  return post('/api/v1/favorites', { productId, skuId, groupId })
}

export function removeFavorite(productId, skuId) {
  // 走 query 传参,避免 DELETE + body 在部分网关下被丢弃导致服务端拿不到参数
  const params = {}
  if (productId !== undefined && productId !== null) params.productId = productId
  if (skuId !== undefined && skuId !== null) params.skuId = skuId
  const qs = Object.keys(params)
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  return del(`/api/v1/favorites${qs ? `?${qs}` : ''}`)
}

export default {
  getCart,
  addItem,
  updateQuantity,
  removeItem,
  toggleCheck,
  toggleCheckAll,
  clearCart,
  checkout,
  getFavorites,
  addFavorite,
  removeFavorite,
}
