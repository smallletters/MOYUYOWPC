<template>
  <view class="subscribe-manage-detail">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">订阅管理</text>
      <view class="header-spacer" />
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 订阅总览卡片 -->
      <view class="overview-card">
        <view class="overview-row">
          <view class="overview-stat">
            <text class="overview-label">活跃订阅</text>
            <view class="overview-num-row">
              <text class="overview-num">{{ activeCount }}</text>
              <text class="overview-unit">个</text>
            </view>
          </view>
          <view class="overview-stat">
            <text class="overview-label">累计节省</text>
            <view class="overview-num-row">
              <text class="overview-num">$326</text>
            </view>
          </view>
          <view class="overview-badge">
            <text class="badge-emoji"><text class="luc luc-crown" /></text>
          </view>
        </view>
        <view class="overview-action" @click="viewAllSubscriptions">
          <text class="overview-action-text">查看全部订阅</text>
          <text class="overview-action-arrow"><text class="luc luc-chevron-right" /></text>
        </view>
      </view>

      <!-- 单个订阅详情卡片 -->
      <view class="detail-card">
        <view class="detail-padding">
          <!-- 商品信息 -->
          <view class="product-row">
            <view class="product-thumb">
              <text class="thumb-emoji"><text class="luc luc-package" /></text>
            </view>
            <view class="product-info">
              <text class="product-name">{{ subscription.name }}</text>
              <text class="product-spec">{{ subscription.spec }}</text>
              <view class="product-tags">
                <text class="status-active">
                  <text class="luc luc-check" />
                  活跃
                </text>
                <text class="sub-id">{{ subscription.subId }}</text>
              </view>
            </view>
          </view>

          <view class="card-divider" />

          <!-- 订阅周期切换 -->
          <view class="cycle-section">
            <text class="cycle-label">订阅周期</text>
            <view class="cycle-btns">
              <view
                v-for="c in cycles"
                :key="c.key"
                class="cycle-btn"
                :class="{ active: selectedCycle === c.key }"
                @click="selectedCycle = c.key"
              >
                <text
                  class="cycle-btn-text"
                  :class="{ 'cycle-btn-active-text': selectedCycle === c.key }"
                >
                  {{ c.label }}
                </text>
              </view>
            </view>
          </view>

          <view class="card-divider" />

          <!-- 配送频率 -->
          <view class="info-row" @click="editDeliveryDate">
            <view class="info-left">
              <text class="info-label">配送频率</text>
              <text class="info-value">{{ subscription.deliveryFreq }}</text>
            </view>
            <text class="info-edit"><text class="luc luc-pencil" /></text>
          </view>

          <!-- 下次配送 + 倒计时 -->
          <view class="info-row">
            <view class="info-left">
              <text class="info-label">下次配送</text>
              <text class="info-value">{{ subscription.nextDelivery }}</text>
            </view>
            <view class="countdown">
              <text class="countdown-num">{{ countdown.days }}</text>
              <text class="countdown-sep">:</text>
              <text class="countdown-num">{{ countdown.hours }}</text>
              <text class="countdown-sep">:</text>
              <text class="countdown-num">{{ countdown.minutes }}</text>
            </view>
          </view>
          <view class="countdown-units">
            <text class="countdown-unit">天</text>
            <text class="countdown-unit">时</text>
            <text class="countdown-unit">分</text>
          </view>

          <view class="card-divider" style="margin-top: 16rpx" />

          <!-- 价格信息 -->
          <view class="price-row">
            <view class="price-left">
              <text class="price-discount">${{ subscription.discountPrice }}</text>
              <text class="discount-badge">10% OFF</text>
            </view>
            <view class="price-right">
              <text class="price-original">${{ subscription.originalPrice }}</text>
              <text class="price-original-label">原价</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 配送管理操作区 -->
      <view class="manage-section">
        <text class="manage-title">配送管理</text>
        <view class="manage-grid">
          <view class="manage-item" @click="skipDelivery">
            <view class="manage-icon" style="background: rgba(219, 201, 138, 0.15)">
              <text>⏩</text>
            </view>
            <text class="manage-label">跳过本次</text>
          </view>
          <view class="manage-item" @click="earlyDelivery">
            <view class="manage-icon" style="background: rgba(171, 185, 173, 0.2)">
              <text><text class="luc luc-alert-triangle" /></text>
            </view>
            <text class="manage-label">提前配送</text>
          </view>
          <view class="manage-item" @click="changeProduct">
            <view class="manage-icon" style="background: rgba(255, 149, 0, 0.1)">
              <text><text class="luc luc-refresh-cw" /></text>
            </view>
            <text class="manage-label">更换商品</text>
          </view>
          <view class="manage-item" @click="modifyDate">
            <view class="manage-icon" style="background: rgba(0, 0, 0, 0.04)">
              <text><text class="luc luc-calendar" /></text>
            </view>
            <text class="manage-label">改配送日</text>
          </view>
        </view>
      </view>

      <!-- 配送历史（折叠区域） -->
      <view class="history-card">
        <view class="history-toggle" @click="toggleHistory">
          <view class="history-toggle-left">
            <text class="history-toggle-icon"><text class="luc luc-clock" /></text>
            <text class="history-toggle-title">配送历史</text>
            <text class="history-count-badge">{{ deliveryHistory.length }} 次</text>
          </view>
          <text class="chevron" :class="{ rotated: historyExpanded }">▼</text>
        </view>

        <view v-if="historyExpanded" class="history-list">
          <view v-for="(item, i) in deliveryHistory" :key="i" class="history-item">
            <view class="history-item-left">
              <text class="history-item-name">{{ item.name }}</text>
              <text class="history-item-date">{{ item.date }}</text>
            </view>
            <view class="history-item-right">
              <text class="history-item-price">${{ item.price }}</text>
              <text class="history-item-status" :class="'status-' + item.status">
                {{ item.statusText }}
              </text>
            </view>
          </view>
        </view>
      </view>

      <!-- 合规信息 -->
      <view class="compliance-box">
        <view class="compliance-row">
          <text class="compliance-icon">ℹ</text>
          <view class="compliance-content">
            <text class="compliance-title">自动续订条款</text>
            <text class="compliance-desc">
              您的订阅将按所选周期自动续订并扣款。每次续订前 24 小时我们将发送提醒通知。订阅价享受
              10% 优惠折扣，取消订阅后优惠即刻失效。
            </text>
          </view>
        </view>
        <view class="cancel-btn" @click="cancelSubscription">
          <text class="cancel-icon"><text class="luc luc-x" /></text>
          <text class="cancel-text">取消订阅</text>
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

