<template>
  <view class="returns">
    <view class="page-header">
      <view class="back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">申请售后</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 类型切换 -->
      <scroll-view scroll-x class="type-scroll">
        <view class="type-list">
          <view
            v-for="t in types"
            :key="t.value"
            class="type-btn"
            :class="{ active: activeType === t.value }"
            @click="activeType = t.value"
          >
            {{ t.label }}
          </view>
        </view>
      </scroll-view>

      <!-- 商品列表 -->
      <view class="goods-card">
        <image :src="orderItem?.mainImage || ''" class="goods-image" />
        <view class="goods-info">
          <text class="goods-name">{{ orderItem?.productName || '商品名称' }}</text>
          <text class="goods-spec">{{ orderItem?.skuSpec || '' }}</text>
          <text class="goods-price">${{ orderItem?.price || 0 }}</text>
        </view>
      </view>

      <!-- 退款原因 -->
      <view class="section">
        <text class="section-title">退款原因</text>
        <view class="reason-grid">
          <view
            v-for="r in reasons"
            :key="r"
            class="reason-chip"
            :class="{ selected: selectedReason === r }"
            @click="selectedReason = r"
          >
            {{ r }}
          </view>
        </view>
      </view>

      <!-- 退款金额 -->
      <view class="section">
        <text class="section-title">退款金额</text>
        <view class="amount-row">
          <text class="amount-label">申请退款</text>
          <input v-model.number="refundAmount" type="number" class="amount-input">
          <text class="amount-unit">元</text>
        </view>
        <text class="amount-tip">最多可退 ${{ orderItem?.price || 0 }}</text>
      </view>

      <!-- 退款说明 -->
      <view class="section">
        <text class="section-title">退款说明</text>
        <textarea
          v-model="description"
          class="desc-input"
          placeholder="请描述问题，便于客服快速处理"
          maxlength="200"
        />
      </view>

      <!-- 上传凭证 -->
      <view class="section">
        <text class="section-title">上传凭证</text>
        <view class="upload-list">
          <view v-for="(img, i) in uploadImages" :key="i" class="upload-item">
            <image :src="img" class="upload-image" />
            <view class="upload-remove" @click="uploadImages.splice(i, 1)">
              <text class="luc luc-x" />
            </view>
          </view>
          <view v-if="uploadImages.length < 3" class="upload-add" @click="onAddImage">
            <text>+</text>
            <text class="upload-add-text">添加图片</text>
          </view>
        </view>
      </view>

      <view class="bottom-bar safe-area-bottom">
        <view class="btn-primary" aria-label="提交申请" @click="onSubmit">提交申请</view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { orderApi } from '@/api'

export default {
  data() {
    return {
      types: [
        { value: 'refund', label: '退货退款' },
        { value: 'only-refund', label: '仅退款' },
        { value: 'exchange', label: '换货' },
        { value: 'price-protect', label: '价格保护' },
      ],
      activeType: 'refund',
      reasons: ['质量问题', '与描述不符', '不想要了', '收到错误商品', '商品损坏', '其他'],
      selectedReason: '',
      refundAmount: 0,
      description: '',
      uploadImages: [],
      orderItem: null,
    }
  },

  onLoad(query) {
    if (query.amount) this.refundAmount = Number(query.amount) || 0
    if (query.orderId) this.loadOrderItem(query.orderId)
  },

  methods: {
    async loadOrderItem(orderId) {
      try {
        const order = await orderApi.getOrderDetail(orderId)
        this.orderItem = (order.items || [])[0] || null
        if (this.orderItem?.price) this.refundAmount = this.orderItem.price
      } catch (e) {
        console.warn('[returns] load order failed', e)
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onAddImage() {
      uni.chooseImage({
        count: 3 - this.uploadImages.length,
        success: (res) => {
          this.uploadImages.push(...res.tempFilePaths)
        },
      })
    },

    onSubmit() {
      if (!this.selectedReason) {
        uni.showToast({ title: '请选择退款原因', icon: 'none' })
        return
      }
      uni.showLoading({ title: '提交中...' })
      setTimeout(() => {
        uni.hideLoading()
        uni.showToast({ title: '申请已提交', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      }, 1000)
    },
  },
}
</script>

<style lang="scss" scoped>
.returns {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 140rpx;
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

.type-scroll {
  white-space: nowrap;
  margin-bottom: 24rpx;
}

.type-list {
  display: inline-flex;
  gap: 16rpx;
}

.type-btn {
  display: inline-flex;
  align-items: center;
  height: 72rpx;
  padding: 0 32rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: 999rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.type-btn.active {
  background: var(--color-primary);
  color: var(--color-text);
  border-color: var(--color-primary);
}

.goods-card {
  display: flex;
  gap: 16rpx;
  padding: 16rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  margin-bottom: 24rpx;
}

.goods-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
}

.goods-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.goods-name {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.goods-spec {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.goods-price {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  margin-top: auto;
}

.section {
  margin-bottom: 24rpx;
}

.section-title {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.reason-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.reason-chip {
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.reason-chip.selected {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.amount-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.amount-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.amount-input {
  flex: 1;
  font-size: var(--font-size-lg);
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
  text-align: right;
}

.amount-unit {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.amount-tip {
  display: block;
  margin-top: 8rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.desc-input {
  width: 100%;
  height: 160rpx;
  padding: 16rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.upload-item {
  position: relative;
  width: 160rpx;
  height: 160rpx;
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
  width: 160rpx;
  height: 160rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  background: var(--color-surface);
  border: 1rpx dashed var(--color-divider);
  border-radius: var(--radius-sm);
  color: var(--color-text-tertiary);
  font-size: 48rpx;
}

.upload-add-text {
  font-size: var(--font-size-xs);
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.btn-primary {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: 999rpx;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}
</style>
