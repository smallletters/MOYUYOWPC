<template>
  <view class="pet-hub">
    <!-- 可滚动内容区（场景直达页面顶部） -->
    <scroll-view scroll-y class="page-scroll">
      <!-- 3D 宠物互动场景 -->
      <!-- 注意:scene 容器不设 @click,避免背景图被点击后误触跳转;
           3D 空间入口只能通过右上角「3D 空间」按钮(scene-pill)触发 -->
      <view class="scene">
        <!-- 选中背景(优先) > 原固定背景 -->
        <image class="scene-bg" :src="sceneBgSrc" mode="aspectFill" />
        <!-- 顶部轻微压暗、底部加深，保证浮层文字可读 -->
        <view class="scene-mask" />

        <!-- 场景粒子光点（装饰动画） -->
        <view class="scene-particles">
          <view class="particle p1" />
          <view class="particle p2" />
          <view class="particle p3" />
          <view class="particle p4" />
        </view>

        <!-- 场景内悬浮宠物切换条（位于信息浮层下方） -->
        <view class="scene-pets" @click.stop>
          <scroll-view scroll-x class="pets-scroll" :show-scrollbar="false">
            <view class="pets-inner">
              <!-- 宠物头像列表 -->
              <view
                v-for="pet in petStore.pets"
                :key="pet.id"
                class="scene-pet"
                :class="{ active: pet.id === activePet?.id }"
                @click.stop="switchPet(pet)"
              >
                <view class="avatar-ring" :class="{ active: pet.id === activePet?.id }">
                  <image
                    v-if="pet.avatar"
                    class="pet-avatar"
                    :src="pet.avatar"
                    mode="aspectFill" />
                  <view v-else class="pet-avatar pet-avatar-fallback">
                    <text class="luc" :class="$luc(petIcon(pet))" />
                  </view>
                </view>
              </view>
              <!-- 暂无宠物：我的宠物占位（点击添加档案） -->
              <view v-if="petStore.pets.length === 0" class="scene-pet" @click.stop="goAddPet">
                <view class="avatar-ring">
                  <view class="pet-avatar pet-avatar-fallback">
                    <text class="luc luc-paw-print" />
                  </view>
                </view>
              </view>
            </view>
          </scroll-view>
        </view>

        <!-- 中央:当前装扮形象(用户自定义 / 内置) - 3D 模型 / 图片 -->
        <view v-if="dressedAvatar" class="scene-figure" @click.stop>
          <image
            v-if="dressedAvatar.type !== 'model3d'"
            class="scene-figure-img"
            :src="dressedAvatar.src"
            mode="aspectFit"
          />
          <!-- 3D 模型嵌入 canvas(仅 H5/APP) -->
          <view v-else :id="figureCanvasId" class="scene-figure-canvas" />
        </view>

        <!-- 左上：宠物信息浮层 -->
        <view v-if="activePet" class="glass scene-info">
          <text class="info-kicker">{{ sceneKicker }}</text>
          <text class="info-title">{{ sceneTitle }}</text>
        </view>

        <!-- 右上：装扮入口 + 3D 空间入口（始终显示，点击守卫见 methods） -->
        <view class="scene-top">
          <view class="glass scene-pill" @click.stop="openDressPopup">
            <text class="luc luc-shirt scene-pill-icon" />
            <text class="scene-pill-text">{{ $t('petHub.dress') }}</text>
          </view>
          <view class="glass scene-pill" @click.stop="goSpace">
            <text class="luc luc-sparkles scene-pill-icon" />
            <text class="scene-pill-text">{{ $t('petHub.space3d') }}</text>
          </view>
        </view>

        <!-- 底部互动提示：仅无宠物时显示「去添加」 -->
        <view v-if="!activePet" class="scene-hint" @click.stop="goAddPet">
          <text>{{ $t('petHub.hintAddPet') }}</text>
        </view>
      </view>

      <!-- 护理状态卡片（上叠 16px 到场景图底部） -->
      <view class="care-wrap">
        <view class="care-grid">
          <view
            v-for="c in careItems"
            :key="c.type"
            class="care-card"
            @click="onCareClick(c)">
            <!-- 图标：lucide iconfont，APP/H5 跨端均能正确渲染（内联 svg 在 app-plus 端部分机型不可见） -->
            <view class="care-icon" :class="`tone-${c.tone}`">
              <!-- 洗护：浴缸 -->
              <text v-if="c.type === 'BATH'" class="luc luc-bath care-icon-font" />
              <!-- 疫苗：注射器 -->
              <text v-else-if="c.type === 'VACCINE'" class="luc luc-syringe care-icon-font" />
              <!-- 驱虫：药丸 -->
              <text v-else class="luc luc-pill care-icon-font" />
            </view>
            <text class="care-label">{{ c.label }}</text>
            <text class="care-value" :class="{ muted: !c.enabled }">{{ c.value }}</text>
            <text class="care-status" :class="c.statusClass">{{ c.status }}</text>
          </view>
        </view>

        <!-- 下次护理提醒条 -->
        <view v-if="nextReminder" class="reminder-banner" @click="goCareRecords(nextReminder.type)">
          <view class="reminder-main">
            <text class="rm-text">{{ nextReminder.text }}</text>
            <text v-if="nextReminder.num" class="rm-num">{{ nextReminder.num }}</text>
            <text v-if="nextReminder.suffix" class="rm-text">{{ nextReminder.suffix }}</text>
          </view>
          <view class="rm-btn" @click.stop="goCareRecords(nextReminder.type)">
            <text class="luc luc-more-horizontal" />
          </view>
        </view>
      </view>

      <!-- 快捷操作 -->
      <view class="quick-block">
        <view class="quick-head">
          <text class="quick-title">{{ $t('petHub.quickTitle') }}</text>
        </view>
        <view class="quick-grid">
          <view
            v-for="a in actions"
            :key="a.id"
            class="quick-item"
            @click="onActionClick(a)">
            <view class="quick-icon">
              <text class="luc" :class="$luc(a.icon)" />
            </view>
            <text class="quick-label">{{ a.label }}</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 装扮弹窗:用 v-if 控制挂载,未点装扮按钮前组件完全不渲染,避免任何残留显示 -->
    <dress-popup
      v-if="dressPopupMounted"
      ref="dressPopup"
      :pet="activePet"
      :initial-avatar-id="petStore.selectedAvatarId"
      :initial-bg-id="petStore.selectedBgId"
      :custom-avatar-list="petStore.customAvatars"
      @confirm="onDressConfirm"
      @upload-avatar="onUploadAvatar"
      @delete-custom="onDeleteCustom"
      @close="onDressClose"
    />
  </view>
