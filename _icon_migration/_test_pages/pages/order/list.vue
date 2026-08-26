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
        @click="goDetail(o.id)">
        <view class="card-header">
          <text class="order-no">#{{ o.orderNo }}</text>
          <text class="order-status" :class="`status-${o.status}`">{{ statusText(o.status) }}</text>
        </view>
        <view class="order-items">
          <view v-for="item in (o.items || []).slice(0, 2)" :key="item.id" class="item-row">
            <image :src="item.mainImage || ''" class="item-image" />
            <view class="item-info">
              <text class="item-name text-ellipsis-2">{{ item.productName }}</text>
              <text class="item-qty">x {{ item.quantity }}</text>
            </view>
          </view>
          <view v-if="(o.items || []).length > 2" class="more">+{{ o.items.length - 2 }} more</view>
        </view>
        <view class="card-footer">
          <text class="total">Total: ${{ o.payAmount }}</text>
          <view class="btn btn-primary action-btn" @click.stop="onAction(o)">
            {{ actionText(o.status) }}
          </view>
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
        { value: 'all', label: '全部' },
        { value: 'PENDING_PAY', label: '待付款' },
        { value: 'PENDING_SHIP', label: '待发货' },
        { value: 'PENDING_RECEIVE', label: '待收货' },
        { value: 'COMPLETED', label: '待评价' },
      ],
      orders: [],
      loading: false,
      noMore: false,
      page: 1,
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
        const params = { page: this.page, size: 10 }
        if (this.activeTab !== 'all') params.status = this.activeTab
        const result = await orderApi.getOrderList(params)
        // result could be Page wrapper with records, or plain array
        const list = result?.records || result || []
        this.orders.push(...list)
        this.noMore = !result?.records ? list.length < 10 : (result.total || 0) <= this.page * 10
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
        PENDING_PAY: 'To Pay',
        PENDING_SHIP: 'To Ship',
        PENDING_RECEIVE: 'To Receive',
        COMPLETED: 'Completed',
        CANCELLED: 'Cancelled',
        REFUNDING: 'Refunding',
        REFUNDED: 'Refunded',
      }
      return map[status] || status
    },

    actionText(status) {
      const map = {
        PENDING_PAY: 'Pay Now',
        PENDING_SHIP: 'Track',
        PENDING_RECEIVE: 'Track',
        COMPLETED: 'Review',
      }
      return map[status] || 'View'
    },

    onAction(order) {
      if (order.status === 'PENDING_PAY') {
        uni.navigateTo({
          url: `/pages/order/pay?orderId=${order.id}&amount=${order.payAmount}`,
        })
      } else if (order.status === 'PENDING_SHIP' || order.status === 'PENDING_RECEIVE') {
        uni.navigateTo({ url: `/pages/order/logistics?id=${order.id}` })
      } else if (order.status === 'COMPLETED') {
        uni.navigateTo({ url: `/pages/order/review?orderId=${order.id}` })
      } else {
        this.goDetail(order.id)
      }
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
    },
  },
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
  text-align: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  position: relative;

  &.active {
    color: var(--color-primary);
    font-weight: 600;

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%);
      width: 60rpx;
      height: 4rpx;
      background: var(--color-primary);
      border-radius: 2rpx;
    }
  }
}

.scroll {
  flex: 1;
  padding: 20rpx;
}

.order-card {
  margin-bottom: 20rpx;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.order-no {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.order-status {
  font-size: 26rpx;
  font-weight: 600;

  &.status-PENDING_PAY {
    color: var(--color-warning);
  }
  &.status-PENDING_SHIP {
    color: var(--color-info);
  }
  &.status-PENDING_RECEIVE {
    color: var(--color-primary);
  }
  &.status-COMPLETED {
    color: var(--color-success);
  }
  &.status-CANCELLED {
    color: var(--color-text-tertiary);
  }
}

.order-items {
  .item-row {
    display: flex;
    align-items: center;
    margin-bottom: 16rpx;
  }

  .item-image {
    width: 120rpx;
    height: 120rpx;
    border-radius: 12rpx;
    margin-right: 16rpx;
    background: var(--color-background);
  }

  .item-info {
    flex: 1;
  }

  .item-name {
    font-size: 26rpx;
    display: block;
  }

  .item-qty {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
  }
}

.more {
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  padding: 8rpx;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--color-divider);
}

.total {
  font-size: 28rpx;
  font-weight: 600;
}

.action-btn {
  padding: 12rpx 32rpx;
  font-size: 24rpx;
}

.loading {
  text-align: center;
  padding: 40rpx;
  color: var(--color-text-tertiary);
  font-size: 26rpx;
}

.empty {
  text-align: center;
  padding: 100rpx 40rpx;
  color: var(--color-text-tertiary);
  font-size: 28rpx;
}
</style>
