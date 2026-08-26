<template>
  <view class="refund-page">
    <view v-if="order" class="card order-info">
      <text class="card-title">Order #{{ order.orderNo }}</text>
      <text class="order-amount">Amount: ${{ order.payAmount }}</text>
    </view>

    <view class="card form-card">
      <view class="form-item">
        <text class="label">Refund Type</text>
        <view class="radio-group">
          <view class="radio-row" @click="form.type = 'FULL'">
            <view class="radio" :class="{ checked: form.type === 'FULL' }">
              <text v-if="form.type === 'FULL'"><text class="luc luc-check"></text></text>
            </view>
            <text>Full Refund</text>
          </view>
          <view class="radio-row" @click="form.type = 'PARTIAL'">
            <view class="radio" :class="{ checked: form.type === 'PARTIAL' }">
              <text v-if="form.type === 'PARTIAL'"><text class="luc luc-check"></text></text>
            </view>
            <text>Partial Refund</text>
          </view>
        </view>
      </view>

      <view v-if="form.type === 'PARTIAL'" class="form-item">
        <text class="label">Refund Amount ($)</text>
        <input
          v-model="form.amount"
          class="input"
          type="digit"
          placeholder="0.00">
      </view>

      <view class="form-item">
        <text class="label">Reason</text>
        <picker :value="reasonIndex" :range="reasonOptions" @change="onReasonChange">
          <view class="picker">
            <text>{{ reasonOptions[reasonIndex] || 'Select a reason' }}</text>
            <text class="arrow"><text class="luc luc-chevron-right"></text></text>
          </view>
        </picker>
      </view>

      <view class="form-item">
        <text class="label">Description (optional)</text>
        <textarea v-model="form.description" class="textarea" placeholder="Describe the issue..." />
      </view>
    </view>

    <view class="btn btn-primary submit-btn" :class="{ disabled: !canSubmit }" @click="onSubmit">
      Submit Refund Request
    </view>
  </view>
</template>

<script>
import { orderApi } from '@/api'

export default {
  data() {
    return {
      orderId: null,
      order: null,
      form: {
        type: 'FULL',
        amount: '',
        reason: '',
        description: '',
      },
      reasonIndex: -1,
      reasonOptions: [
        'Item damaged',
        'Wrong item received',
        'Item not as described',
        'Quality issue',
        'No longer needed',
        'Other',
      ],
    }
  },

  computed: {
    canSubmit() {
      if (!this.form.reason) return false
      if (this.form.type === 'PARTIAL' && (!this.form.amount || parseFloat(this.form.amount) <= 0))
        return false
      return true
    },
  },

  onLoad(query) {
    this.orderId = query.orderId
    this.loadOrder()
  },

  methods: {
    async loadOrder() {
      try {
        this.order = await orderApi.getOrderDetail(this.orderId)
      } catch (e) {
        uni.showToast({ title: 'Failed to load order', icon: 'none' })
      }
    },

    onReasonChange(e) {
      this.reasonIndex = e.detail.value
      this.form.reason = this.reasonOptions[e.detail.value]
    },

    async onSubmit() {
      if (!this.canSubmit) return
      uni.showLoading({ title: 'Submitting...', mask: true })
      try {
        await orderApi.applyRefund({
          orderId: this.orderId,
          type: this.form.type,
          amount: this.form.type === 'FULL' ? null : parseFloat(this.form.amount),
          reason: this.form.reason,
          description: this.form.description,
        })
        uni.hideLoading()
        uni.showToast({ title: 'Refund submitted!', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1500)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || 'Submit failed', icon: 'none' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.refund-page {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 8rpx;
}

.order-amount {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.form-item {
  margin-bottom: 24rpx;
}

.label {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: 12rpx;
}

.radio-group {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.radio-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: var(--font-size-base);
}

.radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  color: #fff;
  flex-shrink: 0;
}

.radio.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.input {
  width: 100%;
  padding: 20rpx 16rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-base);
}

.picker {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 16rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.arrow {
  font-size: var(--font-size-lg);
  color: var(--color-text-tertiary);
}

.textarea {
  width: 100%;
  height: 160rpx;
  padding: 20rpx 16rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-base);
}

.submit-btn {
  padding: 28rpx 0;
  font-size: var(--font-size-md);
}

.submit-btn.disabled {
  opacity: 0.5;
}
</style>