// 订阅详情 mock
const subscription = ref({
  name: '高端猫粮 成猫专用',
  spec: '鸡肉配方 / 2kg 装',
  subId: '订阅号 SUB20250101001',
  deliveryFreq: '每月 15 号配送',
  nextDelivery: '2026年7月15日',
  discountPrice: '170.10',
  originalPrice: '189.00',
})

// 活跃订阅数
const activeCount = ref(2)

// 选中周期
const selectedCycle = ref('bimonthly')

// 周期选项
const cycles = [
  { key: 'monthly', label: '月付' },
  { key: 'bimonthly', label: '双月付' },
  { key: 'quarterly', label: '季付' },
]

// 倒计时 mock
const countdown = ref({ days: '7', hours: '12', minutes: '36' })

// 历史区域折叠
const historyExpanded = ref(true)

// 配送历史 mock
const deliveryHistory = ref([
  {
    name: '高端猫粮 成猫专用',
    date: '2026-06-15',
    price: '170.10',
    status: 'delivered',
    statusText: '已配送',
  },
  {
    name: '高端猫粮 成猫专用',
    date: '2026-05-15',
    price: '170.10',
    status: 'delivered',
    statusText: '已配送',
  },
  {
    name: '高端猫粮 成猫专用',
    date: '2026-04-15',
    price: '170.10',
    status: 'delivered',
    statusText: '已配送',
  },
  {
    name: '高端猫粮 成猫专用',
    date: '2026-03-15',
    price: '170.10',
    status: 'skipped',
    statusText: '已跳过',
  },
  {
    name: '高端猫粮 成猫专用',
    date: '2026-02-15',
    price: '170.10',
    status: 'delivered',
    statusText: '已配送',
  },
  {
    name: '高端猫粮 成猫专用',
    date: '2026-01-15',
    price: '170.10',
    status: 'delivered',
    statusText: '已配送',
  },
])

