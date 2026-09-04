<template>
  <view class="space-3d">
    <!-- 顶部导航 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="header-title">宠物空间 3D · 第一人称</text>
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
    <view class="canvas-wrap">
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
        </view>

        <!-- 中心十字准星 -->
        <view class="crosshair">
          <view class="crosshair-h" />
          <view class="crosshair-v" />
        </view>

        <!-- 右下：方向摇杆 (WASD/方向键) -->
        <view class="joystick-area">
          <view
            class="joystick-base"
            @touchstart.stop="onJoystickStart"
            @touchmove.stop="onJoystickMove"
            @touchend.stop="onJoystickEnd"
            @touchcancel.stop="onJoystickEnd"
          >
            <view
              class="joystick-stick"
              :style="{
                transform: `translate(${joystickDx - 30}rpx, ${joystickDy - 30}rpx)`,
              }"
            />
            <text class="joystick-label">{{ joystickLabel }}</text>
          </view>
        </view>

        <!-- 左下：前进/后退快捷按钮 (移动端备用) -->
        <view class="action-buttons">
          <view
            class="action-btn"
            @touchstart.stop="onActionDown('forward')"
            @touchend.stop="onActionUp('forward')"
          >
            <text>▲</text>
          </view>
          <view
            class="action-btn"
            @touchstart.stop="onActionDown('backward')"
            @touchend.stop="onActionUp('backward')"
          >
            <text>▼</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
