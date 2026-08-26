<template>
  <view class="after-sales">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">售后记录</text>
      <view class="header-btn right" @click="onApply">
        <text class="apply-text">申请售后</text>
      </view>
    </view>

    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="activeTab = t.value"
      >
        <text>{{ t.label }}</text>
        <view v-if="activeTab === t.value" class="tab-indicator" />
      </view>
    </view>

    <scroll-view scroll-y class="scroll" @scrolltolower="onLoadMore">
      <view v-if="filteredList.length === 0" class="empty-state">
        <view class="empty-icon">
          <view class="empty-box">
            <view class="empty-file">
              <view class="empty-line short" />
              <view class="empty-line medium" />
              <view class="empty-line long" />
            </view>
          </view>
          <view class="empty-search">
            <text class="search-icon luc luc-search"></text>
          </view>
        </view>
        <text class="empty-title">暂无售后记录</text>
        <text class="empty-desc">您的售后申请记录将在这里展示</text>
        <view class="empty-btn" @click="goShopping">去购物</view>
      </view>

      <view
        v-for="item in filteredList"
        :key="item.id"
        class="card"
        @click="goDetail(item)">
        <view class="card-header">
          <view class="card-tags">
            <text class="tag-status" :class="`tag-${item.status}`">{{ item.statusLabel }}</text>
            <text class="tag-type">{{ item.typeLabel }}</text>
          </view>
          <text class="card-arrow"><text class="luc luc-chevron-right"></text></text>
        </view>

        <view class="card-body">
          <image :src="item.image" mode="aspectFill" class="card-img" />
          <view class="card-info">
            <text class="card-name">{{ item.productName }}</text>
            <text class="card-sku">{{ item.sku }}</text>
          </view>
        </view>

        <view class="card-divider" />

        <view class="card-footer">
          <view class="card-amount">
            <text class="amount-label">退款金额</text>
            <text class="amount-value">-¥{{ item.refundAmount }}</text>
          </view>
          <text class="card-time">{{ item.applyTime }} 申请</text>
        </view>

        <view class="card-progress">
          <view class="progress-bar">
            <view
              v-for="(step, idx) in item.progressSteps"
              :key="idx"
              class="progress-step"
              :class="{ filled: idx < item.progressIndex, current: idx === item.progressIndex }"
            />
          </view>
          <view class="progress-labels">
            <text
              v-for="(label, idx) in item.progressSteps"
              :key="idx"
              class="progress-label"
              :class="{ active: idx <= item.progressIndex }"
            >
              {{ label }}
            </text>
          </view>
        </view>
      </view>

      <view v-if="loading" class="loading">加载中...</view>
      <view v-else-if="noMore && filteredList.length > 0" class="loading">— 没有更多了 —</view>

      <view class="policy-section">
        <view class="policy-header" @click="showPolicy = !showPolicy">
          <text class="policy-title">售后政策</text>
          <text class="policy-toggle">{{ showPolicy ? '收起' : '查看' }}</text>
        </view>
        <view v-if="showPolicy" class="policy-content">
          <text class="policy-text">
            自签收之日起 7
            个自然日内，商品完好且不影响二次销售的情况下，可申请无理由退货。退款将在审核通过后 3-5
            个工作日内原路退回。
          </text>
          <view class="policy-contact">
            <text class="contact-text">售后服务热线：400-888-6688</text>
            <text class="contact-text">服务时间：09:00 - 21:00</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { afterSalesApi } from '@/api'