// 返回
const goBack = () => {
  uni.navigateBack()
}

// 查看全部订阅
const viewAllSubscriptions = () => {
  uni.navigateTo({ url: '/pages/user/subscription-manage' })
}

// 编辑配送日期
const editDeliveryDate = () => {
  uni.showToast({ title: '修改配送频率', icon: 'none' })
}

// 配送管理操作
const skipDelivery = () => {
  uni.showToast({ title: '已跳过本次', icon: 'success' })
}

const earlyDelivery = () => {
  uni.showToast({ title: '已申请提前配送', icon: 'success' })
}

const changeProduct = () => {
  uni.showToast({ title: '更换商品', icon: 'none' })
}

const modifyDate = () => {
  uni.showToast({ title: '修改配送日期', icon: 'none' })
}

// 切换历史
const toggleHistory = () => {
  historyExpanded.value = !historyExpanded.value
}

// 取消订阅
const cancelSubscription = () => {
  uni.showModal({
    title: '取消订阅',
    content: '确定要取消此订阅吗？取消后优惠即刻失效。',
    confirmColor: '#C96E5F',
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: '订阅已取消', icon: 'success' })
      }
    },
  })
}
</script>

<style lang="scss" scoped>
.subscribe-manage-detail {
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
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
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
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.04);
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
  height: 48rpx;
}

/* 概览卡片 */
.overview-card {
  margin-top: 32rpx;
  padding: 40rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, var(--color-primary), #c4a85a);
  color: #ffffff;
}

.overview-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.overview-stat {
  display: flex;
  flex-direction: column;
}

.overview-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.7);
}

.overview-num-row {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
  margin-top: 8rpx;
}

.overview-num {
  font-size: 44rpx;
  font-weight: 700;
  color: #ffffff;
}

.overview-unit {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.7);
}

.overview-badge {
  width: 80rpx;
  height: 80rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
}

.badge-emoji {
  font-size: 36rpx;
}

.overview-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 80rpx;
  margin-top: 32rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.15);
}

.overview-action-text {
  font-size: 24rpx;
  font-weight: 600;
  color: #ffffff;
}

.overview-action-arrow {
  font-size: 28rpx;
  color: #ffffff;
  margin-left: 8rpx;
}

/* 详情卡片 */
.detail-card {
  margin-top: 32rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
  overflow: hidden;
}

.detail-padding {
  padding: 32rpx;
}

.product-row {
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
}

.product-thumb {
  width: 144rpx;
  height: 144rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, var(--color-background), rgba(0, 0, 0, 0.06));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1rpx solid var(--color-border);
}

.thumb-emoji {
  font-size: 44rpx;
  opacity: 0.4;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
}

.product-spec {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 4rpx;
}

.product-tags {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
}

.status-active {
  font-size: 22rpx;
  font-weight: 600;
  color: var(--color-success);
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(171, 185, 173, 0.2);
}

.sub-id {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.card-divider {
  height: 1rpx;
  background: var(--color-border);
  margin: 32rpx 0;
}

/* 周期切换 */
.cycle-section {
  display: flex;
  flex-direction: column;
}

.cycle-label {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 20rpx;
}

.cycle-btns {
  display: flex;
  gap: 16rpx;
}

.cycle-btn {
  padding: 12rpx 32rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.04);
  border: none;
}

