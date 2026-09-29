/**
 * 通用带退避的重试包装器。
 *  - 用于"非关键可恢复"的接口（如可发货国家下拉、字典项等），失败重试避免 picker 为空。
 *  - 退避策略：指数 backoff（200ms / 400ms / 800ms），最多 3 次。
 *  - 任何 non-network 错误（4xx 业务错误）立即抛出，不进入重试。
 *
 * @param {() => Promise<T>} task    要执行的异步函数
 * @param {object} [opts]
 * @param {number} [opts.retries=3]        最大重试次数（不含首次）
 * @param {number[]} [opts.delaysMs=[200,400,800]]  每次重试前等待 ms
 * @param {(err:any)=>boolean} [opts.shouldRetry]  自定义重试判断
 * @returns {Promise<T>}
 */
export async function withRetry(task, opts = {}) {
  const { retries = 3, delaysMs = [200, 400, 800], shouldRetry = isNetworkError } = opts
  let lastErr
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      return await task()
    } catch (e) {
      lastErr = e
      if (attempt === retries) break
      if (!shouldRetry(e)) throw e
      // 退避
      const wait = delaysMs[Math.min(attempt, delaysMs.length - 1)]
      await new Promise((resolve) => setTimeout(resolve, wait))
    }
  }
  throw lastErr
}

/**
 * 是否属于可重试的网络/超时错误。
 * request.js 在 fail 回调里给 err.isNetworkError = true，统一在这里识别。
 */
function isNetworkError(err) {
  if (!err) return false
  if (err.isNetworkError) return true
  // uni-app 部分版本 errMsg 是字符串
  const m = String(err.errMsg || '')
  return /timeout|request:fail|abort/i.test(m)
}
