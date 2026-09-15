<template>
  <view class="balance-manage">
    <view class="page-header">
      <view class="back" :aria-label="$t('balanceManage.backLabel')" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">{{ $t('balanceManage.title') }}</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 余额卡片 -->
      <view class="balance-card">
        <text class="balance-label">{{ $t('balanceManage.accountBalance') }}</text>
        <view class="balance-value-wrap">
          <text class="balance-value">${{ showBalance ? balance : '****' }}</text>
          <view
            class="toggle luc"
            :aria-label="$t('balanceManage.toggleBalanceLabel')"
            :class="$luc(showBalance ? 'eye-off' : 'eye')"
            @click="showBalance = !showBalance"
          />
        </view>
        <view class="balance-actions">
          <view class="action-btn primary" @click="onRecharge">
            {{ $t('balanceManage.recharge') }}
          </view>
          <view class="action-btn" @click="onWithdraw">{{ $t('balanceManage.withdraw') }}</view>
        </view>
      </view>

      <!-- 交易明细 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">{{ $t('balanceManage.txTitle') }}</text>
          <view class="filter-tabs">
            <view
              v-for="t in filterTabs"
              :key="t.value"
              class="filter-tab"
              :class="{ active: activeFilter === t.value }"
              @click="activeFilter = t.value"
            >
              {{ $t(t.labelKey) }}
            </view>
          </view>
        </view>
        <view v-if="filteredTx.length === 0" class="empty">{{ $t('balanceManage.empty') }}</view>
        <view v-else class="tx-list">
          <view v-for="tx in filteredTx" :key="tx.id" class="tx-item">
            <view class="tx-left">
              <text class="tx-name">{{ tx.desc }}</text>
              <text class="tx-time">{{ tx.time }}</text>
            </view>
            <text class="tx-amount" :class="tx.type">
              {{ tx.type === 'income' ? '+' : '-' }}${{ tx.amount }}
            </text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { walletApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  data() {
    return {
      balance: 0,
      showBalance: true,
      activeFilter: 'all',
      filterTabs: [
        { value: 'all', labelKey: 'balanceManage.filters.all' },
        { value: 'income', labelKey: 'balanceManage.filters.income' },
        { value: 'expense', labelKey: 'balanceManage.filters.expense' },
      ],
      txList: [],
      localeVersion: 0,
    }
  },

  computed: {
    filteredTx() {
      void this.localeVersion
      const list =
        this.activeFilter === 'all'
          ? this.txList
          : this.txList.filter((tx) => tx.type === this.activeFilter)
      // 兜底演示数据存 i18n key，展示时按当前语言映射
      return list.map((tx) => ({
        ...tx,
        desc: tx.descKey ? i18n.t(tx.descKey) : tx.desc,
        time: tx.timeKey ? i18n.t(tx.timeKey, tx.timeParams) : tx.time,
      }))
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
          {
            id: 1,
            descKey: 'balanceManage.tx.topup',
            timeKey: 'balanceManage.tx.todayAt',
            timeParams: { time: '10:00' },
            amount: 100,
            type: 'income',
          },
          {
            id: 2,
            descKey: 'balanceManage.tx.shopping',
            timeKey: 'balanceManage.tx.yesterdayAt',
            timeParams: { time: '14:30' },
            amount: 89,
            type: 'expense',
          },
          {
            id: 3,
            descKey: 'balanceManage.tx.refund',
            timeKey: 'balanceManage.tx.daysAgo',
            timeParams: { count: 3 },
            amount: 50,
            type: 'income',
          },
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
      uni.showToast({ title: i18n.t('balanceManage.withdrawInProgress'), icon: 'none' })
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
