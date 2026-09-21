<template>
  <view class="space-3d">
    <!-- 顶部导航 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="header-title">宠物空间 3D · 第一人称(人物化身)</text>
      <view class="header-actions">
        <view
          class="icon-btn"
          :class="{ 'icon-btn-active': viewMode === 'tps' }"
          @click="toggleViewMode"
        >
          <!-- lucide.ttf 里 luc-eye 缺字，改用 luc-camera（视角切换） -->
          <text class="luc luc-camera" />
        </view>
        <view class="icon-btn" @click="resetCamera">
          <text class="luc luc-refresh-cw" />
        </view>
        <view class="icon-btn" @click="toggleWireframe">
          <text class="luc luc-grid" />
        </view>
      </view>
    </view>

    <!-- 3D 画布 -->
    <!--
      仅保留 touchstart:
      - PointerLockControls 必须在 canvas 元素的用户手势上 lock()(全局 capture 不行)
      - move/end 由全局 capture 监听处理,避免双路径重复更新 yaw/pitch
    -->
    <view class="canvas-wrap" @touchstart="onCanvasTouchStart">
      <view :id="canvasId" ref="canvasEl" class="canvas">
        <view v-if="debugInfo" class="debug-info">
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
          <text>重试</text>
        </view>
      </view>

      <!-- 第一人称视角 HUD -->
      <view v-if="!loading && !errorMsg" class="hud">
        <!-- 顶部：位置 + 操作提示 -->
        <view class="hud-top">
          <view class="hud-pill">
            <text class="hud-pill-text">📍 {{ positionText }}</text>
          </view>
          <view class="hud-pill" :class="{ 'hud-pill-active': pointerLocked }">
            <text class="hud-pill-text">
              {{
                pointerLocked ? '🖱️ 已锁定视角 (ESC 退出)' : '👆 拖动空白区看视角 · WASD/摇杆移动'
              }}
            </text>
          </view>
          <view v-if="figureFallback" class="hud-pill">
            <text class="hud-pill-text">🧍 化身:占位几何体(0/1.fbx 缺失)</text>
          </view>
        </view>

        <!-- 中心十字准星 -->
        <view class="crosshair">
          <view class="crosshair-h" />
          <view class="crosshair-v" />
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
              :style="{ transform: `translate(${joystickOffset.x}px, ${joystickOffset.y}px)` }"
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

