<template>
  <el-dialog
    v-model="dialogVisible"
    title="查看 3D 模型"
    width="900px"
    append-to-body
    class="model-preview-dialog"
    @opened="startPreview"
    @closed="dispose"
  >
    <div v-if="model" class="preview-meta">
      <div class="meta-item"><span>模型名称</span><b>{{ model.displayName || '-' }}</b></div>
      <div class="meta-item"><span>用途</span><b>{{ purposeName }}</b></div>
      <div class="meta-item"><span>格式</span><b>{{ model.fileFormat || '-' }}</b></div>
      <div class="meta-item"><span>大小</span><b>{{ formatSize(model.fileSize) }}</b></div>
      <div class="meta-item"><span>版本</span><b>{{ model.version || '-' }}</b></div>
      <div class="meta-item"><span>状态</span><b>{{ model.status === 'ENABLED' ? '启用' : '停用' }}</b></div>
    </div>

    <div v-if="model" ref="viewportRef" class="preview-viewport">
      <div v-if="loading" class="preview-overlay">
        <div class="preview-loading-text">模型加载中 {{ progress }}%</div>
        <div class="preview-progress">
          <div class="preview-progress-bar" :style="{ width: progress + '%' }" />
        </div>
      </div>
      <div v-else-if="errorMsg" class="preview-overlay preview-error">
        <div class="preview-error-title">模型预览失败</div>
        <div class="preview-error-text">{{ errorMsg }}</div>
        <div class="preview-error-text">可尝试在新窗口打开模型文件排查</div>
      </div>
    </div>

    <div v-if="model" class="preview-url" :title="model.fileUrl">{{ model.fileUrl }}</div>

    <template #footer>
      <el-button v-if="model" plain @click="openInNewWindow">新窗口打开</el-button>
      <el-button v-if="model" plain @click="copyUrl">复制地址</el-button>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  visible: { type: Boolean, default: false },
  model: { type: Object, default: null },
  purposeName: { type: String, default: '-' }
})
const emit = defineEmits(['update:visible'])

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const viewportRef = ref(null)
const loading = ref(true)
const progress = ref(0)
const errorMsg = ref('')

// three 相关对象不放进响应式，避免 Proxy 包装导致内部只读属性报错
let THREE = null
let renderer = null
let scene = null
let camera = null
let controls = null
let frameId = null
let resizeObserver = null
let loadedRoot = null
let dracoLoader = null

// 预览归一化尺寸：把任意模型缩放到最大边 3.2，保证相机视角稳定
const PREVIEW_TARGET_SIZE = 3.2

function formatSize(bytes) {
  const n = Number(bytes) || 0
  if (n <= 0) return '-'
  if (n < 1024 * 1024) return (n / 1024).toFixed(0) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

/** 延迟加载 three 与对应 Loader，避免管理端首屏就加载 3D 依赖 */
async function ensureModules() {
  if (THREE) return
  const three = await import('three')
  THREE = three
}

async function startPreview() {
  if (!props.model || !viewportRef.value) return
  dispose()
  loading.value = true
  progress.value = 0
  errorMsg.value = ''

  try {
    await ensureModules()
    initScene()
    await loadModel()
  } catch (e) {
    console.error('[3d-preview] 初始化失败', e)
    errorMsg.value = e?.message || String(e)
    loading.value = false
  }
}

function initScene() {
  const el = viewportRef.value
  const width = el.clientWidth || 800
  const height = el.clientHeight || 460

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setSize(width, height)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  el.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x1a1a1a)

  camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100)
  camera.position.set(3.2, 2.2, 4.2)

  scene.add(new THREE.AmbientLight(0xffffff, 1.1))
  scene.add(new THREE.HemisphereLight(0xffeebb, 0x080820, 0.8))
  const dir = new THREE.DirectionalLight(0xffffff, 1.4)
  dir.position.set(4, 7, 5)
  scene.add(dir)

  const grid = new THREE.GridHelper(8, 16, 0x666666, 0x333333)
  grid.position.y = 0
  scene.add(grid)

  // OrbitControls 通过动态 import，避免把示例模块打进主包
  import('three/examples/jsm/controls/OrbitControls.js').then(({ OrbitControls }) => {
    if (!renderer || !camera) return
    controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.08
    controls.target.set(0, 1.1, 0)
    controls.update()
  })

  resizeObserver = new ResizeObserver(onResize)
  resizeObserver.observe(el)
  animate()
}