</template>

<script>
// 显式 import 兜底:vite-plugin-uni H5 模式下 easycom.autoscan 偶尔扫不到 kebab-case 引用
import DressPopup from '@/components/dress-popup/dress-popup.vue'
import { usePetStore } from '@/store'

// 内置宠物形象:与 dress-popup 的 BUILTIN_AVATARS 保持一致,用于 scene-figure 回查
// store 仅持久化 customAvatars + selectedAvatarId,内置形象只在前端常量里;
// 当 selectedAvatarId 是内置 id(a1~a4)时,dressedAvatar 需要在此列表里查到 src
// name 走 i18n(key 在下方 builtinAvatars computed 里解析),避免硬编码中文
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

// three.js 实例挂到 WeakMap 桶,避免 Vue 响应式代理 three 只读属性触发 "is read-only" 报错
const figureThreeBucket = new WeakMap()

// 支持护理记录/提醒的类型（护理卡图标为内联 SVG，类型文案走 i18n: petHub.careTypes.*）
const REMINDER_TYPES = ['BATH', 'VACCINE', 'DEWORM', 'EXAM']
const CARE_TYPE_SET = new Set(REMINDER_TYPES)

// 护理卡类型与图标状态色：洗护/疫苗 → 绿；驱虫 → 红（与设计稿一致）
const CARE_TYPES = ['BATH', 'VACCINE', 'DEWORM']
const CARE_TONES = {
  BATH: { tone: 'ok' },
  VACCINE: { tone: 'ok' },
  DEWORM: { tone: 'err' },
}

