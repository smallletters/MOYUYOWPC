<template>
  <view class="space-3d">
    <!--
      3D 画布（全屏，无顶部返回栏）
      全屏化改造说明：
      - 页面已切到 landscape（pages.json 里 pageOrientation），竖屏进入会被系统提示旋转
      - 原 header（返回栏 + 标题 + 功能按钮）整体移除，功能按钮并入 HUD，
        在横屏下做成半透明浮层，避免遮挡画面且不占用布局高度
      - canvas-wrap 直接占满 100% width/height，HUD 用 absolute 覆盖在其上
    -->
    <!--
      仅保留 touchstart:
      - PointerLockControls 必须在 canvas 元素的用户手势上 lock()(全局 capture 不行)
      - move/end 由全局 capture 监听处理,避免双路径重复更新 yaw/pitch
    -->
    <view class="canvas-wrap" data-pet3d-canvas-wrap>
      <!-- 摇杆/画布的 touch 监听由 pet3d 模块在视图层直接绑定，
           因为 APP 端模板事件跑在逻辑层，拿不到 view 层的手势序列 -->
      <view :id="canvasId" ref="canvasEl" class="canvas">
        <!--
          加载/错误进度条（复用 loading 区块的样式体系）
          原本这里还有一个常驻 .debug-info 文本层，加载完成后仍会显示模型尺寸，
          在横屏游戏视图里长期占掉顶部一整条，纯视觉噪音 —— 改为仅在
          loading 或 errorMsg 时展示，成功进入游戏后自动消失。
          运行时数值统一交给右上角 🔧 诊断面板（面板里信息更全且可随时开关）。
        -->
        <view v-if="debugInfo && (loading || errorMsg)" class="debug-info">
          <text>{{ debugInfo }}</text>
        </view>
      </view>

      <!-- 加载状态 -->
      <view v-if="loading" class="loading">
        <text class="loading-text">{{ loadingText }}</text>
        <view v-if="progress > 0 && progress < 100" class="progress-bar">
          <view class="progress-fill" :style="{ width: progress + '%' }" />
        </view>
      </view>

      <!-- 错误提示 -->
      <view v-if="errorMsg" class="error-mask">
        <text class="error-text">{{ errorMsg }}</text>
        <view class="retry-btn" @click="onPet3dRetry">
          <text>{{ $t('common.retry') }}</text>
        </view>
      </view>

      <!-- 游戏 HUD -->
      <view v-if="!loading && !errorMsg" class="hud">
        <!--
          左上角：仅保留返回键。
          原先这里的 4 个功能按钮（视角切换 / 复位 / 线框 / 诊断）与顶部信息胶囊
          已按需求全部移除，顶部整条清空，只留退出入口 —— 更接近游戏的全屏沉浸感。
          · 视角切换：默认第三人称就是主玩法，第一人称非必需
          · 线框 / 诊断：纯调试功能，已挪到诊断面板按需排查
          返回键是唯一的显式退出入口，不能删（否则只能靠系统返回手势）。
        -->
        <view class="hud-actions hud-actions-left">
          <view class="hud-btn hud-btn-back" @click="goBack">
            <text class="luc luc-arrow-left" />
          </view>
        </view>

        <!-- 中心十字准星：只在第一人称显示（第三人称有角色挡视线，准星无意义） -->
        <view v-if="viewMode === 'fps'" class="crosshair">
          <view class="crosshair-h" />
          <view class="crosshair-v" />
        </view>

        <!--
          运行时诊断面板：显示运行时真实数值。
          APP 真机上 console 不好抓，这个面板是唯一可靠的数据来源。
          重点看 floorAnchor / scale / indoor / spawn 四项：
          floorAnchor 必须是 BackFascia（地板），不能是 BackRoofPanel（天花板板）；
          scale 应该在 0.856 附近；indoor 的 maxZ 应该 < 1（FrontWall 位置）。

          【触发方式】顶部图标已全部移除，这里改用「长按摇杆圆盘 0.8 秒」唤出。
          放在摇杆上是因为它是游戏里唯一闲置的固定触点，不会遮挡画面中央，
          也不会与拖动摇杆冲突（长按判定期间不产生摇杆位移，见 onJoystickStart）。

          【关闭方式】点面板外的遮罩关闭。

          【为什么要 closeDiag 而不是直接 showDiag = false】
          长按摇杆触发时，手指按下（touchstart）落在摇杆上，抬手瞬间手指下方的
          元素可能已经变成遮罩/面板。移动端浏览器会把这段判定补发成一次 click，
          直接落在遮罩上 —— 于是面板刚弹出就被自己点掉（ghost click）。
          closeDiag 里用 DIAG_CLOSE_GUARD 静默掉触发后的这段时间内的点击，
          与面板位置无关，布局怎么改都不会复发。
        -->
        <view v-if="showDiag" class="diag-mask" @click="onPet3dCloseDiag" />
        <view v-if="showDiag" class="diag-panel">
          <text class="diag-title">{{ $t('petHub3d.diagTitle') }}</text>
          <text class="diag-line">{{ $t('petHub3d.diagPlatformLabel') }}: {{ diagPlatform }}</text>
          <text class="diag-line">
            {{ $t('petHub3d.diagModelSourceLabel') }}: {{ diagModelSource }}
          </text>
          <text class="diag-line">floorAnchor: {{ diagAnchor }}</text>
          <text class="diag-line">floorHeight: {{ diagFloorHeight }}</text>
          <text class="diag-line">scale: {{ diagScale }}</text>
          <text class="diag-line">meshCount: {{ diagMeshCount }}</text>
          <text class="diag-line">indoor: {{ diagIndoor }}</text>
          <text class="diag-line">bounds: {{ diagBounds }}</text>
          <text class="diag-line">walls: {{ diagWalls }}</text>
          <text class="diag-line">colliders: {{ diagColliders }}</text>
          <text class="diag-line">spawn: {{ diagSpawn }}</text>
          <text class="diag-line">camera: {{ diagCamera }}</text>
          <text class="diag-line ok">{{ $t('petHub3d.diagCloseHint') }}</text>
        </view>

        <!-- 左下：方向控制摇杆 (圆盘) -->
        <view class="joystick-pad">
          <view class="joystick-base">
            <view
              class="joystick-knob"
              :class="{ 'joystick-knob-drag': joystickDragging }"
              :style="joystickKnobStyle"
            />
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<!-- 逻辑层：只做 HUD 状态镜像与命令转发 -->
<script>
import { ensureLocalModel, resolveRemoteUrl } from '@/utils/modelDownload'
import { getPetSpaceModels } from '@/api/pet'

