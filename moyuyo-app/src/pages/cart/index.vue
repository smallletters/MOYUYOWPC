<template>
  <view class="cart">
    <view v-if="cartStore.items.length === 0" class="empty">
      <text class="empty-emoji">🛒</text>
      <text class="empty-text">Your cart is empty</text>
      <view class="btn btn-primary" @click="goShopping">Start Shopping</view>
    </view>

    <template v-else>
      <scroll-view scroll-y class="scroll">
        <view
          v-for="item in cartStore.items"
          :key="item.variationId || item.productId"
          class="cart-item card"
        >
          <view class="item-check" @click="onCheck(item)">
            <view class="checkbox" :class="{ checked: item.checked }">
              <text v-if="item.checked">✓</text>
            </view>
          </view>
          <image :src="item.image" class="item-image" mode="aspectFill" />
          <view class="item-info">
            <text class="item-name text-ellipsis-2">{{ item.name }}</text>
            <text v-if="item.attrs?.length" class="item-attrs">{{ item.attrs.map(a => a.value).join(' / ') }}</text>
            <view class="item-bottom">
              <text class="price">${{ item.price }}</text>
              <view class="quantity-control">
                <view class="qty-btn" @click="onQtyChange(item, -1)">-</view>
                <text class="qty-value">{{ item.quantity }}</text>
                <view class="qty-btn" @click="onQtyChange(item, 1)">+</view>
              </view>
            </view>
          </view>
          <view class="item-delete" @click="onDelete(item)">×</view>
        </view>
        <view class="bottom-spacer"></view>
      </scroll-view>

      <!-- 底部结算栏 -->
      <view class="bottom-bar safe-area-bottom">
        <view class="check-all" @click="onCheckAll">
          <view class="checkbox" :class="{ checked: cartStore.isAllChecked }">
            <text v-if="cartStore.isAllChecked">✓</text>
          </view>
          <text>All</text>
        </view>
        <view class="total">
          <text class="total-label">Total</text>
          <text class="total-price">${{ cartStore.selectedPrice.toFixed(2) }}</text>
        </view>
        <view
          class="btn btn-primary checkout-btn"
          :class="{ disabled: cartStore.selectedQuantity === 0 }"
          @click="onCheckout"
        >
          Checkout ({{ cartStore.selectedQuantity }})
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import { useCartStore } from '@/store'

export default {
  data() {
    return {}
  },

  computed: {
    cartStore() {
      return useCartStore()
    }
  },

  methods: {
    onCheck(item) {
      const key = item.variationId || item.productId
      this.cartStore.toggleCheck(key)
    },

    onCheckAll() {
      this.cartStore.toggleCheckAll(!this.cartStore.isAllChecked)
    },

    onQtyChange(item, delta) {
      const key = item.variationId || item.productId
      const next = item.quantity + delta
      if (next >= 1) {
        this.cartStore.updateQuantity(key, next)
      } else {
        this.onDelete(item)
      }
    },

    onDelete(item) {
      uni.showModal({
        title: 'Remove from cart?',
        success: (res) => {
          if (res.confirm) {
            const key = item.variationId || item.productId
            this.cartStore.removeItem(key)
            uni.showToast({ title: 'Removed', icon: 'success' })
          }
        }
      })
    },

    onCheckout() {
      if (this.cartStore.selectedQuantity === 0) {
        uni.showToast({ title: '请选择商品', icon: 'none' })
        return
      }
      uni.navigateTo({ url: '/pages/cart/checkout' })
    },

    goShopping() {
      uni.switchTab({ url: '/pages/tabbar/category' })
    }
  }
}
</script>

<style lang="scss" scoped>
.cart {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
}

.empty-emoji {
  font-size: 200rpx;
  opacity: 0.3;
}

.empty-text {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}

.scroll {
  flex: 1;
  padding: 16rpx;
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: var(--color-surface);
  margin-bottom: 16rpx;
  padding: 16rpx;
  border-radius: var(--radius-md);
}

.item-check, .check-all {
  display: flex;
  align-items: center;
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid var(--color-divider);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24rpx;
}

.checkbox.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-text);
}

.item-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.item-name {
  font-size: var(--font-size-base);
  color: var(--color-text);
  line-height: 1.4;
}

.item-attrs {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.item-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.quantity-control {
  display: flex;
  align-items: center;
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-pill);
}

.qty-btn {
  width: 48rpx;
  height: 48rpx;
  text-align: center;
  line-height: 48rpx;
  font-size: 28rpx;
}

.qty-value {
  min-width: 60rpx;
  text-align: center;
  font-size: var(--font-size-sm);
}

.item-delete {
  font-size: 48rpx;
  color: var(--color-text-tertiary);
  padding: 0 8rpx;
}

.bottom-spacer {
  height: 120rpx;
}

.bottom-bar {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  padding: 16rpx 24rpx;
  border-top: 1rpx solid var(--color-divider);
  gap: 16rpx;
}

.total {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.total-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.total-price {
  font-size: 32rpx;
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-bold);
}

.checkout-btn {
  padding: 20rpx 32rpx;
  font-size: var(--font-size-sm);
}

.checkout-btn.disabled {
  opacity: 0.5;
}
</style>
