/**
 * 商品接口（直接走 Vite dev proxy → Spring Boot 8080）
 * 避免与 config.apiBase（WordPress）混用。
 * dev 模式代理前缀：/api/v1/*
 * 生产由 nginx 反代统一对外。
 */
import { getStorage, STORAGE_KEYS } from '@/utils/storage'

// 后端 API 绝对地址：vite.config.js 的 define 会把 process.env.VITE_ADMIN_API_BASE
// 在编译期静态替换成字符串字面量。uni-app APP 端 process 由引擎 polyfill，
// 直接读 process.env.VITE_ADMIN_API_BASE 即可拿到值。
// 注意：不能用 import.meta.env，vite-plugin-uni 在 APP 端 polyfill import.meta
// 时会插入 new URL/document，导致 APP 白屏。
const ABSOLUTE_BASE = process.env.VITE_ADMIN_API_BASE || ''

function buildUrl(path) {
  return ABSOLUTE_BASE ? `${ABSOLUTE_BASE}${path}` : path
}

/** 构造请求头：登录后自动带上 JWT，便于后端获取 userId（任务进度等场景） */
function buildAuthHeader() {
  const token = getStorage(STORAGE_KEYS.TOKEN)
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function uniGet(path, params = {}, options = {}) {
  return new Promise((resolve, reject) => {
    const query = Object.keys(params)
      .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
      .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
      .join('&')
    const url = query ? `${path}${path.includes('?') ? '&' : '?'}${query}` : path
    // 详情页加载/查询图片较多的商品需要更长超时,默认 30s
    const timeout = options.timeout || 30000
    uni.request({
      url: buildUrl(url),
      method: 'GET',
      timeout,
      header: buildAuthHeader(),
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === 0) {
          resolve(res.data.data)
        } else {
          reject(new Error(res.data?.message || `Request failed (${res.statusCode})`))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || 'Network error')),
    })
  })
}

/** 列表分页（返回 Page<实体>，含 records/total） */
export function getProductList(params = {}) {
  // 将 category 参数名映射为后端 categoryId，并归一排序字段
  const mapped = {
    page: params.page || 1,
    size: params.per_page || params.size || 20,
    sortBy: mapSortBy(params.orderby, params.sortBy),
    sortOrder: params.order || params.sortOrder || 'asc',
  }
  if (params.categoryId) mapped.categoryId = params.categoryId
  else if (params.category) {
    // 数字则直接传，否则按名称解析（由后端 controller 处理）
    mapped.categoryId = params.category
  }
  if (params.keyword) mapped.keyword = params.keyword
  if (params.brandIpId) mapped.brandIpId = params.brandIpId
  return uniGet('/api/v1/products', mapped)
}

function mapSortBy(orderby, sortBy) {
  // 兼容 WooCommerce 风格与新风格
  if (sortBy) return sortBy
  const m = {
    menu_order: 'createdAt',
    popularity: 'sales',
    price: 'price',
    date: 'createdAt',
    rating: 'sales',
    modified: 'updatedAt',
    id: 'id',
  }
  return m[orderby] || 'createdAt'
}

export function getProductDetail(id, options = {}) {
  // 商品详情接口包含图集/SKU/评价聚合,接口较重,给足 60s
  return uniGet(`/api/v1/products/${id}`, {}, { timeout: options.timeout || 60000 })
}

export function getCategoryList() {
  return uniGet('/api/v1/categories')
}

export function searchProducts(keyword, params = {}) {
  return uniGet('/api/v1/products', { keyword, ...params })
}

export function getCategoryChildren(parentId) {
  return uniGet(`/api/v1/categories/${parentId}/children`)
}

export default {
  getProductList,
  getProductDetail,
  getCategoryList,
  searchProducts,
  getCategoryChildren,
}
