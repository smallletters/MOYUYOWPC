<template>
  <view class="checkout">
    <scroll-view scroll-y class="scroll">
      <!-- 收货地址 -->
      <view class="card address-card" @click="onSelectAddress">
        <view v-if="selectedAddress" class="address-selected">
          <text class="address-name">{{ selectedAddress.first_name }} {{ selectedAddress.last_name }} · {{ selectedAddress.phone }}</text>
          <text class="address-detail">{{ selectedAddress.address_1 }} {{ selectedAddress.address_2 }} {{ selectedAddress.city }} {{ selectedAddress.state }} {{ selectedAddress.postcode }} {{ selectedAddress.country }}</text>
        </view>
        <view v-else class="address-empty">
          <text>+ Add Shipping Address</text>
        </view>
        <text class="arrow">›</text>
      </view>

      <!-- 商品列表 -->
      <view class="card goods-card">
        <view class="card-title">Items ({{ cartStore.selectedQuantity }})</view>
        <view
          v-for="item in cartStore.selectedItems"
          :key="item.variationId || item.productId"
          class="goods-item"
        >
          <image :src="item.image" class="goods-image" />
          <view class="goods-info">
            <text class="goods-name text-ellipsis-2">{{ item.name }}</text>
            <view class="goods-bottom">
              <text class="price">${{ item.price }}</text>
              <text class="goods-qty">x {{ item.quantity }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 优惠券 -->
      <view class="card row-card" @click="onSelectCoupon">
        <text class="row-label">Coupon</text>
        <view class="row-value">
          <text v-if="cartStore.selectedCoupon" class="coupon-selected">
            -${{ cartStore.selectedCoupon.amount }}
          </text>
          <text v-else class="row-placeholder">Select</text>
          <text class="arrow">›</text>
        </view>
      </view>

      <!-- 积分 -->
      <view class="card row-card" @click="onUsePoints">
        <text class="row-label">Use Points (1,280 available)</text>
        <switch :checked="usePoints" color="#DBC98A" @change="usePoints = $event.detail.value" />
      </view>

      <!-- 配送方式 -->
      <view class="card row-card">
        <text class="row-label">Shipping</text>
        <text class="row-value">Standard · $5.99</text>
      </view>

      <!-- 价格明细 -->
      <view class="card price-card">
        <view class="price-row">
          <text>Subtotal</text>
          <text>${{ subtotal.toFixed(2) }}</text>
        </view>
        <view class="price-row">
          <text>Shipping</text>
          <text>$5.99</text>
        </view>
        <view v-if="cartStore.selectedCoupon" class="price-row">
          <text>Coupon Discount</text>
          <text class="discount">-${{ discount.toFixed(2) }}</text>
        </view>
        <view v-if="usePoints" class="price-row">
          <text>Points Used</text>
          <text class="discount">-${{ pointsDiscount.toFixed(2) }}</text>
        </view>
        <view class="price-row total-row">
          <text>Total</text>
          <text class="total-amount">${{ total.toFixed(2) }}</text>
        </view>
      </view>

      <view class="bottom-spacer"></view>
    </scroll-view>

    <!-- 提交订单 -->
    <view class="bottom-bar safe-area-bottom">
      <view class="total-info">
        <text class="total-label">Total</text>
        <text class="total-price">${{ total.toFixed(2) }}</text>
      </view>
      <view class="btn btn-primary submit-btn" @click="onSubmit">Place Order</view>
    </view>
  </view>
</template>

<script>
import { orderApi } from '@/api'
import { useCartStore } from '@/store'
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      selectedAddress: null,
      addressList: [],
      usePoints: false
    }
  },

  computed: {
    cartStore() {
      return useCartStore()
    },
    userStore() {
      return useUserStore()
    },
    subtotal() {
      return this.cartStore.selectedPrice
    },
    discount() {
      return this.cartStore.selectedCoupon ? parseFloat(this.cartStore.selectedCoupon.amount) : 0
    },
    pointsDiscount() {
      // 100 积分 = $1
      return this.usePoints ? Math.min(12.8, this.subtotal * 0.1) : 0
    },
    total() {
      return Math.max(0, this.subtotal + 5.99 - this.discount - this.pointsDiscount)
    }
  },

  onLoad() {
    this.loadAddress()
  },

  methods: {
    loadAddress() {
      // 实际项目应从 /wc/v3/customers/{id} 拉取
      const saved = uni.getStorageSync('moyuyo_address_list') || []
      this.addressList = saved
      this.selectedAddress = saved.find((a) => a.default) || saved[0]
    },

    onSelectAddress() {
      uni.navigateTo({ url: '/pages/user/address?from=checkout' })
    },

    onSelectCoupon() {
      uni.navigateTo({ url: '/pages/user/coupons?from=checkout' })
    },

    onUsePoints() {
      // toggle 由 switch 处理
    },

    /**
     * 提交订单：调用 WooCommerce 创建订单 → 跳支付 WebView
     */
    async onSubmit() {
      if (!this.selectedAddress) {
        uni.showToast({ title: '请选择收货地址', icon: 'none' })
        return
      }

      uni.showLoading({ title: '提交订单中...', mask: true })
      try {
        const items = this.cartStore.selectedItems
        const orderData = {
          payment_method: 'bacs', // 由 WP 后台配置决定
          payment_method_title: 'Place Order',
          set_paid: false,
          status: 'pending',
          billing: this.formatAddress(this.selectedAddress),
          shipping: this.formatAddress(this.selectedAddress),
          line_items: items.map((it) => ({
            product_id: it.productId,
            quantity: it.quantity,
            variation_id: it.variationId || 0
          })),
          shipping_lines: [
            { method_id: 'flat_rate', method_title: 'Standard', total: '5.99' }
          ],
          coupon_lines: this.cartStore.selectedCoupon
            ? [{ code: this.cartStore.selectedCoupon.code }]
            : [],
          customer_id: this.userStore.userId || 0,
          meta_data: [
            { key: '_use_points', value: this.usePoints ? '1' : '0' }
          ]
        }
        const order = await orderApi.createOrder(orderData)
        uni.hideLoading()
        // 跳转到 WebView 支付页（方案A）
        const payUrl = orderApi.getPayUrl(order.id)
        uni.navigateTo({ url: `/pages/webview/pay?orderId=${order.id}&url=${encodeURIComponent(payUrl)}` })
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: '提交失败：' + e.message, icon: 'none' })
      }
    },

    formatAddress(addr) {
      return {
        first_name: addr.first_name || 'Customer',
        last_name: addr.last_name || '',
        address_1: addr.address_1 || '',
        address_2: addr.address_2 || '',
        city: addr.city || '',
        state: addr.state || '',
        postcode: addr.postcode || '',
        country: addr.country || 'US',
        email: addr.email || this.userStore.userInfo?.email || '',
        phone: addr.phone || ''
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.checkout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.scroll {
  flex: 1;
  padding: 16rpx;
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.address-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.address-selected {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.address-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.address-detail {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.address-empty {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  color: var(--color-primary-dark);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
}

.goods-item {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.goods-item:last-child {
  border-bottom: none;
}

.goods-image {
  width: 120rpx;
  height: 120rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.goods-name {
  font-size: var(--font-size-sm);
  line-height: 1.4;
}

.goods-bottom {
  display: flex;
  justify-content: space-between;
  font-size: var(--font-size-sm);
}

.goods-qty {
  color: var(--color-text-tertiary);
}

.row-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-base);
}

.row-label {
  color: var(--color-text);
}

.row-value {
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: var(--color-text-secondary);
}

.row-placeholder {
  color: var(--color-text-tertiary);
}

.coupon-selected {
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-medium);
}

.price-card .price-row {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
  font-size: var(--font-size-base);
}

.discount {
  color: var(--color-primary-dark);
}

.total-row {
  border-top: 1rpx solid var(--color-divider);
  padding-top: 16rpx;
  margin-top: 8rpx;
  font-weight: var(--font-weight-semibold);
}

.total-amount {
  font-size: 32rpx;
  color: var(--color-primary-dark);
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

.total-info {
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
  font-size: 36rpx;
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-bold);
}

.submit-btn {
  padding: 20rpx 48rpx;
  font-size: var(--font-size-md);
}
</style>
