<template>
  <view class="gift-cards">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">礼品卡</text>
      <view class="header-btn" />
    </view>

    <view class="content">
      <!-- 顶部汇总卡片 -->
      <view class="summary-card">
        <view class="summary-card-bg" />
        <view class="summary-card-content">
          <view class="summary-card-body">
            <view class="summary-item">
              <text class="summary-label">可用礼品卡</text>
              <text class="summary-value">{{ summary.count }} 张</text>
            </view>
            <view class="summary-divider" />
            <view class="summary-item">
              <text class="summary-label">总余额</text>
              <text class="summary-value">¥{{ summary.totalBalance }}</text>
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
          :style="{ color: activeTab === tab.key ? 'var(--primary)' : 'var(--text-400)' }"
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
              ¥{{ card.faceValue }}
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
                  有效期至 {{ card.expiry }}
                </text>
              </view>
              <view class="card-balance">
                <text
                  class="balance-label"
                  :style="{ color: card.active ? 'rgba(255,255,255,0.5)' : 'var(--text-400)' }"
                >
                  余额
                </text>
                <text
                  class="balance-value"
                  :style="{ color: card.active ? '#ffffff' : 'var(--text-400)' }"
                >
                  ¥{{ card.balance }}
                </text>
              </view>
            </view>
          </view>
          <view v-if="card.active && card.balance < card.faceValue" class="card-used-overlay" />
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else class="empty-state">
        <text class="empty-icon"><text class="luc luc-inbox"></text></text>
        <text class="empty-title">{{ emptyText }}</text>
      </view>

      <!-- 底部操作按钮 -->
      <view class="bottom-actions">
        <view class="action-btn action-btn--outline" @click="onBindCard">
          <text class="action-icon"><text class="luc luc-link"></text></text>
          <text>绑定新卡</text>
        </view>
        <view class="action-btn action-btn--primary" @click="onBuyCard">
          <text class="action-icon"><text class="luc luc-shopping-bag"></text></text>
          <text>购买礼品卡</text>
        </view>
      </view>
    </view>

    <!-- 绑定礼品卡弹窗 -->
    <view class="modal-overlay" :class="{ active: showBindModal }" @click="onCloseBindModal">
      <view class="modal-panel" @click.stop>
        <view class="modal-handle" />
        <text class="modal-title">绑定礼品卡</text>
        <text class="modal-desc">请输入礼品卡卡号和验证码以完成绑定</text>

        <view class="form-group">
          <text class="form-label">礼品卡卡号</text>
          <view class="form-field">
            <input
              v-model="bindForm.cardNo"
              class="form-input"
              type="text"
              placeholder="请输入16位卡号"
              maxlength="16"
            >
          </view>
        </view>

        <view class="form-group">
          <text class="form-label">验证码</text>
          <view class="form-field-row">
            <view class="form-field form-field--flex">
              <input
                v-model="bindForm.code"
                class="form-input"
                type="text"
                placeholder="请输入验证码"
                maxlength="6"
              >
            </view>
            <view class="code-btn" @click="onGetCode">
              <text class="code-btn-text">{{ codeBtnText }}</text>
            </view>
          </view>
        </view>

        <view class="modal-actions">
          <view class="modal-btn modal-btn--cancel" @click="onCloseBindModal">取消</view>
          <view class="modal-btn modal-btn--confirm" @click="onConfirmBind">绑定</view>
        </view>
      </view>
    </view>

    <!-- 交易记录弹窗 -->
    <view class="modal-overlay" :class="{ active: showTxModal }" @click="onCloseTxModal">
      <view class="modal-panel" @click.stop>
        <view class="modal-handle" />
        <text class="modal-title">交易记录</text>
        <view class="tx-list">
          <view v-for="tx in currentTxList" :key="tx.id" class="tx-item">
            <view class="tx-left">
              <text class="tx-desc">{{ tx.desc }}</text>
              <text class="tx-time">{{ tx.time }}</text>
            </view>
            <text class="tx-amount" :class="tx.type === 'in' ? 'tx-in' : 'tx-out'">
              {{ tx.type === 'in' ? '+' : '-' }}¥{{ tx.amount }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { giftCardApi } from '@/api'

export default {
  data() {
    return {
      activeTab: 'available',
      tabs: [
        { label: '可用', key: 'available' },
        { label: '已使用', key: 'used' },
        { label: '已过期', key: 'expired' },
      ],
      showBindModal: false,
      showTxModal: false,
      codeBtnText: '获取验证码',
      bindForm: {
        cardNo: '',
        code: '',
      },
      giftCards: [],
      currentTxList: [],
    }
  },
  computed: {
    summary() {
      const available = this.giftCards.filter((c) => c.status === 'available')
      const total = available.reduce((sum, c) => sum + parseFloat(c.balance), 0)
      return {
        count: available.length,
        totalBalance: total.toFixed(2),
      }
    },
    filteredCards() {
      return this.giftCards.filter((c) => c.status === this.activeTab)
    },
    emptyText() {
      const map = {
        available: '没有可用的礼品卡',
        used: '没有已使用的礼品卡',
        expired: '没有已过期的礼品卡',
      }
      return map[this.activeTab]
    },
  },
  onLoad() {
    this.loadGiftCards()
  },
  methods: {
    async loadGiftCards() {
      try {
        const res = await giftCardApi.getGiftCards()
        const list = res.data || res
        this.giftCards = (list.items || list || []).map((c) => ({
          id: c.id,
          faceValue: c.faceValue || c.faceValue?.toFixed(2) || '0.00',
          balance: c.balance || c.balance?.toFixed(2) || '0.00',
          lastFour: c.lastFour || '0000',
          expiry: c.expiry || '',
          status: c.status || 'available',
          gradient:
            c.gradient ||
            'linear-gradient(135deg, var(--brand-700) 0%, var(--brand-500) 60%, var(--brand-400) 100%)',
          active: c.status === 'available',
        }))
      } catch (e) {
        this.giftCards = []
      }
    },
    goBack() {
      uni.navigateBack()
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
      uni.showToast({ title: '购买礼品卡', icon: 'none' })
    },
    onGetCode() {
      if (this.codeBtnText !== '获取验证码') return
      this.codeBtnText = '60s'
      const timer = setInterval(() => {
        const sec = parseInt(this.codeBtnText)
        if (sec <= 1) {
          clearInterval(timer)
          this.codeBtnText = '获取验证码'
        } else {
          this.codeBtnText = sec - 1 + 's'
        }
      }, 1000)
    },
    async onConfirmBind() {
      if (!this.bindForm.cardNo || !this.bindForm.code) {
        uni.showToast({ title: '请填写完整信息', icon: 'none' })
        return
      }
      try {
        await giftCardApi.bindGiftCard(this.bindForm.cardNo, this.bindForm.code)
        uni.showToast({ title: '绑定成功', icon: 'success' })
        this.showBindModal = false
        this.bindForm = { cardNo: '', code: '' }
        this.loadGiftCards()
      } catch (e) {
        uni.showToast({ title: '绑定失败，请重试', icon: 'none' })
      }
    },
    async onShowTx(cardId) {
      try {
        const res = await giftCardApi.getGiftCardTransactions(cardId)
        const txData = res.data || res
        this.currentTxList = (txData.items || txData || []).map((t) => ({
          id: t.id,
          desc: t.desc || '',
          time: t.time || '',
          amount: t.amount || '0.00',
          type: t.type || 'out',
        }))
        this.showTxModal = true
      } catch (e) {
        this.currentTxList = []
        this.showTxModal = true
      }
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
  background: var(--background);
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 30rpx;
  background: var(--background);
  border-bottom: 1rpx solid var(--border);
  position: sticky;
  top: 0;
  z-index: 30;
}

.header-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 44rpx;
  color: var(--icon-700);
  line-height: 1;
}

.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
}

.content {
  padding: 30rpx 30rpx 40rpx;
}

/* ===== 汇总卡片 ===== */
.summary-card {
  position: relative;
  overflow: hidden;
  border-radius: calc(var(--radius) * 1.2);
  min-height: 200rpx;
  background: linear-gradient(
    135deg,
    var(--brand-600) 0%,
    var(--brand-500) 50%,
    var(--brand-400) 100%
  );
}

.summary-card-bg {
  position: absolute;
  inset: 0;
  opacity: 0.08;
  background-image:
    radial-gradient(circle at 20% 30%, var(--brand-50) 0%, transparent 50%),
    radial-gradient(circle at 80% 70%, var(--brand-100) 0%, transparent 50%);
  pointer-events: none;
}

.summary-card-content {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 200rpx;
  padding: 36rpx;
}

.summary-card-body {
  display: flex;
  align-items: center;
  gap: 32rpx;
}

.summary-item {
  flex: 1;
}

.summary-label {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.7);
  display: block;
}

