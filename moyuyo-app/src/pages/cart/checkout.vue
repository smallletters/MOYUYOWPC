<template>
  <view class="checkout">
    <scroll-view scroll-y class="scroll">
      <!-- 收货地址 -->
      <view class="card address-card" @click="onSelectAddress">
        <view v-if="selectedAddress" class="address-selected">
          <text class="address-name">
            {{ selectedAddress.receiverName || selectedAddress.first_name }} ·
            {{ selectedAddress.receiverPhone || selectedAddress.phone }}
          </text>
          <text class="address-detail">
            {{ selectedAddress.addressLine || selectedAddress.address_1 }}
            {{ selectedAddress.city || '' }}
            {{ selectedAddress.state || '' }}
            {{ selectedAddress.zipCode || selectedAddress.postcode || '' }}
          </text>
        </view>
        <view v-else class="address-empty">
          <text>+ Add Shipping Address</text>
        </view>
        <text class="arrow"><text class="luc luc-chevron-right"></text></text>
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
          <text class="arrow"><text class="luc luc-chevron-right"></text></text>
        </view>
      </view>

      <!-- 章节 3.1：积分抵现（100 积分 = $1，最高抵订单金额 30%） -->
      <view class="card row-card" @click="onUsePoints">
        <text class="row-label">
          Use Points（{{ pointsBalance }} available, {{ maxPointsUsable }} 可用）
        </text>
        <switch :checked="usePoints" color="#DBC98A" @change="usePoints = $event.detail.value" />
      </view>

      <!-- 配送方式 -->
      <view class="card">
        <text class="card-title">Shipping Method</text>
        <view
          v-for="s in shippingMethods"
          :key="s.id"
          class="shipping-option"
          :class="{ active: selectedShipping === s.id }"
          @click="selectedShipping = s.id"
        >
          <view class="shipping-radio">
            <view v-if="selectedShipping === s.id" class="shipping-dot" />
          </view>
          <view class="shipping-info">
            <text class="shipping-name">{{ s.name }}</text>
            <text class="shipping-time">{{ s.eta }}</text>
          </view>
          <text class="shipping-price" :class="{ free: s.free }">
            {{ s.free ? 'Free' : '$' + s.price.toFixed(2) }}
          </text>
        </view>
      </view>

      <!-- 支付方式 -->
      <view class="card">
        <text class="card-title">Payment Method</text>
        <scroll-view scroll-x class="payment-scroll" show-scrollbar="false">
          <view
            v-for="p in paymentMethods"
            :key="p.id"
            class="payment-option"
            :class="{ active: selectedPayment === p.id }"
            @click="selectedPayment = p.id"
          >
            <text class="payment-icon luc" :class="$luc(p.icon)"></text>
            <text class="payment-name">{{ p.name }}</text>
          </view>
        </scroll-view>
      </view>

      <!-- 优惠券 -->
      <view class="card row-card" @click="onSelectCoupon">
        <text class="row-label">Coupon</text>
        <view class="row-value">
          <text v-if="cartStore.selectedCoupon" class="coupon-selected text-danger">
            -${{ cartStore.selectedCoupon.amount }}
          </text>
          <text v-else class="row-placeholder">Select coupon</text>
          <text class="arrow"><text class="luc luc-chevron-right"></text></text>
        </view>
      </view>

      <!-- 订单备注 -->
      <view class="card">
        <textarea
          v-model="orderRemark"
          class="remark-input"
          placeholder="Optional: order notes (e.g. special delivery instructions)..."
          maxlength="500"
        />
      </view>

      <!-- 价格明细 -->
      <view class="card price-card">
        <view class="price-row">
          <text>Subtotal</text>
          <text>${{ subtotal.toFixed(2) }}</text>
        </view>
        <view class="price-row">
          <text>Shipping</text>
          <text>
            {{ selectedShippingPrice > 0 ? '$' + selectedShippingPrice.toFixed(2) : 'Free' }}
          </text>
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

      <view class="bottom-spacer" />
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
</template><script>
import { orderApi, pointsApi } from '@/api'
import { addressApi } from '@/api'
import { useCartStore } from '@/store'
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      selectedAddress: null,
      addressList: [],
      usePoints: false,
      pointsBalance: 0,
      pointsToUse: 0,
      orderRemark: '',
      selectedShipping: 'standard',
      selectedPayment: 'stripe',
      shippingMethods: [
        {
          id: 'standard',
          name: 'Standard Shipping',
          eta: '3-5 business days',
          price: 0,
          free: true,
        },
        {
          id: 'express',
          name: 'Express Shipping',
          eta: 'Next day delivery',
          price: 12.0,
          free: false,
        },
      ],
      paymentMethods: [
        { id: 'stripe', name: 'Card', icon: 'credit-card' },
        { id: 'paypal', name: 'PayPal', icon: 'wallet' },
        { id: 'applepay', name: 'Apple Pay', icon: 'apple' },
        { id: 'alipay', name: 'Alipay', icon: 'banknote' },
      ],
    }
  },

  computed: {
    cartStore() {
      return useCartStore()
    },
    userStore() {
      return useUserStore()
    },
    selectedShippingPrice() {
      const method = this.shippingMethods.find((s) => s.id === this.selectedShipping)
      return method ? method.price : 0
    },
    subtotal() {
      return this.cartStore.selectedPrice
    },
    discount() {
      return this.cartStore.selectedCoupon ? parseFloat(this.cartStore.selectedCoupon.amount) : 0
    },
    pointsDiscount() {
      // 章节 3.1：100 积分 = $1，最高抵扣订单金额 30%
      if (!this.usePoints) return 0
      const maxByRate = this.subtotal * 0.3
      const maxByBalance = this.pointsBalance / 100
      return Math.min(maxByRate, maxByBalance)
    },
    maxPointsUsable() {
      // 最多可使用的积分 = MIN(余额, 订单金额30% × 100)
      return Math.floor(Math.min(this.pointsBalance, this.subtotal * 0.3 * 100))
    },
    total() {
      return Math.max(
        0,
        this.subtotal + this.selectedShippingPrice - this.discount - this.pointsDiscount,
      )
    },
  },

  onLoad() {
    this.loadAddress()
    this.loadPoints()
  },

  methods: {
    loadAddress() {
      addressApi
        .getAddressList()
        .then((list) => {
          if (list && list.length > 0) {
            this.addressList = list
            this.selectedAddress = list.find((a) => a.isDefault) || list[0]
          }
        })
        .catch(() => {
          const saved = uni.getStorageSync('moyuyo_address_list') || []
          this.addressList = saved
          this.selectedAddress = saved.find((a) => a.default) || saved[0]
        })
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

    /** 章节 3.1：查询可用积分余额 */
    async loadPoints() {
      try {
        const bal = await pointsApi.getPointsBalance()
        this.pointsBalance = Number(bal) || 0
      } catch (e) {
        console.warn('[checkout] load points failed', e)
      }
    },

    /**
     * 提交订单
     */
    async onSubmit() {
      if (!this.selectedAddress) {
        uni.showToast({ title: 'Please select shipping address', icon: 'none' })
        return
      }

      uni.showLoading({ title: 'Submitting order...', mask: true })
      try {
        const items = this.cartStore.selectedItems
        const usedPoints = this.usePoints
          ? Math.floor(this.pointsDiscount * 100) // 折算回积分数
          : 0
        const orderData = {
          items: items.map((it) => ({
            skuId: it.skuId || null,
            productId: it.productId,
            quantity: it.quantity,
          })),
          addressId: this.selectedAddress.id || null,
          remark: this.orderRemark,
          couponId: this.cartStore.selectedCoupon?.code || null,
          paymentMethod: this.selectedPayment,
          shippingMethod: this.selectedShipping,
          // 章节 3.1：积分抵现字段（后端对应 points_used / points_discount）
          pointsUsed: usedPoints,
          pointsDiscount: this.usePoints ? this.pointsDiscount : 0,
        }
        const order = await orderApi.createOrder(orderData)
        uni.hideLoading()

        this.cartStore.clear()

        uni.showToast({ title: 'Order placed!', icon: 'success' })
        setTimeout(() => {
          uni.navigateTo({ url: `/pages/order/pay?id=${order.id || order}` })
        }, 1500)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: 'Submit failed: ' + e.message, icon: 'none' })
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
        phone: addr.phone || '',
      }
    },
  },
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