async function loadModel() {
  const url = props.model.fileUrl
  const format = String(props.model.fileFormat || url.split('.').pop() || '').toUpperCase()
  const onProgress = (xhr) => {
    if (xhr && xhr.lengthComputable && xhr.total > 0) {
      progress.value = Math.min(99, Math.round((xhr.loaded / xhr.total) * 100))
    }
  }

  if (format === 'FBX') {
    const { FBXLoader } = await import('three/examples/jsm/loaders/FBXLoader.js')
    new FBXLoader().load(url, onLoaded, onProgress, onLoadError)
    return
  }

  const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js')
  const { DRACOLoader } = await import('three/examples/jsm/loaders/DRACOLoader.js')
  const loader = new GLTFLoader()
  dracoLoader = new DRACOLoader()
  // Draco 解码器由后端 /static/draco/ 提供，离线环境也可预览压缩 GLB
  dracoLoader.setDecoderPath('/static/draco/')
  loader.setDRACOLoader(dracoLoader)
  loader.load(url, (gltf) => onLoaded(gltf.scene), onProgress, onLoadError)
}

function onLoaded(root) {
  if (!root || !scene) return

  // FBX 常见无材质/无法线，补默认材质保证可见
  root.traverse((child) => {
    if (!child.isMesh) return
    child.frustumCulled = false
    if (child.geometry && !child.geometry.attributes.normal) {
      child.geometry.computeVertexNormals()
    }
    if (!child.material) {
      child.material = new THREE.MeshStandardMaterial({ color: 0xd9b48a, roughness: 0.7, metalness: 0.1 })
    }
  })

  // 归一化：按最大边缩放并把底部放在网格上
  const box = new THREE.Box3().setFromObject(root)
  const size = box.getSize(new THREE.Vector3())
  const maxDim = Math.max(size.x, size.y, size.z) || 1
  root.scale.setScalar(PREVIEW_TARGET_SIZE / maxDim)
  root.updateMatrixWorld(true)

  const finalBox = new THREE.Box3().setFromObject(root)
  const center = finalBox.getCenter(new THREE.Vector3())
  root.position.x -= center.x
  root.position.z -= center.z
  root.position.y -= finalBox.min.y

  scene.add(root)
  loadedRoot = root

  if (controls) {
    controls.target.set(0, Math.max(0.8, Math.min(1.8, finalBox.max.y * 0.5)), 0)
    controls.update()
  }
  progress.value = 100
  loading.value = false
}

function onLoadError(err) {
  console.error('[3d-preview] 模型加载失败', err)
  errorMsg.value = '模型文件加载失败，请检查文件是否可访问、格式是否匹配'
  loading.value = false
}

function onResize() {
  const el = viewportRef.value
  if (!el || !renderer || !camera) return
  const width = el.clientWidth
  const height = el.clientHeight
  if (!width || !height) return
  renderer.setSize(width, height)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

function animate() {
  frameId = requestAnimationFrame(animate)
  if (controls) controls.update()
  if (renderer && scene && camera) renderer.render(scene, camera)
}

function dispose() {
  if (frameId) {
    cancelAnimationFrame(frameId)
    frameId = null
  }
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (controls) {
    controls.dispose()
    controls = null
  }
  if (loadedRoot) {
    loadedRoot.traverse((child) => {
      if (child.isMesh) {
        child.geometry?.dispose?.()
        const mats = Array.isArray(child.material) ? child.material : [child.material]
        mats.forEach((m) => {
          if (!m) return
          Object.values(m).forEach((v) => {
            if (v && v.isTexture) v.dispose()
          })
          m.dispose?.()
        })
      }
    })
    loadedRoot = null
  }
  if (dracoLoader) {
    try {
      dracoLoader.dispose()
    } catch (e) {
      /* ignore */
    }
    dracoLoader = null
  }
  if (renderer) {
    renderer.dispose()
    const canvas = renderer.domElement
    if (canvas && canvas.parentNode) canvas.parentNode.removeChild(canvas)
    renderer = null
  }
  scene = null
  camera = null
}

function openInNewWindow() {
  if (!props.model?.fileUrl) return
  window.open(props.model.fileUrl, '_blank', 'noopener,noreferrer')
}

async function copyUrl() {
  if (!props.model?.fileUrl) return
  try {
    await navigator.clipboard.writeText(props.model.fileUrl)
    ElMessage.success('模型地址已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制地址')
  }
}
</script>

<style scoped>
.preview-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 18px;
  margin-bottom: 14px;
}
.meta-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: var(--text-500);
  min-width: 0;
}
.meta-item b {
  color: var(--text-700);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-viewport {
  position: relative;
  width: 100%;
  height: 460px;
  background: #1a1a1a;
  border-radius: 8px;
  overflow: hidden;
}
.preview-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(0, 0, 0, 0.55);
  color: #f6f2ee;
}
.preview-loading-text {
  font-size: 14px;
  color: #dbc98a;
}
.preview-progress {
  width: 240px;
  height: 4px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 2px;
  overflow: hidden;
}
.preview-progress-bar {
  height: 100%;
  background: #dbc98a;
  transition: width 0.2s;
}
.preview-error-title {
  font-size: 15px;
  font-weight: 600;
  color: #d9b4b0;
}
.preview-error-text {
  font-size: 12px;
  color: #cccccc;
  max-width: 80%;
  text-align: center;
  word-break: break-all;
}
.preview-url {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-400);
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
