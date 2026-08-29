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
            v-for="item in checkoutItems"
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
            <view class="reward-left">
              <text class="reward-label">Apply a coupon</text>
              <text v-if="activeCoupon" class="reward-sub reward-sub-active">
                {{ activeCoupon.name }}
                <text
                  v-if="activeCoupon.id !== cartStore.selectedCoupon?.id"
                  class="reward-sub-tag"
                >
                  Auto
                </text>
              </text>
              <text
                v-else-if="myCouponsLoaded && bestCoupon === null && !couponAutoDismissed"
                class="reward-sub"
              >
                No applicable coupon for this order
              </text>
              <text v-else-if="couponAutoDismissed" class="reward-sub">Coupon removed</text>
            </view>
            <view class="reward-right">
              <text v-if="activeCoupon" class="reward-active">−${{ discount.toFixed(2) }}</text>
              <text v-else class="reward-placeholder">None</text>
              <!-- 用户已选券时显示 Remove 按钮,允许取消自动选择 -->
              <text v-if="activeCoupon" class="reward-remove" @click.stop="onClearCoupon">
                Remove
              </text>
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
            <text class="sum-label">Items ({{ checkoutQuantity }}):</text>
            <text class="sum-value">${{ subtotal.toFixed(2) }}</text>
          </view>
          <view class="sum-row">
            <text class="sum-label">Shipping & handling:</text>
            <text class="sum-value">
              {{ selectedShippingPrice > 0 ? '$' + selectedShippingPrice.toFixed(2) : 'FREE' }}
            </text>
          </view>
          <view v-if="activeCoupon" class="sum-row">
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

    <!-- 优惠券选择弹窗:点击"Apply a coupon"行时底部弹出用户已领取的优惠券列表 -->
    <view v-if="showCouponPopup" class="coupon-mask" @click="closeCouponPopup">
      <view class="coupon-popup" @click.stop>
        <!-- 弹窗标题 -->
        <view class="cp-header">
          <text class="cp-title">Select a coupon</text>
          <text class="cp-close" @click="closeCouponPopup">✕</text>
        </view>
        <!-- 优惠券列表(可用券排前,不可用券排后) -->
        <scroll-view scroll-y class="cp-list">
          <view v-if="couponPopupList.length === 0" class="cp-empty">
            <text class="cp-empty-text">No coupons available</text>
          </view>
          <view
            v-for="c in couponPopupList"
            :key="c.id"
            class="cp-item"
            :class="{
              'cp-disabled': !isCouponAvailable(c),
              'cp-selected': isCouponSelected(c),
            }"
            @click="onPickCoupon(c)"
          >
            <!-- 左侧金额/折扣区 -->
            <view class="cp-item-left">
              <text class="cp-amount">{{ formatCouponValue(c) }}</text>
              <text class="cp-threshold">{{ formatCouponThreshold(c) }}</text>
            </view>
            <!-- 中右信息区 -->
            <view class="cp-item-right">
              <text class="cp-name">{{ c.name }}</text>
              <text v-if="formatCouponDesc(c)" class="cp-desc">{{ formatCouponDesc(c) }}</text>
              <text v-if="c.endTime" class="cp-expire">
                Valid until {{ formatDate(c.endTime) }}
              </text>
            </view>
            <!-- 选中标记 / 不可用原因 -->
            <text v-if="isCouponSelected(c)" class="cp-check">✓</text>
            <text
              v-else-if="!isCouponAvailable(c) && couponUnavailableReason(c)"
              class="cp-unavailable"
            >
              {{ couponUnavailableReason(c) }}
            </text>
          </view>
        </scroll-view>
        <!-- 底部:不使用优惠券 -->
        <view class="cp-footer">
          <view
            class="cp-none-btn"
            :class="{ 'cp-none-active': !activeCoupon }"
            @click="onDontUseCoupon"
          >
            <text>Do not use a coupon</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { orderApi, pointsApi, addressApi, couponApi } from '@/api'
