<template>
  <view class="page">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">{{ $t('transactions.title') }}</text>
      <view class="header-btn" />
    </view>

    <!-- 错误状态:优先展示,避免错误信息被列表或加载态吞掉 -->
    <view v-if="loadError" class="state-block">
      <empty-state
        type="error"
        icon="alert-triangle"
        :title="$t('transactions.loadFailed')"
        :desc="loadError"
        :btn-text="$t('common.retry')"
        btn-icon="refresh-cw"
        @action="loadTransactions"
      />
    </view>

    <!-- 加载中 -->
    <view v-else-if="loading && list.length === 0" class="state-block">
      <empty-state type="loading" :title="$t('transactions.loading')" />
    </view>

    <!-- 空状态 -->
    <view v-else-if="list.length === 0" class="state-block">
      <empty-state
        type="empty"
        icon="scroll-text"
        :title="$t('transactions.empty')"
        :desc="$t('transactions.emptyDesc')"
      />
    </view>

    <!-- 列表 -->
    <view v-else class="list">
      <view v-for="tx in list" :key="tx.id" class="tx-item">
        <view class="tx-info">
          <text class="tx-name">{{ tx.title || 'Account Activity' }}</text>
          <text class="tx-date">{{ formatDate(tx.date || tx.createdAt) }}</text>
        </view>
        <text class="tx-amount" :class="Number(tx.amount) >= 0 ? 'amount-in' : 'amount-out'">
          {{ Number(tx.amount) >= 0 ? '+' : '' }}{{ currencySymbol
          }}{{ formatAmount(Math.abs(Number(tx.amount) || 0)) }}
        </text>
      </view>
    </view>
  </view>
</template>

<script>
import { memberApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  data() {
    return {
      loading: false,
      loadError: '',
      list: [],
      localeVersion: 0,
    }
  },

  computed: {
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onLoad() {
    this.loadTransactions()
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadTransactions() {
      this.loading = true
      this.loadError = ''
      try {
        // showError: false 防止与本页 catch 中的 Toast 重复
        const data = await memberApi.getWallet({}, { showError: false })
        this.list = Array.isArray(data?.transactions) ? data.transactions : []
      } catch (e) {
        this.loadError = e?.message || i18n.t('wallet.loadErrorFallback')
      } finally {
        this.loading = false
      }
    },

    goBack() {
      uni.navigateBack()
    },

    /**
     * 金额千分位格式化(兼容后端返回 number 或 string)
     */
    formatAmount(n) {
      return Number(n).toLocaleString('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })
    },

    /**
     * 日期格式化:支持 ISO 字符串或 yyyy-MM-dd
     */
    formatDate(v) {
      if (!v) return ''
      const d = new Date(v)
      if (Number.isNaN(d.getTime())) return String(v)
      const pad = (x) => String(x).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    },
  },
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
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
}

/* 状态块:加载/空/错误均居中 */
.state-block {
  padding-top: 64rpx;
}

/* 列表:与 wallet.vue 交易记录同款卡片规格 */
.list {
  margin: 32rpx;
  background: #ffffff;
  border: 1rpx solid #f2f2f7;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 16rpx rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.tx-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.tx-item:last-child {
  border-bottom: none;
}

.tx-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.tx-name {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx-date {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.tx-amount {
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
