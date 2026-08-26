<template>
  <view class="post-create">
    <view class="page-header">
      <view class="cancel" @click="goBack">取消</view>
      <text class="title">发布</text>
      <view class="publish-btn" @click="onPublish">发布</view>
    </view>

    <scroll-view scroll-y class="content">
      <textarea
        v-model="content"
        class="content-input"
        placeholder="说点什么吧..."
        maxlength="500"
      />

      <!-- 上传图片 -->
      <view class="upload-section">
        <view class="upload-list">
          <view v-for="(img, i) in images" :key="i" class="upload-item">
            <image :src="img" class="upload-image" />
            <view class="upload-remove" @click="images.splice(i, 1)">×</view>
          </view>
          <view v-if="images.length < 9" class="upload-add" @click="onAddImage">
            <text class="upload-add-icon">+</text>
            <text class="upload-add-text">添加图片</text>
          </view>
        </view>
      </view>

      <!-- 选项 -->
      <view class="option-list">
        <view class="option-item">
          <text>话题</text>
          <view class="option-right">
            <text class="option-text">{{ topic || '选择话题' }}</text>
            <text class="arrow">›</text>
          </view>
        </view>
        <view class="option-item">
          <text>位置</text>
          <view class="option-right">
            <text class="option-text">{{ location || '不显示位置' }}</text>
            <text class="arrow">›</text>
          </view>
        </view>
        <view class="option-item">
          <text>谁可以看</text>
          <view class="option-right">
            <text class="option-text">公开</text>
            <text class="arrow">›</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { communityApi } from '@/api'
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      content: '',
      images: [],
      topic: '',
      location: '',
      submitting: false,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
  },

  onLoad(query) {
    // 支持从外部传入预选话题
    if (query && query.topic) {
      this.topic = decodeURIComponent(query.topic)
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onAddImage() {
      uni.chooseImage({
        count: 9 - this.images.length,
        success: (res) => {
          this.images.push(...res.tempFilePaths)
        },
      })
    },

    async onPublish() {
      if (!this.userStore.isLoggedIn) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        return
      }
      if (!this.content && this.images.length === 0) {
        uni.showToast({ title: '请输入内容或上传图片', icon: 'none' })
        return
      }
      if (this.submitting) return
      this.submitting = true
      uni.showLoading({ title: '发布中...' })
      try {
        // 后端接口 /api/v1/community/posts（POST）
        const result = await communityApi.createPost(
          this.content,
          this.images,
          this.topic || null,
        )
        uni.hideLoading()
        uni.showToast({ title: '发布成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
        // result 是 CommunityPostVO，发帖成功可携带 id
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || '发布失败', icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.post-create {
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

.cancel {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.publish-btn {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.content {
  padding: 24rpx;
}

.content-input {
  width: 100%;
  min-height: 240rpx;
  font-size: var(--font-size-base);
  color: var(--color-text);
  line-height: 1.6;
  background: transparent;
}

.upload-section {
  padding: 24rpx 0;
  border-top: 1rpx solid var(--color-divider);
}

.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.upload-item {
  position: relative;
  width: 200rpx;
  height: 200rpx;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.upload-image {
  width: 100%;
  height: 100%;
}

.upload-remove {
  position: absolute;
  top: -10rpx;
  right: -10rpx;
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-text);
  color: #fff;
  border-radius: 50%;
  font-size: 24rpx;
}

.upload-add {
  width: 200rpx;
  height: 200rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  background: var(--color-surface);
  border: 1rpx dashed var(--color-divider);
  border-radius: var(--radius-sm);
  color: var(--color-text-tertiary);
}

.upload-add-icon {
  font-size: 56rpx;
}

.upload-add-text {
  font-size: var(--font-size-xs);
}

.option-list {
  margin-top: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.option-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.option-item:last-child {
  border-bottom: none;
}

.option-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.option-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.arrow {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}
</style>