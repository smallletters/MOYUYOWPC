<template>
  <view class="gift-card-manage">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-left" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">礼品卡管理</text>
      <view class="header-right" />
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 快捷入口 -->
      <view class="quick-entries">
        <view class="quick-entry" @tap="handleBuyCard">
          <view class="quick-icon" style="background: var(--brand-50)">
            <text class="quick-emoji"><text class="luc luc-shopping-bag" /></text>
          </view>
          <text class="quick-label">购买礼品卡</text>
        </view>
        <view class="quick-entry" @tap="handleBindCard">
          <view class="quick-icon" style="background: var(--state-success-surface)">
            <text class="quick-emoji"><text class="luc luc-link" /></text>
          </view>
          <text class="quick-label">绑定礼品卡</text>
        </view>
        <view class="quick-entry" @tap="handleSendCard">
          <view class="quick-icon" style="background: var(--state-warning-surface)">
            <text class="quick-emoji"><text class="luc luc-gift" /></text>
          </view>
          <text class="quick-label">赠送礼品卡</text>
        </view>
      </view>

      <!-- Tab 切换 -->
      <view class="card-tabs">
        <view
          v-for="tab in cardTabs"
          :key="tab.key"
          class="card-tab"
          :class="{ active: activeCardTab === tab.key }"
          :style="{
            color: activeCardTab === tab.key ? 'var(--primary)' : 'var(--text-400)',
            fontWeight: activeCardTab === tab.key ? '600' : '500',
          }"
          @tap="activeCardTab = tab.key"
        >
          <text>{{ tab.label }}</text>
        </view>
      </view>

      <!-- 礼品卡列表 -->
      <view class="card-list">
        <view
          v-for="card in filteredCards"
          :key="card.id"
          class="gift-card"
          :class="{
            active: card.status === 'active',
            used: card.status === 'used',
            expired: card.status === 'expired',
          }"
          :style="{
            background: card.status === 'active' ? card.gradient : 'var(--background-200)',
            opacity: card.status === 'active' ? 1 : 0.6,
          }"
          @tap="handleCardTap(card)"
        >
          <view v-if="card.status === 'active'" class="card-deco" />
          <view class="card-body">
            <view class="card-top">
              <text
                class="card-brand"
                :style="{
                  color: card.status === 'active' ? 'rgba(255,255,255,0.9)' : 'var(--text-400)',
                }"
              >
                MOYUYO
              </text>
              <view class="card-chip" :class="{ muted: card.status !== 'active' }" />
            </view>
            <text
              class="card-amount"
              :style="{
                color: card.status === 'active' ? '#ffffff' : 'var(--text-400)',
                textDecoration: card.status === 'used' ? 'line-through' : 'none',
              }"
            >
              ${{ card.faceValue }}
            </text>
            <view class="card-footer">
              <view class="card-number-row">
                <text
                  class="card-number-masked"
                  :style="{
                    color: card.status === 'active' ? 'rgba(255,255,255,0.5)' : 'var(--text-400)',
                  }"
                >
                  **** **** **** {{ card.lastFour }}
                </text>
                <text
                  class="card-expiry"
                  :style="{
                    color: card.status === 'active' ? 'rgba(255,255,255,0.5)' : 'var(--text-400)',
                  }"
                >
                  {{ card.expiryText }}
                </text>
              </view>
              <view class="card-status-section">
                <text
                  v-if="card.status === 'active'"
                  class="card-balance"
                  :style="{ color: 'rgba(255,255,255,0.5)' }"
                >
                  余额 ${{ card.balance }} / ${{ card.faceValue }}
                </text>
                <view class="card-status-badge" :class="card.status">
                  <text>{{ card.statusText }}</text>
                </view>
              </view>
            </view>
          </view>
          <view
            v-if="card.status === 'active' && card.balance < card.faceValue"
            class="card-used-gradient"
          />
        </view>
      </view>

      <!-- 操作按钮（仅活跃卡片） -->
      <view v-if="selectedCard && selectedCard.status === 'active'" class="card-actions">
        <view class="action-btn text" @tap="handleViewDetail(selectedCard)">查看详情</view>
        <view class="action-btn primary" @tap="handleUseCard(selectedCard)">使用</view>
        <view class="action-btn outline" @tap="handleGiftCard(selectedCard)">赠送</view>
      </view>

      <!-- 购买礼品卡区域 -->
      <view class="section-card">
        <view class="section-card-header">
          <view class="section-card-icon" style="background: var(--brand-50)">
            <text><text class="luc luc-shopping-bag" /></text>
          </view>
          <view>
            <text class="section-card-title">购买礼品卡</text>
            <text class="section-card-desc">自定义面额，心意无限</text>
          </view>
        </view>

        <!-- 面额预设 -->
        <view class="amount-presets">
          <view
            v-for="amt in amounts"
            :key="amt.value"
            class="amount-btn"
            :class="{ active: selectedAmount === amt.value }"
            :style="{
              background: selectedAmount === amt.value ? 'var(--primary)' : 'var(--secondary)',
              color: selectedAmount === amt.value ? 'var(--primary-foreground)' : 'var(--text-500)',
            }"
            @tap="selectedAmount = amt.value"
          >
            <text>${{ amt.label }}</text>
          </view>
        </view>

        <!-- 自定义面额滑块 -->
        <view class="slider-section">
          <view class="slider-header">
            <text class="slider-label">自定义面额</text>
            <text class="slider-value">${{ customAmount }}</text>
          </view>
          <slider
            class="amount-slider"
            min="10"
            max="500"
            step="10"
            :value="customAmount"
            active-color="var(--primary)"
            background-color="var(--background-300)"
            block-color="var(--primary)"
            block-size="22"
            @changing="onSliderChange"
          />
          <view class="slider-range">
            <text>$10</text>
            <text>$500</text>
          </view>
        </view>

        <!-- 购买数量 -->
        <view class="qty-section">
          <text class="qty-label">购买数量</text>
          <view class="qty-controls">
            <view class="qty-btn" @tap="decreaseQty">−</view>
            <text class="qty-value">{{ purchaseQty }}</text>
            <view class="qty-btn" @tap="increaseQty">+</view>
          </view>
        </view>

        <!-- 购买后赠送折叠 -->
        <view class="collapse-section">
          <view class="collapse-header" @tap="toggleGiftAfterBuy">
            <view class="collapse-header-left">
              <text class="collapse-icon"><text class="luc luc-gift" /></text>
              <text class="collapse-label">购买后直接赠送</text>
            </view>
            <text class="collapse-arrow" :class="{ open: giftAfterBuyOpen }">
              <text class="luc luc-chevron-right" />
            </text>
          </view>
          <view class="collapse-body" :class="{ open: giftAfterBuyOpen }">
            <view class="collapse-content">
              <view class="input-row">
                <text class="input-label">收件人邮箱</text>
                <input
                  v-model="giftEmail"
                  class="input-field"
                  placeholder="输入对方邮箱"
                  type="email"
                >
              </view>
              <view class="input-row">
                <text class="input-label">手机号码</text>
                <input
                  v-model="giftPhone"
                  class="input-field"
                  placeholder="输入对方手机号"
                  type="tel"
                >
              </view>
            </view>
          </view>
        </view>

        <view class="buy-now-btn" @tap="handleBuyNow">
          <text>立即购买</text>
        </view>
      </view>

      <!-- 绑定礼品卡 -->
      <view class="section-card">
        <view class="collapse-header" @tap="toggleBindCard">
          <view class="collapse-header-left">
            <text class="collapse-icon"><text class="luc luc-link" /></text>
            <text class="collapse-label">绑定礼品卡</text>
          </view>
          <text class="collapse-arrow" :class="{ open: bindCardOpen }">
            <text class="luc luc-chevron-right" />
          </text>
        </view>
        <view class="collapse-body" :class="{ open: bindCardOpen }">
          <view class="collapse-content">
            <view class="input-row">
              <text class="input-label">礼品卡卡号</text>
              <input
                v-model="bindCardNumber"
                class="input-field"
                placeholder="请输入16位卡号"
                maxlength="16"
              >
            </view>
            <view class="input-row">
              <text class="input-label">礼品卡密码</text>
              <input
                v-model="bindCardPwd"
                class="input-field"
                placeholder="请输入礼品卡密码"
                type="password"
              >
            </view>
            <view class="bind-btn" @tap="handleBindConfirm">绑定</view>
          </view>
        </view>
      </view>

      <!-- 使用记录 -->
      <view class="section-card">
        <view class="collapse-header" @tap="toggleUsageLog">
          <view class="collapse-header-left">
            <text class="collapse-icon"><text class="luc luc-clipboard-list" /></text>
            <text class="collapse-label">礼品卡 **** 5678 使用记录</text>
          </view>
          <text class="collapse-arrow" :class="{ open: usageLogOpen }">
            <text class="luc luc-chevron-right" />
          </text>
        </view>
        <view class="collapse-body" :class="{ open: usageLogOpen }">
          <view class="collapse-content">
            <view class="timeline">
              <view class="timeline-item">
                <view class="timeline-dot" />
                <view class="timeline-info">
                  <text class="timeline-date">2026-07-01 14:23</text>
                  <text class="timeline-order">关联订单：#MOY20260701001</text>
                </view>
                <view class="timeline-amount">
                  <text class="timeline-spend">-$35.00</text>
                  <text class="timeline-remain">剩余 $45.00</text>
                </view>
              </view>
              <view class="timeline-item">
                <view class="timeline-dot green" />
                <view class="timeline-info">
                  <text class="timeline-date">2026-06-10 09:15</text>
                  <text class="timeline-order">关联订单：#MOY20260610023</text>
                </view>
                <view class="timeline-amount">
                  <text class="timeline-spend">-$20.00</text>
                  <text class="timeline-remain">剩余 $80.00</text>
                </view>
              </view>
            </view>
            <view class="usage-summary">
              <view class="summary-row">
                <text class="summary-label">累计使用</text>
                <text class="summary-value">$55.00</text>
              </view>
              <view class="summary-row">
                <text class="summary-label">当前余额</text>
                <text class="summary-value primary">$45.00</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template>

