<template>
  <view class="gift-cards">
    <view class="content">
      <!-- 顶部汇总卡片 -->
      <view class="summary-card">
        <view class="summary-card-bg" />
        <view class="summary-card-content">
          <view class="summary-card-body">
            <view class="summary-item">
              <text class="summary-label">{{ $t('giftCards.available') }}</text>
              <text class="summary-value">{{ summary.count }} {{ $t('coupons.unit') }}</text>
            </view>
            <view class="summary-divider" />
            <view class="summary-item">
              <text class="summary-label">{{ $t('giftCards.balance') }}</text>
              <text class="summary-value">{{ currencySymbol }}{{ summary.totalBalance }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- Tab 切换 -->
      <view class="tab-bar">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-btn"
          :class="{ active: activeTab === tab.key }"
          @click="onTabChange(tab.key)"
        >
          <text>{{ tab.label }}</text>
        </view>
      </view>

      <!-- 礼品卡列表 -->
      <view v-if="filteredCards.length > 0" class="card-list">
        <view
          v-for="card in filteredCards"
          :key="card.id"
          class="gift-card-item"
          :style="{
            background: card.active ? card.gradient : 'var(--background-200)',
            opacity: card.active ? 1 : 0.6,
          }"
        >
          <view class="card-decoration" />
          <view class="card-body">
            <view class="card-top">
              <text
                class="card-brand"
                :style="{ color: card.active ? 'rgba(255,255,255,0.9)' : 'var(--text-400)' }"
              >
                MOYUYO
              </text>
              <view
                class="card-chip"
                :style="card.active ? {} : { borderColor: 'var(--background-400)' }"
              />
            </view>
            <text
              class="card-amount"
              :style="{ color: card.active ? '#ffffff' : 'var(--text-400)' }"
            >
              {{ currencySymbol }}{{ card.faceValue }}
            </text>
            <view class="card-footer">
              <view>
                <text
                  class="card-number"
                  :style="{ color: card.active ? 'rgba(255,255,255,0.5)' : 'var(--text-400)' }"
                >
                  **** **** **** {{ card.lastFour }}
                </text>
                <text
                  class="card-expiry"
                  :style="{ color: card.active ? 'rgba(255,255,255,0.5)' : 'var(--text-400)' }"
                >
                  {{ $t('giftCards.expiresAt', { date: card.expiry }) }}
                </text>
              </view>
              <view class="card-balance">
                <text
                  class="balance-label"
                  :style="{ color: card.active ? 'rgba(255,255,255,0.5)' : 'var(--text-400)' }"
                >
                  {{ $t('giftCards.cardBalance') }}
                </text>
                <text
                  class="balance-value"
                  :style="{ color: card.active ? '#ffffff' : 'var(--text-400)' }"
                >
                  {{ currencySymbol }}{{ card.balance }}
                </text>
              </view>
            </view>
          </view>
          <view v-if="card.active && card.balance < card.faceValue" class="card-used-overlay" />
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else class="empty-state">
        <text class="empty-icon luc-inbox" />
        <text class="empty-title">{{ emptyText }}</text>
      </view>

      <!-- 底部操作按钮 -->
      <view class="bottom-actions">
        <view class="action-btn action-btn--outline" @click="onBindCard">
          <text class="action-icon luc-link" />
          <text>{{ $t('giftCards.bindNew') }}</text>
        </view>
        <view class="action-btn action-btn--primary" @click="onBuyCard">
          <text class="action-icon luc-shopping-bag" />
          <text>{{ $t('giftCards.buyNew') }}</text>
        </view>
      </view>
    </view>

    <!-- 绑定礼品卡弹窗 -->
    <view class="modal-overlay" :class="{ active: showBindModal }" @click="onCloseBindModal">
      <view class="modal-panel" @click.stop>
        <view class="modal-handle" />
        <text class="modal-title">{{ $t('giftCards.bindModalTitle') }}</text>
        <text class="modal-desc">{{ $t('giftCards.bindModalDesc') }}</text>

        <view class="form-group">
          <text class="form-label">{{ $t('giftCards.cardNoLabel') }}</text>
          <view class="form-field">
            <input
              v-model="bindForm.cardNo"
              class="form-input"
              type="text"
              :placeholder="$t('giftCards.cardNoPlaceholder')"
              maxlength="16"
            >
          </view>
        </view>

        <view class="form-group">
          <text class="form-label">{{ $t('giftCards.pinLabel') }}</text>
          <view class="form-field-row">
            <view class="form-field form-field--flex">
              <input
                v-model="bindForm.code"
                class="form-input"
                type="text"
                :placeholder="$t('giftCards.pinPlaceholder')"
                maxlength="6"
              >
            </view>
            <view class="code-btn" @click="onGetCode">
              <text class="code-btn-text">{{ codeBtnText }}</text>
            </view>
          </view>
        </view>

        <view class="modal-actions">
          <view class="modal-btn modal-btn--cancel" @click="onCloseBindModal">
            {{ $t('common.cancel') }}
          </view>
          <view class="modal-btn modal-btn--confirm" @click="onConfirmBind">
            {{ $t('giftCards.confirmBind') }}
          </view>
        </view>
      </view>
    </view>

    <!-- 交易记录弹窗 -->
    <view class="modal-overlay" :class="{ active: showTxModal }" @click="onCloseTxModal">
      <view class="modal-panel" @click.stop>
        <view class="modal-handle" />
        <text class="modal-title">{{ $t('giftCards.txTitle') }}</text>
        <view class="tx-list">
          <view v-for="tx in cardTransactions" :key="tx.id" class="tx-item">
            <view class="tx-info">
              <text class="tx-type">{{ tx.type }}</text>
              <text class="tx-date">{{ tx.date }}</text>
            </view>
            <text class="tx-amount" :class="tx.amount > 0 ? 'tx-positive' : 'tx-negative'">
              {{ tx.amount > 0 ? '+' : '' }}{{ currencySymbol }}{{ Math.abs(tx.amount) }}
            </text>
          </view>
        </view>
        <view class="modal-actions">
          <view class="modal-btn modal-btn--cancel" @click="onCloseTxModal">
            {{ $t('common.confirm') }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userGiftCards',

  data() {
    return {
      activeTab: 'available',
      showBindModal: false,
      showTxModal: false,
      cardTransactions: [],
      bindForm: { cardNo: '', code: '' },
      codeCountdown: 0,
      codeTimer: null,
      summary: { count: 2, totalBalance: '450.00' },
      // tabs 改为 computed
      cards: [],
      localeVersion: 0,
    }
  },

  computed: {
    tabs() {
      void this.localeVersion
      return [
        { key: 'available', label: i18n.t('giftCards.tabs.available') },
        { key: 'used', label: i18n.t('giftCards.tabs.used') },
        { key: 'expired', label: i18n.t('giftCards.tabs.expired') },
      ]
    },
    filteredCards() {
      return this.cards.filter((c) => c.status === this.activeTab)
    },
    emptyText() {
      void this.localeVersion
      return i18n.t(`giftCards.empty.${this.activeTab}`)
    },
    codeBtnText() {
      return this.codeCountdown > 0 ? `${this.codeCountdown}s` : i18n.t('giftCards.getCode')
    },
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onLoad() {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    this.loadCards()
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
    if (this.codeTimer) clearInterval(this.codeTimer)
  },

  methods: {
    loadCards() {
      // mock 数据(实际项目接 API)
      this.cards = [
        {
          id: 1,
          status: 'available',
          active: true,
          faceValue: '300.00',
          balance: '280.00',
          lastFour: '1234',
          expiry: '2027-12-31',
          gradient: 'linear-gradient(135deg, #667eea, #764ba2)',
        },
        {
          id: 2,
          status: 'available',
          active: true,
          faceValue: '200.00',
          balance: '170.00',
          lastFour: '5678',
          expiry: '2027-06-30',
          gradient: 'linear-gradient(135deg, #f093fb, #f5576c)',
        },
      ]
    },

    onTabChange(key) {
      this.activeTab = key
    },

    onBindCard() {
      this.showBindModal = true
    },

    onCloseBindModal() {
      this.showBindModal = false
    },

    onBuyCard() {
      uni.showToast({ title: i18n.t('giftCards.buy'), icon: 'none' })
    },

    onGetCode() {
      if (this.codeCountdown > 0) return
      this.codeCountdown = 60
      this.codeTimer = setInterval(() => {
        this.codeCountdown -= 1
        if (this.codeCountdown <= 0 && this.codeTimer) {
          clearInterval(this.codeTimer)
          this.codeTimer = null
        }
      }, 1000)
    },

    onConfirmBind() {
      this.showBindModal = false
      uni.showToast({ title: i18n.t('giftCards.bound'), icon: 'success' })
    },

    onCloseTxModal() {
      this.showTxModal = false
    },
  },
}
</script>

<style lang="scss" scoped>
.gift-cards {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 200rpx;
}
.content {
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.summary-card {
  position: relative;
  overflow: hidden;
  border-radius: 24rpx;
}
.summary-card-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #f6d365, #fda085);
}
.summary-card-content {
  position: relative;
  z-index: 10;
  padding: 32rpx 24rpx;
  color: #fff;
}
.summary-card-body {
  display: flex;
  align-items: center;
}
.summary-item {
  flex: 1;
  text-align: center;
}
.summary-label {
  display: block;
  font-size: 24rpx;
  opacity: 0.9;
}
.summary-value {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  margin-top: 8rpx;
}
.summary-divider {
  width: 1rpx;
  height: 48rpx;
  background: rgba(255, 255, 255, 0.4);
}
.tab-bar {
  display: flex;
  background: var(--color-surface);
  border-radius: 16rpx;
  padding: 8rpx;
}
.tab-btn {
  flex: 1;
  padding: 16rpx 0;
  text-align: center;
  font-size: 26rpx;
  border-radius: 12rpx;
  color: var(--color-text-secondary);
}
.tab-btn.active {
  background: var(--color-primary);
  color: var(--color-text);
  font-weight: 600;
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.gift-card-item {
  position: relative;
  overflow: hidden;
  border-radius: 24rpx;
  padding: 32rpx;
  color: #fff;
}
.card-decoration {
  position: absolute;
  top: -40rpx;
  right: -40rpx;
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}
.card-body {
  position: relative;
  z-index: 10;
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-brand {
  font-size: 24rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}
.card-chip {
  width: 48rpx;
  height: 32rpx;
  border-radius: 6rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.6);
}
.card-amount {
  display: block;
  font-size: 56rpx;
  font-weight: 700;
  margin: 24rpx 0;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.card-number {
  display: block;
  font-size: 24rpx;
  letter-spacing: 2rpx;
}
.card-expiry {
  display: block;
  font-size: 22rpx;
  margin-top: 4rpx;
}
.card-balance {
  text-align: right;
}
.balance-label {
  display: block;
  font-size: 22rpx;
}
.balance-value {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  margin-top: 4rpx;
}
.card-used-overlay {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 80rpx;
  height: 80rpx;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 50%;
}
.empty-state {
  padding: 96rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.empty-icon {
  font-size: 120rpx;
  opacity: 0.4;
}
.empty-title {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}
.bottom-actions {
  display: flex;
  gap: 16rpx;
}
.action-btn {
  flex: 1;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.action-btn--outline {
  background: var(--color-surface);
  border: 2rpx solid var(--color-divider);
  color: var(--color-text);
}
.action-btn--primary {
  background: var(--color-primary);
  color: var(--color-text);
}
.action-icon {
  font-size: 32rpx;
}
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0);
  pointer-events: none;
  transition: background 0.3s;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.modal-overlay.active {
  background: rgba(0, 0, 0, 0.4);
  pointer-events: auto;
}
.modal-panel {
  width: 100%;
  max-height: 80vh;
  background: var(--color-surface);
  border-radius: 32rpx 32rpx 0 0;
  padding: 16rpx 32rpx 32rpx;
  overflow-y: auto;
  transform: translateY(100%);
  transition: transform 0.3s;
}
.modal-overlay.active .modal-panel {
  transform: translateY(0);
}
.modal-handle {
  width: 60rpx;
  height: 6rpx;
  background: var(--color-divider);
  border-radius: 3rpx;
  margin: 0 auto 24rpx;
}
.modal-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  text-align: center;
  margin-bottom: 12rpx;
}
.modal-desc {
  display: block;
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  text-align: center;
  margin-bottom: 24rpx;
}
.form-group {
  margin-bottom: 24rpx;
}
.form-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin-bottom: 12rpx;
}
.form-field {
  background: var(--color-background);
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
}
.form-field-row {
  display: flex;
  gap: 16rpx;
}
.form-field--flex {
  flex: 1;
}
.form-input {
  width: 100%;
  font-size: 28rpx;
}
.code-btn {
  padding: 16rpx 24rpx;
  background: var(--color-primary);
  border-radius: 12rpx;
}
.code-btn-text {
  font-size: 24rpx;
  color: var(--color-text);
  font-weight: 600;
}
.modal-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 32rpx;
}
.modal-btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  font-size: 28rpx;
}
.modal-btn--cancel {
  background: var(--color-background);
  color: var(--color-text);
}
.modal-btn--confirm {
  background: var(--color-primary);
  color: var(--color-text);
  font-weight: 600;
}
.tx-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.tx-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}
.tx-item:last-child {
  border-bottom: none;
}
.tx-info {
  display: flex;
  flex-direction: column;
}
.tx-type {
  font-size: 26rpx;
  color: var(--color-text);
}
.tx-date {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}
.tx-amount {
  font-size: 28rpx;
  font-weight: 600;
}
.tx-positive {
  color: var(--color-success);
}
.tx-negative {
  color: var(--color-text-secondary);
}
</style>