export default {
  pageTitleKey: 'pageTitle.tabbarPet',

  // 显式注册装扮弹窗组件,避免 easycom.autoscan 在 vite-plugin-uni H5 模式下偶尔未扫到
  components: { DressPopup },

  data() {
    return {
      // 场景中央 3D 模型预览的 canvas id(与装扮弹窗内的独立开,避免冲突)
      figureCanvasId: 'pet-figure-canvas-' + Math.random().toString(36).slice(2, 9),
      // 装扮弹窗挂载控制:false 时组件完全不渲染,避免任何残留显示
      dressPopupMounted: false,
    }
  },

  computed: {
    // 快捷操作入口（标签走 i18n）
    actions() {
      return [
        { id: 'shop', label: this.$t('petHub.actions.shop'), icon: 'shopping-bag' },
        { id: 'share', label: this.$t('petHub.actions.share'), icon: 'camera' },
        { id: 'calendar', label: this.$t('petHub.actions.calendar'), icon: 'calendar' },
        { id: 'achievement', label: this.$t('petHub.actions.memory'), icon: 'book' },
        { id: 'weight', label: this.$t('petHub.actions.weight'), icon: 'scale' },
      ]
    },

    petStore() {
      return usePetStore()
    },
    activePet() {
      return this.petStore.activePet
    },

    // 内置宠物形象列表（name 走 i18n，locale 切换时自动更新）
    // 用于 dressedAvatar 回查，以及其它场景需要展示内置名时复用
    builtinAvatars() {
      return BUILTIN_AVATAR_DEFS.map((it) => ({
        id: it.id,
        name: this.$t(it.nameKey),
        src: it.src,
        type: it.type,
      }))
    },

    // 场景卡信息浮层：kicker 为种类/品种，title 为「名字, 年龄」
    sceneKicker() {
      const p = this.activePet
      if (!p) return this.$t('petHub.noPetScene')
      const label = [p.species, p.breed].filter(Boolean).join(' · ')
      return label || p.type || this.$t('petHub.buddy')
    },
    sceneTitle() {
      const p = this.activePet
      if (!p) return this.$t('petHub.emptyTitle')
      const age = this.ageText(p.birthday)
      return age ? `${p.name}, ${age}` : p.name
    },

    // 当前场景背景:优先用装扮选中的背景,否则用回默认场景图
    sceneBgSrc() {
      const bgId = this.petStore.selectedBgId
      if (bgId) {
        // 内置背景 id 映射到 static 路径
        const builtInMap = {
          b1: '/static/pet/bg/bg-living.jpg',
          b2: '/static/pet/bg/bg-bedroom.jpg',
          b3: '/static/pet/bg/bg-garden.jpg',
          b4: '/static/pet/bg/bg-beach.jpg',
          b5: '/static/pet/bg/bg-forest.jpg',
        }
        if (builtInMap[bgId]) return builtInMap[bgId]
      }
      return '/static/pet/pet-hub-scene.jpg'
    },

    // 当前场景中央显示的形象(用户选中的装扮)
    // 优先级:customAvatars 中匹配 selectedAvatarId > 内置 builtinAvatars 匹配 > 默认 pet-self
    dressedAvatar() {
      const sid = this.petStore.selectedAvatarId
      if (sid) {
        // 1. 自定义形象(id 通常是 'cu-xxx')
        const custom = (this.petStore.customAvatars || []).find((a) => a.id === sid)
        if (custom) return custom
        // 2. 内置形象(id 是 a1~a4),从本地常量查 src
        const builtin = this.builtinAvatars.find((a) => a.id === sid)
        if (builtin) return builtin
      }
      // 未选装扮时:用当前宠物头像作为默认形象
      const pet = this.activePet
      if (pet?.avatar) {
        return {
          id: 'pet-self',
          name: pet.name || this.$t('petHub.petSelfFallback'),
          src: pet.avatar,
          type: 'image',
        }
      }
      return null
    },

    // 护理卡：由 care-summary 聚合项驱动（最近记录 + 提醒配置），无宠物时显示占位卡
    careItems() {
      const p = this.activePet
      const list = Array.isArray(this.petStore.careSummary) ? this.petStore.careSummary : []
      const byType = {}
      list.forEach((s) => {
        if (s && s.careType) byType[s.careType] = s
      })
      return CARE_TYPES.map((type) => {
        const tone = CARE_TONES[type]
        const item = p ? byType[type] || null : null
        return this.buildCareCard(type, tone, item)
      })
    },

    // 距离最近的下一条护理提醒（用于提醒条单行文案）
    nextReminder() {
      const list = Array.isArray(this.petStore.careSummary) ? this.petStore.careSummary : []
      const active = list.filter((s) => s.nextDate && s.enabled !== false)
      if (!this.activePet || active.length === 0) return null
      const soon = active.reduce((a, b) => (a.daysUntilNext <= b.daysUntilNext ? a : b))
      const label = this.careLabel(soon.careType)
      const d = soon.daysUntilNext
      if (d < 0) {
        return {
          type: soon.careType,
          text: this.$t('petHub.reminder.overdue', { label }),
          num: '',
          suffix: '',
        }
      }
      if (d === 0) {
        return {
          type: soon.careType,
          text: this.$t('petHub.reminder.today', { label }),
          num: '',
          suffix: '',
        }
      }
      return {
        type: soon.careType,
        text: this.$t('petHub.reminder.next', { label }),
        num: String(d),
        suffix: this.$t('petHub.reminder.dayUnit'),
      }
    },
  },

  onShow() {
    // 先加载本地装扮缓存(无需宠物),再加载宠物列表
    this.petStore.loadDress()
    // 始终走 loadPets：未登录时 store 内部会清空宠物缓存并直接返回（不发请求、无 401），
    // 避免登出/换号后 Pet Tab 仍残留上一账号的宠物数据
    this.petStore.loadPets().then(() => {
      // 宠物就绪后重新拉取装扮(从后端同步用户自定义形象,APP 更新后可恢复)
      this.petStore.loadDress()
      this.refreshCareSummary()
      // 加载完后,如当前装扮为 3D 模型则启动场景中央预览
      this.$nextTick(() => this.syncFigureModel())
    })
  },

  watch: {
    // 监听 dressedAvatar 变化(已依赖 selectedAvatarId + customAvatars),同步场景中央 3D 模型
    dressedAvatar: {
      handler() {
        this.$nextTick(() => this.syncFigureModel())
      },
    },
    // 弹窗挂载状态变化(冗余保险:openDressPopup 已经显式调用,这里只是兜底)
    dressPopupMounted(val) {
      if (val) {
        // 已由 openDressPopup 主动调用,此处无需重复触发
      }
    },
  },

  beforeUnmount() {
    // 页面销毁时释放 three.js 资源
    this.disposeFigureModel()
  },

  methods: {
    // 按宠物类型返回头像占位图标
    petIcon(pet) {
      if (!pet) return 'paw-print'
      const map = { DOG: 'dog', CAT: 'cat' }
      return map[pet.type] || 'paw-print'
    },

    // 刷新当前宠物护理聚合数据（care-summary）
    refreshCareSummary() {
      const pet = this.activePet
      if (pet?.id) this.petStore.loadCareSummary(pet.id)
    },

    switchPet(pet) {
      this.petStore.currentPet = pet
      this.refreshCareSummary()
    },

    // 护理类型展示文案（未知类型直接回退原值，避免显示字典 key）
    careLabel(type) {
      if (CARE_TYPE_SET.has(type)) return this.$t(`petHub.careTypes.${type}`)
      return type || ''
    },

    // 组装单张护理卡展示数据：以「最近一次护理」为值，「下次护理」判断紧迫度
    buildCareCard(type, tone, item) {
      const statusT = (k, params) => this.$t(`petHub.status.${k}`, params)
      const base = { type, label: this.careLabel(type), ...tone }
      const hasRecord = !!(item && item.hasRecord)
      const value = hasRecord
        ? item.daysAgo === 0
          ? statusT('today')
          : statusT('daysAgo', { days: item.daysAgo })
        : statusT('never')
      // 提醒被用户显式关闭
      if (item && item.enabled === false) {
        return { ...base, enabled: false, value, status: statusT('off'), statusClass: 'todo' }
      }
      // 完全未配置（无下次护理日期）
      if (!item || !item.nextDate) {
        if (hasRecord) {
          return { ...base, enabled: true, value, status: statusT('setup'), statusClass: 'todo' }
        }
        return {
          ...base,
          enabled: false,
          value: statusT('never'),
          status: statusT('toRecord'),
          statusClass: 'todo',
        }
      }
      // 已过期 → 红色警示
      if (item.overdue) {
        return { ...base, enabled: true, value, status: statusT('overdue'), statusClass: 'warn' }
      }
      // 临近下次护理（advanceDays 内）→ 黄色提醒
      const warnDays = item.advanceDays || 3
      if (item.daysUntilNext !== null && item.daysUntilNext <= warnDays) {
        return { ...base, enabled: true, value, status: statusT('dueSoon'), statusClass: 'warn' }
      }
      return { ...base, enabled: true, value, status: statusT('good'), statusClass: 'ok' }
    },

    // 生日 → 年龄文案
    ageText(birthday) {
      if (!birthday) return ''
      const b = new Date(birthday)
      if (Number.isNaN(b.getTime())) return ''
      const now = new Date()
      let months = (now.getFullYear() - b.getFullYear()) * 12 + now.getMonth() - b.getMonth()
      if (now.getDate() < b.getDate()) months -= 1
      if (months <= 0) return this.$t('petHub.age.puppy')
      if (months < 12) return this.$t('petHub.age.months', { n: months })
      const years = Math.floor(months / 12)
      return this.$t('petHub.age.years', { n: years })
    },

    // 打开装扮弹窗
    openDressPopup() {
      console.log('[pet] openDressPopup called')
      // 不强制要求有宠物:无宠物时弹窗仍可打开
      // 1. 同步挂载组件
      this.dressPopupMounted = true
      // 2. 立即尝试调用 open()(多次尝试确保 ref 已就绪)
      this.tryOpenDressPopup(0)
    },

    // 多次尝试调用 open(),直到 ref 建立完成
    tryOpenDressPopup(retry) {
      if (this.$refs.dressPopup && typeof this.$refs.dressPopup.open === 'function') {
        this.$refs.dressPopup.open()
        console.log('[pet] dressPopup opened')
      } else if (retry < 5) {
        // ref 还没建立,等几毫秒再试
        setTimeout(() => this.tryOpenDressPopup(retry + 1), 20)
      } else {
        console.warn('[pet] dressPopup ref 多次重试仍未就绪')
      }
    },

    // 弹窗关闭回调:从子组件通过 emit('close') 通知,父组件卸载组件
    onDressClose() {
      this.dressPopupMounted = false
    },

    // 弹窗 confirm:把选中项写入 store
    onDressConfirm(payload) {
      if (!payload) return
      if (payload.avatar) this.petStore.setSelectedAvatar(payload.avatar.id)
      if (payload.background) this.petStore.setSelectedBg(payload.background.id)
      uni.showToast({ title: this.$t('petHub.dressApplied'), icon: 'success' })
    },

    // 弹窗 emit 上传事件:写入 store
    onUploadAvatar(item) {
      const ok = this.petStore.addCustomAvatar(item)
      if (ok) {
        // 上传后自动选中新形象,提升体验
        this.petStore.setSelectedAvatar(item.id)
      }
    },

    // 弹窗 emit 删除事件
    onDeleteCustom(id) {
      this.petStore.removeCustomAvatar(id)
    },

    // 同步场景中央 3D 模型:仅当 dressedAvatar 是 model3d 时初始化 canvas
    syncFigureModel() {
      const item = this.dressedAvatar
      if (!item) {
        this.disposeFigureModel()
        return
      }
      if (item.type !== 'model3d') {
        this.disposeFigureModel()
        return
      }
      // #ifdef H5 || APP-PLUS
      // 传 storeKey(优先)给 initFigureModel,内部从 IDB 拿 blob URL;
      // 兼容老数据(item.src 仍可能是个临时 URL)
      this.initFigureModel(item.storeKey || null, item.format, item.src)
      // #endif
    },

    // 初始化场景中央 3D 模型 canvas
    // storeKey: IDB 里的 key(优先),不存在时 fallback 到 fallbackUrl
    initFigureModel(storeKey, format, fallbackUrl) {
      // #ifdef H5 || APP-PLUS
      // 先释放旧实例
      this.disposeFigureModel()
      if (typeof document === 'undefined') return
      const el = document.getElementById(this.figureCanvasId)
      if (!el) return
      const w = el.clientWidth
      const h = el.clientHeight
      if (!w || !h) return

      Promise.all([
        import('three'),
        import('three/examples/jsm/loaders/GLTFLoader.js'),
        import('@/utils/idb-storage'),
      ])
        .then(async ([THREE, { GLTFLoader }, { idbGetBlobURL }]) => {
          // 1. 优先从 IDB 拿 blob URL(持久化路径,刷新后仍可用)
          let url = null
          if (storeKey) {
            const r = await idbGetBlobURL(storeKey)
            if (r && r.url) url = r.url
          }
          // 2. fallback:用 item.src(老数据或同步流程)
          if (!url && fallbackUrl) url = fallbackUrl
          if (!url) {
            uni.showToast({ title: this.$t('petHub.modelMissing'), icon: 'none' })
            return
          }

          let FBXLoader = null
          if (format === 'fbx') {
            try {
              const m = await import('three/examples/jsm/loaders/FBXLoader.js')
              FBXLoader = m.FBXLoader
            } catch (e) {
              console.warn('[pet] FBXLoader 加载失败', e)
            }
          }
          const scene = new THREE.Scene()
          scene.background = null
          const camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 100)
          camera.position.set(0, 0.8, 3.2)

          const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
          renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
          renderer.setSize(w, h)
          el.appendChild(renderer.domElement)

          scene.add(new THREE.AmbientLight(0xffffff, 1.1))
          const dir = new THREE.DirectionalLight(0xffffff, 0.7)
          dir.position.set(3, 5, 3)
          scene.add(dir)

          const Loader = format === 'fbx' && FBXLoader ? FBXLoader : GLTFLoader
          const loader = new Loader()
          loader.load(
            url,
            (gltf) => {
              const obj = gltf.scene
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

              // 简单交互:触摸拖动旋转
              let dragging = false
              let lastX = 0
              let lastY = 0
              const onDown = (e) => {
                dragging = true
                lastX = e.clientX
                lastY = e.clientY
              }
              const onMove = (e) => {
                if (!dragging) return
                obj.rotation.y += (e.clientX - lastX) * 0.01
                obj.rotation.x += (e.clientY - lastY) * 0.01
                lastX = e.clientX
                lastY = e.clientY
              }
              const onUp = () => {
                dragging = false
              }
              renderer.domElement.addEventListener('pointerdown', onDown)
              renderer.domElement.addEventListener('pointermove', onMove)
              renderer.domElement.addEventListener('pointerup', onUp)
              renderer.domElement.addEventListener('pointercancel', onUp)

              const animate = () => {
                const bucket = figureThreeBucket.get(this)
                if (!bucket) return
                bucket.animId = requestAnimationFrame(animate)
                if (!dragging) obj.rotation.y += 0.004
                renderer.render(scene, camera)
              }
              figureThreeBucket.set(this, {
                scene,
                camera,
                renderer,
                animId: null,
                storeKey, // 销毁时释放 IDB blob URL 引用
                cleanups: [
                  () => renderer.domElement.removeEventListener('pointerdown', onDown),
                  () => renderer.domElement.removeEventListener('pointermove', onMove),
                  () => renderer.domElement.removeEventListener('pointerup', onUp),
                  () => renderer.domElement.removeEventListener('pointercancel', onUp),
                ],
              })
              animate()
            },
            undefined,
            (err) => {
              console.error('[pet] 场景中央模型加载失败', err)
              uni.showToast({ title: this.$t('petHub.modelLoadFailed'), icon: 'none' })
            },
          )
        })
        .catch((e) => console.error('[pet] three.js 加载失败', e))
      // #endif
    },

    // 释放场景中央 3D 资源
    disposeFigureModel() {
      const t = figureThreeBucket.get(this)
      if (!t) return
      if (t.animId) cancelAnimationFrame(t.animId)
      if (Array.isArray(t.cleanups)) t.cleanups.forEach((fn) => fn())
      if (t.renderer) {
        t.renderer.dispose()
        const dom = t.renderer.domElement
        if (dom && dom.parentNode) dom.parentNode.removeChild(dom)
      }
      // 释放 IDB blob URL 引用(refCount -1,降到 0 自动 revoke)
      if (t.storeKey) {
        import('@/utils/idb-storage').then(({ idbReleaseBlobURL }) => {
          idbReleaseBlobURL(t.storeKey)
        })
      }
      figureThreeBucket.delete(this)
    },

    // 护理卡点击 → 对应类型的护理记录页
    onCareClick(card) {
      this.goCareRecords(card?.type)
    },

    // 进入护理记录页（记录一次护理 / 查看历史 / 滚动下次提醒）
    goCareRecords(careType) {
      const pet = this.activePet
      if (!pet?.id) {
        this.goAddPet()
        return
      }
      // 提醒条可能来自体检等类型，故按完整类型集合校验而非仅卡片三类
      const type = CARE_TYPE_SET.has(careType) ? careType : 'BATH'
      uni.navigateTo({ url: `/pages/pet/care-records?petId=${pet.id}&type=${type}` })
    },

    onActionClick(action) {
      const pet = this.activePet
      const petUrl = (path) => (pet?.id ? `${path}?petId=${pet.id}` : path)
      switch (action.id) {
        case 'shop':
          uni.switchTab({ url: '/pages/tabbar/category' })
          break
        case 'share':
          // 晒宠：有宠物时直达社区发帖并预选当前宠物（帖子归入宠物记忆树），否则进社区流
          if (pet?.id) {
            uni.navigateTo({ url: `/pages/community/create?petId=${pet.id}` })
          } else {
            uni.switchTab({ url: '/pages/tabbar/community' })
          }
          break
        case 'calendar':
          uni.navigateTo({ url: petUrl('/pages/pet/health-calendar') })
          break
        case 'achievement':
          // “记忆”入口：进入记忆树状时间轴（记录成长足迹 + 加入家庭时间）
          uni.navigateTo({ url: petUrl('/pages/pet/memory') })
          break
        case 'weight':
          uni.navigateTo({ url: petUrl('/pages/pet/weight-chart') })
          break
        default:
          uni.showToast({ title: this.$t('petHub.comingSoon'), icon: 'none' })
      }
    },

    goAddPet() {
      // mode=new 直达新增表单：有宠物档案时该入口也保持“新增”语义，不误入档案回显
      uni.navigateTo({ url: '/pages/pet/profile?mode=new' })
    },

    // 进入 3D 房间（页面独立运行，不依赖宠物参数，任何状态都可进入）
    goSpace() {
      uni.navigateTo({ url: '/pages/pet/space-3d' })
    },
  },
}
</script>

