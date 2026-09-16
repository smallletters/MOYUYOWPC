<!--
  装扮弹窗组件
  功能:
    1. 顶部 Tab 切换:宠物形象 / 背景
    2. 中部实时预览区(显示选中的形象 + 背景)
    3. 底部选项列表:内置 + 自定义上传
    4. 支持图片格式 + GLB/FBX 3D 模型(GLB/FBX 仅 H5/APP 可用,小程序自动降级为图片模式)

  用法:
    <dress-popup ref="dressPopup" :pet="activePet" @confirm="onConfirm" />
    this.$refs.dressPopup.open()
    this.$refs.dressPopup.close()
-->
<template>
  <!-- 用 v-if 控制挂载,避免 uni-popup 在初始化时即显示遮罩层的问题 -->
  <view v-if="visible" class="dress-popup-mask" @click.self="close">
    <view class="dress-popup" @click.stop>
      <!-- 顶部 Tab 切换 -->
      <view class="dp-tabs">
        <view
          v-for="t in tabs"
          :key="t.id"
          class="dp-tab"
          :class="{ active: activeTab === t.id }"
          @click="activeTab = t.id"
        >
          <text class="luc" :class="t.icon" />
          <text>{{ t.label }}</text>
        </view>
        <view class="dp-close" @click="close">
          <text class="luc luc-x" />
        </view>
      </view>

      <!-- 中部预览区 -->
      <view class="dp-preview" :style="{ backgroundImage: previewBg }">
        <view class="dp-preview-mask" />
        <view class="dp-preview-content">
          <!-- 渲染选中形象:图片 / 3D 模型 / 占位 -->
          <image
            v-if="previewType === 'image'"
            :src="selectedAvatarItem.src"
            mode="aspectFit"
            class="dp-preview-img"
          />
          <view v-else-if="previewType === 'model3d'" :id="canvasId" class="dp-preview-canvas" />
          <view v-else class="dp-preview-empty">
            <text class="luc luc-paw-print" />
          </view>
          <view class="dp-preview-tip">
            <text>{{ previewTip }}</text>
          </view>
        </view>
      </view>

      <!-- 底部选项列表 -->
      <scroll-view scroll-y class="dp-options">
        <!-- 宠物形象 Tab -->
        <template v-if="activeTab === 'avatar'">
          <view class="dp-section-title">
            <text>{{ $t('dressPopup.builtinAvatar') }}</text>
          </view>
          <view class="dp-grid">
            <view
              v-for="item in builtinAvatars"
              :key="item.id"
              class="dp-grid-item"
              :class="{ active: selectedAvatarId === item.id }"
              @click="selectAvatar(item)"
            >
              <image :src="item.src" mode="aspectFill" class="dp-grid-img" />
              <text class="dp-grid-name">{{ item.name }}</text>
            </view>
          </view>
          <view class="dp-section-title">
            <text>{{ $t('dressPopup.customAvatar') }}</text>
          </view>
          <view class="dp-grid">
            <!-- 已上传的自定义形象 -->
            <view
              v-for="item in customAvatars"
              :key="item.id"
              class="dp-grid-item"
              :class="{ active: selectedAvatarId === item.id }"
              @click="selectAvatar(item)"
            >
              <image :src="item.src" mode="aspectFill" class="dp-grid-img" />
              <view class="dp-grid-del" @click.stop="delCustom(item)">
                <text class="luc luc-x" />
              </view>
            </view>
            <!-- 上传按钮:图片 -->
            <view class="dp-grid-item dp-grid-upload" @click="uploadImage">
              <text class="luc luc-image" />
              <text>{{ $t('dressPopup.uploadImg') }}</text>
            </view>
            <!-- 上传按钮:3D 模型 -->
            <view class="dp-grid-item dp-grid-upload" @click="uploadModel">
              <text class="luc luc-box" />
              <text>GLB/FBX</text>
            </view>
          </view>
        </template>

        <!-- 背景 Tab -->
        <template v-else>
          <view class="dp-grid">
            <view
              v-for="bg in bgList"
              :key="bg.id"
              class="dp-bg-item"
              :class="{ active: selectedBgId === bg.id }"
              @click="selectBg(bg)"
            >
              <image :src="bg.src" mode="aspectFill" class="dp-bg-img" />
              <text class="dp-bg-name">{{ bg.name }}</text>
              <view v-if="selectedBgId === bg.id" class="dp-bg-check">
                <text class="luc luc-check" />
              </view>
            </view>
          </view>
        </template>
      </scroll-view>

      <!-- 底部确认按钮 -->
      <view class="dp-footer">
        <view class="dp-btn dp-btn-cancel" @click="close">
          <text>{{ $t('common.cancel') }}</text>
        </view>
        <view class="dp-btn dp-btn-confirm" @click="confirm">
          <text>{{ $t('dressPopup.confirm') }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
// 内置宠物形象定义:name 走 i18n(nameKey),避免硬编码中英文案
// 在 data 里通过 this.$t 解析,locale 切换时跟随更新
const BUILTIN_AVATAR_DEFS = [
  {
    id: 'a1',
    nameKey: 'petHub.builtinAvatars.labrador',
    src: '/static/pet/avatar-labrador.png',
    type: 'image',
  },
  {
    id: 'a2',
    nameKey: 'petHub.builtinAvatars.shepherd',
    src: '/static/pet/avatar-shepherd.png',
    type: 'image',
  },
  {
    id: 'a3',
    nameKey: 'petHub.builtinAvatars.bulldog',
    src: '/static/pet/avatar-bulldog.png',
    type: 'image',
  },
  {
    id: 'a4',
    nameKey: 'petHub.builtinAvatars.maineCoon',
    src: '/static/pet/avatar-maine-coon.png',
    type: 'image',
  },
]

// 预设背景:5 张宠物场景图;name 走 i18n(nameKey),由 computed 解析
const BG_DEFS = [
  { id: 'b1', nameKey: 'dressPopup.bgNames.living', src: '/static/pet/bg/bg-living.jpg' },
  { id: 'b2', nameKey: 'dressPopup.bgNames.bedroom', src: '/static/pet/bg/bg-bedroom.jpg' },
  { id: 'b3', nameKey: 'dressPopup.bgNames.garden', src: '/static/pet/bg/bg-garden.jpg' },
  { id: 'b4', nameKey: 'dressPopup.bgNames.beach', src: '/static/pet/bg/bg-beach.jpg' },
  { id: 'b5', nameKey: 'dressPopup.bgNames.forest', src: '/static/pet/bg/bg-forest.jpg' },
]

// 3D 模型单文件大小上限 30MB(IDB 容量充足,但太大加载慢)
const MODEL_MAX_SIZE = 30 * 1024 * 1024

// 用 import 引入;uni-app 编译 H5 时走 ES Module,小程序/APP 也能识别
import { idbPut } from '@/utils/idb-storage'
import { uploadApi } from '@/api'

export default {
  name: 'DressPopup',
  props: {
    // 当前宠物(用于头像回退)
    pet: { type: Object, default: null },
    // 初始已选中的形象 id(可空)
    initialAvatarId: { type: String, default: '' },
    // 初始已选中的背景 id
    initialBgId: { type: String, default: '' },
    // 自定义上传的形象列表(从父组件传入,持久化由父组件负责)
    customAvatarList: { type: Array, default: () => [] },
  },

  data() {
    return {
      // 弹窗显隐:用 v-if 控制整个遮罩+弹窗的挂载,open 时才挂载 DOM
      visible: false,
      activeTab: 'avatar', // 'avatar' | 'bg'
      // 当前预览选中项
      selectedAvatarId: '',
      selectedAvatarItem: null,
      selectedBgId: '',
      selectedBgItem: null,
      // three.js 实例(放 data 外,避免 Vue 响应式代理 three 对象)
      canvasId: 'dress-canvas-' + Math.random().toString(36).slice(2, 9),
      threeInst: null,
      // 临时缓存选中状态,confirm 时再 emit 给父组件
    }
  },

  computed: {
    // Tab 列表(label 走 i18n,locale 切换时跟随更新)
    tabs() {
      return [
        { id: 'avatar', label: this.$t('dressPopup.tabAvatar'), icon: 'luc-paw-print' },
        { id: 'bg', label: this.$t('dressPopup.tabBg'), icon: 'luc-image' },
      ]
    },

    // 背景列表(name 走 i18n,locale 切换时跟随更新)
    bgList() {
      return BG_DEFS.map((b) => ({
        id: b.id,
        name: this.$t(b.nameKey),
        src: b.src,
      }))
    },

    // 当前选中的自定义形象列表(从 prop 取)
    customAvatars() {
      return Array.isArray(this.customAvatarList) ? this.customAvatarList : []
    },

    // 内置宠物形象列表(name 走 i18n,locale 切换时跟随更新)
    builtinAvatars() {
      return BUILTIN_AVATAR_DEFS.map((it) => ({
        id: it.id,
        name: this.$t(it.nameKey),
        src: it.src,
        type: it.type,
      }))
    },

    // 预览类型:'image' | 'model3d' | 'empty'
    previewType() {
      const item = this.selectedAvatarItem
      if (!item) return 'empty'
      if (item.type === 'model3d') return 'model3d'
      return 'image'
    },

    // 预览区背景:优先用选中背景,否则用当前宠物头像作为模糊背景
    previewBg() {
      const bg = this.selectedBgItem
      if (bg?.src) return `url(${bg.src})`
      if (this.pet?.avatar) return `url(${this.pet.avatar})`
      return 'linear-gradient(135deg, #f7e9d7, #e6c9a3)'
    },

    // 预览区下方提示文案
    previewTip() {
      const item = this.selectedAvatarItem
      if (!item) return this.$t('dressPopup.tipPick')
      if (item.type === 'model3d') return item.name + ' · 拖动旋转预览'
      return item.name
    },
  },

  watch: {
    // 预览类型变为 3D 时初始化 canvas
    previewType(val, oldVal) {
      if (val === 'model3d') {
        this.$nextTick(() => this.initModelPreview())
      } else if (oldVal === 'model3d' && this.threeInst) {
        // 切走时释放
        this.disposeModelPreview()
      }
    },
  },

  // 组件销毁时释放资源
  beforeUnmount() {
    this.disposeModelPreview()
  },

  methods: {
    // 打开弹窗(由父组件调用)
    open() {
      // 重置选中状态为初始值
      this.selectedAvatarId = this.initialAvatarId || (this.pet?.avatar ? 'pet-self' : '')
      this.selectedBgId = this.initialBgId || 'b1'
      this.selectedAvatarItem = this.findAvatarItem(this.selectedAvatarId)
      this.selectedBgItem = this.bgList.find((b) => b.id === this.selectedBgId) || this.bgList[0]
      // 打开弹窗(挂载 DOM)
      this.visible = true
    },

    close() {
      if (this.threeInst) this.disposeModelPreview()
      // 卸载 DOM(下次 open 重新挂载,避免某些组件库残留状态)
      this.visible = false
      // 通知父组件卸载整个组件(v-if 控制)
      this.$emit('close')
    },

    // 查找形象项(内置/自定义/特殊 pet-self)
    findAvatarItem(id) {
      if (!id) return null
      if (id === 'pet-self') {
        return this.pet?.avatar
          ? {
              id: 'pet-self',
              name: this.pet.name || this.$t('petHub.petSelfFallback'),
              src: this.pet.avatar,
              type: 'image',
            }
          : null
      }
      return (
        this.builtinAvatars.find((a) => a.id === id) ||
        this.customAvatars.find((a) => a.id === id) ||
        null
      )
    },

    // 选中内置/自定义形象
    selectAvatar(item) {
      this.selectedAvatarId = item.id
      this.selectedAvatarItem = item
    },

    // 选中背景
    selectBg(bg) {
      this.selectedBgId = bg.id
      this.selectedBgItem = bg
    },

    // 删除自定义形象
    delCustom(item) {
      this.$emit('delete-custom', item.id)
      if (this.selectedAvatarId === item.id) {
        this.selectedAvatarId = ''
        this.selectedAvatarItem = null
      }
    },

    // 上传图片:上传到服务器得到 URL,持久化到后端(APP 更新不会丢失)
    // 3D 模型仍走本地 IDB(后端 outfit 接口仅支持图片 URL)
    async uploadImage() {
      const res = await new Promise((resolve) => {
        uni.chooseImage({
          count: 1,
          sizeType: ['compressed'],
          success: (r) => resolve(r),
          fail: () => resolve(null),
        })
      })
      const path = res?.tempFilePaths?.[0]
      if (!path) return
      uni.showLoading({ title: this.$t('dressPopup.uploadingImg'), mask: true })
      try {
        // 上传图片到服务器,拿到持久化 URL(/uploads/yyyy/MM/dd/uuid.ext)
        const uploadRes = await uploadApi.uploadImage(path)
        if (!uploadRes?.url) throw new Error(this.$t('dressPopup.readImageFailed'))
        // emit 给父组件,由 store 同步写入后端 mo_pet_outfit(user_id=当前用户)
        this.$emit('upload-avatar', {
          id: 'cu-' + Date.now(),
          name: this.$t('dressPopup.customAvatarName'),
          src: uploadRes.url,
          type: 'image',
        })
      } catch (e) {
        uni.showToast({ title: e?.message || this.$t('dressPopup.readImageFailed'), icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },

    // 上传 3D 模型(GLB/FBX):存入 IndexedDB 持久化,后续通过 idbGetBlobURL 重建 blob URL
    async uploadModel() {
      // #ifdef H5 || APP-PLUS
      let picked
      try {
        picked = await new Promise((resolve, reject) => {
          uni.chooseFile({
            count: 1,
            extension: ['.glb', '.gltf', '.fbx'],
            success: (res) => resolve(res),
            fail: (err) => reject(err),
          })
        })
      } catch (e) {
        // 用户取消选择
        return
      }
      const file = picked.tempFiles[0]
      if (!file) return
      const fileName = file.name || '3D模型.glb'
      const ext = (fileName.split('.').pop() || 'glb').toLowerCase()
      const format = ext === 'fbx' ? 'fbx' : 'glb'

      // 文件大小保护
      if (file.size && file.size > MODEL_MAX_SIZE) {
        uni.showToast({
          title: `模型超过 ${MODEL_MAX_SIZE / 1024 / 1024}MB,请压缩后上传`,
          icon: 'none',
        })
        return
      }

      uni.showLoading({ title: this.$t('dressPopup.savingModel') })

      try {
        const id = 'cu-' + Date.now()
        const blob = await this.toBlob(file, format)

        // 存进 IndexedDB(按 user 段隔离,避免切换账号时读到别人的模型)
        const storeKey = `pet-model:${id}`
        await idbPut(storeKey, blob, { name: fileName, format, size: file.size || blob.size })

        this.$emit('upload-avatar', {
          id,
          // storeKey 是 IDB 的真 key,后续场景中央重建 blob URL 用得到
          storeKey,
          name: fileName,
          // src 此时不存 blob URL,因为页面关闭后 blob URL 会失效
          // 改用 storeKey 标记,使用方按需调用 idbGetBlobURL 拿真实 URL
          src: null,
          type: 'model3d',
          format,
        })
        uni.hideLoading()
        uni.showToast({ title: '3D模型已保存', icon: 'success' })
      } catch (e) {
        uni.hideLoading()
        console.error('[dress-popup] 模型保存失败', e)
        uni.showToast({ title: this.$t('dressPopup.modelSaveFailed'), icon: 'none' })
      }
      // #endif
      // #ifdef MP-WEIXIN
      uni.showToast({ title: this.$t('dressPopup.mpNoModelUpload'), icon: 'none' })
      // #endif
    },

    // 把 uni.chooseFile 拿到的文件转成 Blob(H5 用 fetch,APP 端走 plus.io 转)
    toBlob(file, format) {
      if (typeof window !== 'undefined' && typeof fetch !== 'undefined') return this.toBlobH5(file)
      // eslint-disable-next-line no-undef
      if (typeof plus !== 'undefined') return this.toBlobApp(file, format)
      return Promise.reject(new Error('toBlob: unsupported platform'))
    },
    // H5:blob URL/File 直接 fetch
    toBlobH5(file) {
      if (file.path instanceof Blob) return Promise.resolve(file.path)
      return fetch(file.path).then((resp) => resp.blob())
    },
    // APP:走 plus.io 读本地文件后包成 Blob
    toBlobApp(file, format) {
      // eslint-disable-next-line no-undef
      const plusObj = typeof plus !== 'undefined' ? plus : null
      if (!plusObj) return Promise.reject(new Error('plus not ready'))
      return new Promise((resolve, reject) => {
        plusObj.io.resolveLocalFileSystemURL(
          file.path,
          (entry) => {
            entry.file(
              (f) => {
                const reader = new plusObj.io.FileReader()
                reader.onload = (e) => {
                  const arr = new Uint8Array(e.target.result)
                  const mime = format === 'fbx' ? 'application/octet-stream' : 'model/gltf-binary'
                  resolve(new Blob([arr], { type: mime }))
                }
                reader.onerror = (err) => reject(err)
                reader.readAsArrayBuffer(f)
              },
              (err) => reject(err),
            )
          },
          (err) => reject(err),
        )
      })
    },

    // 临时路径转 base64(用于持久化图片)
    toBase64(path) {
      if (typeof window !== 'undefined' && typeof fetch !== 'undefined')
        return this.toBase64H5(path)
      // eslint-disable-next-line no-undef
      if (typeof plus !== 'undefined') return this.toBase64App(path)
      if (typeof wx !== 'undefined' && wx.getFileSystemManager) return this.toBase64Mp(path)
      return Promise.reject(new Error('toBase64: unsupported platform'))
    },
    // H5 实现:fetch + FileReader
    toBase64H5(path) {
      return new Promise((resolve, reject) => {
        fetch(path)
          .then((r) => r.blob())
          .then((blob) => {
            const reader = new FileReader()
            reader.onload = () => resolve(reader.result)
            reader.onerror = reject
            reader.readAsDataURL(blob)
          })
          .catch(reject)
      })
    },
    // APP 实现:简化直接返回 path,后续如需持久化再补
    toBase64App(path) {
      // eslint-disable-next-line no-undef
      const plusObj = typeof plus !== 'undefined' ? plus : null
      if (!plusObj) return Promise.resolve(path)
      return Promise.resolve(path)
    },
    // 小程序实现:FileSystemManager
    toBase64Mp(path) {
      return new Promise((resolve, reject) => {
        const fs = wx.getFileSystemManager()
        fs.readFile({
          filePath: path,
          encoding: 'base64',
          success: (r) => resolve('data:image/jpeg;base64,' + r.data),
          fail: reject,
        })
      })
    },

    // 初始化 3D 模型预览(canvas)
    initModelPreview() {
      const item = this.selectedAvatarItem
      if (!item || item.type !== 'model3d') return
      // #ifdef H5 || APP-PLUS
      this.disposeModelPreview()
      const isFbx = item.format === 'fbx'
      // 优先用 storeKey 从 IDB 拿 blob URL(持久化路径);
      // 兼容老数据:若无 storeKey,fallback 用 item.src(可能是已失效的临时 URL)
      const storeKey = item.storeKey

      Promise.all([
        import('three'),
        import('three/examples/jsm/loaders/GLTFLoader.js'),
        // 把 idbGetBlobURL 同步进来,运行时从 IDB 取 blob URL
        import('@/utils/idb-storage'),
      ])
        .then(async ([THREE, { GLTFLoader }, { idbGetBlobURL }]) => {
          let url = item.src
          if (storeKey) {
            // 从 IndexedDB 重建 blob URL(refCount 自带管理,无需手动释放)
            const r = await idbGetBlobURL(storeKey)
            if (r && r.url) url = r.url
          }
          if (!url) {
            uni.showToast({ title: this.$t('dressPopup.modelMissing'), icon: 'none' })
            return
          }
          // FBX 需要额外加载 FBXLoader
          let FBXLoader = null
          if (isFbx) {
            try {
              const m = await import('three/examples/jsm/loaders/FBXLoader.js')
              FBXLoader = m.FBXLoader
            } catch (e) {
              console.warn('[dress-popup] FBXLoader 加载失败,降级用 GLB 解析', e)
            }
          }

          const el = document.getElementById(this.canvasId)
          if (!el || !el.clientWidth) return
          const w = el.clientWidth
          const h = el.clientHeight

          const scene = new THREE.Scene()
          scene.background = null // 透明,显示弹窗背景
          const camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 100)
          camera.position.set(0, 0.8, 3.2)

          const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
          renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
          renderer.setSize(w, h)
          el.appendChild(renderer.domElement)

          scene.add(new THREE.AmbientLight(0xffffff, 1.0))
          const dir = new THREE.DirectionalLight(0xffffff, 0.8)
          dir.position.set(3, 5, 3)
          scene.add(dir)

          const Loader = isFbx && FBXLoader ? FBXLoader : GLTFLoader
          const loader = new Loader()
          loader.load(
            url,
            (gltf) => {
              const obj = gltf.scene
              // 自动居中 + 缩放到合适大小
              const box = new THREE.Box3().setFromObject(obj)
              const size = box.getSize(new THREE.Vector3())
              const center = box.getCenter(new THREE.Vector3())
              const maxAxis = Math.max(size.x, size.y, size.z) || 1
              const scale = 1.6 / maxAxis
              obj.scale.setScalar(scale)
              obj.position.x = -center.x * scale
              obj.position.y = -box.min.y * scale
              obj.position.z = -center.z * scale
              scene.add(obj)

              // 鼠标拖动旋转
              let dragging = false
              let lastX = 0
              let lastY = 0
              renderer.domElement.addEventListener('pointerdown', (e) => {
                dragging = true
                lastX = e.clientX
                lastY = e.clientY
              })
              renderer.domElement.addEventListener('pointermove', (e) => {
                if (!dragging) return
                const dx = e.clientX - lastX
                const dy = e.clientY - lastY
                obj.rotation.y += dx * 0.01
                obj.rotation.x += dy * 0.01
                lastX = e.clientX
                lastY = e.clientY
              })
              renderer.domElement.addEventListener('pointerup', () => {
                dragging = false
              })
              renderer.domElement.addEventListener('pointercancel', () => {
                dragging = false
              })

              // 渲染循环
              const animate = () => {
                if (!this.threeInst) return
                this.threeInst.animId = requestAnimationFrame(animate)
                if (!dragging) obj.rotation.y += 0.005 // 自动旋转
                renderer.render(scene, camera)
              }
              animate()
            },
            undefined,
            (err) => {
              console.error('[dress-popup] 模型加载失败', err)
              uni.showToast({ title: this.$t('dressPopup.modelLoadFailed'), icon: 'none' })
            },
          )

          this.threeInst = { scene, camera, renderer, animId: null, storeKey }
        })
        .catch((e) => {
          console.error('[dress-popup] three.js 加载失败', e)
          uni.showToast({ title: this.$t('dressPopup.thxLoadFailed'), icon: 'none' })
        })
      // #endif
      // #ifdef MP-WEIXIN
      uni.showToast({ title: this.$t('dressPopup.mpNoModelPreview'), icon: 'none' })
      // #endif
    },

    // 释放 3D 预览资源
    disposeModelPreview() {
      const t = this.threeInst
      if (!t) return
      if (t.animId) cancelAnimationFrame(t.animId)
      if (t.renderer) {
        t.renderer.dispose()
        const dom = t.renderer.domElement
        if (dom && dom.parentNode) dom.parentNode.removeChild(dom)
      }
      // 释放 IDB blob URL 引用(refCount 减 1,降到 0 自动 revoke)
      if (t.storeKey) {
        import('@/utils/idb-storage').then(({ idbReleaseBlobURL }) => {
          idbReleaseBlobURL(t.storeKey)
        })
      }
      this.threeInst = null
    },

    // 确认选择,emit 给父组件
    confirm() {
      this.$emit('confirm', {
        avatar: this.selectedAvatarItem,
        background: this.selectedBgItem,
      })
      this.close()
    },
  },
}
</script>

<style lang="scss" scoped>
/* 全屏遮罩(自管,替代 uni-popup 的遮罩层) */
.dress-popup-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dress-popup {
  width: 600rpx;
  max-width: 92vw;
  max-height: 85vh;
  background: var(--color-surface);
  border-radius: 32rpx;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 顶部 Tab */
.dp-tabs {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  border-bottom: 1rpx solid var(--color-divider);
  flex-shrink: 0;
}
.dp-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 12rpx 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
}
.dp-tab.active {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
.dp-tab .luc {
  font-size: 28rpx;
}
.dp-close {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: var(--color-text-tertiary);
}

/* 中部预览区 */
.dp-preview {
  position: relative;
  height: 420rpx;
  background-size: cover;
  background-position: center;
  flex-shrink: 0;
}
.dp-preview-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.25));
}
.dp-preview-content {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dp-preview-img {
  width: 280rpx;
  height: 280rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.15);
}
.dp-preview-canvas {
  width: 400rpx;
  height: 360rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.4);
  overflow: hidden;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.15);
}
.dp-preview-empty {
  width: 280rpx;
  height: 280rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 96rpx;
  color: var(--color-text-tertiary);
}
.dp-preview-tip {
  position: absolute;
  bottom: 16rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 8rpx 24rpx;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 999rpx;
  font-size: 22rpx;
  color: #ffffff;
  white-space: nowrap;
}

