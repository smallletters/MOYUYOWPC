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
      url: '/api/v1/file/upload/image',
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

export default {
  uploadImage,
  uploadImages,
}