export default {
  data() {
    return {
      activeTab: 'all',
      showPolicy: false,
      loading: false,
      noMore: false,
      page: 1,
      tabs: [
        { label: '全部', value: 'all' },
        { label: '待处理', value: 'pending' },
        { label: '处理中', value: 'processing' },
        { label: '已完成', value: 'completed' },
      ],
      records: [],
    }
  },

  computed: {
    filteredList() {
      if (this.activeTab === 'all') return this.records
      return this.records.filter((r) => r.status === this.activeTab)
    },
  },

  onLoad() {
    this.loadAfterSales()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async loadAfterSales() {
      this.loading = true
      try {
        const res = await afterSalesApi.getAfterSales({ page: this.page })
        const data = res.data || []
        this.records = this.page === 1 ? data : [...this.records, ...data]
        this.noMore = data.length === 0
      } catch (err) {
        uni.showToast({ title: '加载售后记录失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },

    onApply() {
      uni.navigateTo({ url: '/pages/order/apply-after-sales' })
    },

    onLoadMore() {
      if (this.loading || this.noMore) return
      this.page++
      this.loadAfterSales()
    },

    goDetail(item) {
      uni.navigateTo({ url: `/pages/order/detail?id=${item.id}` })
    },

    goShopping() {
      uni.switchTab({ url: '/pages/tabbar/home' })
    },
  },
}
</script>

<style lang="scss" scoped>
.after-sales {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 48rpx;
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

.header-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.header-btn.right {
  left: auto;
  right: 16rpx;
  width: auto;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.apply-text {
  font-size: 26rpx;
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.tabs {
  position: sticky;
  top: 88rpx;
  z-index: 20;
  display: flex;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.tab {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: var(--color-text-tertiary);
  position: relative;

  &.active {
    color: var(--color-primary);
    font-weight: var(--font-weight-semibold);
  }
}

.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}

.scroll {
  padding: 20rpx;
}

.card {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-bottom: 20rpx;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 24rpx 16rpx;
}

.card-tags {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.tag-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36rpx;
  padding: 0 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;

  &.tag-pending {
    background: #e8f2ff;
    color: var(--color-primary);
  }

  &.tag-processing {
    background: #fff3e0;
    color: #ff9500;
  }

  &.tag-completed {
    background: #e9f9ee;
    color: #34c759;
  }
}

.tag-type {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36rpx;
  padding: 0 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  background: var(--color-divider);
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.card-arrow {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

.card-body {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 0 24rpx 20rpx;
}

.card-img {
  width: 128rpx;
  height: 128rpx;
  border-radius: var(--radius-sm);
  border: 1rpx solid var(--color-divider);
  flex-shrink: 0;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  display: block;
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-sku {
  display: block;
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  margin-top: 6rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-divider {
  height: 1rpx;
  background: var(--color-divider);
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
}

.card-amount {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.amount-label {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.amount-value {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-danger);
}

.card-time {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.card-progress {
  padding: 0 24rpx 24rpx;
}

.progress-bar {
  display: flex;
  gap: 6rpx;
}

.progress-step {
  flex: 1;
  height: 6rpx;
  border-radius: 3rpx;
  background: var(--color-divider);

  &.filled {
    background: var(--color-primary);
  }

  &.current {
    background: var(--color-primary);
  }
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 12rpx;
}

.progress-label {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
  white-space: nowrap;

  &.active {
    color: var(--color-primary);
    font-weight: var(--font-weight-medium);
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 40rpx 80rpx;
}

.empty-icon {
  position: relative;
  width: 192rpx;
  height: 192rpx;
  margin-bottom: 48rpx;
}

.empty-box {
  position: absolute;
  inset: 0;
  border-radius: var(--radius-md);
  background: var(--color-divider);
  border: 1rpx solid var(--color-divider);
}

.empty-file {
  position: absolute;
  top: 40rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 80rpx;
  height: 96rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-text-tertiary);
  border-radius: var(--radius-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding-top: 24rpx;
}

.empty-line {
  height: 4rpx;
  border-radius: 2rpx;
  background: #d1d1d6;

  &.short {
    width: 48rpx;
  }
  &.medium {
    width: 40rpx;
  }
  &.long {
    width: 56rpx;
  }
}

.empty-search {
  position: absolute;
  bottom: -8rpx;
  right: -8rpx;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: var(--color-divider);
  border: 4rpx solid var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-icon {
  font-size: 32rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

.empty-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.empty-desc {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  margin-bottom: 48rpx;
}

.empty-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  padding: 0 64rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
}

.policy-section {
  margin-top: 32rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.policy-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.policy-title {
  font-size: 26rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.policy-toggle {
  font-size: 24rpx;
  color: var(--color-primary);
}

.policy-content {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
}

.policy-text {
  display: block;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.policy-contact {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  margin-top: 16rpx;
}

.contact-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.loading {
  text-align: center;
  padding: 40rpx;
  color: var(--color-text-tertiary);
  font-size: 26rpx;
}
</style>
