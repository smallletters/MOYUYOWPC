<template>
  <view class="cart">
    <!-- 顶部导航栏：页面已关闭原生导航(navigationStyle: custom)，统一在此渲染，避免两条导航栏 -->
    <view class="header">
      <view class="header-inner">
        <view class="header-back" :aria-label="$t('common.back')" @click="goBack">
          <text class="header-back-icon luc luc-arrow-left" />
        </view>
        <text class="header-title">{{ $t('pageTitle.cartIndex') }}</text>
        <view class="header-edit" @click="onToggleEdit">
          <text v-if="cartItems.length > 0" class="header-edit-text">
            {{ editing ? $t('cart.editDone') : $t('cart.edit') }}
          </text>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view v-if="cartItems.length === 0 && expiredItems.length === 0" class="empty">
      <text class="empty-emoji luc-shopping-cart" />
      <text class="empty-text">{{ $t('cart.empty') }}</text>
      <view class="btn btn-primary" @click="goShopping">{{ $t('cart.goShopping') }}</view>
    </view>

    <template v-else>
      <!-- 降价提醒横幅 -->
      <view v-if="showBanner" class="banner">
        <text class="banner-tag luc-tag" />
        <text class="banner-text">
          {{ $t('cart.priceDropTip', { count: 2, amount: '$18.00' }) }}
        </text>
        <view class="banner-close" :aria-label="$t('cart.closeBanner')" @click="showBanner = false">
          <text class="banner-close-icon luc-x" />
        </view>
      </view>

      <scroll-view scroll-y class="scroll">
        <!-- 有效商品列表 -->
        <view class="item-list">
          <view
            v-for="item in cartItems"
            :key="itemKey(item)"
            class="cart-item"
            @touchstart="onTouchStart"
            @touchend="onTouchEnd"
          >
            <view class="item-check" @click="onCheck(item)">
              <view
                class="checkbox"
                :class="{ checked: item.checked, disabled: item.cartStatus === 'OUT_OF_STOCK' }"
              >
                <text v-if="item.checked"><text class="luc luc-check" /></text>
              </view>
            </view>
            <image :src="$img(item.image)" class="item-image" mode="aspectFill" />
            <view class="item-content">
              <view class="item-header">
                <text class="item-name">{{ item.name }}</text>
              </view>
              <text v-if="item.attrs?.length" class="item-attrs">
                {{ formatAttrs(item.attrs) }}
              </text>
              <view class="item-bottom">
                <view class="price-wrap">
                  <text class="item-price">${{ item.price.toFixed(2) }}</text>
                  <text
                    v-if="item.originalPrice && item.originalPrice > item.price"
                    class="item-original"
                  >
                    ${{ item.originalPrice.toFixed(2) }}
                  </text>
                  <text v-if="item.campaignTag" class="item-campaign-tag">
                    {{ item.campaignTag }}
                  </text>
                  <!-- 缺货标记:仍展示在购物车,但不可勾选结算 -->
                  <text v-if="item.cartStatus === 'OUT_OF_STOCK'" class="out-stock-text">
                    {{ $t('cart.outOfStockLabel') }}
                  </text>
                </view>
                <view v-if="!editing" class="quantity-control">
                  <view
                    class="qty-btn"
                    :aria-label="$t('cart.decreaseQty')"
                    @click.stop="onQtyChange(item, -1)"
                  >
                    -
                  </view>
                  <text class="qty-value">{{ item.quantity }}</text>
                  <view
                    class="qty-btn"
                    :aria-label="$t('cart.increaseQty')"
                    @click.stop="onQtyChange(item, 1)"
                  >
                    +
                  </view>
                </view>
                <view v-else class="item-delete" @click.stop="onDelete(item)">
                  <text class="item-delete-text">{{ $t('cart.delete') }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 失效商品区 -->
        <view v-if="expiredItems.length > 0" class="expired-section">
          <view class="expired-header" @click="expandedExpired = !expandedExpired">
            <text class="expired-header-text">
              {{ $t('cart.expiredTitle', { count: expiredItems.length }) }}
            </text>
            <text class="expired-header-arrow" :class="{ rotated: expandedExpired }">
              <text class="luc luc-chevron-right" />
            </text>
          </view>
          <view v-show="expandedExpired" class="expired-list">
            <view v-for="item in expiredItems" :key="itemKey(item)" class="expired-item">
              <image :src="$img(item.image)" class="expired-image" mode="aspectFill" />
              <view class="expired-info">
                <text class="expired-name">{{ item.name }}</text>
                <text class="expired-status">{{ item.statusText }}</text>
                <text class="expired-price">${{ item.price.toFixed(2) }}</text>
              </view>
              <view class="expired-del" @click.stop="onExpiredDelete(item)">
                <text class="expired-del-text">{{ $t('cart.delete') }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 凑单推荐 -->
        <view v-if="upsellItems.length > 0" class="upsell-section">
          <view class="upsell-header">
            <text class="upsell-title">{{ $t('cart.upsellTitle') }}</text>
            <view class="upsell-tag">
              {{ $t('cart.promoRule', { threshold: 199, discount: 20 }) }}
            </view>
          </view>
          <scroll-view scroll-x class="upsell-scroll" show-scrollbar="false">
            <view class="upsell-track">
              <view
                v-for="item in upsellItems"
                :key="item.id"
                class="upsell-card"
                @click="onUpsellClick(item)"
              >
                <image :src="$img(item.image)" class="upsell-card-image" mode="aspectFill" />
                <text class="upsell-card-name">{{ item.name }}</text>
                <text class="upsell-card-price">${{ item.price.toFixed(2) }}</text>
              </view>
            </view>
          </scroll-view>
        </view>

        <!-- 价格汇总 -->
        <view class="summary-section">
          <text class="summary-title">{{ $t('cart.orderSummary') }}</text>
          <view class="summary-row">
            <text class="summary-label">{{ $t('cart.goodsSubtotal') }}</text>
            <text class="summary-value">${{ subtotal.toFixed(2) }}</text>
          </view>
          <view class="summary-row">
            <text class="summary-label">{{ $t('cart.shipping') }}</text>
            <view class="summary-value-wrap">
              <!-- 有默认地址时展示后端实时运费；无地址时提示结算时计算，避免误显示"免运费" -->
              <text v-if="shippingLoading" class="summary-value">…</text>
              <text v-else-if="shippingLoaded" class="summary-value">
                ${{ shipping.toFixed(2) }}
              </text>
              <text v-else class="summary-value">{{ $t('cart.shippingAtCheckout') }}</text>
              <text
                v-if="!shippingLoading && shippingLoaded && shipping === 0"
                class="summary-badge"
              >
                {{ $t('cart.freeShipping') }}
              </text>
            </view>
          </view>
          <view v-if="memberDiscount > 0" class="summary-row">
            <text class="summary-label">{{ $t('cart.memberDiscount') }}</text>
            <text class="summary-value summary-discount">-${{ memberDiscount.toFixed(2) }}</text>
          </view>
          <view v-if="couponDiscount > 0" class="summary-row">
            <text class="summary-label">{{ couponLabel }}</text>
            <text class="summary-value summary-discount">-${{ couponDiscount.toFixed(2) }}</text>
          </view>
          <view class="summary-total">
            <text class="summary-total-label">{{ $t('cart.payableAmount') }}</text>
            <text class="summary-total-price">${{ total.toFixed(2) }}</text>
          </view>
          <!-- 无可用地址/运费未算出时，合计不含运费，明确提示避免误读 -->
          <text v-if="subtotal > 0 && !shippingLoaded" class="summary-total-hint">
            {{ $t('cart.totalExcludesShipping') }}
          </text>
        </view>

        <view class="bottom-spacer" />
      </scroll-view>

      <!-- 底部结算栏 -->
      <view class="bottom-bar safe-area-bottom">
        <view class="check-all" @click="onCheckAll">
          <view class="checkbox" :class="{ checked: cartStore.isAllChecked }">
            <text v-if="cartStore.isAllChecked"><text class="luc luc-check" /></text>
          </view>
          <text class="check-all-text">{{ $t('cart.selectAll') }}</text>
        </view>
        <view class="bottom-right">
          <view class="bottom-total">
            <text class="bottom-total-label">{{ $t('cart.totalLabel') }}</text>
            <text class="bottom-total-price">${{ total.toFixed(2) }}</text>
          </view>
          <view
            class="btn btn-primary checkout-btn"
            :class="{ disabled: cartStore.selectedQuantity === 0 }"
            @click="onCheckout"
          >
            {{ $t('cart.checkoutBtn', { count: cartStore.selectedQuantity }) }}
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import { useCartStore } from '@/store'
import { addressApi, couponApi, memberApi } from '@/api'
import { getShippingMethods } from '@/api/order'
import { getFlashSales } from '@/api/flashSale'

export default {
  pageTitleKey: 'pageTitle.cartIndex',

  onLoad() {
    // 已登录则从服务端同步购物车
    if (this.cartStore && this.cartStore.syncFromServer) {
      this.cartStore.syncFromServer()
    }
    // 订单摘要的运费按默认地址国家 + 选中商品实时计算，避免硬编码为 0 显示"免运费"
    this.loadShippingCountry()
    // 会员优惠 / 真实优惠券 / 秒杀商品（秒杀不叠加会员折扣）
    this.loadMemberDiscountRate()
    this.loadUnusedCoupons()
    this.loadActiveFlashSales()
  },
  onShow() {
    // onLoad 已首次加载；后续回到购物车时刷新地址/会员/优惠券，避免离开期间变更导致金额过期
    if (this.firstShow) {
      this.firstShow = false
      return
    }
    this.loadShippingCountry()
    this.loadMemberDiscountRate()
    this.loadUnusedCoupons()
    this.loadActiveFlashSales()
  },
  onUnload() {
    if (this.shippingTimer) {
      clearTimeout(this.shippingTimer)
      this.shippingTimer = null
    }
  },
  data() {
    return {
      showBanner: true,
      editing: false,
      expandedExpired: false,
      upsellItems: [],
      // 运费计算上下文：默认地址国家、后端返回的运费、请求序号（丢弃过期响应）
      shippingCountry: '',
      shippingFreight: 0,
      shippingLoading: false,
      shippingLoaded: false,
      shippingReqSeq: 0,
      shippingTimer: null,
      // 首次 onShow 跳过刷新（onLoad 已加载），避免重复请求
      firstShow: true,
      // 会员价折扣系数（1.00 = 原价）与进行中的秒杀商品 ID
      memberDiscountRate: 1,
      activeFlashSaleProductIds: [],
      // 当前用户未使用的优惠券（自动挑选最优券，口径与结算页一致）
      unusedCoupons: [],
      couponsLoaded: false,
    }
  },

  computed: {
    cartStore() {
      return useCartStore()
    },
    /**
     * 有效商品列表：可售(VALID) + 缺货(OUT_OF_STOCK,置灰不可结算)
     * 失效(下架/删除/规格失效)在失效商品区展示
     */
    cartItems() {
      return (this.cartStore.items || []).filter(
        (i) =>
          i.cartStatus !== 'OFF_SALE' &&
          i.cartStatus !== 'PRODUCT_GONE' &&
          i.cartStatus !== 'INVALID_SKU',
      )
    },
    /**
     * 失效商品区：已下架/商品已删除/规格失效 的下单项
     */
    expiredItems() {
      return (this.cartStore.items || [])
        .filter(
          (i) =>
            i.cartStatus === 'OFF_SALE' ||
            i.cartStatus === 'PRODUCT_GONE' ||
            i.cartStatus === 'INVALID_SKU',
        )
        .map((i) => ({ ...i, statusText: this.invalidReason(i.cartStatus) }))
    },
    subtotal() {
      return this.cartStore.selectedPrice
    },
    /** 运费：后端按地址国家 + 商品 + 小计实时计算；无地址/计算失败时按 0 展示并提示结算时计算 */
    shipping() {
      return this.shippingFreight
    },
    /**
     * 会员优惠金额：按会员等级统一折扣，作用于非秒杀商品销售价（与结算页/后端下单口径一致）。
     */
    memberDiscount() {
      const rate = Number(this.memberDiscountRate)
      if (!(rate > 0 && rate < 1)) return 0
      const eligibleSubtotal = (this.cartStore.selectedItems || []).reduce((sum, item) => {
        const productId = item && item.productId
        if (productId != null && this.activeFlashSaleProductIds.includes(productId)) {
          return sum
        }
        return sum + (Number(item && item.price) || 0) * (Number(item && item.quantity) || 0)
      }, 0)
      return Number((eligibleSubtotal * (1 - rate)).toFixed(2))
    },
    /**
     * 当前生效优惠券：优先用户手动选择，否则自动挑选抵扣最大且满足门槛的未使用券。
     * 规则与结算页 bestCoupon 保持一致。
     */
    activeCoupon() {
      const manual = this.cartStore.selectedCoupon
      // 手选券也要复核可用性，避免购物车商品变化后继续展示已不可用的券
      if (manual && this.isCouponUsable(manual)) return manual
      if (!this.couponsLoaded || this.subtotal <= 0) return null
      const available = (this.unusedCoupons || []).filter((c) => this.isCouponUsable(c))
      if (available.length === 0) return null
      const scored = available
        .map((c) => ({ coupon: c, discount: this.couponDiscountOf(c) }))
        .sort((a, b) => b.discount - a.discount || Number(b.coupon.id) - Number(a.coupon.id))
      return scored[0].discount > 0 ? scored[0].coupon : null
    },
    couponDiscount() {
      return this.activeCoupon ? this.couponDiscountOf(this.activeCoupon) : 0
    },
    /** 优惠券行文案：带券名，便于用户确认使用了哪张券 */
    couponLabel() {
      const name = this.activeCoupon && this.activeCoupon.name
      return name ? `${this.$t('cart.couponDiscount')} · ${name}` : this.$t('cart.couponDiscount')
    },
    total() {
      return Math.max(0, this.subtotal + this.shipping - this.memberDiscount - this.couponDiscount)
    },
  },

  watch: {
    // 选中商品数量/金额变化时重新计算运费（含勾选、改数量、删除商品）
    subtotal() {
      this.scheduleLoadShipping()
    },
    'cartStore.selectedQuantity'() {
      this.scheduleLoadShipping()
    },
  },

  methods: {
    /** 优惠券当前是否可用：启用、已开始、未过期、金额门槛满足（与服务端一致，门槛对所有类型生效） */
    isCouponUsable(coupon) {
      if (!coupon || coupon.active === false) return false
      const now = new Date()
      if (coupon.startTime && new Date(String(coupon.startTime).replace(' ', 'T')) > now)
        return false
      if (coupon.endTime && new Date(String(coupon.endTime).replace(' ', 'T')) <= now) return false
      return Number(coupon.minOrderAmount || 0) <= this.subtotal
    },

    /**
     * 单张优惠券的实际抵扣金额（与服务端 computeCouponDiscount 口径一致）：
     * PERCENT 的 discountValue 是价格百分比（95 = 9.5 折），AMOUNT 为固定金额；
     * 同时套用券自身的 maxDiscountAmount 与订单小计封顶。
     */
    couponDiscountOf(coupon) {
      const subtotal = Number(this.subtotal) || 0
      if (!coupon || subtotal <= 0) return 0
      const type = String(coupon.type || '').toUpperCase()
      let discount
      if (type === 'PERCENT') {
        const rate = Number(coupon.discountValue || 0)
        discount = (subtotal * (100 - rate)) / 100
      } else {
        discount = Number(coupon.discountValue || 0)
      }
      if (!Number.isFinite(discount) || discount <= 0) return 0
      const maxDiscount = Number(coupon.maxDiscountAmount)
      if (Number.isFinite(maxDiscount) && maxDiscount > 0) {
        discount = Math.min(discount, maxDiscount)
      }
      return Number(Math.min(subtotal, discount).toFixed(2))
    },

    /** 拉取当前用户未使用的优惠券，供订单摘要自动挑选最优券 */
    async loadUnusedCoupons() {
      try {
        const list = await couponApi.getMyCoupons('UNUSED')
        this.unusedCoupons = Array.isArray(list) ? list : []
      } catch (e) {
        console.warn('[cart] load coupons failed', e)
        this.unusedCoupons = []
      } finally {
        this.couponsLoaded = true
      }
    },

    /** 拉取当前用户会员价折扣系数（无会员/无配置时后端返回 1.00） */
    async loadMemberDiscountRate() {
      try {
        const res = await memberApi.getMemberPriceDiscount()
        const rate = Number(res && res.discountRate)
        this.memberDiscountRate = rate > 0 && rate <= 1 ? rate : 1
      } catch (e) {
        console.warn('[cart] load member discount failed', e)
        this.memberDiscountRate = 1
      }
    },

    /** 拉取进行中的秒杀商品，这些商品不叠加会员折扣 */
    async loadActiveFlashSales() {
      try {
        const res = await getFlashSales({ page: 1, size: 100 })
        const list = Array.isArray(res) ? res : (res && res.records) || []
        this.activeFlashSaleProductIds = list
          .filter((f) => f && f.active !== false && f.productId != null)
          .map((f) => f.productId)
      } catch (e) {
        console.warn('[cart] load flash sales failed', e)
        this.activeFlashSaleProductIds = []
      }
    },

    /**
     * 读取默认收货地址的国家，作为运费试算的入参。
     * 购物车未强制选地址，因此无地址时订单摘要显示"结算时计算"，而不是错误的"免运费"。
     */
    async loadShippingCountry() {
      try {
        const list = await addressApi.getAddressList()
        const addresses = Array.isArray(list) ? list : []
        const addr = addresses.find((a) => a.isDefault) || addresses[0]
        const country = addr && addr.country ? String(addr.country).trim().toUpperCase() : ''
        this.shippingCountry = country
        if (country) this.loadShipping()
      } catch (e) {
        console.warn('[cart] load address for shipping failed', e)
        this.shippingCountry = ''
      }
    },

    /** 防抖：数量/勾选变化时避免连续请求运费 */
    scheduleLoadShipping() {
      if (!this.shippingCountry) return
      if (this.shippingTimer) clearTimeout(this.shippingTimer)
      this.shippingTimer = setTimeout(() => {
        this.shippingTimer = null
        this.loadShipping()
      }, 300)
    },

    /** 按默认地址国家 + 已选商品向服务端试算运费（与结算页同一接口与口径） */
    async loadShipping() {
      if (!this.shippingCountry) return
      const selected = this.cartStore.selectedItems || []
      if (selected.length === 0) {
        // 未勾选商品时不算运费，也不展示"免运费"徽章
        this.shippingFreight = 0
        this.shippingLoaded = false
        return
      }
      const seq = ++this.shippingReqSeq
      this.shippingLoading = true
      try {
        const items = selected.map((it) => ({
          productId: it.productId,
          skuId: it.skuId || it.variationId || null,
          quantity: it.quantity || 1,
        }))
        const list = await getShippingMethods({
          country: this.shippingCountry,
          items,
          subtotal: this.subtotal,
          currency: 'USD',
        })
        // 丢弃过期响应：用户连续改数量时只认最后一次请求
        if (seq !== this.shippingReqSeq) return
        const methods = Array.isArray(list) ? list : []
        // 与结算页默认选中规则一致：取第一个启用中的配送方式
        const first = methods.find((m) => m.active !== false) || methods[0]
        if (!first) {
          // 该国家无可用配送方式/未配置运费：不显示"免运费"，交由结算页给出明确结果
          this.shippingFreight = 0
          this.shippingLoaded = false
          return
        }
        this.shippingFreight = Number(first.freight) || 0
        this.shippingLoaded = true
      } catch (e) {
        if (seq !== this.shippingReqSeq) return
        console.warn('[cart] load shipping failed', e)
        this.shippingFreight = 0
        this.shippingLoaded = false
      } finally {
        if (seq === this.shippingReqSeq) this.shippingLoading = false
      }
    },

    itemKey(item) {
      return item.variationId || item.skuId || item.productId
    },
    formatAttrs(attrs) {
      if (!attrs || !attrs.length) return ''
      return attrs.map((a) => a.value).join(' / ')
    },
    /** 失效原因文案(电商购物车"失效商品"区展示) */
    invalidReason(status) {
      const map = {
        OFF_SALE: this.$t('cart.offSale'),
        PRODUCT_GONE: this.$t('cart.productGone'),
        INVALID_SKU: this.$t('cart.invalidSku'),
      }
      return map[status] || this.$t('cart.invalidDefault')
    },
    onExpiredDelete(item) {
      const key = this.itemKey(item)
      this.cartStore.removeItem(key)
      uni.showToast({ title: this.$t('cart.removed'), icon: 'success' })
    },
    onCheck(item) {
      if (item.cartStatus === 'OUT_OF_STOCK') {
        uni.showToast({ title: this.$t('cart.outOfStock'), icon: 'none' })
        return
      }
      const key = this.itemKey(item)
      this.cartStore.toggleCheck(key)
    },
    onCheckAll() {
      this.cartStore.toggleCheckAll(!this.cartStore.isAllChecked)
    },
    onQtyChange(item, delta) {
      const key = this.itemKey(item)
      const next = item.quantity + delta
      if (next < 1) {
        this.onDelete(item)
        return
      }
      // 库存上限:加购数量不允许超过库存(缺失库存信息时交由后端校验拦截)
      const stockLimit =
        typeof item.stock === 'number' && Number.isFinite(item.stock)
          ? Math.max(0, item.stock)
          : null
      if (delta > 0 && stockLimit !== null && item.quantity >= stockLimit) {
        uni.showToast({
          title: stockLimit <= 0 ? this.$t('cart.soldOut') : this.$t('cart.stockShort'),
          icon: 'none',
        })
        return
      }
      this.cartStore.updateQuantity(key, next)
    },
    onDelete(item) {
      uni.showModal({
        title: this.$t('cart.deleteConfirmTitle'),
        content: this.$t('cart.deleteConfirmContent'),
        success: (res) => {
          if (res.confirm) {
            const key = this.itemKey(item)
            this.cartStore.removeItem(key)
            uni.showToast({ title: this.$t('cart.removed'), icon: 'success' })
          }
        },
      })
    },
    onToggleEdit() {
      this.editing = !this.editing
    },
    onCheckout() {
      if (this.cartStore.selectedQuantity === 0) {
        uni.showToast({ title: this.$t('cart.selectItemsFirst'), icon: 'none' })
        return
      }
      // 清理可能残留的“立即购买”临时单品，避免劫持购物车结算
      this.cartStore.clearBuyNow()
      uni.navigateTo({ url: '/pages/cart/checkout' })
    },
    onUpsellClick(item) {
      uni.navigateTo({
        url: `/pages/goods/detail?id=${item.id}`,
      })
    },
    goShopping() {
      // 分类页已从 tabBar 移出，改为普通页面跳转
      uni.navigateTo({ url: '/pages/tabbar/category' })
    },
    goBack() {
      uni.navigateBack()
    },
    onTouchStart() {
      // 预留滑动交互
    },
    onTouchEnd() {
      // 预留滑动交互
    },
  },
}
</script>

<style lang="scss" scoped>
.cart {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--color-background);
}

/* ===== 空状态 ===== */
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

/* ===== 顶部导航栏（自定义导航，替代原生导航栏） ===== */
.header {
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
  flex-shrink: 0;
  /* 顶部状态栏安全区：取 --status-bar-height 与 env(safe-area-inset-top) 的**较大值**而非相加，
     iOS 支持 env() 时二者同为状态栏高度，相加会让顶栏被推低一倍。
     原先用 JS 的 uni.getSystemInfoSync().statusBarHeight 绑定内联 paddingTop，
     在 H5 下该值为 0 却被 `|| 20` 当成 falsy 退化成 20px，且 data 初值 20 会有首帧跳动。 */
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  box-sizing: content-box;
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 var(--space-md);
}

.header-back {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.header-edit {
  min-width: 72rpx;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 8rpx 0;
  box-sizing: border-box;
}

.header-edit-text {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

/* ===== 降价提醒横幅 ===== */
.banner {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: var(--space-sm) var(--space-md) 0;
  padding: 20rpx var(--space-sm);
  border-radius: var(--radius-md);
  background: var(--color-primary-light);
  border: 1rpx solid var(--color-primary);
  flex-shrink: 0;
}

.banner-tag {
  font-size: 28rpx;
  flex-shrink: 0;
}

.banner-text {
  flex: 1;
  font-size: var(--font-size-xs);
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-medium);
}

.banner-close {
  width: 36rpx;
  height: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-primary);
  flex-shrink: 0;
}

.banner-close-icon {
  font-size: 20rpx;
  color: #fff;
  line-height: 1;
}

/* ===== 滚动容器 ===== */
.scroll {
  flex: 1;
}

/* ===== 商品列表 ===== */
.item-list {
  padding: var(--space-sm) var(--space-md);
}

.cart-item {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: var(--space-sm);
  margin-bottom: var(--space-sm);
  background: var(--color-surface);
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
}

.item-check {
  display: flex;
  align-items: center;
  justify-content: center;
  /* 让勾选圈与商品图片视觉上垂直居中 */
  min-height: 96rpx;
  padding-top: 0;
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid var(--color-divider);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: transparent;
  font-size: 24rpx;
  line-height: 1;
  flex-shrink: 0;
  background-color: var(--color-surface);
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    transform 0.18s ease;
  box-sizing: border-box;
}

.checkbox.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-text);
  /* 选中态轻微缩放,提升反馈感 */
  transform: scale(1.05);
  box-shadow: 0 2rpx 8rpx rgba(219, 201, 138, 0.45);
}

