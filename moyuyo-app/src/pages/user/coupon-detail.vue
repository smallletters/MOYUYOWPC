<template>
  <view class="page">


    <view v-if="loading" class="loading">
      <text class="loading-text">{{ $t('common.loading') }}</text>
    </view>
    <view v-else-if="!detail" class="empty">
      <text class="empty-text">{{ $t('couponDetail.notExist') }}</text>
    </view>
    <view v-else class="content">
      <view class="hero">
        <view class="hero-left">
          <text class="amount">
            <text v-if="detail.type === 'PERCENT'" class="amount-num">
              {{ detail.discountValue || 0 }}
            </text>
            <text v-else class="amount-num">
              {{ currencySymbol }}{{ detail.discountValue || 0 }}
            </text>
            <text class="amount-unit">{{ detail.type === 'PERCENT' ? '%' : '' }}</text>
          </text>
          <text class="condition">
            {{
              $t('couponDetail.minOrder', {
                amount: `${currencySymbol}${detail.minOrderAmount || 0}`,
              })
            }}
          </text>
        </view>
        <view class="hero-right">
          <text class="status" :class="'status-' + (detail.status || 'UNUSED')">
            {{ statusLabel(detail.status) }}
          </text>
        </view>
      </view>

      <view class="card">
        <view class="row">
          <text class="row-label">{{ $t('couponDetail.name') }}</text>
          <text class="row-value">{{ detail.name }}</text>
        </view>
        <view v-if="detail.description" class="row">
          <text class="row-label">{{ $t('couponDetail.desc') }}</text>
          <text class="row-value">{{ detail.description }}</text>
        </view>
        <view class="row">
          <text class="row-label">{{ $t('couponDetail.type') }}</text>
          <text class="row-value">{{ typeLabel(detail.type) }}</text>
        </view>
        <view v-if="detail.maxDiscountAmount" class="row">
          <text class="row-label">{{ $t('couponDetail.maxDiscount') }}</text>
          <text class="row-value">{{ currencySymbol }}{{ detail.maxDiscountAmount }}</text>
        </view>
        <view class="row">
          <text class="row-label">{{ $t('couponDetail.claimTime') }}</text>
          <text class="row-value">{{ formatTime(detail.createTime) }}</text>
        </view>
        <view v-if="detail.usedTime" class="row">
          <text class="row-label">{{ $t('couponDetail.usedTime') }}</text>
          <text class="row-value">{{ formatTime(detail.usedTime) }}</text>
        </view>
      </view>

      <view class="actions">
        <view v-if="detail.status === 'UNUSED'" class="btn primary" @tap="goUse">
          {{ $t('couponDetail.useNow') }}
        </view>
        <view v-if="detail.status === 'UNUSED'" class="btn" @tap="goTransfer">
          {{ $t('couponDetail.transfer') }}
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { get } from '@/utils/request'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userCouponDetail',

  data() {
    return {
      detail: null,
      loading: false,
      localeVersion: 0,
    }
  },

  computed: {
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onLoad(query) {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    if (query && query.id) this.load(query.id)
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async load(id) {
      this.loading = true
      try {
        this.detail = await get(`/api/v1/coupons/user-coupon/${id}`)
      } catch (e) {
        console.warn('[coupon-detail] load failed', e)
      } finally {
        this.loading = false
      }
    },

    statusLabel(s) {
      void this.localeVersion
      const map = {
        USED: i18n.t('coupons.tabs.used'),
        EXPIRED: i18n.t('coupons.tabs.expired'),
      }
      return map[s] || i18n.t('coupons.tabs.unused')
    },

    typeLabel(t) {
      void this.localeVersion
      if (t === 'PERCENT') return i18n.t('couponCenter.categories.discount')
      if (t === 'FIXED') return i18n.t('couponDetail.fixedType')
      return t || i18n.t('couponDetail.general')
    },

    formatTime(t) {
      if (!t) return ''
      try {
        const d = new Date(t)
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
      } catch {
        return ''
      }
    },

    goUse() {
      uni.switchTab({ url: '/pages/tabbar/category' })
    },
    goTransfer() {
      uni.navigateTo({ url: `/pages/user/coupon-transfer?id=${this.detail.id}` })
    },
    },
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--color-background);
}
.nav-back {
  width: 60rpx;
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
.loading,
.empty {
  padding: 80rpx 24rpx;
  text-align: center;
}
.loading-text,
.empty-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
.content {
  padding: 24rpx;
}
.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx 24rpx;
  background: linear-gradient(135deg, #ff6b6b, #ee5a52);
  border-radius: 16rpx;
  color: #fff;
  margin-bottom: 16rpx;
}
.amount {
  display: flex;
  align-items: baseline;
}
.amount-num {
  font-size: 64rpx;
  font-weight: 700;
}
.amount-unit {
  font-size: 32rpx;
  margin-left: 4rpx;
}
.condition {
  display: block;
  margin-top: 4rpx;
  font-size: 24rpx;
  opacity: 0.85;
}
.status {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  background: rgba(255, 255, 255, 0.25);
}
.status-USED {
  background: rgba(255, 255, 255, 0.4);
}
.status-EXPIRED {
  background: rgba(0, 0, 0, 0.3);
}
.card {
  background: var(--color-surface);
  padding: 16rpx 24rpx;
  border-radius: 16rpx;
}
.row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}
.row:last-child {
  border-bottom: none;
}
.row-label {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
.row-value {
  font-size: 26rpx;
  color: var(--color-text);
}
.actions {
  display: flex;
  gap: 12rpx;
  margin-top: 24rpx;
}
.btn {
  flex: 1;
  height: 88rpx;
  border-radius: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid var(--color-divider);
  font-size: 28rpx;
}
.btn.primary {
  background: var(--color-primary);
  color: #fff;
  border-color: transparent;
}
</style>