<style lang="scss" scoped>
/* 页面内局部 token：把设计稿的 iOS 风格色映射到本页，不改动全局主题 */
.pet-hub {
  --hub-bg: #f7f7fa; /* 页面浅灰背景 */
  --hub-card: #ffffff; /* 卡片 / 顶部栏表面 */
  --hub-line: #e5e5ea; /* 分隔 / 卡片描边 */
  --hub-chip: #f2f2f7; /* 图标底圆 / 灰按钮 */
  --hub-muted: #8e8e93; /* 次要文字 */
  --hub-ok: #34c759; /* 状态良好 */
  --hub-ok-bg: #e9f9ee;
  --hub-err: #ff3b30; /* 即将到期 */
  --hub-err-bg: #ffecea;
  /* 设计稿 text-xs = 12px，本页统一覆盖为 24rpx（1rpx≈0.5px） */
  --font-size-xs: 24rpx;
  /* 场景内容顶部距（状态栏高度 + H5 安全区），供场景内绝对定位浮层使用 */
  --sb: calc(var(--status-bar-height, 0px) + env(safe-area-inset-top, 0px));

  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--hub-bg);
}

/* 深色模式适配（跟随系统） */
@media (prefers-color-scheme: dark) {
  .pet-hub {
    --hub-bg: #1a1816;
    --hub-card: #2a2724;
    --hub-line: #3a3530;
    --hub-chip: #232120;
    --hub-muted: #807a72;
    --hub-ok: #30d158;
    --hub-ok-bg: rgba(48, 209, 88, 0.12);
    --hub-err: #ff453a;
    --hub-err-bg: rgba(255, 69, 58, 0.14);
  }
}

