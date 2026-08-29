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

    <scroll-view
      scroll-y
      class="scroll"
      @scrolltolower="onLoadMore"
      @click="closeAllSwipe">
      <view v-for="o in orders" :key="o.id" class="swipe-wrapper">
        <!-- 底层操作按钮（被卡片覆盖；左滑后露出） -->
        <view class="swipe-actions">
          <view v-if="canDelete(o)" class="swipe-btn btn-delete" @click.stop="handleDelete(o)">
            删除
          </view>
          <view v-if="canCancel(o)" class="swipe-btn btn-cancel" @click.stop="handleCancel(o)">
            取消订单
          </view>
        </view>

        <!-- 上层卡片 -->
        <view
          class="card order-card"
          :class="{ 'is-swiping': isSwiping }"
          :style="{ transform: `translateX(${o.swipeOffset || 0}px)` }"
          @touchstart="onTouchStart($event, o)"
          @touchmove="onTouchMove($event, o)"
          @touchend="onTouchEnd($event, o)"
          @touchcancel="onTouchEnd($event, o)"
          @click="onCardClick(o, $event)"
        >
          <view class="card-header">
            <text class="order-no">#{{ o.orderNo }}</text>
            <text class="order-status" :class="`status-${o.status}`">
              {{ statusText(o.status) }}
            </text>
          </view>
          <view class="order-items">
            <view v-for="item in (o.items || []).slice(0, 2)" :key="item.id" class="item-row">
              <image :src="item.mainImage || ''" lazy-load class="item-image" />
              <view class="item-info">
                <text class="item-name text-ellipsis-2">{{ item.productName }}</text>
                <text class="item-qty">x {{ item.quantity }}</text>
              </view>
            </view>
            <view v-if="(o.items || []).length > 2" class="more">
              +{{ o.items.length - 2 }} more
            </view>
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
import { i18n } from '@/i18n'
import { getPendingOrders, removePendingOrder } from '@/utils/storage'