.cycle-btn.active {
  background: var(--color-primary);
}

.cycle-btn-text {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.cycle-btn-active-text {
  color: #ffffff;
}

/* 信息行 */
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 0;
}

.info-left {
  display: flex;
  flex-direction: column;
}

.info-label {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.info-value {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--color-text);
  margin-top: 4rpx;
}

.info-edit {
  font-size: 28rpx;
}

/* 倒计时 */
.countdown {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.countdown-num {
  min-width: 56rpx;
  height: 56rpx;
  padding: 0 12rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.2);
  border: 1rpx solid var(--color-border);
  font-size: 26rpx;
  font-weight: 700;
  color: var(--color-text);
  display: flex;
  align-items: center;
  justify-content: center;
}

.countdown-sep {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.countdown-units {
  display: flex;
  justify-content: flex-end;
  gap: 24rpx;
  margin-top: -8rpx;
  padding-right: 0;
}

.countdown-unit {
  font-size: 20rpx;
  color: var(--color-text-secondary);
  width: 56rpx;
  text-align: center;
}

/* 价格行 */
.price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 0;
}

.price-left {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}

.price-discount {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--color-primary);
}

.discount-badge {
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(171, 185, 173, 0.2);
  color: var(--color-success);
  font-size: 22rpx;
  font-weight: 700;
}

.price-right {
  text-align: right;
}

.price-original {
  font-size: 28rpx;
  text-decoration: line-through;
  color: var(--color-text-secondary);
}

.price-original-label {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 4rpx;
  display: block;
}

/* 配送管理 */
.manage-section {
  margin-top: 32rpx;
}

.manage-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 24rpx;
  padding-left: 8rpx;
}

.manage-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
}

.manage-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 32rpx 16rpx;
  border-radius: 24rpx;
  background: rgba(0, 0, 0, 0.04);
}

.manage-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
}

.manage-label {
  font-size: 22rpx;
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
}

/* 历史卡片 */
.history-card {
  margin-top: 32rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
  overflow: hidden;
}

.history-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
}

.history-toggle-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.history-toggle-icon {
  font-size: 28rpx;
}

.history-toggle-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.history-count-badge {
  font-size: 24rpx;
  font-weight: 500;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.04);
  color: var(--color-text-secondary);
}

.chevron {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  transition: transform 0.3s;
}

.chevron.rotated {
  transform: rotate(180deg);
}

.history-list {
  padding: 0 32rpx 16rpx;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 0;
  border-bottom: 1rpx solid var(--color-border);
}

.history-item:last-child {
  border-bottom: none;
}

.history-item-left {
  flex: 1;
  min-width: 0;
}

.history-item-name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--color-text);
}

.history-item-date {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 4rpx;
}

.history-item-right {
  text-align: right;
  flex-shrink: 0;
  margin-left: 24rpx;
}

.history-item-price {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.history-item-status {
  font-size: 24rpx;
  margin-top: 4rpx;
}

.status-delivered {
  color: var(--color-success);
}

.status-skipped {
  color: var(--color-primary);
}

/* 合规信息 */
.compliance-box {
  margin-top: 32rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: rgba(0, 0, 0, 0.04);
}

.compliance-row {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
}

.compliance-icon {
  font-size: 28rpx;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.compliance-content {
  flex: 1;
}

.compliance-title {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.compliance-desc {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 12rpx;
  line-height: 1.6;
}

.cancel-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 88rpx;
  margin-top: 24rpx;
  border-radius: 20rpx;
  background: rgba(201, 110, 95, 0.1);
  gap: 12rpx;
}

.cancel-icon {
  font-size: 28rpx;
  color: var(--color-error);
}

.cancel-text {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-error);
}
</style>
