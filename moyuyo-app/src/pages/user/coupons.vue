<template>
  <view class="coupons">
    <view class="page-header">
      <view class="back" :aria-label="$t('common.back')" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">{{ $t('coupons.title') }}</text>
    </view>

    <view class="tab-bar">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="activeTab = t.value"
      >
        {{ t.label }}
      </view>
    </view>

    <scroll-view scroll-y class="content">
      <view v-if="filteredCoupons.length === 0" class="empty">
        <text class="empty-icon luc-ticket" />
        <text class="empty-text">{{ $t('coupons.empty') }}</text>
      </view>

      <view v-else class="coupon-list">
        <view
          v-for="c in filteredCoupons"
          :key="c.id"
          class="coupon-card"
          :class="{ used: c.status === 'USED', expired: c.status === 'EXPIRED' }"
        >
          <view class="coupon-left">
            <text class="coupon-amount">{{ c.amountText }}</text>
            <text class="coupon-threshold">{{ c.thresholdText }}</text>
          </view>
          <view class="coupon-right">
            <view class="coupon-title">
              <text class="coupon-name">{{ c.name }}</text>
              <text class="coupon-status" :class="'coupon-status-' + c.status.toLowerCase()">
                {{ c.statusText }}
              </text>
            </view>
            <text class="coupon-scope">{{ c.scopeText }}</text>
            <text class="coupon-expire">{{ c.expireText }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { couponApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  data() {
    return {
      // 状态统一使用后端大写值（UNUSED / LOCKED / USED / EXPIRED）
      activeTab: 'UNUSED',
      rawCoupons: [],
      localeVersion: 0,
    }
  },

  computed: {
    filteredCoupons() {
      void this.localeVersion
      // 每次按当前语言归一化，保证切换语言时卡片文案同步刷新
      return this.rawCoupons
        .map((c) => this.normalizeCoupon(c))
        .filter((c) => c.status === this.activeTab)
    },
    tabs() {
      void this.localeVersion
      return [
        { value: 'UNUSED', label: i18n.t('coupons.tabs.unused') },
        { value: 'LOCKED', label: i18n.t('coupons.tabs.locked') },
        { value: 'USED', label: i18n.t('coupons.tabs.used') },
        { value: 'EXPIRED', label: i18n.t('coupons.tabs.expired') },
      ]
    },
    /**
     * 当前语言货币符号:locale 切换时通过 localeVersion 触发重算
     */
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onShow() {
    this.loadCoupons()
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadCoupons() {
      try {
        // 一次拉全量：后端已按券模板 endTime 把过期券推导为 EXPIRED，
        // 本地按 tab 筛选，避免每个 tab 各发一次请求
        const list = await couponApi.getMyCoupons()
        this.rawCoupons = Array.isArray(list) ? list : []
      } catch (e) {
        console.warn('[coupons] load failed', e)
        this.rawCoupons = []
      }
    },

    /**
     * 归一化后端 CouponView → 卡片展示模型
     * - status：后端返回 UNUSED / LOCKED / USED / EXPIRED，统一转大写后比较
     * - amount：AMOUNT → 货币符号+面额；PERCENT → 中文"X折"/英文"X% off"
     * - threshold / scope / expire：由 minOrderAmount、description、endTime 拼装
     */
    normalizeCoupon(c) {
      const status = String(c.status || 'UNUSED').toUpperCase()
      const isPercent = String(c.type || '').toUpperCase() === 'PERCENT'
      // displayValue 由后端按 UI 语义产出：PERCENT → 折数；AMOUNT → 减免金额
      const displayValue = Number(c.displayValue != null ? c.displayValue : c.discountValue || 0)
      const minAmt = Number(c.minOrderAmount || 0)
      return {
        // userCouponId 才是"我的一张券"的唯一主键（同一券模板可能被多人各领一张）
        id: c.userCouponId || c.id,
        status,
        statusText: this.statusText(status),
        amountText: this.formatAmount(c, isPercent, displayValue),
        thresholdText:
          minAmt > 0
            ? i18n.t('coupons.minOrder', { amount: this.formatMoney(minAmt) })
            : i18n.t('couponCenter.noThreshold'),
        scopeText:
          c.description ||
          i18n.t(isPercent ? 'couponCenter.percentCoupon' : 'couponCenter.amountCoupon'),
        expireText: this.formatExpire(c.endTime),
        name: c.name || '',
      }
    },

    statusText(status) {
      const map = {
        UNUSED: 'coupons.tabs.unused',
        LOCKED: 'coupons.tabs.locked',
        USED: 'coupons.tabs.used',
        EXPIRED: 'coupons.tabs.expired',
      }
      return i18n.t(map[status] || 'coupons.tabs.unused')
    },

    /**
     * 面额展示：AMOUNT → ¥/ $ + 两位小数；PERCENT → 中文"X折"、英文"X% off"
     * 后端 discountValue 对 PERCENT 存的是"价格百分比"(90=9折)，所以英文按 100-discountValue 换算
     */
    formatAmount(c, isPercent, displayValue) {
      if (!isPercent) return this.formatMoney(displayValue)
      const isZh = String(i18n.locale || '')
        .toLowerCase()
        .startsWith('zh')
      const num = isZh ? displayValue : 100 - Number(c.discountValue || 0)
      return `${String(num).replace(/\.0+$/, '')}${i18n.t('couponCenter.discountUnit')}`
    },

    formatMoney(v) {
      return `${this.currencySymbol}${Number(v).toFixed(2)}`
    },

    formatExpire(endTime) {
      if (!endTime) return i18n.t('couponCenter.validityLongTerm')
      // 后端返 LocalDateTime 形如 "2026-12-31T23:59:59" 或 "2026-12-31 23:59:59"
      const d = new Date(String(endTime).replace(' ', 'T'))
      if (Number.isNaN(d.getTime())) return ''
      const date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
        d.getDate(),
      ).padStart(2, '0')}`
      return i18n.t('coupons.expiresAt', { date })
    },

    goBack() {
      uni.navigateBack()
    },
  },
}
</script>

<style lang="scss" scoped>
.coupons {
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

.tab-bar {
  display: flex;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.tab {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  position: relative;
}

.tab.active {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

.tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}

.content {
  padding: 24rpx;
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

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.coupon-card {
  display: flex;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.coupon-card.used {
  opacity: 0.5;
}

/* 已过期券置灰，与"已使用"区分：过期用状态角标 + 稍浅透明度 */
.coupon-card.expired {
  opacity: 0.6;
}

.coupon-left {
  width: 200rpx;
  padding: 24rpx 16rpx;
  background: var(--color-primary);
  color: var(--color-text);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.coupon-amount {
  font-size: 48rpx;
  font-weight: var(--font-weight-bold);
}

.coupon-threshold {
  font-size: var(--font-size-xs);
  margin-top: 4rpx;
}

.coupon-right {
  flex: 1;
  padding: 20rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.coupon-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.coupon-name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

/* 状态角标：未使用/已使用/已过期/待支付占用 */
.coupon-status {
  flex-shrink: 0;
  padding: 4rpx 12rpx;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  background: var(--color-divider);
  color: var(--color-text-secondary);
}

.coupon-status-expired {
  background: rgba(0, 0, 0, 0.08);
  color: var(--color-text-tertiary);
}

.coupon-scope {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.coupon-expire {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: auto;
}
</style>
