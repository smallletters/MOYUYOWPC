<template>
  <view class="splash-page">
    <!-- 简化的首页占位背景 -->
    <view class="home-placeholder">
      <text class="home-emoji"><text class="luc luc-home"></text></text>
      <text class="home-text">MOYUYO 首页</text>
    </view>

    <!-- 开屏营销弹窗 -->
    <view v-if="popupVisible" class="splash-overlay" @tap="onOverlayTap">
      <!-- 弹窗卡片（阻止冒泡） -->
      <view class="popup-card" @tap.stop>
        <!-- 关闭按钮 -->
        <view class="close-btn" @tap="closePopup('')">
          <text class="close-icon"><text class="luc luc-x"></text></text>
        </view>

        <!-- 顶部营销图片区 -->
        <view class="popup-hero">
          <!-- 装饰元素 -->
          <view class="deco-circle deco-circle-sm" />
          <view class="deco-circle deco-circle-lg" />

          <!-- 活动标签 -->
          <view class="campaign-tag">
            <text class="campaign-tag-text">SUMMER SALE</text>
          </view>
          <!-- 活动主标题 -->
          <text class="campaign-title">夏日限定</text>
          <text class="campaign-title">全场 8 折</text>
        </view>

        <!-- 弹窗正文区域 -->
        <view class="popup-body">
          <!-- 弹窗描述 -->
          <text class="popup-desc">夏日清凉季，精选洗护/出行/服饰商品限时折扣，不容错过！</text>

          <!-- 优惠券预览卡片 -->
          <view class="coupon-card">
            <!-- 左侧金额区 -->
            <view class="coupon-left">
              <text class="coupon-condition">满$50减</text>
              <text class="coupon-amount">$10</text>
            </view>
            <!-- 右侧说明区 -->
            <view class="coupon-right">
              <text class="coupon-name">夏日专享优惠券</text>
              <text class="coupon-limit">全场通用 / 限领 1 张</text>
            </view>
            <!-- 领取标记 -->
            <view class="coupon-mark">
              <text class="coupon-mark-icon"><text class="luc luc-ticket"></text></text>
            </view>
          </view>

          <!-- 操作按钮 -->
          <view class="popup-actions">
            <!-- 主按钮：立即领取 -->
            <view class="action-primary" @tap="claimCoupon">
              <text class="action-icon"><text class="luc luc-gift"></text></text>
              <text class="action-primary-text">立即领取</text>
            </view>

            <!-- 次级按钮：暂不需要 -->
            <view class="action-secondary" @tap="closePopup('')">
              <text class="action-secondary-text">暂不需要</text>
            </view>
          </view>

          <!-- 不再显示链接 -->
          <view class="never-show" @tap="neverShowAgain">
            <text class="never-show-text">不再显示此活动</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Toast 提示 -->
    <view v-if="toastVisible" class="toast-popup">
      <text class="toast-popup-text">{{ toastMessage }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

// 弹窗是否可见
const popupVisible = ref(true)

// Toast 提示
const toastVisible = ref(false)
const toastMessage = ref('')

// 显示 Toast 提示
const showToast = (message, duration = 2500) => {
  toastMessage.value = message
  toastVisible.value = true
  setTimeout(() => {
    toastVisible.value = false
  }, duration)
}

// 关闭弹窗
const closePopup = (message) => {
  popupVisible.value = false
  if (message) {
    showToast(message)
  }
}

// 点击遮罩关闭
const onOverlayTap = () => {
  closePopup('')
}

// 领取优惠券
const claimCoupon = () => {
  closePopup('优惠券已领取，下单时自动抵扣')
}

// 不再显示此活动
const neverShowAgain = () => {
  // 存入本地存储，后续不再展示
  try {
    const dismissed = uni.getStorageSync('moyuyo_dismissed_campaigns') || '[]'
    const list = JSON.parse(dismissed)
    list.push('summer_sale_2025')
    uni.setStorageSync('moyuyo_dismissed_campaigns', JSON.stringify(list))
  } catch (e) {
    // 忽略存储错误
  }
  closePopup('已关闭，后续将不再展示此活动')
}
</script>

<style lang="scss" scoped>
.splash-page {
  position: relative;
  height: 100vh;
}

/* 首页占位 */
.home-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 32rpx;
}

.home-emoji {
  font-size: 96rpx;
}

.home-text {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

/* 遮罩层 */
.splash-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
}

/* 弹窗卡片 */
.popup-card {
  position: relative;
  width: 640rpx;
  max-width: 640rpx;
  border-radius: 32rpx;
  overflow: hidden;
  background: var(--color-card);
}

/* 关闭按钮 */
.close-btn {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  z-index: 10;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.25);
}

.close-icon {
  font-size: 36rpx;
  color: #ffffff;
}

/* 顶部营销区域 */
.popup-hero {
  position: relative;
  height: 400rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  background: linear-gradient(135deg, var(--color-primary) 0%, #b8963f 50%, #8b6f2a 100%);
  padding: 0 48rpx;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
}

.deco-circle-sm {
  top: 32rpx;
  left: 32rpx;
  width: 128rpx;
  height: 128rpx;
}

.deco-circle-lg {
  bottom: 48rpx;
  right: 48rpx;
  width: 192rpx;
  height: 192rpx;
  opacity: 0.1;
}

.campaign-tag {
  padding: 8rpx 24rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.2);
  margin-bottom: 16rpx;
}

.campaign-tag-text {
  font-size: 24rpx;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 0.06em;
}

.campaign-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.3;
}

/* 弹窗正文 */
.popup-body {
  padding: 40rpx 48rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.popup-desc {
  font-size: 28rpx;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

/* 优惠券卡片 */
.coupon-card {
  position: relative;
  display: flex;
  align-items: center;
  overflow: hidden;
  border-radius: 24rpx;
  border: 3rpx solid var(--color-error);
  background: rgba(201, 110, 95, 0.08);
}

.coupon-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24rpx 32rpx;
  min-width: 160rpx;
  border-right: 3rpx dashed var(--color-error);
}

.coupon-condition {
  font-size: 22rpx;
  font-weight: 500;
  color: var(--color-error);
}

.coupon-amount {
  font-size: 44rpx;
  font-weight: 700;
  color: var(--color-error);
  line-height: 1.1;
}

.coupon-right {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 24rpx 32rpx;
  gap: 8rpx;
}

.coupon-name {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text);
}

.coupon-limit {
  font-size: 22rpx;
  color: var(--color-text-secondary);
}

.coupon-mark {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0.5;
}

.coupon-mark-icon {
  font-size: 36rpx;
}

/* 操作按钮 */
.popup-actions {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  padding-top: 8rpx;
}

.action-primary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  width: 100%;
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--color-primary);
}

.action-icon {
  font-size: 28rpx;
}

.action-primary-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}

.action-secondary {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 96rpx;
  border-radius: 24rpx;
  background: #f2f2f7;
}

.action-secondary-text {
  font-size: 32rpx;
  font-weight: 500;
  color: var(--color-text);
}

/* 不再显示 */
.never-show {
  display: flex;
  justify-content: center;
  padding-top: 8rpx;
}

.never-show-text {
  font-size: 24rpx;
  text-decoration: underline;
  color: var(--color-text-secondary);
}

/* Toast 提示 */
.toast-popup {
  position: fixed;
  bottom: 200rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 20rpx 40rpx;
  border-radius: 24rpx;
  background: var(--color-text);
  z-index: 9999;
}

.toast-popup-text {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--color-background);
}
</style>
