<template>
  <view class="subscription-manage">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">我的订阅</text>
      <view class="header-spacer" />
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 订阅概览卡片 -->
      <view class="overview-card">
        <view class="overview-stats">
          <view class="stat-item">
            <text class="stat-label">活跃订阅</text>
            <text class="stat-value">{{ activeCount }}</text>
          </view>
          <view class="stat-divider" />
          <view class="stat-item">
            <text class="stat-label">总节省</text>
            <text class="stat-value stat-success">¥{{ totalSaved }}</text>
          </view>
        </view>
        <view class="overview-icon">
          <text class="icon-emoji"><text class="luc luc-star"></text></text>
        </view>
      </view>

      <!-- 活跃订阅列表 -->
      <view class="section-header">
        <text class="section-title">活跃订阅</text>
      </view>

      <view v-for="item in activeSubscriptions" :key="item.id" class="sub-card">
        <!-- 商品信息行 -->
        <view class="sub-info">
          <image :src="item.image" class="sub-image" mode="aspectFill" />
          <view class="sub-detail">
            <view class="sub-name-row">
              <text class="sub-name">{{ item.name }}</text>
              <text class="status-tag" :class="'status-' + item.status">{{ item.statusText }}</text>
            </view>
            <text class="sub-spec">{{ item.spec }}</text>
            <view class="sub-cycle">
              <text class="cycle-icon luc" :class="$luc(item.status === 'paused'  ?  'pause'  :  'play')"></text>
              <text class="cycle-text">{{ item.cycleText }}</text>
            </view>
          </view>
        </view>

        <!-- 分隔线 -->
        <view class="card-divider" />

        <!-- 价格与配送信息 -->
        <view class="price-delivery">
          <view class="price-row">
            <text class="price-current">¥{{ item.price }}</text>
            <text class="price-original">¥{{ item.originalPrice }}</text>
            <text class="discount-tag">省{{ item.discount }}%</text>
          </view>
          <view class="delivery-row">
            <text class="delivery-icon"><text class="luc luc-package"></text></text>
            <text class="delivery-text">下次 {{ item.nextDate }}</text>
          </view>
        </view>

        <!-- 配送倒计时进度条 -->
        <view class="countdown-section">
          <view class="countdown-labels">
            <text class="countdown-label">距下次配送</text>
            <text class="countdown-days">{{ item.daysLeft }} 天</text>
          </view>
          <view class="progress-bar">
            <view class="progress-fill" :style="{ width: item.progress + '%' }" />
          </view>
        </view>

        <!-- 分隔线 -->
        <view class="card-divider" />

        <!-- 操作按钮行 -->
        <view class="action-row">
          <view class="action-btn action-primary" @click="editPlan(item)">
            <text class="action-text action-text-primary">修改周期</text>
          </view>
          <view class="action-btn" @click="togglePause(item)">
            <text class="action-text">{{ item.status === 'paused' ? '恢复' : '暂停' }}</text>
          </view>
          <view class="action-btn" @click="skipDelivery(item)">
            <text class="action-text">跳过本次</text>
          </view>
          <view class="action-btn action-danger" @click="cancelSubscription(item)">
            <text class="action-text action-text-danger">取消订阅</text>
          </view>
        </view>
      </view>

      <!-- 历史订阅（折叠区域） -->
      <view class="history-section">
        <view class="history-toggle" @click="toggleHistory">
          <text class="section-title">历史订阅</text>
          <view class="history-toggle-right">
            <text class="history-count">{{ historySubscriptions.length }} 条记录</text>
            <text class="chevron" :class="{ rotated: historyExpanded }">▼</text>
          </view>
        </view>

        <view v-if="historyExpanded" class="history-list">
          <view v-for="item in historySubscriptions" :key="item.id" class="history-card">
            <view class="sub-info">
              <image :src="item.image" class="sub-image" mode="aspectFill" />
              <view class="sub-detail">
                <view class="sub-name-row">
                  <text class="sub-name">{{ item.name }}</text>
                  <text class="status-tag status-cancelled">已取消</text>
                </view>
                <text class="sub-spec">{{ item.spec }} / {{ item.cycleText }}</text>
                <view class="sub-cycle">
                  <text class="cycle-icon"><text class="luc luc-minus"></text></text>
                  <text class="cycle-text cycle-text-muted">{{ item.dateRange }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部提示 -->
      <view class="tip-box">
        <text class="tip-icon"><text class="luc luc-help-circle"></text></text>
        <text class="tip-text">
          订阅灵活管理：可随时调整周期、暂停或取消，无额外费用。如有疑问请联系客服。
        </text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'

// 历史订阅折叠状态
const historyExpanded = ref(false)

// 活跃订阅 mock 数据
const activeSubscriptions = ref([
  {
    id: 1,
    name: 'MOYUYO 温和沐浴露',
    spec: '500ml',
    image: '/static/images/product-shampoo.png',
    price: '25.20',
    originalPrice: '28.00',
    discount: 10,
    status: 'active',
    statusText: '活跃',
    cycleText: '每月配送',
    nextDate: '07-15',
    daysLeft: 7,
    progress: 70,
  },
  {
    id: 2,
    name: 'MOYUYO 宠物零食混合装',
    spec: '混合口味 200g',
    image: '/static/images/product-treats.png',
    price: '18.00',
    originalPrice: '20.00',
    discount: 10,
    status: 'paused',
    statusText: '暂停',
    cycleText: '双月配送',
    nextDate: '08-01',
    daysLeft: 24,
    progress: 20,
  },
])

// 历史订阅 mock 数据
const historySubscriptions = ref([
  {
    id: 3,
    name: 'MOYUYO 温和沐浴露',
    spec: '500ml',
    image: '/static/images/product-shampoo.png',
    cycleText: '每月配送',
    dateRange: '2026-03-01 至 2026-06-01',
  },
])

// 活跃订阅数量
const activeCount = computed(
  () => activeSubscriptions.value.filter((s) => s.status === 'active').length,
)

// 总节省金额
const totalSaved = computed(() => {
  return activeSubscriptions.value
    .reduce((sum, item) => {
      const saved = parseFloat(item.originalPrice) - parseFloat(item.price)
      return sum + saved
    }, 0)
    .toFixed(2)
})

// 返回上一页
const goBack = () => {
  uni.navigateBack()
}

// 切换历史区域折叠
const toggleHistory = () => {
  historyExpanded.value = !historyExpanded.value
}

// 修改订阅周期
const editPlan = (item) => {
  uni.navigateTo({ url: '/pages/user/subscribe-plan' })
}

// 暂停/恢复订阅
const togglePause = (item) => {
  const action = item.status === 'paused' ? '恢复' : '暂停'
  uni.showModal({
    title: '确认操作',
    content: `确定要${action}「${item.name}」的订阅吗？`,
    success: (res) => {
      if (res.confirm) {
        item.status = item.status === 'paused' ? 'active' : 'paused'
        item.statusText = item.status === 'paused' ? '暂停' : '活跃'
        uni.showToast({ title: `${action}成功`, icon: 'success' })
      }
    },
  })
}

// 跳过本次配送
const skipDelivery = (item) => {
  uni.showToast({ title: '已跳过本次配送', icon: 'success' })
}

// 取消订阅
const cancelSubscription = (item) => {
  uni.showModal({
    title: '取消订阅',
    content: `确定要取消「${item.name}」的订阅吗？取消后将不再享受订阅折扣。`,
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
.subscription-manage {
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
  padding: 0 32rpx 120rpx;
}

/* 概览卡片 */
.overview-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 32rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.overview-stats {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.stat-value {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--color-text);
  margin-top: 4rpx;
}

.stat-success {
  color: var(--color-success);
}

.stat-divider {
  width: 2rpx;
  height: 64rpx;
  background: var(--color-border);
}

.overview-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  background: rgba(219, 201, 138, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-emoji {
  font-size: 36rpx;
}

/* 区域标题 */
.section-header {
  margin-top: 48rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

/* 订阅卡片 */
.sub-card {
  margin-top: 24rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
  overflow: hidden;
}

.sub-info {
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
  padding: 28rpx;
}

.sub-image {
  width: 128rpx;
  height: 128rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}

.sub-detail {
  flex: 1;
  min-width: 0;
}

.sub-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.sub-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.sub-spec {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 4rpx;
}

.sub-cycle {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 12rpx;
}

.cycle-icon {
  font-size: 24rpx;
}

.cycle-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.cycle-text-muted {
  color: var(--color-text-secondary);
  opacity: 0.6;
}

/* 状态标签 */
.status-tag {
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
  flex-shrink: 0;
}

.status-active {
  background: rgba(171, 185, 173, 0.2);
  color: var(--color-success);
}

.status-paused {
  background: rgba(255, 149, 0, 0.1);
  color: #f59e0b;
}

.status-cancelled {
  background: rgba(0, 0, 0, 0.05);
  color: var(--color-text-secondary);
}

/* 分隔线 */
.card-divider {
  height: 1rpx;
  margin: 0 28rpx;
  background: var(--color-border);
}

/* 价格与配送 */
.price-delivery {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}

.price-current {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-text);
}

.price-original {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.discount-tag {
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: rgba(219, 201, 138, 0.15);
  color: var(--color-primary);
  font-size: 22rpx;
  font-weight: 700;
}

.delivery-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.delivery-icon {
  font-size: 24rpx;
}

.delivery-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

/* 倒计时进度条 */
.countdown-section {
  padding: 0 28rpx 16rpx;
}

.countdown-labels {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.countdown-label {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.countdown-days {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.progress-bar {
  height: 12rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: var(--color-primary);
}

/* 操作按钮行 */
.action-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 28rpx;
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--color-border);
  background: transparent;
}

.action-text {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text);
}

.action-text-primary {
  color: var(--color-primary);
}

.action-text-danger {
  color: var(--color-error);
}

/* 历史订阅区域 */
.history-section {
  margin-top: 48rpx;
}

.history-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.history-toggle-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.history-count {
  font-size: 24rpx;
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
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.history-card {
  opacity: 0.5;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
  overflow: hidden;
}

/* 底部提示 */
.tip-box {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-top: 48rpx;
  margin-bottom: 32rpx;
  padding: 28rpx;
  border-radius: 24rpx;
  background: rgba(0, 0, 0, 0.04);
}

.tip-icon {
  font-size: 28rpx;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.tip-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
</style>
