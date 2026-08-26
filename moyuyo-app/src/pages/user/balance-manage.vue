<template>
  <view class="balance-manage">
    <view class="page-header">
      <view class="back" @click="goBack" aria-label="返回">‹</view>
      <text class="title">余额管理</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 余额卡片 -->
      <view class="balance-card">
        <text class="balance-label">账户余额</text>
        <view class="balance-value-wrap">
          <text class="balance-value">¥{{ showBalance ? balance : '****' }}</text>
          <view class="toggle" @click="showBalance = !showBalance" aria-label="显示/隐藏余额">
            {{ showBalance ? '🙈' : '👁' }}
          </view>
        </view>
        <view class="balance-actions">
          <view class="action-btn primary" @click="onRecharge">充值</view>
          <view class="action-btn" @click="onWithdraw">提现</view>
        </view>
      </view>

      <!-- 交易明细 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">交易明细</text>
          <view class="filter-tabs">
            <view
              v-for="t in filterTabs"
              :key="t.value"
              class="filter-tab"
              :class="{ active: activeFilter === t.value }"
              @click="activeFilter = t.value"
            >
              {{ t.label }}
            </view>
          </view>
        </view>
        <view v-if="filteredTx.length === 0" class="empty">暂无交易记录</view>
        <view v-else class="tx-list">
          <view v-for="tx in filteredTx" :key="tx.id" class="tx-item">
            <view class="tx-left">
              <text class="tx-name">{{ tx.desc }}</text>
              <text class="tx-time">{{ tx.time }}</text>
            </view>
            <text class="tx-amount" :class="tx.type">{{ tx.type === 'income' ? '+' : '-' }}¥{{ tx.amount }}</text>
          </view>
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
      showBalance: true,
      activeFilter: 'all',
      filterTabs: [
        { value: 'all', label: '全部' },
        { value: 'income', label: '收入' },
        { value: 'expense', label: '支出' },
      ],
      txList: [],
    }
  },

  computed: {
    filteredTx() {
      if (this.activeFilter === 'all') return this.txList
      return this.txList.filter((tx) => tx.type === this.activeFilter)
    },
  },

  onShow() {
    this.loadBalance()
    this.loadTransactions()
  },

  methods: {
    async loadBalance() {
      try {
        const info = await walletApi.getWalletInfo()
        this.balance = info.balance || 0
      } catch (e) {
        console.warn('[balance-manage] load failed', e)
      }
    },

    async loadTransactions() {
      try {
        const list = await walletApi.getTransactions()
        this.txList = Array.isArray(list) ? list : []
      } catch (e) {
        this.txList = [
          { id: 1, desc: '充值', time: '今天 10:00', amount: 100, type: 'income' },
          { id: 2, desc: '购物消费', time: '昨天 14:30', amount: 89, type: 'expense' },
          { id: 3, desc: '退款', time: '3天前', amount: 50, type: 'income' },
        ]
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onRecharge() {
      uni.navigateTo({ url: '/pages/user/wallet-recharge' })
    },

    onWithdraw() {
      uni.showToast({ title: '提现功能开发中', icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.balance-manage {
  min-height: 100vh;
  background: var(--color-background);
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
  padding: 32rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  margin-bottom: 24rpx;
}

.balance-label {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.balance-value-wrap {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 8rpx 0 24rpx;
}

.balance-value {
  font-size: 56rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
}

.toggle {
  font-size: 28rpx;
}

.balance-actions {
  display: flex;
  gap: 16rpx;
}

.action-btn {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-background);
  border: 1rpx solid var(--color-divider);
  border-radius: 999rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.action-btn.primary {
  background: var(--color-primary);
  color: var(--color-text);
  border-color: var(--color-primary);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.section-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.filter-tabs {
  display: flex;
  gap: 8rpx;
}

.filter-tab {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  background: var(--color-surface);
}

.filter-tab.active {
  background: var(--color-primary);
  color: var(--color-text);
}

.empty {
  text-align: center;
  padding: 48rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
  background: var(--color-surface);
  border: 1rpx dashed var(--color-divider);
  border-radius: var(--radius-md);
}

.tx-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.tx-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.tx-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.tx-name {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.tx-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.tx-amount {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.tx-amount.income {
  color: var(--color-success, #34c759);
}

.tx-amount.expense {
  color: var(--color-text);
}
</style>