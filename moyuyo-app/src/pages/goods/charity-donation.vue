<template>
  <view class="charity-donation">
    <!-- 顶部导航栏 -->
    <view class="nav-header">
      <view class="nav-back" @tap="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="nav-title">为流浪动物献爱心</text>
      <view class="nav-placeholder" />
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 爱心横幅卡片 -->
      <view class="charity-banner">
        <view class="banner-top">
          <view class="banner-icon">
            <text class="luc luc-heart" />
          </view>
          <text class="banner-title">每一份爱，都不算少</text>
        </view>
        <text class="banner-desc">
          您的每一笔微小捐赠，都能为流浪动物带来温暖与希望。MOYUYO 将 100%
          捐赠金额直接转交合作救助机构。
        </text>
      </view>

      <!-- 当前订单信息 -->
      <view class="order-card">
        <view class="order-label">
          <text class="luc luc-shopping-bag" />
          <text>当前订单</text>
        </view>
        <view class="order-row">
          <text class="order-text">已选商品 3 件</text>
          <text class="order-amount">$67.30</text>
        </view>
      </view>

      <!-- 捐赠方式选择 -->
      <view class="donation-section">
        <text class="section-title">选择捐赠方式</text>

        <!-- 凑整捐赠（推荐） -->
        <view
          class="donation-card"
          :class="{ selected: currentOption === 'roundup' }"
          @tap="selectOption('roundup')"
        >
          <view class="donation-top">
            <view class="donation-left">
              <view class="donation-icon bg-roundup">
                <text class="luc luc-arrow-up" />
              </view>
              <view>
                <view class="donation-name-row">
                  <text class="donation-name">凑整捐赠</text>
                  <text class="donation-recommend">推荐</text>
                </view>
                <text class="donation-sub">Round up to the next dollar</text>
              </view>
            </view>
            <view class="donation-right">
              <text class="donation-amount">$0.70</text>
              <text class="donation-sub">凑整至 $68.00</text>
            </view>
          </view>
        </view>

        <!-- 固定金额 -->
        <view
          class="donation-card"
          :class="{ selected: currentOption === 'fixed' }"
          @tap="selectOption('fixed')"
        >
          <view class="donation-top">
            <view class="donation-left">
              <view class="donation-icon bg-fixed">
                <text class="luc luc-coins" />
              </view>
              <text class="donation-name">固定金额</text>
            </view>
          </view>
          <view class="amount-btns">
            <view
              v-for="amt in fixedAmounts"
              :key="amt"
              class="amount-btn"
              :class="{ selected: currentFixedAmount === amt && currentOption === 'fixed' }"
              @tap.stop="selectFixedAmount(amt)"
            >
              ${{ amt }}
            </view>
          </view>
        </view>

        <!-- 自定义金额 -->
        <view
          class="donation-card"
          :class="{ selected: currentOption === 'custom' }"
          @tap="selectOption('custom')"
        >
          <view class="donation-top">
            <view class="donation-left">
              <view class="donation-icon bg-custom">
                <text class="luc luc-pencil" />
              </view>
              <text class="donation-name">自定义金额</text>
            </view>
          </view>
          <view class="custom-input-row">
            <text class="dollar-sign">$</text>
            <input
              class="custom-input"
              type="number"
              placeholder="输入金额"
              :value="customAmount"
              @input="onCustomInput"
              @tap.stop
            >
          </view>
        </view>
      </view>

      <!-- 每次自动凑整捐赠开关 -->
      <view class="auto-toggle-card">
        <view class="toggle-left">
          <text class="luc luc-repeat" />
          <view>
            <text class="toggle-title">每次自动凑整捐赠</text>
            <text class="toggle-desc">开启后后续下单自动凑整</text>
          </view>
        </view>
        <view class="toggle-switch" :class="{ active: autoRoundup }" @tap="toggleAutoRoundup" />
      </view>

      <!-- 合作救助机构 -->
      <view class="org-section">
        <text class="section-title">合作救助机构</text>
        <view v-for="(org, i) in orgList" :key="i" class="org-card">
          <view class="org-icon" :class="org.iconClass">
            <text class="luc" :class="$luc(org.icon)" />
          </view>
          <view class="org-info">
            <text class="org-name">{{ org.name }}</text>
            <text class="org-desc">{{ org.desc }}</text>
          </view>
          <text class="luc luc-external-link" />
        </view>
      </view>

      <!-- 累计捐赠信息 -->
      <view class="impact-card">
        <view class="impact-top">
          <text class="luc luc-award" />
          <text class="impact-label">您的爱心记录</text>
        </view>
        <text class="impact-text">
          您已累计捐赠
          <text class="impact-highlight">$23.70</text>
          ，帮助了
          <text class="impact-highlight">5</text>
          只流浪动物
        </text>
        <text class="impact-hint">捐赠完成后可获取电子收据，可用于抵税</text>
      </view>

      <!-- 捐赠说明 -->
      <view class="info-card">
        <text class="iconfont icon-info info-icon" />
        <text class="info-text">
          捐赠金额将与订单一并支付。捐赠证书（电子收据）将在支付完成后发送至您的邮箱，可作为税务抵扣凭证。MOYUYO
          承诺将 100% 捐赠金额转交合作机构。
        </text>
      </view>

      <view class="bottom-safe" />
    </scroll-view>

    <!-- 底部操作按钮 -->
    <view class="bottom-bar">
      <view class="bar-top">
        <text class="bar-label">捐赠 + 订单总额</text>
        <text class="bar-total">${{ totalAmount }}</text>
      </view>
      <view class="confirm-btn" @tap="handleConfirm">
        <text class="luc luc-heart" />
        <text>确认捐赠并继续结算</text>
      </view>
    </view>
  </view>
