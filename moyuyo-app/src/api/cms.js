/**
 * CMS Banner 公开接口（无需鉴权）
 * dev 模式由 Vite proxy 转发 /api/v1/cms/* → http://localhost:8080，
 * 生产由 nginx 反代统一对外，避免 CORS。
 * 也可通过 process.env.VITE_ADMIN_API_BASE 注入绝对地址（移动端打包走绝对 URL）。
 */
const ABSOLUTE_BASE = (typeof process !== 'undefined' && process.env && process.env.VITE_ADMIN_API_BASE)
  || (typeof window !== 'undefined' && window.__MOYUYO_CONFIG__ && window.__MOYUYO_CONFIG__.VITE_ADMIN_API_BASE)
  || ''

function buildUrl(path) {
  return ABSOLUTE_BASE ? `${ABSOLUTE_BASE}${path}` : path
}

export function getBannerList() {
  return new Promise((resolve, reject) => {
    uni.request({
      url: buildUrl('/api/v1/cms/banners'),
      method: 'GET',
      timeout: 10000,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === 0) {
          resolve(res.data.data || [])
        } else {
          reject(new Error(res.data?.message || `CMS banner fetch failed (${res.statusCode})`))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || 'Network error')),
    })
  })
}

/**
 * 商品列表（用于 APP 首页推荐区 3-tab）
 * tab 类型：
 *   - guess：猜你喜欢（推荐位，默认）
 *   - hot：今日爆款（按销量排序）
 *   - rating：口碑好评（按评分+评论数排序）
 */
export function getRecommendProducts(tab = 'guess', size = 10) {
  const sortMap = {
    guess: { sortBy: 'createdAt', sortOrder: 'desc' },
    hot: { sortBy: 'sales', sortOrder: 'desc' },
    rating: { sortBy: 'sales', sortOrder: 'desc' }, // 评分排序由后端根据 rating 字段计算
  }
  const params = { page: 1, size, ...(sortMap[tab] || sortMap.guess) }
  return new Promise((resolve, reject) => {
    uni.request({
      url: buildUrl('/api/v1/products'),
      method: 'GET',
      data: params,
      timeout: 10000,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === 0) {
          resolve(res.data.data?.records || [])
        } else {
          reject(new Error(res.data?.message || `Recommend products fetch failed (${res.statusCode})`))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || 'Network error')),
    })
  })
}

export default { getBannerList, getRecommendProducts }