// 不在顶层 import three.js —— 顶层 import 失败会导致整个页面渲染不出来
let THREE = null
let PointerLockControls = null
let GLTFLoader = null

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
      joystickDx: 30,
      joystickDy: 30,
      joystickLabel: '拖动控制方向',
      // 视角模式：fps = 第一人称（动物眼睛）/ tps = 第三人称跟随（能看到自己）
      viewMode: 'fps',
    }
  },

  computed: {
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
          joystick: { x: 0, y: 0, active: false, touchId: null },
          moveSpeed: 0.05, // 每次步进的位移
          lookSensitivity: 0.003, // 触摸旋转灵敏度（每像素多少弧度）
          // 视角触摸状态：PointerLockControls 在 webview 不可用，自己实现触摸看视角
          lookTouch: { active: false, touchId: null, lastX: 0, lastY: 0 },
          // 累计 yaw/pitch（弧度）
          yaw: 0,
          pitch: 0,
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
  },

  onShow() {
    const t = this.three
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
        )

        if (typeof document === 'undefined') {
          this.errorMsg = '当前环境不支持 3D（仅 H5/APP 支持）'
          this.loading = false
          this.debugInfo = `typeof document=undefined`
          return
        }

        const canvasEl = document.getElementById(this.canvasId)
        if (!canvasEl) {
          this.debugInfo = `找不到节点 #${this.canvasId}，200ms 后重试`
          setTimeout(() => this.startScene(), 200)
          return
        }

        this.debugInfo = `容器尺寸 ${canvasEl.clientWidth}x${canvasEl.clientHeight}`

        const width = canvasEl.clientWidth || window.innerWidth
        const height = canvasEl.clientHeight || window.innerHeight

        // 渲染器
        const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
        renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
        renderer.setSize(width, height)
        renderer.outputColorSpace = THREE.SRGBColorSpace
        renderer.toneMapping = THREE.ACESFilmicToneMapping
        renderer.toneMappingExposure = 1.0
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

        // 环境光：提高强度，确保小狗模型即使没有纹理也可见
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
     */
    loadModel(renderer, scene) {
      this.loadingText = '正在加载模型...'
      const modelUrl = '/static/models/modern_apartment.glb'
      // GLTFLoader 是单独加载的，不能从 THREE 取
      const loader = new GLTFLoader()
      loader.load(
        modelUrl,
        (gltf) => {
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

          // 6. 门口定位：相机在房间门口**内** 0.5m（往 minX 方向缩进 0.5m）
          // 视高 0.5m（宠物视角，接近地板）
          const doorX = finalBox.min.x + 0.5
          const doorY = 0.5
          const doorZ = finalCenter.z
          const startPos = new THREE.Vector3(doorX, doorY, doorZ)
          this.three.camera.position.copy(startPos)

          // 加载小狗化身（放在相机脚下，狗头位置 ≈ 相机视高）
          this.loadPetAvatar()

          // 7. 朝房间深处看（视线水平，水平方向看向 maxX 端，y 保持 doorY 避免仰/俯视）
          this.three.camera.lookAt(finalBox.max.x, doorY, finalCenter.z)

          // 8. 配置移动边界（限制在模型包围盒内 + 0.5m 缓冲）
          this.three.bounds = {
            minX: finalBox.min.x - 0.5,
            maxX: finalBox.max.x + 1.0,
            minY: doorY,
            maxY: doorY + 1.0, // 不允许抬头看到模型顶外（天花板）
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
            this.progress = Math.round((xhr.loaded / xhr.total) * 100)
            this.loadingText = `加载中 ${this.progress}%`
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
     */
    bindKeyboard() {
      const t = this.three
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
        if (this._loading) return // 加载中不响应
        // 找到第一个不在摇杆/按钮区的 touch
        for (const touch of e.touches) {
          if (this.isInControlArea(touch.clientX, touch.clientY)) continue
          t.lookTouch.active = true
          t.lookTouch.touchId = touch.identifier
          t.lookTouch.lastX = touch.clientX
          t.lookTouch.lastY = touch.clientY
          break
        }
      }
      this._onWindowTouchMove = (e) => {
        if (!t.lookTouch.active) return
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
      }
      this._onWindowTouchEnd = (e) => {
        let stillActive = false
        for (const touch of e.touches) {
          if (touch.identifier === t.lookTouch.touchId) {
            stillActive = true
            break
          }
        }
        if (!stillActive) {
          t.lookTouch.active = false
          t.lookTouch.touchId = null
        }
      }
      window.addEventListener('touchstart', this._onWindowTouchStart, { passive: true })
      window.addEventListener('touchmove', this._onWindowTouchMove, { passive: true })
      window.addEventListener('touchend', this._onWindowTouchEnd, { passive: true })
      window.addEventListener('touchcancel', this._onWindowTouchEnd, { passive: true })
    },

    unbindKeyboard() {
      if (this._onKeyDown) window.removeEventListener('keydown', this._onKeyDown)
      if (this._onKeyUp) window.removeEventListener('keyup', this._onKeyUp)
      if (this._onWindowTouchStart)
        window.removeEventListener('touchstart', this._onWindowTouchStart)
      if (this._onWindowTouchMove) window.removeEventListener('touchmove', this._onWindowTouchMove)
      if (this._onWindowTouchEnd) window.removeEventListener('touchend', this._onWindowTouchEnd)
      if (this._onWindowTouchEnd) window.removeEventListener('touchcancel', this._onWindowTouchEnd)
    },

    /**
     * 渲染循环
     */
    animate() {
      this.three.animationId = requestAnimationFrame(() => this.animate())
      this.applyMovement()
      // applyLook 在 FPS 模式用 yaw/pitch 重设 quaternion；TPS 模式由 syncPetAvatar 用 lookAt(pet)
      if (this.viewMode === 'fps') {
        this.applyLook()
      }
      this.syncPetAvatar() // 同步小狗化身位置/朝向到相机
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
      // 用 quaternion 设置朝向，避免 euler 万向锁
      const quaternion = new THREE.Quaternion()
      // YXZ 顺序：先 yaw 再 pitch，符合 FPS 习惯
      const euler = new THREE.Euler(t.pitch, t.yaw, 0, 'YXZ')
      quaternion.setFromEuler(euler)
      t.camera.quaternion.copy(quaternion)
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

      if (forward === 0 && rightward === 0) return

      // 2. 取当前相机朝向向量（在 XZ 平面投影，避免上下飞）
      const cam = t.camera
      const direction = new THREE.Vector3()
      cam.getWorldDirection(direction)
      // 锁定水平面 (y 分量清零)，这样移动是地面平行的
      direction.y = 0
      direction.normalize()

      // 3. right 向量 = direction × up
      const right = new THREE.Vector3()
      right.crossVectors(direction, new THREE.Vector3(0, 1, 0)).normalize()

      // 4. 计算位移
      const moveVec = new THREE.Vector3()
      moveVec.addScaledVector(direction, forward * t.moveSpeed)
      moveVec.addScaledVector(right, rightward * t.moveSpeed)

      // 5. 应用位移
      cam.position.add(moveVec)

      // 6. 边界限制
      if (t.bounds) {
        cam.position.x = Math.max(t.bounds.minX, Math.min(t.bounds.maxX, cam.position.x))
        cam.position.z = Math.max(t.bounds.minZ, Math.min(t.bounds.maxZ, cam.position.z))
        // 视高根据视角模式：fps 锁 0.5（动物眼睛）；tps 由 syncPetAvatar 设 0.8，不强制覆盖
        if (this.viewMode === 'fps') cam.position.y = t.bounds.minY
      }

      // 7. 更新 HUD 位置
      this.positionText = `${cam.position.x.toFixed(1)}, ${cam.position.y.toFixed(1)}, ${cam.position.z.toFixed(1)}`
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
     * 重置相机到门口
     */
    resetCamera() {
      const t = this.three
      if (!t.modelBox) return
      // 门口**内** 0.5m + 视高 0.5m（宠物视角）
      const doorX = t.modelBox.min.x + 0.5
      const doorY = 0.5
      const doorZ = t.modelCenter.z
      t.camera.position.set(doorX, doorY, doorZ)
      t.camera.lookAt(t.modelBox.max.x, doorY, t.modelCenter.z)
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

    // ============== 触摸摇杆 ==============
    onCanvasTouchStart(e) {
      const t = this.three
      // 1. 尝试 PointerLock（H5 桌面浏览器有效，APP webview 多半失败，会自动 catch）
      if (t.controls && !this.pointerLocked) {
        try {
          t.controls.lock()
        } catch (err) {
          console.warn('PointerLock 不可用，改用触摸旋转', err)
        }
      }
      // 2. 找到落在非摇杆/非按钮区的第一个 touch，作为视角旋转的输入
      for (const touch of e.touches) {
        if (this.isInControlArea(touch.clientX, touch.clientY)) continue
        // 第一个落在空白区的 touch = 视角控制 touch
        t.lookTouch.active = true
        t.lookTouch.touchId = touch.identifier
        t.lookTouch.lastX = touch.clientX
        t.lookTouch.lastY = touch.clientY
        break
      }
    },

    onCanvasTouchMove(e) {
      const t = this.three
      if (!t.lookTouch.active) return
      // 找到对应的 touch
      const touch = Array.from(e.touches).find((tt) => tt.identifier === t.lookTouch.touchId)
      if (!touch) return
      const dx = touch.clientX - t.lookTouch.lastX
      const dy = touch.clientY - t.lookTouch.lastY
      t.lookTouch.lastX = touch.clientX
      t.lookTouch.lastY = touch.clientY

      // 累积 yaw（水平）和 pitch（垂直）
      // 手指拖向右 (dx > 0) → 视角应该向右转（和鼠标拖动一致） → yaw 减小（右手系绕 Y 轴反向）
      // 手指拖向下 (dy > 0) → 视角应该向下看 → pitch 减小
      // 注意：用 quaternion setFromEuler(pitch, yaw, 0, 'YXZ') 时，
      //   yaw 正值 = 顺时针从上往下看（右手系），所以手指拖右要 yaw 减小
      t.yaw += dx * t.lookSensitivity
      t.pitch += dy * t.lookSensitivity
      // 限制 pitch 在 [-PI/2, PI/2]，防止翻转
      const halfPi = Math.PI / 2
      if (t.pitch > halfPi) t.pitch = halfPi
      if (t.pitch < -halfPi) t.pitch = -halfPi
    },

    onCanvasTouchEnd(e) {
      const t = this.three
      // 如果松开的 touch 是当前视角控制 touch，则停止
      let stillActive = false
      for (const touch of e.touches) {
        if (touch.identifier === t.lookTouch.touchId) {
          stillActive = true
          break
        }
      }
      if (!stillActive) {
        t.lookTouch.active = false
        t.lookTouch.touchId = null
      }
    },

    /**
     * 判断 (x, y) 是否落在 UI 控制区（摇杆/前进后退按钮）
     * 摇杆在右下 240rpx、按钮在左下各 96rpx
     * 这里用页面坐标做简化判断（rpx 已按 750 设计 宽 折算）
     */
    isInControlArea(x, y) {
      const winW = window.innerWidth
      const winH = window.innerHeight
      // 右下摇杆：右 60rpx、底 60rpx、240rpx 见方（按 750 设计宽 折算 px）
      const rpx2px = winW / 750
      const joyR = 60 * rpx2px
      const joyS = 240 * rpx2px
      if (x > winW - joyR - joyS && x < winW - joyR && y > winH - joyR - joyS && y < winH - joyR)
        return true
      // 左下前进/后退：左 60rpx、底 60rpx、宽 96rpx
      const btnL = 60 * rpx2px
      const btnW = 96 * rpx2px
      const btnH = 96 * rpx2px
      if (x > btnL && x < btnL + btnW && y > winH - joyR - btnH * 2 - 16 && y < winH - joyR)
        return true
      return false
    },

    onJoystickStart(e) {
      const touch = e.touches[0]
      this.three.joystick.touchId = touch.identifier
      this.three.joystick.active = true
      this.updateJoystick(touch, e.currentTarget)
    },

    onJoystickMove(e) {
      const t = this.three
      if (!t.joystick.active) return
      const touch = Array.from(e.touches).find((tt) => tt.identifier === t.joystick.touchId)
      if (!touch) return
      this.updateJoystick(touch, e.currentTarget)
    },

    onJoystickEnd() {
      this.three.joystick.active = false
      this.three.joystick.x = 0
      this.three.joystick.y = 0
      this.three.joystick.touchId = null
      this.joystickDx = 30
      this.joystickDy = 30
      this.joystickLabel = '拖动控制方向'
    },

    updateJoystick(touch, baseEl) {
      const rect = baseEl.getBoundingClientRect()
      const cx = rect.left + rect.width / 2
      const cy = rect.top + rect.height / 2
      let dx = touch.clientX - cx
      let dy = touch.clientY - cy
      const radius = Math.min(rect.width, rect.height) / 2 - 20
      const dist = Math.hypot(dx, dy)
      if (dist > radius) {
        dx = (dx / dist) * radius
        dy = (dy / dist) * radius
      }
      this.three.joystick.x = dx / radius
      this.three.joystick.y = dy / radius
      this.joystickDx = 30 + dx
      this.joystickDy = 30 + dy
      const labelX = this.three.joystick.x.toFixed(1)
      const labelY = this.three.joystick.y.toFixed(1)
      this.joystickLabel = `X:${labelX} Y:${labelY}`
    },

    // ============== 前进/后退快捷按钮（移动端备用） ==============
    onActionDown(dir) {
      if (dir === 'forward') this.three.keys.w = true
      if (dir === 'backward') this.three.keys.s = true
    },
    onActionUp(dir) {
      if (dir === 'forward') this.three.keys.w = false
      if (dir === 'backward') this.three.keys.s = false
    },

    /**
     * 释放 three.js 资源
     */
    /**
     * 加载小狗化身 GLB 模型
     * 位置：相机脚下（y=0），相机视高 ≈ 狗头高度
     * 模型跟着相机移动，但不渲染相机本身（第一人称看不到自己）
     */
    loadPetAvatar() {
      const t = this.three
      if (!t.scene) return
      const petUrl = '/static/models/puppy.glb'
      // eslint-disable-next-line
      console.log('[space-3d] 开始加载 puppy, url=', petUrl)
      const loader = new GLTFLoader()

      loader.load(
        petUrl,
        (gltf) => {
          const pet = gltf.scene
          // eslint-disable-next-line
          console.log('[space-3d] puppy GLTF 响应，scene type=', pet.type)

          // 自动缩放到合适大小（小狗身高 ~0.5m，对应视高）
          const box = new THREE.Box3().setFromObject(pet)
          const size = box.getSize(new THREE.Vector3())
          // eslint-disable-next-line
          console.log('[space-3d] puppy 原始包围盒 min=', box.min, 'max=', box.max, 'size=', size)
          const maxAxis = Math.max(size.x, size.y, size.z)
          // GLB 模型单位可能不是米，强制从更小 scale 开始
          // 0.5 倍缩放，最大边约 0.5m
          const scale = 0.5
          pet.scale.set(scale, scale, scale) // 用三元组 set
          pet.scale.setScalar(scale) // 二次保险
          pet.rotation.x = 0
          pet.rotation.y = 0
          pet.rotation.z = 0
          // 缩放后重新计算包围盒
          pet.updateMatrixWorld(true)
          const finalBox = new THREE.Box3().setFromObject(pet)
          // 把模型**底部**对齐到 y=0（即使物体小，也别埋地里）
          pet.position.y = -finalBox.min.y
          // eslint-disable-next-line
          console.log('[space-3d] puppy scale SET to', scale, '最终尺寸:', {
            x: finalBox.max.x - finalBox.min.x,
            y: finalBox.max.y - finalBox.min.y,
            z: finalBox.max.z - finalBox.min.z,
          })

          // 保留模型原始材质（不再强制覆盖）
          // 但确保有法线 + frustumCulled=false
          let meshCount = 0
          pet.traverse((child) => {
            if (child.isMesh) {
              meshCount++
              if (!child.geometry.attributes.normal) {
                child.geometry.computeVertexNormals()
              }
              // 关闭 frustum culling，避免穿过屏幕时消失
              child.frustumCulled = false
              // 如果材质缺失，给个兜底
              if (!child.material) {
                child.material = new THREE.MeshStandardMaterial({ color: 0x8b4513 })
              }
            }
          })
          // eslint-disable-next-line
          console.log(
            '[space-3d] puppy mesh count=',
            meshCount,
            'scale=',
            scale,
            'finalBox.min.y=',
            finalBox.min.y,
            'pet.position.y=',
            pet.position.y,
          )

          // 初始位置：放在场景中心（syncPetAvatar 每帧会更新）
          pet.position.set(0, 0, 0)
          // 直接设置一个固定的抬升量，把小狗浮到地面以上
          // 数值根据实际渲染效果调整
          // eslint-disable-next-line
          console.log(
            '[space-3d] puppy finalBox.min.y=',
            finalBox.min.y,
            ', height=',
            finalBox.max.y - finalBox.min.y,
          )
          t.petGroundOffset = 0.15 // 硬编码：往上抬 0.15m（小狗一半身体高度）
          // eslint-disable-next-line
          console.log('[space-3d] petGroundOffset hardcoded to 0.15')

          t.scene.add(pet)
          t.petAvatar = pet
          // eslint-disable-next-line
          console.log('[space-3d] puppy DEBUG 固定放在相机前方 1.5m, pet.position=', pet.position)
        },
        (xhr) => {
          // eslint-disable-next-line
          console.log('[space-3d] puppy 加载进度', xhr.loaded, '/', xhr.total)
        },
        (err) => {
          console.error('[space-3d] puppy 模型加载失败', err)
          uni.showToast({ title: '小狗模型加载失败', icon: 'none' })
        },
      )
    },

    /**
     * 同步小狗化身到相机位置（每帧调用）
     * 根据 viewMode 不同：
     * - fps 第一人称：模型就在相机脚下（看不到自己）
     * - tps 第三人称：模型走在前面，相机在身后略上方
     */
    syncPetAvatar() {
      const t = this.three
      if (!t.petAvatar || !t.camera) return
      const cam = t.camera
      const pet = t.petAvatar
      const petGroundY = t.petGroundOffset

      if (this.viewMode === 'tps') {
        // === 第三人称：相机固定在小狗身后 1.5m + 高 1.2m ===
        // 玩家移动 = 小狗移动（applyMovement 移动 camera，syncPetAvatar 反向算小狗位置）
        // 玩家拖屏 = 相机围绕小狗转

        // 计算相机水平视线方向（基于 yaw）
        const forward = new THREE.Vector3(-Math.sin(t.yaw), 0, -Math.cos(t.yaw))

        // 1. 玩家输入控制的"主角"是相机（applyMovement 移动 camera.x/z）
        // 2. 小狗位置 = 相机位置 + 视线前方 1.5m（小狗在相机前方）
        const petOffset = 1.5
        pet.position.x = cam.position.x + forward.x * petOffset
        pet.position.z = cam.position.z + forward.z * petOffset
        pet.position.y = petGroundY // 脚下踩地

        // 3. 小狗朝向：面朝前进方向
        // GLB 模型小狗默认朝 +x 方向（建模习惯），要旋转 -90° 才能和相机视线对齐
        pet.rotation.y = t.yaw - Math.PI / 2

        // 4. 相机视高：1.2m（俯瞰小狗）
        cam.position.y = 1.2

        // 5. 相机看向小狗（保证小狗在画面中央）
        cam.lookAt(pet.position.x, 0.3, pet.position.z)
      } else {
        // === 第一人称：模型在相机脚下（看不到自己） ===
        pet.position.x = cam.position.x
        pet.position.z = cam.position.z
        pet.position.y = petGroundY
        // GLB 模型小狗默认朝 +x，需要偏移 -90° 才能和相机视线对齐
        pet.rotation.y = t.yaw - Math.PI / 2
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
          t.camera.position.y = 0.5 // 动物眼睛高度
        } else {
          t.camera.position.y = 1.2 // 第三人称俯瞰
        }
      }
      uni.showToast({
        title: next === 'fps' ? '🐶 第一人称（动物眼睛）' : '👀 第三人称跟随',
        icon: 'none',
      })
    },

    disposeScene() {
      const t = this.three
      this.unbindKeyboard()
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
      }
      // 释放小狗化身
      if (t.petAvatar) {
        t.petAvatar.traverse((child) => {
          if (child.isMesh) {
            if (child.geometry) child.geometry.dispose()
            if (child.material) {
              const mats = Array.isArray(child.material) ? child.material : [child.material]
              mats.forEach((m) => m.dispose())
            }
          }
        })
        t.petAvatar = null
      }
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

.joystick-area {
  position: absolute;
  bottom: 60rpx;
  right: 60rpx;
  width: 240rpx;
  height: 240rpx;
  pointer-events: auto;
}
.joystick-base {
  position: relative;
  width: 240rpx;
  height: 240rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  border: 4rpx solid rgba(255, 255, 255, 0.15);
  box-sizing: border-box;
  overflow: hidden;
}
.joystick-stick {
  position: absolute;
  top: 0;
  left: 0;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: radial-gradient(circle, #dbc98a, #b38a5a);
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.4);
  transition: transform 0.05s linear;
}
.joystick-label {
  position: absolute;
  bottom: -36rpx;
  left: 50%;
  transform: translateX(-50%);
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.5);
  white-space: nowrap;
}

.action-buttons {
  position: absolute;
  bottom: 60rpx;
  left: 60rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  pointer-events: auto;
}
.action-btn {
  width: 96rpx;
  height: 96rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 2rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  color: #dbc98a;
  user-select: none;
}
.action-btn:active {
  background: rgba(219, 201, 138, 0.25);
}
</style>