.summary-value {
  margin-top: 12rpx;
  font-size: 52rpx;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: -1rpx;
}

.summary-divider {
  width: 2rpx;
  height: 80rpx;
  background: rgba(255, 255, 255, 0.2);
}

/* ===== Tab 栏 ===== */
.tab-bar {
  margin-top: 40rpx;
  display: flex;
  border-bottom: 2rpx solid var(--border);
}

.tab-btn {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  position: relative;
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--primary);
}

/* ===== 礼品卡卡片 ===== */
.card-list {
  margin-top: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.gift-card-item {
  position: relative;
  border-radius: calc(var(--radius) * 1.2);
  padding: 36rpx;
  min-height: 320rpx;
  overflow: hidden;
}

.card-decoration::before {
  content: '';
  position: absolute;
  top: -60rpx;
  right: -60rpx;
  width: 240rpx;
  height: 240rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  pointer-events: none;
}

.card-decoration::after {
  content: '';
  position: absolute;
  bottom: -80rpx;
  right: 60rpx;
  width: 320rpx;
  height: 320rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  pointer-events: none;
}

.card-body {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 250rpx;
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.card-brand {
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 4rpx;
  text-transform: uppercase;
}

.card-chip {
  width: 60rpx;
  height: 44rpx;
  border-radius: 6rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.35);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.2) 0%, rgba(255, 255, 255, 0.05) 100%);
  position: relative;
}

