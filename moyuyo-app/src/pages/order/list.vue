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
            <image :src="item.mainImage || ''" lazy-load class="item-image" />
            <view class="item-info">
              <text class="item-name text-ellipsis-2">{{ item.productName }}</text>
              <text class="item-qty">x {{ item.quantity }}</text>
            </view>
          </view>
          <view v-if="(o.items || []).length > 2" class="more">+{{ o.items.length - 2 }} more</view>
        </view>
        <view class="card-footer">
          <text class="total">
            {{ $t('orderList.totalLabel', { amount: `${currencySymbol}${o.payAmount}` }) }}
          </text>
          <view class="btn btn-primary action-btn" @click.stop="onAction(o)">
            {{ actionText(o.status) }}
          </view>
        </view>
      </view>

      <view v-if="loading" class="loading">{{ $t('common.loading') }}</view>
      <view v-else-if="noMore && orders.length === 0" class="empty">
        {{ $t('orderList.empty') }}
      </view>
      <view v-else-if="noMore" class="loading">— No more —</view>
    </scroll-view>
  </view>
</template>

<script>
import { orderApi } from '@/api'
import { useUserStore } from '@/store'
import { i18n } from '@/i18n'
import { getPendingOrders } from '@/utils/storage'

export default {
  data() {
    return {
      activeTab: 'all',
      orders: [],
      loading: false,
      noMore: false,
      page: 1,
      localeVersion: 0,
    }
  },

  computed: {
    tabs() {
      void this.localeVersion
      return [
        { value: 'all', label: i18n.t('orderList.statusTabs.all') },
        { value: 'PENDING_PAY', label: i18n.t('orderList.statusTabs.pendingPay') },
        { value: 'PENDING_SHIP', label: i18n.t('orderList.statusTabs.pendingShip') },
        { value: 'PENDING_RECEIVE', label: i18n.t('orderList.statusTabs.pendingReceive') },
        { value: 'COMPLETED', label: i18n.t('orderList.statusTabs.completed') },
      ]
    },
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onLoad(query) {
    if (query.type && query.type !== 'all') {
      this.activeTab = query.type
    }
    this.loadOrders(true)
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
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
        // request.js 已解包外层 envelope,result 即 IPage { records, total, size, current, ... }
        const result = await orderApi.getOrderList(params)
        let list = Array.isArray(result?.records)
          ? result.records
          : Array.isArray(result)
            ? result
            : []

        // 合并本地未支付订单:all 与 待付款 tab 下,把下单未支付/支付失败暂存本地的记录并入列表,按 id 去重
        if (this.activeTab === 'all' || this.activeTab === 'PENDING_PAY') {
          const pending = getPendingOrders()
          if (pending.length > 0) {
            const seen = new Set(list.map((o) => o.id))
            const merged = [...pending.filter((o) => !seen.has(o.id)), ...list]
            list = merged.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
          }
        }

        list = Array.isArray(list) ? list : []
        this.orders.push(...list)
        // 优先使用后端 total;老接口返回数组时,根据当前次拉取数量判定
        const total = Number(result?.total)
        if (Number.isFinite(total) && total >= 0) {
          this.noMore = this.orders.length >= total
        } else {
          this.noMore = list.length < 10
        }
        if (list.length > 0) this.page += 1
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
      void this.localeVersion
      // 状态文案统一从字典读:key 与后端状态码对齐
      const key = `orderStatus.${status}`
      const v = i18n.t(key)
      // i18n.t 在 key 缺失时返回原 key,这里回退到 status 本身
      return v === key ? status : v
    },

    actionText(status) {
      void this.localeVersion
      const map = {
        PENDING_PAY: i18n.t('orderDetail.pay'),
        PENDING_SHIP: i18n.t('orderList.actionTrack'),
        PENDING_RECEIVE: i18n.t('orderList.actionTrack'),
        COMPLETED: i18n.t('orderList.actionReview'),
      }
      return map[status] || i18n.t('orderList.actionView')
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
