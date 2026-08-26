<template>
  <view class="pet-album">
    <view class="page-header">
      <view class="back" @click="goBack" aria-label="返回">‹</view>
      <text class="title">{{ currentPet?.name || '宠物' }}的相册</text>
      <view class="upload-btn" @click="onUpload" aria-label="上传照片">+</view>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 月份切换 -->
      <view class="month-nav">
        <view class="month-arrow" @click="prevMonth" aria-label="上个月">‹</view>
        <text class="month-title">{{ currentMonth }}</text>
        <view class="month-arrow" @click="nextMonth" aria-label="下个月">›</view>
      </view>

      <!-- 照片网格 -->
      <view class="photo-grid" aria-label="照片网格">
        <view v-for="(photo, i) in photos" :key="i" class="photo-item" @click="onPhotoClick(i)">
          <image :src="photo" class="photo-image" mode="aspectFill" />
        </view>
        <view v-if="photos.length === 0" class="empty">暂无照片</view>
      </view>

      <!-- FAB -->
      <view class="fab" @click="onUpload" aria-label="添加新照片">+</view>
    </scroll-view>
  </view>
</template>

<script>
import { petApi } from '@/api'

export default {
  data() {
    return {
      currentPet: null,
      currentMonth: '',
      photos: [],
    }
  },

  onShow() {
    this.loadData()
  },

  methods: {
    async loadData() {
      try {
        const pets = await petApi.getPetList()
        this.currentPet = (pets && pets[0]) || null
      } catch (e) {
        this.currentPet = { name: '宠物' }
      }
      // mock photos
      this.photos = [
        'https://picsum.photos/300/300?random=20',
        'https://picsum.photos/300/300?random=21',
        'https://picsum.photos/300/300?random=22',
        'https://picsum.photos/300/300?random=23',
        'https://picsum.photos/300/300?random=24',
        'https://picsum.photos/300/300?random=25',
      ]
      this.currentMonth = this.formatMonth(new Date())
    },

    formatMonth(date) {
      return `${date.getFullYear()}年${date.getMonth() + 1}月`
    },

    goBack() {
      uni.navigateBack()
    },

    prevMonth() {
      uni.showToast({ title: '上个月', icon: 'none' })
    },

    nextMonth() {
      uni.showToast({ title: '下个月', icon: 'none' })
    },

    onUpload() {
      uni.chooseImage({
        count: 9,
        success: (res) => {
          this.photos.unshift(...res.tempFilePaths)
          uni.showToast({ title: '已上传', icon: 'success' })
        },
      })
    },

    onPhotoClick(i) {
      uni.previewImage({
        current: this.photos[i],
        urls: this.photos,
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.pet-album {
  min-height: 100vh;
  background: var(--color-background);
}

.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-primary);
}

.back,
.upload-btn {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  color: #fff;
}

.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: #fff;
}

.content {
  padding: 16rpx;
}

.month-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  padding: 24rpx 0;
}

.month-arrow {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}

.month-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8rpx;
}

.photo-item {
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: var(--color-background);
}

.photo-image {
  width: 100%;
  height: 100%;
}

.empty {
  grid-column: span 3;
  padding: 96rpx 0;
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.fab {
  position: fixed;
  left: 50%;
  bottom: calc(48rpx + env(safe-area-inset-bottom));
  transform: translateX(-50%);
  width: 112rpx;
  height: 112rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: 50%;
  font-size: 56rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.15);
}
</style>