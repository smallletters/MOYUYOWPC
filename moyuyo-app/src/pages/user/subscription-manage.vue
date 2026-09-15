<template>
  <view class="subscription-manage">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ t('subscriptionManage.mySubscriptions') }}</text>
      <view class="header-spacer" />
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 订阅概览卡片 -->
      <view class="overview-card">
        <view class="overview-stats">
          <view class="stat-item">
            <text class="stat-label">{{ t('subscriptionManage.activeSubscriptions') }}</text>
            <text class="stat-value">{{ activeCount }}</text>
          </view>
          <view class="stat-divider" />
          <view class="stat-item">
            <text class="stat-label">{{ t('subscriptionManage.totalSaved') }}</text>
            <text class="stat-value stat-success">{{ currencySymbol }}{{ totalSaved }}</text>
          </view>
        </view>
        <view class="overview-icon">
          <text class="icon-emoji luc-star" />
        </view>
      </view>

      <!-- 活跃订阅列表 -->
      <view class="section-header">
        <text class="section-title">{{ t('subscriptionManage.activeSubscriptions') }}</text>
      </view>

      <view v-for="item in activeSubscriptions" :key="item.id" class="sub-card">
        <!-- 商品信息行 -->
        <view class="sub-info">
          <image :src="item.image" class="sub-image" mode="aspectFill" />
          <view class="sub-detail">
            <view class="sub-name-row">
              <text class="sub-name">{{ item.name }}</text>
              <text class="status-tag" :class="'status-' + item.status">
                {{ t(item.statusKey) }}
              </text>
            </view>
            <text class="sub-spec">{{ item.spec }}</text>
            <view class="sub-cycle">
              <text
                class="cycle-icon luc"
                :class="$luc(item.status === 'paused' ? 'pause' : 'play')"
              />
              <text class="cycle-text">{{ t(item.cycleKey) }}</text>
            </view>
          </view>
        </view>

        <!-- 分隔线 -->
        <view class="card-divider" />

        <!-- 价格与配送信息 -->
        <view class="price-delivery">
          <view class="price-row">
            <text class="price-current">{{ currencySymbol }}{{ item.price }}</text>
            <text class="price-original">{{ currencySymbol }}{{ item.originalPrice }}</text>
            <text class="discount-tag">
              {{ t('subscriptionManage.savePercent', { discount: item.discount }) }}
            </text>
          </view>
          <view class="delivery-row">
            <text class="delivery-icon luc-package" />
            <text class="delivery-text">
              {{ t('subscriptionManage.nextDeliveryOn', { date: item.nextDate }) }}
            </text>
          </view>
        </view>

        <!-- 配送倒计时进度条 -->
        <view class="countdown-section">
          <view class="countdown-labels">
            <text class="countdown-label">{{ t('subscriptionManage.countdownLabel') }}</text>
            <text class="countdown-days">
              {{ t('subscriptionManage.daysSuffix', { n: item.daysLeft }) }}
            </text>
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
            <text class="action-text action-text-primary">
              {{ t('subscriptionManage.editCycle') }}
            </text>
          </view>
          <view class="action-btn" @click="togglePause(item)">
            <text class="action-text">
              {{
                t(
                  item.status === 'paused'
                    ? 'subscriptionManage.resume'
                    : 'subscriptionManage.pause',
                )
              }}
            </text>
          </view>
          <view class="action-btn" @click="skipDelivery(item)">
            <text class="action-text">{{ t('subscriptionManage.skipThis') }}</text>
          </view>
          <view class="action-btn action-danger" @click="cancelSubscription(item)">
            <text class="action-text action-text-danger">{{ t('subscriptionManage.cancel') }}</text>
          </view>
        </view>
      </view>

      <!-- 历史订阅（折叠区域） -->
      <view class="history-section">
        <view class="history-toggle" @click="toggleHistory">
          <text class="section-title">{{ t('subscriptionManage.historyTitle') }}</text>
          <view class="history-toggle-right">
            <text class="history-count">
              {{ t('subscriptionManage.historyCount', { n: historySubscriptions.length }) }}
            </text>
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
                  <text class="status-tag status-cancelled">
                    {{ t('subscriptionManage.cancelled') }}
                  </text>
                </view>
                <text class="sub-spec">{{ item.spec }} / {{ t(item.cycleKey) }}</text>
                <view class="sub-cycle">
                  <text class="cycle-icon luc-minus" />
                  <text class="cycle-text cycle-text-muted">
                    {{
                      t('subscriptionManage.dateRange', {
                        start: item.dateStart,
                        end: item.dateEnd,
                      })
                    }}
                  </text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部提示 -->
      <view class="tip-box">
        <text class="tip-icon luc-help-circle" />
        <text class="tip-text">{{ t('subscriptionManage.tip') }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { i18n } from '@/i18n'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userSubscriptionManage')

// 语言版本号：语言切换时触发模板重渲染
const localeVersion = ref(0)
let _unsubLocale = null
// 国际化翻译（依赖 localeVersion 实现响应式）
function t(key, params) {
  void localeVersion.value // 触发依赖追踪
  return i18n.t(key, params)
}
// 货币符号（随语言切换）
const currencySymbol = computed(() => {
  void localeVersion.value
  return i18n.currencySymbol
})

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
    statusKey: 'subscriptionManage.statusActive',
    cycleKey: 'subscriptionManage.cycleMonthly',
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
    statusKey: 'subscriptionManage.statusPaused',
    cycleKey: 'subscriptionManage.cycleBimonthly',
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
    cycleKey: 'subscriptionManage.cycleMonthly',
    dateStart: '2026-03-01',
    dateEnd: '2026-06-01',
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
  const isPaused = item.status === 'paused'
  const actionKey = isPaused ? 'subscriptionManage.resume' : 'subscriptionManage.pause'
  uni.showModal({
    title: i18n.t('subscriptionManage.pauseConfirmTitle'),
    content: i18n.t('subscriptionManage.pauseConfirmContent', {
      action: i18n.t(actionKey),
      name: item.name,
    }),
    success: (res) => {
      if (res.confirm) {
        item.status = isPaused ? 'active' : 'paused'
        item.statusKey =
          item.status === 'paused'
            ? 'subscriptionManage.statusPaused'
            : 'subscriptionManage.statusActive'
        uni.showToast({
          title: i18n.t('subscriptionManage.pauseSuccess', { action: i18n.t(actionKey) }),
          icon: 'success',
        })
      }
    },
  })
}

// 跳过本次配送
const skipDelivery = (item) => {
  uni.showToast({ title: i18n.t('subscriptionManage.skipped'), icon: 'success' })
}

// 取消订阅
const cancelSubscription = (item) => {
  uni.showModal({
    title: i18n.t('subscriptionManage.cancel'),
    content: i18n.t('subscriptionManage.cancelConfirmContent', { name: item.name }),
    confirmColor: '#C96E5F',
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: i18n.t('subscriptionManage.cancelledToast'), icon: 'success' })
      }
    },
  })
}

onMounted(() => {
  _unsubLocale = i18n.subscribe(() => {
    localeVersion.value += 1
  })
})

onBeforeUnmount(() => {
  if (_unsubLocale) _unsubLocale()
})
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