/* 底部选项列表 */
.dp-options {
  flex: 1;
  max-height: 520rpx;
  padding: 16rpx 24rpx;
  /* 兼容 H5:uni-app 的 scroll-view 在 H5 下会渲染多层嵌套 div,
     这里强制让最内层内容容器占满弹窗宽度,避免内容缩到一侧 */
  width: 100%;
  box-sizing: border-box;
}
/* H5 下 scroll-view 内部包裹 div,显式撑满宽度 */
.dp-options :deep(.uni-scroll-view),
.dp-options :deep(.uni-scroll-view-content) {
  width: 100% !important;
  min-width: 0;
  box-sizing: border-box;
}
.dp-section-title {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  margin: 12rpx 0 12rpx;
  font-weight: var(--font-weight-medium);
}
.dp-grid {
  display: grid;
  /* 用 auto-fill + 最小列宽,让列数随弹窗宽度自适应,
     避免固定 4 列导致窄弹窗下内容溢出或宽弹窗下最后一列空着 */
  grid-template-columns: repeat(auto-fill, minmax(140rpx, 1fr));
  gap: 16rpx;
  margin-bottom: 16rpx;
  width: 100%;
}
.dp-grid-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 8rpx;
  border: 2rpx solid var(--color-divider);
  border-radius: 16rpx;
  background: var(--color-background);
}
.dp-grid-item.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}
.dp-grid-img {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: var(--color-divider);
}
.dp-grid-name {
  font-size: 20rpx;
  color: var(--color-text-secondary);
  text-align: center;
}
.dp-grid-del {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
}
.dp-grid-upload {
  border-style: dashed;
  color: var(--color-text-tertiary);
  font-size: 20rpx;
  justify-content: center;
}
.dp-grid-upload .luc {
  font-size: 36rpx;
}

/* 背景项(略大) */
.dp-bg-item {
  position: relative;
  display: flex;
  flex-direction: column;
  border-radius: 16rpx;
  overflow: hidden;
  border: 2rpx solid var(--color-divider);
}
.dp-bg-item.active {
  border-color: var(--color-primary);
}
.dp-bg-img {
  width: 100%;
  height: 180rpx;
}
.dp-bg-name {
  padding: 8rpx;
  font-size: 22rpx;
  color: var(--color-text);
  text-align: center;
  background: var(--color-surface);
}
.dp-bg-check {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: var(--color-primary);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
}

/* 底部按钮 */
.dp-footer {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx 24rpx;
  border-top: 1rpx solid var(--color-divider);
  flex-shrink: 0;
}
.dp-btn {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}
.dp-btn-cancel {
  background: var(--color-background);
  color: var(--color-text-secondary);
}
.dp-btn-confirm {
  background: var(--color-primary);
  color: #ffffff;
}
</style>