/* ===== 通用头像圆（40px） ===== */
.avatar-ring {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  padding: 2rpx;
  border: 2rpx solid var(--hub-line); /* 未选中：灰环 */
  box-sizing: border-box;
}
.avatar-ring.active {
  border-color: var(--color-primary); /* 选中：品牌主色环 */
}
.pet-avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2rpx solid var(--hub-card); /* 内容白边，iOS 头像效果 */
  box-sizing: border-box;
}
.pet-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hub-chip);
  color: var(--hub-muted);
  font-size: 32rpx;
}

/* ===== 中央形象区（装扮形象的展示） ===== */
.scene-figure {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 360rpx;
  height: 360rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: auto;
  z-index: 4;
}
.scene-figure-img {
  width: 320rpx;
  height: 320rpx;
  filter: drop-shadow(0 8rpx 24rpx rgba(0, 0, 0, 0.25));
}
.scene-figure-canvas {
  width: 360rpx;
  height: 360rpx;
}

/* ===== 场景内悬浮宠物切换条（场景左上角） ===== */
.scene-pets {
  position: absolute;
  top: calc(var(--sb) + 24rpx);
  left: 32rpx;
  right: 32rpx;
  z-index: 6;
}
.pets-scroll {
  width: 100%;
  white-space: nowrap;
}
.pets-inner {
  display: inline-flex;
  align-items: center;
  gap: 20rpx;
}
.scene-pet {
  flex-shrink: 0;
}
/* 悬浮于场景图上：未选中用白色描边，选中用品牌金环 */
.scene-pets .avatar-ring {
  border-color: rgba(255, 255, 255, 0.85);
}
.scene-pets .avatar-ring.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 6rpx rgba(219, 201, 138, 0.22);
}
.scene-pets .pet-avatar {
  border-color: rgba(255, 255, 255, 0.9);
}
.scene-pets .pet-avatar-fallback {
  background: rgba(255, 255, 255, 0.35);
  color: #ffffff;
}

