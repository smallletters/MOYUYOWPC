<template>
  <view class="returns">
    <view class="page-header">
      <view class="back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">{{ $t('returns.title') }}</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 未指定订单：先选择要售后的订单（对齐亚马逊从订单列表发起退货） -->
      <block v-if="!orderId">
        <view class="section">
          <text class="section-title">{{ $t('returns.selectOrder') }}</text>
        </view>
        <view v-if="orderLoading" class="loading">{{ $t('common.loading') }}</view>
        <view v-else-if="orders.length === 0" class="empty-state">
          <text class="empty-title">{{ $t('returns.orderEmpty') }}</text>
          <view class="empty-btn" @click="goOrders">{{ $t('returns.goOrders') }}</view>
        </view>
        <view
          v-for="o in orders"
          :key="o.id"
          class="order-card"
          @click="selectOrder(o)">
          <view class="order-top">
            <text class="order-no">{{ $t('returns.orderNo', { no: o.orderNo }) }}</text>
            <text class="order-status">{{ orderStatusLabel(o.status) }}</text>
          </view>
          <view class="order-body">
            <image
              :src="resolveItemImage(firstItem(o).mainImage)"
              mode="aspectFill"
              class="order-img"
            />
            <view class="order-info">
              <text class="order-name">{{ firstItem(o).productName || '-' }}</text>
              <text class="order-count">
                {{ $t('returns.itemCount', { count: (o.items || []).length }) }}
              </text>
            </view>
            <text class="order-amount">${{ o.payAmount }}</text>
          </view>
        </view>
      </block>

      <block v-else>
        <!-- 售后类型：未发货只能仅退款；已发货可选退货退款/仅退款/换货 -->
        <view class="section">
          <text class="section-title">{{ $t('returns.typeLabel') }}</text>
          <scroll-view scroll-x class="type-scroll">
            <view class="type-list">
              <view
                v-for="t in typeOptions"
                :key="t.value"
                class="type-btn"
                :class="{ active: form.type === t.value }"
                @click="onTypeChange(t.value)"
              >
                {{ t.label }}
              </view>
            </view>
          </scroll-view>
        </view>

        <!-- 商品与数量 -->
        <view class="section">
          <text class="section-title">{{ $t('returns.itemLabel') }}</text>
          <view
            v-for="item in orderItems"
            :key="item.id"
            class="goods-card"
            :class="{ selected: form.orderItemId === item.id, disabled: isItemSoldOut(item) }"
            @click="selectItem(item)"
          >
            <view class="radio" :class="{ checked: form.orderItemId === item.id }">
              <text v-if="form.orderItemId === item.id" class="luc luc-check" />
            </view>
            <image mode="aspectFill" :src="resolveItemImage(item.mainImage)" class="goods-image" />
            <view class="goods-info">
              <text class="goods-name">{{ item.productName }}</text>
              <text class="goods-spec">{{ item.skuSpec || '' }}</text>
              <view class="goods-bottom">
                <text class="goods-price">${{ item.price }}</text>
                <text class="goods-qty">×{{ item.quantity }}</text>
              </view>
              <!-- 分次退货：展示已申请件数，全部退完后该商品不可再选 -->
              <text v-if="isItemSoldOut(item)" class="goods-quota sold-out">
                {{ $t('returns.fullyReturned') }}
              </text>
              <text v-else-if="item.afterSalesQuantity > 0" class="goods-quota">
                {{
                  $t('returns.partlyReturned', {
                    returned: item.afterSalesQuantity,
                    remain: item.returnableQuantity,
                  })
                }}
              </text>
            </view>
          </view>

          <view v-if="selectedItem" class="qty-row">
            <text class="qty-label">{{ $t('returns.quantityLabel') }}</text>
            <view class="qty-stepper">
              <view
                class="qty-btn"
                :class="{ disabled: form.quantity <= 1 }"
                @click="changeQty(-1)"
              >
                <text>-</text>
              </view>
              <text class="qty-value">{{ form.quantity }}</text>
              <view
                class="qty-btn"
                :class="{ disabled: form.quantity >= maxQuantity }"
                @click="changeQty(1)"
              >
                <text>+</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 售后原因 -->
        <view class="section">
          <text class="section-title">{{ $t('returns.reasonLabel') }}</text>
          <view class="reason-grid">
            <view
              v-for="r in reasonOptions"
              :key="r"
              class="reason-chip"
              :class="{ selected: form.reason === r }"
              @click="form.reason = r"
            >
              {{ r }}
            </view>
          </view>
        </view>

        <!-- 退款金额：退货退款按商品可退金额锁定，仅退款可调整，换货无需退款 -->
        <view class="section">
          <text class="section-title">{{ $t('returns.amountLabel') }}</text>
          <view class="amount-row">
            <text class="amount-label">{{ $t('returns.amountApply') }}</text>
            <input
              v-model.number="form.amount"
              type="number"
              class="amount-input"
              :disabled="form.type !== 'REFUND_ONLY'"
            >
            <text class="amount-unit">{{ currencySymbol }}</text>
          </view>
          <text class="amount-tip">
            <template v-if="form.type === 'EXCHANGE'">
              {{ $t('returns.exchangeNoRefund') }}
            </template>
            <template v-else>{{ $t('returns.amountTip', { amount: maxRefundAmount }) }}</template>
          </text>
        </view>

        <!-- 说明 -->
        <view class="section">
          <text class="section-title">{{ $t('returns.descLabel') }}</text>
          <textarea
            v-model="form.description"
            class="desc-input"
            :placeholder="$t('returns.descPlaceholder')"
            maxlength="200"
          />
        </view>

        <!-- 上传凭证 -->
        <view class="section">
          <text class="section-title">{{ $t('returns.uploadLabel') }}</text>
          <view class="upload-list">
            <view v-for="(img, i) in uploadPaths" :key="i" class="upload-item">
              <image mode="aspectFill" :src="img" class="upload-image" />
              <view class="upload-remove" @click="uploadPaths.splice(i, 1)">
                <text class="luc luc-x" />
              </view>
            </view>
            <view v-if="uploadPaths.length < 3" class="upload-add" @click="onAddImage">
              <text>+</text>
              <text class="upload-add-text">{{ $t('returns.addImage') }}</text>
            </view>
          </view>
        </view>

        <view class="bottom-bar safe-area-bottom">
          <view class="btn-primary" :class="{ disabled: submitting }" @click="onSubmit">
            {{ submitting ? $t('returns.submitting') : $t('returns.submit') }}
          </view>
        </view>
      </block>
    </scroll-view>
  </view>
