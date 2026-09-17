import { getStorage, STORAGE_KEYS } from '@/utils/storage'

/**
 * 用户端文件上传 API（社区发帖/客服/反馈 等场景）。
 * 后端: UserUploadController(/api/v1/file/upload)
 *  - POST /image  单张
 *  - POST /images 批量(≤9)
 *
 * 返回 URL 为相对路径 /uploads/yyyy/MM/dd/uuid.ext,
 * 浏览器通过 vite proxy (dev) / nginx 同源 (prod) 加载。
 */

/**
 * 把上传接口的相对路径解析为实际请求 URL。
 * <p>
 * 关键修复:APP 端 uni.uploadFile 不会像 uni.request 那样被前端代码做 baseURL 拼接,
 * 相对路径在 APP 端会被拼到当前页面 URL(file:///android_asset/... 等本地包路径)上,
 * 最终 URL 变成 file:///api/v1/file/upload/image —— 非 HTTP 协议,网络层直接失败,
 * 表现为 uploadFile:fail statusCode:null(根本没收到响应)。
 * <p>
 * 此处与 utils/request.js 的 resolveBaseUrl 行为保持一致:
 * 1) 已是绝对路径(http/https)直接用
 * 2) 编译期注入了 VITE_ADMIN_API_BASE(APP 真机/自定义基座)则拼接绝对 URL
 * 3) 否则保留相对路径(H5 dev 走 Vite proxy;prod 走 nginx 同源)
 */
function resolveUploadUrl(url) {
  if (url.startsWith('http')) return url
  const absBase = process.env.VITE_ADMIN_API_BASE
  if (absBase) return `${absBase}${url}`
  return url
}

/**
 * 上传单张图片
 * @param {string} filePath uni.chooseImage 返回的本地路径(tempFilePaths[0])
 * @returns {Promise<{url: string, filename: string, size: number, ...}>}
 */
export function uploadImage(filePath) {
  return new Promise((resolve, reject) => {
    const token = getStorage(STORAGE_KEYS.TOKEN)
    const header = token ? { Authorization: `Bearer ${token}` } : {}
    // 注意: 不能再设置 Content-Type,让浏览器自动生成 multipart boundary
    uni.uploadFile({
      url: resolveUploadUrl('/api/v1/file/upload/image'),
      filePath,
      name: 'file',
      header,
      success: (res) => {
        try {
          const body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          if (res.statusCode >= 200 && res.statusCode < 300 && body?.code === 0) {
            resolve(body.data)
          } else {
            reject(new Error(body?.message || `上传失败(${res.statusCode})`))
          }
        } catch (e) {
          reject(new Error('上传响应解析失败'))
        }
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '网络错误'))
      },
    })
  })
}

/**
 * 批量上传图片(顺序上传,逐张返回 URL)
 * @param {string[]} filePaths
 * @returns {Promise<string[]>} URL 数组(失败的会被跳过并 toast)
 */
export async function uploadImages(filePaths) {
  if (!filePaths || !filePaths.length) return []
  const urls = []
  for (const p of filePaths) {
    try {
      const result = await uploadImage(p)
      if (result?.url) urls.push(result.url)
    } catch (e) {
      // 单张失败不影响其他张
      console.warn('[upload] skip failed:', e.message)
    }
  }
  return urls
}

/**
 * 上传单个视频(社区发帖)。
 * 后端: POST /api/v1/file/upload/video
 * 大小上限受 application.yml spring.servlet.multipart.max-file-size 控制(当前 200MB)
 *
 * @param {string} filePath uni.chooseVideo 返回的本地路径(tempFilePath)
 * @param {(percent: number) => void} [onProgress] 上传进度回调(0~100)
 * @returns {Promise<{url: string, filename: string, size: number, ...}>}
 */
export function uploadVideo(filePath, onProgress) {
  return new Promise((resolve, reject) => {
    const token = getStorage(STORAGE_KEYS.TOKEN)
    const header = token ? { Authorization: `Bearer ${token}` } : {}
    const task = uni.uploadFile({
      url: resolveUploadUrl('/api/v1/file/upload/video'),
      filePath,
      name: 'file',
      header,
      success: (res) => {
        try {
          const body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          if (res.statusCode >= 200 && res.statusCode < 300 && body?.code === 0) {
            resolve(body.data)
          } else {
            reject(new Error(body?.message || `视频上传失败(${res.statusCode})`))
          }
        } catch (e) {
          reject(new Error('视频上传响应解析失败'))
        }
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '网络错误'))
      },
    })
    // 监听上传进度(uni.uploadFile 在 H5 上不暴露 onProgressUpdate,但部分平台支持)
    if (task && typeof task.onProgressUpdate === 'function' && typeof onProgress === 'function') {
      task.onProgressUpdate((e) => {
        if (typeof e.progress === 'number') onProgress(Math.min(100, Math.max(0, e.progress)))
      })
    }
  })
}

export default {
  uploadImage,
  uploadImages,
  uploadVideo,
}
