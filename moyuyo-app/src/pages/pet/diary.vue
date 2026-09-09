<template>
  <view class="pet-diary">
    <view class="page-header">
      <view class="back" :aria-label="$t('petDiary.title')" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">{{ $t('petDiary.title') }}</text>
      <view class="add-btn" @click="onAdd">+ {{ $t('petDiary.add') }}</view>
    </view>

    <scroll-view scroll-y class="content">
      <view v-if="!loading && entries.length === 0" class="empty">
        <text class="empty-icon luc-book" />
        <text class="empty-text">{{ $t('petDiary.empty') }}</text>
      </view>

      <view v-else class="entry-list">
        <view v-for="e in entries" :key="e.id" class="entry-card">
          <view class="entry-header">
            <text class="entry-date">{{ e.date }}</text>
            <text class="entry-pet">{{ e.petName }}</text>
          </view>
          <text class="entry-content">{{ e.content }}</text>
          <view v-if="e.image" class="entry-image-wrap">
            <image :src="e.image" class="entry-image" />
          </view>
          <view class="entry-mood">
            <text>{{ e.mood }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { petApi, petDiaryApi } from '@/api'
import { usePetStore } from '@/store'

export default {
  pageTitleKey: 'pageTitle.petDiary',

  data() {
    return {
      petId: null,
      loading: true,
      entries: [],
    }
  },

  computed: {
    petStore() {
      return usePetStore()
    },
  },

  onLoad(query) {
    // 与记忆树等页一致：URL petId 优先，缺省回退当前宠物
    // petId 为雪花 ID（约 2e18），超出 JS 安全整数范围(2^53)，
    // 必须保持字符串传递，不能用 Number() 强转，否则精度丢失会请求到错误宠物
    const raw = query.petId === undefined || query.petId === null ? '' : String(query.petId)
    this.petId = raw || null
    if (!this.petId) {
      const pet = this.petStore.activePet || this.petStore.pets[0] || null
      if (pet && pet.id) this.petId = String(pet.id)
    }
    this.loadDiaries()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    // 拉取宠物档案（取名字）+ 真实成长日记列表，映射为卡片展示数据
    async loadDiaries() {
      if (!this.petId) {
        this.loading = false
        return
      }
      try {
        const [pet, list] = await Promise.all([
          petApi.getPetDetail(this.petId),
          petDiaryApi.getPetDiaries(this.petId),
        ])
        const rows = Array.isArray(list) ? list : []
        const petName = pet?.name || ''
        this.entries = rows.map((d) => ({
          id: d.id,
          date: this.fmtDate(d.createTime),
          petName,
          content: d.content || '',
          image: this.firstImage(d.images),
          mood: d.mood || '',
        }))
      } catch (e) {
        console.warn('[pet-diary] load failed', e)
        this.entries = []
      } finally {
        this.loading = false
      }
    },

    onAdd() {
      uni.showToast({ title: this.$t('petDiary.addToast'), icon: 'none' })
    },

    // LocalDateTime → YYYY-MM-DD
    fmtDate(v) {
      if (!v) return ''
      const s = String(v)
      return s.length >= 10 ? s.slice(0, 10) : s
    },

    // 日记图片：兼容数组 / JSON 字符串 / 单个 URL
    firstImage(images) {
      if (!images) return ''
      if (Array.isArray(images)) return images[0] || ''
      const s = String(images).trim()
      if (!s) return ''
      try {
        const arr = JSON.parse(s)
        if (Array.isArray(arr)) return arr[0] || ''
      } catch (e) {
        /* 非 JSON 按单 URL 处理 */
      }
      return s
    },
  },
}
</script>

<style lang="scss" scoped>
.pet-diary {
  min-height: 100vh;
  background: var(--color-background);
}

.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  color: var(--color-text);
}

.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.add-btn {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.content {
  padding: 24rpx;
}

.empty {
  padding: 96rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.empty-icon {
  font-size: 120rpx;
  opacity: 0.4;
}

.empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.entry-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.entry-card {
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.entry-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.entry-date {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.entry-pet {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.entry-content {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  line-height: 1.6;
  margin-bottom: 12rpx;
}

.entry-image-wrap {
  margin-bottom: 12rpx;
}

.entry-image {
  width: 100%;
  border-radius: var(--radius-sm);
  aspect-ratio: 3 / 2;
  object-fit: cover;
}

.entry-mood {
  padding-top: 12rpx;
  border-top: 1rpx solid var(--color-divider);
  font-size: var(--font-size-xs);
  color: var(--color-primary);
}
</style>
