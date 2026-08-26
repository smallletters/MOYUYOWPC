<template>
  <view class="subscribe-plan">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">订阅计划</text>
      <view class="header-spacer" />
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 商品信息摘要 -->
      <view class="product-summary">
        <image :src="product.image" class="product-image" mode="aspectFill" />
        <view class="product-info">
          <text class="product-name">{{ product.name }}</text>
          <text class="product-price">¥{{ product.price }}</text>
          <text class="product-price-label">单次购买价</text>
        </view>
      </view>

      <!-- Subscribe & Save 标题 -->
      <view class="subscribe-header">
        <view class="subscribe-title-row">
          <text class="subscribe-title">Subscribe & Save</text>
          <text class="save-badge">省10%</text>
        </view>
        <text class="subscribe-desc">选择配送周期，定期自动送达，享受订阅专属折扣</text>
      </view>

      <!-- 订阅周期选择 -->
      <view class="plan-list">
        <view
          v-for="plan in plans"
          :key="plan.key"
          class="plan-card"
          :class="{ selected: selectedPlan === plan.key }"
          @click="selectedPlan = plan.key"
        >
          <!-- 推荐标签 -->
          <view v-if="plan.recommend" class="recommend-badge">
            <text class="recommend-text">推荐</text>
          </view>

          <!-- 自定义 radio -->
          <view class="plan-radio">
            <view v-if="selectedPlan === plan.key" class="plan-radio-dot" />
          </view>

          <view class="plan-content">
            <view class="plan-name-row">
              <text class="plan-name">{{ plan.name }}</text>
              <text class="plan-save">省{{ plan.discount }}%</text>
            </view>
            <text class="plan-price">
              ¥{{ plan.price }}
              <text class="plan-period">/{{ plan.periodLabel }}</text>
            </text>
            <!-- 额外权益 -->
            <view v-if="plan.benefits.length" class="plan-benefits">
              <view v-for="(b, i) in plan.benefits" :key="i" class="benefit-item">
                <text class="benefit-icon"><text class="luc luc-check"></text></text>
                <text class="benefit-text">{{ b }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 订阅权益说明 -->
      <view class="benefits-section">
        <text class="section-title">订阅权益</text>
        <view class="benefits-card">
          <view v-for="(b, i) in subscribeBenefits" :key="i" class="benefit-row">
            <text class="benefit-check"><text class="luc luc-check"></text></text>
            <text class="benefit-desc">{{ b }}</text>
          </view>
        </view>
      </view>

      <!-- 配送信息 -->
      <view class="delivery-section">
        <text class="section-title">配送信息</text>
        <view class="delivery-card">
          <!-- 首次配送日期 -->
          <view class="delivery-row" @click="showDatePicker">
            <view class="delivery-left">
              <text class="delivery-icon"><text class="luc luc-map-pin"></text></text>
              <text class="delivery-label">首次配送日期</text>
            </view>
            <view class="delivery-right">
              <text class="delivery-value">{{ firstDeliveryDate }}</text>
              <text class="delivery-arrow"><text class="luc luc-chevron-right"></text></text>
            </view>
          </view>
          <view class="delivery-divider" />
          <!-- 配送地址 -->
          <view class="delivery-row" @click="showAddressPicker">
            <view class="delivery-left">
              <text class="delivery-icon"><text class="luc luc-map-pin"></text></text>
              <text class="delivery-label">配送地址</text>
            </view>
            <view class="delivery-right">
              <text class="delivery-value">{{ deliveryAddress }}</text>
              <text class="delivery-arrow"><text class="luc luc-chevron-right"></text></text>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部占位 -->
      <view class="bottom-spacer" />
    </scroll-view>

    <!-- 底部固定 CTA -->
    <view class="bottom-bar">
      <view class="bottom-price-row">
        <text class="bottom-label">订阅总价</text>
        <text class="bottom-price">
          ¥{{ currentPlanPrice }}
          <text class="bottom-period">/{{ currentPlanPeriod }}</text>
        </text>
      </view>
      <button class="subscribe-btn" @click="onSubscribe">开始订阅</button>
      <text class="bottom-note">可随时取消，无额外费用</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'

// 商品信息 mock
const product = ref({
  name: 'MOYUYO 温和沐浴露 500ml',
  price: '28.00',
  image: '/static/images/product-shampoo.png',
})

// 选中的订阅计划
const selectedPlan = ref('monthly')

// 首次配送日期
const firstDeliveryDate = ref('2026-07-15')

// 配送地址
const deliveryAddress = ref('默认地址 - 北京市朝阳区')

// 订阅计划列表 mock
const plans = ref([
  {
    key: 'monthly',
    name: '每月配送',
    price: '25.20',
    periodLabel: '月',
    discount: 10,
    recommend: false,
    benefits: [],
  },
  {
    key: 'bimonthly',
    name: '双月配送',
    price: '50.40',
    periodLabel: '2月',
    discount: 10,
    recommend: false,
    benefits: ['免运费'],
  },
  {
    key: 'quarterly',
    name: '季度配送',
    price: '75.60',
    periodLabel: '3月',
    discount: 10,
    recommend: true,
    benefits: ['免运费', '赠品'],
  },
])

// 订阅权益说明
const subscribeBenefits = [
  '自动享 10% off',
  '免运费',
  '随时调整周期 / 暂停 / 取消',
  '提前 7 天续订提醒',
]

// 当前选中计划的价格
const currentPlanPrice = computed(() => {
  const plan = plans.value.find((p) => p.key === selectedPlan.value)
  return plan ? plan.price : '25.20'
})

// 当前选中计划的周期
const currentPlanPeriod = computed(() => {
  const plan = plans.value.find((p) => p.key === selectedPlan.value)
  return plan ? plan.periodLabel : '月'
})

// 返回上一页
const goBack = () => {
  uni.navigateBack()
}

// 选择配送日期
const showDatePicker = () => {
  uni.showToast({ title: '选择配送日期', icon: 'none' })
}

// 选择配送地址
const showAddressPicker = () => {
  uni.navigateTo({ url: '/pages/user/address' })
}

// 开始订阅
const onSubscribe = () => {
  uni.showModal({
    title: '确认订阅',
    content: `将以 ¥${currentPlanPrice.value}/${currentPlanPeriod.value} 开启订阅，可随时取消。`,
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: '订阅成功', icon: 'success' })
      }
    },
  })
}
</script>

