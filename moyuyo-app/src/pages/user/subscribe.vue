<template>
  <view class="subscribe">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('subscribeService.title') }}</text>
      <view class="header-spacer" />
    </view>

    <!-- 当前订阅概览 -->
    <view v-if="currentPlan" class="overview-card">
      <view class="overview-left">
        <text class="overview-label">{{ $t('subscribeService.currentLabel') }}</text>
        <text class="overview-plan">{{ currentPlan.label }}</text>
        <text class="overview-date">
          {{ $t('subscribeService.nextBilling', { date: currentPlan.nextBilling }) }}
        </text>
      </view>
      <view class="overview-right">
        <text class="overview-price">{{ currentPlan.price }}</text>
      </view>
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 套餐卡片区 -->
      <view class="plans-section">
        <view class="section-title">{{ $t('subscribeService.selectPlan') }}</view>
        <view class="plans-row">
          <view
            v-for="plan in plans"
            :key="plan.key"
            class="plan-card"
            :class="{ active: selectedPlan === plan.key, 'plan-highlight': plan.highlight }"
            @click="selectedPlan = plan.key"
          >
            <view v-if="plan.badge" class="plan-badge">{{ plan.badge }}</view>
            <text class="plan-name">{{ plan.name }}</text>
            <text class="plan-price">{{ plan.price }}</text>
            <text class="plan-period">/{{ plan.period }}</text>
            <text v-if="plan.original" class="plan-original">
              {{ $t('subscribeService.originalPrice', { price: plan.original }) }}
            </text>
            <view v-if="plan.save" class="plan-save">
              <text class="plan-save-text">
                {{ $t('subscribeService.saveAmount', { amount: plan.save }) }}
              </text>
            </view>
          </view>
        </view>
        <!-- 订阅按钮 -->
        <button
          class="subscribe-btn"
          :class="{ subscribed: currentPlan && selectedPlan === currentPlan.key }"
          @click="onSubscribe"
        >
          {{
            currentPlan && selectedPlan === currentPlan.key
              ? $t('subscribeService.currentPlan')
              : $t('subscribeService.subscribeNow')
          }}
        </button>
      </view>

      <!-- 自动续费开关 -->
      <view class="toggle-row">
        <text class="toggle-label">{{ $t('subscribeService.autoRenew') }}</text>
        <switch
          class="toggle-switch"
          :checked="autoRenew"
          color="var(--color-primary)"
          @change="autoRenew = $event.detail.value"
        />
      </view>

      <!-- 功能对比 -->
      <view class="feature-section">
        <view class="section-title">{{ $t('subscribeService.featureCompare') }}</view>
        <view class="feature-table">
          <view class="feature-header">
            <text class="feature-cell feature-label">{{ $t('subscribeService.feature') }}</text>
            <text class="feature-cell feature-head">{{ $t('subscribeService.freeTier') }}</text>
            <text class="feature-cell feature-head feature-premium">
              {{ $t('subscribeService.premiumTier') }}
            </text>
          </view>
          <view v-for="item in featureItems" :key="item.key" class="feature-row">
            <text class="feature-cell feature-label">{{ item.label }}</text>
            <text
              class="feature-cell"
              :class="{ 'feature-yes': item.free, 'feature-no': !item.free }"
            >
              {{ item.free ? '✓' : '—' }}
            </text>
            <text class="feature-cell feature-premium feature-yes">
              <text class="luc luc-check" />
            </text>
          </view>
        </view>
      </view>

      <!-- 历史订阅 -->
      <view class="history-section">
        <view class="section-title">{{ $t('subscribeService.historyTitle') }}</view>
        <view v-if="historyList.length > 0" class="history-list">
          <view v-for="item in historyList" :key="item.id" class="history-item">
            <view class="history-left">
              <text class="history-plan">{{ item.plan }}</text>
              <text class="history-date">
                {{ $t('subscribeService.dateRange', { start: item.startDate, end: item.endDate }) }}
              </text>
            </view>
            <text class="history-status" :class="'status-' + item.status">
              {{ item.statusText }}
            </text>
          </view>
        </view>
        <view v-else class="history-empty">
          <text class="history-empty-text">{{ $t('subscribeService.historyEmpty') }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { subscribeApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userSubscribe',

  data() {
    return {
      currentPlan: null,
      selectedPlan: '',
      autoRenew: true,
      plans: [],
      // 语言版本号：语言切换时触发模板重渲染
      localeVersion: 0,
      featureList: [
        { key: 'browse', free: true },
        { key: 'community', free: true },
        { key: 'health', free: true },
        { key: 'discount', free: false },
        { key: 'shipping', free: false },
        { key: 'support', free: false },
        { key: 'exclusive', free: false },
        { key: 'birthday', free: false },
      ],
      historyList: [],
    }
  },

  computed: {
    // 功能对比清单（label 随语言切换）
    featureItems() {
      void this.localeVersion
      return this.featureList.map((item) => ({
        key: item.key,
        free: item.free,
        label: i18n.t('subscribeService.features.' + item.key),
      }))
    },
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  onLoad() {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    this.loadPlans()
    this.loadMySubscription()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async loadPlans() {
      try {
        const res = await subscribeApi.getSubscribePlans()
        this.plans = res.data || []
        if (this.plans.length > 0 && !this.selectedPlan) {
          this.selectedPlan = this.plans[0].key
        }
      } catch (err) {
        uni.showToast({ title: i18n.t('subscribeService.loadFailed'), icon: 'none' })
      }
    },

    async loadMySubscription() {
      try {
        const res = await subscribeApi.getMySubscription()
        this.currentPlan = res.data || null
        if (this.currentPlan) {
          this.selectedPlan = this.currentPlan.key
          this.autoRenew = this.currentPlan.autoRenew !== false
        }
      } catch (err) {
        console.error('加载订阅信息失败', err)
      }
    },

    async onSubscribe() {
      if (this.currentPlan && this.selectedPlan === this.currentPlan.key) {
        uni.showToast({ title: i18n.t('subscribeService.alreadyCurrent'), icon: 'none' })
        return
      }
      try {
        const res = await subscribeApi.subscribe(this.selectedPlan)
        this.currentPlan = res.data
        uni.showToast({ title: i18n.t('subscribeService.subscribeSuccess'), icon: 'success' })
      } catch (err) {
        uni.showToast({
          title: err.message || i18n.t('subscribeService.subscribeFailed'),
          icon: 'none',
        })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.subscribe {
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
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.header-spacer {
  width: 72rpx;
}

.content {
  width: 100%;
  box-sizing: border-box;
  padding: 0 32rpx 48rpx;
}

.overview-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 24rpx 32rpx 0;
  padding: 32rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
}

.overview-left {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.overview-label {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.overview-plan {
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.overview-date {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.overview-price {
  font-size: 36rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
}

.section-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 24rpx;
}

.plans-section {
  margin-top: 32rpx;
}

.plans-row {
  display: flex;
  gap: 16rpx;
}

.plan-card {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32rpx 16rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 2rpx solid var(--color-divider);
  transition: all 0.2s;
}

.plan-card.active {
  border-color: var(--color-primary);
}

.plan-card.plan-highlight {
  border-color: var(--color-primary);
  background: linear-gradient(180deg, var(--color-primary-light), var(--color-surface));
}

.plan-badge {
  position: absolute;
  top: -16rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  color: #fff;
  font-size: 20rpx;
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
}

.plan-name {
  font-size: 26rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.plan-price {
  font-size: 40rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  line-height: 1;
}

.plan-period {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}

.plan-original {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
  text-decoration: line-through;
  margin-top: 8rpx;
}

.plan-save {
  margin-top: 12rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: var(--color-primary-light);
}

.plan-save-text {
  font-size: 20rpx;
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-medium);
}

.subscribe-btn {
  width: 100%;
  height: 96rpx;
  margin-top: 32rpx;
  border-radius: 48rpx;
  background: var(--color-primary);
  color: #fff;
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.subscribe-btn.subscribed {
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}

.subscribe-btn::after {
  border: none;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 32rpx;
  padding: 24rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
}

.toggle-label {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.toggle-switch {
  transform: scale(0.8);
}

.feature-section {
  margin-top: 40rpx;
}

.feature-table {
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.feature-header,
.feature-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  border-bottom: 1rpx solid var(--color-divider);
}

.feature-row:last-child {
  border-bottom: none;
}

.feature-cell {
  padding: 24rpx 16rpx;
  font-size: 26rpx;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.feature-label {
  text-align: left;
  color: var(--color-text);
  justify-content: flex-start;
}

.feature-head {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.feature-premium {
  background: var(--color-primary-light);
}

.feature-yes {
  color: var(--color-primary);
  font-weight: var(--font-weight-bold);
}

.feature-no {
  color: var(--color-text-tertiary);
}

.history-section {
  margin-top: 40rpx;
  padding-bottom: 48rpx;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
}

.history-left {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.history-plan {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.history-date {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.history-status {
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
}

.status-cancelled {
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}

.history-empty {
  display: flex;
  justify-content: center;
  padding: 48rpx 0;
}

.history-empty-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
</style>