<script>
import { giftCardApi } from '@/api'

export default {
  data() {
    return {
      activeCardTab: 'all',
      selectedAmount: 100,
      customAmount: 100,
      purchaseQty: 1,
      giftAfterBuyOpen: false,
      bindCardOpen: false,
      usageLogOpen: false,
      giftEmail: '',
      giftPhone: '',
      bindCardNumber: '',
      bindCardPwd: '',
      selectedCard: null,
      cardTabs: [
        { key: 'all', label: '全部' },
        { key: 'available', label: '可用' },
        { key: 'used', label: '已使用' },
        { key: 'expired', label: '已过期' },
      ],
      amounts: [
        { value: 25, label: '25' },
        { value: 50, label: '50' },
        { value: 100, label: '100' },
        { value: 200, label: '200' },
      ],
      cards: [],
    }
  },
  computed: {
    filteredCards() {
      if (this.activeCardTab === 'all') return this.cards
      if (this.activeCardTab === 'available') return this.cards.filter((c) => c.status === 'active')
      if (this.activeCardTab === 'used') return this.cards.filter((c) => c.status === 'used')
      if (this.activeCardTab === 'expired') return this.cards.filter((c) => c.status === 'expired')
      return this.cards
    },
  },
  onLoad() {
    this.loadCards()
  },
  methods: {
    async loadCards() {
      try {
        const res = await giftCardApi.getGiftCards()
        const list = res.data || res
        this.cards = (list.items || list || []).map((c) => ({
          id: c.id,
          status: c.status || 'active',
          statusText:
            c.statusText ||
            (c.status === 'active' ? '可用' : c.status === 'used' ? '已使用' : '已过期'),
          faceValue: c.faceValue || 0,
          balance: c.balance || 0,
          lastFour: c.lastFour || '0000',
          expiryText: c.expiryText || c.expiry || '',
          gradient:
            c.gradient ||
            'linear-gradient(135deg, var(--brand-800), var(--brand-600), var(--brand-500))',
        }))
      } catch (e) {
        this.cards = []
      }
    },
    goBack() {
      uni.navigateBack()
    },
    handleBuyCard() {
      uni.showToast({ title: '购买礼品卡', icon: 'none' })
    },
    handleBindCard() {
      this.bindCardOpen = !this.bindCardOpen
    },
    handleSendCard() {
      uni.showToast({ title: '赠送礼品卡', icon: 'none' })
    },
    handleCardTap(card) {
      this.selectedCard = card
    },
    handleViewDetail(card) {
      this.usageLogOpen = !this.usageLogOpen
    },
    handleUseCard(card) {
      uni.showToast({ title: '使用礼品卡', icon: 'none' })
    },
    handleGiftCard(card) {
      uni.showToast({ title: '赠送礼品卡', icon: 'none' })
    },
    onSliderChange(e) {
      this.customAmount = e.detail.value
      this.selectedAmount = this.amounts.find((a) => a.value === this.customAmount)
        ? this.customAmount
        : 0
    },
    decreaseQty() {
      if (this.purchaseQty > 1) this.purchaseQty--
    },
    increaseQty() {
      if (this.purchaseQty < 99) this.purchaseQty++
    },
    toggleGiftAfterBuy() {
      this.giftAfterBuyOpen = !this.giftAfterBuyOpen
    },
    toggleBindCard() {
      this.bindCardOpen = !this.bindCardOpen
    },
    toggleUsageLog() {
      this.usageLogOpen = !this.usageLogOpen
    },
    async handleBuyNow() {
      try {
        await giftCardApi.purchaseGiftCard(this.customAmount, this.purchaseQty)
        uni.showToast({
          title: `购买 ${this.purchaseQty} 张 $${this.customAmount} 礼品卡成功`,
          icon: 'success',
        })
        this.loadCards()
      } catch (e) {
        uni.showToast({ title: '购买失败，请重试', icon: 'none' })
      }
    },
    async handleBindConfirm() {
      if (!this.bindCardNumber || !this.bindCardPwd) {
        uni.showToast({ title: '请填写完整信息', icon: 'none' })
        return
      }
      try {
        await giftCardApi.bindGiftCard(this.bindCardNumber, this.bindCardPwd)
        uni.showToast({ title: '礼品卡绑定成功', icon: 'success' })
        this.bindCardNumber = ''
        this.bindCardPwd = ''
        this.bindCardOpen = false
        this.loadCards()
      } catch (e) {
        uni.showToast({ title: '绑定失败，请重试', icon: 'none' })
      }
    },
  },
}
</script>

