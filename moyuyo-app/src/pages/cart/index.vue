<template>
  <view class="cart">
    <!-- 顶部导航栏：页面已关闭原生导航(navigationStyle: custom)，统一在此渲染，避免两条导航栏 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="header-inner">
        <view class="header-back" aria-label="返回" @click="goBack">
          <text class="header-back-icon luc luc-arrow-left" />
        </view>
        <text class="header-title">{{ $t('pageTitle.cartIndex') }}</text>
        <view class="header-edit" @click="onToggleEdit">
          <text v-if="cartItems.length > 0" class="header-edit-text">
            {{ editing ? '完成' : '编辑' }}
          </text>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view v-if="cartItems.length === 0 && expiredItems.length === 0" class="empty">
      <text class="empty-emoji luc-shopping-cart" />
      <text class="empty-text">购物车是空的</text>
      <view class="btn btn-primary" @click="goShopping">去逛逛</view>
    </view>

    <template v-else>
      <!-- 降价提醒横幅 -->
      <view v-if="showBanner" class="banner">
        <text class="banner-tag luc-tag" />
        <text class="banner-text">2件商品降价啦，已为您节省$18.00</text>
        <view class="banner-close" aria-label="关闭提醒" @click="showBanner = false">
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
            <image :src="item.image" class="item-image" mode="aspectFill" />
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
                  <!-- 缺货标记:仍展示在购物车,但不可勾选结算 -->
                  <text v-if="item.cartStatus === 'OUT_OF_STOCK'" class="out-stock-text">缺货</text>
                </view>
                <view v-if="!editing" class="quantity-control">
                  <view class="qty-btn" aria-label="减少数量" @click.stop="onQtyChange(item, -1)">
                    -
                  </view>
                  <text class="qty-value">{{ item.quantity }}</text>
                  <view class="qty-btn" aria-label="增加数量" @click.stop="onQtyChange(item, 1)">
                    +
                  </view>
                </view>
                <view v-else class="item-delete" @click.stop="onDelete(item)">
                  <text class="item-delete-text">删除</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 失效商品区 -->
        <view v-if="expiredItems.length > 0" class="expired-section">
          <view class="expired-header" @click="expandedExpired = !expandedExpired">
            <text class="expired-header-text">失效商品 ({{ expiredItems.length }})</text>
            <text class="expired-header-arrow" :class="{ rotated: expandedExpired }">
              <text class="luc luc-chevron-right" />
            </text>
          </view>
          <view v-show="expandedExpired" class="expired-list">
            <view v-for="item in expiredItems" :key="itemKey(item)" class="expired-item">
              <image :src="item.image" class="expired-image" mode="aspectFill" />
              <view class="expired-info">
                <text class="expired-name">{{ item.name }}</text>
                <text class="expired-status">{{ item.statusText }}</text>
                <text class="expired-price">${{ item.price.toFixed(2) }}</text>
              </view>
              <view class="expired-del" @click.stop="onExpiredDelete(item)">
                <text class="expired-del-text">删除</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 凑单推荐 -->
        <view v-if="upsellItems.length > 0" class="upsell-section">
          <view class="upsell-header">
            <text class="upsell-title">凑单推荐</text>
            <view class="upsell-tag">满199减20</view>
          </view>
          <scroll-view scroll-x class="upsell-scroll" show-scrollbar="false">
            <view class="upsell-track">
              <view
                v-for="item in upsellItems"
                :key="item.id"
                class="upsell-card"
                @click="onUpsellClick(item)"
              >
                <image :src="item.image" class="upsell-card-image" mode="aspectFill" />
                <text class="upsell-card-name">{{ item.name }}</text>
                <text class="upsell-card-price">${{ item.price.toFixed(2) }}</text>
              </view>
            </view>
          </scroll-view>
        </view>

        <!-- 价格汇总 -->
        <view class="summary-section">
          <text class="summary-title">订单摘要</text>
          <view class="summary-row">
            <text class="summary-label">商品合计</text>
            <text class="summary-value">${{ subtotal.toFixed(2) }}</text>
          </view>
          <view class="summary-row">
            <text class="summary-label">运费</text>
            <view class="summary-value-wrap">
              <text class="summary-value">${{ shipping.toFixed(2) }}</text>
              <text v-if="shipping === 0" class="summary-badge">免运费</text>
            </view>
          </view>
          <view class="summary-row">
            <text class="summary-label">优惠</text>
            <text class="summary-value summary-discount">-${{ discountAmount.toFixed(2) }}</text>
          </view>
          <view class="summary-total">
            <text class="summary-total-label">应付金额</text>
            <text class="summary-total-price">${{ total.toFixed(2) }}</text>
          </view>
        </view>

        <view class="bottom-spacer" />
      </scroll-view>

      <!-- 底部结算栏 -->
      <view class="bottom-bar safe-area-bottom">
        <view class="check-all" @click="onCheckAll">
          <view class="checkbox" :class="{ checked: cartStore.isAllChecked }">
            <text v-if="cartStore.isAllChecked"><text class="luc luc-check" /></text>
          </view>
          <text class="check-all-text">全选</text>
        </view>
        <view class="bottom-right">
          <view class="bottom-total">
            <text class="bottom-total-label">合计:</text>
            <text class="bottom-total-price">${{ total.toFixed(2) }}</text>
          </view>
          <view
            class="btn btn-primary checkout-btn"
            :class="{ disabled: cartStore.selectedQuantity === 0 }"
            @click="onCheckout"
          >
            结算({{ cartStore.selectedQuantity }})
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import { useCartStore } from '@/store'

