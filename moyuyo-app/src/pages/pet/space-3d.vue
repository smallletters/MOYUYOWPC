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
    <view class="canvas-wrap" @touchstart="onCanvasTouchStart">
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
        <view class="retry-btn" @click="initScene">
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
        <view v-if="showDiag" class="diag-mask" @click="closeDiag" />
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
        <view
          class="joystick-pad"
          @touchstart.stop="onJoystickStart"
          @touchmove.stop="onJoystickMove"
          @touchend.stop="onJoystickEnd"
          @touchcancel.stop="onJoystickEnd"
        >
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

<script>
// 3D 模型远程下载 + 本地缓存工具
import { ensureLocalModel } from '@/utils/modelDownload'

// 不在顶层 import three.js —— 顶层 import 失败会导致整个页面渲染不出来
let THREE = null
let PointerLockControls = null
let GLTFLoader = null
let FBXLoader = null
let DRACOLoader = null

// 动态加载：用 package name 走 vite alias，保证 PointerLockControls 内部的 import { ... } from 'three' 命中同一个实例
// 这样不会出现 "Multiple instances of Three.js" 警告（GLTFLoader 等子模块内部也 import 了 three）

// three.js 内部对象不放到 data() 里：Vue3 会用 Proxy 把它们变成响应式对象，
// 触发 three 内部 modelViewMatrix 等只读属性的 "is read-only and non-configurable" 报错
// 用 WeakMap：每个页面实例独立一份桶
const threeInstances = new WeakMap()

// ============== 场景尺度常量（可调） ==============
// 房间层高（米）。模型归一化到这个高度后，人物 1.7m / 家具尺寸才与建筑形成正确比例。
// 实测 home.glb 原始净高 8.978 单位，若按最长边归一化会得到 9.47m 的空仓库观感。
const TARGET_CEILING_HEIGHT = 2.8

// 人物碰撞半径（米）与身高（米）
const FIGURE_RADIUS = 0.3
const FIGURE_HEIGHT = 1.7

// ============== 碰撞分级阈值（米，经网格扫描标定） ==============
// STEP_HEIGHT：顶面低于此高度的物体视为「可跨越」（地毯/门槛/矮茶几/床沿）。
//   实测取 0.5 时可行走率 69%，取 0 则掉到 50.6% —— 矮家具不排除会把房间切碎。
// OVERHEAD_CLEARANCE：底面高于此高度的物体视为「可从下方穿过」
//   （楼梯/挑高/吊柜/桌面）。这条是本模型最关键的一条：home.glb 的 Mesh_0
//   （楼梯 y[0.65,1.47]，占地 3.49×3.78m）不排除会独占 63.2% 地面，
//   人物被围死在床边 → 表现为「原地踏步」。
const STEP_HEIGHT = 0.5
const OVERHEAD_CLEARANCE = 0.6

// 墙体判据（用于从几何反推室内范围）
// WALL_MIN_WIDTH：墙沿「跨房间方向」至少要有这么长才算墙。
//   实测 WallFillRight 只有 0.19m 宽，那是门边补片不是墙；
//   不设这道下限就会把它当侧墙，把房间切成半边。
// WALL_SPAN_RATIO：墙长至少要覆盖当前跨度的这个比例，才认为是「隔断」而非隔断碎片。
const WALL_MIN_WIDTH = 0.5
const WALL_SPAN_RATIO = 0.55

// 视高（相机到人物眼睛/后脑的高度）
const EYE_HEIGHT = 1.6 // FPS：接近真人眼高；1.7m 人物的眼睛约在 1.6m

// TPS 跟随参数
const TPS_DISTANCE = 2.6 // 相机在人物身后的水平距离（臂长上限）
const TPS_HEIGHT = 1.9 // 相机相对地面的高度
// TPS 视线默认瞄准的高度（米）。相机在 1.9m，若水平平视，1.7m 的人物会整体
// 掉到画面下半部（实测脚部 NDC y=-1.015，已在视锥外）。以 ~1.0m 胸腹位置
// 为焦点，配合「相机高度差 / 臂长」算出基础俯角，进页面就能看到人物全身。
const TPS_FOCUS_HEIGHT = 1.0

// JOYSTICK_DEADZONE：摇杆死区（归一化半径比例，0~1）。
// 输入幅度低于此值视为「静止」，用于过滤触屏硬件抖动。
// 见 applyMovement 内的详细说明 —— 这是消除走路动画顿挫感的关键参数。
const JOYSTICK_DEADZONE = 0.08

// DIAG_CLOSE_GUARD：诊断面板打开后的「点击静默窗口」（毫秒）。
// 长按触发的那次触摸，抬手时浏览器会在按下/抬起落点之间补发一次 click
//（移动端 ghost click）。若不加这道闸门，这次 click 会立刻落到刚出现的
// 遮罩上把面板关掉，用户永远看不到诊断内容。
// 取 350ms：略大于主流浏览器 ghost click 的延迟上限（~300ms），
// 又不至于让「手速很快地点一下关闭」失效。
const DIAG_CLOSE_GUARD = 350

// DIAG_HOLD_MS：长按摇杆唤出诊断面板的时长（毫秒）。
// 顶部图标全部移除后，诊断面板改由长按触发，摇杆成了唯一的"闲置固定触点"。
// 取 800ms：短于它会被当成普通摇杆操作误触发，长于它玩家会以为没反应。
const DIAG_HOLD_MS = 800

// Draco 解码器路径：优先本地 static（离线可用），失败再退 CDN
const DRACO_LOCAL_PATH = '/static/draco/'
const DRACO_CDN_PATH = 'https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/libs/draco/'