</template>

<script>
import { orderApi, afterSalesApi, uploadApi } from '@/api'
import { toAbsoluteImageUrl } from '@/utils/imageUrl'
import { i18n } from '@/i18n'

// 已发货后可申请退货退款；未发货仅支持仅退款
const SHIPPED_STATUSES = ['SHIPPED', 'RECEIVED', 'COMPLETED']
// 可发起售后的订单状态（与后端校验保持一致）
const ELIGIBLE_ORDER_STATUSES = ['PAID', 'PENDING_SHIP', 'SHIPPED', 'RECEIVED', 'COMPLETED']

export default {
  pageTitleKey: 'pageTitle.orderReturns',

  data() {
    return {
      orderId: null,
      orderLoading: false,
      orders: [],
      order: null,
      form: {
        orderItemId: null,
        type: '',
        reason: '',
        amount: 0,
        quantity: 1,
        description: '',
      },
      uploadPaths: [],
      submitting: false,
      reasonOptions: [],
      localeVersion: 0,
    }
  },

  computed: {
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },

    orderItems() {
      return (this.order && this.order.items) || []
    },

    selectedItem() {
      return this.orderItems.find((item) => item.id === this.form.orderItemId) || null
    },

    maxQuantity() {
      const item = this.selectedItem
      if (!item) return 1
      // 分次退货：剩余可退数量 = 购买数量 - 已申请售后数量（后端下发的 returnableQuantity）
      const remaining =
        item.returnableQuantity != null
          ? Number(item.returnableQuantity)
          : Number(item.quantity) || 1
      return Math.max(remaining, 1)
    },

    typeOptions() {
      // 依赖 localeVersion，切换语言时重新生成类型文案
      void this.localeVersion
      const shipped = SHIPPED_STATUSES.includes(this.order && this.order.status)
      const list = []
      if (shipped) {
        list.push({ value: 'RETURN_REFUND', label: i18n.t('returns.types.RETURN_REFUND') })
      }
      list.push({ value: 'REFUND_ONLY', label: i18n.t('returns.types.REFUND_ONLY') })
      if (shipped) {
        list.push({ value: 'EXCHANGE', label: i18n.t('returns.types.EXCHANGE') })
      }
      return list
    },

    maxRefundAmount() {
      const item = this.selectedItem
      if (!item) return orderRefundable(this.order)
      const itemQty = Number(item.quantity) || 1
      const subtotal =
        item.subtotal != null ? Number(item.subtotal) : (Number(item.price) || 0) * itemQty
      // 本次数量对应的退款金额，且不超过后端下发的该商品行剩余可退金额
      const proportional = (subtotal * Number(this.form.quantity || 1)) / itemQty
      const itemRemaining = item.returnableAmount != null ? Number(item.returnableAmount) : subtotal
      return round2(Math.min(proportional, itemRemaining, orderRefundable(this.order)))
    },
  },

  watch: {
    'form.quantity'() {
      this.syncAmountByType()
    },
  },

  onLoad(query) {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
      this.reasonOptions = i18n.t('returns.reasons')
    })
    this.reasonOptions = i18n.t('returns.reasons')
    if (query.orderId) {
      this.orderId = query.orderId
      this.loadOrder()
    } else {
      this.loadOrders()
    }
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    /** 相对路径图片补全为绝对地址，避免真机空白 */
    resolveItemImage(url) {
      return toAbsoluteImageUrl(url) || ''
    },

    firstItem(order) {
      return (order && order.items && order.items[0]) || {}
    },

    orderStatusLabel(status) {
      const label = i18n.t(`orderStatus.${status}`)
      return label && label !== `orderStatus.${status}` ? label : status
    },

    goBack() {
      uni.navigateBack()
    },

    goOrders() {
      uni.navigateTo({ url: '/pages/order/list' })
    },

    async loadOrders() {
      this.orderLoading = true
      try {
        const res = await orderApi.getOrderList({ page: 1, size: 20 })
        const records = (res && res.records) || []
        this.orders = records.filter((o) => ELIGIBLE_ORDER_STATUSES.includes(o.status))
      } catch (e) {
        uni.showToast({ title: i18n.t('returns.loadFailed'), icon: 'none' })
      } finally {
        this.orderLoading = false
      }
    },

    selectOrder(order) {
      this.orderId = order.id
      // 列表数据不含售后额度字段，进入申请前重新拉取订单详情
      this.loadOrder()
    },

    async loadOrder() {
      this.orderLoading = true
      try {
        this.order = await orderApi.getOrderDetail(this.orderId)
        this.initForm()
      } catch (e) {
        uni.showToast({ title: i18n.t('returns.loadFailed'), icon: 'none' })
      } finally {
        this.orderLoading = false
      }
    },

    /** 初始化表单：默认选中第一个仍有剩余可退数量的商品 */
    initForm() {
      const items = this.orderItems
      const firstAvailable = items.find((item) => !this.isItemSoldOut(item)) || items[0] || null
      this.form.orderItemId = firstAvailable ? firstAvailable.id : null
      this.form.quantity = 1
      this.form.type = this.typeOptions[0] ? this.typeOptions[0].value : ''
      this.form.reason = ''
      this.form.description = ''
      this.syncAmountByType()
    },

    onTypeChange(type) {
      this.form.type = type
      this.syncAmountByType()
    },

    /** 该商品行是否已全部申请过售后（剩余可退数量为 0） */
    isItemSoldOut(item) {
      return Boolean(item) && Number(item.returnableQuantity) === 0
    },

    selectItem(item) {
      // 已全部退完的商品不可再选，避免提交后被服务端拒绝
      if (this.isItemSoldOut(item)) {
        uni.showToast({ title: i18n.t('returns.fullyReturned'), icon: 'none' })
        return
      }
      this.form.orderItemId = item.id
      this.form.quantity = 1
      this.syncAmountByType()
    },

    changeQty(delta) {
      const next = Number(this.form.quantity) + delta
      if (next < 1 || next > this.maxQuantity) return
      this.form.quantity = next
    },

    /** 金额随类型/商品/数量联动 */
    syncAmountByType() {
      if (this.form.type === 'EXCHANGE') {
        this.form.amount = 0
        return
      }
      const max = this.maxRefundAmount
      // 退货退款锁定为商品可退金额；仅退款默认带出可退金额，用户可下调
      this.form.amount = round2(max)
    },

    onAddImage() {
      uni.chooseImage({
        count: 3 - this.uploadPaths.length,
        success: (res) => {
          this.uploadPaths.push(...res.tempFilePaths)
        },
      })
    },

    async onSubmit() {
      if (this.submitting) return
      if (!this.form.type) {
        uni.showToast({ title: i18n.t('returns.selectTypeFirst'), icon: 'none' })
        return
      }
      if (!this.form.orderItemId) {
        uni.showToast({ title: i18n.t('returns.selectItemFirst'), icon: 'none' })
        return
      }
      if (!this.form.reason) {
        uni.showToast({ title: i18n.t('returns.selectReasonFirst'), icon: 'none' })
        return
      }
      // 分次退货：数量不能超过该商品行剩余可退数量
      if (this.selectedItem && this.isItemSoldOut(this.selectedItem)) {
        uni.showToast({ title: i18n.t('returns.fullyReturned'), icon: 'none' })
        return
      }
      if (Number(this.form.quantity) > this.maxQuantity) {
        uni.showToast({ title: i18n.t('returns.quantityExceeded'), icon: 'none' })
        return
      }
      if (this.form.type !== 'EXCHANGE') {
        const amount = Number(this.form.amount)
        if (!(amount > 0) || amount > this.maxRefundAmount) {
          uni.showToast({ title: i18n.t('returns.amountInvalid'), icon: 'none' })
          return
        }
      }

      this.submitting = true
      uni.showLoading({ title: i18n.t('returns.submitting'), mask: true })
      try {
        // 先上传凭证图片，再提交售后单（图片失败不阻断主流程）
        const images = this.uploadPaths.length
          ? await uploadApi.uploadImages(this.uploadPaths, 'USER')
          : []
        const payload = {
          orderId: this.orderId,
          orderItemId: this.form.orderItemId,
          type: this.form.type,
          reason: this.form.reason,
          quantity: Number(this.form.quantity),
          description: this.form.description,
          images: images.length ? JSON.stringify(images) : undefined,
        }
        if (this.form.type !== 'EXCHANGE') {
          payload.amount = round2(Number(this.form.amount))
        }
        const created = await afterSalesApi.createAfterSale(payload)
        uni.hideLoading()
        uni.showToast({ title: i18n.t('returns.submitted'), icon: 'success' })
        // 提交后直接进入售后详情，用户可立即查看进度并（审核通过后）填写退货运单号
        setTimeout(() => {
          if (created && created.id) {
            uni.redirectTo({ url: `/pages/order/after-sales-detail?id=${created.id}` })
          } else {
            uni.navigateBack()
          }
        }, 1200)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || i18n.t('returns.submitFailed'), icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
  },
}