.item-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  flex-shrink: 0;
}

.item-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.item-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.item-name {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  line-height: 1.4;
  font-weight: var(--font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.item-attrs {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.item-price {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary-dark);
}

/* 数量加减器 */
.quantity-control {
  display: inline-flex;
  align-items: center;
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-pill);
  overflow: hidden;
}

.qty-btn {
  width: 52rpx;
  height: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.qty-value {
  min-width: 56rpx;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
  border-left: 1rpx solid var(--color-divider);
  border-right: 1rpx solid var(--color-divider);
}

/* 编辑模式下的删除按钮 */
.item-delete {
  padding: 8rpx 16rpx;
  border-radius: var(--radius-pill);
  background: var(--color-danger);
}

.item-delete-text {
  font-size: var(--font-size-xs);
  color: #fff;
  font-weight: var(--font-weight-medium);
}

/* ===== 失效商品区 ===== */
.expired-section {
  padding: 0 var(--space-md);
  margin-top: var(--space-xs);
}

.expired-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx var(--space-sm);
  background: var(--color-surface);
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
}

.expired-header-text {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-medium);
}

.expired-header-arrow {
  font-size: 32rpx;
  color: var(--color-text-tertiary);
  transition: transform 0.2s ease;
}

.expired-header-arrow.rotated {
  transform: rotate(90deg);
}

