<template>
  <view class="recycle-bin">
    <view class="page-header">
      <view class="back" @click="goBack" aria-label="返回">‹</view>
      <text class="title">订单回收站</text>
    </view>

    <scroll-view scroll-y class="content">
      <view class="banner">
        <text class="banner-icon">♻</text>
        <view class="banner-info">
          <text class="banner-title">回收站规则</text>
          <text class="banner-desc">已删除的订单会保留 30 天，过期将自动清理</text>
        </view>
      </view>

      <view v-if="orders.length === 0" class="empty">
        <text class="empty-icon">📭</text>
        <text class="empty-text">回收站空空如也</text>
      </view>

      <view v-else class="order-list">
        <view v-for="o in orders" :key="o.id" class="order-card">
          <view class="order-header">
            <text class="order-no">#{{ o.orderNo }}</text>
            <text class="order-removed">删除于 {{ formatDate(o.removedAt) }}</text>
          </view>
          <view class="order-items">
            <text class="items-count">{{ o.itemCount || 0 }} 件商品</text>
            <text class="order-amount">¥{{ o.payAmount }}</text>
          </view>
          <view class="order-actions">
            <view class="btn outline" @click="onRestore(o)">恢复订单</view>
            <view class="btn danger" @click="onDeleteForever(o)">永久删除</view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { orderApi } from '@/api'

export default {
  data() {
    return {
      orders: [],
    }
  },

  onShow() {
    this.loadRecycleBin()
  },

  methods: {
    async loadRecycleBin() {
      try {
        const list = await orderApi.getRecycleBin()
        this.orders = Array.isArray(list) ? list : []
      } catch (e) {
        console.warn('[recycle-bin] load failed', e)
      }
    },

    formatDate(s) {
      if (!s) return ''
      return new Date(s).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
    },

    goBack() {
      uni.navigateBack()
    },

    onRestore(order) {
      uni.showModal({
        title: '恢复订单？',
        content: `订单 #${order.orderNo} 将回到我的订单列表`,
        success: async (res) => {
          if (res.confirm) {
            try {
              await orderApi.restoreOrder(order.id)
              this.loadRecycleBin()
              uni.showToast({ title: '已恢复', icon: 'success' })
            } catch (e) {
              uni.showToast({ title: '恢复失败', icon: 'none' })
            }
          }
        },
      })
    },

    onDeleteForever(order) {
      uni.showModal({
        title: '永久删除？',
        content: '此操作不可撤销，订单将彻底删除',
        success: async (res) => {
          if (res.confirm) {
            try {
              await orderApi.permanentDeleteOrder(order.id)
              this.loadRecycleBin()
              uni.showToast({ title: '已删除', icon: 'success' })
            } catch (e) {
              uni.showToast({ title: '删除失败', icon: 'none' })
            }
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.recycle-bin {
  min-height: 100vh;
  background: var(--color-background);
}

.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  color: var(--color-text);
}

.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-right: 60rpx;
}

.content {
  padding: 24rpx;
}

.banner {
  display: flex;
  gap: 16rpx;
  padding: 24rpx;
  background: var(--color-primary-light);
  border-radius: var(--radius-md);
  margin-bottom: 24rpx;
}

.banner-icon {
  font-size: 48rpx;
}

.banner-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.banner-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.banner-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.empty {
  padding: 96rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.empty-icon {
  font-size: 120rpx;
  opacity: 0.4;
}

.empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.order-card {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  padding: 24rpx;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.order-no {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.order-removed {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.order-items {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
}

.items-count {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.order-amount {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.order-actions {
  display: flex;
  gap: 16rpx;
}

.btn {
  flex: 1;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  border: 1rpx solid var(--color-divider);
  color: var(--color-text);
}

.btn.outline {
  background: var(--color-surface);
}

.btn.danger {
  color: var(--color-danger);
  border-color: var(--color-danger);
}
</style>