/* ===== 滚动内容 ===== */
.page-scroll {
  flex: 1;
}

/* ===== 3D 互动场景 ===== */
.scene {
  position: relative;
  width: 100%;
  height: 50vh;
  min-height: 720rpx;
  max-height: 920rpx;
  overflow: hidden;
}
.scene-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  /* 装饰背景图:不接收点击,避免遮挡按钮点击事件 */
  pointer-events: none;
  /* 浏览器原生 <img> 默认可拖拽,关闭避免长按拖动影响交互 */
  -webkit-user-drag: none;
  user-drag: none;
}
.scene-mask {
  position: absolute;
  inset: 0;
  /* 装饰层:不接收点击,避免拦截按钮事件 */
  pointer-events: none;
  /* 顶部轻微压暗 + 底部加深（from-black/10 via-transparent to-black/25） */
  background: linear-gradient(
    180deg,
    rgba(0, 0, 0, 0.1) 0%,
    rgba(0, 0, 0, 0) 40%,
    rgba(0, 0, 0, 0.25) 100%
  );
}
.glass {
  /* 玻璃拟态：半透明 + 背景模糊 */
  backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

/* 左上宠物信息浮层（位于宠物头像排下方） */
.scene-info {
  position: absolute;
  top: calc(var(--sb) + 132rpx);
  left: 32rpx;
  padding: 16rpx 24rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  flex-direction: column;
  gap: 2rpx;
  max-width: 480rpx;
}
.info-kicker {
  font-size: var(--font-size-xs);
  color: rgba(255, 255, 255, 0.85);
}
.info-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
}

