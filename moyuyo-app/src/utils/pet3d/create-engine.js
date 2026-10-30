/**
 * 把 space3d-engine.js 里的「页面选项」装配成一个可以跑的引擎实例。
 *
 * 原来的 space-3d.vue 是标准 Vue 组件（data / computed / methods / 生命周期），
 * three.js 只是寄生在里面的业务代码。搬到 renderjs 后视图层不适合再跑一套
 * Vue 响应式（three 对象被 Proxy 包住会触发 "read-only" 报错，这也是原代码
 * 用 WeakMap 隔离的原因），所以这里把它降级成普通对象：
 *
 *   - data()      → 实例自有属性
 *   - computed    → Object.defineProperty getter（three 桶仍是 WeakMap 缓存）
 *   - methods     → 直接挂到实例上，调用时 this 就是实例
 *   - 生命周期     → onLoad / onReady / onShow / onHide / onUnload 由宿主手动调用
 *   - 响应式      → 用 32ms 轮询做「增量 diff」，只把变化的 UI 字段推给逻辑层
 *
 * 业务代码一行没改，只换了宿主。
 */
import { pageOptions, setModelResolver, setHostBridge } from './space3d-engine'

/**
 * 需要镜像到逻辑层（也就是要显示在 HUD 上）的字段。
 * 只有这些字段变化才会跨层通信，避免每帧都发消息把桥打爆。
 */
const SYNC_KEYS = [
  'loading',
  'loadingText',
  'progress',
  'errorMsg',
  'debugInfo',
  'showDiag',
  'viewMode',
  'positionText',
  'pointerLocked',
  'wireframe',
  'figureFallback',
  'joystickDragging',
  'joystickOffset',
  'diagPlatform',
  'diagModelSource',
  'diagAnchor',
  'diagFloorHeight',
  'diagScale',
  'diagMeshCount',
  'diagIndoor',
  'diagBounds',
  'diagWalls',
  'diagColliders',
  'diagSpawn',
  'diagCamera',
]

// 32ms ≈ 30fps：摇杆手柄位移要靠它跟手，再慢就看得出拖影；
// 因为只推变化字段，静止时一次都不发。
const SYNC_INTERVAL = 32

const LIFECYCLE = ['onLoad', 'onReady', 'onShow', 'onHide', 'onUnload']

/** 对象型字段（joystickOffset）用序列化比较，其余用 Object.is */
const digest = (v) => (v && typeof v === 'object' ? JSON.stringify(v) : v)

/**
 * @param {object} host
 * @param {string} host.canvasId      模板里 canvas 节点的 id（跨层必须一致）
 * @param {(key:string, params?:object)=>string} host.t  文案
 * @param {(patch:object)=>void} host.onState            状态回流
 * @param {(url:string)=>Promise<{primary:string,fallback:string|null}>} host.modelResolver
 * @param {(action:string, payload:any)=>void} host.onHostAction
 * @returns {object} engine
 */
export function createEngine(host) {
  if (host.modelResolver) setModelResolver(host.modelResolver)
  if (host.onHostAction) setHostBridge(host.onHostAction)

  const engine = {}

  // 1. computed → getter（three 桶内部有 WeakMap 缓存，多次访问不重建）
  const computed = pageOptions.computed || {}
  Object.keys(computed).forEach((key) => {
    Object.defineProperty(engine, key, {
      configurable: true,
      enumerable: true,
      get: () => computed[key].call(engine),
    })
  })

  // 2. methods（含 resolveModelSource 等后加的方法）
  Object.assign(engine, pageOptions.methods || {})

  // 3. 生命周期钩子，由宿主按顺序调用
  LIFECYCLE.forEach((key) => {
    const fn = pageOptions[key]
    if (typeof fn === 'function') engine[key] = fn
  })

  // 4. 宿主能力注入（必须在 data() 之前，data 里会用到 $t）
  engine.$t = host.t || ((key) => key)
  // 视图层没有 Vue 的 nextTick，用宏任务近似（只用于 onReady 里延后一帧）
  engine.$nextTick = (fn) => setTimeout(fn, 0)

  // 5. data
  Object.assign(engine, pageOptions.data.call(engine))
  if (host.canvasId) engine.canvasId = host.canvasId

  // 6. 增量状态同步
  const lastSnapshot = {}
  const flush = () => {
    const patch = {}
    let changed = false
    for (let i = 0; i < SYNC_KEYS.length; i++) {
      const key = SYNC_KEYS[i]
      const value = engine[key]
      const d = digest(value)
      if (Object.is(lastSnapshot[key], d)) continue
      lastSnapshot[key] = d
      // 跨层传值必须能结构化克隆，这里直接归一成纯数据
      patch[key] = value === undefined ? null : JSON.parse(JSON.stringify(value))
      changed = true
    }
    if (!changed || typeof host.onState !== 'function') return
    try {
      host.onState(patch)
    } catch (e) {
      console.warn('[pet3d] 状态回流失败', e)
    }
  }

  const timer = setInterval(flush, SYNC_INTERVAL)

  /** 宿主销毁时必须调用，否则定时器与 WebGL 上下文都会泄漏 */
  engine.$destroy = () => {
    clearInterval(timer)
    if (typeof engine.onUnload === 'function') {
      try {
        engine.onUnload()
      } catch (e) {
        console.warn('[pet3d] 卸载异常', e)
      }
    }
  }

  return engine
}

export default { createEngine }