export default {
  data() {
    return {
      activeTab: 'all',
      orders: [],
      loading: false,
      noMore: false,
      page: 1,
      localeVersion: 0,

      // --- 左滑删除状态 ---
      // 被触摸的那一张订单的引用（用于 touchmove 中实时更新 offset）
      touchingOrder: null,
      // 触摸起始 X 坐标（px）
      touchStartX: 0,
      touchStartY: 0,
      // 触摸开始时是否已展开（展开了就记为 btnWidth，没展开就记为 0）
      touchStartOffset: 0,
      // 当前是否处于"正在滑动"状态（用于给卡片加 is-swiping class，去掉 transition）
      isSwiping: false,
      // 刚结束滑动的那一刻，阻止 click 冒泡（touch 结束后 50ms 内）
      justTouchedAt: 0,
      // 滑动按钮宽度（px），H5 用 rpx 换算：1rpx ≈ 0.5px @750px 设计稿
      btnWidthPx: 60,
    }
  },

  computed: {
    tabs() {
      void this.localeVersion
      // 状态值对齐后端 OrderStatusEnum:
      // - PENDING_PAY → 待支付
      // - PAID,PENDING_SHIP → 支付成功后、发货前（admin 发货前状态可能是 PAID 或 PENDING_SHIP）
      // - SHIPPED → 已发货、待收货
      // - RECEIVED,COMPLETED → 已收货（待评价）、已完成
      return [
        { value: 'all', label: i18n.t('orderList.statusTabs.all') },
        { value: 'PENDING_PAY', label: i18n.t('orderList.statusTabs.pendingPay') },
        { value: 'PAID,PENDING_SHIP', label: i18n.t('orderList.statusTabs.pendingShip') },
        { value: 'SHIPPED', label: i18n.t('orderList.statusTabs.pendingReceive') },
        { value: 'RECEIVED,COMPLETED', label: i18n.t('orderList.statusTabs.completed') },
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
        // 每条加 swipeOffset: 0（左滑展开标记）
        const normalized = list.map((o) => ({ ...o, swipeOffset: 0 }))
        this.orders.push(...normalized)
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
      // 对齐后端 OrderStatusEnum 真实值
      const map = {
        PENDING_PAY: i18n.t('orderDetail.pay'),
        PAID: i18n.t('orderList.actionWaitShip'), // 已支付等待发货
        PENDING_SHIP: i18n.t('orderList.actionWaitShip'), // 待发货
        SHIPPED: i18n.t('orderList.actionConfirmReceive'), // 已发货 → 确认收货
        RECEIVED: i18n.t('orderList.actionReview'), // 已收货 → 去评价
        COMPLETED: i18n.t('orderList.actionReview'), // 已完成 → 查看评价
      }
      return map[status] || i18n.t('orderList.actionView')
    },

    onAction(order) {
      const s = order.status
      if (s === 'PENDING_PAY') {
        uni.navigateTo({
          url: `/pages/order/pay?orderId=${order.id}&amount=${order.payAmount}`,
        })
      } else if (s === 'PAID' || s === 'PENDING_SHIP') {
        // 已支付/待发货 → 暂无 APP 侧操作按钮（等 admin 发货）
        this.goDetail(order.id)
      } else if (s === 'SHIPPED') {
        // 已发货 → 支持"查看物流"和"确认收货"
        uni.showActionSheet({
          itemList: ['查看物流', '确认收货'],
          success: (res) => {
            if (res.tapIndex === 0) {
              uni.navigateTo({ url: `/pages/order/logistics?id=${order.id}` })
            } else if (res.tapIndex === 1) {
              this.doConfirmReceive(order)
            }
          },
          fail: () => uni.navigateTo({ url: `/pages/order/logistics?id=${order.id}` }),
        })
      } else if (s === 'RECEIVED') {
        uni.navigateTo({ url: `/pages/order/review?orderId=${order.id}` })
      } else if (s === 'COMPLETED') {
        uni.navigateTo({ url: `/pages/order/detail?id=${order.id}` })
      } else {
        this.goDetail(order.id)
      }
    },

    /** 确认收货（SHIPPED → RECEIVED/COMPLETED） */
    async doConfirmReceive(order) {
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: '确认收货',
          content: `确定已收到订单 ${order.orderNo} 吗？`,
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false),
        })
      })
      if (!confirmed) return
      try {
        await orderApi.confirmReceived(order.id)
        // 从 SHIPPED tab 移到已收货 tab
        order.status = 'RECEIVED'
        order.swipeOffset = 0
        uni.showToast({ title: '收货成功', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: e?.data?.msg || e?.message || '操作失败', icon: 'none' })
      }
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
    },

    // ========== 左滑删除相关 ==========

    /** 判断某订单是否存在可滑出的操作按钮（至少一个） */
    hasSwipeAction(o) {
      return this.canDelete(o) || this.canCancel(o)
    },
    /** 待付款 / 待发货 / 已取消 的订单可物理删除 */
    canDelete(o) {
      return ['PENDING_PAY', 'PENDING_SHIP', 'CANCELLED'].includes(o.status)
    },
    /** 仅待付款订单可直接取消；已付款未发货订单需走退款流程，避免与后端白名单不一致 */
    canCancel(o) {
      return ['PENDING_PAY'].includes(o.status)
    },

    /** 根据按钮数量计算展开时应滑动到的 px 值（每按钮约 60rpx ≈ 30px） */
    computeBtnPx(o) {
      let count = 0
      if (this.canDelete(o)) count++
      if (this.canCancel(o)) count++
      // 设计稿 750rpx → 实际宽度一半（H5 下 1rpx ≈ 0.5px）
      // 实际按钮宽由 CSS 控制：.swipe-btn { width: 120rpx } → 60px
      return count * 60
    },

    /** 触摸开始：记录起始坐标 & 起始 offset */
    onTouchStart(e, order) {
      if (!this.hasSwipeAction(order)) return
      this.touchingOrder = order
      const t = e.touches[0] || e.changedTouches[0]
      this.touchStartX = t.clientX
      this.touchStartY = t.clientY
      this.touchStartOffset = order.swipeOffset || 0
    },

    /** 触摸移动：实时更新 translateX（加 clamp 防越界） */
    onTouchMove(e, order) {
      if (!this.touchingOrder) return
      const t = e.touches[0] || e.changedTouches[0]
      const dx = t.clientX - this.touchStartX
      const dy = t.clientY - this.touchStartY
      // 水平位移必须大于垂直位移的 1.5 倍才算"滑动卡片"，否则让父滚动视图接管
      if (!this.isSwiping && Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy) * 1.5) {
        this.isSwiping = true
      }
      if (!this.isSwiping) return

      const full = this.computeBtnPx(order)
      let next = this.touchStartOffset + dx
      // 双向 clamp：最小 -full，最大 0（禁止向右露出"空白"）
      next = Math.max(-full, Math.min(0, next))
      // 注意：这里 order 是列表中的引用，改 swipeOffset 会直接触发模板 transform 更新
      this.touchingOrder.swipeOffset = next
    },

    /** 触摸结束：根据最终位置决定"弹回去"还是"展开" */
    onTouchEnd(e, order) {
      if (!this.touchingOrder) return
      this.justTouchedAt = Date.now()
      const full = this.computeBtnPx(order)
      const current = this.touchingOrder.swipeOffset || 0
      // 超过一半就展开到底，否则弹回
      let target = 0
      if (current < -full / 2) target = -full
      else target = 0
      this.touchingOrder.swipeOffset = target
      this.touchingOrder = null
      this.isSwiping = false
    },

    /** 点击卡片：若刚滑动过（50ms 内），拦截跳转 */
    onCardClick(order, _e) {
      if (Date.now() - this.justTouchedAt < 50) return
      this.goDetail(order.id)
    },

    /** 关闭所有已展开的 swipe（scroll 或点击其他区域时调用） */
    closeAllSwipe() {
      let changed = false
      for (const o of this.orders) {
        if (o.swipeOffset !== 0) {
          o.swipeOffset = 0
          changed = true
        }
      }
      if (changed) {
        // 强制让模板重渲染 transform
        this.orders = [...this.orders]
      }
    },

    /** 点击「删除」按钮：本地未支付订单 → removePendingOrder；其他走后端 delete */
    async handleDelete(order) {
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: '确认删除',
          content: `确定删除订单 ${order.orderNo} 吗？删除后不可恢复。`,
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false),
        })
      })
      if (!confirmed) {
        this.closeAllSwipe()
        return
      }
      try {
        // 本地未支付订单：直接从 storage 移除，不用调后端（可能还没持久化）
        const isLocal = order.id && String(order.id).startsWith('local-')
        if (isLocal) {
          removePendingOrder(order.id)
        } else {
          await orderApi.deleteOrder(order.id)
        }
        // 从列表中移除
        this.orders = this.orders.filter((o) => o.id !== order.id)
        uni.showToast({ title: '已删除', icon: 'success' })
      } catch (e) {
        console.error('[order-list] delete fail', e)
        uni.showToast({ title: e?.data?.msg || e?.message || '删除失败', icon: 'none' })
        this.closeAllSwipe()
      }
    },

    /** 点击「取消订单」按钮 */
    async handleCancel(order) {
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: '取消订单',
          content: `确定取消订单 ${order.orderNo} 吗？`,
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false),
        })
      })
      if (!confirmed) {
        this.closeAllSwipe()
        return
      }
      try {
        await orderApi.cancelOrder(order.id, '用户主动取消')
        // 取消成功后：如果当前 tab 不含 CANCELLED，把该订单从当前列表隐藏
        // 等 tab 不是 ALL / CANCELLED 时隐藏
        if (this.activeTab !== 'all' && this.activeTab !== 'CANCELLED') {
          this.orders = this.orders.filter((o) => o.id !== order.id)
        } else {
          // 刷新该订单显示状态
          order.status = 'CANCELLED'
          order.swipeOffset = 0
        }
        uni.showToast({ title: '已取消', icon: 'success' })
      } catch (e) {
        console.error('[order-list] cancel fail', e)
        uni.showToast({ title: e?.data?.msg || e?.message || '取消失败', icon: 'none' })
        this.closeAllSwipe()
      }
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
  margin-bottom: 0; // 由外层 swipe-wrapper 控制
  position: relative;
  z-index: 2;
  background: var(--color-surface);
  border-radius: 16rpx;
  transition: transform 0.22s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  will-change: transform;
  // 正在被手指拖动时,去掉 transition 保证跟手感
  &.is-swiping {
    transition: none;
  }
}

/* ---------- 左滑删除结构 ---------- */
.swipe-wrapper {
  position: relative;
  overflow: hidden;
  margin-bottom: 20rpx;
  border-radius: 16rpx;
}

.swipe-actions {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: stretch;
  z-index: 1; // 比卡片低,被遮挡
}

.swipe-btn {
  width: 120rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 26rpx;
  font-weight: 500;
  letter-spacing: 2rpx;
  cursor: pointer;
  user-select: none;

  &.btn-delete {
    background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  }
  &.btn-cancel {
    background: linear-gradient(135deg, #a8a29e 0%, #78716c 100%);
  }
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
