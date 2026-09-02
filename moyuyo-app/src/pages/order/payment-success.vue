<template>
  <view class="payment-success">
    <view class="success-icon-wrap">
      <view class="success-icon">
        <text class="check-mark luc-check" />
      </view>
    </view>

    <text class="title">支付成功</text>

    <text class="order-no">订单号: MOY20260715001</text>

    <view class="amount-section">
      <text class="amount-label">支付金额</text>
      <text class="amount-value">$197.00</text>
    </view>

    <view class="order-summary">
      <text class="summary-text">经典尼龙宠物牵引套装 x1 + 温和植物萃取宠物洗护套装 x1</text>
    </view>

    <view class="actions">
      <view class="btn btn-outline" @click="onViewOrder">{{ $t('paymentSuccess.viewOrder') }}</view>
      <view class="btn btn-primary" @click="onContinueShopping">
        {{ $t('paymentSuccess.continueShopping') }}
      </view>
    </view>

    <view class="footer">
      <view class="share-btn" @click="onShare">
        <text class="share-icon">⤴</text>
        <text>{{ $t('paymentSuccess.shareFriend') }}</text>
      </view>
      <view class="points-badge">
        <text class="gift-icon luc-gift" />
        <text>{{ $t('paymentSuccess.earnedPoints', { count: 50 }) }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.orderPaymentSuccess',

  data() {
    return {
      orderNo: 'MOY20260715001',
      payAmount: '197.00',
      localeVersion: 0,
    }
  },

  onLoad(query) {
    if (query.orderNo) this.orderNo = query.orderNo
    if (query.amount) this.payAmount = query.amount
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    onViewOrder() {
      uni.navigateTo({ url: '/pages/order/detail?id=1' })
    },

    onContinueShopping() {
      uni.switchTab({ url: '/pages/tabbar/home' })
    },

    onShare() {
      uni.showToast({ title: i18n.t('paymentSuccess.shareDeveloping'), icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.payment-success {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 48rpx 64rpx;
  background: var(--color-background);
}

.success-icon-wrap {
  position: relative;
  width: 300rpx;
  height: 300rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 48rpx;
  animation: fadeInUp 0.6s ease-out;
}

.success-icon {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: var(--color-success, #34c759);
  display: flex;
  align-items: center;
  justify-content: center;
  position: absolute;
  bottom: 0;
  box-shadow: 0 8rpx 24rpx rgba(52, 199, 89, 0.3);
  animation: scaleBounce 0.5s 0.3s ease-out both;
}

.check-mark {
  font-size: 56rpx;
  color: #ffffff;
  font-weight: 700;
}

.title {
  font-size: 40rpx;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.order-no {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
  margin-bottom: 48rpx;
}

.amount-section {
  text-align: center;
  margin-bottom: 48rpx;
}

.amount-label {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  display: block;
  margin-bottom: 8rpx;
}

.amount-value {
  font-size: 48rpx;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--color-text);
}

.order-summary {
  width: 100%;
  max-width: 600rpx;
  padding: 32rpx;
  background: var(--color-surface, var(--color-background));
  border-radius: 20rpx;
  border: 1rpx solid var(--color-divider);
  margin-bottom: 64rpx;
}

.summary-text {
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.actions {
  width: 100%;
  max-width: 600rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.btn {
  height: 88rpx;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
  transition: opacity 0.2s ease;
}

.btn:active {
  opacity: 0.8;
  transform: scale(0.98);
}

.btn-outline {
  border: 3rpx solid var(--color-primary);
  color: var(--color-primary);
  background: transparent;
}

.btn-primary {
  background: var(--color-primary);
  color: #ffffff;
}

.footer {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 64rpx;
}

.share-btn {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.share-icon {
  font-size: 32rpx;
}

.points-badge {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  border-radius: 999px;
  background: var(--color-primary-light, #e8f2ff);
  color: var(--color-primary);
  font-size: 24rpx;
  font-weight: 500;
}

.gift-icon {
  font-size: 28rpx;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(32rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes scaleBounce {
  0% {
    opacity: 0;
    transform: scale(0.3);
  }
  50% {
    transform: scale(1.1);
  }
  70% {
    transform: scale(0.95);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