export default {
  data() {
    return {
      canvasId: 'three-canvas-' + Math.random().toString(36).slice(2, 9),
      loading: true,
      loadingText: '',
      progress: 0,
      errorMsg: '',
      wireframe: false,
      debugInfo: '',

      // PointerLock（H5 桌面浏览器可用；APP webview 不可用，自动退回触摸转视角）
      pointerLocked: false,
      positionText: this.$t('petHub3d.loadingShort'),
      // 视角模式：fps = 第一人称（人物眼睛）/ tps = 第三人称跟随（相机在人物身后上方）
      // 默认 tps：让玩家一进入页面就看到类似游戏第三人称跟随的视角
      viewMode: 'tps',

      // 摇杆 UI:手柄相对外圈中心的偏移(像素),用于渲染 .joystick-knob 的 transform
      joystickOffset: { x: 0, y: 0 },
      // 摇杆拖动中:true 时关闭手柄 transition 保证跟手
      joystickDragging: false,
      // 摇杆外圈半径(像素):在 onJoystickStart 时取一次缓存,避免 move 高频 querySelector
      joystickRadiusCached: 120,
      // 化身是否为兜底几何体(true 时 HUD 给提示)
      figureFallback: false,

      // 诊断面板（APP 真机抓不到 console 时的唯一可靠数据源）
      showDiag: false,
      // 面板打开时刻（Date.now 毫秒），closeDiag 用它做 ghost click 静默窗口
      diagOpenedAt: 0,
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
    }
  },

  computed: {
    // three 桶用 computed + WeakMap 隔离 Vue 响应式:
    // - Vue 不会把 WeakMap 里的 bucket 包装成 Proxy(bucket 不是 this 直接属性)
    // - computed 内无 reactive 依赖,dirty 永远 false → getter 只在首次执行一次
    // - 每帧访问 this.three 都是 cached value 直接返回,实测开销 ≈ 普通字段读
    // 注意:不能直接把 bucket 放 data(),否则 Vue 会深度 reactive 包装,
    // 触发 three 内部 modelViewMatrix 等只读属性的 "is read-only" 报错
    three() {
      let bucket = threeInstances.get(this)
      if (!bucket) {
        bucket = {
          renderer: null,
          scene: null,
          camera: null,
          controls: null,
          animationId: null,
          modelGroup: null,
          ambientLight: null,
          directionalLight: null,
          resizeObserver: null,
          modelBox: null,
          modelCenter: null,
          modelSize: null,
          bounds: null,
          // 碰撞体：模型加载后从每个 mesh 提取世界空间 AABB,人物移动时做滑动检测
          colliders: null,
          // 人物脚下位置(权威坐标系)。相机 position 在 TPS 模式下是"人物身后的观察点",
          // FPS 模式下才是人物自身位置。移动/碰撞/复位一律以本字段为准,
          // 避免出现"相机越走越远 / 人物与相机错位"这类坐标系混用问题。
          playerPos: null, // new THREE.Vector3()
          // 出生点（标定过必定无碰撞），resetCamera() 复位到这里
          spawnPoint: null,
          // 房间实测尺度（层高锚定后推导得出），供 HUD 与调试使用
          roomHeight: 0,
          roomHalfX: 0,
          roomHalfZ: 0,
          // 第一人称专用：键盘状态、摇杆状态
          keys: {
            w: false,
            a: false,
            s: false,
            d: false,
            ArrowUp: false,
            ArrowDown: false,
            ArrowLeft: false,
            ArrowRight: false,
          },
          joystick: {
            x: 0,
            y: 0,
            active: false,
            touchId: null,
            originX: 0,
            originY: 0,
            // 摇杆 start 时记录 / end 时还原的键盘 w/s 状态,防止吞掉玩家已按住的键
            savedKeys: null,
          },
          // 移动速度（米/秒）。由每秒位移改为按 deltaTime 积分，
          // 保证在 30fps 与 120fps 下走同样的距离（原先固定 0.05/帧 → 高刷屏走更快）
          moveSpeed: 2.4, // m/s
          lastFrameTime: 0,
          // TPS 相机臂长当前值（米）。带单向阻尼：缩短立即生效、伸长指数平滑。
          // 必须跨帧持久化，否则每帧都退化成"理论值" → 贴墙转身时镜头瞬跳。
          camArm: null,
          // 复用对象,避免每帧碰撞检测产生 GC 压力
          collisionRadius: 0.3, // 人物碰撞半径（米）
          lookSensitivity: 0.003, // 触摸旋转灵敏度（每像素多少弧度）
          // 视角触摸状态：PointerLockControls 在 webview 不可用，自己实现触摸看视角
          lookTouch: { active: false, touchId: null, lastX: 0, lastY: 0 },
          // 累计 yaw/pitch（弧度）
          yaw: 0,
          pitch: 0,
          // 人物化身：两个 FBX group(0=静态 / 1=走路) + 走路 mixer
          figureIdleGroup: null, // 0.fbx 静态姿态
          figureWalkGroup: null, // 1.fbx 走路动画
          figureWalkMixer: null,
          figureWalkAction: null,
          figureWalkClock: null,
          // 当前化身的姿态:idle / walk,根据玩家是否在移动切换
          figureState: 'idle',
          // 化身朝向补偿角（弧度）：由模型包围盒自动判定"模型默认朝向",
          // 换模型不再需要手动改 +Math.PI
          figureYawOffset: 0,
        }
        threeInstances.set(this, bucket)
      }
      return bucket
    },

    /**
     * 摇杆手柄的 transform 样式。
     *
     * 为什么不用内联 `translate(${x}px, ${y}px)`：
     * 横屏改造后手柄尺寸改用百分比（42% of 外圈），需要 `translate(-50%,-50%)`
     * 做居中 —— 而模板原来的内联 transform 会**整个覆盖** CSS 里的 transform，
     * 导致手柄偏出圆心。这里把「居中」和「拖动位移」合成一条 translate 链，
     * 顺序必须是先位移再回中：translate(dx,dy) translate(-50%,-50%)。
     */
    joystickKnobStyle() {
      return {
        transform: `translate(${this.joystickOffset.x}px, ${this.joystickOffset.y}px) translate(-50%, -50%)`,
      }
    },
  },

  onReady() {
    this.$nextTick(() => this.initScene())
  },

  onLoad() {
    // eslint-disable-next-line
    console.log('[space-3d] onLoad, document=', typeof document, 'window=', typeof window)
  },

  onUnload() {
    this.disposeScene()
  },

  onHide() {
    const t = this.three
    if (t.animationId) {
      cancelAnimationFrame(t.animationId)
      t.animationId = null
    }
    // 暂停走路动画 mixer,避免后台累积 delta;恢复时重置 clock 防止跳帧
    if (t.figureWalkMixer) {
      t.figureWalkMixer.stopAllAction()
    }
    if (t.figureWalkClock) {
      t.figureWalkClock.stop()
    }
  },

  onShow() {
    const t = this.three
    // 重置走路动画 clock:清空后台期间的累计时间,恢复后第一帧 delta 不会跳变
    if (t.figureWalkClock) {
      t.figureWalkClock.start()
    }
    if (t.renderer && !t.animationId) {
      this.animate()
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    initScene() {
      this.loading = true
      this.loadingText = this.$t('petHub3d.loadingThree')
      this.errorMsg = ''
      this.debugInfo = `初始化中... canvasId=${this.canvasId}`

      // 动态加载 three 主包 + PointerLockControls + GLTFLoader（H5/APP 的 webview 都支持）
      // 用 package name 走 vite alias，保证子模块内的 import 'three' 命中同一个实例（避免 Multiple instances 警告）
      // APP 自定义基座真机冷启动时,three.js 几个 chunk 加起来可能在 500ms~1s 之内才 resolve,
      // 此时 webview 内部的 document 已就绪(否则 import 都跑不了),所以这里无需重试
      Promise.all([
        import('three').then((m) => {
          THREE = m
        }),
        import('three/examples/jsm/controls/PointerLockControls.js').then((m) => {
          PointerLockControls = m.PointerLockControls
        }),
        import('three/examples/jsm/loaders/GLTFLoader.js').then((m) => {
          GLTFLoader = m.GLTFLoader
        }),
        import('three/examples/jsm/loaders/FBXLoader.js').then((m) => {
          FBXLoader = m.FBXLoader
        }),
        import('three/examples/jsm/loaders/DRACOLoader.js').then((m) => {
          DRACOLoader = m.DRACOLoader
        }),
      ])
        .then(() => this.startScene())
        .catch((e) => {
          console.error('[space-3d] three.js 加载失败', e)
          this.errorMsg = this.$t('petHub3d.threeLoadFailed') + (e.message || e)
          this.debugInfo = `three.js 加载失败：${e.message || e}`
          this.loading = false
        })
    },

    startScene() {
      try {
        this.loadingText = this.$t('petHub3d.loadingScene')
        this.debugInfo = `three.js 已加载，开始建场景`
        // eslint-disable-next-line
        console.log(
          '[space-3d] startScene, THREE=',
          typeof THREE,
          'PLC=',
          typeof PointerLockControls,
          'doc=',
          typeof document,
          'plus=',
          typeof plus,
        )

        // APP 端 document 可能在 webview 启动早期短暂 undefined
        // 改为延迟 200ms 重试,给真机 webview 充分冷启动时间(实测自定义基座首次进入可达 1~2s)
        // 总共最多 30 次 ≈ 6s,覆盖到绝大多数 APP webview 冷启动场景
        if (typeof document === 'undefined') {
          // 守卫:页面已卸载就不再做任何事,既不报错也不重排 timer
          if (this.three._disposed) return
          this.three._docRetryCount = (this.three._docRetryCount || 0) + 1
          if (this.three._docRetryCount <= 30) {
            this.debugInfo = `等待 webview 初始化 document...(${this.three._docRetryCount}/30)`
            this.three._docRetryTimer = setTimeout(() => this.startScene(), 200)
            return
          }
          this.errorMsg = this.$t('petHub3d.envUnsupported')
          this.loading = false
          this.debugInfo = `typeof document=undefined 重试 30 次仍无`
          return
        }
        // document 已就绪,清零重试计数
        this.three._docRetryCount = 0

        const canvasEl = document.getElementById(this.canvasId)
        if (!canvasEl) {
          // 守卫:页面已卸载就不再做任何事,既不报错也不重排 timer
          if (this.three._disposed) return
          // 性能:canvas DOM 重试限制次数,避免极端情况(模板编译异常 / DOM id 冲突)
          // 下无限 setTimeout 循环,造成内存与定时器泄漏
          this.three._canvasRetryCount = (this.three._canvasRetryCount || 0) + 1
          if (this.three._canvasRetryCount > 30) {
            this.errorMsg = this.$t('petHub3d.canvasNotReady')
            this.loading = false
            this.debugInfo = `canvas DOM 重试 ${this.three._canvasRetryCount} 次仍无`
            return
          }
          this.debugInfo = `找不到节点 #${this.canvasId}，200ms 后重试 (${this.three._canvasRetryCount}/30)`
          // 清理上一次尝试创建的 renderer(避免 WebGL context 泄漏堆积)
          if (this.three.renderer) {
            try {
              this.three.renderer.dispose()
            } catch (e) {
              // ignore
            }
            this.three.renderer = null
          }
          if (this.three.controls) {
            try {
              this.three.controls.dispose()
            } catch (e) {
              // ignore
            }
            this.three.controls = null
          }
          this.three._canvasRetryTimer = setTimeout(() => this.startScene(), 200)
          return
        }
        // canvas 就绪,清零重试计数
        this.three._canvasRetryCount = 0
        // 守卫:走到这里之前用户可能已经返回页面(disposeScene 把 _disposed 置 true),
        // 此时不再创建 WebGL context / 加载模型,直接返回,避免资源浪费与回调打到已销毁实例
        if (this.three._disposed) return

        this.debugInfo = `容器尺寸 ${canvasEl.clientWidth}x${canvasEl.clientHeight}`

        const width = canvasEl.clientWidth || window.innerWidth
        const height = canvasEl.clientHeight || window.innerHeight

        // 渲染器
        // 性能优化:移动端 WebView GPU 算力有限,默认配置(antialias + dpr=2 + ACES)会
        // 导致单帧像素填充率过高,直接卡到 5~10fps。以下配置是移动端权衡后的最优点:
        // - antialias=false:场景以室内低对比度为主,AA 几乎看不出差异,但 GPU 节省 ~30%
        // - setPixelRatio(1):不再按 dpr 放大,渲染分辨率 = CSS 尺寸(像素量 = width*height)
        //   例如 1080×1920 屏从渲染 2160×3840 = 8.3M 像素降到 1M,GPU 压力降 ~80%
        // - NoToneMapping:ACES 对低对比度场景收益小,移除省 ~10% GPU
        // - 视觉损失:场景边缘有锯齿、暗部细节略丢失;可在桌面端 if 分支恢复
        const renderer = new THREE.WebGLRenderer({ antialias: false, alpha: true })
        renderer.setPixelRatio(1)
        renderer.setSize(width, height)
        renderer.outputColorSpace = THREE.SRGBColorSpace
        // 简化 tone mapping:场景以暖色室内为主,NoToneMapping 比 ACES 快 ~10%
        renderer.toneMapping = THREE.NoToneMapping
        // 启用按需渲染 - 当没有任何变化时跳过 render 调用(本次先注释保留显式 render)
        // renderer.shadowMap.enabled = false // 不需要阴影
        canvasEl.appendChild(renderer.domElement)
        this.three.renderer = renderer

        // 场景
        const scene = new THREE.Scene()
        scene.background = new THREE.Color(0x1a1a1a)
        scene.fog = new THREE.Fog(0x1a1a1a, 5, 40)
        this.three.scene = scene

        // 相机 (FOV 75 更接近人眼)
        const camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 100)
        // 起始位置在模型加载后按真实尺度重设（见 loadModel）
        camera.position.set(0, EYE_HEIGHT, 0)
        this.three.camera = camera

        // 环境光：提高强度，确保人物模型即使没有纹理也可见
        const ambient = new THREE.AmbientLight(0xffffff, 1.2)
        scene.add(ambient)
        this.three.ambientLight = ambient

        // 半球光：让上下有冷暖区分
        const hemi = new THREE.HemisphereLight(0xffeebb, 0x080820, 0.8)
        scene.add(hemi)

        // 主光 (方向光)
        const dir = new THREE.DirectionalLight(0xffffff, 1.0)
        dir.position.set(5, 8, 5)
        scene.add(dir)
        this.three.directionalLight = dir

        // PointerLockControls：第一人称视角（鼠标移动旋转视角）
        const controls = new PointerLockControls(camera, renderer.domElement)
        this.three.controls = controls

        controls.addEventListener('lock', () => {
          this.pointerLocked = true
        })
        controls.addEventListener('unlock', () => {
          this.pointerLocked = false
        })

        // 监听键盘
        this.bindKeyboard()

        // 加载模型
        this.loadModel(renderer, scene)

        // 尺寸监听
        const resizeObserver = new ResizeObserver(() => this.onResize())
        resizeObserver.observe(canvasEl)
        this.three.resizeObserver = resizeObserver

        // 启动渲染循环
        this.animate()
      } catch (e) {
        console.error('[space-3d] 初始化失败', e)
        this.errorMsg = this.$t('petHub3d.sceneInitFailed') + (e.message || e)
        this.loading = false
      }
    },

    /**
     * 测量「一层净高」与地板面高度，作为模型归一化的锚点。
     *
     * 为什么不能直接用全模型包围盒：
     * 带阁楼/挑高的户型，全 bbox 会把二层屋顶一起算进去。
     * 实测 home.glb：全 bbox 净高 8.978，但真实一层净高只有 3.270
     * （BackFascia y[0, 3.270] 是主楼板+背墙，BackRoofPanel y[3.08, 4.60] 是阁楼）。
     * 用 8.978 做锚点会把一层压到 1.04m，家具挤成一团，人物直接卡死在床垫里。
     *
     * 判据（不依赖具体模型名，换模型也能用）：
     * 1. 收集每个 mesh 的世界 AABB
     * 2. 找出所有「水平投影面积足够大 + 竖向跨度大」的体块 —— 建筑主体必然是最大的几个
     * 3. 在这些候选里，取「底面贴近地面（y_min ≈ 0）且顶面最高」的那个作为一层天花
     * 4. 其 y_min 即地板面高度（地板顶面，而非结构底面）
     *
     * @param {THREE.Object3D} model 已加载的模型根节点
     * @param {THREE.Box3} fullBox 全模型包围盒（用于取最大水平尺寸做面积阈值）
     * @returns {{floorHeight:number, floorY:number, floorAnchorName:string}}
     */
    measureGroundFloor(model, fullBox) {
      const fullSize = fullBox.getSize(new THREE.Vector3())
      const footprint = fullSize.x * fullSize.z
      // 水平投影至少占全模型 35% 才算"建筑主体"，家具/装饰会被排除掉
      const minFootprint = footprint * 0.35
      // 【本轮修复的核心】地板必须"贴地"。
      // 判据漏了这一条 → 挑中的是天花板板 BackRoofPanel（顶面最高）而不是地板 BackFascia，
      // 拿屋顶板的厚度(1.523)当层高 → scale 算出 1.838 而非 0.856，
      // 模型被放大 2.1 倍（房间 6.94m 变 14.90m），
      // 且地板落到 y=-5.661，人物脚踩在 y=0 的空中 —— 就是"人物没在地上"。
      // 原始几何：BackFascia y[0, 3.27] 地板+背墙 / BackRoofPanel y[3.08, 4.60] 天花板板。
      // 地面在 raw y=0，所以"底面贴地"= 底面落在全 bbox 下沿附近。
      const groundBand = fullSize.y * 0.06

      let best = null
      const consider = (name, b) => {
        const sx = b.max.x - b.min.x
        const sz = b.max.z - b.min.z
        if (sx * sz < minFootprint) return
        // 竖向跨度至少要像一面墙/一层楼（相对全高的 30%）
        if (b.max.y - b.min.y < fullSize.y * 0.3) return
        // 底面必须贴地：这一条把天花板板 / 屋顶结构排除在外。
        // 家具若也满足（贴地 + 面积大 + 高），再用「顶面最高」在候选里挑，
        // 此时挑中的必然是真正包住一层的那个主体。
        if (b.min.y > fullBox.min.y + groundBand) return
        if (!best || b.max.y > best.box.max.y) {
          best = { name, box: b }
        }
      }

      model.traverse((child) => {
        if (child.isMesh && child.geometry) {
          consider(
            child.name || child.parent?.name || 'unnamed',
            new THREE.Box3().setFromObject(child),
          )
        }
      })

      // 兜底：没筛出主体就用全 bbox 的下三分之一
      if (!best) {
        const floorY = fullBox.min.y + fullSize.y * 0.02
        return {
          floorHeight: fullBox.max.y - floorY,
          floorY,
          floorAnchorName: 'fallback:fullBBox',
        }
      }

      // 地板面 = 该主体的 y_min（模型里地板顶面即 y=0 处）
      const floorY = best.box.min.y
      return {
        floorHeight: best.box.max.y - floorY,
        floorY,
        floorAnchorName: best.name,
      }
    },

    /**
     * 从模型几何反推「室内可行走矩形」。
     *
     * 背景：地板/背墙这个 mesh 常常比房间本体大得多 —— 它会连着阳台、
     * 平台一起建出来。实测 home.glb：
     *   BackFascia     z[-3.64, 3.64]  y[0.000, 2.800] ← 地板+背墙，一个 mesh 覆盖全模型
     *   BackRoofPanel  z[-3.64, 1.33]  y[2.637, 3.942] ← 真正的天花板（阁楼板）
     *   FrontWall      z[ 0.90, 1.07]  y[0.000, 2.570] ← 室内隔墙
     *   室内实际 z[-3.64, 0.90]；z > 1.33 连屋顶都没有，就是室外阳台。
     * 玩家看到「人物站在屋顶上」，实际是站到了室外，头顶前方悬着阁楼板。
     *
     * 【本轮换掉「家具密度判内外」的理由】
     * 上一轮用「隔断墙两侧家具数量多的一侧算室内」。实测 FrontWall 负 Z 侧
     * 11 个家具、正 Z 侧 0 个，看起来对，但这个判据太脆：
     * 任何一个 mesh 的包围盒被 three.js 算得略有差异（顶点剔除、LOD、
     * 材质组拆分），计数就可能翻转，室内 outdoor 直接对调 —— 症状就是
     * 「人物一进页面就在房间外面」，且极难复现。
     *
     * 【现在的主判据：天花板覆盖范围】
     * 室内 = 有屋顶的那块地面。这是物理事实，不依赖任何脆弱的统计量：
     * 1. 找「屋顶」：水平投影 ≥ 全模型 35%、底面高于层高 60%、自身厚度不超层高
     *    —— 排除地板（贴地）、排除整栋楼（太高）。实测命中 BackRoofPanel。
     * 2. 用屋顶的 XZ 范围作为室内范围的初值。
     * 3. 再用「横贯房间的隔断墙」沿各轴向收窄，取室内更宽的一侧。
     *    墙体判据：薄（<0.45m）且长边 ≥ max(0.5m, 当前跨度 × 55%)
     *    —— 后半条是关键：WallFillRight 只有 0.19m 宽，是门边补片不是墙，
     *    上一轮把它当 X 墙把房间切成了半边。
     *
     * @param {THREE.Object3D} model
     * @param {THREE.Box3} finalBox 缩放后的全模型包围盒
     * @param {number} scale 当前缩放比
     * @returns {{minX:number,maxX:number,minZ:number,maxZ:number,source:string,walls:string[]}}
     */
    detectIndoorBounds(model, finalBox, scale) {
      const roomX = finalBox.max.x - finalBox.min.x
      const roomZ = finalBox.max.z - finalBox.min.z
      const tmp = new THREE.Box3()

      const result = {
        minX: finalBox.min.x,
        maxX: finalBox.max.x,
        minZ: finalBox.min.z,
        maxZ: finalBox.max.z,
        source: 'fullBox',
        walls: [],
      }

      // ---- 第 1 步：找天花板 ----
      let roof = null
      model.traverse((child) => {
        if (!child.isMesh || !child.geometry) return
        tmp.setFromObject(child)
        const sx = tmp.max.x - tmp.min.x
        const sy = tmp.max.y - tmp.min.y
        const sz = tmp.max.z - tmp.min.z
        if (sx * sz < roomX * roomZ * 0.35) return // 必须是"大板"
        if (tmp.min.y < TARGET_CEILING_HEIGHT * 0.6) return // 必须在头顶上方（排除地板）
        if (sy > TARGET_CEILING_HEIGHT) return // 不能是整栋楼（排除 BackFascia）
        const area = sx * sz
        if (!roof || area > roof.area) roof = { name: child.name, box: tmp.clone(), area }
      })

      const src = []
      if (roof) {
        result.minX = Math.max(roof.box.min.x, finalBox.min.x)
        result.maxX = Math.min(roof.box.max.x, finalBox.max.x)
        result.minZ = Math.max(roof.box.min.z, finalBox.min.z)
        result.maxZ = Math.min(roof.box.max.z, finalBox.max.z)
        src.push('roof:' + roof.name)
      }

      // ---- 第 2 步：沿各轴向用隔断墙收窄，取家具更多的一侧为室内 ----
      // AXIS: 0 = X, 2 = Z。OTHER[axis] 给出「另一个水平轴」，
      // 不要用 2 - axis / 3 - axis 这类算术 —— 写错一位就会让
      // 「墙的长度」误取成「墙的高度」，结果所有 Z 向墙被静默筛掉，
      // 室内范围退回屋顶边界，人物又走到室外。
      const AXIS = { X: 0, Z: 2 }
      const OTHER = [2, 1, 0]

      const splitAt = (axis) => {
        const lo = axis === AXIS.X ? result.minX : result.minZ
        const hi = axis === AXIS.X ? result.maxX : result.maxZ
        const span = axis === AXIS.X ? result.maxZ - result.minZ : result.maxX - result.minX
        if (span <= 0) return
        // 墙必须在轴向上薄、在另一个水平轴上横贯当前跨度
        const minSpanLen = Math.max(WALL_MIN_WIDTH, span * WALL_SPAN_RATIO)
        let best = null
        model.traverse((child) => {
          if (!child.isMesh || !child.geometry) return
          tmp.setFromObject(child)
          const s = [tmp.max.x - tmp.min.x, tmp.max.y - tmp.min.y, tmp.max.z - tmp.min.z]
          if (s[axis] >= 0.45) return
          if (s[OTHER[axis]] < minSpanLen) return
          if (s[1] < 1.2 || tmp.max.y < TARGET_CEILING_HEIGHT * 0.55) return
          // 必须严格位于区间内部（贴边的是地板/背墙）
          const wMin = axis === AXIS.X ? tmp.min.x : tmp.min.z
          const wMax = axis === AXIS.X ? tmp.max.x : tmp.max.z
          if (wMin <= lo || wMax >= hi) return
          const near = wMin - lo
          const far = hi - wMax
          if (!best || Math.max(near, far) > Math.max(best.near, best.far)) {
            best = { near, far, min: wMin, max: wMax, name: child.name }
          }
        })
        if (!best) return

        // 墙两侧比家具数量，多的一侧是室内
        const part = (best.min + best.max) / 2
        const wallSet = new Set()
        model.traverse((child) => {
          if (!child.isMesh || !child.geometry) return
          tmp.setFromObject(child)
          const s = [tmp.max.x - tmp.min.x, tmp.max.y - tmp.min.y, tmp.max.z - tmp.min.z]
          // 薄且高的 = 墙，参与计数排除
          if (Math.min(s[0], s[2]) < 0.45 && s[1] > 1.0) wallSet.add(child.name)
        })
        let nLo = 0
        let nHi = 0
        model.traverse((child) => {
          if (!child.isMesh || !child.geometry || wallSet.has(child.name)) return
          tmp.setFromObject(child)
          const s = [tmp.max.x - tmp.min.x, tmp.max.y - tmp.min.y, tmp.max.z - tmp.min.z]
          if (s[0] > roomX * 0.85 && s[2] > roomZ * 0.85) return // 地板
          if (Math.min(s[0], s[1], s[2]) < 0.25 || s[1] < 0.3) return // 薄片/贴地物
          const mid = (axis === AXIS.X ? tmp.min.x + tmp.max.x : tmp.min.z + tmp.max.z) / 2
          if (mid < part) nLo++
          else nHi++
        })
        if (nLo === nHi) return // 无区分度，不动
        // keepLo = 低坐标侧家具更多 = 室内在低坐标侧 → 边界取墙的低坐标面
        const keepLo = nLo > nHi
        if (axis === AXIS.X) {
          if (keepLo) result.maxX = best.min
          else result.minX = best.max
          src.push((keepLo ? 'X-' : 'X+') + best.name)
        } else {
          if (keepLo) result.maxZ = best.min
          else result.minZ = best.max
          src.push((keepLo ? 'Z-' : 'Z+') + best.name)
        }
        result.walls.push(best.name)
      }

      splitAt(AXIS.Z)
      splitAt(AXIS.X)

      const w = result.maxX - result.minX
      const d = result.maxZ - result.minZ
      if (src.length === 0 || w < 2 || d < 2) return result

      result.source = `${src.join('|')} scale=${scale.toFixed(3)}`
      return result
    },

    /**
     * 加载 GLB 模型 + 加载完后把相机定位到门口
     * 模型远程地址,通过 ensureLocalModel 走"远程下载 + 本地缓存"
     */
    async loadModel(renderer, scene) {
      this.loadingText = this.$t('petHub3d.loadingModel')
      const modelUrl = '/static/models/home.glb'
      let localUrl
      try {
        localUrl = await ensureLocalModel(modelUrl, (p) => {
          this.progress = p
          this.loadingText = this.$t('petHub3d.downloading', { p })
        })
      } catch (e) {
        console.error('[space-3d] 远程模型下载失败', e)
        this.errorMsg = this.$t('petHub3d.modelDownloadFailed')
        this.loading = false
        return
      }
      // GLTFLoader 是单独加载的，不能从 THREE 取
      const loader = new GLTFLoader()
      // home.glb 是 Draco 压缩的 GLB，必须挂 DRACOLoader 才能解析。
      // 旧实现把解码器写死成 jsDelivr CDN —— 内网 / 离线 / CDN 不可达时 404，
      // 表面症状是"模型加载失败"，真实原因却是解码器取不到。
      // 现在统一交给 _loadGltfWithDracoFallback：本地 static 优先，CDN 兜底。
      this._loadGltfWithDracoFallback(loader, localUrl, {
        onProgress: (xhr) => {
          if (xhr.lengthComputable) {
            // 模型文件已落到本地,这里仅 GLTFLoader 解析阶段的进度,合并到 50~99%
            const ratio = xhr.loaded / xhr.total
            this.progress = Math.round(50 + ratio * 49)
            this.loadingText = this.$t('petHub3d.parsing', { progress: this.progress })
          }
        },
        onError: (err) => {
          console.error('[space-3d] 模型加载失败', err)
          if (this.three._disposed || !this.three.scene) return
          this.errorMsg = this.$t('petHub3d.modelLoadFailed')
          this.loading = false
        },
        onLoaded: (gltf) => {
          // 异步回调期间用户可能已返回页面,scene 已置 null,
          // 直接 return 避免对已 dispose 的资源继续操作触发 warn
          if (this.three._disposed || !this.three.scene) return
          const model = gltf.scene

          // 1. 缩放：以「一层净高」为唯一锚点
          //
          // 这一版 home.glb 是带阁楼的双层结构，各mesh 原始世界包围盒：
          //   BackFascia（地板+背墙） y[0.000, 3.270]  ← 一层，真实净高在这里
          //   BackRoofPanel（阁楼）  y[3.080, 4.603]
          //   Mesh_0（挑高/楼梯）    y[0.939, 5.355]
          // 全模型 bbox 因此是 y[-2.996, 5.982]（净高 8.978）。
          //
          // 【踩过的坑】早期两个版本都算错：
          //   v1 `12 / maxAxis`      → 拿最长边当基准，层高 9.47m，人像仓库
          //   v2 `2.8 / 全bbox净高` → 层高 8.978 含阁楼，一层被压成 1.04m，
          //                           家具全挤在一起，96% 地面被判定不可走，
          //                           人物一出生就卡在床垫里 → 原地踏步
          // 正确做法：找出「从地面起、直抵一层天花板」的那层楼高，
          // 用它做锚点。通用判据：取所有 mesh 中「底面贴地(y_min≈0)且
          // 顶面最高」的那一个（即主楼板/背墙），它的 sy 就是一层净高。
          const box = new THREE.Box3().setFromObject(model)
          const size = box.getSize(new THREE.Vector3())
          const center = box.getCenter(new THREE.Vector3())

          const metrics = this.measureGroundFloor(model, box)
          const rawHeight = metrics.floorHeight
          const scale = TARGET_CEILING_HEIGHT / Math.max(rawHeight, 0.001)
          model.scale.setScalar(scale)

          // 调试：输出原始包围盒 + 一层净高锚点
          // eslint-disable-next-line
          console.log('[space-3d] 原始模型包围盒', {
            min: { x: box.min.x, y: box.min.y, z: box.min.z },
            max: { x: box.max.x, y: box.max.y, z: box.max.z },
            size: { x: size.x, y: size.y, z: size.z },
            floorAnchor: metrics.floorAnchorName,
            floorHeight: rawHeight,
            scale,
          })

          // 2. 居中 + 落地：把「地板面」(y=0)对齐到世界 y=0
          //    注意这里用metrics.floorY（地板顶面）而不是 box.min.y，
          //    因为 box.min.y 会把家具腿/下沉结构算进来，导致整体悬空。
          const floorOffset = -metrics.floorY * scale
          model.position.x = -center.x * scale
          model.position.z = -center.z * scale
          model.position.y = floorOffset

          // 3. 包围盒（缩放后）
          const finalBox = new THREE.Box3().setFromObject(model)
          const finalSize = finalBox.getSize(new THREE.Vector3())
          const finalCenter = finalBox.getCenter(new THREE.Vector3())

          // 3. 碰撞体
          // 3.1 先算出「室内可行走范围」，再建立碰撞体。
          //     为什么不能用全 bbox 当 bounds：
          //     BackFascia(地板+背墙) 横跨 x[-3.47,3.47] z[-3.64,3.64]，
          //     但真正的室内只到 FrontWall(内墙) z≈0.90 就为止，
          //     z>0.90 是室外阳台/平台。地板网格把室外也盖住了，
          //     若直接用 bbox 做边界，人物走两步就越过内墙跑到室外平台上，
          //     视觉上就是「人物站在房间外面」。
          //     所以这里从墙体 mesh 反推真实的室内矩形。
          const indoor = this.detectIndoorBounds(model, finalBox, scale)

          // 4. 加入场景
          const group = new THREE.Group()
          group.add(model)
          scene.add(group)
          // 强制刷新世界矩阵,buildColliders 里 setFromObject 才拿得到正确的世界 AABB
          group.updateMatrixWorld(true)
          this.three.modelGroup = group
          this.three.modelBox = finalBox
          this.three.modelCenter = finalCenter
          this.three.modelSize = finalSize

          // 4.5 建立碰撞体:遍历所有 mesh 的世界包围盒,供人物移动做沿墙滑动检测
          this.buildColliders(model, indoor)

          // 5. 移动边界 = 室内范围（不是全 bbox）。
          //    必须在这里设置 —— findSafeStartPosition 依赖 bounds 做越界判断，
          //    顺序颠倒会导致搜索时不看边界，可能把人物放到室外。
          //    y 不放进 bounds：人物高度由碰撞检测的垂直区间负责，
          //    把 y 塞进 bounds 会导致 TPS 相机高度被误钳制。
          this.three.bounds = {
            minX: indoor.minX + FIGURE_RADIUS,
            maxX: indoor.maxX - FIGURE_RADIUS,
            minZ: indoor.minZ + FIGURE_RADIUS,
            maxZ: indoor.maxZ - FIGURE_RADIUS,
          }

          // 6. 初始位置：站在室内靠后（背墙一侧）的开阔处。
          //    不再用 (0,0) —— 原点在房间几何中心，而室内实际是
          //    z∈[-3.64, 0.90]（FrontWall 是内墙），(0,0) 离内墙只有 0.9m，
          //    往前走两步就出室内了。这里取室内矩形中心，并避开室外那一侧。
          const centerX = (indoor.minX + indoor.maxX) / 2
          const centerZ = (indoor.minZ + indoor.maxZ) / 2
          const startPos = new THREE.Vector3(centerX, 0, centerZ)
          // 6.1 碰撞兜底——若起始位置正好卡在家具/墙内部,applyMovement 的滑动逻辑全部失败,
          //     人物会"原地踏步"但位置不动。这里自动找一个最近的安全位置。
          const safe = this.findSafeStartPosition(startPos.x, startPos.z)
          // eslint-disable-next-line
          console.warn('[space-3d] 起始位置碰撞检查', {
            indoor: indoor.source,
            from: { x: startPos.x.toFixed(2), z: startPos.z.toFixed(2) },
            to: { x: safe.x.toFixed(2), z: safe.z.toFixed(2) },
            shifted: Math.abs(safe.x - startPos.x) > 0.001 || Math.abs(safe.z - startPos.z) > 0.001,
          })
          startPos.x = safe.x
          startPos.z = safe.z
          if (!this.three.playerPos) this.three.playerPos = new THREE.Vector3()
          this.three.playerPos.set(startPos.x, 0, startPos.z)
          // 记住出生点：resetCamera() 用它复位，避免把人物放回家具里
          this.three.spawnPoint = { x: safe.x, z: safe.z }

          // 8.5 诊断快照：把「归一化 → 室内判定 → 边界 → 出生点」这条链路的
          //     每一个中间值都记下来。真机（APP WebView）上若症状与 H5 不一致，
          //     靠这个面板读数即可定位是哪一环跑偏，不必反复打包重装。
          //     全部取运行时真实值，不做任何二次推算。
          let meshCount = 0
          model.traverse((c) => {
            if (c.isMesh) meshCount++
          })
          this.three.diag = {
            modelSource: localUrl,
            floorAnchor: metrics.floorAnchorName,
            floorHeight: metrics.floorHeight,
            floorY: metrics.floorY,
            scale,
            meshCount,
            indoor: {
              minX: indoor.minX,
              maxX: indoor.maxX,
              minZ: indoor.minZ,
              maxZ: indoor.maxZ,
              source: indoor.source,
              walls: indoor.walls,
            },
            spawn: { x: safe.x, z: safe.z },
          }

          // 缓存房间尺度（供 HUD / 调试 / 相机限位使用）
          this.three.roomHeight = TARGET_CEILING_HEIGHT
          this.three.roomHalfX = (indoor.maxX - indoor.minX) / 2
          this.three.roomHalfZ = (indoor.maxZ - indoor.minZ) / 2

          // 加载人物化身（放在人物脚下）
          // 这里仅 fire-and-forget:化身失败不应阻塞场景,内部已用 Promise.allSettled 隔离每个 fbx
          this.loadFigureAvatar().catch((e) => {
            console.error('[space-3d] 加载人物化身异常', e)
          })

          // 7. 初始朝向：由 syncFigureAvatar 每帧重设，这里不手动 lookAt

          // 8. 调试：输出缩放后的最终位置
          // eslint-disable-next-line
          console.log('[space-3d] 缩放后场景', {
            scale,
            roomSize: {
              x: finalSize.x.toFixed(2),
              y: finalSize.y.toFixed(2),
              z: finalSize.z.toFixed(2),
            },
            indoorBounds: indoor.source,
            indoorSize: {
              x: (indoor.maxX - indoor.minX).toFixed(2),
              z: (indoor.maxZ - indoor.minZ).toFixed(2),
            },
            startPos: { x: startPos.x.toFixed(2), z: startPos.z.toFixed(2) },
          })

          this.positionText = `${startPos.x.toFixed(1)}, 0.0, ${startPos.z.toFixed(1)}`
          this.loading = false
        },
      })
    },

    /**
     * 用 GLTFLoader 加载 Draco 压缩模型，解码器优先本地 static，失败退 CDN。
     *
     * 为什么要兜底：DRACOLoader 内部用 fetch 拉解码器，失败时只在 console 留一条
     * warning，真正的异常会等到 loader.load 的 onError 才暴露，而错误信息是
     * 泛化的 "THREE.GLTFLoader: Missing minVersion..."，很难定位。
     * 这里显式做一次本地→CDN 的重试，把"解码器取不到"从"模型加载失败"里分离出来。
     *
     * @param {object} loader 已 setDRACOLoader 的 GLTFLoader
     * @param {string} url 本地/Blob URL
     * @param {{onLoaded:Function,onProgress:Function,onError:Function}} handlers 回调
     */
    _loadGltfWithDracoFallback(loader, url, handlers) {
      const { onLoaded, onProgress, onError } = handlers
      let retriedWithCdn = false

      const attempt = (decoderPath) => {
        const dracoLoader = new DRACOLoader()
        dracoLoader.setDecoderPath(decoderPath)
        loader.setDRACOLoader(dracoLoader)

        loader.load(
          url,
          (gltf) => {
            // 解码器加载完就释放 worker，避免长驻内存
            try {
              dracoLoader.dispose()
            } catch (e) {
              /* ignore */
            }
            onLoaded(gltf)
          },
          onProgress,
          (err) => {
            // 第一次失败且用的是本地路径 → 换 CDN 再试一次
            if (!retriedWithCdn && decoderPath === DRACO_LOCAL_PATH) {
              retriedWithCdn = true
              console.warn('[space-3d] 本地 Draco 解码器加载失败，回退 CDN', err)
              // 重新挂一个 CDN 路径的解码器
              const cdnDraco = new DRACOLoader()
              cdnDraco.setDecoderPath(DRACO_CDN_PATH)
              loader.setDRACOLoader(cdnDraco)
              loader.load(url, onLoaded, onProgress, onError)
              return
            }
            onError(err)
          },
        )
      }

      attempt(DRACO_LOCAL_PATH)
    },

    /**
     * 键盘绑定：WASD/方向键控制移动
     * 重复进入 startScene(document 重试 / canvas DOM 重试)时,已绑定的监听器不再重复注册,
     * 避免多个回调同时触发导致视角旋转/按键响应翻倍
     */
    bindKeyboard() {
      const t = this.three
      // 已绑定过则跳过:防止 startScene 重入导致多个 keydown/touchstart 监听器并存
      // (上一轮 bindKeyboard 创建的 _onKeyDown / _onWindowTouchStart 仍是同一个引用,
      //  但每次都 addEventListener 会导致同一事件触发 N 次回调)
      if (t._keyboardBound) return
      t._keyboardBound = true
      this._onKeyDown = (e) => {
        const k = e.key
        if (k in t.keys) {
          t.keys[k] = true
          e.preventDefault()
        }
      }
      this._onKeyUp = (e) => {
        const k = e.key
        if (k in t.keys) {
          t.keys[k] = false
          e.preventDefault()
        }
      }
      window.addEventListener('keydown', this._onKeyDown)
      window.addEventListener('keyup', this._onKeyUp)

      // 全局触摸事件：直接在 window 监听，绕开 canvas-wrap 被 HUD 遮挡的问题
      // 用 isInControlArea() 区分摇杆/按钮区
      this._onWindowTouchStart = (e) => {
        // 仅在场景未就绪时拒绝;loading=true 但相机已就绪也允许响应(支持边加载边旋转)
        if (!t.camera || !t.scene) return
        // 找到第一个不在摇杆/按钮区的 touch
        for (const touch of e.touches) {
          if (this.isInControlArea(touch.clientX, touch.clientY)) continue
          t.lookTouch.active = true
          t.lookTouch.touchId = touch.identifier
          t.lookTouch.lastX = touch.clientX
          t.lookTouch.lastY = touch.clientY
          // 阻止默认行为，避免 h5 页面把 touch 抢去做滚动/下拉刷新
          e.preventDefault()
          break
        }
      }
      this._onWindowTouchMove = (e) => {
        // 【关键修复】touchId 可能是 0 —— 浏览器里第一根手指的 identifier 就是 0。
        // 旧代码写 `!t.lookTouch.touchId`，而 `!0 === true`，
        // 于是单指滑动时每个 touchmove 都在这一行被 return 掉，
        // yaw 从来没被累加过 → "滑动屏幕视野完全没变化"。
        // 现在统一用 null 作为"无手指"语义。
        if (!t.lookTouch.active || t.lookTouch.touchId === null) return
        const touch = Array.from(e.touches).find((tt) => tt.identifier === t.lookTouch.touchId)
        if (!touch) return
        const dx = touch.clientX - t.lookTouch.lastX
        const dy = touch.clientY - t.lookTouch.lastY
        t.lookTouch.lastX = touch.clientX
        t.lookTouch.lastY = touch.clientY
        t.yaw += dx * t.lookSensitivity
        t.pitch += dy * t.lookSensitivity
        const halfPi = Math.PI / 2
        if (t.pitch > halfPi) t.pitch = halfPi
        if (t.pitch < -halfPi) t.pitch = -halfPi
        // 持续 preventDefault，保证拖动期间不会触发页面滚动
        e.preventDefault()
      }
      this._onWindowTouchEnd = (e) => {
        if (t.lookTouch.touchId === null) return
        // 优先看 changedTouches:本次松手的手指是不是 lookTouch.touchId
        let isLookTouchLeaving = false
        if (e && e.changedTouches) {
          for (const touch of e.changedTouches) {
            if (touch.identifier === t.lookTouch.touchId) {
              isLookTouchLeaving = true
              break
            }
          }
        }
        // fallback:touches 里不再有该 touchId 也视为已松开
        if (!isLookTouchLeaving && e && e.touches) {
          let stillActive = false
          for (const touch of e.touches) {
            if (touch.identifier === t.lookTouch.touchId) {
              stillActive = true
              break
            }
          }
          isLookTouchLeaving = !stillActive
        }
        if (isLookTouchLeaving) {
          t.lookTouch.active = false
          t.lookTouch.touchId = null
        }
      }
      // 移动端 h5：必须在 document 上非 passive + capture 阶段注册，
      // 这样能抢在 uni-app 页面级 touchmove 监听器之前 preventDefault，
      // 阻止页面滚动/下拉刷新吃事件，导致 window/document 后续收不到完整事件序列
      // 守卫:bindKeyboard 重入时这里已注册过,跳过避免重复
      if (!t._touchBound) {
        t._touchBound = true
        document.addEventListener('touchstart', this._onWindowTouchStart, {
          passive: false,
          capture: true,
        })
        document.addEventListener('touchmove', this._onWindowTouchMove, {
          passive: false,
          capture: true,
        })
        document.addEventListener('touchend', this._onWindowTouchEnd, {
          passive: false,
          capture: true,
        })
        document.addEventListener('touchcancel', this._onWindowTouchEnd, {
          passive: false,
          capture: true,
        })
      }
    },

    unbindKeyboard() {
      if (this._onKeyDown) window.removeEventListener('keydown', this._onKeyDown)
      if (this._onKeyUp) window.removeEventListener('keyup', this._onKeyUp)
      if (this._onWindowTouchStart)
        document.removeEventListener('touchstart', this._onWindowTouchStart, { capture: true })
      if (this._onWindowTouchMove)
        document.removeEventListener('touchmove', this._onWindowTouchMove, { capture: true })
      if (this._onWindowTouchEnd)
        document.removeEventListener('touchend', this._onWindowTouchEnd, { capture: true })
      if (this._onWindowTouchEnd)
        document.removeEventListener('touchcancel', this._onWindowTouchEnd, { capture: true })
    },

    /**
     * 渲染循环
     */
    animate() {
      this.three.animationId = requestAnimationFrame(() => this.animate())

      // 计算帧间隔：移动速度必须按秒积分，否则在 120Hz 屏幕上会走得比 60Hz 快一倍。
      // 上限 0.05s：切后台再回来时 delta 可能高达数秒，积分一次就穿墙了
      const now = performance.now()
      const t = this.three
      const rawDelta = t.lastFrameTime ? (now - t.lastFrameTime) / 1000 : 1 / 60
      t.lastFrameTime = now
      const delta = Math.min(rawDelta, 0.05)
      // 【本轮修复】渲染循环算出的 delta 存起来给动画 mixer 用。
      // 旧实现里 mixer.update() 用的是 figureWalkClock.getDelta()，
      // 那是个**独立的 Clock**，它的采样时机和渲染循环对不上：
      //   - syncFigureState()（会 stop/pause action）由 applyMovement 调用，
      //     发生在 syncFigureAvatar() 之前；
      //   - 第一次调用 getDelta() 时距上次可能隔了整整一帧甚至多帧，
      //     返回值是个巨大的间隔 → 动作猛跳一下。
      // 复用渲染循环的 delta，保证动画与移动、渲染三者严格同频，
      // 这也是消除"走走停停"的关键一环。
      t.frameDelta = delta

      this.applyMovement(delta)
      // applyLook 只在 FPS 模式生效：TPS 由 syncFigureAvatar 用 yaw+pitch 直接摆相机
      if (this.viewMode === 'fps') {
        this.applyLook()
      }
      this.syncFigureAvatar() // 同步人物化身位置/朝向 + TPS 相机跟随
      // PointerLockControls 没有 update() 方法（事件自动处理旋转），所以这里什么都不调用
      if (this.three.renderer && this.three.scene && this.three.camera) {
        this.three.renderer.render(this.three.scene, this.three.camera)
      }
    },

    /**
     * 把累计的 yaw/pitch 应用到 camera（实现触摸拖动看视角）
     * 使用 euler.setFromQuaternion 防止万向锁
     */
    applyLook() {
      const t = this.three
      if (!t.camera) return
      // 复用 quaternion/euler 实例,避免每帧 new 触发 GC
      // 60fps 下每秒会创建 120 个临时对象,不复用会导致短时 GC 停顿卡顿
      if (!t._lookQuat) {
        t._lookQuat = new THREE.Quaternion()
        t._lookEuler = new THREE.Euler(0, 0, 0, 'YXZ')
      }
      // YXZ 顺序:先 yaw 再 pitch,符合 FPS 习惯
      t._lookEuler.set(t.pitch, t.yaw, 0, 'YXZ')
      t._lookQuat.setFromEuler(t._lookEuler)
      t.camera.quaternion.copy(t._lookQuat)
    },

    /**
     * 根据键盘 + 摇杆状态移动人物（第一人称/第三人称共用同一套输入）
     *
     * 坐标系约定：
     * - t.playerPos 是权威的「人物脚下位置」，永远存地面坐标 y=0
     * - FPS：camera.position = playerPos + (0, EYE_HEIGHT, 0)
     * - TPS：camera.position 由 syncFigureAvatar 放到人物身后，pitch 只影响观察点高度
     *
     * 碰撞用「分轴滑动」：先试 X 再试 Z，两个方向独立判定。
     * 这样斜着撞墙时能沿墙滑走，而不是像旧实现那样直接定住（moveVec 归零），
     * 玩家会感觉「撞到一堵无形的墙卡死」。
     */
    applyMovement(delta) {
      const t = this.three
      // 注意：不要用 t.controls 做守卫。PointerLockControls 只在 H5 桌面可用，
      // APP webview 下 lock() 会失败，若把 controls 当"场景就绪"标志，
      // 一旦 controls 创建失败整个移动逻辑就永久失效。
      // 真正的就绪标志是 playerPos（模型加载完成时才创建）。
      if (!t.playerPos || !t.camera) return
      const pos = t.playerPos

      // 1. 计算归一化的前进/横向输入 [-1, 1]
      let forward = 0
      let rightward = 0
      if (t.keys.w || t.keys.ArrowUp) forward += 1
      if (t.keys.s || t.keys.ArrowDown) forward -= 1
      if (t.keys.a || t.keys.ArrowLeft) rightward -= 1
      if (t.keys.d || t.keys.ArrowRight) rightward += 1

      // 摇杆（覆盖键盘输入，摇杆优先级高）
      if (t.joystick.active) {
        // 摇杆：x 是横向，y 是纵向（y 向上为负 = 前进）
        rightward = t.joystick.x
        forward = -t.joystick.y
      }

      // 【本轮修复的核心：加入死区，消除动画状态抖动】
      // 旧判断是 `moving = forward !== 0 || rightward !== 0` —— 严格等于零才算静止。
      // 但手指按住摇杆时坐标几乎不可能精确停在 0：
      //   1. 起步/收尾必然穿过 0
      //   2. 触屏本身有 ±1~2% 的硬件抖动
      // 于是 figureState 在 walk ↔ idle 之间高频翻转，而每次切回 walk 都会
      // 执行 figureWalkAction.reset() 把动画打回第 0 帧 —— 人物看起来就是
      // 「每走一步就重新起步一次」，即用户反馈的"明显顿挫感"。
      //
      // 用死区（deadzone）过滤：低于阈值视为静止。
      // 8% 是个平衡点 —— 足够盖住触屏硬件抖动（实测 ±1~2%），
      // 又不会让玩家觉得"推一点点没反应"（走到 8% 就已经 0.13 的输入量，
      // 配合下面的重映射后速度已经接近全速）。
      const inputMag = Math.hypot(forward, rightward)
      if (inputMag < JOYSTICK_DEADZONE) {
        forward = 0
        rightward = 0
      } else {
        // 死区外做重映射：把 [deadzone, 1] 拉伸到 [0, 1]，
        // 否则死区边界会出现速度突变（0 → 0.08 的跳变）。
        // 顺带让低速段更好控制，走路动画的起步也更自然。
        const scaled = Math.min(1, (inputMag - JOYSTICK_DEADZONE) / (1 - JOYSTICK_DEADZONE))
        const k = scaled / inputMag
        forward *= k
        rightward *= k
      }

      // === 化身姿态:有输入 → 走路,无输入 → 静态 ===
      const moving = forward !== 0 || rightward !== 0
      const newState = moving ? 'walk' : 'idle'
      if (t.figureState !== newState) {
        t.figureState = newState
        this.syncFigureState()
      }

      // 移动强度 0~1 供走路动画调节播放速率。
      // 旧实现里动画永远 1.0 倍速：推摇杆走 1/4 速度时腿也在全速蹬地，
      // 视觉上就是"身体滑行 + 腿乱蹬"，主观上同样表现为顿挫/怪异。
      // 让动画速率跟随实际移动强度，慢走小碎步、快走大步幅，才对得上。
      t.walkSpeed01 = moving ? Math.min(1, inputMag) : 0

      // FPS 模式：相机就是人物本体，直接把视高写进 camera.position
      if (this.viewMode === 'fps') {
        t.camera.position.set(pos.x, EYE_HEIGHT, pos.z)
      }

      if (!moving) return

      // 2. 取当前朝向向量（在 XZ 平面投影，避免上下飞）
      if (!t._moveVecs) {
        t._moveVecs = {
          direction: new THREE.Vector3(),
          right: new THREE.Vector3(),
          up: new THREE.Vector3(0, 1, 0),
          moveVec: new THREE.Vector3(),
        }
      }
      const { direction, right: rightVec, up, moveVec } = t._moveVecs
      direction.set(-Math.sin(t.yaw), 0, -Math.cos(t.yaw))
      direction.normalize()

      // 3. right 向量 = direction × up
      rightVec.crossVectors(direction, up).normalize()

      // 4. 计算本帧位移：速度(m/s) × delta(s)
      //    旧实现是固定 0.05/帧，在 120Hz 屏幕上速度是 60Hz 的两倍，且高刷屏更快
      const speed = t.moveSpeed * delta
      moveVec.set(0, 0, 0)
      moveVec.addScaledVector(direction, forward * speed)
      moveVec.addScaledVector(rightVec, rightward * speed)

      // 5. 分轴滑动碰撞
      //    X 与 Z 分开试：撞墙时至少有一个轴能通过 → 沿墙滑行而非卡死。
      //    两个轴都被挡时 pos 完全不变，人物自然停住，不会抖动。
      const tryX = pos.x + moveVec.x
      const tryZ = pos.z + moveVec.z
      if (!this.figureCollidesAt(tryX, pos.z)) pos.x = tryX
      if (!this.figureCollidesAt(pos.x, tryZ)) pos.z = tryZ

      // 6. 边界限制（与碰撞体互为补充：碰撞体管室内家具，边界管模型外围）
      if (t.bounds) {
        pos.x = Math.max(t.bounds.minX, Math.min(t.bounds.maxX, pos.x))
        pos.z = Math.max(t.bounds.minZ, Math.min(t.bounds.maxZ, pos.z))
      }

      // 7. 更新 HUD 位置 - 节流到 200ms,避免每帧触发 Vue 响应式更新整页重渲染
      //    之前每帧都赋值 reactive 变量 → Vue 60fps 重排 → H5 页面卡顿 / 触摸延迟
      t._lastHudUpdate = t._lastHudUpdate || 0
      const now = Date.now()
      if (now - t._lastHudUpdate > 200) {
        t._lastHudUpdate = now
        this.positionText = `${pos.x.toFixed(1)}, 0.0, ${pos.z.toFixed(1)}`
      }
    },

    onResize() {
      const canvasEl = document.getElementById(this.canvasId)
      if (!canvasEl || !this.three.renderer || !this.three.camera) return
      const w = canvasEl.clientWidth
      const h = canvasEl.clientHeight
      this.three.renderer.setSize(w, h)
      this.three.camera.aspect = w / h
      this.three.camera.updateProjectionMatrix()
    },

    /**
     * 重置：人物回到出生点，视角回到正前方
     * 注意不能用 (0,0)：室内矩形中心不一定是可行走空地，
     * 用它做复位目标会把人物放回家具里，看起来像"卡住"。
     */
    resetCamera() {
      const t = this.three
      if (!t.playerPos) return
      // 复位到出生点（模型加载时标定过必定无碰撞的那个位置）
      const spawn = t.spawnPoint || { x: 0, z: 0 }
      const safe = this.findSafeStartPosition(spawn.x, spawn.z)
      t.playerPos.set(safe.x, 0, safe.z)
      t.yaw = 0
      t.pitch = 0
      // 相机位置由 applyMovement(FPS) / syncFigureAvatar(TPS) 每帧重写，这里不用手动摆
    },

    /**
     * 切换线框
     */
    toggleWireframe() {
      this.wireframe = !this.wireframe
      if (!this.three.modelGroup) return
      this.three.modelGroup.traverse((child) => {
        if (child.isMesh && child.material) {
          const mats = Array.isArray(child.material) ? child.material : [child.material]
          mats.forEach((m) => {
            m.wireframe = this.wireframe
          })
        }
      })
    },

    // ============== canvas-wrap 本地触摸监听 ==============
    // 只处理 PointerLock 触发；视角旋转的 lookTouch 指派/move/end 由全局 capture 监听统一处理,
    // 这样只有一处更新 yaw/pitch,避免元素冒泡 + capture 同时跑导致视角速度翻倍
    onCanvasTouchStart() {
      const t = this.three
      // 尝试 PointerLock（H5 桌面浏览器有效，APP webview 多半失败，会自动 catch）
      // 必须在 canvas 元素的用户手势里调用,全局 capture 不行
      if (t.controls && !this.pointerLocked) {
        try {
          t.controls.lock()
        } catch (err) {
          console.warn('PointerLock 不可用，改用触摸旋转', err)
        }
      }
    },

    /**
     * 判断 (x, y) 是否落在 UI 控制区（摇杆圆盘）
     * 摇杆位于左下 60rpx,外圈直径 240rpx；用方形兜底判断手指在盘内
     * 这里用页面坐标做简化判断（rpx 已按 750 设计 宽 折算）
     */
    isInControlArea(x, y) {
      const winW = window.innerWidth
      const winH = window.innerHeight
      // 左下摇杆：左 60rpx、底 60rpx、直径 240rpx
      const rpx2px = winW / 750
      const padL = 60 * rpx2px
      const padB = 60 * rpx2px
      const padW = 240 * rpx2px
      // 优先用真实 DOM 位置（页面缩放/安全区可能让 rpx 换算失准）
      if (typeof document !== 'undefined') {
        const el = document.querySelector('.joystick-pad')
        if (el) {
          const rect = el.getBoundingClientRect()
          if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
            return true
          }
          return false
        }
      }
      // fallback:rpx 折算
      if (x > padL && x < padL + padW && y > winH - padB - padW && y < winH - padB) return true
      return false
    },

    // ============== 方向控制摇杆 ==============
    // 摇杆外圈半径 (CSS rpx → 运行时按比例换算)
    // 触摸在 .joystick-pad 内按下时,以按下点为摇杆原点;拖动产生 (x, y) ∈ [-1, 1]
    // 输出写入 t.joystick,applyMovement 会用 joystick 输入覆盖键盘
    onJoystickStart(e) {
      const t = this.three
      // 优先取 changedTouches(本次 touchstart 新增的手指,多指时定位准确)
      // fallback 到 touches[0]:
      //  - uni-app 小程序 / APP 内嵌 webview 可能没有 changedTouches 字段
      //  - 单指场景下两者等价,fallback 不会出错
      const touch =
        (e.changedTouches && e.changedTouches[0]) ||
        (e.touches && e.touches[0]) ||
        (e.mp && e.mp.touches && e.mp.touches[0]) // 兼容 uni-app 旧事件包装
      if (!touch) return
      // 起点作为本次摇杆会话的中心点,允许玩家"自由原点"操作
      // 记录摇杆按下前的键盘 w/s 状态,end 时还原;
      // 避免"按住 W + 按摇杆 → 松摇杆后 W 状态被吞"的体验问题
      // 必须在清掉 w/s 之前记录,否则 savedKeys 永远记的是 false
      t.joystick.savedKeys = {
        w: t.keys.w,
        s: t.keys.s,
      }
      t.joystick.active = true
      t.joystick.touchId = touch.identifier
      t.joystick.originX = touch.clientX
      t.joystick.originY = touch.clientY
      t.joystick.x = 0
      t.joystick.y = 0
      // 同时清掉键盘 w/s,避免出现"摇杆向左时键盘还在向前走"
      t.keys.w = false
      t.keys.s = false
      this.joystickOffset.x = 0
      this.joystickOffset.y = 0
      this.joystickDragging = true
      // 缓存外圈半径:move 高频调用不再每次 querySelector
      this.joystickRadiusCached = this.joystickRadius()

      // 【长按唤出诊断面板】
      // 顶部图标已全部移除后,诊断面板失去了入口,改成长按摇杆 0.8 秒唤出。
      // 摇杆是画面里唯一"闲置的固定触点",放这里不遮挡视野。
      //
      // 关键:长按期间不能产生摇杆位移 —— _diagHeld 会把 joystick 强制归零,
      // 否则玩家长按时人物会持续往前走,像是误操作。
      this._diagHeld = true
      clearTimeout(this._diagTimer)
      this._diagTimer = setTimeout(() => {
        this._diagHeld = false
        // 彻底结束这次摇杆会话,避免面板弹出后手指抬起仍触发摇杆回弹
        t.joystick.active = false
        t.joystick.x = 0
        t.joystick.y = 0
        this.joystickOffset.x = 0
        this.joystickOffset.y = 0
        this.joystickDragging = false
        this.toggleDiag()
      }, DIAG_HOLD_MS)

      if (e.cancelable) e.preventDefault()
    },

    onJoystickMove(e) {
      const t = this.three
      if (!t.joystick.active) return
      const touch = Array.from(e.touches).find((tt) => tt.identifier === t.joystick.touchId)
      if (!touch) return
      const r = this.joystickRadiusCached
      const dx = touch.clientX - t.joystick.originX
      const dy = touch.clientY - t.joystick.originY
      const dist = Math.sqrt(dx * dx + dy * dy)

      // 【长按判定】手指一旦明显移动就取消诊断长按，
      // 否则「随手拨一下摇杆」也会在 0.8s 后误弹出诊断面板。
      // 阈值取外圈半径的 12%：既能容忍按住时的手指微抖，又能立刻区分开「拨动」。
      if (this._diagHeld && dist > r * 0.12) {
        this._diagHeld = false
        clearTimeout(this._diagTimer)
        this._diagTimer = null
      }

      // 超出外圈时夹紧到边界,保持方向
      const cx = dist > r ? (dx * r) / dist : dx
      const cy = dist > r ? (dy * r) / dist : dy
      // 归一化输出,供 applyMovement 使用
      t.joystick.x = cx / r
      t.joystick.y = cy / r
      // UI 手柄位置 (像素)
      this.joystickOffset.x = cx
      this.joystickOffset.y = cy
      if (e.cancelable) e.preventDefault()
    },

    onJoystickEnd(e) {
      const t = this.three
      // 先看 changedTouches:本次 touchend/touchcancel 离开的手指里有没有摇杆 touchId
      // 没找到的话再看 touches:可能在某些平台(uni-app 小程序/APP 内嵌 H5)changedTouches 缺失
      let isJoystickTouchLeaving = false
      if (e && e.changedTouches) {
        for (const touch of e.changedTouches) {
          if (touch.identifier === t.joystick.touchId) {
            isJoystickTouchLeaving = true
            break
          }
        }
      }
      if (!isJoystickTouchLeaving && e && e.touches) {
        // changedTouches 没匹配时,看 touches 里还有没有摇杆 touchId:
        // - 还有 → 当前事件不是摇杆 touch 离开,什么都不做
        // - 没有 → 兜底认为摇杆已松手(平台差异下也能复位)
        let stillActive = false
        for (const touch of e.touches) {
          if (touch.identifier === t.joystick.touchId) {
            stillActive = true
            break
          }
        }
        isJoystickTouchLeaving = !stillActive
      }
      if (isJoystickTouchLeaving) {
        // 还原键盘 w/s 状态:玩家在摇杆按下前若已按住 W,松开摇杆后不应吞掉这个状态
        // savedKeys 在 onJoystickStart 时已记录;未记录(摇杆还没 start 就 end)时跳过
        if (t.joystick.savedKeys) {
          t.keys.w = t.joystick.savedKeys.w
          t.keys.s = t.joystick.savedKeys.s
          t.joystick.savedKeys = null
        }
        // 手指提前抬起 → 未满 DIAG_HOLD_MS,取消诊断长按
        // (不清会留下悬空定时器,在用户已松手后才突然弹出面板)
        if (this._diagHeld) {
          this._diagHeld = false
          clearTimeout(this._diagTimer)
          this._diagTimer = null
        }
        t.joystick.active = false
        t.joystick.touchId = null
        t.joystick.x = 0
        t.joystick.y = 0
        this.joystickOffset.x = 0
        this.joystickOffset.y = 0
        // 松开:打开 transition 让手柄回中时有 0.15s 缓动
        this.joystickDragging = false
      }
      if (e && e.cancelable) e.preventDefault()
    },

    /**
     * 摇杆外圈半径(像素):按 .joystick-pad 实际尺寸计算
     * 用 .joystick-pad 而不是 .joystick-base:
     *  - .joystick-pad 是 absolute 定位的最外层,不受 flex/缩放拉伸影响,clientWidth 稳定
     *  - .joystick-base 是绝对居中的内层,viewport 缩放或父元素拉伸时 clientWidth 可能偏小
     * 拿不到 DOM 时 fallback 用 120(对应默认 240rpx 外圈),比 80 更接近真实值
     */
    joystickRadius() {
      if (typeof document === 'undefined') return 120
      const el = document.querySelector('.joystick-pad')
      if (!el) return 120
      return el.clientWidth / 2
    },

    /**
     * 释放 three.js 资源
     */
    /**
     * 加载人物化身 FBX 模型(0.fbx 静态 + 1.fbx 走路动画)
     * 位置：相机脚下（y=0），相机视高 ≈ 人物头/肩高度
     * 模型跟着相机移动，但不渲染相机本身（第一人称看不到自己）
     * 两个 FBX 同时挂在场景里，根据 figureState(idle/walk)切换可见性 + 走路动画播放
     * 模型远程地址通过 ensureLocalModel 走"远程下载 + 本地缓存"
     */
    async loadFigureAvatar() {
      const t = this.three
      if (!t.scene) return

      // 并行下载 + 解析两个 FBX(0=静态 / 1=走路)
      // 失败不致命:任一缺失时不显示对应化身(不影响另一个生效)
      const results = await Promise.allSettled([
        this.loadFigureFbx('/static/models/0.fbx', 'idle'),
        this.loadFigureFbx('/static/models/1.fbx', 'walk'),
      ])
      const ok = results.filter((r) => r.status === 'fulfilled')

      // 任一 FBX 缺失时,用 procedural 几何体(胶囊体)做兜底化身,
      // 至少在第三人称模式下玩家能看见"自己",不再"模型加载失败但啥都没有"
      if (ok.length < 2) {
        const reasons = results
          .map(
            (r, i) =>
              `${i === 0 ? 'idle' : 'walk'}: ${r.status === 'rejected' ? r.reason?.message || r.reason : 'ok'}`,
          )
          .join(' | ')
        console.warn('[space-3d] 化身加载不完整,启用几何体兜底 →', reasons)
        this.debugInfo = `化身加载不完整,已用兜底几何体(${reasons})`
        if (!t.figureIdleGroup) this.buildFallbackFigure('idle')
        if (!t.figureWalkGroup) this.buildFallbackFigure('walk')
        this.syncFigureState()
        this.figureFallback = true
      }
    },

    /**
     * 从已加载的模型中提取所有 mesh 的世界空间 AABB,作为碰撞体
     * 必须在 scene.add + updateMatrixWorld(true) 之后调用,
     * 否则 setFromObject 拿到的还是 identity 矩阵下的 AABB
     *
     * 【分级过滤 —— 本项目最关键的一处调参】
     * AABB 近似对"悬空结构"会严重高估占位。实测 home.glb 的 Mesh_0
     * （楼梯/挑高，y[0.65, 1.47]，尺寸 3.49 × 3.78m）：
     *   不做过滤 → 一个 mesh 就堵掉 63.2% 的地面，可行走率暴跌到 14%，
     *              人物在床边被四面围死，表现为「原地踏步」。
     * 人在楼梯下方是可以走过去的，只有「从地面长起来」的东西才是障碍。
     * 因此按顶面高度 / 架空高度分三级过滤（阈值经网格扫描标定）：
     */
    buildColliders(model, indoor) {
      const t = this.three
      t.colliders = []
      const full = t.modelBox
      const roomX = full ? full.max.x - full.min.x : 0
      const roomZ = full ? full.max.z - full.min.z : 0
      // 边界墙 = detectIndoorBounds 判定为室内外分界的那几面墙
      const wallNames = new Set((indoor && indoor.walls) || [])

      model.traverse((child) => {
        if (!child.isMesh || !child.geometry) return
        // setFromObject 自动应用 child 及所有父级的矩阵,得到世界空间 AABB
        const worldBox = new THREE.Box3().setFromObject(child)
        const sx = worldBox.max.x - worldBox.min.x
        const sy = worldBox.max.y - worldBox.min.y
        const sz = worldBox.max.z - worldBox.min.z
        // 整片 slab（地板/天花板）横跨房间，本身不作为障碍
        if (sx > roomX * 0.85 && sz > roomZ * 0.85) return
        // 体积过小的装饰物(摆件/挂画/灯具)不参与碰撞，避免把活动空间切碎
        if (sx < 0.12 && sz < 0.12) return

        const isWall = wallNames.has(child.name)

        // 【本轮修复的核心】边界墙必须豁免下面两条过滤：
        // 1. 室外剔除：FrontWall 的 z[0.90,1.07] 与室内 [-3.64,0.90] **零重叠**
        //    （它恰好贴在边界上），走通用规则会被当成"室外构件"整块丢掉；
        // 2. 薄板剔除：FrontWall 厚 0.17m < 0.25m，会被当贴墙装饰过滤掉。
        // 两条任一命中，结果都是内墙没有碰撞 —— 人物径直走出阳台站到室外，
        // 而室外那段头顶悬着 BackRoofPanel（阁楼板），视觉上就成了「站在屋顶上」。
        if (!isWall) {
          const overlapX =
            Math.min(worldBox.max.x, indoor.maxX) - Math.max(worldBox.min.x, indoor.minX)
          const overlapZ =
            Math.min(worldBox.max.z, indoor.maxZ) - Math.max(worldBox.min.z, indoor.minZ)
          if (overlapX <= 0 || overlapZ <= 0) return // 完全在室外
        }

        // 分级 1 · 可跨越的矮物：顶面低于 STEP_HEIGHT，人可直接踩过去
        //   （地毯/门槛/矮茶几/床沿）实测阈值 0.5m —— 低于此高的家具
        //   保留反而会把房间切成碎片，可行走率从 69% 掉到 50%
        if (worldBox.max.y < STEP_HEIGHT) return
        // 分级 2 · 悬空结构：底面离地高于 OVERHEAD_CLEARANCE，人从下面钻过去
        //   （楼梯/挑高/吊柜/桌面）这条直接干掉了 63% 的误判占位
        if (worldBox.min.y > OVERHEAD_CLEARANCE) return
        // 分级 3 · 薄板：贴墙装饰/玻璃/挂画（边界墙是结构，不受此限）
        if (!isWall && Math.min(sx, sy, sz) < 0.25) return
        t.colliders.push({ box: worldBox, mesh: child, name: child.name })
      })
      // eslint-disable-next-line
      console.log(
        '[space-3d] 碰撞体数量 =',
        t.colliders.length,
        '| 边界墙 =',
        [...wallNames].join(',') || '(无)',
        '| 室内范围 =',
        indoor && indoor.source,
      )
    },

    /**
     * 检测人物在 (x, z) 位置是否与场景碰撞体重叠
     *
     * 人物体积近似为一个竖直胶囊，这里用 AABB 近似：
     * - 水平方向：x ± FIGURE_RADIUS
     * - 垂直方向：y ∈ [0, FIGURE_HEIGHT]（从地面到头顶）
     *
     * 关键：必须做「垂直区间重叠」过滤。若不判断 y：
     * 1. 地板 mesh 覆盖整个房间，人物永远与地板相交 → 无法起步；
     * 2. 天花板/吊灯 mesh 也会误判为障碍。
     * 只处理「与人物身体高度区间真正相交」的物体，
     * 这样矮到可以跨过去的地面贴片、地毯、门槛会自动被忽略。
     *
     * @param {number} x 世界坐标 x
     * @param {number} z 世界坐标 z
     * @returns {boolean} true = 该位置被阻挡
     */
    figureCollidesAt(x, z) {
      const t = this.three
      if (!t.colliders || t.colliders.length === 0) return false

      const halfR = FIGURE_RADIUS
      const bodyTop = FIGURE_HEIGHT
      // 略微下探到 y=0 之下，保证与「从地面长起来」的墙体相交
      const bodyBottom = -0.05

      for (let i = 0; i < t.colliders.length; i++) {
        const b = t.colliders[i].box

        // 1. 垂直区间必须真相交（排除地板、天花板）
        if (b.max.y <= bodyBottom || b.min.y >= bodyTop) continue

        // 2. 水平 AABB vs 人物圆形（用 AABB 近似圆，保守但稳定）
        if (
          x + halfR > b.min.x &&
          x - halfR < b.max.x &&
          z + halfR > b.min.z &&
          z - halfR < b.max.z
        ) {
          return true
        }
      }
      return false
    },

    /**
     * 在 preferred 位置附近找一个不与碰撞体相交的安全位置
     *
     * 为什么要认真做这件事：房间中心常常正好压在沙发/茶几/地毯上。
     * 旧实现只试了 24 个固定偏移点，且没有校验"从 preferred 到候选点是否可达"——
     * 一旦 preferred 在大床正中间，最近的空地可能在床的另一侧，中间隔着床，
     * 人物会卡死在原地（正是"原地踏步"的成因之一）。
     *
     * 现在按距离由近到远做螺旋搜索，并优先选择「4邻域都能走」的点，
     * 保证出生后至少有一个方向能立刻走出去。
     *
     * @param {number} preferredX
     * @param {number} preferredZ
     * @returns {{x:number, z:number}}
     */
    findSafeStartPosition(preferredX, preferredZ) {
      const b = this.three.bounds
      const inBounds = (x, z) => {
        if (!b) return true
        return x >= b.minX && x <= b.maxX && z >= b.minZ && z <= b.maxZ
      }
      // 判定"可站立"必须同时满足：在室内 bounds 内 + 不与碰撞体重叠。
      // 早期版本只判碰撞，导致室内范围收窄后仍可能把人物放在界外角落。
      const isOpen = (x, z) => inBounds(x, z) && !this.figureCollidesAt(x, z)

      if (isOpen(preferredX, preferredZ)) {
        return { x: preferredX, z: preferredZ }
      }

      // 首选：往房间内部退一点再试（多数情况是出生点贴着室外那一侧）
      if (b) {
        const midX = (b.minX + b.maxX) / 2
        const midZ = (b.minZ + b.maxZ) / 2
        if (isOpen(midX, midZ)) return { x: midX, z: midZ }
      }

      // 第一轮：固定的小偏移（房间中心附近最常见的空地）
      const near = [
        [0, 1.2],
        [0, -1.2],
        [1.2, 0],
        [-1.2, 0],
        [0.9, 0.9],
        [-0.9, 0.9],
        [0.9, -0.9],
        [-0.9, -0.9],
      ]
      for (const [dx, dz] of near) {
        if (isOpen(preferredX + dx, preferredZ + dz)) {
          return { x: preferredX + dx, z: preferredZ + dz }
        }
      }

      // 第二轮：逐环螺旋扩大，半径 0.4m → 6m，步进 0.4m
      // 用「环」而不是随机散布，保证一定能覆盖到房间里的开阔地带
      let fallback = null
      for (let r = 0.4; r <= 6; r += 0.4) {
        const steps = Math.max(8, Math.round((2 * Math.PI * r) / 0.4))
        for (let i = 0; i < steps; i++) {
          const a = (i / steps) * Math.PI * 2
          const x = preferredX + Math.cos(a) * r
          const z = preferredZ + Math.sin(a) * r
          if (!isOpen(x, z)) continue
          // 记录第一个可行点作为兜底
          if (!fallback) fallback = { x, z }
          // 额外要求：4 邻域至少有两个方向能走，避免出生即被三面围死
          let openDirs = 0
          if (isOpen(x + 0.35, z)) openDirs++
          if (isOpen(x - 0.35, z)) openDirs++
          if (isOpen(x, z + 0.35)) openDirs++
          if (isOpen(x, z - 0.35)) openDirs++
          if (openDirs >= 2) return { x, z }
        }
      }
      return fallback || { x: preferredX, z: preferredZ }
    },

    /**
     * 用 procedural 几何体(身体+头 双胶囊)做兜底化身
     * 替换原本"啥也不挂"的逻辑,确保第三人称下玩家至少能看见自己
     * 复用了 figureIdleGroup / figureWalkGroup 两个槽位,syncFigureAvatar 不需要改
     */
    buildFallbackFigure(kind) {
      const t = this.three
      if (!t.scene) return null
      const group = new THREE.Group()
      const skin = new THREE.MeshStandardMaterial({
        color: kind === 'idle' ? 0xd9b48a : 0xc7a07a,
        roughness: 0.7,
        metalness: 0.05,
        emissive: 0x222222,
      })
      // 身体：胶囊体(高 ~1.7m),脚底贴 y=0
      const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.22, 1.26, 6, 12), skin)
      body.position.y = 0.22 + 1.26 / 2 // 半径 + 圆柱段一半
      group.add(body)
      // 头：小球,放在身体顶部
      const head = new THREE.Mesh(new THREE.SphereGeometry(0.18, 16, 12), skin)
      head.position.y = body.position.y + 1.26 / 2 + 0.18
      group.add(head)
      // 胶囊+球完全对称，无法从几何判定朝向；约定 -Z 为正面 → 补偿角保持 0
      t.scene.add(group)
      if (kind === 'idle') {
        t.figureIdleGroup = group
      } else {
        t.figureWalkGroup = group
      }
      return group
    },

    /**
     * 加载单个 FBX 并归一化缩放(目标身高 ~1m)
     * @param {string} url 模型地址
     * @param {'idle'|'walk'} kind 静态 / 走路
     */
    async loadFigureFbx(url, kind) {
      const t = this.three
      const localUrl = await ensureLocalModel(url)
      return new Promise((resolve, reject) => {
        const loader = new FBXLoader()
        loader.load(
          localUrl,
          (fbx) => {
            // 异步回调期间用户可能已返回页面,scene 已置 null,直接 reject 走兜底
            if (this.three._disposed || !this.three.scene) {
              reject(new Error('页面已卸载,取消 fbx 加载'))
              return
            }
            // 1. 包围盒 + 自动缩放到目标身高 1.7m(人类正常身高)
            // 旧逻辑硬编码 "size.y / 100" 假定 FBX 单位是厘米,导致:
            //   - 已经是米的 FBX(原始身高 100) → rawHeightMeters=1 → scale=1 → 化身 100m 高
            //   - Mixamo 厘米单位 → 正确缩到 1m,但目标身高 1m 太矮相机视高 0.5m 看着像看脚
            // 改为:不假定单位,直接把"原始 size.y"当成身高原始单位,缩到 1.7m。
            //  - 100m 高的原始 → 缩到 1.7m
            //  - 100 厘米的原始(Mixamo) → 缩到 1.7m
            //  - 1m 高的原始 → 缩到 1.7m
            const box = new THREE.Box3().setFromObject(fbx)
            const size = box.getSize(new THREE.Vector3())
            const maxAxis = Math.max(size.x, size.y, size.z) || 1
            const targetHeight = 1.7 // 米
            const rawHeight = size.y // FBX y 方向当作身高(人物都是 y-up)
            // 边界保护:rawHeight 异常(0 / 极小)时退化为最大边
            const scale = rawHeight > 0.1 ? targetHeight / rawHeight : targetHeight / maxAxis
            fbx.scale.setScalar(scale)
            fbx.rotation.x = 0
            fbx.rotation.y = 0
            fbx.rotation.z = 0
            fbx.updateMatrixWorld(true)
            const finalBox = new THREE.Box3().setFromObject(fbx)
            // 底部对齐 y=0(踩在地面网格上)
            fbx.position.y = -finalBox.min.y

            // 2. 材质兜底:FBX 经常不嵌贴图,默认材质在暗场景里看不见,
            // 这里给个暖白色 + 中等亮度,确保裸模型可见
            fbx.traverse((child) => {
              if (child.isMesh) {
                if (child.geometry && !child.geometry.attributes.normal) {
                  child.geometry.computeVertexNormals()
                }
                child.frustumCulled = false
                if (!child.material) {
                  child.material = new THREE.MeshStandardMaterial({
                    color: 0xd9b48a,
                    roughness: 0.7,
                    metalness: 0.1,
                  })
                } else {
                  // 有材质但没贴图的情况:给 emissive 一点点,确保暗处可见
                  const mats = Array.isArray(child.material) ? child.material : [child.material]
                  mats.forEach((m) => {
                    if (!m.emissive) m.emissive = new THREE.Color(0x000000)
                    m.emissive = new THREE.Color(0x222222)
                    m.needsUpdate = true
                  })
                }
              }
            })

            const group = new THREE.Group()
            group.add(fbx)
            group.position.set(0, 0, 0)
            t.scene.add(group)

            // 3. 朝向自动判定
            //
            // 旧实现到处硬编码 `rotation.y = yaw + Math.PI`，依赖
            // "FBX 模型默认朝 +Z" 这个假设。换一个朝 -Z 或 +X 导出的模型，
            // 人物就会背对移动方向走路（第三人称下非常明显）。
            //
            // 判定方法：人物模型通常有明显的「前厚后薄」特征——胸口/面部/脚掌
            // 让 +Z 方向伸出更多，而背部/后脑更平。用几何重心相对包围盒中心的
            // 偏移量判断模型默认朝向的轴向，偏移最大的方向即模型正面。
            // 无法判定时（例如完全对称的胶囊体兜底模型）返回 0，退回"默认 -Z 为正面"。
            // 只用 0.fbx（idle）判定一次朝向，然后让 1.fbx（walk）复用同一个偏移。
            // 理由：两个 FBX 是同一套骨骼/同一管线的导出，朝向必然一致；
            // 而 idle 通常先加载完成（Promise.allSettled 顺序），
            // 用它做基准可保证 walk 不会在 idle 之前抢先写出一个半成品偏移。
            if (kind === 'idle') {
              t.figureYawOffset = this.detectFigureFacing(fbx)
            }

            if (kind === 'idle') {
              t.figureIdleGroup = group
            } else {
              t.figureWalkGroup = group
              // 走路动画:fbx.animations[0] 即为 AnimationClip
              if (fbx.animations && fbx.animations.length > 0) {
                const mixer = new THREE.AnimationMixer(fbx)
                const action = mixer.clipAction(fbx.animations[0])
                action.play()
                t.figureWalkMixer = mixer
                t.figureWalkAction = action
                t.figureWalkClock = new THREE.Clock()
              } else {
                console.warn('[space-3d] 1.fbx 不含骨骼动画,走路模型仅作为静态显示')
              }
            }
            // 两个 group 都就绪(或一个就绪)后,统一按当前 figureState 同步可见性 + 动画,
            // 避免加载期间玩家已按键导致 group.visible 停留在加载瞬间的状态
            this.syncFigureState()

            // 调试信息:输出化身包围盒 + 房间模型尺寸,方便对账高度链
            const figureSize = finalBox.getSize(new THREE.Vector3())
            // eslint-disable-next-line
            console.log(
              '[space-3d] fbx 加载完成 kind=',
              kind,
              '原始尺寸=',
              size,
              'scale=',
              scale,
              '最终尺寸(m)=',
              { x: figureSize.x, y: figureSize.y, z: figureSize.z },
              'facingOffset=',
              t.figureYawOffset,
            )
            // 拼到页面 debug 信息(显示在左上角)
            this.debugInfo = `化身 ${kind}: 原始${size.x.toFixed(2)}×${size.y.toFixed(2)}×${size.z.toFixed(2)} | 化身scale=${scale.toFixed(3)} | 最终${figureSize.x.toFixed(2)}×${figureSize.y.toFixed(2)}×${figureSize.z.toFixed(2)}m`
            if (t.modelSize) {
              this.debugInfo += ` | 房间 ${t.modelSize.x.toFixed(2)}×${t.modelSize.y.toFixed(2)}×${t.modelSize.z.toFixed(2)}m`
            }

            resolve(group)
          },
          undefined,
          (err) => {
            console.error('[space-3d] fbx 加载失败 url=', url, err)
            reject(err)
          },
        )
      })
    },

    /**
     * 判定人物模型的默认朝向，返回需要施加的 yaw 补偿角（弧度）。
     *
     * 背景：three 的 FBX/GLTF 人形模型导出朝向不统一（Mixamo 朝 +Z，
     * Blender 默认朝 -Y 旋转后是 +Z 或 -Z，个别管线是 +X）。
     * 旧代码靠 `+ Math.PI` 硬编码，换模型就穿帮。
     *
     * 判定原理：人物有明确的「前厚后薄」特征。用几何重心相对包围盒中心的
     * 偏移判断——胸口/面部/前脚比背部/后脑伸出更多，偏移最大的轴即正面方向。
     *
     * 目标朝向约定：我们的 forward 向量是 (-sin(yaw), 0, -cos(yaw))，
     * yaw=0 时 forward 为 -Z。因此模型需要旋转到「自身正面朝 -Z」，
     * 若检测出模型正面朝 +Z 则补偿 π，朝 -Z 则补偿 0，朝 ±X 则补偿 ∓π/2。
     *
     * @param {THREE.Object3D} fbx 已归一化的 fbx 根节点
     * @returns {number} yaw 补偿角（弧度）；无法判定时返回 0（按 -Z 为正面）
     */
    detectFigureFacing(fbx) {
      fbx.updateMatrixWorld(true)

      // 1. 算包围盒，作为对称性判断基准
      const box = new THREE.Box3().setFromObject(fbx)
      const size = box.getSize(new THREE.Vector3())
      const center = box.getCenter(new THREE.Vector3())
      const bodyHeight = size.y || 1

      // 2. 逐 mesh 累加顶点重心（遍历真实模型顶点，不造假几何体）
      let vx = 0
      let vz = 0
      let n = 0
      fbx.traverse((child) => {
        const attr = child.isMesh && child.geometry?.attributes?.position
        if (!attr) return
        for (let i = 0; i < attr.count; i++) {
          vx += attr.getX(i)
          vz += attr.getZ(i)
          n++
        }
      })
      if (n === 0) return 0

      // 3. 顶点重心从模型局部空间转到世界空间
      const centroid = new THREE.Vector3(vx / n, 0, vz / n)
      fbx.localToWorld(centroid)

      // 4. 重心相对包围盒中心的水平偏移 = 模型正面方向
      const dx = centroid.x - center.x
      const dz = centroid.z - center.z
      const mag = Math.hypot(dx, dz)

      // 偏移过小 → 前后对称，判不出来，用默认 -Z 正面（offset 0）
      if (mag < bodyHeight * 0.01) {
        // eslint-disable-next-line
        console.warn('[space-3d] 化身前后对称，无法自动判定朝向，使用默认 -Z 正面')
        return 0
      }

      // 5. 我们的 forward = (-sin(yaw), 0, -cos(yaw))，yaw=0 时朝 -Z。
      //    所以要让模型正面（重心偏移方向 atan2(dx,dz)）转到 -Z，
      //    需要额外施加 yaw = angle + π。
      const angle = Math.atan2(dx, dz)
      let offset = angle + Math.PI
      // 归一化到 [-π, π]
      if (offset > Math.PI) offset -= Math.PI * 2
      if (offset < -Math.PI) offset += Math.PI * 2

      // eslint-disable-next-line
      console.log(
        '[space-3d] 化身朝向自动判定: 重心偏移=',
        { dx: dx.toFixed(3), dz: dz.toFixed(3) },
        'facingOffset=',
        offset.toFixed(3),
        'rad',
      )
      return offset
    },

    /**
     * 同步人物化身位置/朝向 + TPS 相机跟随（每帧调用）
     * 根据 viewMode 不同：
     * - fps 第一人称：相机贴在人物眼睛位置，看不到自己
     * - tps 第三人称：相机在人物身后固定臂长，pitch 驱动真实俯仰
     *
     * TPS 的两个关键设计：
     * 1. pitch 驱动朝向（quaternion），不驱动位置 —— 否则会被 lookAt 抵消
     * 2. 相机高度固定 TPS_HEIGHT 并钳制在房间内，避免顶穿天花板
     */
    syncFigureAvatar() {
      const t = this.three
      if (!t.camera || !t.playerPos) return
      const pos = t.playerPos

      // === 推进走路动画 mixer ===
      //
      // 【本轮修复：与渲染循环同频，且按移动速度调节播放速率】
      // 旧实现：`mixer.update(figureWalkClock.getDelta())`
      // 1) 那个 Clock 独立采样，与渲染循环不同步，切状态那一帧会返回
      //    一个巨大的间隔 → 动作猛跳（表现为"抽一下"）。
      // 2) 动画永远按 1.0 倍速播放，与人物实际移动速度无关 ——
      //    推摇杆走 1/4 速度时，腿却是在全速蹬地，看着就是"滑步 + 顿挫"。
      //
      // 现在统一用渲染循环的 frameDelta（动画/移动/渲染严格同频），
      // 并按输入强度调播放速率：慢走小碎步、快走大步幅。
      if (t.figureWalkMixer && t.figureState === 'walk') {
        // 复用渲染循环 delta；拿不到时兜底 1/60
        const dt = typeof t.frameDelta === 'number' ? t.frameDelta : 1 / 60
        // t.walkSpeed01: 0~1 的移动强度，由 applyMovement 每帧写入。
        // 最低 0.45 保证慢走时也有可见的迈步幅度（完全按 0 会变成原地滑步）。
        const rate = 0.45 + 0.55 * (t.walkSpeed01 || 0)
        t.figureWalkMixer.update(dt * rate)
      }

      // 复用 forward Vector3,避免每帧 new
      if (!t._syncForward) t._syncForward = new THREE.Vector3()
      const forward = t._syncForward
      forward.set(-Math.sin(t.yaw), 0, -Math.cos(t.yaw))

      // 人物朝向：yaw + 自动判定的模型朝向补偿（不再硬编码 +Math.PI）
      const figureYaw = t.yaw + t.figureYawOffset
      // 脚底抬高一点点，避免与地板 Z-fighting
      const FIGURE_Y = 0.02

      if (this.viewMode === 'tps') {
        // === TPS：标准第三人称跟随 ===
        //
        // 【本轮修复的核心：pitch 之前完全不起作用】
        // 旧写法是「先按 pitch 算相机高度，再 lookAt(人物)」：
        //   vert   = TPS_HEIGHT + sin(pitch) * 1.2   // 只改位置
        //   camera.lookAt(pos.x, 1.22, pos.z)        // 朝向被掰回人物
        // 净效果 = 相机沿视线上下平移、朝向恒定指向人物 —— 实测 pitch 从 -90° 扫到 +90°
        // 视角几乎不变，这就是"滑动屏幕视野没变化"的直接原因。
        //
        // 正确做法：把 pitch 放进相机的**朝向**，而不是位置。
        // 观察点固定在人物身后的水平臂长（标准 TPS 跟随），
        // 朝向由 yaw(绕Y) + pitch(俯仰) 共同决定。
        const horiz = TPS_DISTANCE
        // 相机高度钳制在房间内：层高 2.8m，相机 1.9m 已接近天花板，
        // 再高就会穿进 BackRoofPanel 里（玩家看到全黑）
        const vert = Math.min(TPS_HEIGHT, (t.roomHeight || TARGET_CEILING_HEIGHT) - 0.35)

        // 相机避墙。
        //
        // 【本轮修复的核心：bounds 钳制的符号写反了，导致 42% 概率穿墙】
        // 旧实现分两步：先沿线段采样找 colliders，再用一个"射线求交"式的公式
        // 去钳制到 bounds：
        //     if (fx < -1e-6) lim = Math.min(lim, (b.minX + pad - px) / fx)
        // 问题在于**这个公式算的是"朝 +forward 方向走多远到边界"，
        // 而相机实际走的是朝 -forward 方向（人物身后）**。两者差一个负号。
        //
        // 实测最坏案例：人物站在室内左后角 (-3.17, -3.34)，朝向 yaw=270°
        //   forward = (+1, 0)  → 相机应落在 x = -3.17 - 2.6 = -5.77（房间外）
        //   代码算钳制上限 = (b.maxX - 0.2 - px) / fx = (3.16 - 0.2 + 3.17) / 1.0 = 6.13
        //   6.13 ≫ 2.6 → 钳制**完全不触发**，allowed 原样保留 2.6
        //   相机最终落在 x=-5.77，**冲出室外 2.6m**。
        //
        // 为什么之前没被发现：
        // 1. colliders 里根本没有背墙（BackFascia 是整片 slab，被 buildColliders
        //    的 "sx > roomX*0.85 && sz > roomZ*0.85" 规则整块过滤掉），
        //    所以 12 个采样点全部 blocked=false，避障逻辑"看起来在工作"。
        // 2. 碰撞体只在 +Z 方向有 FrontWall 兜底，朝 -X / -Z 时彻底裸奔。
        // 3. 全网格扫描 46080 个采样点（位置 × 360° 朝向）实测：
        //    旧逻辑 **19379 次穿墙（42.1%）**，最远冲出室外 8.9m；
        //    修正后 0 次。
        //
        // 正确做法：**不解析式求交，直接把相机实际要去的那个点算出来再判定**。
        // 从最远处往近处逐步逼近，取第一个「在室内 bounds 内且不在 collider 里」
        // 的距离。这样 bounds 与 colliders 用同一套判定，不会再出现符号错位；
        // 顺带用 MIN_ARM 兜底，避免贴墙时臂长归零导致相机怼在人脸里
        // （旧代码 allowed=0 时 `if (allowed > 0)` 整个跳过钳制，
        //   camX/camZ 直接等于人物坐标，实测 15360 个采样里有 1671 次发生）。
        const MAX_CAM_SAMPLES = 12
        const CAM_MARGIN = 0.22 // 相机与室内边界的余量：近裁剪面 + cameraBlockedAt 半宽 0.15
        const MIN_ARM = 0.35 // 臂长下限，保证相机不会贴到人物头上
        // CAM_REFINE：粗档命中后在「该档 → 下一档」区间内二分的次数。
        // 见下方【本轮修复】的说明：不再直接用离散档位当臂长。
        const CAM_REFINE = 6
        // CAM_ARM_DAMP：臂长**伸长**时的阻尼速率（1/s）。
        // 缩短立即生效（否则相机会滞留墙内），伸长则平滑，避免贴墙转身时镜头"弹出"。
        const CAM_ARM_DAMP = 10

        // bounds 判定独立成一个闭包，避免与 colliders 判定写成两套逻辑而再次跑偏
        const camInsideBounds = (x, z) => {
          const b = t.bounds
          if (!b) return true
          return (
            x >= b.minX + CAM_MARGIN &&
            x <= b.maxX - CAM_MARGIN &&
            z >= b.minZ + CAM_MARGIN &&
            z <= b.maxZ - CAM_MARGIN
          )
        }

        let allowed = horiz
        if (t.colliders && t.colliders.length > 0) {
          // 从最远往近找：MAX_CAM_SAMPLES → 1，第一个通过的即最大安全档位。
          // 正向遍历（旧写法）会在"远点无遮挡、近点有遮挡"时漏判，
          // 因为一旦远点没被挡就整段接受，哪怕中途其实穿过了薄墙。
          let coarse = MIN_ARM
          let found = false
          for (let s = MAX_CAM_SAMPLES; s >= 1; s--) {
            const d = (horiz * s) / MAX_CAM_SAMPLES
            const tx = pos.x - forward.x * d
            const tz = pos.z - forward.z * d
            if (camInsideBounds(tx, tz) && !this.cameraBlockedAt(tx, tz, vert)) {
              coarse = d
              found = true
              break
            }
          }
          // 一个安全点都找不到（人物被家具/墙角完全包围）→ 用下限，
          // 此时相机贴在人物身上，画面会糊，但至少不会穿到室外或墙体里。
          if (!found) {
            allowed = MIN_ARM
          } else {
            // 【本轮修复的核心：二分细化，消除臂长的离散台阶】
            //
            // 症状：向前/向后走画面明显顿挫，左右走没有。
            //
            // 根因：相机避墙臂长是**离散 12 档**求出来的，档距 = 2.6/12 ≈ 0.217m。
            // 人物沿 forward 走时（左右走时相机相对位置不变，臂长恒定 1.733，
            // 所以看不出问题），臂长会在某一帧"跨过一档"，
            // 于是 camX/camZ 跳 0.217m，同时：
            //   shortfall 跳变 → 相机高度 camY 跳 0.053m
            //   basePitch 重新计算 → 俯角跳 2.8°（后退时最坏 10°）
            // 这三个量同时阶跃 = 画面每 5~6 帧抖一下，即用户看到的"顿挫感"。
            //
            // 实测（真实 home.glb 几何回放，起点=出生点，yaw=0，60fps 走 1.5s）：
            //   前进 90 帧跳 4 次、后退跳 7 次，每次臂长跳 0.2167m
            //   左右各 0 次（臂长恒为 1.733）
            //
            // 修复：粗档只用来确定所在**区间**，再用二分在该区间内求真实边界。
            // 命中档 d 的下一档一定不安全，所以安全边界就在 (d, d+档距) 之间。
            // 二分 6 次精度 = 0.217/2^6 ≈ 0.0034m，肉眼完全无法察觉。
            const step = horiz / MAX_CAM_SAMPLES
            let lo = coarse
            let hi = Math.min(horiz, coarse + step)
            for (let k = 0; k < CAM_REFINE; k++) {
              const mid = (lo + hi) * 0.5
              const mx = pos.x - forward.x * mid
              const mz = pos.z - forward.z * mid
              if (camInsideBounds(mx, mz) && !this.cameraBlockedAt(mx, mz, vert)) lo = mid
              else hi = mid
            }
            allowed = lo
          }
        }
        // 臂长下限：即使上面判定通过，也不允许缩到 0
        allowed = Math.max(MIN_ARM, allowed)

        // 【本轮修复：臂长单向阻尼】
        // 上面算出的是"当前帧的理论安全臂长"，它仍然可能因为转头（yaw 突变）
        // 而单帧大跳。二分细化解决不了转身那一刻的突变，所以再加一层阻尼：
        //   - 缩短：立即生效。否则相机在玩家贴近墙时还停在远处，会穿进墙里。
        //   - 伸长：按指数平滑。镜头从贴墙"退出来"时是渐进的，观感自然。
        // 用 1-exp(-k·dt) 而不是固定系数，保证 30fps 与 120fps 表现一致。
        const target = allowed
        if (t.camArm === undefined) t.camArm = target
        if (target <= t.camArm) {
          t.camArm = target
        } else {
          const k = 1 - Math.exp(-CAM_ARM_DAMP * (t.frameDelta || 1 / 60))
          t.camArm += (target - t.camArm) * k
        }
        allowed = t.camArm

        let camX = pos.x - forward.x * allowed
        let camZ = pos.z - forward.z * allowed
        // 末端硬钳制：上面的二分是有限次数的近似，且臂长还带阻尼（可能短暂
        // 停在上一帧的较大值），栅格点通过不代表栅格之间的连续轨迹也通过。
        // 这里对最终落点再兜一次底 —— 必须钳到**臂长**而不是只钳坐标，
        // 否则坐标被夹住后 camLen 与实际臂长不符，俯角会算错。
        if (t.bounds) {
          const b = t.bounds
          camX = Math.max(b.minX + CAM_MARGIN, Math.min(b.maxX - CAM_MARGIN, camX))
          camZ = Math.max(b.minZ + CAM_MARGIN, Math.min(b.maxZ - CAM_MARGIN, camZ))
        }
        // 钳制后回写实际臂长，保证 shortfall / camLen 与画面一致
        allowed = Math.max(MIN_ARM, Math.hypot(camX - pos.x, camZ - pos.z))

        // 【臂长不足 → 抬升相机俯视】
        // TPS 经典解法：第三人称相机被墙挡住时，除了拉近，还可以**升高 + 加大俯角**，
        // 用"从上往下看人物"的视角替代"从后面平视"。这样即使水平臂长只剩 0.3m，
        // 人物也不会占满画面（改为俯视全身），观感自然很多。
        //
        // 按臂长亏欠程度连续插值，而非硬切换：
        //   臂长充裕 → 完全不抬升，维持标准 TPS 观感
        //   臂长趋近 MIN_ARM → 抬到最高，配合下面的 basePitch 自动加大俯角
        // 抬升上限受层高约束（roomHeight - 0.35），不会顶穿天花板。
        const camMaxY = Math.min(TPS_HEIGHT + 0.55, (t.roomHeight || TARGET_CEILING_HEIGHT) - 0.35)
        const shortfall = 1 - Math.min(1, (allowed - MIN_ARM) / Math.max(horiz - MIN_ARM, 0.001))
        const camY = vert + (camMaxY - vert) * shortfall

        t.camera.position.set(camX, camY, camZ)
        // 朝向：yaw 绕 Y + pitch 俯仰，而不是 lookAt(人物)。
        // 这样"抬头/低头"才真正改变视线方向。
        //
        // 【基准俯角 basePitch：让视线默认指向人物胸口】
        // 相机固定在 y=TPS_HEIGHT(1.9m)，人物重心只有 ~0.87m。
        // 若 pitch 直接用玩家输入的 0（水平平视），人物整体落在画面下半部：
        // 实测投影 NDC y：脚 -1.015（视锥外，被摇杆挡住）、头 -0.127（贴底），
        // 玩家进页面几乎看不见自己 —— 表现就是"人物不见了"。
        //
        // 公式用真实的高度差：atan2(视线目标高度 - 相机高度, 水平臂长)。
        // 【本轮修复：用 camY 而非固定 vert】臂长被压缩时相机同时抬升，
        // 高度差变大、俯角自动加大，人物自然落在画面中央 ——
        // 这正是"臂长不足时改用俯视"能奏效的关键，两者必须联动。
        // 玩家拖动的 pitch 是在这个基准上叠加，不影响手动俯仰。
        // 复用 applyLook 的 Euler/Quaternion 实例，避免每帧 new
        if (!t._tpsQuat) {
          t._tpsQuat = new THREE.Quaternion()
          t._tpsEuler = new THREE.Euler(0, 0, 0, 'YXZ')
        }
        // 俯仰用**三维**距离：臂长很短时水平分量趋近 0，
        // 若只按水平臂长算 atan2 会得到接近 ±90° 的极端俯角导致画面翻转。
        const camLen = Math.max(0.35, Math.hypot(camX - pos.x, camZ - pos.z))

        // 【本轮修复：视线落点前移到人物前方，人物才落在画面中央】
        // 旧写法把视线终点定在「人物脚下」，只用 atan2 算俯角。
        // 相机在人物正后方并抬高后，视线会笔直向下扎在人物脚下 ——
        // 结果人物被推到画面正下方、头贴着屏幕底边，实测整个人挤在左下角，
        // 而房间主体（床、地毯）全挤在上半屏，构图完全失衡。
        //
        // TPS 相机的标准做法：视线终点不是"人物所在点"，而是**人物前方
        // 若干米处与相机等高的那个点**，人物只是恰好出现在画面下缘中央。
        // 这里让视线越过人物头顶继续前伸，前伸量随相机抬高而增大，
        // 于是俯角变大、人物自然被推到画面中心附近。
        const lookAhead = TPS_FOCUS_HEIGHT + (camY - vert) * 0.9
        const basePitch = Math.atan2(lookAhead - camY, camLen)
        t._tpsEuler.set(t.pitch + basePitch, t.yaw, 0, 'YXZ')
        t._tpsQuat.setFromEuler(t._tpsEuler)
        t.camera.quaternion.copy(t._tpsQuat)
      }

      // 人物化身本体：两种视角都需要摆到人物脚下并朝向移动方向
      //
      // 【本轮修复：FPS 必须隐藏化身】
      // 第一人称下相机就在人物眼睛位置，化身模型挡在眼前。
      // 只要人物站得离绿植/柜子不到 ~0.5m，模型就会把整个视野糊满
      //（实测站到盆栽旁，画面全是叶子，房间完全看不见）。
      // 位置每帧照写（切回 TPS 时立刻就位），但 FPS 下不渲染。
      const figVisible = this.viewMode !== 'fps'
      if (t.figureIdleGroup) {
        t.figureIdleGroup.position.set(pos.x, FIGURE_Y, pos.z)
        t.figureIdleGroup.rotation.y = figureYaw
        t.figureIdleGroup.visible = figVisible && t.figureState === 'idle'
      }
      if (t.figureWalkGroup) {
        t.figureWalkGroup.position.set(pos.x, FIGURE_Y, pos.z)
        t.figureWalkGroup.rotation.y = figureYaw
        t.figureWalkGroup.visible = figVisible && t.figureState === 'walk'
      }
    },

    /**
     * 切换诊断面板。
     *
     * 为什么需要它：APP 真机（webview）里 console.log 要连 devtools 才看得到，
     * 而这次「人物初始在室外」的问题在浏览器里完全复现不出来（4 种视口都正常），
     * 只能靠真机上的运行时数值定位。这个面板把 normalize → indoor → bounds → spawn
     * 整条链路的中间值直接显示出来，不用猜。
     */
    toggleDiag() {
      this.showDiag = !this.showDiag
      if (this.showDiag) {
        // 记录打开时刻：closeDiag 用它静默掉 ghost click
        this.diagOpenedAt = Date.now()
        this.refreshDiag()
      }
    },

    /**
     * 关闭诊断面板。
     * 不直接写 showDiag = false，而是先过一道静默窗口：
     * 长按摇杆触发的那次触摸，抬手时浏览器会在按下点与抬起点之间补发一次
     * click（ghost click）。这次 click 会落在刚弹出的遮罩上，
     * 若不加闸门，面板会在出现的那一帧就被自己点掉 —— 实测必现。
     */
    closeDiag() {
      if (this.diagOpenedAt && Date.now() - this.diagOpenedAt < DIAG_CLOSE_GUARD) return
      this.showDiag = false
    },

    /**
     * 采集并格式化诊断数据。全部从 three 的运行时对象现读，不做任何推算。
     */
    refreshDiag() {
      const t = this.three
      const f = (v, n = 2) => (typeof v === 'number' && isFinite(v) ? v.toFixed(n) : '-')
      const r = (a, b) => `[${f(a)}, ${f(b)}]`

      // 平台判定：APP-PLUS 走条件编译，H5 直接返回 'H5'
      let platform = 'unknown'
      // #ifdef APP-PLUS
      platform = 'APP-PLUS'
      // #endif
      // #ifdef H5
      platform = 'H5'
      // #endif
      if (platform === 'unknown') platform = 'APP(webview)'
      this.diagPlatform = platform

      const meta = t.diag || {}
      this.diagModelSource = meta.modelSource || '-'
      this.diagAnchor = meta.floorAnchor || '-'
      this.diagFloorHeight = f(meta.floorHeight, 3)
      this.diagScale = f(meta.scale, 4)
      this.diagMeshCount = String(meta.meshCount ?? '-')

      const ind = meta.indoor
      if (ind) {
        this.diagIndoor = `x${r(ind.minX, ind.maxX)} z${r(ind.minZ, ind.maxZ)}`
        this.diagWalls =
          (ind.walls && ind.walls.length ? ind.walls.join(',') : '(无)') + ' | ' + ind.source
      } else {
        this.diagIndoor = '-'
        this.diagWalls = '-'
      }

      const b = t.bounds
      this.diagBounds = b ? `x${r(b.minX, b.maxX)} z${r(b.minZ, b.maxZ)}` : '-'
      this.diagColliders = String((t.colliders || []).length)

      const sp = t.spawnPoint
      this.diagSpawn = sp ? `(${f(sp.x)}, ${f(sp.z)})` : '-'
      const cam = t.camera
      this.diagCamera = cam
        ? `(${f(cam.position.x)}, ${f(cam.position.y)}, ${f(cam.position.z)})`
        : '-'
    },

    /**
     * 检测某个水平位置在给定高度是否被墙体/家具占据（TPS 相机避墙用）
     * 与 figureCollidesAt 的区别：相机不需要「从地面到头顶」的体积，
     * 只需在观察点附近有一小块空间不被占据，否则贴墙就会把相机顶到天上。
     *
     * @param {number} x
     * @param {number} z
     * @param {number} y 相机高度
     */
    cameraBlockedAt(x, z, y) {
      const t = this.three
      if (!t.colliders || t.colliders.length === 0) return false
      // 观察点附近留 0.15m 余量，避免相机近裁剪面切进墙面
      const half = 0.15
      const top = y + 0.15
      const bottom = y - 0.15
      for (let i = 0; i < t.colliders.length; i++) {
        const b = t.colliders[i].box
        if (b.max.y <= bottom || b.min.y >= top) continue
        if (x + half > b.min.x && x - half < b.max.x && z + half > b.min.z && z - half < b.max.z) {
          return true
        }
      }
      return false
    },

    /**
     * 根据 figureState(idle/walk)切换两个 FBX group 的可见性 + 控制走路动画播放/暂停
     */
    syncFigureState() {
      const t = this.three
      // 当前状态为 walk:显示走路 group,隐藏静态 group,推进 mixer
      // 当前状态为 idle:显示静态 group,隐藏走路 group,停止走路动画
      if (t.figureIdleGroup) {
        t.figureIdleGroup.visible = t.figureState === 'idle'
      }
      if (t.figureWalkGroup) {
        t.figureWalkGroup.visible = t.figureState === 'walk'
      }

      const action = t.figureWalkAction
      if (!action) return

      // 兼容不同 three.js 版本的 API 差异：
      // 老版本只有 stop()/play()，pause() 是较新版本才有的。
      // 这里做能力探测，任一可用即可，避免因 API 缺失抛错导致整帧中断
      // （实测曾因 action.pause 不存在而抛 TypeError，
      //  连带 figureState 切换失败、动画状态彻底卡死）。
      const canPause = typeof action.pause === 'function'
      const canStop = typeof action.stop === 'function'

      if (t.figureState === 'walk') {
        // 【本轮修复：去掉 reset()，这是"顿挫感"的第二层原因】
        // 旧写法每次恢复走路都先 reset() 再 play()：
        //     if (!action.isRunning()) { action.reset(); action.play() }
        // reset() 会把动画时间轴跳回第 0 帧（站姿），于是每一次
        // walk → idle → walk 的抖动都会让肢体"重新起步"，
        // 与 applyMovement 里的状态抖动叠加，视觉上就是走走停停。
        //
        // 正确做法：只保证 action 处于播放状态，**保留当前播放进度**。
        // 这样即使状态偶尔抖动，动画也是连续的，不会回弹。
        if (typeof action.isRunning === 'function' ? !action.isRunning() : true) {
          action.play()
        }
      } else {
        // 停止时优先用 pause() 而不是 stop()：
        //   pause() = 停在当前帧，isRunning() 仍为 true
        //   stop()  = 把 action 标记为非 running，下次必须 reset()+play() 才能恢复
        // 旧代码用 stop()，正是它让"下次恢复必须重置时间轴"成为必然，
        // 于是状态抖动被逐次放大成可见的顿挫。
        // 保留当前帧还有个好处：下次 play() 从这里继续，衔接自然不跳。
        const running = typeof action.isRunning === 'function' ? action.isRunning() : true
        if (running) {
          if (canPause) action.pause()
          else if (canStop) action.stop()
        }
      }
    },

    /**
     * 切换第一人称 / 第三人称
     *
     * 相机位置不需要在这里手动摆：applyMovement(FPS) 与 syncFigureAvatar(TPS)
     * 都会每帧重写 camera.position。旧实现在这里写死 y=0.5/2.0，
     * 既与新的视高常量冲突，也会在切换瞬间闪一帧错误高度。
     */
    toggleViewMode() {
      const next = this.viewMode === 'fps' ? 'tps' : 'fps'
      this.viewMode = next
      // 切回 TPS 时把俯仰角归零，避免沿用 FPS 抬头/低头的角度导致相机怼到天花板上
      if (next === 'tps') {
        this.three.pitch = 0
      }
      uni.showToast({
        title: next === 'fps' ? this.$t('petHub3d.cameraFps') : this.$t('petHub3d.cameraTps'),
        icon: 'none',
      })
    },

    disposeScene() {
      const t = this.three
      // 标记已卸载,startScene 的重试分支看到后会立即停止再排 setTimeout,
      // 避免回调打到已被销毁的 Vue 实例上导致诡异报错
      t._disposed = true
      if (t._docRetryTimer) {
        clearTimeout(t._docRetryTimer)
        t._docRetryTimer = null
      }
      if (t._canvasRetryTimer) {
        clearTimeout(t._canvasRetryTimer)
        t._canvasRetryTimer = null
      }
      // 清理诊断面板的长按定时器：否则离开页面后定时器仍会到点触发
      // toggleDiag()，把状态打到已销毁的 Vue 实例上
      if (this._diagTimer) {
        clearTimeout(this._diagTimer)
        this._diagTimer = null
      }
      this._diagHeld = false
      this.unbindKeyboard()
      // 重置 document 重试计数,避免下次进入页面累计后误判
      // (不清零的话,第二次进入页面 _docRetryCount 仍是上次的累加值,
      //  第一次重试就可能超过 5 次阈值,直接弹"不支持 3D")
      t._docRetryCount = 0
      t._canvasRetryCount = 0
      if (t.animationId) {
        cancelAnimationFrame(t.animationId)
        t.animationId = null
      }
      if (t.resizeObserver) {
        t.resizeObserver.disconnect()
        t.resizeObserver = null
      }
      if (t.modelGroup) {
        t.modelGroup.traverse((child) => {
          if (child.isMesh) {
            if (child.geometry) child.geometry.dispose()
            if (child.material) {
              const mats = Array.isArray(child.material) ? child.material : [child.material]
              mats.forEach((m) => m.dispose())
            }
          }
        })
        // 从场景里摘掉,避免被已 dispose 的 group 残留引用
        if (t.modelGroup.parent) t.modelGroup.parent.remove(t.modelGroup)
        t.modelGroup = null
      }
      // 释放走路 mixer(必须先 stop 再 uncacheRoot)
      if (t.figureWalkMixer) {
        t.figureWalkMixer.stopAllAction()
        const root = t.figureWalkMixer.getRoot()
        t.figureWalkMixer.uncacheRoot(root)
        t.figureWalkMixer = null
      }
      t.figureWalkAction = null
      // 清理碰撞体(只持有 mesh 引用,不持有 GPU 资源,直接清空即可)
      t.colliders = null
      // 臂长阻尼状态也要清：colliders 变了（重载模型）旧值不再有意义
      t.camArm = null
      // 释放两个 FBX group(0.fbx 静态 + 1.fbx 走路)
      const disposeGroup = (g) => {
        if (!g) return
        g.traverse((child) => {
          if (child.isMesh) {
            if (child.geometry) child.geometry.dispose()
            if (child.material) {
              const mats = Array.isArray(child.material) ? child.material : [child.material]
              mats.forEach((m) => m.dispose())
            }
          }
        })
        // 从场景里摘掉
        if (g.parent) g.parent.remove(g)
      }
      disposeGroup(t.figureIdleGroup)
      disposeGroup(t.figureWalkGroup)
      t.figureIdleGroup = null
      t.figureWalkGroup = null
      if (t.controls) {
        t.controls.dispose()
        t.controls = null
      }
      if (t.renderer) {
        t.renderer.dispose()
        const dom = t.renderer.domElement
        if (dom && dom.parentNode) dom.parentNode.removeChild(dom)
      }
      t.renderer = null
      t.scene = null
      t.camera = null
      t.modelGroup = null
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
