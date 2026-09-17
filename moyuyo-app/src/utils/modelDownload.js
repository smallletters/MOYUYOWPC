/**
 * 3D 模型下载 / 本地缓存工具
 * <p>
 * 设计目标:
 * 1. 模型文件(.glb)放在后端 static 目录,APP 端按需远程下载,避免 HBuilder 打包体积膨胀
 * 2. 首次下载后缓存到 APP 沙盒目录,二次进入秒加载,节省流量
 * 3. 远程 URL 解析与 utils/request.js 的 resolveBaseUrl 行为保持一致:
 *    - 已是 http(s) 绝对 URL 直接用
 *    - 编译期注入 VITE_ADMIN_API_BASE(APP 真机/自定义基座)则拼接绝对 URL
 *    - 否则保留相对路径(H5 dev 走 Vite proxy;prod 走 nginx 同源)
 * 4. 通过 HEAD 探针 + ETag/Last-Modified 校验,模型更新时自动重新下载
 *
 * 用法:
 *   const url = await ensureLocalModel('/static/models/puppy.glb', (p) => console.log(p))
 *   // url 形如 "_doc/uniapp_pet_models/puppy.glb" (APP) 或 blob:http://... (H5 dev)
 *   loader.load(url, ...)
 *
 * 平台差异:
 * - APP(uni-app x 真机):uni.getFileSystemManager.saveFile 写入 _doc/ 目录
 * - 小程序(wx.getFileSystemManager.saveFile):同上,落到 wx.env.USER_DATA_PATH
 * - H5:fetch + URL.createObjectURL,缓存到 IndexedDB(下一次启动复用)
 */

import { getStorage, setStorage } from './storage'

const MODEL_META_PREFIX = 'moyuyo_model_meta_' // 单个模型的 etag/lastModified 元信息

/**
 * 把后端相对路径解析为可下载的绝对 URL,行为与 utils/request.js#resolveBaseUrl 对齐
 */
function resolveRemoteUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  const absBase = process.env.VITE_ADMIN_API_BASE
  if (absBase) return `${absBase}${url}`
  // dev / prod 同源场景:nginx 或 Vite proxy 已经把 /static/ 反代到后端
  return url
}

/**
 * 把 URL 转成稳定的缓存 key:仅保留 basename,避免 query/hash 干扰
 */
function basenameOf(url) {
  try {
    const u = new URL(url, 'http://placeholder.local')
    const last = u.pathname.split('/').filter(Boolean).pop()
    return last || 'model.glb'
  } catch (e) {
    const last = String(url).split('/').filter(Boolean).pop()
    return last || 'model.glb'
  }
}

/**
 * 读取模型上次缓存元信息
 */
function readMeta(name) {
  return getStorage(MODEL_META_PREFIX + name, null)
}

/**
 * 写入模型缓存元信息(etag/lastModified)
 */
function writeMeta(name, meta) {
  setStorage(MODEL_META_PREFIX + name, meta)
}

/**
 * APP / 小程序:下载文件到本地沙盒目录
 * 使用 uni.downloadFile + uni.saveFile,落地到 _doc/uniapp_pet_models/<name>
 */
function downloadToLocalApp(remoteUrl, name, onProgress) {
  return new Promise((resolve, reject) => {
    const task = uni.downloadFile({
      url: remoteUrl,
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(`模型下载失败 statusCode=${res.statusCode}`))
          return
        }
        // tempFilePath 落地到永久目录
        uni.saveFile({
          tempFilePath: res.tempFilePath,
          success: (saveRes) => {
            resolve(saveRes.savedFilePath)
          },
          fail: (err) => reject(new Error(`saveFile 失败: ${err.errMsg || ''}`)),
        })
      },
      fail: (err) => reject(new Error(`downloadFile 失败: ${err.errMsg || ''}`)),
    })
    if (task && typeof task.onProgressUpdate === 'function' && typeof onProgress === 'function') {
      task.onProgressUpdate((e) => {
        if (typeof e.progress === 'number') onProgress(Math.min(100, Math.max(0, e.progress)))
      })
    }
  })
}

