<template>
  <view class="wallet">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">{{ $t('wallet.title') }}</text>
      <view class="header-btn" />
    </view>

    <!-- 加载中:首次进入时展示骨架 -->
    <view v-if="loading && !loadedOnce" class="loading-mask">
      <view class="loading-card">
        <text class="loading-text">{{ $t('wallet.loading') }}</text>
      </view>
    </view>

    <!-- 错误态:请求失败时提供重试入口 -->
    <view v-else-if="loadError || (!loading && !loadedOnce)" class="error-state">
      <empty-state
        type="error"
        icon="alert-triangle"
        :title="$t('wallet.loadingFailed')"
        :desc="loadError || $t('wallet.loadErrorFallback')"
        :btn-text="$t('common.retry')"
        btn-icon="refresh-cw"
        @action="loadWalletData"
      />
    </view>

    <view v-else class="content">
      <!-- 余额卡片 -->
      <view class="balance-card">
        <view class="balance-bg" />
        <view class="balance-content">
          <!-- 标签行 -->
          <view class="balance-label-row">
            <text class="balance-label">{{ $t('wallet.balance') }}</text>
            <view class="eye-btn" @click="toggleBalanceVisible">
              <text class="eye-icon luc" :class="$luc(balanceVisible ? 'eye' : 'eye-off')" />
            </view>
          </view>
          <!-- 金额(隐藏态用 8 个圆点,符合主流钱包视觉规范) -->
          <view class="balance-amount-row">
            <text class="balance-amount">
              <text class="currency-sign">{{ currencySymbol }}</text>
              <text v-if="balanceVisible" class="amount-num">{{ balanceFormatted }}</text>
              <text v-else class="amount-hidden">••••••••</text>
            </text>
          </view>
          <!-- 操作按钮(防重复点击:点击后短暂置灰) -->
          <view class="balance-actions">
            <view class="btn-action btn-topup" :class="{ disabled: actionLock }" @click="onTopup">
              {{ $t('wallet.buttons.topup') }}
            </view>
            <view
              class="btn-action btn-withdraw"
              :class="{ disabled: actionLock }"
              @click="onWithdraw"
            >
              {{ $t('wallet.buttons.withdraw') }}
            </view>
          </view>
        </view>
      </view>

      <!-- 钱包功能网格 -->
      <view class="features-grid">
        <view class="feature-item" @click="goPoints">
          <view class="feature-info">
            <text class="feature-label">{{ $t('wallet.features.points') }}</text>
            <text class="feature-value">{{ walletData.points.toLocaleString() }}</text>
          </view>
          <text class="feature-arrow"><text class="luc luc-chevron-right" /></text>
        </view>
        <view class="feature-item" @click="goCoupons">
          <view class="feature-info">
            <text class="feature-label">{{ $t('wallet.features.coupons') }}</text>
            <text class="feature-value">{{ walletData.coupons }}</text>
          </view>
          <text class="feature-arrow"><text class="luc luc-chevron-right" /></text>
        </view>
        <view class="feature-item" @click="goGiftCards">
          <view class="feature-info">
            <text class="feature-label">{{ $t('wallet.features.giftCards') }}</text>
            <text class="feature-value">{{ walletData.giftCards }}</text>
          </view>
          <text class="feature-arrow"><text class="luc luc-chevron-right" /></text>
        </view>
        <view class="feature-item" @click="viewAllTransactions">
          <view class="feature-info">
            <text class="feature-label">{{ $t('wallet.features.transactions') }}</text>
            <text class="feature-value">{{ $t('wallet.features.viewAll') }}</text>
          </view>
          <text class="feature-arrow"><text class="luc luc-chevron-right" /></text>
        </view>
      </view>

      <!-- 交易记录 -->
      <view class="transactions">
        <view class="trans-header">
          <text class="trans-title">{{ $t('wallet.transTitle') }}</text>
          <text class="trans-more" @click="viewAllTransactions">
            {{ $t('wallet.features.viewAll') }}
          </text>
        </view>
        <!-- 交易列表为空时展示空态,避免空白卡片给用户'加载失败'错觉 -->
        <view v-if="transactions.length === 0" class="trans-empty">
          <empty-state
            type="empty"
            icon="scroll-text"
            :title="$t('wallet.transEmpty')"
            :desc="$t('wallet.transEmptyDesc')"
          />
        </view>
        <view v-else class="trans-list">
          <view v-for="tx in transactions" :key="tx.id" class="trans-item">
            <view class="trans-info">
              <text class="trans-name">{{ tx.title }}</text>
              <text class="trans-date">{{ formatDate(tx.date || tx.createdAt) }}</text>
            </view>
            <text class="trans-amount" :class="tx.amount > 0 ? 'amount-in' : 'amount-out'">
              {{ tx.amount > 0 ? '+' : '' }}{{ currencySymbol }}{{ Math.abs(tx.amount).toFixed(2) }}
            </text>
          </view>
        </view>
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
      balanceVisible: true,
      currentBalance: 0,
      loading: false,
      // 首次加载是否成功:用于控制空/错误态仅在首次展示,避免下拉刷新时整个页面被替换
      loadedOnce: false,
      loadError: '',
      walletData: {
        balance: 0,
        points: 0,
        coupons: 0,
        giftCards: 0,
      },
      // 充值/提现按钮防重复点击锁:点击后 800ms 内禁用
      actionLock: false,
      transactions: [],
      // locale 版本号:locale 切换时自增,触发 computed 重算
      localeVersion: 0,
    }
  },

  computed: {
    balanceFormatted() {
      // 千分位格式化:与 feature-value(积分)保持一致,符合主流钱包视觉规范
      const n = Number(this.currentBalance) || 0
      return n.toLocaleString('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })
    },
    /**
     * 当前语言货币符号:locale 切换时通过 localeVersion 触发重算
     */
    currencySymbol() {
      // 读取 this.localeVersion 让 Vue 追踪依赖,触发 reactive
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onLoad() {
    this.loadWalletData()
    // 订阅 locale 变化:locale 切换时让 currencySymbol 重新计算
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  // 下拉刷新:重新拉取钱包数据,提升金额敏感页面的数据时效性
  onPullDownRefresh() {
    this.loadWalletData().finally(() => {
      uni.stopPullDownRefresh()
    })
  },

  methods: {
    async loadWalletData() {
      this.loading = true
      this.loadError = ''
      try {
        // request.js 已解包外层 envelope,直接拿 payload 即可
        // showError: false 防止 request.js 默认 Toast 与 catch 中 Toast 重复弹出
        const data = await memberApi.getWallet({}, { showError: false })
        if (data) {
          this.currentBalance = data.balance || 0
          this.walletData = {
            balance: data.balance || 0,
            points: data.points || 0,
            coupons: data.coupons || 0,
            giftCards: data.giftCards || 0,
          }
          this.transactions = Array.isArray(data.transactions) ? data.transactions : []
          // 加载成功后标记首次成功,之后下拉刷新不会再触发整页替换
          this.loadedOnce = true
        }
      } catch (e) {
        // 失败时不回退硬编码假数据,展示空状态,避免误导用户
        this.loadError = e?.message || i18n.t('wallet.loadErrorFallback')
        this.currentBalance = 0
        this.walletData = { balance: 0, points: 0, coupons: 0, giftCards: 0 }
        this.transactions = []
      } finally {
        this.loading = false
      }
    },

    goBack() {
      uni.navigateBack()
    },

    toggleBalanceVisible() {
      this.balanceVisible = !this.balanceVisible
    },

    onTopup() {
      // 防重复点击:点击后 800ms 内不再响应
      // 用 try/finally 保证 navigateTo 抛异常时锁也能解锁,避免按钮永久置灰
      if (this.actionLock) return
      this.actionLock = true
      try {
        uni.navigateTo({ url: '/pages/user/wallet-recharge' })
      } finally {
        setTimeout(() => {
          this.actionLock = false
        }, 800)
      }
    },

    onWithdraw() {
      // 提现走独立页面,与充值严格分开,避免参数路由误用造成资金风险
      // 用 try/finally 保证锁必定释放
      if (this.actionLock) return
      this.actionLock = true
      try {
        uni.navigateTo({ url: '/pages/user/balance-withdraw' })
      } finally {
        setTimeout(() => {
          this.actionLock = false
        }, 800)
      }
    },

    goPoints() {
      uni.navigateTo({ url: '/pages/user/points-shop' })
    },

    goCoupons() {
      uni.navigateTo({ url: '/pages/user/coupons' })
    },

    goGiftCards() {
      uni.navigateTo({ url: '/pages/user/gift-cards' })
    },

    viewAllTransactions() {
      // 钱包交易记录走专用页,与积分明细分开,避免语义混乱
      uni.navigateTo({ url: '/pages/user/wallet-transactions' })
    },

    /**
     * 交易日期格式化:支持 ISO 字符串或 yyyy-MM-dd HH:mm
     * 与 wallet-transactions.vue 保持一致的输出格式
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

/* 首次加载中的骨架占位 */
.loading-mask {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 88rpx);
  padding: 64rpx 32rpx;
}

.loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
}

.loading-text {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}

/* 错误态容器(让 empty-state 居中占满) */
.error-state {
  min-height: calc(100vh - 88rpx);
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 余额卡片(品牌主色渐变 + 统一阴影,与任务中心进度卡同款卡片规格) */
.balance-card {
  position: relative;
  overflow: hidden;
  border-radius: 24rpx;
  min-height: 280rpx;
  box-shadow: 0 8rpx 32rpx rgba(219, 201, 138, 0.35);
}

.balance-bg {
  position: absolute;
  inset: 0;
  /* 改用品牌主色 Sand Gold 渐变,与全站一致 */
  background: linear-gradient(135deg, #e8ddb5 0%, #dbc98a 50%, #c9b47a 100%);
}

.balance-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 80% 20%, rgba(255, 255, 255, 0.25) 0%, transparent 60%),
    radial-gradient(ellipse at 20% 80%, rgba(184, 166, 107, 0.25) 0%, transparent 50%);
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

/* 防重复点击锁态:灰化 + 不可点击 */
.btn-action.disabled {
  opacity: 0.45;
  pointer-events: none;
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

/* 磨砂白卡片(与任务中心 mission-item 同款卡片规格) */
.feature-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 24rpx;
  background: #ffffff;
  border: 1rpx solid #f2f2f7;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 16rpx rgba(0, 0, 0, 0.04);
  transition: all 0.18s ease;
}

.feature-item:active {
  background: #fafafa;
  transform: scale(0.985);
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
  /* 改用品牌主色深一档 + font-weight 600 提升对比度,通过 WCAG AA */
  color: #8a7a4a;
  font-weight: 600;
}

/* 交易列表(同款磨砂白卡片) */
.trans-list {
  background: #ffffff;
  border: 1rpx solid #f2f2f7;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 16rpx rgba(0, 0, 0, 0.04);
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

/* 交易记录空态容器:让 empty-state 不占满整屏,贴合卡片宽度 */
.trans-empty {
  padding: 24rpx 0 8rpx;
}
</style>