.card-chip::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 8rpx;
  right: 8rpx;
  height: 2rpx;
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-50%);
}

.card-amount {
  margin-top: 24rpx;
  font-size: 48rpx;
  font-weight: 600;
  letter-spacing: -1rpx;
}

.card-footer {
  margin-top: 32rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.card-number {
  font-size: 22rpx;
  letter-spacing: 2rpx;
  display: block;
}

.card-expiry {
  font-size: 22rpx;
  display: block;
  margin-top: 8rpx;
}

.card-balance {
  text-align: right;
}

.balance-label {
  font-size: 22rpx;
  display: block;
}

.balance-value {
  font-size: 30rpx;
  font-weight: 600;
  display: block;
  margin-top: 4rpx;
}

.card-used-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 55%, rgba(0, 0, 0, 0.25) 100%);
  border-radius: inherit;
  pointer-events: none;
}

/* ===== 空状态 ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
}

.empty-icon {
  font-size: 80rpx;
  margin-bottom: 24rpx;
}

.empty-title {
  font-size: 28rpx;
  color: var(--text-400);
}

/* ===== 底部操作 ===== */
.bottom-actions {
  margin-top: 48rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 88rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.action-btn--outline {
  background: var(--secondary);
  color: var(--secondary-foreground);
  border: 2rpx solid var(--border);
}

.action-btn--primary {
  background: var(--primary);
  color: var(--primary-foreground);
}

.action-icon {
  font-size: 28rpx;
}

/* ===== 弹窗 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.25s ease;
}

.modal-overlay.active {
  opacity: 1;
  pointer-events: auto;
}

.modal-panel {
  width: 100%;
  background: var(--card);
  border-radius: calc(var(--radius) * 1.2) calc(var(--radius) * 1.2) 0 0;
  padding: 24rpx 40rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  transform: translateY(100%);
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);
}

.modal-overlay.active .modal-panel {
  transform: translateY(0);
}

.modal-handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: var(--background-400);
  margin: 0 auto 32rpx;
}

.modal-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
}

.modal-desc {
  font-size: 26rpx;
  color: var(--muted-foreground);
  display: block;
  margin-top: 12rpx;
}

.form-group {
  margin-top: 32rpx;
}

.form-label {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--text-500);
  display: block;
  margin-bottom: 12rpx;
}

.form-field {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 28rpx;
  border: 2rpx solid var(--input);
  border-radius: var(--radius);
  background: var(--background);
}

.form-field--flex {
  flex: 1;
}

.form-field:focus-within {
  border-color: var(--ring);
}

.form-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 28rpx;
  color: var(--foreground);
}

.form-input::placeholder {
  color: var(--muted-foreground);
}

.form-field-row {
  display: flex;
  gap: 20rpx;
}

.code-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  padding: 0 28rpx;
  border-radius: 999rpx;
  border: 2rpx solid var(--primary);
  background: transparent;
  white-space: nowrap;
}

.code-btn-text {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--primary);
}

.modal-actions {
  margin-top: 48rpx;
  display: flex;
  gap: 20rpx;
}

.modal-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.modal-btn--cancel {
  background: var(--secondary);
  color: var(--secondary-foreground);
}

.modal-btn--confirm {
  background: var(--primary);
  color: var(--primary-foreground);
}

/* ===== 交易记录 ===== */
.tx-list {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  max-height: 500rpx;
  overflow-y: auto;
}

.tx-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--border);
}

.tx-left {
  flex: 1;
  min-width: 0;
}

.tx-desc {
  font-size: 26rpx;
  color: var(--foreground);
  display: block;
}

.tx-time {
  font-size: 22rpx;
  color: var(--text-400);
  display: block;
  margin-top: 6rpx;
}

.tx-amount {
  font-size: 28rpx;
  font-weight: 600;
  flex-shrink: 0;
}

.tx-in {
  color: var(--state-success);
}

.tx-out {
  color: var(--foreground);
}
</style>