/**
 * 3D 空间页 —— 逻辑层薄壳
 *
 * 【为什么是薄壳】
 * APP 端（uni-app vue3）页面 <script> 跑在逻辑层（jscore / V8），
 * 那里没有 document、没有 window、没有 WebGL —— three.js 根本跑不起来。
 * 旧实现直接在页面脚本里 new THREE.WebGLRenderer()，并且"等 document 出现"
 * 重试 30 次，于是必然走到 `当前环境不支持 3D（仅 H5/APP 支持）` 这句提示。
 *
 * 现在的分工：
 *  - 逻辑层（本文件）：下载模型、翻译文案、镜像 HUD 状态、转发用户操作
 *  - 视图层（下面的 pet3d renderjs）：真正跑 three.js / 挂手势 / 渲染 canvas
 *
 * H5 同样是这套结构（renderjs 在 H5 也是视图层语义），
 * 两条端只维护一份引擎代码。
 */

/**
 * 内置默认模型（管理后台接口不可用时的兜底）。
 * 后台「3D空间模型」页面启用新版本后，这里会被接口返回的地址覆盖。
 */
const DEFAULT_MODEL_URLS = {
  scene: '/static/models/home.glb',
  avatarIdle: '/static/models/0.fbx',
  avatarWalk: '/static/models/1.fbx',
}

