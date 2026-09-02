<template>
  <view class="refund-page">
    <view v-if="order" class="card order-info">
      <text class="card-title">Order #{{ order.orderNo }}</text>
      <text class="order-amount">
        {{ $t('orderRefund.amountLabel') }}: {{ currencySymbol }}{{ order.payAmount }}
      </text>
    </view>

    <view class="card form-card">
      <view class="form-item">
        <text class="label">{{ $t('orderRefund.typeLabel') }}</text>
        <view class="radio-group">
          <view class="radio-row" @click="form.type = 'FULL'">
            <view class="radio" :class="{ checked: form.type === 'FULL' }">
              <text v-if="form.type === 'FULL'"><text class="luc luc-check" /></text>
            </view>
            <text>{{ $t('orderRefund.fullRefund') }}</text>
          </view>
          <view class="radio-row" @click="form.type = 'PARTIAL'">
            <view class="radio" :class="{ checked: form.type === 'PARTIAL' }">
              <text v-if="form.type === 'PARTIAL'"><text class="luc luc-check" /></text>
            </view>
            <text>{{ $t('orderRefund.partialRefund') }}</text>
          </view>
        </view>
      </view>

      <view v-if="form.type === 'PARTIAL'" class="form-item">
        <text class="label">{{ $t('orderRefund.partialAmountLabel') }}</text>
        <input
          v-model="form.amount"
          class="input"
          type="digit"
          :placeholder="$t('orderRefund.amountPlaceholder')"
        >
      </view>

      <view class="form-item">
        <text class="label">{{ $t('orderRefund.reasonLabel') }}</text>
        <picker :value="reasonIndex" :range="reasonOptions" @change="onReasonChange">
          <view class="picker">
            <text>{{ reasonOptions[reasonIndex] || $t('orderRefund.reasonPlaceholder') }}</text>
            <text class="arrow luc-chevron-right" />
          </view>
        </picker>
      </view>

      <view class="form-item">
        <text class="label">{{ $t('orderRefund.descLabel') }}</text>
        <textarea
          v-model="form.description"
          class="textarea"
          :placeholder="$t('orderRefund.descPlaceholder')"
        />
      </view>
    </view>

    <view class="btn btn-primary submit-btn" :class="{ disabled: !canSubmit }" @click="onSubmit">
      {{ $t('orderRefund.submit') }}
    </view>
  </view>
</template>

<script>
import { orderApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.orderRefund',

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
      reasonOptions: [],
      localeVersion: 0,
    }
  },

  computed: {
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onLoad(query) {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    this.reasonOptions = i18n.t('orderRefund.reasonOptions')
    this.orderId = query.orderId
    this.loadOrder()
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadOrder() {
      try {
        this.order = await orderApi.getOrderDetail(this.orderId)
      } catch (e) {
        uni.showToast({ title: i18n.t('orderRefund.loadFailed'), icon: 'none' })
      }
    },

    onReasonChange(e) {
      this.reasonIndex = e.detail.value
      this.form.reason = this.reasonOptions[e.detail.value]
    },

    async onSubmit() {
      if (!this.canSubmit) return
      uni.showLoading({ title: i18n.t('common.loading'), mask: true })
      try {
        await orderApi.applyRefund({
          orderId: this.orderId,
          type: this.form.type,
          amount: this.form.type === 'FULL' ? null : parseFloat(this.form.amount),
          reason: this.form.reason,
          description: this.form.description,
        })
        uni.hideLoading()
        uni.showToast({ title: i18n.t('orderRefund.submitted'), icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1500)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || i18n.t('orderRefund.failed'), icon: 'none' })
      }
    },

    canSubmit() {
      if (!this.form.reason) return false
      if (this.form.type === 'PARTIAL' && (!this.form.amount || parseFloat(this.form.amount) <= 0))
        return false
      return true
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
