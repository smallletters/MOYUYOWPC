<template>
  <view class="review">
    <scroll-view scroll-y class="form">
      <view v-for="item in orderItems" :key="item.id" class="card review-item">
        <view class="item-info">
          <image :src="item.image?.src" class="item-image" />
          <text class="item-name">{{ item.name }}</text>
        </view>
        <!-- 评分 -->
        <view class="rating-row">
          <text>Rating</text>
          <view class="stars">
            <text
              v-for="i in 5"
              :key="i"
              class="star"
              :class="{ filled: (item.rating || 0) >= i }"
              @click="item.rating = i"
            >★</text>
          </view>
        </view>
        <!-- 标签 -->
        <view class="tags">
          <view
            v-for="tag in presetTags"
            :key="tag"
            class="tag-chip"
            :class="{ active: (item.tags || []).includes(tag) }"
            @click="toggleTag(item, tag)"
          >{{ tag }}</view>
        </view>
        <!-- 文字评价 -->
        <textarea
          v-model="item.content"
          class="textarea"
          placeholder="Share your thoughts..."
          :maxlength="500"
        />
        <!-- 图片上传 -->
        <view class="images">
          <view
            v-for="(img, idx) in (item.images || [])"
            :key="idx"
            class="image-preview"
          >
            <image :src="img" mode="aspectFill" />
            <view class="image-remove" @click="removeImage(item, idx)">×</view>
          </view>
          <view v-if="(item.images || []).length < 9" class="image-add" @click="addImage(item)">+</view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar safe-area-bottom">
      <view class="btn btn-primary submit-btn" @click="onSubmit">Submit Reviews</view>
    </view>
  </view>
</template>

<script>
import { productApi } from '@/api'

export default {
  data() {
    return {
      orderId: null,
      orderItems: [],
      presetTags: ['Great Quality', 'Smells Good', 'Pet Loves It', 'True to Size', 'Fast Delivery']
    }
  },

  onLoad(query) {
    this.orderId = query.orderId
    // 实际项目应根据 orderId 拉取订单项
    this.orderItems = [
      { id: 1, name: 'MILO Harness', image: { src: 'https://picsum.photos/200?random=1' }, rating: 0, tags: [], content: '', images: [] },
      { id: 2, name: 'LUNA Care Set', image: { src: 'https://picsum.photos/200?random=2' }, rating: 0, tags: [], content: '', images: [] }
    ]
  },

  methods: {
    toggleTag(item, tag) {
      item.tags = item.tags || []
      const idx = item.tags.indexOf(tag)
      if (idx >= 0) item.tags.splice(idx, 1)
      else item.tags.push(tag)
    },

    addImage(item) {
      // 实际项目应调 uni.chooseImage
      item.images = item.images || []
      item.images.push(`https://picsum.photos/200?random=${Math.floor(Math.random() * 100)}`)
    },

    removeImage(item, idx) {
      item.images.splice(idx, 1)
    },

    async onSubmit() {
      // 校验
      for (const item of this.orderItems) {
        if (!item.rating) {
          uni.showToast({ title: '请为每件商品评分', icon: 'none' })
          return
        }
      }
      uni.showLoading({ title: '提交中...' })
      try {
        for (const item of this.orderItems) {
          await productApi.submitProductReview({
            product_id: item.id,
            review: item.content,
            rating: item.rating
          })
        }
        uni.hideLoading()
        uni.showToast({ title: 'Reviews submitted', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: 'Submit failed', icon: 'none' })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.review {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.form {
  flex: 1;
  padding: 16rpx;
}

.review-item {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.item-info {
  display: flex;
  gap: 16rpx;
  align-items: center;
  margin-bottom: 16rpx;
}

.item-image {
  width: 100rpx;
  height: 100rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
}

.item-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.rating-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
  border-top: 1rpx solid var(--color-divider);
}

.stars {
  display: flex;
  gap: 8rpx;
}

.star {
  font-size: 40rpx;
  color: var(--color-divider);
}

.star.filled {
  color: var(--color-primary);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding: 16rpx 0;
  border-top: 1rpx solid var(--color-divider);
}

.tag-chip {
  padding: 8rpx 20rpx;
  font-size: var(--font-size-xs);
  background: var(--color-background);
  color: var(--color-text-secondary);
  border-radius: var(--radius-pill);
}

.tag-chip.active {
  background: rgba(219, 201, 138, 0.15);
  color: var(--color-primary-dark);
}

.textarea {
  width: 100%;
  height: 200rpx;
  padding: 16rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-base);
  margin-top: 16rpx;
  box-sizing: border-box;
}

.images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}

.image-preview {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--color-background);
}

.image-preview image {
  width: 100%;
  height: 100%;
}

.image-remove {
  position: absolute;
  top: 0;
  right: 0;
  width: 40rpx;
  height: 40rpx;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  text-align: center;
  line-height: 40rpx;
  font-size: 28rpx;
}

.image-add {
  width: 160rpx;
  height: 160rpx;
  border: 2rpx dashed var(--color-divider);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  color: var(--color-text-tertiary);
}

.bottom-bar {
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.submit-btn {
  padding: 24rpx 0;
  font-size: var(--font-size-md);
}
</style>