</template>

<script>
import { memberApi } from '@/api'

export default {
  data() {
    return {
      currentOption: 'roundup',
      currentFixedAmount: 0,
      customAmount: '',
      autoRoundup: false,
      orderAmount: 67.3,
      donationAmount: 0.7,
      fixedAmounts: [1, 3, 5],
      orgList: [
        {
          name: 'ASPCA',
          desc: '美国防止虐待动物协会',
          icon: 'shield',
          iconClass: 'org-ascpa',
        },
        {
          name: 'RSPCA',
          desc: '英国皇家防止虐待动物协会',
          icon: 'handshake',
          iconClass: 'org-rspca',
        },
        {
          name: '当地动物救助组织',
          desc: '支持您所在社区的救助站',
          icon: 'map-pin',
          iconClass: 'org-local',
        },
      ],
    }
  },
  computed: {
    totalAmount() {
      return (this.orderAmount + this.donationAmount).toFixed(2)
    },
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },

    selectOption(option) {
      this.currentOption = option
      this.updateDonation()
    },

    selectFixedAmount(amount) {
      this.currentFixedAmount = amount
      this.currentOption = 'fixed'
      this.donationAmount = amount
    },

    onCustomInput(e) {
      this.customAmount = e.detail.value
      const val = parseFloat(this.customAmount)
      this.donationAmount = isNaN(val) || val < 0 ? 0 : val
    },

    updateDonation() {
      if (this.currentOption === 'roundup') {
        this.donationAmount = 0.7
      } else if (this.currentOption === 'fixed') {
        this.donationAmount = this.currentFixedAmount || 0
      } else if (this.currentOption === 'custom') {
        const val = parseFloat(this.customAmount)
        this.donationAmount = isNaN(val) || val < 0 ? 0 : val
      }
    },

    toggleAutoRoundup() {
      this.autoRoundup = !this.autoRoundup
    },

    async handleConfirm() {
      try {
        await memberApi.submitDonation({
          option: this.currentOption,
          amount: this.donationAmount,
          orderAmount: this.orderAmount,
          autoRoundup: this.autoRoundup,
        })
        uni.showToast({ title: `捐赠 $${this.donationAmount.toFixed(2)} 并结算`, icon: 'success' })
      } catch (err) {
        uni.showToast({ title: err.message || '捐赠失败', icon: 'none' })
      }
    },
  },
}
</script>

<style scoped>
.charity-donation {
  min-height: 100vh;
  background: var(--background);
}
.nav-header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--background-800);
}
.nav-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  color: var(--primary-foreground);
}
.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--background-50);
}
.nav-placeholder {
  width: 64rpx;
  height: 64rpx;
}
.scroll {
  padding: 32rpx 32rpx 180rpx;
}

/* 爱心横幅 */
.charity-banner {
  background: linear-gradient(135deg, #fff5ee 0%, #ffe8d6 40%, #ffdab9 100%);
  border-radius: 36rpx;
  padding: 40rpx;
  margin-bottom: 32rpx;
}
.banner-top {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.banner-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ff8c42, #ff6b35);
}
.banner-icon .iconfont {
  color: #fff;
  font-size: 40rpx;
}
.banner-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text-800);
}
.banner-desc {
  font-size: 28rpx;
  color: var(--text-600);
  line-height: 1.6;
}

/* 订单卡片 */
.order-card {
  padding: 32rpx;
  border-radius: 36rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  margin-bottom: 32rpx;
}
.order-label {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.order-label .iconfont {
  color: var(--icon-muted);
}
.order-label text:last-child {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
}
.order-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.order-text {
  font-size: 28rpx;
  color: var(--muted-foreground);
}
.order-amount {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--foreground);
}

