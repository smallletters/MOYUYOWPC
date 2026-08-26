<template>
  <view class="wallet">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">我的钱包</text>
      <view class="header-btn" />
    </view>

    <view class="content">
      <!-- 余额卡片 -->
      <view class="balance-card">
        <view class="balance-bg" />
        <view class="balance-content">
          <!-- 标签行 -->
          <view class="balance-label-row">
            <text class="balance-label">账户余额</text>
            <view class="eye-btn" @click="toggleBalanceVisible">
              <text class="eye-icon luc" :class="$luc(balanceVisible  ?  'eye'  :  'eye-off')"></text>
            </view>
          </view>
          <!-- 金额 -->
          <view class="balance-amount-row">
            <text class="balance-amount">
              <text class="currency-sign">¥</text>
              <text v-if="balanceVisible" class="amount-num">{{ balanceFormatted }}</text>
              <text v-else class="amount-hidden">****</text>
            </text>
          </view>
          <!-- 操作按钮 -->
          <view class="balance-actions">
            <view class="btn-action btn-topup" @click="onTopup">充值</view>
            <view class="btn-action btn-withdraw" @click="onWithdraw">提现</view>
          </view>
        </view>
      </view>

      <!-- 钱包功能网格 -->
      <view class="features-grid">
        <view class="feature-item" @click="goPoints">
          <view class="feature-info">
            <text class="feature-label">积分</text>
            <text class="feature-value">{{ walletData.points.toLocaleString() }}</text>
          </view>
          <text class="feature-arrow"><text class="luc luc-chevron-right"></text></text>
        </view>
        <view class="feature-item" @click="goCoupons">
          <view class="feature-info">
            <text class="feature-label">优惠券</text>
            <text class="feature-value">{{ walletData.coupons }}张</text>
          </view>
          <text class="feature-arrow"><text class="luc luc-chevron-right"></text></text>
        </view>
        <view class="feature-item" @click="goGiftCards">
          <view class="feature-info">
            <text class="feature-label">礼品卡</text>
            <text class="feature-value">{{ walletData.giftCards }}张</text>
          </view>
          <text class="feature-arrow"><text class="luc luc-chevron-right"></text></text>
        </view>
        <view class="feature-item" @click="goYuEBao">
          <view class="feature-info">
            <text class="feature-label">余额宝</text>
            <text class="feature-value">¥{{ walletData.yuEBao.toFixed(2) }}</text>
          </view>
          <text class="feature-arrow"><text class="luc luc-chevron-right"></text></text>
        </view>
      </view>

      <!-- 交易记录 -->
      <view class="transactions">
        <view class="trans-header">
          <text class="trans-title">交易记录</text>
          <text class="trans-more" @click="viewAllTransactions">查看全部</text>
        </view>
        <view class="trans-list">
          <view v-for="tx in transactions" :key="tx.id" class="trans-item">
            <view class="trans-info">
              <text class="trans-name">{{ tx.title }}</text>
              <text class="trans-date">{{ tx.date }}</text>
            </view>
            <text class="trans-amount" :class="tx.amount > 0 ? 'amount-in' : 'amount-out'">
              {{ tx.amount > 0 ? '+' : '' }}¥{{ Math.abs(tx.amount).toFixed(2) }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { memberApi } from '@/api'

export default {
  data() {
    return {
      balanceVisible: true,
      currentBalance: 0,
      walletData: {
        balance: 326.5,
        points: 1280,
        coupons: 3,
        giftCards: 1,
        yuEBao: 0.0,
      },
      transactions: [
        { id: 1, title: '订单支付', date: '07-15', amount: -128.0 },
        { id: 2, title: '订单退款', date: '07-10', amount: 89.0 },
        { id: 3, title: '积分兑换', date: '07-05', amount: -200.0 },
        { id: 4, title: '充值', date: '07-01', amount: 500.0 },
        { id: 5, title: '订单支付', date: '06-28', amount: -167.0 },
      ],
    }
  },

  computed: {
    balanceFormatted() {
      return this.currentBalance.toFixed(2)
    },
  },

  onLoad() {
    this.loadWalletData()
  },

  methods: {
    async loadWalletData() {
      try {
        const res = await memberApi.getWallet()
        const data = res.data || res
        if (data) {
          this.currentBalance = data.balance || 0
          this.walletData = {
            balance: data.balance || 0,
            points: data.points || 0,
            coupons: data.coupons || 0,
            giftCards: data.giftCards || 0,
            yuEBao: data.yuEBao || 0,
          }
          if (data.transactions) {
            this.transactions = data.transactions
          }
        }
      } catch (e) {
        this.currentBalance = this.walletData.balance
      }
    },

    goBack() {
      uni.navigateBack()
    },

    toggleBalanceVisible() {
      this.balanceVisible = !this.balanceVisible
    },

    onTopup() {
      uni.showToast({ title: '充值功能', icon: 'none' })
    },

    onWithdraw() {
      uni.showToast({ title: '提现功能', icon: 'none' })
    },

    goPoints() {
      uni.navigateTo({ url: '/pages/user/points-shop' })
    },

    goCoupons() {
      uni.showToast({ title: '我的优惠券', icon: 'none' })
    },

    goGiftCards() {
      uni.showToast({ title: '我的礼品卡', icon: 'none' })
    },

    goYuEBao() {
      uni.showToast({ title: '余额宝', icon: 'none' })
    },

    viewAllTransactions() {
      uni.showToast({ title: '全部交易记录', icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.wallet {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
}

/* 顶部导航栏 */
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.header-btn:first-child {
  left: 16rpx;
}

.header-btn:last-child {
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
  padding: 32rpx 32rpx 64rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

/* 余额卡片 */
.balance-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-md);
  min-height: 280rpx;
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
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 36rpx 32rpx;
  min-height: 280rpx;
}

.balance-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.balance-label {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.7);
}

.eye-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: var(--radius-sm);
}

.eye-icon {
  font-size: 32rpx;
  line-height: 1;
}

.balance-amount-row {
  margin-top: 16rpx;
}

.balance-amount {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.currency-sign {
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
}

.amount-num {
  font-size: 72rpx;
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.amount-hidden {
  font-size: 48rpx;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 8rpx;
}

.balance-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 40rpx;
}

.btn-action {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  transition: all 0.15s ease;
}

.btn-action:active {
  transform: scale(0.98);
}

.btn-topup {
  background: #ffffff;
  color: var(--color-primary);
}

.btn-withdraw {
  background: transparent;
  color: #ffffff;
  border: 1rpx solid rgba(255, 255, 255, 0.4);
}

/* 钱包功能网格 */
.features-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.feature-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  transition: background 0.15s ease;
}

.feature-item:active {
  background: var(--color-divider);
}

.feature-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.feature-label {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.feature-value {
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.feature-arrow {
  font-size: 32rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

/* 交易记录 */
.transactions {
  display: flex;
  flex-direction: column;
}

.trans-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.trans-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.trans-more {
  font-size: 26rpx;
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.trans-list {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.trans-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.trans-item:last-child {
  border-bottom: none;
}

.trans-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.trans-name {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trans-date {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.trans-amount {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  margin-left: 24rpx;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.amount-in {
  color: #34c759;
}

.amount-out {
  color: var(--color-text-secondary);
}
</style>
