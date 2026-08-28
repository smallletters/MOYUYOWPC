<template>
  <view class="pet-dresser">
    <view class="page-header">
      <view class="back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">宠物装扮</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 宠物预览 -->
      <view class="preview-card">
        <image :src="currentPet?.avatar || ''" class="pet-avatar" />
        <text class="pet-name">{{ currentPet?.name || '宠物' }}</text>
        <text class="preview-tip">点击右侧装扮为宠物添加配饰</text>
      </view>

      <!-- 装扮分类 -->
      <view class="cat-tabs">
        <view
          v-for="cat in categories"
          :key="cat.id"
          class="cat-tab"
          :class="{ active: activeCat === cat.id }"
          @click="activeCat = cat.id"
        >
          {{ cat.label }}
        </view>
      </view>

      <!-- 装扮列表 -->
      <view class="dresser-grid">
        <view
          v-for="d in filteredItems"
          :key="d.id"
          class="dresser-card"
          :class="{ equipped: d.equipped }"
          @click="onEquip(d)"
        >
          <image :src="d.image" class="dresser-image" />
          <text class="dresser-name">{{ d.name }}</text>
          <text v-if="d.equipped" class="equipped-tag">已穿戴</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { petApi } from '@/api'

export default {
  data() {
    return {
      currentPet: null,
      activeCat: 'all',
      categories: [
        { id: 'all', label: '全部' },
        { id: 'hat', label: '帽子' },
        { id: 'scarf', label: '围巾' },
        { id: 'clothes', label: '衣服' },
        { id: 'toy', label: '玩具' },
      ],
      items: [
        {
          id: 1,
          name: '蝴蝶结',
          category: 'hat',
          image: 'https://picsum.photos/200/200?random=40',
          equipped: true,
        },
        {
          id: 2,
          name: '贝雷帽',
          category: 'hat',
          image: 'https://picsum.photos/200/200?random=41',
        },
        {
          id: 3,
          name: '圣诞帽',
          category: 'hat',
          image: 'https://picsum.photos/200/200?random=42',
        },
        {
          id: 4,
          name: '格子围巾',
          category: 'scarf',
          image: 'https://picsum.photos/200/200?random=43',
        },
        {
          id: 5,
          name: '潮流外套',
          category: 'clothes',
          image: 'https://picsum.photos/200/200?random=44',
        },
        {
          id: 6,
          name: '毛绒球',
          category: 'toy',
          image: 'https://picsum.photos/200/200?random=45',
        },
      ],
    }
  },

  computed: {
    filteredItems() {
      if (this.activeCat === 'all') return this.items
      return this.items.filter((i) => i.category === this.activeCat)
    },
  },

  onShow() {
    this.loadPet()
  },

  methods: {
    async loadPet() {
      try {
        const pets = await petApi.getPetList()
        this.currentPet = (pets && pets[0]) || null
      } catch (e) {
        this.currentPet = { name: '宠物' }
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onEquip(item) {
      this.items.forEach((i) => (i.equipped = false))
      item.equipped = true
      uni.showToast({ title: `已穿戴 ${item.name}`, icon: 'success' })
    },
  },
}
</script>

<style lang="scss" scoped>
.pet-dresser {
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
  margin-right: 60rpx;
}

.content {
  padding: 24rpx;
}

.preview-card {
  padding: 32rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  text-align: center;
  margin-bottom: 24rpx;
}

.pet-avatar {
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  background: var(--color-background);
  margin-bottom: 16rpx;
}

.pet-name {
  display: block;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 8rpx;
}

.preview-tip {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.cat-tabs {
  display: flex;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin-bottom: 16rpx;
}

.cat-tab {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.cat-tab.active {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.dresser-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.dresser-card {
  position: relative;
  padding: 16rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.dresser-card.equipped {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.dresser-image {
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  background: var(--color-background);
}

.dresser-name {
  font-size: var(--font-size-xs);
  color: var(--color-text);
  text-align: center;
}

.equipped-tag {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  padding: 2rpx 8rpx;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: 999rpx;
  font-size: 18rpx;
}
</style>