.card-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
  color: var(--color-text-secondary);
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
  color: var(--color-danger);
  font-weight: var(--font-weight-medium);
}

.shipping-option {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.shipping-option:last-child {
  border-bottom: none;
}

.shipping-option.active .shipping-name {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.shipping-radio {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  border: 2rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.shipping-option.active .shipping-radio {
  border-color: var(--color-primary);
}

.shipping-dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: var(--color-primary);
}

.shipping-info {
  flex: 1;
}

.shipping-name {
  font-size: var(--font-size-sm);
  display: block;
}

.shipping-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.shipping-price {
  font-size: var(--font-size-sm);
}
.shipping-price.free {
  color: var(--color-primary);
}

.payment-scroll {
  display: flex;
  gap: 16rpx;
  white-space: nowrap;
  padding-bottom: 8rpx;
}

.payment-option {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 20rpx 32rpx;
  background: var(--color-background);
  border-radius: var(--radius-md);
  border: 2rpx solid var(--color-divider);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.payment-option.active {
  border-color: var(--color-primary);
  background: rgba(219, 201, 138, 0.08);
  color: var(--color-primary);
}

.payment-icon {
  font-size: 40rpx;
}

.remark-input {
  width: 100%;
  min-height: 120rpx;
  padding: 16rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
}

.text-danger {
  color: var(--color-danger);
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