/* 右上按钮组（装扮 + 3D 空间），纵向排列 */
.scene-top {
  position: absolute;
  top: calc(var(--sb) + 32rpx);
  right: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 16rpx;
  /* 显式高 z-index,确保不被 .scene-mask / .scene-figure 等绝对定位层拦截点击 */
  z-index: 20;
}
.scene-pill {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.2);
}
.scene-pill-icon {
  font-size: 28rpx;
}
.scene-pill-text {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

/* 底部互动提示 */
.scene-hint {
  position: absolute;
  bottom: 160rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 16rpx 32rpx;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.3);
  font-size: var(--font-size-xs);
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}

/* 场景粒子光点（装饰动画） */
.scene-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}
.particle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  animation: particle-pulse 2.4s ease-in-out infinite;
}
.particle.p1 {
  width: 6rpx;
  height: 6rpx;
  top: 25%;
  left: 25%;
}
.particle.p2 {
  width: 4rpx;
  height: 4rpx;
  top: 33%;
  right: 33%;
  background: rgba(255, 255, 255, 0.5);
  animation-delay: 0.6s;
}
.particle.p3 {
  width: 8rpx;
  height: 8rpx;
  bottom: 33%;
  left: 50%;
  background: rgba(255, 255, 255, 0.4);
  animation-delay: 1.2s;
}
.particle.p4 {
  width: 4rpx;
  height: 4rpx;
  top: 40%;
  right: 25%;
  background: rgba(255, 255, 255, 0.5);
  animation-delay: 1.8s;
}
@keyframes particle-pulse {
  0%,
  100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}

