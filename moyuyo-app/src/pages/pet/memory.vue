<template>
  <view class="memory">
    <!-- 顶部导航（自定义导航栏，叠加状态栏高度） -->
    <view class="header">
      <view class="header-inner">
        <view class="header-btn" :aria-label="$t('petMemory.title')" @click="onBack">
          <text class="luc luc-arrow-left" />
        </view>
        <text class="header-title">{{ $t('petMemory.title') }}</text>
        <view class="header-btn header-btn-placeholder" />
      </view>
    </view>

    <!-- 加载中 -->
    <view v-if="!loaded" class="pet-missing">
      <text class="pet-missing-text">{{ $t('petMemory.loading') }}</text>
    </view>

    <!-- 无宠物兜底（正常入口均带 petId） -->
    <view v-else-if="!pet" class="pet-missing">
      <text class="luc pet-missing-icon luc-paw-print" />
      <text class="pet-missing-text">{{ $t('petMemory.noPet') }}</text>
    </view>

    <template v-else>
      <!-- 宠物摘要 -->
      <view class="pet-strip">
        <image
          v-if="pet.avatar"
          :src="pet.avatar"
          class="pet-avatar"
          mode="aspectFill" />
        <view v-else class="pet-avatar pet-avatar-fallback">
          <text class="luc luc-paw-print" />
        </view>
        <view class="pet-meta">
          <text class="pet-name">{{ pet.name }}</text>
          <text class="pet-sub">{{ petMetaSub }}</text>
        </view>
      </view>

      <!-- 记忆树：时间倒序（最新在上），底部为加入家庭 -->
      <view class="tree-title">{{ $t('petMemory.growthTitle') }}</view>
      <view class="timeline">
        <view v-for="(item, i) in timeline" :key="item.key" class="tl-item">
          <view class="tl-track">
            <view class="tl-node" :style="{ background: toneColor(item) }">
              <text class="luc" :class="'luc-' + item.icon" />
            </view>
            <view v-if="i < timeline.length - 1" class="tl-line" />
          </view>

          <view class="tl-card" :class="{ 'tl-card-root': item.root }">
            <view class="tl-head">
              <text class="tl-type" :style="{ color: toneColor(item) }">{{ item.label }}</text>
              <text class="tl-date">{{ item.day }}</text>
            </view>
            <text
              v-if="item.title"
              class="tl-title"
              :style="item.root ? { color: toneColor(item) } : {}"
            >
              {{ item.title }}
            </text>
            <text v-if="item.desc" class="tl-desc clamp-3">{{ item.desc }}</text>
            <image
              v-if="item.thumb"
              :src="item.thumb"
              class="tl-thumb"
              mode="aspectFill" />
          </view>
        </view>
      </view>

      <view class="bottom-spacer" />
    </template>
  </view>
</template>

<script>
import { petApi, petDiaryApi, petWeightApi, communityApi } from '@/api'
import { usePetStore } from '@/store'

// 事件类型 → 图标 / 颜色（文案走 i18n：护理类复用 petHub.careTypes.*，其余见 petMemory.*）
const TYPE_META = {
  diary: { icon: 'book', color: '#ff6b9d' },
  BATH: { icon: 'bath', color: '#4aa8f0' },
  VACCINE: { icon: 'syringe', color: '#34c759' },
  DEWORM: { icon: 'pill', color: '#a060ff' },
  EXAM: { icon: 'stethoscope', color: '#f5a623' },
  weight: { icon: 'scale', color: '#ff9500' },
  community: { icon: 'image', color: '#14b8a6' },
  root: { icon: 'home', color: '#fb7185' },
}

// 护理类型白名单（成长记录 recordType 仅支持这些值）
const CARE_KINDS = new Set(['BATH', 'VACCINE', 'DEWORM', 'EXAM'])

