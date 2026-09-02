<template>
  <view class="recharge">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('recharge.title') }}</text>
      <view class="header-btn" />
    </view>

    <view class="content">
      <view class="balance-card">
        <view class="balance-bg" />
        <view class="balance-content">
          <text class="balance-label">{{ $t(`recharge.currentBalance`) }}</text>
          <text class="balance-amount">{{ currencySymbol }}{{ balanceAmount.toFixed(2) }}</text>
          <text class="balance-note">{{ $t(`recharge.balanceNote`) }}</text>
        </view>
      </view>

      <view class="section">
        <text class="section-title">{{ $t(`recharge.selectAmount`) }}</text>
        <view class="amount-grid">
          <view
            v-for="opt in amountOptions"
            :key="opt.value"
            class="amount-item"
            :class="{ selected: selectedAmount === opt.value, recommended: opt.recommended }"
            @click="onSelectAmount(opt.value)"
          >
            <text v-if="opt.recommended" class="recommend-badge">Recommended</text>
            <text class="amount-text">{{ opt.label }}</text>
          </view>
        </view>

        <view v-if="showCustomInput" class="custom-input-wrap">
          <view class="custom-input">
            <text class="currency-sign">{{ currencySymbol }}</text>
            <input
              v-model="customValue"
              class="custom-field"
              type="digit"
              placeholder="Enter amount"
              @input="onCustomInput"
            >
          </view>
        </view>
      </view>

      <view class="section">
        <text class="section-title">{{ $t('recharge.paymentMethods') }}</text>
        <view class="payment-list">
          <view
            v-for="method in paymentMethods"
            :key="method.value"
            class="payment-item"
            @click="selectedPayment = method.value"
          >
            <view class="payment-left">
              <view class="payment-icon">
                <text class="icon-text luc" :class="$luc(method.icon)" />
              </view>
              <view class="payment-info">
                <text class="payment-name">{{ method.name }}</text>
                <text v-if="method.hint" class="payment-hint">{{ method.hint }}</text>
              </view>
            </view>
            <view class="radio-dot" :class="{ selected: selectedPayment === method.value }">
              <view v-if="selectedPayment === method.value" class="radio-inner" />
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-bar">
      <view class="bottom-inner">
        <view class="recharge-btn" @click="onRecharge">
          <text class="btn-text">{{ $t('recharge.action', { amount: displayAmount }) }}</text>
        </view>
        <text class="terms-note">{{ $t('recharge.termsNote') }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { memberApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userBalanceRecharge',

  data() {
    return {
      selectedAmount: 50,
      customValue: '',
      showCustomInput: false,
      selectedPayment: 'applePay',
      balanceAmount: 0,
      localeVersion: 0,
      amountOptions: [
        { value: 10, label: '$10' },
        { value: 20, label: '$20' },
        { value: 50, label: '$50', recommended: true },
        { value: 100, label: '$100' },
        { value: 200, label: '$200' },
        { value: 'custom', label: '自定义' },
      ],
      paymentMethods: [
        { value: 'applePay', name: 'Apple Pay', icon: 'A', hint: '' },
        { value: 'creditCard', name: '信用卡/借记卡', icon: 'C', hint: 'Visa **** 4242' },
        { value: 'paypal', name: 'PayPal', icon: 'P', hint: '' },
      ],
    }
  },

  computed: {
    displayAmount() {
      if (this.selectedAmount === 'custom') {
        const val = parseFloat(this.customValue)
        if (!isNaN(val) && val > 0) {
          return this.currencySymbol + val.toFixed(2)
        }
        return this.currencySymbol + '0.00'
      }
      return this.currencySymbol + this.selectedAmount.toFixed(2)
    },
    /**
     * 当前语言货币符号:locale 切换时通过 localeVersion 触发重算
     */
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onLoad() {
    this.loadBalance()
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadBalance() {
      try {
        const res = await memberApi.getWallet()
        const data = res.data || res
        this.balanceAmount = data.balance || 0
      } catch (e) {
        // 静默失败，保持默认值
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onSelectAmount(value) {
      this.selectedAmount = value

      if (value === 'custom') {
        this.showCustomInput = true
        this.$nextTick(() => {
          this.customValue = ''
        })
      } else {
        this.showCustomInput = false
        this.customValue = ''
      }
    },

    onCustomInput(e) {
      this.selectedAmount = 'custom'
    },

    async onRecharge() {
      let amount = 0
      if (this.selectedAmount === 'custom') {
        amount = parseFloat(this.customValue)
        if (isNaN(amount) || amount <= 0) {
          uni.showToast({ title: i18n.t('recharge.invalidAmount'), icon: 'none' })
          return
        }
      } else {
        amount = this.selectedAmount
      }
      try {
        await memberApi.recharge(amount, this.selectedPayment)
        uni.showToast({
          title: `i18n.t("recharge.success", { amount: this.currencySymbol + amount.toFixed(2) })`,
          icon: 'success',
        })
        this.loadBalance()
      } catch (e) {
        uni.showToast({ title: 'Top-up failed, please retry', icon: 'none' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.recharge {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 200rpx;
}

.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.header-btn:last-child {
  left: auto;
  right: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.content {
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.balance-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-md);
}

.balance-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 40%, #0f3460 100%);
}

.balance-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 80% 20%, rgba(255, 255, 255, 0.08) 0%, transparent 60%),
    radial-gradient(ellipse at 20% 80%, rgba(0, 122, 255, 0.1) 0%, transparent 50%);
}

.balance-content {
  position: relative;
  z-index: 10;
  padding: 40rpx 36rpx;
}

.balance-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.7);
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.03em;
}

.balance-amount {
  font-size: 80rpx;
  font-weight: var(--font-weight-bold);
  color: #ffffff;
  margin-top: 12rpx;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.balance-note {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 16rpx;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.amount-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16rpx;
}

.amount-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 160rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  position: relative;
  transition: all 0.15s ease;

  &:active {
    transform: scale(0.97);
  }

  &.selected {
    background: #e8f2ff;
    border-color: var(--color-primary);
  }

  &.recommended {
    border-color: var(--color-divider);
  }

  &.selected.recommended {
    border-color: var(--color-primary);
  }
}

.recommend-badge {
  position: absolute;
  top: 8rpx;
  font-size: 18rpx;
  font-weight: var(--font-weight-semibold);
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  color: #ffffff;
  line-height: 1.4;
}

.amount-text {
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);

  .selected & {
    color: var(--color-primary);
  }
}

.custom-input-wrap {
  margin-top: 8rpx;
}

.custom-input {
  display: flex;
  align-items: center;
  gap: 12rpx;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.currency-sign {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-tertiary);
}

.custom-field {
  flex: 1;
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  background: transparent;
  border: none;
  outline: none;
}

.payment-list {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.payment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100rpx;
  padding: 0 28rpx;
  border-bottom: 1rpx solid var(--color-divider);

  &:last-child {
    border-bottom: none;
  }
}

.payment-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.payment-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: var(--radius-sm);
  background: var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  line-height: 1;
}

.payment-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.payment-name {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.payment-hint {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.radio-dot {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  border: 2rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s ease;

  &.selected {
    border-color: var(--color-primary);
    background: var(--color-primary);
  }
}

.radio-inner {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #ffffff;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 40;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  border-top: 1rpx solid var(--color-divider);
}

.bottom-inner {
  padding: 24rpx 32rpx;
  padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 24rpx);
}

.recharge-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  transition: opacity 0.15s ease;

  &:active {
    opacity: 0.9;
  }
}

.btn-text {
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
}

.terms-note {
  display: block;
  text-align: center;
  font-size: 20rpx;
  color: var(--color-text-tertiary);
  margin-top: 16rpx;
}
</style>