/** 订单剩余可退金额（后端详情已返回 refundableAmount，缺失时回退实付金额） */
function orderRefundable(order) {
  if (!order) return 0
  if (order.refundableAmount != null) return Number(order.refundableAmount)
  return Number(order.payAmount) || 0
}

function round2(value) {
  return Math.round((Number(value) || 0) * 100) / 100
}
</script>

<style lang="scss" scoped>
.returns {
  width: 100%;
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 140rpx;
  /* 横向类型切换内容可能超宽，禁止页面级横向滚动，避免整页被撑宽 */
  overflow-x: hidden;
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
  /* 宽度跟随屏幕，box-sizing 让 padding 不额外撑宽容器 */
  width: 100%;
  box-sizing: border-box;
  padding: 24rpx;
  overflow-x: hidden;
}

.type-scroll {
  /* 横向滚动区域限制在内容宽度内，避免把父容器撑宽 */
  width: 100%;
  max-width: 100%;
  white-space: nowrap;
}

.type-list {
  display: inline-flex;
  gap: 16rpx;
  padding-right: 24rpx;
}

.type-btn {
  display: inline-flex;
  align-items: center;
  height: 72rpx;
  padding: 0 32rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: 999rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.type-btn.active {
  background: var(--color-primary);
  color: var(--color-text);
  border-color: var(--color-primary);
}

.goods-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  margin-bottom: 16rpx;
}