<style lang="scss" scoped>
.subscribe-plan {
  min-height: 100vh;
  background: var(--color-background);
}

.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-background);
  border-bottom: 1rpx solid var(--color-border);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
}

.header-spacer {
  width: 72rpx;
}

.content {
  height: calc(100vh - 88rpx);
  padding: 0 32rpx;
}

.bottom-spacer {
  height: 280rpx;
}

/* 商品摘要 */
.product-summary {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 32rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.product-image {
  width: 128rpx;
  height: 128rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}

.product-info {
  display: flex;
  flex-direction: column;
}

.product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.product-price {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--color-text);
  margin-top: 8rpx;
}

.product-price-label {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 4rpx;
}

/* Subscribe & Save 标题区 */
.subscribe-header {
  margin-top: 40rpx;
}

.subscribe-title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.subscribe-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-text);
}

.save-badge {
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(219, 201, 138, 0.15);
  color: var(--color-primary);
  font-size: 22rpx;
  font-weight: 700;
}

.subscribe-desc {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 12rpx;
  line-height: 1.5;
}

/* 订阅计划列表 */
.plan-list {
  margin-top: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.plan-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 3rpx solid var(--color-border);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.plan-card.selected {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2rpx var(--color-primary);
}

.recommend-badge {
  position: absolute;
  top: -1rpx;
  right: 32rpx;
  padding: 4rpx 20rpx;
  border-radius: 0 0 16rpx 16rpx;
  background: var(--color-primary);
}

.recommend-text {
  font-size: 22rpx;
  font-weight: 700;
  color: #ffffff;
}

.plan-radio {
  width: 40rpx;
  height: 40rpx;
  border-radius: 999rpx;
  border: 4rpx solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: border-color 0.2s;
}

.plan-card.selected .plan-radio {
  border-color: var(--color-primary);
}

.plan-radio-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
}

.plan-content {
  flex: 1;
  min-width: 0;
}

.plan-name-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}

.plan-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.plan-save {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--color-primary);
}

.plan-price {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-text);
  margin-top: 8rpx;
}

.plan-period {
  font-size: 24rpx;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.plan-benefits {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 12rpx;
}

.benefit-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.benefit-icon {
  font-size: 24rpx;
  color: var(--color-success);
}

.benefit-text {
  font-size: 24rpx;
  color: var(--color-success);
  font-weight: 500;
}

/* 订阅权益 */
.benefits-section {
  margin-top: 40rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.benefits-card {
  margin-top: 20rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.benefit-row {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
}

.benefit-check {
  font-size: 28rpx;
  color: var(--color-success);
  flex-shrink: 0;
}

.benefit-desc {
  font-size: 28rpx;
  color: var(--color-text);
}

/* 配送信息 */
.delivery-section {
  margin-top: 40rpx;
}

.delivery-card {
  margin-top: 20rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.delivery-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.delivery-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.delivery-icon {
  font-size: 28rpx;
}

.delivery-label {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.delivery-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.delivery-value {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.delivery-arrow {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.delivery-divider {
  height: 1rpx;
  background: var(--color-border);
  margin: 24rpx 0;
}

/* 底部固定栏 */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 40;
  padding: 24rpx 32rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  border-top: 1rpx solid var(--color-border);
}

.bottom-price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.bottom-label {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.bottom-price {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--color-text);
}

.bottom-period {
  font-size: 24rpx;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.subscribe-btn {
  width: 100%;
  height: 96rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.subscribe-btn::after {
  border: none;
}

.bottom-note {
  display: block;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 16rpx;
}
</style>