// 引擎会用到 14 个文案：逻辑层翻译好一次性送过去。
// 视图层不引 i18n 运行时（i18n/index.js 依赖 uni 存储 API，视图层不保证可用）。
const PLAIN_LABEL_KEYS = [
  'loadingShort',
  'loadingThree',
  'threeLoadFailed',
  'loadingScene',
  'envUnsupported',
  'canvasNotReady',
  'sceneInitFailed',
  'loadingModel',
  'modelDownloadFailed',
  'modelLoadFailed',
  'cameraFps',
  'cameraTps',
]
// 这两个带占位符：用自指的 params 保证占位符原样留下来，
// 由引擎侧的 $t 在运行时替换（引擎传的是真实数值）。
const TEMPLATE_LABEL_KEYS = ['downloading', 'parsing']

// 视图层迟迟不上报就绪时的兜底提示，避免一直卡在"加载中"
const BOOT_TIMEOUT_MS = 10000

export default {
  data() {
    return {
      // 逻辑层与视图层共用同一个 id：模板把它打在 canvas 节点上，
      // 引擎在视图层用 getElementById 找它
      canvasId: 'pet3d-canvas-' + Math.random().toString(36).slice(2, 9),

      // ===== 以下字段是引擎状态的镜像，唯一写入方是 onPet3dState =====
      loading: true,
      loadingText: '',
      progress: 0,
      errorMsg: '',
      debugInfo: '',
      showDiag: false,
      viewMode: 'tps',
      positionText: '',
      pointerLocked: false,
      wireframe: false,
      figureFallback: false,
      joystickDragging: false,
      joystickOffset: { x: 0, y: 0 },
      diagPlatform: '-',
      diagModelSource: '-',
      diagAnchor: '-',
      diagFloorHeight: '-',
      diagScale: '-',
      diagMeshCount: '-',
      diagIndoor: '-',
      diagBounds: '-',
      diagWalls: '-',
      diagColliders: '-',
      diagSpawn: '-',
      diagCamera: '-',

      // ===== 壳自身状态 =====
      viewReady: false,
      booted: false,
    }
  },

  computed: {
    /**
     * 摇杆手柄的 transform。
     * 与引擎里的一样：先把位移、再回中，顺序不能反 ——
     * 内联 transform 会整体覆盖 CSS 里的 translate(-50%,-50%)。
     */
    joystickKnobStyle() {
      return {
        transform: `translate(${this.joystickOffset.x}px, ${this.joystickOffset.y}px) translate(-50%, -50%)`,
      }
    },
  },

  onReady() {
    this.armBootWatchdog()
  },

  onShow() {
    this.pet3dPush('show')
  },

  onHide() {
    this.pet3dPush('hide')
  },

  onUnload() {
    if (this._bootTimer) {
      clearTimeout(this._bootTimer)
      this._bootTimer = null
    }
    this.pet3dPush('destroy')
  },

  methods: {
    /**
     * 视图层挂载完成（renderjs 的 mounted 主动回调上来）。
     *
     * 为什么方向是「视图层 → 逻辑层」而不是反过来：
     * APP 端两层的挂载顺序没有保证，逻辑层在 onReady 里直接调 pet3d.boot()
     * 有可能打到还没挂载的视图层实例上，被静默丢弃。
     * 让视图层主动报到，顺序就一定对。
     */
    async onPet3dViewReady() {
      if (this.viewReady) return
      this.viewReady = true
      this.booted = false

      // 1. 先向后台拉取当前启用的模型地址（失败时回退到内置默认模型）
      const modelUrls = await this.resolveModelUrls()

      // 2. 在逻辑层把三个模型落地（下载 / 命中缓存）
      const models = {}
      try {
        for (const key of Object.keys(modelUrls)) {
          const url = modelUrls[key]
          models[url] = await this.loadModelSource(url)
        }
      } catch (e) {
        console.error('[space-3d] 模型准备失败', e)
        this.errorMsg = this.$t('petHub3d.modelDownloadFailed')
        this.loading = false
        return
      }

      // 3. 交给视图层起引擎
      this.booted = this.pet3dPush('boot', {
        canvasId: this.canvasId,
        labels: this.buildLabels(),
        models,
        modelUrls,
      })
      if (!this.booted) {
        this.errorMsg = this.$t('petHub3d.engineBootTimeout')
        this.loading = false
      }
    },

    /**
     * 解析 APP 3D 空间应使用的模型地址。
     * 后台未配置、接口异常或字段缺失时，逐项回退到内置默认模型，保证页面始终可进入。
     */
    async resolveModelUrls() {
      try {
        const list = await getPetSpaceModels()
        const byKey = {}
        ;(Array.isArray(list) ? list : []).forEach((item) => {
          if (item && item.modelKey && item.fileUrl) {
            byKey[item.modelKey] = item.fileUrl
          }
        })
        return {
          scene: byKey.SCENE || DEFAULT_MODEL_URLS.scene,
          avatarIdle: byKey.AVATAR_IDLE || DEFAULT_MODEL_URLS.avatarIdle,
          avatarWalk: byKey.AVATAR_WALK || DEFAULT_MODEL_URLS.avatarWalk,
        }
      } catch (e) {
        console.warn('[space-3d] 3D 模型配置获取失败，使用内置默认模型', e)
        return { ...DEFAULT_MODEL_URLS }
      }
    },

    /**
     * 引擎状态回流（renderjs → 逻辑层）。
     * 引擎每 32ms 只推「有变化」的字段，所以这里天然是增量更新。
     */
    onPet3dState(patch) {
      if (!patch || typeof patch !== 'object') return
      Object.keys(patch).forEach((k) => {
        // 只接受壳里声明过的字段，避免引擎新增内部字段污染 data
        if (Object.prototype.hasOwnProperty.call(this.$data, k)) {
          this[k] = patch[k]
        }
      })
    },

    /** 引擎需要逻辑层代劳的动作（返回键 / toast） */
    onPet3dHostAction(payload) {
      const action = payload && payload.action
      if (action === 'navigateBack') {
        uni.navigateBack()
      } else if (action === 'showToast') {
        const opts = payload.payload || {}
        uni.showToast({ title: opts.title, icon: opts.icon || 'none' })
      }
    },

    onPet3dRetry() {
      this.errorMsg = ''
      this.progress = 0
      this.pet3dPush('retry')
    },

    onPet3dCloseDiag() {
      this.pet3dPush('closeDiag')
    },

    /**
     * 退出 3D 空间，回到宠物主页。
     * 正常从宠物页 navigateTo 进入时页面栈 > 1，直接出栈；
     * 分享直链 / 地址栏直接打开本页时栈内只有当前页，navigateBack 不会生效
     * （H5 下既不跳转也不一定回调 fail），所以先判断栈长度，再兜底 switchTab。
     */
    goBack() {
      const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
      if (pages.length > 1) {
        uni.navigateBack({ delta: 1 })
      } else {
        uni.switchTab({ url: '/pages/tabbar/pet' })
      }
    },

    /**
     * 视图层不在（比如 renderjs 编译异常）时安全降级：
     * this.pet3d 在 APP 是一个代理对象、在 H5 就是页面实例本身，
     * 两者都可能取不到，统一 try/catch。
     */
    pet3dPush(action, payload) {
      try {
        const mod = this.pet3d
        if (!mod || typeof mod.onHostCommand !== 'function') return false
        mod.onHostCommand(action, payload)
        return true
      } catch (e) {
        console.warn('[space-3d] 指令下发失败', action, e)
        return false
      }
    },

    buildLabels() {
      const labels = {}
      PLAIN_LABEL_KEYS.forEach((k) => {
        labels['petHub3d.' + k] = this.$t('petHub3d.' + k)
      })
      TEMPLATE_LABEL_KEYS.forEach((k) => {
        // 自指占位符：让 {p} / {progress} 原样保留，交给引擎运行时替换
        const keeper = k === 'downloading' ? { p: '{p}' } : { progress: '{progress}' }
        labels['petHub3d.' + k] = this.$t('petHub3d.' + k, keeper)
      })
      return labels
    },

    /**
     * 单个模型的「本地路径 + 远程兜底」
     * - primary：逻辑层下载/缓存好的路径（APP 是 _doc/...，H5 是 blob:）
     * - fallback：远程 HTTP 地址。APP 视图层读不到 _doc/ 文件时由引擎自动回退
     */
    async loadModelSource(path) {
      this.loadingText = this.$t('petHub3d.downloading', { p: 0 })
      const primary = await ensureLocalModel(path, (p) => {
        this.progress = p
        this.loadingText = this.$t('petHub3d.downloading', { p })
      })
      let fallback = null
      // #ifdef APP-PLUS
      // H5 的 blob: 地址本身就是最优路径，不需要远程兜底；
      // APP 的 _doc/ 路径在 webview 里有可能读不到，留一个 HTTP 兜底。
      fallback = resolveRemoteUrl(path)
      // #endif
      return { primary, fallback }
    },

    armBootWatchdog() {
      if (this._bootTimer) clearTimeout(this._bootTimer)
      this._bootTimer = setTimeout(() => {
        if (this.viewReady) return
        this.errorMsg = this.$t('petHub3d.engineBootTimeout')
        this.loading = false
        this.debugInfo = 'pet3d 视图层未在 10s 内就绪'
      }, BOOT_TIMEOUT_MS)
    },
  },
}
</script>

