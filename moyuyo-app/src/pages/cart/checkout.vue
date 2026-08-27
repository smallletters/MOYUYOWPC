<template>
  <view class="checkout">
    <!-- 顶部导航栏：左侧返回 + 居中标题 -->
    <view class="nav-bar">
      <view class="nav-back" @click="onBack">
        <text class="back-icon">‹</text>
      </view>
      <text class="nav-title">Checkout</text>
      <view class="nav-step">Step {{ currentStep }} of {{ totalSteps }}</view>
    </view>

    <!-- 顶部进度条 -->
    <view class="progress-bar">
      <view
        v-for="(s, idx) in steps"
        :key="idx"
        class="progress-dot"
        :class="{ active: idx + 1 <= currentStep, done: idx + 1 < currentStep }"
      >
        <view class="dot-circle">
          <text v-if="idx + 1 < currentStep" class="dot-check">✓</text>
          <text v-else class="dot-num">{{ idx + 1 }}</text>
        </view>
        <text class="dot-label">{{ s }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 1. 收货地址卡片 -->
      <view class="block">
        <view class="block-head">
          <text class="block-title">1. Shipping address</text>
          <text class="block-action" @click="onSelectAddress">Change</text>
        </view>
        <view class="block-body">
          <view v-if="selectedAddress" class="addr-line">
            <text class="addr-name">
              {{ selectedAddress.receiverName || selectedAddress.first_name }}
            </text>
            <text class="addr-detail">
              {{ selectedAddress.addressLine || selectedAddress.address_1 }}
              {{ selectedAddress.city || '' }} {{ selectedAddress.state || '' }}
              {{ selectedAddress.zipCode || selectedAddress.postcode || '' }}
            </text>
            <text class="addr-phone">
              Phone: {{ selectedAddress.receiverPhone || selectedAddress.phone }}
            </text>
          </view>
          <view v-else class="addr-line addr-empty" @click="onSelectAddress">
            <text class="addr-empty-text">+ Add a new shipping address</text>
          </view>
        </view>
      </view>

      <!-- 2. 支付方式 -->
      <view class="block">
        <view class="block-head">
          <text class="block-title">2. Payment method</text>
        </view>
        <view class="block-body payment-body">
          <view
            v-for="p in paymentMethods"
            :key="p.id"
            class="pay-row"
            :class="{ active: selectedPayment === p.id }"
            @click="selectedPayment = p.id"
          >
            <view class="pay-radio">
              <view v-if="selectedPayment === p.id" class="pay-radio-dot" />
            </view>
            <view class="pay-icon-box">
              <text class="pay-icon-text">{{ p.iconText }}</text>
            </view>
            <view class="pay-info">
              <text class="pay-name">{{ p.name }}</text>
              <text class="pay-desc">{{ p.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 3. 复核订单 -->
      <view class="block">
        <view class="block-head">
          <text class="block-title">3. Review items and shipping</text>
        </view>

        <!-- 商品列表 -->
        <view class="block-body items-body">
          <view
            v-for="item in cartStore.selectedItems"
            :key="item.variationId || item.productId"
            class="item-row"
          >
            <image :src="item.image" class="item-image" mode="aspectFill" />
            <view class="item-info">
              <text class="item-name text-ellipsis-2">{{ item.name }}</text>
              <text class="item-qty">Qty: {{ item.quantity }}</text>
              <text v-if="item.seller" class="item-seller">Sold by: {{ item.seller }}</text>
              <view class="item-stock">
                <text class="stock-dot" />
                <text class="stock-text">In stock</text>
              </view>
            </view>
            <view class="item-price-wrap">
              <text class="item-price">${{ Number(item.price).toFixed(2) }}</text>
            </view>
          </view>
        </view>

        <!-- 配送方式 -->
        <view class="block-divider" />
        <view class="block-sub">
          <text class="sub-title">Choose a delivery option:</text>
          <view
            v-for="s in shippingMethods"
            :key="s.id"
            class="ship-row"
            :class="{ active: selectedShipping === s.id }"
            @click="selectedShipping = s.id"
          >
            <view class="ship-radio">
              <view v-if="selectedShipping === s.id" class="ship-radio-dot" />
            </view>
            <view class="ship-info">
              <text class="ship-name">{{ s.name }}</text>
              <text class="ship-time">{{ s.eta }}</text>
            </view>
            <text class="ship-price">
              {{ s.free ? 'FREE' : '$' + s.price.toFixed(2) }}
            </text>
          </view>
        </view>

        <!-- 优惠/积分 -->
        <view class="block-divider" />
        <view class="block-sub">
          <view class="reward-row" @click="onSelectCoupon">
            <text class="reward-label">Apply a coupon</text>
            <view class="reward-right">
              <text v-if="cartStore.selectedCoupon" class="reward-active">
                −${{ cartStore.selectedCoupon.amount }}
              </text>
              <text v-else class="reward-placeholder">None</text>
              <text class="chev">›</text>
            </view>
          </view>
          <view class="reward-row">
            <view class="reward-left">
              <text class="reward-label">Use Points ({{ pointsBalance }} available)</text>
              <text class="reward-sub">100 points = $1, max 30% off</text>
            </view>
            <switch
              :checked="usePoints"
              :disabled="!pointsLoaded"
              color="#F0C14B"
              @change="usePoints = $event.detail.value"
            />
            <!-- M4 修复：积分余额未加载完时禁止勾选，避免按 0 抵扣导致前端显示与实际扣款不一致 -->
            <text v-if="!pointsLoaded" class="reward-sub" style="margin-top: 4rpx">
              Loading points balance...
            </text>
          </view>
        </view>

        <!-- 订单备注 -->
        <view class="block-divider" />
        <view class="block-sub">
          <text class="sub-title">Add a gift message or note (optional)</text>
          <textarea
            v-model="orderRemark"
            class="remark-input"
            placeholder="Type your note here..."
            maxlength="500"
          />
        </view>
      </view>

      <!-- 4. 价格明细 -->
      <view class="block">
        <view class="block-head">
          <text class="block-title">4. Order summary</text>
        </view>
        <view class="block-body summary-body">
          <view class="sum-row">
            <text class="sum-label">Items ({{ cartStore.selectedQuantity }}):</text>
            <text class="sum-value">${{ subtotal.toFixed(2) }}</text>
          </view>
          <view class="sum-row">
            <text class="sum-label">Shipping & handling:</text>
            <text class="sum-value">
              {{ selectedShippingPrice > 0 ? '$' + selectedShippingPrice.toFixed(2) : 'FREE' }}
            </text>
          </view>
          <view v-if="cartStore.selectedCoupon" class="sum-row">
            <text class="sum-label">Coupon discount:</text>
            <text class="sum-value">−${{ discount.toFixed(2) }}</text>
          </view>
          <view v-if="usePoints" class="sum-row">
            <text class="sum-label">Points discount:</text>
            <text class="sum-value">−${{ pointsDiscount.toFixed(2) }}</text>
          </view>
          <view class="sum-row sum-total">
            <text class="sum-total-label">Order total:</text>
            <text class="sum-total-value">${{ total.toFixed(2) }}</text>
          </view>
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <!-- 底部固定下单栏 -->
    <view class="bottom-bar safe-area-bottom">
      <view class="bottom-left">
        <text class="bottom-total-label">Order total:</text>
        <text class="bottom-total-price">${{ total.toFixed(2) }}</text>
      </view>
      <view class="place-btn" @click="onSubmit">
        <text class="place-btn-text">Place your order</text>
      </view>
    </view>
  </view>
</template>

<script>
import { orderApi, pointsApi, addressApi } from '@/api'
import { useCartStore, useUserStore } from '@/store'

export default {
  data() {
    return {
      selectedAddress: null,
      addressList: [],
      usePoints: false,
      pointsBalance: 0,
      pointsLoaded: false,
      pointsToUse: 0,
      orderRemark: '',
      selectedShipping: 'standard',
      selectedPayment: 'stripe',
      steps: ['Shipping', 'Payment', 'Review', 'Place'],
      currentStep: 4,
      totalSteps: 4,
      shippingMethods: [
        {
          id: 'standard',
          name: 'Standard Shipping',
          eta: 'Arrives in 3-5 business days',
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
        {
          id: 'stripe',
          name: 'Credit / Debit Card',
          desc: 'Visa, Mastercard, Amex',
          iconText: 'CARD',
        },
        { id: 'paypal', name: 'PayPal', desc: 'Use your PayPal account', iconText: 'PP' },
        { id: 'applepay', name: 'Apple Pay', desc: 'Fast checkout with Touch ID', iconText: '' },
        { id: 'alipay', name: 'Alipay', desc: '扫码 / 快捷支付', iconText: '支' },
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
    // 章节 3.1：100 积分 = $1，最高抵扣订单金额 30%
    pointsDiscount() {
      if (!this.usePoints) return 0
      const maxByRate = this.subtotal * 0.3
      const maxByBalance = this.pointsBalance / 100
      return Math.min(maxByRate, maxByBalance)
    },
    maxPointsUsable() {
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
    onBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.switchTab({ url: '/pages/cart/index' })
      }
    },

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
        // 即使失败也标记为已加载（按 0 余额展示），避免按钮永久禁用
      } finally {
        // M4 修复：无论成功失败都标记加载完成，确保 switch 不被永久 disable
        this.pointsLoaded = true
      }
    },

    /** 提交订单 */
    async onSubmit() {
      if (!this.selectedAddress) {
        uni.showToast({ title: 'Please select shipping address', icon: 'none' })
        return
      }

      uni.showLoading({ title: 'Submitting order...', mask: true })
      try {
        const items = this.cartStore.selectedItems
        const usedPoints = this.usePoints ? Math.floor(this.pointsDiscount * 100) : 0
        const orderData = {
          items: items.map((it) => ({
            skuId: it.skuId || null,
            productId: it.productId,
            quantity: it.quantity,
          })),
          addressId: this.selectedAddress.id || null,
          remark: this.orderRemark,
          couponId: this.cartStore.selectedCoupon?.code || null,
          // M1 修复：按 CreateOrderRequest 实际接收的字段名提交，缺则被 Jackson 默默丢弃
          couponDiscount: this.cartStore.selectedCoupon
            ? parseFloat(this.cartStore.selectedCoupon.amount)
            : 0,
          pointsUsed: usedPoints,
          pointsDiscount: this.usePoints ? this.pointsDiscount : 0,
          shippingMethod: this.selectedShipping,
          freight: this.selectedShippingPrice,
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
/* === Amazon-style Checkout ===
 * 配色：白底 + 浅灰分隔 + 亚马逊黄 #F0C14B 强调
 * 风格：极简、信息密集、卡片化分块
 * === 响应式策略 ===
 * - 全宽布局，flex 自适应
 * - box-sizing: border-box 防溢出
 * - min-width: 0 防止 flex 子项溢出
 */

.checkout {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 100vh;
  background: var(--color-background);
  box-sizing: border-box;
}

/* 顶部导航 —— 全宽 */
.nav-bar {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 24rpx 32rpx;
  background: var(--color-surface, #fff);
  border-bottom: 1rpx solid #e7e7e7;
  box-sizing: border-box;
  gap: 16rpx;
  position: sticky;
  top: 0;
  z-index: 10;
}

.nav-back {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  transition: background 0.2s;
}

.nav-back:active {
  background: #f0f0f0;
}

.back-icon {
  font-size: 48rpx;
  font-weight: 300;
  color: #2e2b29;
  line-height: 1;
}

.nav-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #2e2b29;
  letter-spacing: 0.5rpx;
  flex: 1;
}

.nav-step {
  font-size: 22rpx;
  color: #6e6e73;
  letter-spacing: 1rpx;
  flex-shrink: 0;
}

/* 步骤条 —— 全宽 */
.progress-bar {
  display: flex;
  width: 100%;
  background: var(--color-surface, #fff);
  padding: 16rpx 24rpx 20rpx;
  box-sizing: border-box;
  gap: 8rpx;
  border-bottom: 1rpx solid #e7e7e7;
}

.progress-dot {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
  min-width: 0;
  position: relative;
}

/* 步骤之间的连线 */
.progress-dot:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 16rpx;
  left: calc(50% + 20rpx);
  right: calc(-50% + 20rpx);
  height: 2rpx;
  background: #e7e7e7;
}

.progress-dot.done:not(:last-child)::after,
.progress-dot.active:not(:last-child)::after {
  background: #f0c14b;
}

.dot-circle {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #e7e7e7;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  z-index: 1;
}

.progress-dot.active .dot-circle {
  border-color: #f0c14b;
  background: #f0c14b;
}

.progress-dot.done .dot-circle {
  border-color: #f0c14b;
  background: #f0c14b;
}

.dot-check {
  color: #2e2b29;
  font-size: 22rpx;
  font-weight: 700;
  line-height: 1;
}

.dot-num {
  color: #8e8e93;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1;
}

.progress-dot.active .dot-num {
  color: #2e2b29;
}

.dot-label {
  font-size: 20rpx;
  color: #8e8e93;
  letter-spacing: 0.5rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  text-align: center;
}

.progress-dot.active .dot-label {
  color: #2e2b29;
  font-weight: 600;
}

/* 滚动区域 */
.scroll {
  flex: 1;
  width: 100%;
  padding: 16rpx 16rpx 0;
  box-sizing: border-box;
  /* 留出底部固定栏的高度，避免最后一项被遮挡 */
  padding-bottom: 160rpx;
}

/* 通用块 —— 白底卡片 */
.block {
  width: 100%;
  background: var(--color-surface, #fff);
  border-radius: 6rpx;
  margin-bottom: 16rpx;
  box-sizing: border-box;
  overflow: hidden;
  box-shadow: 0 1rpx 2rpx rgba(0, 0, 0, 0.05);
}

.block-head {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 20rpx 24rpx;
  box-sizing: border-box;
  border-bottom: 1rpx solid #e7e7e7;
  gap: 12rpx;
}

.block-title {
  flex: 1;
  font-size: 28rpx;
  font-weight: 700;
  color: #2e2b29;
  letter-spacing: 0.5rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.block-action {
  font-size: 24rpx;
  color: #007185;
  font-weight: 500;
  flex-shrink: 0;
}

.block-body {
  width: 100%;
  padding: 20rpx 24rpx;
  box-sizing: border-box;
}

.block-divider {
  width: 100%;
  height: 1rpx;
  background: #e7e7e7;
}

.block-sub {
  width: 100%;
  padding: 20rpx 24rpx;
  box-sizing: border-box;
}

.sub-title {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
  color: #2e2b29;
  margin-bottom: 16rpx;
}

/* 地址行 */
.addr-line {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.addr-name {
  font-size: 28rpx;
  font-weight: 700;
  color: #2e2b29;
}

.addr-detail {
  font-size: 26rpx;
  color: #2e2b29;
  line-height: 1.5;
  word-break: break-word;
}

.addr-phone {
  font-size: 24rpx;
  color: #6e6e73;
  margin-top: 4rpx;
}

.addr-empty {
  padding: 24rpx 0;
}

.addr-empty-text {
  font-size: 28rpx;
  color: #007185;
  font-weight: 500;
}

/* 支付方式行 */
.payment-body {
  padding: 8rpx 0;
}

.pay-row {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 20rpx 24rpx;
  box-sizing: border-box;
  gap: 20rpx;
  border-bottom: 1rpx solid #f0f0f0;
  transition: background 0.15s ease;
}

.pay-row:last-child {
  border-bottom: none;
}

.pay-row.active {
  background: #fff8e3;
}

.pay-radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #aeaeb2;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #fff;
}

.pay-row.active .pay-radio {
  border-color: #f0c14b;
}

.pay-radio-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: #f0c14b;
}

.pay-icon-box {
  width: 72rpx;
  height: 48rpx;
  border: 1rpx solid #d5d5d5;
  border-radius: 4rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f7f7;
  flex-shrink: 0;
}

.pay-row.active .pay-icon-box {
  background: #fff;
  border-color: #f0c14b;
}

.pay-icon-text {
  font-size: 20rpx;
  font-weight: 700;
  color: #2e2b29;
  letter-spacing: 0.5rpx;
}

.pay-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.pay-name {
  font-size: 26rpx;
  font-weight: 600;
  color: #2e2b29;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pay-desc {
  font-size: 22rpx;
  color: #6e6e73;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 商品行 */
.items-body {
  padding: 8rpx 24rpx;
}

.item-row {
  display: flex;
  align-items: flex-start;
  width: 100%;
  padding: 20rpx 0;
  box-sizing: border-box;
  gap: 20rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.item-row:last-child {
  border-bottom: none;
}

.item-image {
  width: 120rpx;
  height: 120rpx;
  border-radius: 4rpx;
  background: #f7f7f7;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.item-name {
  font-size: 26rpx;
  line-height: 1.45;
  color: #007185;
  font-weight: 500;
  word-break: break-word;
}

.item-qty {
  font-size: 22rpx;
  color: #6e6e73;
}

.item-seller {
  font-size: 22rpx;
  color: #6e6e73;
}

.item-stock {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 4rpx;
}

.stock-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #067d62;
  flex-shrink: 0;
}

.stock-text {
  font-size: 22rpx;
  color: #067d62;
  font-weight: 500;
}

.item-price-wrap {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
}

.item-price {
  font-size: 28rpx;
  font-weight: 700;
  color: #2e2b29;
}

/* 配送行 */
.ship-row {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 16rpx 0;
  box-sizing: border-box;
  gap: 16rpx;
}

.ship-radio {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  border: 2rpx solid #aeaeb2;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #fff;
}

.ship-row.active .ship-radio {
  border-color: #f0c14b;
}

.ship-radio-dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: #f0c14b;
}

.ship-info {
  flex: 1;
  min-width: 0;
}

.ship-name {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: #2e2b29;
}

.ship-time {
  display: block;
  font-size: 22rpx;
  color: #6e6e73;
  margin-top: 4rpx;
}

.ship-price {
  font-size: 26rpx;
  font-weight: 700;
  color: #2e2b29;
  flex-shrink: 0;
}

/* 优惠 / 积分行 */
.reward-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 12rpx 0;
  box-sizing: border-box;
  gap: 16rpx;
}

.reward-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.reward-label {
  font-size: 26rpx;
  color: #2e2b29;
  font-weight: 500;
}

.reward-sub {
  font-size: 22rpx;
  color: #6e6e73;
}

.reward-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}

.reward-active {
  font-size: 26rpx;
  font-weight: 700;
  color: #b12704;
}

.reward-placeholder {
  font-size: 24rpx;
  color: #8e8e93;
}

.chev {
  font-size: 32rpx;
  color: #8e8e93;
  line-height: 1;
}

/* 备注输入 */
.remark-input {
  width: 100%;
  box-sizing: border-box;
  min-height: 120rpx;
  padding: 16rpx;
  background: #f7f7f7;
  border-radius: 4rpx;
  font-size: 26rpx;
  border: 1rpx solid #d5d5d5;
  line-height: 1.5;
}

/* 价格明细 */
.summary-body {
  padding: 12rpx 24rpx 20rpx;
}

.sum-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  width: 100%;
  padding: 10rpx 0;
  gap: 12rpx;
}

.sum-label {
  font-size: 24rpx;
  color: #2e2b29;
  flex-shrink: 0;
}

.sum-value {
  font-size: 26rpx;
  font-weight: 500;
  color: #2e2b29;
  text-align: right;
}

.sum-total {
  margin-top: 12rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #e7e7e7;
}

.sum-total-label {
  font-size: 28rpx;
  font-weight: 700;
  color: #b12704;
}

.sum-total-value {
  font-size: 36rpx;
  font-weight: 700;
  color: #b12704;
}

/* 底部占位 */
.bottom-spacer {
  width: 100%;
  height: 140rpx;
}

/* 底部下单栏 —— 固定在页面底部 */
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  width: 100%;
  background: var(--color-surface, #fff);
  padding: 16rpx 32rpx 20rpx;
  box-sizing: border-box;
  gap: 24rpx;
  border-top: 1rpx solid #e7e7e7;
  box-shadow: 0 -4rpx 12rpx rgba(0, 0, 0, 0.04);
  z-index: 100;
}

.bottom-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.bottom-total-label {
  font-size: 22rpx;
  color: #6e6e73;
  letter-spacing: 0.5rpx;
}

.bottom-total-price {
  font-size: 38rpx;
  font-weight: 700;
  color: #b12704;
  line-height: 1.1;
}

.place-btn {
  background: #f0c14b;
  border: 1rpx solid #a88734;
  border-radius: 6rpx;
  padding: 20rpx 32rpx;
  box-shadow: 0 1rpx 0 rgba(255, 255, 255, 0.4) inset;
  flex-shrink: 0;
  transition:
    transform 0.15s ease,
    background 0.15s ease;
}

.place-btn:active {
  background: #ddb347;
  transform: translateY(1rpx);
}

.place-btn-text {
  font-size: 28rpx;
  font-weight: 700;
  color: #2e2b29;
  letter-spacing: 0.5rpx;
}
</style>