.expired-list {
  margin-top: var(--space-sm);
}

.expired-item {
  display: flex;
  gap: 16rpx;
  padding: var(--space-sm);
  background: var(--color-surface);
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
  opacity: 0.45;
}

.expired-image {
  width: 128rpx;
  height: 128rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  flex-shrink: 0;
}

.expired-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 4rpx 0;
}

.expired-name {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.expired-status {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.expired-price {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  text-decoration: line-through;
}

/* ===== 凑单推荐 ===== */
.upsell-section {
  margin-top: var(--space-md);
}

.upsell-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-md);
  margin-bottom: 16rpx;
}

.upsell-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.upsell-tag {
  display: inline-flex;
  align-items: center;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: var(--color-danger);
  color: #fff;
  font-size: 20rpx;
  font-weight: var(--font-weight-bold);
  line-height: 1;
}

.upsell-scroll {
  padding: 0 var(--space-md) 8rpx;
  overflow: hidden;
}

.upsell-track {
  display: flex;
  gap: 16rpx;
}

.upsell-card {
  flex-shrink: 0;
  width: 224rpx;
  padding: 12rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
}

.upsell-card-image {
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  margin-bottom: 12rpx;
  background: var(--color-background);
}

.upsell-card-name {
  display: block;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upsell-card-price {
  display: block;
  margin-top: 6rpx;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--color-danger);
}

