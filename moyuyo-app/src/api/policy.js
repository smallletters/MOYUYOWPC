/**
 * C 端协议公示接口（无需登录）
 * 隐私政策正文统一由后台 GDPR 政策库维护，APP 打开协议页时实时读取；
 * 接口不可用或政策库为空时，调用方回退到 i18n 内置文案。
 * dev 模式由 Vite proxy 转发 /api/v1/policies/* → 后端 8080，
 * 也可通过 VITE_ADMIN_API_BASE 注入绝对地址（移动端打包走绝对 URL）。
 */
const ABSOLUTE_BASE = process.env.VITE_ADMIN_API_BASE || ''

function buildUrl(path) {
  return ABSOLUTE_BASE ? `${ABSOLUTE_BASE}${path}` : path
}

/**
 * 获取当前生效的隐私政策
 * @param {string} [lang] 语言（zh-CN / en-US），后台按语言返回对应正文
 * @returns {Promise<Object>} { id, version, title, content, effectiveDate, lang }，无政策时为空对象
 */
export function getActivePrivacyPolicy(lang) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: buildUrl('/api/v1/policies/active'),
      method: 'GET',
      data: lang ? { lang } : undefined,
      timeout: 10000,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === 0) {
          resolve(res.data.data || {})
        } else {
          reject(new Error(res.data?.message || `Privacy policy fetch failed (${res.statusCode})`))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || 'Network error')),
    })
  })
}

export default { getActivePrivacyPolicy }