/* ===== 护理状态卡片（上叠 32rpx 到场景图） ===== */
.care-wrap {
  position: relative;
  z-index: 10;
  margin-top: -32rpx;
  padding: 0 32rpx;
}
.care-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24rpx;
}
.care-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 24rpx;
  background: var(--hub-card);
  border: 1rpx solid var(--hub-line);
  border-radius: 32rpx;
  box-shadow: var(--shadow-sm);
  box-sizing: border-box;
}
.care-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;
}
.care-icon .care-icon-font {
  /* lucide iconfont 字符需要合适的字号和行高,才能在 64rpx 圆内视觉居中 */
  font-size: 32rpx;
  line-height: 1;
}
.care-icon.tone-ok {
  background: var(--hub-ok-bg);
  color: var(--hub-ok);
}
.care-icon.tone-err {
  background: var(--hub-err-bg);
  color: var(--hub-err);
}
.care-label {
  font-size: var(--font-size-xs);
  color: var(--hub-muted);
}
.care-value {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  margin-top: 4rpx;
  word-break: keep-all;
}
.care-value.muted {
  color: var(--hub-muted);
  font-weight: var(--font-weight-medium);
}
.care-status {
  font-size: 20rpx;
  margin-top: 6rpx;
  font-weight: var(--font-weight-medium);
}
.care-status.ok {
  color: var(--hub-ok);
}
.care-status.todo {
  color: var(--hub-muted);
}
.care-status.warn {
  color: var(--hub-err);
}

/* ===== 下次护理提醒条 ===== */
.reminder-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding: 24rpx 32rpx;
  background: var(--hub-chip);
  border-radius: 32rpx;
}
.reminder-main {
  flex: 1;
  display: flex;
  align-items: baseline;
}
.rm-text {
  font-size: var(--font-size-xs);
  color: var(--hub-muted);
}
.rm-num {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  margin: 0 4rpx;
}
.rm-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  color: var(--hub-muted);
}

/* ===== 快捷操作 ===== */
.quick-block {
  margin-top: 32rpx;
  padding: 0 32rpx 48rpx;
}
.quick-head {
  margin-bottom: 24rpx;
}
.quick-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16rpx;
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 4rpx;
  background: var(--hub-card);
  border: 1rpx solid var(--hub-line);
  border-radius: 32rpx;
  box-sizing: border-box;
}
.quick-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: var(--hub-chip);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  color: var(--color-text);
}
.quick-label {
  font-size: 20rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}
</style>
