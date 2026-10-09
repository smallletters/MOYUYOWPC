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
            >
              <text class="luc luc-star" />
            </text>
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
          >
            {{ tag }}
          </view>
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
          <view v-for="(img, idx) in item.images || []" :key="idx" class="image-preview">
            <image :src="img" mode="aspectFill" />
            <view class="image-remove" @click="removeImage(item, idx)">
              <text class="luc luc-x" />
            </view>
          </view>
          <view v-if="(item.images || []).length < 9" class="image-add" @click="addImage(item)">
            +
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar safe-area-bottom">
      <view class="btn btn-primary submit-btn" @click="onSubmit">
        {{ $t('orderReview.submit') }}
      </view>
    </view>
  </view>
</template>

<script>
import { orderApi, reviewApi } from '@/api'
import { uploadImage } from '@/api/upload'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.orderReview',

  data() {
    return {
      orderId: null,
      orderItems: [],
      // presetTags 在 computed 中读取,跟随 locale 切换
      localeVersion: 0,
    }
  },

  computed: {
    presetTags() {
      void this.localeVersion
      return i18n.t('orderReview.presetTags')
    },
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  async onLoad(query) {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    this.orderId = query.orderId
    try {
      const order = await orderApi.getOrderDetail(this.orderId)
      if (order && order.items) {
        this.orderItems = order.items.map((item) => ({
          id: item.id,
          productId: item.productId,
          skuId: item.skuId,
          name: item.productName,
          image: { src: item.mainImage || '' },
          rating: 0,
          tags: [],
          content: '',
          images: [],
        }))
      }
    } catch (e) {
      console.warn('[review] load order items failed', e)
    }
  },

  methods: {
    toggleTag(item, tag) {
      item.tags = item.tags || []
      const idx = item.tags.indexOf(tag)
      if (idx >= 0) item.tags.splice(idx, 1)
      else item.tags.push(tag)
    },

    /**
     * 选图 → 上传 → 拿到后端 URL 后再 push 到 item.images。
     * 与 pet/profile.vue 头像上传保持同一套流程(uni.chooseImage + uploadImage),
     * 防止把临时路径(tempFilePaths/带 _tmp 后缀)直接交给后端——后端拿这种路径无法显示。
     * 失败时只 toast 当前图片,不阻断用户继续编辑或删除其它图。
     */
    async addImage(item) {
      if ((item.images || []).length >= 9) return
      try {
        // 单张选图;压缩以节省流量;显式 ['album'] 绕开 Android 上
        // uni-app 的 sourceType 弹层文案渲染 bug(显示成 "uni.chooseImage.sourceType.xxx")
        const chooseRes = await uni.chooseImage({
          count: 1,
          sizeType: ['compressed'],
          sourceType: ['album'],
        })
        const filePath = chooseRes.tempFilePaths?.[0]
        if (!filePath) return
        uni.showLoading({ title: i18n.t('common.loading'), mask: true })
        const uploadRes = await uploadImage(filePath)
        const url = uploadRes?.url
        uni.hideLoading()
        if (!url) throw new Error('Upload returned no URL')
        item.images = item.images || []
        item.images.push(url)
      } catch (e) {
        uni.hideLoading()
        // 用户在系统选择器里"取消"时,errMsg 含 "cancel",不弹错误
        const msg = String(e?.errMsg || e?.message || '')
        if (msg.includes('cancel')) return
        uni.showToast({
          title: e?.message || i18n.t('orderReview.uploadFailed'),
          icon: 'none',
        })
      }
    },

    removeImage(item, idx) {
      item.images.splice(idx, 1)
    },

    async onSubmit() {
      for (const item of this.orderItems) {
        if (!item.rating) {
          uni.showToast({
            title: i18n.t('orderReview.ratingRequired', { name: item.name }),
            icon: 'none',
          })
          return
        }
      }
      uni.showLoading({ title: i18n.t('common.loading') })
      try {
        for (const item of this.orderItems) {
          await reviewApi.createReview({
            productId: item.productId,
            orderId: this.orderId ? Number(this.orderId) : null,
            orderItemId: item.id,
            rating: item.rating,
            content: item.content,
            tags: item.tags || [],
            images: item.images || [],
          })
        }
        uni.hideLoading()
        uni.showToast({ title: i18n.t('orderReview.submitted'), icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e?.message || i18n.t('orderReview.failed'), icon: 'none' })
      }
    },
  },
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
