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
 * 4. 缓存有效性用 HEAD 探针校验远程体积,模型更新后自动重新下载。
 *    （这一条原先只在注释里承诺、并未实现,导致真机永久复用旧模型,
 *      渲染出无贴图的白模 —— H5 却正常,极难排查。详见 isCacheValid 注释）
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
 * 检查本地缓存是否仍然有效。
 *
 * 【本轮修复的核心 —— 缓存永不失效导致的"白模"幽灵 Bug】
 * 旧实现只有一行：`return !!meta && !!meta.savedFilePath`。
 * 只要文件曾经下载过，就永远复用，**模型换了也不会重新拉**。
 * 症状：APP 真机上 home.glb 渲染成一堆纯灰/纯白方块（完全无贴图），
 * 而同一份代码在 H5 上完全正常 —— 因为 H5 走 Blob URL 每次都重新下载。
 * 排查时极具迷惑性：几何量测(indoor / bounds / spawn)全对，唯独"贴图没了"，
 * 让人以为是材质 / 光照 / 渲染器的问题，绕了很远的弯路。
 *
 * 现在改成两层校验，任何一层不满足就重新下载：
 * 1. 本地元信息里记下下载时的文件体积 remoteSize
 * 2. 进入页面时用 HEAD 探一次远程文件的 Content-Length
 *    体积对不上 → 远程模型已更新 → 删除本地旧文件并重新下载
 *
 * 为什么用体积而不是 ETag / Last-Modified：
 * - 静态资源服务器常把 ETag 关掉，或返回弱校验值（W/"..."）
 * - Last-Modified 精度只到秒，同一天内改两次就漏判
 * - Content-Length 对 .glb 这种"内容变了长度几乎必变"的资源足够可靠
 *
 * 探针失败（离线 / 服务器不支持 HEAD / 跨域被拦）时**保守放行**沿用缓存，
 * 避免弱网下每次进页面都重下 25MB。只有明确拿到"长度不一致"才判失效。
 *
 * 【老缓存必须判失效，否则本修复对存量用户无效】
 * 用户手机上已经存在的元信息是旧格式 —— 根本没有 remoteSize 字段。
 * 若此时"保守沿用"，装了新包的设备仍会继续加载那个白模，修复等于没做。
 * 因此只要探测成功（拿到了远程体积）而本地没记体积，就判失效重下一次；
 * 这相当于给老用户做了一次自动的模型升级，重下代价只发生一次。
 *
 * @param {object|null} meta 缓存元信息
 * @param {number|null} remoteSize 本次 HEAD 探针拿到的远程字节数
 * @returns {boolean}
 */
function isCacheValid(meta, remoteSize) {
  if (!meta || !meta.savedFilePath) return false
  // 本次探针没拿到远程体积：离线或 HEAD 不支持，无法判定，保守沿用缓存。
  // 放在最前面判断，保证弱网下不会反复重下 25MB。
  if (typeof remoteSize !== 'number') return true
  // 探针成功但本地是老格式元信息（没记体积）→ 判失效，借这次机会自动升级缓存
  if (typeof meta.remoteSize !== 'number') return false
  return meta.remoteSize === remoteSize
}

/**
 * 用 HEAD 探针取远程文件的字节数。失败返回 null（调用方据此保守放行缓存）。
 * 走 uni.request 而非 fetch，是为了在 APP WebView / 小程序里行为一致。
 *
 * @param {string} remoteUrl
 * @returns {Promise<number|null>}
 */
function probeRemoteSize(remoteUrl) {
  return new Promise((resolve) => {
    uni.request({
      url: remoteUrl,
      method: 'HEAD',
      timeout: 8000,
      success: (res) => {
        if (res.statusCode !== 200) {
          resolve(null)
          return
        }
        const header =
          (res.header && (res.header['Content-Length'] || res.header['content-length'])) || null
        const n = header ? Number(header) : NaN
        resolve(isFinite(n) && n > 0 ? n : null)
      },
      fail: () => resolve(null),
    })
  })
}

/** 删除 APP 沙盒里的旧缓存文件（失败不阻断，仅告警） */
function removeLocalFile(savedFilePath) {
  if (!savedFilePath) return
  try {
    // #ifdef APP-PLUS
    const fsm = uni.getFileSystemManager()
    fsm.unlinkSync(savedFilePath)
    // #endif
  } catch (e) {
    console.warn('[modelDownload] 旧缓存删除失败(可忽略):', e)
  }
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

  // 1. 先探一次远程体积，再决定缓存能不能用。
  //    顺序很关键：先探针、后判定，才能识别出"远程已更新但本地还是旧文件"。
  //    H5 端不需要探针（每次都是 Blob URL，没有本地缓存），
  //    这里仅对 APP / 小程序走这一步，避免 H5 平白多发一个请求。
  let remoteSize = null
  if (!isH5()) {
    remoteSize = await probeRemoteSize(remoteUrl)
  }

  // 2. 缓存命中且远程体积一致 → 直接复用
  if (isCacheValid(meta, remoteSize)) {
    if (typeof onProgress === 'function') onProgress(100)
    return meta.savedFilePath
  }

  // 3. 缓存已失效：先删掉旧文件，否则 saveFile 每次都会在 _doc/ 里
  //    堆积一个新副本（uni.saveFile 不覆盖同名文件），
  //    25MB × N 次很快把手机存储撑满，也导致老模型仍可能被命中。
  if (meta && meta.savedFilePath) {
    console.warn(
      `[modelDownload] ${name} 缓存失效(本地 ${meta.remoteSize ?? '未知'}B /远程 ${remoteSize ?? '未知'}B)，重新下载`,
    )
    removeLocalFile(meta.savedFilePath)
    writeMeta(name, null)
  }

  // 4. 缓存未命中,按平台下载
  if (isH5()) {
    return downloadToH5(remoteUrl, onProgress)
  }
  return downloadToLocalApp(remoteUrl, name, onProgress).then((savedPath) => {
    // 记下体积，下次进入才能比对。
    // 探针失败时 remoteSize 为 null：isCacheValid 会因 typeof remoteSize !== 'number'
    // 直接沿用缓存，因此不会出现"每次进页面都重下 25MB"的流量问题。
    writeMeta(name, { savedFilePath: savedPath, savedAt: Date.now(), remoteSize })
    return savedPath
  })
}

/**
 * 主动清理单个模型的本地缓存(模型下架/版本切换时调用)
 */
export function clearModelCache(name) {
  const meta = readMeta(name)
  removeLocalFile(meta?.savedFilePath)
  writeMeta(name, null)
}

export default {
  ensureLocalModel,
  clearModelCache,
}
