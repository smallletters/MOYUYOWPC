<template>
  <view class="order-list">
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="onTabChange(t.value)"
      >
        {{ t.label }}
      </view>
    </view>

    <scroll-view scroll-y class="scroll" @scrolltolower="onLoadMore">
      <view
        v-for="o in orders"
        :key="o.id"
        class="card order-card"
        @click="goDetail(o.id)"
      >
        <view class="card-header">
          <text class="order-no">#{{ o.number }}</text>
          <text class="order-status" :class="`status-${o.status}`">{{ statusText(o.status) }}</text>
        </view>
        <view class="order-items">
          <view
            v-for="item in o.line_items.slice(0, 2)"
            :key="item.id"
            class="item-row"
          >
            <image :src="item.image?.src || ''" class="item-image" />
            <view class="item-info">
              <text class="item-name text-ellipsis-2">{{ item.name }}</text>
              <text class="item-qty">x {{ item.quantity }}</text>
            </view>
          </view>
          <view v-if="o.line_items.length > 2" class="more">+{{ o.line_items.length - 2 }} more</view>
        </view>
        <view class="card-footer">
          <text class="total">Total: ${{ o.total }}</text>
          <view class="btn btn-primary action-btn" @click.stop="onAction(o)">{{ actionText(o.status) }}</view>
        </view>
      </view>

      <view v-if="loading" class="loading">Loading...</view>
      <view v-else-if="noMore && orders.length === 0" class="empty">No orders</view>
      <view v-else-if="noMore" class="loading">— No more —</view>
    </scroll-view>
  </view>
</template>

<script>
import { orderApi } from '@/api'
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      activeTab: 'all',
      tabs: [
        { value: 'all', label: 'All' },
        { value: 'pending', label: 'To Pay' },
        { value: 'processing', label: 'To Ship' },
        { value: 'shipped', label: 'Shipped' },
        { value: 'completed', label: 'Completed' }
      ],
      orders: [],
      loading: false,
      noMore: false,
      page: 1
    }
  },

  onLoad(query) {
    if (query.type && query.type !== 'all') {
      this.activeTab = query.type
    }
    this.loadOrders(true)
  },

  methods: {
    async loadOrders(reset = false) {
      if (reset) {
        this.page = 1
        this.noMore = false
        this.orders = []
      }
      this.loading = true
      try {
        const params = { page: this.page, per_page: 10 }
        if (this.activeTab !== 'all') params.status = this.activeTab
        const list = await orderApi.getOrderList(params)
        this.orders.push(...list)
        this.noMore = list.length < 10
        this.page += 1
      } catch (e) {
        console.error('[order-list] error', e)
      } finally {
        this.loading = false
      }
    },

    onTabChange(value) {
      this.activeTab = value
      this.loadOrders(true)
    },

    onLoadMore() {
      if (this.loading || this.noMore) return
      this.loadOrders(false)
    },

    statusText(status) {
      const map = {
        pending: 'To Pay',
        processing: 'To Ship',
        shipped: 'Shipped',
        completed: 'Completed',
        cancelled: 'Cancelled'
      }
      return map[status] || status
    },

    actionText(status) {
      const map = {
        pending: 'Pay Now',
        processing: 'Track',
        shipped: 'Track',
        completed: 'Review'
      }
      return map[status] || 'View'
    },

    onAction(order) {
      if (order.status === 'pending') {
        const payUrl = orderApi.getPayUrl(order.id)
        uni.navigateTo({ url: `/pages/webview/pay?orderId=${order.id}&url=${encodeURIComponent(payUrl)}` })
      } else if (order.status === 'shipped' || order.status === 'processing') {
        uni.navigateTo({ url: `/pages/order/logistics?id=${order.id}` })
      } else if (order.status === 'completed') {
        uni.navigateTo({ url: `/pages/order/review?orderId=${order.id}` })
      } else {
        this.goDetail(order.id)
      }
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.order-list {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.tabs {
  display: flex;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.tab {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  position: relative;
}

.tab.active {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

.tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 32rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}

.scroll {
  flex: 1;
  padding: 16rpx;
}

.order-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.order-no {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.order-status {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}

.status-pending { color: var(--color-warning); }
.status-processing { color: var(--color-info); }
.status-shipped { color: var(--color-info); }
.status-completed { color: var(--color-success); }
.status-cancelled { color: var(--color-text-tertiary); }

.order-items {
  padding: 16rpx 0;
}

.item-row {
  display: flex;
  gap: 16rpx;
  padding: 8rpx 0;
}

.item-image {
  width: 100rpx;
  height: 100rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4rpx;
}

.item-name {
  font-size: var(--font-size-sm);
}

.item-qty {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.more {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  text-align: center;
  padding: 8rpx 0;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
}

.total {
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.total::before {
  content: 'Total: ';
  color: var(--color-text-tertiary);
}

.action-btn {
  padding: 12rpx 24rpx;
  font-size: var(--font-size-sm);
}

.loading, .empty {
  text-align: center;
  padding: 64rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
