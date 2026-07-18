import { ref } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 可复用数据加载器
 * 提供统一的加载状态、错误处理和自动重试能力
 *
 * @param {Function} fetchFn - 数据加载函数，返回 Promise
 * @param {Object} options - 配置项
 * @param {string} options.errorMsg - 错误提示文字
 * @param {number} options.retryCount - 重试次数（默认2次）
 * @returns {{ data, loading, error, load, retry }}
 */
export function useDataLoader(fetchFn, options = {}) {
  const data = ref(null)
  const loading = ref(false)
  const error = ref(null)

  const { errorMsg = '数据加载失败', retryCount = 2 } = options

  async function load(...args) {
    loading.value = true
    error.value = null

    let lastError = null
    for (let attempt = 0; attempt <= retryCount; attempt++) {
      try {
        const result = await fetchFn(...args)
        data.value = result
        loading.value = false
        return result
      } catch (err) {
        lastError = err
        if (attempt < retryCount) {
          // 等待后重试（递增等待时间）
          await new Promise(r => setTimeout(r, 1000 * (attempt + 1)))
        }
      }
    }

    // 所有重试均失败
    error.value = lastError
    loading.value = false
    if (errorMsg) {
      ElMessage.error(errorMsg)
    }
    return null
  }

  function retry(...args) {
    return load(...args)
  }

  return { data, loading, error, load, retry }
}