<!-- 视图层：three.js 真正运行的地方（APP / H5 通用） -->
<script module="pet3d" lang="renderjs">
// 注意：renderjs 块由 uni 单独用 esbuild 打包，**不认 @ 别名**，
// 这里必须写相对路径，否则 APP 构建直接报 Could not resolve "@/utils/..."
import { createEngine } from '../../utils/pet3d/create-engine'

/**
 * 视图层宿主：three.js 真正运行的地方
 *
 * 运行环境差异（同一份代码，两端都成立）：
 *  - APP：本模块被编译进 app-view.js，在 webview 里执行 —— 有 document / WebGL
 *  - H5 ：被 uni 作为 mixin 合入页面组件（this.pet3d === this），也在 DOM 环境里
 *
 * 与逻辑层的两个方向：
 *  - 逻辑层 → 这里：this.pet3d.onHostCommand(action, payload)
 *  - 这里 → 逻辑层：this.$ownerInstance.callMethod('onPet3dState', patch)
 */

// 一个页面视图只有一份引擎
let engine = null
let unbindInput = null

const findTouch = (e) =>
  (e && e.changedTouches && e.changedTouches[0]) || (e && e.touches && e.touches[0]) || null

export default {
  mounted() {
    // 主动向逻辑层报到，由它决定何时 boot（模型还没下载，不能在这里就起引擎）
    this.notifyReady(0)
  },

  beforeDestroy() {
    this.teardown()
  },

  unmounted() {
    this.teardown()
  },

  methods: {
    /**
     * 报到。$ownerInstance 在部分时序下可能还没就位，做几次短重试。
     * 逻辑层收到后会把「已就绪」置位，之后永不再问。
     */
    notifyReady(attempt) {
      const owner = this.$ownerInstance
      if (owner && typeof owner.callMethod === 'function') {
        try {
          owner.callMethod('onPet3dViewReady')
          return
        } catch (e) {
          console.warn('[pet3d] 上报就绪失败', e)
        }
      }
      if (attempt >= 20) {
        console.error('[pet3d] 视图层始终拿不到 $ownerInstance')
        return
      }
      setTimeout(() => this.notifyReady(attempt + 1), 100)
    },

    /** 逻辑层下发的指令 */
    onHostCommand(action, payload) {
      if (action === 'boot') {
        this.boot(payload || {})
        return
      }
      if (!engine) return
      try {
        if (action === 'retry') engine.initScene()
        else if (action === 'closeDiag') engine.closeDiag()
        else if (action === 'hide') engine.onHide()
        else if (action === 'show') engine.onShow()
        else if (action === 'destroy') this.teardown()
      } catch (e) {
        console.error('[pet3d] 指令执行失败', action, e)
      }
    },

    boot(cfg) {
      this.teardown()

      const owner = this.$ownerInstance
      const labels = cfg.labels || {}
      const models = cfg.models || {}
      // 后台启用的模型地址（逻辑层已拉取），引擎按用途使用
      const modelUrls = cfg.modelUrls || {}

      engine = createEngine({
        canvasId: cfg.canvasId,
        modelUrls,

        // 文案用逻辑层翻译好的快照：视图层不引 i18n 运行时。
        // 保留 {p} / {progress} 的运行时替换。
        t: (key, params) => {
          const raw = labels[key] || key
          if (!params) return raw
          return String(raw).replace(/\{(\w+)\}/g, (m, k) =>
            params[k] === undefined || params[k] === null ? m : params[k],
          )
        },

        onState: (patch) => {
          try {
            owner.callMethod('onPet3dState', patch)
          } catch (e) {
            /* 页面已销毁，忽略 */
          }
        },

        onHostAction: (action, payload) => {
          try {
            owner.callMethod('onPet3dHostAction', { action, payload })
          } catch (e) {
            /* 页面已销毁，忽略 */
          }
        },

        // 模型路径由逻辑层给：它已经下载好并算出了远程兜底地址
        modelResolver: async (url) => {
          const hit = models[url]
          if (hit) return hit
          return { primary: url, fallback: null }
        },
      })

      // 手势必须先绑：APP 端模板事件在逻辑层，拿不到视图层的手势序列
      this.bindInput()

      // 调试入口：浏览器/真机排查时可在控制台直接看引擎内部状态
      if (typeof window !== 'undefined') window.__pet3d = engine

      engine.onLoad()
      engine.onReady()
      engine.onShow()
    },

    /**
     * 手势绑定在 document 的捕获阶段。
     *
     * 为什么不用模板的 @touchstart：
     * APP 端模板事件由逻辑层派发，事件对象是序列化过的，clientX/touches
     * 这类字段不可靠；而且 preventDefault 必须发生在视图层才拦得住页面滚动。
     *
     * 分工：
     *  - 落在摇杆圆盘内 → 摇杆逻辑（含长按唤出诊断面板）
     *  - 其余区域       → 视角逻辑（PointerLock 尝试 + 引擎自己的 look 监听）
     */
    bindInput() {
      if (unbindInput) return

      const onStart = (e) => {
        if (!engine) return
        const touch = findTouch(e)
        if (touch && engine.isInControlArea(touch.clientX, touch.clientY)) {
          engine.onJoystickStart(e)
          return
        }
        engine.onCanvasTouchStart(e)
      }
      const onMove = (e) => {
        if (engine && engine.three.joystick.active) engine.onJoystickMove(e)
      }
      const onEnd = (e) => {
        if (engine && engine.three.joystick.active) engine.onJoystickEnd(e)
      }

      const opts = { passive: false, capture: true }
      document.addEventListener('touchstart', onStart, opts)
      document.addEventListener('touchmove', onMove, opts)
      document.addEventListener('touchend', onEnd, opts)
      document.addEventListener('touchcancel', onEnd, opts)

      unbindInput = () => {
        document.removeEventListener('touchstart', onStart, { capture: true })
        document.removeEventListener('touchmove', onMove, { capture: true })
        document.removeEventListener('touchend', onEnd, { capture: true })
        document.removeEventListener('touchcancel', onEnd, { capture: true })
      }
    },

    teardown() {
      if (unbindInput) {
        try {
          unbindInput()
        } catch (e) {
          /* ignore */
        }
        unbindInput = null
      }
      if (engine) {
        try {
          engine.$destroy()
        } catch (e) {
          console.warn('[pet3d] 引擎销毁异常', e)
        }
        engine = null
      }
    },
  },
}
</script>