/* ===== 价格汇总 ===== */
.summary-section {
  margin: var(--space-md) var(--space-md) 0;
  padding: var(--space-md);
  background: var(--color-surface);
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
}

.summary-title {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: var(--space-sm);
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.summary-row:last-of-type {
  border-bottom: none;
}

.summary-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.summary-value {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.summary-value-wrap {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.summary-badge {
  font-size: 18rpx;
  padding: 4rpx 10rpx;
  border-radius: 6rpx;
  background: var(--color-success);
  color: #fff;
  line-height: 1;
}

.summary-discount {
  color: var(--color-success);
}

.summary-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--space-sm);
}

.summary-total-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.summary-total-price {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
}

/* 合计不含运费时的提示 */
.summary-total-hint {
  display: block;
  margin-top: 6rpx;
  text-align: right;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

/* ===== 底部留白 ===== */
.bottom-spacer {
  /* 固定栏高度(100rpx)+ 余量(40rpx)+ 预留 iPhone 安全区 */
  height: calc(140rpx + env(safe-area-inset-bottom));
}

/* ===== 底部结算栏 ===== */
.bottom-bar {
  /* 固定在视口底部,不被 scroll-view 滚动带走 */
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100rpx;
  padding: 0 var(--space-md);
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
  /* safe-area-bottom 兼容 iPhone 刘海/底部指示条 */
  padding-bottom: env(safe-area-inset-bottom);
}

.check-all {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.check-all-text {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.bottom-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.bottom-total {
  text-align: right;
}

.bottom-total-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.bottom-total-price {
  font-size: 32rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
}

.checkout-btn {
  height: 72rpx;
  padding: 0 40rpx;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-text);
  line-height: 72rpx;
}

.checkout-btn.disabled {
  opacity: 0.45;
}

/* ===== 缺货商品 ===== */
.price-wrap {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10rpx;
  min-width: 0;
}

.item-original {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
  text-decoration: line-through;
}

.item-campaign-tag {
  font-size: 18rpx;
  color: #e5484d;
  background: rgba(229, 72, 77, 0.1);
  border-radius: 6rpx;
  padding: 2rpx 8rpx;
}

.out-stock-text {
  flex-shrink: 0;
  font-size: 18rpx;
  line-height: 1;
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: var(--color-danger);
  color: #fff;
}

.checkbox.disabled {
  opacity: 0.35;
}

/* 失效商品区单行删除按钮 */
.expired-del {
  flex-shrink: 0;
  align-self: center;
  padding: 10rpx 14rpx;
}

.expired-del-text {
  font-size: var(--font-size-xs);
  color: var(--color-danger);
  font-weight: var(--font-weight-medium);
}
</style>
