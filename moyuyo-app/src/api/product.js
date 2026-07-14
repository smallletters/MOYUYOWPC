/**
 * 商品 API（WooCommerce REST API v3 /products）
 * 公开数据用 WooCommerce Basic Auth，评价提交需要 OAuth 用户身份
 */
import { get, post } from '@/utils/request'

const BASE = '/wc/v3/products'

/**
 * 获取商品列表
 */
export function getProductList(params = {}) {
  return get(BASE, { per_page: 20, status: 'publish', ...params }, { useAuth: true, useOAuth: false })
}

/**
 * 获取商品详情
 */
export function getProductDetail(id) {
  return get(`${BASE}/${id}`, {}, { useAuth: true, useOAuth: false })
}

/**
 * 获取商品分类树
 */
export function getCategoryList(params = {}) {
  return get(`${BASE}/categories`, { per_page: 100, ...params }, { useAuth: true, useOAuth: false })
}

/**
 * 商品评价列表
 */
export function getProductReviews(productId, params = {}) {
  return get(`${BASE}/reviews`, { product: productId, per_page: 20, ...params }, { useAuth: true, useOAuth: false })
}

/**
 * 提交商品评价（需 OAuth 用户身份）
 */
export function submitProductReview(data) {
  return post(`${BASE}/reviews`, data, { useAuth: true, useOAuth: true })
}

/**
 * 搜索商品
 */
export function searchProducts(keyword, params = {}) {
  return getProductList({ search: keyword, ...params })
}

export default {
  getProductList,
  getProductDetail,
  getCategoryList,
  getProductReviews,
  submitProductReview,
  searchProducts
}