.goods-card.selected {
  border-color: var(--color-primary);
}

.goods-card.disabled {
  opacity: 0.55;
}

.goods-quota {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.goods-quota.sold-out {
  color: var(--color-danger);
}

.radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  color: #fff;
  flex-shrink: 0;
}

.radio.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.goods-image {
  width: 140rpx;
  height: 140rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.goods-name {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-spec {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.goods-bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.goods-price {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

.goods-qty {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.qty-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.qty-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.qty-stepper {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.qty-btn {
  width: 52rpx;
  height: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  font-size: var(--font-size-lg);
  color: var(--color-text);
}

.qty-btn.disabled {
  opacity: 0.4;
}

.qty-value {
  min-width: 48rpx;
  text-align: center;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.section {
  margin-bottom: 28rpx;
}

.section-title {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.reason-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.reason-chip {
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: 999rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.reason-chip.selected {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.amount-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.amount-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.amount-input {
  flex: 1;
  font-size: var(--font-size-lg);
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
  text-align: right;
}

.amount-unit {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.amount-tip {
  display: block;
  margin-top: 8rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.desc-input {
  /* box-sizing 避免 width:100% + padding 撑破父容器 */
  width: 100%;
  height: 160rpx;
  box-sizing: border-box;
  padding: 16rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.upload-item {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.upload-image {
  width: 100%;
  height: 100%;
}

.upload-remove {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border-radius: 50%;
  font-size: 24rpx;
}

.upload-add {
  width: 160rpx;
  height: 160rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  background: var(--color-surface);
  border: 1rpx dashed var(--color-divider);
  border-radius: var(--radius-sm);
  color: var(--color-text-tertiary);
  font-size: 48rpx;
}

.upload-add-text {
  font-size: var(--font-size-xs);
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.btn-primary {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: 999rpx;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.btn-primary.disabled {
  opacity: 0.6;
}

.order-card {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  padding: 20rpx;
  margin-bottom: 16rpx;
}

.order-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.order-no {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.order-status {
  font-size: var(--font-size-xs);
  color: var(--color-primary);
}

.order-body {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.order-img {
  width: 96rpx;
  height: 96rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  flex-shrink: 0;
}

.order-info {
  flex: 1;
  min-width: 0;
}

.order-name {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-count {
  display: block;
  margin-top: 6rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.order-amount {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 40rpx;
}

.empty-title {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  margin-bottom: 32rpx;
}

.empty-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  padding: 0 56rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  color: #fff;
  font-size: var(--font-size-sm);
}

.loading {
  text-align: center;
  padding: 40rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