<style scoped lang="scss">
.gift-card-manage {
  min-height: 100vh;
  background: var(--background);
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 112rpx;
  padding: 0 30rpx;
  background: var(--background);
  border-bottom: 2rpx solid var(--border);
  position: sticky;
  top: 0;
  z-index: 30;
}
.header-left,
.header-right {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon {
  font-size: 40rpx;
  color: var(--foreground);
}
.header-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
}
.scroll {
  padding: 30rpx 30rpx 100rpx;
}
.quick-entries {
  display: flex;
  gap: 20rpx;
  overflow-x: auto;
  padding-bottom: 8rpx;
}
.quick-entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  min-width: 200rpx;
  padding: 30rpx 24rpx;
  border-radius: 24rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  flex-shrink: 0;
}
.quick-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.quick-emoji {
  font-size: 40rpx;
}
.quick-label {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--foreground);
}
.card-tabs {
  display: flex;
  border-bottom: 2rpx solid var(--border);
  margin: 30rpx 0;
}
.card-tab {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  position: relative;
}
.card-tab.active::after {
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
.card-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.gift-card {
  border-radius: 28rpx;
  padding: 40rpx;
  min-height: 280rpx;
  overflow: hidden;
  position: relative;
}
.gift-card.active {
  box-shadow: var(--shadow-lg);
}
.card-deco {
  position: absolute;
  top: -60rpx;
  right: -60rpx;
  width: 240rpx;
  height: 240rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  pointer-events: none;
}
.card-deco::after {
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
  min-height: 260rpx;
}
.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.card-brand {
  font-size: 28rpx;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.card-chip {
  width: 72rpx;
  height: 52rpx;
  border-radius: 8rpx;
  border: 3rpx solid rgba(255, 255, 255, 0.35);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0.05));
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
.card-chip.muted {
  border-color: var(--background-400);
  background: linear-gradient(135deg, var(--background-300), var(--background-200));
}
.card-chip.muted::after {
  background: var(--background-400);
}
.card-amount {
  font-size: 48rpx;
  font-weight: 600;
  margin-top: 24rpx;
}
.card-footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 32rpx;
}
.card-number-masked {
  font-size: 24rpx;
  letter-spacing: 0.1em;
  display: block;
}
.card-expiry {
  font-size: 22rpx;
  display: block;
  margin-top: 8rpx;
}
.card-status-section {
  text-align: right;
}
.card-balance {
  font-size: 22rpx;
  display: block;
}
.card-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 6rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
  margin-top: 8rpx;
}
.card-status-badge.active {
  background: rgba(52, 199, 89, 0.2);
  color: var(--state-success);
}
.card-status-badge.used {
  background: var(--background-300);
  color: var(--text-400);
}
.card-status-badge.expired {
  background: var(--state-error-surface);
  color: var(--state-error);
}
.card-used-gradient {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(90deg, transparent 55%, rgba(0, 0, 0, 0.25) 100%);
  border-radius: inherit;
}
.card-actions {
  display: flex;
  gap: 20rpx;
  margin: 24rpx 0;
}
.action-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 600;
}
.action-btn.text {
  color: var(--primary);
  background: transparent;
}
.action-btn.primary {
  background: var(--primary);
  color: var(--primary-foreground);
}
.action-btn.outline {
  color: var(--primary);
  border: 2rpx solid var(--primary);
  background: transparent;
}
.section-card {
  border-radius: 24rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  padding: 30rpx;
  margin-top: 24rpx;
}
.section-card-header {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 40rpx;
}
.section-card-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
}
.section-card-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
}
.section-card-desc {
  font-size: 24rpx;
  color: var(--text-400);
  display: block;
  margin-top: 4rpx;
}
.amount-presets {
  display: flex;
  gap: 16rpx;
  margin-bottom: 30rpx;
}
.amount-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}
.slider-section {
  margin-bottom: 30rpx;
}
.slider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.slider-label {
  font-size: 24rpx;
  color: var(--text-500);
}
.slider-value {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--primary);
}
.amount-slider {
  width: 100%;
}
.slider-range {
  display: flex;
  justify-content: space-between;
  font-size: 24rpx;
  color: var(--text-400);
  margin-top: 8rpx;
}
.qty-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 30rpx;
}
.qty-label {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
}
.qty-controls {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.qty-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  color: var(--text-600);
}
.qty-value {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  min-width: 48rpx;
  text-align: center;
}
.collapse-section {
  margin-bottom: 30rpx;
}
.collapse-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
}
.collapse-header-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.collapse-icon {
  font-size: 32rpx;
}
.collapse-label {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
}
.collapse-arrow {
  font-size: 40rpx;
  color: var(--text-400);
  transition: transform 0.2s;
}
.collapse-arrow.open {
  transform: rotate(90deg);
}
.collapse-body {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.35s;
}
.collapse-body.open {
  max-height: 600rpx;
}
.collapse-content {
  padding-top: 16rpx;
}
.input-row {
  margin-bottom: 20rpx;
}
.input-label {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--text-500);
  display: block;
  margin-bottom: 12rpx;
}
.input-field {
  width: 100%;
  height: 88rpx;
  border-radius: 24rpx;
  padding: 0 24rpx;
  background: var(--background);
  border: 2rpx solid var(--input);
  font-size: 28rpx;
  color: var(--foreground);
  box-sizing: border-box;
}
.buy-now-btn,
.bind-btn {
  width: 100%;
  height: 96rpx;
  border-radius: 999rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 30rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.timeline {
  position: relative;
}
.timeline-item {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding-left: 24rpx;
  padding-bottom: 32rpx;
  position: relative;
}
.timeline-item::before {
  content: '';
  position: absolute;
  left: 8rpx;
  top: 16rpx;
  bottom: 0;
  width: 2rpx;
  background: var(--background-300);
}
.timeline-item:last-child::before {
  display: none;
}
.timeline-dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: var(--primary);
  position: absolute;
  left: 0;
  top: 12rpx;
  flex-shrink: 0;
}
.timeline-dot.green {
  background: var(--state-success);
}
.timeline-info {
  flex: 1;
}
.timeline-date {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--foreground);
  display: block;
}
.timeline-order {
  font-size: 22rpx;
  color: var(--text-400);
  display: block;
  margin-top: 4rpx;
}
.timeline-amount {
  text-align: right;
}
.timeline-spend {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--state-error);
  display: block;
}
.timeline-remain {
  font-size: 22rpx;
  color: var(--text-400);
  display: block;
  margin-top: 4rpx;
}
.usage-summary {
  margin-top: 16rpx;
  padding-top: 24rpx;
  border-top: 2rpx solid var(--border);
}
.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.summary-label {
  font-size: 24rpx;
  color: var(--text-400);
}
.summary-value {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.summary-value.primary {
  color: var(--primary);
  font-weight: 700;
}
.bottom-spacer {
  height: 60rpx;
}
</style>