<style lang="scss" scoped>
/* ==========================================================================
   全屏横屏游戏布局
   --------------------------------------------------------------------------
   与旧版的区别：
   1. 删掉 .header / .back-btn / .header-title / .icon-btn 等返回栏样式，
      3D 画布直接占满整个页面（position:absolute inset:0），不再有 flex 纵向分栏。
   2. 顶部按需求「清空整条，只留返回键」：视角切换 / 复位 / 线框 / 诊断
      四个功能按钮与两条信息胶囊（位置 + 操作提示）全部移除，
      HUD 只剩左上角一个返回键、右下角摇杆、以及 FPS 下的十字准星。
      诊断面板改由「长按摇杆 0.8 秒」唤出（见 onJoystickStart）。
   3. 横屏下 750rpx 设计稿宽度会被压得很扁，因此所有 HUD 元素改用
      px/vmin 为主、rpx 为辅的手感尺寸，保证按钮足够大、摇杆在拇指区。
   ========================================================================== */
.space-3d {
  position: relative;
  width: 100vw;
  height: 100vh;
  background: #1a1a1a;
  color: #f6f2ee;
  overflow: hidden;
  // 整页禁止浏览器处理触摸手势（滚动/缩放/下拉刷新），全部交给我们自己的 touch 监听
  touch-action: none;
  overscroll-behavior: none;
}