/* 捐赠方式 */
.donation-section {
  margin-bottom: 32rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
  margin-bottom: 24rpx;
}
.donation-card {
  padding: 32rpx;
  border-radius: 36rpx;
  background: var(--card);
  border: 4rpx solid var(--border);
  margin-bottom: 24rpx;
}
.donation-card.selected {
  background: var(--brand-50);
  border-color: var(--primary);
}
.donation-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.donation-left {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.donation-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.bg-roundup {
  background: linear-gradient(135deg, #c2e0ff, #a8d0ff);
}
.bg-roundup .iconfont {
  color: var(--primary);
}
.bg-fixed {
  background: var(--secondary);
}
.bg-fixed .iconfont {
  color: var(--icon-muted);
}
.bg-custom {
  background: var(--secondary);
}
.bg-custom .iconfont {
  color: var(--icon-muted);
}
.donation-icon .iconfont {
  font-size: 44rpx;
}
.donation-name-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.donation-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.donation-recommend {
  font-size: 24rpx;
  font-weight: 500;
  padding: 4rpx 12rpx;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
}
.donation-sub {
  font-size: 24rpx;
  color: var(--muted-foreground);
  display: block;
  margin-top: 4rpx;
}
.donation-right {
  text-align: right;
}
.donation-amount {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--primary);
  display: block;
}
.amount-btns {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}
.amount-btn {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
  font-size: 28rpx;
  font-weight: 600;
  border: 3rpx solid var(--border);
  background: var(--background);
  color: var(--foreground);
}
.amount-btn.selected {
  background: var(--primary);
  color: var(--primary-foreground);
  border-color: var(--primary);
}
.custom-input-row {
  display: flex;
  align-items: center;
  height: 88rpx;
  border: 3rpx solid var(--border);
  border-radius: 24rpx;
  background: var(--background);
  padding: 0 28rpx;
  margin-top: 24rpx;
}
.dollar-sign {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--muted-foreground);
  margin-right: 8rpx;
}
.custom-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
}

/* 自动凑整开关 */
.auto-toggle-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  border-radius: 36rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  margin-bottom: 32rpx;
}
.toggle-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.toggle-left .iconfont {
  color: var(--icon-muted);
  font-size: 36rpx;
}
.toggle-title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
  display: block;
}
.toggle-desc {
  font-size: 24rpx;
  color: var(--muted-foreground);
  display: block;
  margin-top: 4rpx;
}
.toggle-switch {
  width: 102rpx;
  height: 62rpx;
  border-radius: 32rpx;
  background: var(--background-400);
  position: relative;
  cursor: pointer;
  transition: background-color 0.3s ease;
}
.toggle-switch::after {
  content: '';
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 54rpx;
  height: 54rpx;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.3s ease;
}
.toggle-switch.active {
  background: var(--success);
}
.toggle-switch.active::after {
  transform: translateX(40rpx);
}

/* 合作机构 */
.org-section {
  margin-bottom: 32rpx;
}
.org-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx;
  border-radius: 36rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  margin-bottom: 24rpx;
}
.org-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.org-icon .iconfont {
  color: #fff;
  font-size: 48rpx;
}
.org-ascpa {
  background: linear-gradient(135deg, #ff8c42, #ff6b35);
}
.org-rspca {
  background: linear-gradient(135deg, #f4845f, #e76f51);
}
.org-local {
  background: linear-gradient(135deg, #ffb494, #ff9a76);
}
.org-info {
  flex: 1;
  min-width: 0;
}
.org-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
}
.org-desc {
  font-size: 24rpx;
  color: var(--muted-foreground);
  display: block;
  margin-top: 4rpx;
}
.org-section .iconfont {
  color: var(--icon-muted);
  font-size: 32rpx;
}

/* 累计捐赠 */
.impact-card {
  padding: 32rpx;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #fff9f5, #fff0e8);
  border: 2rpx solid #ffe0cc;
  margin-bottom: 32rpx;
}
.impact-top {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.impact-top .iconfont {
  color: #ff8c42;
  font-size: 36rpx;
}
.impact-label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-700);
}
.impact-text {
  font-size: 28rpx;
  color: var(--text-600);
  line-height: 1.6;
}
.impact-highlight {
  font-weight: 700;
  color: var(--text-800);
}
.impact-hint {
  font-size: 24rpx;
  color: var(--muted-foreground);
  display: block;
  margin-top: 16rpx;
}

/* 捐赠说明 */
.info-card {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 32rpx;
  border-radius: 36rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
}
.info-icon {
  color: var(--icon-muted);
  font-size: 32rpx;
  flex-shrink: 0;
  margin-top: 4rpx;
}
.info-text {
  font-size: 24rpx;
  color: var(--muted-foreground);
  line-height: 1.6;
}

.bottom-safe {
  height: 32rpx;
}

/* 底部操作栏 */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 750rpx;
  padding: 24rpx 32rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background: linear-gradient(to top, var(--background) 60%, transparent);
}
.bar-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
  padding: 0 8rpx;
}
.bar-label {
  font-size: 24rpx;
  color: var(--muted-foreground);
}
.bar-total {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--foreground);
}
.confirm-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  height: 96rpx;
  border-radius: 32rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 32rpx;
  font-weight: 600;
  box-shadow: 0 8rpx 28rpx rgba(0, 122, 255, 0.3);
}
</style>