export default {
  pageTitleKey: 'pageTitle.cartIndex',

  onLoad() {
    // 自定义导航：按系统状态栏高度下推，避免内容顶到状态栏
    try {
      const sysInfo = uni.getSystemInfoSync ? uni.getSystemInfoSync() : null
      this.statusBarHeight = (sysInfo && sysInfo.statusBarHeight) || 20
    } catch (e) {
      this.statusBarHeight = 20
    }
    // 已登录则从服务端同步购物车
    if (this.cartStore && this.cartStore.syncFromServer) {
      this.cartStore.syncFromServer()
    }
  },
  data() {
    return {
      statusBarHeight: 20,
      showBanner: true,
      editing: false,
      expandedExpired: false,
      upsellItems: [],
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
    shipping() {
      return 0
    },
    discountAmount() {
      return this.subtotal >= 199 ? 20 : 0
    },
    total() {
      return Math.max(0, this.subtotal + this.shipping - this.discountAmount)
    },
  },

  methods: {
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
        OFF_SALE: '已下架',
        PRODUCT_GONE: '商品已失效',
        INVALID_SKU: '规格已失效',
      }
      return map[status] || '已失效'
    },
    onExpiredDelete(item) {
      const key = this.itemKey(item)
      this.cartStore.removeItem(key)
      uni.showToast({ title: '已移除', icon: 'success' })
    },
    onCheck(item) {
      if (item.cartStatus === 'OUT_OF_STOCK') {
        uni.showToast({ title: '商品缺货,暂不能结算', icon: 'none' })
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
        uni.showToast({ title: stockLimit <= 0 ? '该商品已售罄' : '库存不足', icon: 'none' })
        return
      }
      this.cartStore.updateQuantity(key, next)
    },
    onDelete(item) {
      uni.showModal({
        title: '确认删除',
        content: '确定将该商品从购物车移除吗？',
        success: (res) => {
          if (res.confirm) {
            const key = this.itemKey(item)
            this.cartStore.removeItem(key)
            uni.showToast({ title: '已移除', icon: 'success' })
          }
        },
      })
    },
    onToggleEdit() {
      this.editing = !this.editing
    },
    onCheckout() {
      if (this.cartStore.selectedQuantity === 0) {
        uni.showToast({ title: '请选择商品', icon: 'none' })
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
      uni.switchTab({ url: '/pages/tabbar/category' })
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
  gap: 10rpx;
  min-width: 0;
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