export default {
  pageTitleKey: 'pageTitle.petMemory',

  data() {
    return {
      petId: null,
      pet: null,
      timeline: [],
      loaded: false,
    }
  },

  computed: {
    petStore() {
      return usePetStore()
    },
    // 摘要副文案：宠物种类/品种 + 已加入家庭天数
    petMetaSub() {
      const p = this.pet
      if (!p) return ''
      const breedLine = [p.species, p.breed].filter(Boolean).join(' · ')
      const adopt = this.adoptDate(p)
      const familyText = adopt
        ? this.$t('petMemory.inFamilyDays', {
            days: Math.max(
              0,
              Math.floor((this.startOfToday() - this.startOfDay(adopt)) / 86400000),
            ),
          })
        : ''
      return [breedLine, familyText].filter(Boolean).join(' · ')
    },
  },

  onLoad(query) {
    // 与体重页一致：优先 URL petId，缺省回退当前宠物（正常入口均带 petId）
    // 注意：petId 为雪花 ID（约 2e18），超出 JS 安全整数范围(2^53)，
    // 必须保持字符串传递，不能用 Number() 强转，否则精度丢失会请求到错误宠物
    const raw = query.petId === undefined || query.petId === null ? '' : String(query.petId)
    this.petId = raw || null
    if (!this.petId) {
      const pet = this.petStore.activePet || this.petStore.pets[0] || null
      if (pet && pet.id) this.petId = String(pet.id)
    }
    this.loadData()
  },

  methods: {
    onBack() {
      uni.navigateBack()
    },

    // 拉取宠物档案 + 成长日记 + 护理/成长记录 + 体重，组装记忆树
    async loadData() {
      if (!this.petId) {
        this.loaded = true
        return
      }
      try {
        // 各数据源独立拉取：社区帖子按当前宠物过滤 posts/mine?petId=；
        // 单类失败用 allSettled 兜底，不阻塞整棵树
        const results = await Promise.allSettled([
          petApi.getPetDetail(this.petId),
          petDiaryApi.getPetDiaries(this.petId),
          petApi.getGrowthRecords(this.petId),
          petWeightApi.getPetWeights(this.petId),
          communityApi.getMyPosts({ page: 1, size: 100, petId: this.petId }),
        ])
        const valueOf = (i) =>
          results[i] && results[i].status === 'fulfilled' ? results[i].value : null
        this.pet = valueOf(0) || null
        const diaryList = Array.isArray(valueOf(1)) ? valueOf(1) : []
        const recordList = Array.isArray(valueOf(2)) ? valueOf(2) : []
        const weightList = Array.isArray(valueOf(3)) ? valueOf(3) : []
        const postRes = valueOf(4)
        const postList = Array.isArray(postRes && postRes.records) ? postRes.records : []
        this.timeline = this.buildTimeline(diaryList, recordList, weightList, postList)
      } catch (e) {
        console.warn('[pet-memory] load failed', e)
        this.pet = null
      } finally {
        this.loaded = true
      }
    },

    // 把 日记/护理记录/体重/社区帖子 归一为倒序事件流，末尾追加「加入家庭」根节点
    buildTimeline(diaryList, recordList, weightList, postList) {
      const items = []

      // 1) 成长日记：createTime(LocalDateTime) + content + images[0] 缩略图
      diaryList.forEach((d) => {
        const meta = TYPE_META.diary
        items.push({
          key: `diary-${d.id}`,
          root: false,
          type: 'diary',
          ts: this.tsOf(d.createTime),
          day: this.dateKeyOf(d.createTime),
          icon: meta.icon,
          label: this.eventLabel('diary'),
          color: meta.color,
          title: d.title || this.$t('petMemory.diaryFallbackTitle'),
          desc: d.content || '',
          thumb: this.firstImage(d.images),
        })
      })

      // 2) 护理/成长记录：recordDate(LocalDate) 按 BATH/疫苗/驱虫/体检分类；
      //    类型不在白名单时跳过，避免新增类型被误标为体检
      recordList.forEach((r) => {
        const meta = TYPE_META[r.recordType]
        if (!meta || !CARE_KINDS.has(r.recordType)) return
        const label = this.eventLabel(r.recordType)
        items.push({
          key: `record-${r.id}`,
          root: false,
          type: r.recordType,
          ts: this.tsOf(r.recordDate),
          day: this.dateKeyOf(r.recordDate),
          icon: meta.icon,
          label,
          color: meta.color,
          title: r.content || this.$t('petMemory.careDone', { label }),
          desc: '',
          thumb: this.isImageUrl(r.mediaUrl) ? r.mediaUrl : '',
        })
      })

      // 3) 体重记录：measuredAt(LocalDateTime)
      weightList.forEach((w) => {
        const meta = TYPE_META.weight
        items.push({
          key: `weight-${w.id}`,
          root: false,
          type: 'weight',
          ts: this.tsOf(w.measuredAt),
          day: this.dateKeyOf(w.measuredAt),
          icon: meta.icon,
          label: this.eventLabel('weight'),
          color: meta.color,
          title: `${this.numText(w.weight)} ${w.unit || 'kg'}`,
          desc: w.note || '',
          thumb: '',
        })
      })

      // 4) 社区晒宠帖（关联本宠物的社区帖子）：文案 + 首图/封面略缩图
      const postRows = postList || []
      postRows.forEach((p) => {
        const meta = TYPE_META.community
        items.push({
          key: `post-${p.id}`,
          root: false,
          type: 'community',
          ts: this.tsOf(p.createTime),
          day: this.dateKeyOf(p.createTime),
          icon: meta.icon,
          label: this.eventLabel('community'),
          color: meta.color,
          title: '',
          desc: p.content || '',
          thumb: (Array.isArray(p.images) && p.images[0]) || p.cover || '',
        })
      })

      // 事件按时间倒序（最新在上）
      items.sort((a, b) => b.ts - a.ts)

      // 5) 根节点：加入家庭时间（adoptedAt 缺省回退建档时间 createdAt）
      const adopt = this.adoptDate(this.pet)
      if (adopt) {
        const meta = TYPE_META.root
        items.push({
          key: 'root-adopt',
          root: true,
          type: 'root',
          ts: this.tsOf(adopt),
          day: adopt,
          icon: meta.icon,
          label: this.eventLabel('root'),
          color: meta.color,
          title: `${this.pet.name} · ${this.fmtDate(adopt)}`,
          desc: this.$t('petMemory.adoptionDesc'),
          thumb: '',
        })
      }
      return items
    },

    // 事件类型展示文案：日记/体重/加入家庭/社区晒宠走 petMemory，护理类型复用 petHub.careTypes.*
    eventLabel(kind) {
      const dict = {
        diary: 'petMemory.typeDiary',
        weight: 'petMemory.typeWeight',
        root: 'petMemory.typeRoot',
        community: 'petMemory.typeCommunity',
      }
      const key = dict[kind]
      if (key) return this.$t(key)
      if (CARE_KINDS.has(kind)) return this.$t(`petHub.careTypes.${kind}`)
      return kind || ''
    },

    // 各节点颜色（根节点用品牌色系，其余用类型色）
    toneColor(item) {
      return item.color || '#ff6b9d'
    },

    // 加入家庭日期：adoptedAt 优先，档案字段为空时回退建档日 createdAt
    adoptDate(pet) {
      if (!pet) return ''
      return pet.adoptedAt || (pet.createdAt ? this.dateKeyOf(pet.createdAt) : '')
    },

    // 统一把 LocalDate/LocalDateTime 截取为 YYYY-MM-DD
    dateKeyOf(v) {
      if (!v) return ''
      const s = String(v)
      return s.length >= 10 ? s.slice(0, 10) : s
    },

    // 展示日期：YYYY-MM-DD → YYYY/MM/DD
    fmtDate(key) {
      return key ? key.replace(/-/g, '/') : ''
    },

    // 当天 0 点时间戳（本地时区），用于“加入家庭 N 天”计算
    startOfToday() {
      const d = new Date()
      return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
    },

    startOfDay(key) {
      const [y, m, d] = String(key).split('-').map(Number)
      return new Date(y, (m || 1) - 1, d || 1).getTime()
    },

    // 时间字符串 → 时间戳（LocalDateTime 无时区按本地解析，足够跨源排序）
    tsOf(v) {
      if (!v) return 0
      const t = new Date(String(v).replace(' ', 'T')).getTime()
      return Number.isNaN(t) ? 0 : t
    },

    // 体重数字文本：整数不带小数点
    numText(w) {
      const n = Number(w)
      if (Number.isNaN(n)) return '--'
      return n % 1 === 0 ? String(n) : String(Math.round(n * 10) / 10)
    },

    // 日记 images：兼容数组 / JSON 字符串 / 单个 URL
    firstImage(images) {
      if (!images) return ''
      if (Array.isArray(images)) return images[0] || ''
      const s = String(images).trim()
      if (!s) return ''
      try {
        const arr = JSON.parse(s)
        if (Array.isArray(arr)) return arr[0] || ''
      } catch (e) {
        /* 非 JSON 直接按单 URL 处理 */
      }
      return s
    },

    // mediaUrl 仅当为图片地址时作为缩略图
    isImageUrl(url) {
      if (!url) return false
      return /^(https?:)?\/\//.test(url) || /\.(jpe?g|png|gif|webp|heic)$/i.test(url)
    },
  },
}
</script>