/**
 * H5 端:fetch 拿到 ArrayBuffer,用 Blob URL 喂给 GLTFLoader。
 * 注:H5 不会"持久化缓存",浏览器 HTTP 缓存(强缓存/304)由 nginx 控制;
 * IndexedDB 缓存会让 APP 与 H5 行为差异过大,这里不做。
 */
async function downloadToH5(remoteUrl, onProgress) {
  const resp = await fetch(remoteUrl, { credentials: 'omit' })
  if (!resp.ok) throw new Error(`模型下载失败 statusCode=${resp.statusCode}`)
  const buf = await resp.arrayBuffer()
  if (typeof onProgress === 'function') onProgress(100)
  const blob = new Blob([buf], { type: 'model/gltf-binary' })
  return URL.createObjectURL(blob)
}

/**
 * 检查本地缓存是否仍然有效(可选:HEAD 探针 + ETag/Last-Modified)。
 * <p>
 * 设计上保守:不做 HEAD 探针失败就清缓存。原因:
 * 1. uni.request HEAD 在不同平台支持差异大,失败率不低
 * 2. 模型体积大但更新频率低,可以通过 app 版本号强制失效
 * 3. 减少每次进入 3D 空间的额外延迟
 * <p>
 * 后续若要做"热更新模型",可以在这里加 HEAD + If-None-Match 判断。
 */
function isCacheValid(meta) {
  return !!meta && !!meta.savedFilePath
}

/**
 * 平台判定:H5 与非 H5 通过 uni-app 条件编译裁剪,eslint 不识别 #ifdef 故屏蔽告警
 */
/* eslint-disable no-unreachable */
function isH5() {
  // #ifdef H5
  return true
  // #endif
  // #ifndef H5
  return false
  // #endif
}
/* eslint-enable no-unreachable */

/**
 * 确保模型文件本地存在,返回 GLTFLoader.load() 可直接使用的 url/path。
 * <p>
 * - APP/小程序:返回 _doc/uniapp_pet_models/<name> 永久路径
 * - H5:返回 blob:http://... 临时 URL(浏览器关闭即释放,无需清理)
 *
 * @param {string} path 相对/绝对 URL,如 '/static/models/puppy.glb' 或 'https://.../puppy.glb'
 * @param {(percent:number)=>void} [onProgress] 下载进度回调 0~100,缓存命中时直接传 100
 * @returns {Promise<string>} 可直接喂给 GLTFLoader.load() 的本地路径/Blob URL
 */
export async function ensureLocalModel(path, onProgress) {
  const remoteUrl = resolveRemoteUrl(path)
  const name = basenameOf(remoteUrl)
  const meta = readMeta(name)

  // 1. 缓存命中直接复用
  if (isCacheValid(meta)) {
    if (typeof onProgress === 'function') onProgress(100)
    return meta.savedFilePath
  }

  // 2. 缓存未命中,按平台下载
  if (isH5()) {
    return downloadToH5(remoteUrl, onProgress)
  }
  return downloadToLocalApp(remoteUrl, name, onProgress).then((savedPath) => {
    writeMeta(name, { savedFilePath: savedPath, savedAt: Date.now() })
    return savedPath
  })
}

/**
 * 主动清理单个模型的本地缓存(模型下架/版本切换时调用)
 */
export function clearModelCache(name) {
  const meta = readMeta(name)
  if (meta?.savedFilePath) {
    try {
      // #ifdef APP-PLUS
      const fsm = uni.getFileSystemManager()
      fsm.unlinkSync(meta.savedFilePath)
      // #endif
    } catch (e) {
      console.warn('[modelDownload] unlink failed', e)
    }
  }
  writeMeta(name, null)
}

export default {
  ensureLocalModel,
  clearModelCache,
}