import { useCartStore, useUserStore } from '@/store'
import { savePendingOrder } from '@/utils/storage'

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
      // 已领取的未使用优惠券(由 loadMyCoupons 填充,供 bestCoupon 自动挑选)
      myCoupons: [],
      myCouponsLoaded: false,
      // 自动选券是否已被用户手动取消(用户手动点了"None"后不再自动覆盖)
      couponAutoDismissed: false,
      // 优惠券选择弹窗显示状态(底部弹出用户已领取的优惠券列表)
      showCouponPopup: false,
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
        { id: 'googlepay', name: 'Google Pay', desc: '快速结账，用已保存的卡', iconText: 'G Pay' },
        { id: 'paypal', name: 'PayPal', desc: 'Use your PayPal account', iconText: 'PP' },
        { id: 'venmo', name: 'Venmo', desc: 'PayPal 旗下，扫码或快捷支付', iconText: 'V' },
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
    /** 当前结算商品列表:立即购买临时单品优先,否则取购物车选中项 */
    checkoutItems() {
      return this.cartStore.buyNowItem ? [this.cartStore.buyNowItem] : this.cartStore.selectedItems
    },
    /** 当前结算商品总件数 */
    checkoutQuantity() {
      return this.checkoutItems.reduce((sum, i) => sum + (i.quantity || 0), 0)
    },
    subtotal() {
      return this.checkoutItems.reduce((sum, i) => sum + (i.price || 0) * (i.quantity || 0), 0)
    },
    /**
     * 当前订单适用的最优未使用优惠券。
     * 排序规则:
     * 1) AMOUNT 券:必须满足 minOrderAmount <= subtotal 才算"可用",按实际抵扣额(discountValue)降序
     * 2) PERCENT 券:按 抵扣比例(= discountValue) 降序,无门槛限制
     * 同分情况下选 id 较大的(更"新")
     * 若用户已手动取消(couponAutoDismissed=true),则返回 null 不再自动选
     */
    bestCoupon() {
      if (this.couponAutoDismissed) return null
      if (!this.myCouponsLoaded) return this.cartStore.selectedCoupon
      const subtotal = this.subtotal
      if (!subtotal || subtotal <= 0) return null
      const available = this.myCoupons
        .filter((c) => c.active !== false)
        .filter((c) => !c.endTime || new Date(c.endTime.replace(' ', 'T')) > new Date())
        .filter((c) => {
          // 仅 AMOUNT 券要求门槛;PERCENT 券无门槛
          if ((c.type || '').toUpperCase() === 'AMOUNT') {
            return Number(c.minOrderAmount || 0) <= subtotal
          }
          return true
        })
      if (available.length === 0) return this.cartStore.selectedCoupon
      // 计算每张券的实际抵扣金额,用于排序
      const scored = available.map((c) => {
        const isPercent = (c.type || '').toUpperCase() === 'PERCENT'
        let actual
        if (isPercent) {
          // PERCENT:discountValue 表示折扣百分比(例如 95 = 9.5 折),抵扣 = subtotal * (100 - value) / 100
          const v = Number(c.discountValue || 0)
          actual = (subtotal * (100 - v)) / 100
        } else {
          actual = Number(c.discountValue || 0)
        }
        return { coupon: c, actual }
      })
      scored.sort((a, b) => {
        if (b.actual !== a.actual) return b.actual - a.actual
        // 同抵扣额时选 id 较大(更"新")
        return Number(b.coupon.id) - Number(a.coupon.id)
      })
      const picked = scored[0].coupon
      // 维持现有 selectedCoupon 以兼容用户手动从 /pages/user/coupons 选的场景
      return this.cartStore.selectedCoupon || picked
    },
    /**
     * 当前生效的优惠券(优先 bestCoupon;若用户手动选了别的,保留)
     */
    activeCoupon() {
      return this.bestCoupon || this.cartStore.selectedCoupon
    },
    /**
     * 当前生效优惠券的实际抵扣金额(用于价格明细行展示)
     */
    discount() {
      const coupon = this.activeCoupon
      if (!coupon) return 0
      const isPercent = (coupon.type || '').toUpperCase() === 'PERCENT'
      if (isPercent) {
        const v = Number(coupon.discountValue || 0)
        return (this.subtotal * (100 - v)) / 100
      }
      return Number(coupon.discountValue || 0)
    },
    /**
     * 弹窗展示的券列表:可用券排前,不可用券排后
     * 依赖 subtotal,订单金额变化时自动重算
     */
    couponPopupList() {
      const list = Array.isArray(this.myCoupons) ? this.myCoupons : []
      return [...list].sort((a, b) => {
        const aOk = this.isCouponAvailable(a) ? 1 : 0
        const bOk = this.isCouponAvailable(b) ? 1 : 0
        return bOk - aOk
      })
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
    this.loadMyCoupons()
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

    /**
     * 用户点击"Apply a coupon"行 -> 底部弹出已领取的优惠券列表供选择
     * 替代原跳转 /pages/user/coupons 的方案,在当前页完成选券
     */
    onSelectCoupon() {
      // 若券列表未加载完成,先重新拉取,避免弹窗无内容
      if (!this.myCouponsLoaded) {
        this.loadMyCoupons()
      }
      this.showCouponPopup = true
    },

    /**
     * 用户手动清除已选优惠券(右上角 Remove 或弹窗"不使用")
     * 设置 couponAutoDismissed=true,后续不再自动选券
     */
    onClearCoupon() {
      this.cartStore.selectedCoupon = null
      this.couponAutoDismissed = true
    },

    /** 关闭优惠券选择弹窗 */
    closeCouponPopup() {
      this.showCouponPopup = false
    },

    /**
     * 在弹窗中选中一张优惠券
     * 手动选券后重置 couponAutoDismissed,使 activeCoupon 显示用户所选
     */
    onPickCoupon(coupon) {
      if (!this.isCouponAvailable(coupon)) return
      this.cartStore.selectedCoupon = coupon
      this.couponAutoDismissed = false
      this.closeCouponPopup()
    },

    /** 弹窗底部"不使用优惠券"按钮:等同于清除并关闭弹窗 */
    onDontUseCoupon() {
      this.cartStore.selectedCoupon = null
      this.couponAutoDismissed = true
      this.closeCouponPopup()
    },

    /** 判断某张券对当前订单是否可用(active、未过期、门槛满足) */
    isCouponAvailable(c) {
      if (c.active === false) return false
      if (c.endTime && new Date(String(c.endTime).replace(' ', 'T')) <= new Date()) return false
      if ((c.type || '').toUpperCase() === 'AMOUNT') {
        return Number(c.minOrderAmount || 0) <= this.subtotal
      }
      return true
    },

    /** 判断某张券是否为当前已选中券 */
    isCouponSelected(c) {
      const sel = this.cartStore.selectedCoupon
      return !!sel && String(sel.id) === String(c.id)
    },

    /** 单张券对当前订单的实际抵扣金额 */
    couponActualDiscount(c) {
      const isPercent = (c.type || '').toUpperCase() === 'PERCENT'
      if (isPercent) {
        const v = Number(c.discountValue || 0)
        return (this.subtotal * (100 - v)) / 100
      }
      return Number(c.discountValue || 0)
    },

    /** 弹窗左侧金额/折扣展示 */
    formatCouponValue(c) {
      const isPercent = (c.type || '').toUpperCase() === 'PERCENT'
      if (isPercent) {
        const off = 100 - Number(c.discountValue || 0)
        return off + '% OFF'
      }
      return '$' + Number(c.discountValue || 0).toFixed(2)
    },

    /** 弹窗门槛文案 */
    formatCouponThreshold(c) {
      const isPercent = (c.type || '').toUpperCase() === 'PERCENT'
      if (isPercent) return 'No threshold'
      const min = Number(c.minOrderAmount || 0)
      if (min <= 0) return 'No threshold'
      return 'Min $' + min.toFixed(2)
    },

    /** 弹窗抵扣描述(可用时显示可省金额) */
    formatCouponDesc(c) {
      if (!this.isCouponAvailable(c)) return ''
      return 'Save $' + this.couponActualDiscount(c).toFixed(2)
    },

    /** 不可用原因文案 */
    couponUnavailableReason(c) {
      if (c.active === false) return 'Unavailable'
      if (c.endTime && new Date(String(c.endTime).replace(' ', 'T')) <= new Date()) return 'Expired'
      if ((c.type || '').toUpperCase() === 'AMOUNT') {
        const min = Number(c.minOrderAmount || 0)
        if (min > this.subtotal) {
          return 'Need $' + (min - this.subtotal).toFixed(2) + ' more'
        }
      }
      return ''
    },

    /** 日期格式化为 YYYY-MM-DD */
    formatDate(d) {
      if (!d) return ''
      return String(d).replace(' ', 'T').slice(0, 10)
    },

    /**
     * 拉取当前用户已领取的、未使用优惠券,用于 bestCoupon 自动挑选
     * 后端 GET /api/v1/coupons/mine?status=UNUSED 返回 List<CouponEntity>
     */
    async loadMyCoupons() {
      try {
        const list = await couponApi.getMyCoupons('UNUSED')
        this.myCoupons = Array.isArray(list) ? list : []
      } catch (e) {
        console.warn('[checkout] loadMyCoupons failed', e)
        this.myCoupons = []
      } finally {
        this.myCouponsLoaded = true
      }
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
        const items = this.checkoutItems
        const usedPoints = this.usePoints ? Math.floor(this.pointsDiscount * 100) : 0
        const orderData = {
          items: items.map((it) => ({
            skuId: it.skuId || null,
            productId: it.productId,
            quantity: it.quantity,
          })),
          addressId: this.selectedAddress.id || null,
          remark: this.orderRemark,
          // 优先用 auto-selected activeCoupon(支持 AMOUNT/PERCENT 两种)
          couponId: this.activeCoupon?.code || null,
          couponUserId: this.activeCoupon?.userCouponId || null,
          couponDiscount: this.discount,
          pointsUsed: usedPoints,
          pointsDiscount: this.usePoints ? this.pointsDiscount : 0,
          shippingMethod: this.selectedShipping,
          freight: this.selectedShippingPrice,
        }
        const order = await orderApi.createOrder(orderData)
        uni.hideLoading()

        // 下单成功即写入本地未支付记录,便于待付款列表展示(即使未支付/支付失败也不丢失)
        const orderId = order?.id || order
        if (orderId) {
          savePendingOrder({
            id: orderId,
            orderNo: order?.orderNo || String(orderId),
            status: 'PENDING_PAY',
            payAmount: this.total,
            createdAt: Date.now(),
            items: items.map((it) => ({
              id: it.productId,
              productName: it.name,
              mainImage: it.image,
              quantity: it.quantity,
            })),
          })
        }

        // 立即购买场景只清临时单品,购物车结算场景清空已购选中项
        if (this.cartStore.buyNowItem) {
          this.cartStore.clearBuyNow()
          this.cartStore.selectedCoupon = null
        } else {
          this.cartStore.clear()
        }

        uni.showToast({ title: 'Order placed!', icon: 'success' })
        // 将 checkout 选中的支付方式分两部分传：
        // channel  = 渠道大类（STRIPE / PAYPAL）
        // method   = 同一渠道下的细分方式（CARD / APPLE_PAY / ALIPAY / GOOGLE_PAY 等）
        // 后端 PaymentServiceImpl.createStripePayment 会按 method 决定 Stripe Checkout 的 payment_method_types
        const channelMap = {
          applepay: { channel: 'STRIPE', method: 'APPLE_PAY' },
          alipay: { channel: 'STRIPE', method: 'ALIPAY' },
          googlepay: { channel: 'STRIPE', method: 'GOOGLE_PAY' },
          cashapp: { channel: 'STRIPE', method: 'CASH_APP' },
          affirm: { channel: 'STRIPE', method: 'AFFIRM' },
          afterpay: { channel: 'STRIPE', method: 'AFTERPAY' },
          venmo: { channel: 'PAYPAL', method: 'VENMO' },
          paypal: { channel: 'PAYPAL', method: 'PAYPAL' },
        }
        const picked = channelMap[this.selectedPayment] || channelMap.googlepay
        // 客户端类型：APP / H5 / MP。
        // APP 打 iOS/Android 包时传 APP → 后端 success/cancel URL 用 moyuyo://pay/return 自定义 scheme，
        // 避免从 Stripe/PayPal/支付宝 等外部 APP 付完款后回不来你的 Moyuyo APP
        let clientType = 'H5'
        // //#ifdef APP-PLUS
        clientType = 'APP'
        // //#endif
        // //#ifdef MP
        clientType = 'MP'
        // //#endif
        const qs = new URLSearchParams({
          id: String(order?.id || order),
          channel: picked.channel,
          method: picked.method,
          clientType,
        }).toString()
        setTimeout(() => {
          uni.navigateTo({ url: `/pages/order/pay?${qs}` })
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

/* 自动选中的副标题与 Auto 标签 */
.reward-sub-active {
  color: #2e2b29;
  font-weight: 500;
}

.reward-sub-tag {
  display: inline-block;
  margin-left: 8rpx;
  padding: 2rpx 10rpx;
  background: #f0c14b;
  color: #2e2b29;
  font-size: 18rpx;
  font-weight: 700;
  border-radius: 6rpx;
  letter-spacing: 0.5rpx;
  vertical-align: middle;
}

/* 移除按钮:小号 Amazon 蓝 */
.reward-remove {
  font-size: 22rpx;
  color: #007185;
  text-decoration: underline;
  padding: 0 8rpx;
  cursor: pointer;
}

.chev {
  font-size: 32rpx;
  color: #8e8e93;
  line-height: 1;
}

/* 优惠券选择弹窗 */
.coupon-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.coupon-popup {
  width: 100%;
  max-height: 80vh;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  display: flex;
  flex-direction: column;
  padding-bottom: env(safe-area-inset-bottom);
}

.cp-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-bottom: 1rpx solid #eee;
  flex-shrink: 0;
}

.cp-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #2e2b29;
}

.cp-close {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 32rpx;
  color: #8e8e93;
  padding: 8rpx 16rpx;
}

.cp-list {
  flex: 1;
  max-height: 60vh;
  padding: 16rpx 24rpx;
  box-sizing: border-box;
}

.cp-empty {
  padding: 80rpx 0;
  text-align: center;
}

.cp-empty-text {
  font-size: 26rpx;
  color: #8e8e93;
}

.cp-item {
  display: flex;
  align-items: stretch;
  background: linear-gradient(135deg, #fff5f5 0%, #ffffff 100%);
  border: 1rpx solid #f0d0d0;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
  overflow: hidden;
  position: relative;
}

.cp-item.cp-disabled {
  background: #f5f5f5;
  border-color: #e0e0e0;
  opacity: 0.6;
}

.cp-item.cp-selected {
  border-color: #b12704;
  box-shadow: 0 0 0 2rpx #b12704 inset;
}

.cp-item-left {
  width: 200rpx;
  padding: 24rpx 16rpx;
  background: #b12704;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cp-item.cp-disabled .cp-item-left {
  background: #bbb;
}

.cp-amount {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1.1;
}

.cp-threshold {
  font-size: 20rpx;
  margin-top: 8rpx;
  opacity: 0.9;
}

.cp-item-right {
  flex: 1;
  padding: 20rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  justify-content: center;
  min-width: 0;
}

.cp-name {
  font-size: 26rpx;
  font-weight: 500;
  color: #2e2b29;
}

.cp-desc {
  font-size: 22rpx;
  color: #b12704;
}

.cp-expire {
  font-size: 20rpx;
  color: #8e8e93;
}

.cp-check {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 36rpx;
  color: #b12704;
  font-weight: 700;
}

.cp-unavailable {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 20rpx;
  color: #8e8e93;
  max-width: 200rpx;
  text-align: right;
}

.cp-footer {
  padding: 16rpx 24rpx 24rpx;
  border-top: 1rpx solid #eee;
  flex-shrink: 0;
}

.cp-none-btn {
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #d5d5d5;
  border-radius: 40rpx;
  font-size: 26rpx;
  color: #2e2b29;
}

.cp-none-btn.cp-none-active {
  border-color: #b12704;
  color: #b12704;
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