<style lang="scss" scoped>
.memory {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 48rpx;
}

/* ===== 顶部导航 ===== */
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  padding-top: calc(var(--status-bar-height, 0px) + env(safe-area-inset-top, 0px));
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
}

.header-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 40rpx;
  color: var(--color-text);
}

.header-btn-placeholder {
  /* 右侧留白占位，让标题居中 */
  opacity: 0;
}

.header-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

/* ===== 宠物摘要 ===== */
.pet-strip {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 24rpx 24rpx 0;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.pet-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-background);
}

.pet-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  font-size: 48rpx;
}

.pet-meta {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  flex: 1;
  min-width: 0;
}

.pet-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.pet-sub {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

/* ===== 记忆树（时间轴） ===== */
.tree-title {
  margin: 40rpx 32rpx 20rpx;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.timeline {
  display: flex;
  flex-direction: column;
  padding: 0 24rpx;
}

.tl-item {
  display: flex;
}

/* 树干：节点(圆点图标) + 竖向连接线 */
.tl-track {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 72rpx;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.tl-node {
  width: 56rpx;
  height: 56rpx;
  margin-top: 24rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 28rpx;
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
}

.tl-line {
  flex: 1;
  width: 2rpx;
  margin-top: 4rpx;
  background: var(--color-divider);
}

/* 树枝：事件卡片 */
.tl-card {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 20rpx 24rpx;
  margin: 24rpx 0 0;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.tl-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.tl-type {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.tl-date {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.tl-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  word-break: break-all;
}

.tl-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.6;
}

/* 缩略图（日记文案首图 / 记录附图） */
.tl-thumb {
  width: 100%;
  height: 200rpx;
  border-radius: var(--radius-sm, 12rpx);
  background: var(--color-background);
  margin-top: 4rpx;
}

/* 加入家庭根节点：浅色底强调 */
.tl-card-root {
  background: #fff1f2;
  border: 1rpx solid rgba(251, 113, 133, 0.35);
}

.clamp-3 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}

/* ===== 兜底空态 ===== */
.pet-missing {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  padding-top: 240rpx;
}

.pet-missing-icon {
  font-size: 96rpx;
  color: var(--color-text-tertiary);
}

.pet-missing-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.bottom-spacer {
  height: 24rpx;
}
</style>