export default {
  data() {
    return {
      canvasId: 'three-canvas-' + Math.random().toString(36).slice(2, 9),
      loading: true,
      loadingText: '正在加载 three.js...',
      progress: 0,
      errorMsg: '',
      loadedVertices: 0,
      autoRotate: false,
      wireframe: false,
      lightOn: true,
      debugInfo: '',

      // 第一人称相关 UI 状态
      pointerLocked: false,
      positionText: '加载中...',
      // 视角模式：fps = 第一人称（人物眼睛）/ tps = 第三人称跟随（能看到自己）
      viewMode: 'fps',

      // 摇杆 UI:手柄相对外圈中心的偏移(像素),用于渲染 .joystick-knob 的 transform
      joystickOffset: { x: 0, y: 0 },
      // 摇杆拖动中:true 时关闭手柄 transition 保证跟手
      joystickDragging: false,
      // 摇杆外圈半径(像素):在 onJoystickStart 时取一次缓存,避免 move 高频 querySelector
      joystickRadiusCached: 120,
      // 化身是否为兜底几何体(true 时 HUD 给提示)
      figureFallback: false,
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
          moveSpeed: 0.05, // 每次步进的位移
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
        }
        threeInstances.set(this, bucket)
      }
      return bucket
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
      this.loadingText = '正在加载 three.js...'
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
          this.errorMsg = 'three.js 加载失败：' + (e.message || e)
          this.debugInfo = `three.js 加载失败：${e.message || e}`
          this.loading = false
        })
    },

    startScene() {
      try {
        this.loadingText = '正在初始化场景...'
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
          this.errorMsg = '当前环境不支持 3D（仅 H5/APP 支持）'
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
            this.errorMsg = '画布节点长时间未就绪，请返回重试'
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
        // 起始位置：稍后加载完模型再调整到门口
        camera.position.set(0, 1.7, 0)
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
        this.errorMsg = '场景初始化失败：' + (e.message || e)
        this.loading = false
      }
    },

    /**
     * 加载 GLB 模型 + 加载完后把相机定位到门口
     * 模型远程地址,通过 ensureLocalModel 走"远程下载 + 本地缓存"
     */
    async loadModel(renderer, scene) {
      this.loadingText = '正在加载模型...'
      const modelUrl = '/static/models/home.glb'
      let localUrl
      try {
        localUrl = await ensureLocalModel(modelUrl, (p) => {
          this.progress = p
          this.loadingText = `下载中 ${p}%`
        })
      } catch (e) {
        console.error('[space-3d] 远程模型下载失败', e)
        this.errorMsg = '模型下载失败，请检查网络或文件路径'
        this.loading = false
        return
      }
      // GLTFLoader 是单独加载的，不能从 THREE 取
      const loader = new GLTFLoader()
      // home.glb 是 Draco 压缩的 GLB，必须挂 DRACOLoader 才能解析
      // 使用 jsDelivr CDN 上的 draco 解码器（与 three.js r150+ 兼容），路径与 three 包内 examples/jsm/libs/draco 保持一致
      // 这里走 CDN 避免把 draco 整个目录打进包；如需完全离线，可改为 import 本地 decoder wasm
      const dracoLoader = new DRACOLoader()
      dracoLoader.setDecoderPath(
        'https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/libs/draco/',
      )
      loader.setDRACOLoader(dracoLoader)
      loader.load(
        localUrl,
        (gltf) => {
          // 异步回调期间用户可能已返回页面,scene 已置 null,
          // 直接 return 避免对已 dispose 的资源继续操作触发 warn
          if (this.three._disposed || !this.three.scene) return
          const model = gltf.scene

          // 1. 缩放到合理大小 (长边 ~12 单位)
          // 解释：模型原始 y=3.16（房间高 3 米），x=20.84（房间长）。
          // scale = 12 / maxAxis，会让模型保持原始比例：长边 12m，高 ≈ 1.82m
          // 这样相机视高 1.5m 就在房间中部偏下，符合真人视角
          const box = new THREE.Box3().setFromObject(model)
          const size = box.getSize(new THREE.Vector3())
          const center = box.getCenter(new THREE.Vector3())
          const maxAxis = Math.max(size.x, size.y, size.z)
          const scale = 12 / maxAxis
          model.scale.setScalar(scale)

          // 调试：输出原始包围盒（看模型是否旋转）
          // eslint-disable-next-line
          console.log('[space-3d] 原始模型包围盒', {
            min: { x: box.min.x, y: box.min.y, z: box.min.z },
            max: { x: box.max.x, y: box.max.y, z: box.max.z },
            size: { x: size.x, y: size.y, z: size.z },
            center: { x: center.x, y: center.y, z: center.z },
          })

          // 2. 居中 + 落地
          model.position.x = -center.x * scale
          model.position.z = -center.z * scale
          model.position.y = -box.min.y * scale

          // 3. 包围盒（缩放后）
          const finalBox = new THREE.Box3().setFromObject(model)
          const finalSize = finalBox.getSize(new THREE.Vector3())
          const finalCenter = finalBox.getCenter(new THREE.Vector3())

          // 4. 加入场景
          const group = new THREE.Group()
          group.add(model)
          scene.add(group)
          this.three.modelGroup = group
          this.three.modelBox = finalBox
          this.three.modelCenter = finalCenter
          this.three.modelSize = finalSize

          // 5. 顶点统计
          let vertexCount = 0
          model.traverse((child) => {
            if (child.isMesh && child.geometry) {
              vertexCount += child.geometry.attributes.position?.count || 0
            }
          })
          this.loadedVertices = vertexCount

          // 6. 初始位置：硬编码为门口内偏 0.5m 处（视高 0.5m）
          const startPos = new THREE.Vector3(-0.8, 0.5, -1.1)
          this.three.camera.position.copy(startPos)

          // 加载人物化身（放在相机脚下，人物头/肩位置 ≈ 相机视高）
          // 这里仅 fire-and-forget:化身失败不应阻塞场景,内部已用 Promise.allSettled 隔离每个 fbx
          this.loadFigureAvatar().catch((e) => {
            console.error('[space-3d] 加载人物化身异常', e)
          })

          // 7. 朝房间深处看（视线水平，看向 maxX 端，y 保持 0.5 避免仰/俯视）
          this.three.camera.lookAt(finalBox.max.x, 0.5, finalCenter.z)

          // 8. 配置移动边界（限制在模型包围盒内 + 0.5m 缓冲）
          this.three.bounds = {
            minX: finalBox.min.x - 0.5,
            maxX: finalBox.max.x + 1.0,
            minY: 0.5,
            maxY: 0.5 + 1.0, // 不允许抬头看到模型顶外（天花板）
            minZ: finalBox.min.z - 0.5,
            maxZ: finalBox.max.z + 0.5,
          }

          // 调试：输出缩放后的最终位置
          // eslint-disable-next-line
          console.log('[space-3d] 缩放后相机位置', {
            startPos: { x: startPos.x, y: startPos.y, z: startPos.z },
            modelBox: {
              min: { x: finalBox.min.x, y: finalBox.min.y, z: finalBox.min.z },
              max: { x: finalBox.max.x, y: finalBox.max.y, z: finalBox.max.z },
              size: { x: finalSize.x, y: finalSize.y, z: finalSize.z },
              center: { x: finalCenter.x, y: finalCenter.y, z: finalCenter.z },
            },
            scale,
          })

          this.positionText = `${startPos.x.toFixed(1)}, ${startPos.y.toFixed(1)}, ${startPos.z.toFixed(1)}`
          this.loading = false
        },
        (xhr) => {
          if (xhr.lengthComputable) {
            // 模型文件已落到本地,这里仅 GLTFLoader 解析阶段的进度,合并到 50~99%
            const ratio = xhr.loaded / xhr.total
            this.progress = Math.round(50 + ratio * 49)
            this.loadingText = `解析中 ${this.progress}%`
          }
        },
        (err) => {
          console.error('[space-3d] 模型加载失败', err)
          this.errorMsg = '模型加载失败，请检查网络或文件路径'
          this.loading = false
        },
      )
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
        if (!t.lookTouch.active || !t.lookTouch.touchId) return
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
        if (!t.lookTouch.touchId) return
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
      this.applyMovement()
      // applyLook 在 FPS 模式用 yaw/pitch 重设 quaternion；TPS 模式由 syncFigureAvatar 用 lookAt(figure)
      if (this.viewMode === 'fps') {
        this.applyLook()
      }
      this.syncFigureAvatar() // 同步人物化身位置/朝向到相机
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
     * 根据键盘 + 摇杆状态更新相机位置（第一人称移动）
     */
    applyMovement() {
      const t = this.three
      if (!t.controls || !t.camera) return

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

      // === 化身姿态:有输入 → 走路,无输入 → 静态 ===
      const moving = forward !== 0 || rightward !== 0
      const newState = moving ? 'walk' : 'idle'
      if (t.figureState !== newState) {
        t.figureState = newState
        this.syncFigureState()
      }

      if (!moving) return

      // 2. 取当前相机朝向向量（在 XZ 平面投影，避免上下飞）
      const cam = t.camera
      // 复用 Vector3 实例,减少每帧 GC 压力
      if (!t._moveVecs) {
        t._moveVecs = {
          direction: new THREE.Vector3(),
          right: new THREE.Vector3(),
          up: new THREE.Vector3(0, 1, 0),
          moveVec: new THREE.Vector3(),
        }
      }
      const { direction, right: rightVec, up, moveVec } = t._moveVecs
      cam.getWorldDirection(direction)
      // 锁定水平面 (y 分量清零)，这样移动是地面平行的
      direction.y = 0
      direction.normalize()

      // 3. right 向量 = direction × up
      rightVec.crossVectors(direction, up).normalize()

      // 4. 计算位移
      moveVec.set(0, 0, 0)
      moveVec.addScaledVector(direction, forward * t.moveSpeed)
      moveVec.addScaledVector(rightVec, rightward * t.moveSpeed)

      // 5. 应用位移
      cam.position.add(moveVec)

      // 6. 边界限制
      if (t.bounds) {
        cam.position.x = Math.max(t.bounds.minX, Math.min(t.bounds.maxX, cam.position.x))
        cam.position.z = Math.max(t.bounds.minZ, Math.min(t.bounds.maxZ, cam.position.z))
        // 视高根据视角模式：fps 锁 0.5；tps 由 syncFigureAvatar 设 1.2,不强制覆盖
        if (this.viewMode === 'fps') cam.position.y = t.bounds.minY
      }

      // 7. 更新 HUD 位置 - 节流到 200ms,避免每帧触发 Vue 响应式更新整页重渲染
      // 之前每帧都赋值 reactive 变量 → Vue 60fps 重排 → H5 页面卡顿 / 触摸延迟
      this.three._lastHudUpdate = this.three._lastHudUpdate || 0
      const now = Date.now()
      if (now - this.three._lastHudUpdate > 200) {
        this.three._lastHudUpdate = now
        this.positionText = `${cam.position.x.toFixed(1)}, ${cam.position.y.toFixed(1)}, ${cam.position.z.toFixed(1)}`
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
     * 重置相机到门口初始位置（与加载完成时的初始位置一致）
     */
    resetCamera() {
      const t = this.three
      if (!t.modelBox) return
      // 硬编码初始位置：门口内偏 0.5m，视高 0.5m
      t.camera.position.set(-0.8, 0.5, -1.1)
      t.camera.lookAt(t.modelBox.max.x, 0.5, t.modelCenter.z)
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
    onCanvasTouchStart(e) {
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
      // 身体：胶囊体(高 ~1m),脚底贴 y=0
      const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.22, 0.45, 6, 12), skin)
      body.position.y = 0.22 + 0.45 / 2 // 半径 + 圆柱段一半
      group.add(body)
      // 头：小球,放在身体顶部
      const head = new THREE.Mesh(new THREE.SphereGeometry(0.18, 16, 12), skin)
      head.position.y = body.position.y + 0.45 / 2 + 0.18
      group.add(head)
      // 朝向：默认 -Z 是前方,但我们的人物是用 yaw 旋转控制朝向,这里不需要再额外设 rotation
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
            let meshCount = 0
            fbx.traverse((child) => {
              if (child.isMesh) {
                meshCount++
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
            )
            // 拼到页面 debug 信息(显示在左上角)
            this.debugInfo = `化身 ${kind}: 原始${size.x.toFixed(2)}×${size.y.toFixed(2)}×${size.z.toFixed(2)} | scale=${scale.toFixed(3)} | 最终${figureSize.x.toFixed(2)}×${figureSize.y.toFixed(2)}×${figureSize.z.toFixed(2)}m`
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
     * 同步人物化身到相机位置（每帧调用）
     * 根据 viewMode 不同：
     * - fps 第一人称：模型就在相机脚下（看不到自己）
     * - tps 第三人称：模型走在前面，相机在身后略上方
     */
    syncFigureAvatar() {
      const t = this.three
      if (!t.camera) return
      const cam = t.camera

      // === 推进走路动画 mixer ===
      if (t.figureWalkMixer && t.figureWalkClock && t.figureState === 'walk') {
        t.figureWalkMixer.update(t.figureWalkClock.getDelta())
      }

      // 复用 forward Vector3,避免每帧 new
      if (!t._syncForward) t._syncForward = new THREE.Vector3()
      const forward = t._syncForward
      forward.set(-Math.sin(t.yaw), 0, -Math.cos(t.yaw))

      if (this.viewMode === 'tps') {
        // === 第三人称：相机固定在人物身后 1.5m + 高 1.2m ===
        const figureOffset = 1.5
        const figureX = cam.position.x + forward.x * figureOffset
        const figureZ = cam.position.z + forward.z * figureOffset
        // 性能:可见 group 才更新 transform,不可见 group 直接跳过
        // 同时只更新当前 figureState 对应的那一个 group(idle/walk 二选一)
        if (t.figureState === 'idle' && t.figureIdleGroup && t.figureIdleGroup.visible) {
          t.figureIdleGroup.position.set(figureX, 0, figureZ)
          t.figureIdleGroup.rotation.y = t.yaw - Math.PI / 2
        }
        if (t.figureState === 'walk' && t.figureWalkGroup && t.figureWalkGroup.visible) {
          t.figureWalkGroup.position.set(figureX, 0, figureZ)
          t.figureWalkGroup.rotation.y = t.yaw - Math.PI / 2
        }
        // 相机视高 1.2m,看向人物
        cam.position.y = 1.2
        cam.lookAt(figureX, 0.3, figureZ)
      } else {
        // === 第一人称：模型就在相机脚下(看不到自己) ===
        // 性能:fps 模式下相机看不到自己,本质不需要每帧更新 group 位置
        // 但 tps ↔ fps 切换时需要一次定位,所以保留更新但只在 figureState 对应 group
        if (t.figureState === 'idle' && t.figureIdleGroup && t.figureIdleGroup.visible) {
          t.figureIdleGroup.position.x = cam.position.x
          t.figureIdleGroup.position.z = cam.position.z
          t.figureIdleGroup.position.y = 0
          t.figureIdleGroup.rotation.y = t.yaw - Math.PI / 2
        }
        if (t.figureState === 'walk' && t.figureWalkGroup && t.figureWalkGroup.visible) {
          t.figureWalkGroup.position.x = cam.position.x
          t.figureWalkGroup.position.z = cam.position.z
          t.figureWalkGroup.position.y = 0
          t.figureWalkGroup.rotation.y = t.yaw - Math.PI / 2
        }
      }
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
      if (t.figureWalkAction) {
        if (t.figureState === 'walk') {
          if (!t.figureWalkAction.isRunning()) {
            t.figureWalkAction.reset()
            t.figureWalkAction.play()
          }
        } else {
          t.figureWalkAction.stop()
        }
      }
    },

    /**
     * 切换第一人称 / 第三人称
     */
    toggleViewMode() {
      const next = this.viewMode === 'fps' ? 'tps' : 'fps'
      this.viewMode = next
      // 切换时调整相机 y 到对应视高，避免看到模型底部或顶部
      const t = this.three
      if (t.camera) {
        if (next === 'fps') {
          t.camera.position.y = 0.5 // 人物眼睛高度
        } else {
          t.camera.position.y = 1.2 // 第三人称俯瞰
        }
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
.space-3d {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #1a1a1a;
  color: #f6f2ee;
  overflow: hidden;
  // 整页禁止浏览器处理触摸手势（滚动/缩放/下拉刷新），全部交给我们自己的 touch 监听
  touch-action: none;
  overscroll-behavior: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 16rpx;
  padding-top: var(--status-bar-height, 0);
  background: rgba(0, 0, 0, 0.5);
  z-index: 10;
  flex-shrink: 0;
}
.header-title {
  font-size: 26rpx;
  font-weight: var(--font-weight-semibold);
  flex: 1;
  text-align: center;
  /* 标题过长时裁掉，避免挤掉右侧按钮 */
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  padding: 0 8rpx;
}
.header-actions {
  display: flex;
  gap: 4rpx;
  flex-shrink: 0;
}
.back-btn,
.icon-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  flex-shrink: 0;
}
.icon-btn-active {
  background: rgba(219, 201, 138, 0.25);
  color: #dbc98a;
}

.canvas-wrap {
  flex: 1;
  position: relative;
  overflow: hidden;
  touch-action: none;
}
.canvas {
  width: 100%;
  height: 100%;
  position: relative;
}

.debug-info {
  position: absolute;
  top: 16rpx;
  left: 16rpx;
  right: 16rpx;
  padding: 12rpx 16rpx;
  background: rgba(0, 0, 0, 0.6);
  color: #dbc98a;
  font-size: 20rpx;
  border-radius: 8rpx;
  font-family: monospace;
  z-index: 100;
  word-break: break-all;
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
  font-size: 28rpx;
}
.progress-bar {
  margin-top: 16rpx;
  width: 360rpx;
  height: 6rpx;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3rpx;
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
  font-size: 26rpx;
  margin-bottom: 24rpx;
}
.retry-btn {
  display: inline-block;
  padding: 16rpx 48rpx;
  background: #dbc98a;
  color: #1a1a1a;
  border-radius: 999rpx;
  font-weight: var(--font-weight-medium);
}

/* ====== 第一人称 HUD ====== */
.hud {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.hud-top {
  position: absolute;
  top: 24rpx;
  left: 24rpx;
  right: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  pointer-events: none;
}
.hud-pill {
  align-self: flex-start;
  padding: 12rpx 24rpx;
  background: rgba(0, 0, 0, 0.6);
  border: 2rpx solid rgba(219, 201, 138, 0.3);
  border-radius: 999rpx;
}
.hud-pill-active {
  background: rgba(219, 201, 138, 0.25);
  border-color: rgba(219, 201, 138, 0.7);
}
.hud-pill-text {
  font-size: 22rpx;
  color: #f6f2ee;
}

.crosshair {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 40rpx;
  height: 40rpx;
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
  height: 2rpx;
  transform: translateY(-50%);
}
.crosshair-v {
  left: 50%;
  top: 0;
  bottom: 0;
  width: 2rpx;
  transform: translateX(-50%);
}

/* ===== 方向控制摇杆 (左下角圆盘) ===== */
.joystick-pad {
  position: absolute;
  bottom: 60rpx;
  left: 60rpx;
  width: 240rpx;
  height: 240rpx;
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
  background: rgba(0, 0, 0, 0.35);
  border: 4rpx solid rgba(219, 201, 138, 0.45);
  box-shadow: inset 0 0 16rpx rgba(0, 0, 0, 0.4);
  /* 用 transform 中心点 */
  display: flex;
  align-items: center;
  justify-content: center;
}
.joystick-knob {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 96rpx;
  height: 96rpx;
  margin-left: -48rpx;
  margin-top: -48rpx;
  border-radius: 50%;
  background: rgba(219, 201, 138, 0.85);
  border: 2rpx solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 6rpx 16rpx rgba(0, 0, 0, 0.35);
  /* 松手时缓动回中 */
  transition: transform 0.15s ease-out;
  pointer-events: none;
}
/* 拖动中关掉 transition,保证手柄跟手不滞后 */
.joystick-knob.joystick-knob-drag {
  transition: none;
}
</style>