/* 画布层：占满全屏，作为 HUD 的定位基准 */
.canvas-wrap {
  position: absolute;
  inset: 0;
  overflow: hidden;
  touch-action: none;
}
.canvas {
  width: 100%;
  height: 100%;
  position: relative;
}

/* ====== 左上角返回键浮层 ======
   顶部整条清空后，这里只剩一个退出入口。
   底色 0.55 不透明度：房间多为浅色地板/家具，太淡会看不清箭头
   （原来 0.45 实测偏灰）。 */
.hud-actions {
  position: absolute;
  top: calc(var(--status-bar-height, 0) + 10px);
  display: flex;
  gap: 8px;
  pointer-events: auto;
  z-index: 20;
}
.hud-actions-left {
  left: 12px;
}
.hud-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.55);
  border: 1px solid rgba(219, 201, 138, 0.45);
  color: #f6f2ee;
  font-size: 20px;
  backdrop-filter: blur(6px);
  transition: background 0.15s;
}
.hud-btn:active {
  background: rgba(219, 201, 138, 0.35);
}

.debug-info {
  position: absolute;
  top: 16px;
  left: 16px;
  right: 16px;
  padding: 12px 16px;
  background: rgba(0, 0, 0, 0.6);
  color: #dbc98a;
  font-size: 12px;
  border-radius: 8px;
  font-family: monospace;
  z-index: 100;
  word-break: break-all;
  /* 纯信息展示层：绝不参与命中测试。
     它的 z-index 高于 HUD 按钮且横跨顶部整条，若不关掉 pointer-events，
     左上角「返回 / 视角切换」按钮会被它整条盖住，真机上点不到
     （Playwright 复现：click 被 "debug-info subtree intercepts pointer events" 拦下）。 */
  pointer-events: none;
}

.loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}
.loading-text {
  color: #dbc98a;
  font-size: 15px;
}
.progress-bar {
  margin-top: 16px;
  width: 240px;
  height: 4px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: #dbc98a;
  transition: width 0.2s;
}

.error-mask {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
}
.error-text {
  display: block;
  color: #d9b4b0;
  font-size: 14px;
  margin-bottom: 16px;
}
.retry-btn {
  display: inline-block;
  padding: 10px 32px;
  background: #dbc98a;
  color: #1a1a1a;
  border-radius: 999px;
  font-weight: var(--font-weight-medium);
}

/* ====== 游戏 HUD ====== */
.hud {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.crosshair {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 24px;
  height: 24px;
  transform: translate(-50%, -50%);
  pointer-events: none;
}
.crosshair-h,
.crosshair-v {
  position: absolute;
  background: rgba(219, 201, 138, 0.7);
}
.crosshair-h {
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  transform: translateY(-50%);
}
.crosshair-v {
  left: 50%;
  top: 0;
  bottom: 0;
  width: 1px;
  transform: translateX(-50%);
}

/* ===== 方向控制摇杆 (左下角圆盘) =====
   横屏屏幕很矮（典型 412px），摇杆按视口高度自适应：
   min(30vh, 130px) 在 412px 高时约 124px、740px 高的平板封顶 130px，
   拇指命中舒服又不至于遮住画面。
   原来写死 240rpx 在横屏下会被撑到 300px+，几乎占满左侧半屏。 */
.joystick-pad {
  position: absolute;
  bottom: max(18px, env(safe-area-inset-bottom, 0px));
  left: max(20px, env(safe-area-inset-left, 0px));
  width: min(30vh, 130px);
  height: min(30vh, 130px);
  /* 外圈实际可拖动半径 = pad 宽/2，内部尺寸统一用百分比跟随，
     这样改 pad 尺寸时 knob / base 不需要同步改。 */
  --joy-size: min(30vh, 130px);
  /* 摇杆区域需要接收触摸,不让视角旋转抢事件 */
  pointer-events: auto;
  /* 自己处理触摸,不触发页面滚动 */
  touch-action: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
.joystick-base {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  /* 半透明深色底盘 + 亮金描边：在浅色地板/家具上也清晰可辨 */
  background: rgba(0, 0, 0, 0.42);
  border: 2px solid rgba(219, 201, 138, 0.6);
  box-shadow: inset 0 0 16px rgba(0, 0, 0, 0.5);
  /* 用 transform 中心点 */
  display: flex;
  align-items: center;
  justify-content: center;
}
.joystick-knob {
  position: absolute;
  left: 50%;
  top: 50%;
  /* 约为外圈的 44%（视觉上手柄比例），用百分比保证随外圈同步缩放。
     居中用 transform 而不是负 margin：
     百分比 margin 在 flex 容器里的解析基准不稳定，
     而 translate(-50%,-50%) 是相对元素自身尺寸，行为确定。
     注意 transform 由模板的 joystickKnobStyle 提供（居中 + 拖动位移合成），此处不再重复写。 */
  width: 44%;
  height: 44%;
  border-radius: 50%;
  background: rgba(240, 214, 150, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.35);
  /* 松手时缓动回中 */
  transition: transform 0.15s ease-out;
  pointer-events: none;
}
/* 拖动中关掉 transition,保证手柄跟手不滞后 */
.joystick-knob.joystick-knob-drag {
  transition: none;
}

/* ===== 运行时诊断面板 =====
   之前只有模板没有样式（横屏改造时补上）：
   真机 APP webview 里 console 不好抓，这是唯一的运行时数据来源，
   面板必须半透明且尽量不挡住画面中心，同时行高紧凑以容纳 13 行。
   触发方式：长按左下摇杆 0.8 秒（顶部图标已全部移除）。 */
/* 诊断面板：定位在「右上角」。
   不能放左上角 —— 摇杆在左下（bottom 18px），而面板按内容自适应高度，
   竖屏 412px 高的横屏布局下面板会一路向下延伸到 y≈349，正好把摇杆中心 (y≈332)
   盖住。长按触发时 touchstart 落在摇杆、touchend 落在面板，
   浏览器补发的 ghost click 命中面板 → 触发「点击关闭」→ 面板刚弹出就被自杀。
   所以必须避开左下摇杆区，右上角是画面里唯一空着的角落。 */
/* 诊断面板的关闭遮罩。
   只铺画面右侧 52%，左上角和左下角（摇杆所在的 130px 圆盘区）保持空白，
   这样「点面板外关闭」不会挡住摇杆，长按时手指也不会落在遮罩上。 */
.diag-mask {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 48%;
  z-index: 29;
  pointer-events: auto;
}

.diag-panel {
  position: absolute;
  top: calc(var(--status-bar-height, 0) + 12px);
  right: 12px;
  left: auto;
  padding: 10px 12px;
  background: rgba(0, 0, 0, 0.72);
  border: 1px solid rgba(219, 201, 138, 0.4);
  border-radius: 8px;
  max-width: 46vw;
  max-height: 76vh;
  /* 诊断行是长文本，条目多时内部滚动，绝不外溢到摇杆上 */
  overflow-y: auto;
  overflow-x: hidden;
  pointer-events: auto;
  z-index: 30;
}
.diag-title {
  display: block;
  color: #dbc98a;
  font-size: 13px;
  font-weight: var(--font-weight-semibold);
  margin-bottom: 6px;
}
.diag-line {
  display: block;
  color: #e8e2da;
  font-size: 11px;
  line-height: 1.5;
  font-family: monospace;
}
.diag-line.ok {
  margin-top: 6px;
  color: #dbc98a;
}
</style>
