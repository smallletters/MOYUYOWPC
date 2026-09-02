<template>
  <view class="page">


    <view v-if="loading" class="loading">
      <text class="loading-text">{{ $t('common.loading') }}</text>
    </view>
    <view v-else class="content">
      <view class="coupon-preview">
        <text class="cp-name">{{ detail?.name || $t('couponTransfer.unselected') }}</text>
        <text class="cp-meta">
          {{
            $t('couponTransfer.meta', {
              value: `${currencySymbol}${detail?.discountValue || 0}`,
              min: `${currencySymbol}${detail?.minOrderAmount || 0}`,
            })
          }}
        </text>
      </view>

      <view class="form-card">
        <text class="form-label">{{ $t('couponTransfer.recipientLabel') }}</text>
        <input
          v-model="toUserId"
          class="form-input"
          type="number"
          :placeholder="$t('couponTransfer.recipientPlaceholder')"
        >
        <text class="form-hint">{{ $t('couponTransfer.hint') }}</text>
      </view>

      <view class="actions">
        <view class="btn" @tap="goBack">{{ $t('common.cancel') }}</view>
        <view class="btn primary" @tap="submit">{{ $t('couponTransfer.confirmTransfer') }}</view>
      </view>
    </view>
  </view>
</template>

<script>
import { get, post } from '@/utils/request'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userCouponTransfer',

  data() {
    return {
      detail: null,
      loading: false,
      userCouponId: null,
      toUserId: '',
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
    if (query && query.id) {
      this.userCouponId = query.id
      this.loadCoupon(query.id)
    }
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadCoupon(id) {
      this.loading = true
      try {
        this.detail = await get(`/api/v1/coupons/user-coupon/${id}`)
      } catch (e) {
        console.warn('[coupon-transfer] load failed', e)
      } finally {
        this.loading = false
      }
    },

    async submit() {
      if (!this.userCouponId || !this.toUserId) {
        uni.showToast({ title: i18n.t('couponTransfer.reasonRequired'), icon: 'none' })
        return
      }
      uni.showModal({
        title: i18n.t('couponTransfer.confirmTitle'),
        content: i18n.t('couponTransfer.confirmContent', { userId: this.toUserId }),
        success: async (res) => {
          if (res.confirm) {
            try {
              await post(`/api/v1/coupons/${this.userCouponId}/transfer?toUserId=${this.toUserId}`)
              uni.showToast({ title: i18n.t('couponTransfer.transferred'), icon: 'success' })
              setTimeout(() => uni.navigateBack(), 1200)
            } catch (e) {
              uni.showToast({ title: e?.message || i18n.t('couponTransfer.failed'), icon: 'none' })
            }
          }
        },
      })
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
.loading {
  padding: 80rpx 24rpx;
  text-align: center;
}
.loading-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
.content {
  padding: 24rpx;
}
.coupon-preview {
  padding: 24rpx;
  background: linear-gradient(135deg, #ff9a9e, #fad0c4);
  border-radius: 16rpx;
  color: #fff;
  margin-bottom: 24rpx;
}
.cp-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
}
.cp-meta {
  display: block;
  font-size: 24rpx;
  margin-top: 6rpx;
  opacity: 0.85;
}
.form-card {
  background: var(--color-surface);
  padding: 24rpx;
  border-radius: 16rpx;
}
.form-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin-bottom: 12rpx;
}
.form-input {
  width: 100%;
  height: 80rpx;
  padding: 0 20rpx;
  background: var(--color-background);
  border-radius: 16rpx;
  font-size: 28rpx;
}
.form-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: var(--color-text-tertiary);
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
