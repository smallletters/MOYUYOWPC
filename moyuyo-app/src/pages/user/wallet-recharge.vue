<template>
  <view class="wallet-recharge">
    <view class="page-header">
      <view class="back" @click="goBack" aria-label="返回钱包">‹</view>
      <text class="title">充值</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 余额信息 -->
      <view class="balance-card" aria-label="余额信息">
        <text class="balance-label">当前余额</text>
        <text class="balance-value">¥{{ balance }}</text>
      </view>

      <!-- 充值金额 -->
      <view class="section" aria-label="充值金额">
        <text class="section-title">选择充值金额</text>
        <view class="amount-grid">
          <view
            v-for="opt in amountOptions"
            :key="opt.value"
            class="amount-option"
            :class="{ selected: selectedAmount === opt.value }"
            :aria-label="opt.label"
            @click="onAmountSelect(opt)"
          >
            <text class="amount-num">¥{{ opt.value }}</text>
            <text v-if="opt.bonus" class="amount-bonus">送 ¥{{ opt.bonus }}</text>
          </view>
          <view
            class="amount-option"
            :class="{ selected: selectedAmount === 'custom' }"
            aria-label="自定义金额"
            @click="onAmountSelect({ value: 'custom' })"
          >
            <text class="amount-num">自定义</text>
          </view>
        </view>
        <input
          v-if="selectedAmount === 'custom'"
          v-model="customAmount"
          class="custom-input"
          type="number"
          placeholder="输入金额"
          aria-label="自定义充值金额"
        />
      </view>

      <!-- 支付方式 -->
      <view class="section" aria-label="支付方式">
        <text class="section-title">支付方式</text>
        <view class="pay-list">
          <view
            v-for="p in payMethods"
            :key="p.id"
            class="pay-item"
            :class="{ selected: selectedPay === p.id }"
            @click="selectedPay = p.id"
          >
            <text class="pay-icon">{{ p.icon }}</text>
            <text class="pay-name">{{ p.name }}</text>
            <view class="pay-radio" :class="{ checked: selectedPay === p.id }"></view>
          </view>
        </view>
      </view>

      <view class="bottom-bar safe-area-bottom">
        <view class="btn-primary" @click="onRecharge" aria-label="确认充值">
          确认充值 ¥{{ finalAmount }}
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { walletApi } from '@/api'

export default {
  data() {
    return {
      balance: 0,
      amountOptions: [
        { value: 10, label: '充值 ¥10', bonus: 0 },
        { value: 20, label: '充值 ¥20', bonus: 0 },
        { value: 50, label: '充值 ¥50', bonus: 5 },
        { value: 100, label: '充值 ¥100', bonus: 12 },
        { value: 200, label: '充值 ¥200', bonus: 30 },
      ],
      selectedAmount: 50,
      customAmount: '',
      payMethods: [
        { id: 'wechat', name: '微信支付', icon: '💚' },
        { id: 'alipay', name: '支付宝', icon: '💙' },
        { id: 'card', name: '银行卡', icon: '💳' },
      ],
      selectedPay: 'wechat',
    }
  },

  computed: {
    finalAmount() {
      if (this.selectedAmount === 'custom') {
        return Number(this.customAmount) || 0
      }
      return Number(this.selectedAmount) || 0
    },
  },

  onShow() {
    this.loadBalance()
  },

  methods: {
    async loadBalance() {
      try {
        const info = await walletApi.getWalletInfo()
        this.balance = info.balance || 0
      } catch (e) {
        console.warn('[wallet-recharge] load failed', e)
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onAmountSelect(opt) {
      this.selectedAmount = opt.value
    },

    onRecharge() {
      const amt = this.finalAmount
      if (amt <= 0) {
        uni.showToast({ title: '请选择充值金额', icon: 'none' })
        return
      }
      uni.showLoading({ title: '跳转支付中...' })
      setTimeout(() => {
        uni.hideLoading()
        uni.showToast({ title: '充值成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      }, 1000)
    },
  },
}
</script>

<style lang="scss" scoped>
.wallet-recharge {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 120rpx;
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

.balance-card {
  padding: 40rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  text-align: center;
}

.balance-label {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: 8rpx;
}

.balance-value {
  display: block;
  font-size: 56rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
}

.section {
  margin-top: 32rpx;
}

.section-title {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.amount-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.amount-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 160rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  gap: 4rpx;
}

.amount-option.selected {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}

.amount-num {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.amount-bonus {
  font-size: var(--font-size-xs);
  color: var(--color-primary);
}

.custom-input {
  margin-top: 16rpx;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  font-size: var(--font-size-base);
}

.pay-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.pay-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.pay-item.selected {
  border-color: var(--color-primary);
}

.pay-icon {
  font-size: 36rpx;
}

.pay-name {
  flex: 1;
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.pay-radio {
  width: 32rpx;
  height: 32rpx;
  border: 2rpx solid var(--color-divider);
  border-radius: 50%;
}

.pay-radio.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
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
  border-radius: var(--radius-pill);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}
</style>