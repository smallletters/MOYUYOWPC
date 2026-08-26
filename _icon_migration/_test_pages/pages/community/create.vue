<template>
  <view class="create-post">
    <view class="form">
      <textarea
        v-model="content"
        class="content-input"
        placeholder="What's on your mind? Share your pet moments..."
        maxlength="2000"
      />
      <view class="image-upload" @click="onUploadImage">
        <text v-if="!images.length" class="upload-placeholder">+ Add Photo</text>
        <image
          v-else
          :src="images[0]"
          class="preview-image"
          mode="aspectFill" />
      </view>
      <view class="topic-row">
        <text class="label">Topic (optional)</text>
        <input v-model="topic" class="topic-input" placeholder="e.g. dog, cat, travel">
      </view>
    </view>

    <view class="btn btn-primary publish-btn" :class="{ disabled: !content }" @click="onPublish">
      Publish
    </view>
  </view>
</template>

<script>
import { communityApi } from '@/api'

export default {
  data() {
    return {
      content: '',
      images: [],
      topic: '',
    }
  },

  methods: {
    onUploadImage() {
      uni.chooseImage({
        count: 1,
        success: (res) => {
          this.images = res.tempFilePaths
        },
      })
    },

    async onPublish() {
      if (!this.content) return
      uni.showLoading({ title: 'Publishing...', mask: true })
      try {
        await communityApi.createPost(this.content, this.images, this.topic || null)
        uni.hideLoading()
        uni.showToast({ title: 'Published!', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1500)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: 'Failed to publish', icon: 'none' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.create-post {
  min-height: 100vh;
  background: var(--color-background);
  padding: 24rpx;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.content-input {
  width: 100%;
  height: 300rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  font-size: var(--font-size-base);
  line-height: 1.6;
}
.image-upload {
  width: 200rpx;
  height: 200rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx dashed var(--color-divider);
}
.upload-placeholder {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}
.preview-image {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-md);
  object-fit: cover;
}
.topic-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.topic-input {
  flex: 1;
  padding: 16rpx 20rpx;
  background: var(--color-surface);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
}
.publish-btn {
  padding: 28rpx 0;
  font-size: var(--font-size-md);
  margin-top: 48rpx;
}
.publish-btn.disabled {
  opacity: 0.5;
}
</style>
