<template>
  <view class="points-shop">
    <view class="balance-card card">
      <text class="balance-label">{{ $t('pointsShop.pointsLabel') }}</text>
      <text class="balance-value">{{ points }}</text>
      <view v-if="loading" class="loading-line">{{ $t('pointsShop.loading') }}</view>
      <view v-else-if="checkedIn" class="checked-in">
        <text class="luc luc-check" />
        {{ $t('pointsShop.checkedIn') }}
      </view>
      <view v-else class="btn btn-sm btn-outline" @click="onCheckin">
        {{ $t('pointsShop.checkIn') }}
      </view>
    </view>

    <view class="section">
      <text class="section-title">{{ $t('pointsShop.historyTitle') }}</text>
      <view v-for="log in logs" :key="log.id" class="log-item">
        <text class="log-type">{{ logLabel(log.type) }}</text>
        <text class="log-amount" :class="log.changeValue > 0 ? 'positive' : 'negative'">
          {{ log.changeValue > 0 ? '+' : '' }}{{ log.changeValue }}
        </text>
        <text class="log-time">{{ formatTime(log.createdAt) }}</text>
      </view>
      <view v-if="!logs.length && !loading" class="empty">{{ $t('pointsShop.noHistory') }}</view>
    </view>
  </view>
</template>

<script>
import { pointsApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userPointsShop',

  data() {
    return {
      points: 0,
      checkedIn: false,
      logs: [],
      rewards: [],
      loading: false,
      redeemingId: null,
    }
  },

  onLoad() {
    this.loadData()
  },

  methods: {
    async loadData() {
      this.loading = true
      try {
        // request.js 已解包外层 envelope
        const [balance, logRes, goodsRes] = await Promise.all([
          pointsApi.getPointsBalance(),
          pointsApi.getPointsLog({ page: 1, size: 20 }),
          // 拉取真实积分商品列表,失败也不影响余额/流水展示
          pointsApi.getPointsGoods({ page: 1, size: 50 }).catch(() => null),
        ])
        this.points = balance || 0
        // logRes 已是 IPage,直接取 records;兼容老数组返回
        this.logs = logRes?.records || (Array.isArray(logRes) ? logRes : [])
        const list = (goodsRes && (goodsRes.records || goodsRes)) || []
        this.rewards = Array.isArray(list) ? list : []
      } catch (e) {
        console.error('[points] load error', e)
      } finally {
        this.loading = false
      }
    },

    async onCheckin() {
      try {
        // request.js 已解包,result 即 payload: { points, consecutiveDays, doubleReward }
        const result = await pointsApi.checkin()
        this.checkedIn = true
        const earned = result?.points || 5
        this.points += earned
        // 重新拉取流水与余额,确保展示与服务端一致
        this.loadData()
        // 连续签到翻倍时用带 (x2) 的文案
        const toastKey = result?.doubleReward
          ? 'pointsShop.checkinSuccessDouble'
          : 'pointsShop.checkinSuccess'
        uni.showToast({
          title: i18n.t(toastKey, { points: earned }),
          icon: 'success',
        })
      } catch (e) {
        const msg = (e && e.message) || ''
        // msg 为后端返回的中文业务提示,此处仅做判定,不翻译
        if (msg.includes('今日已签到')) {
          this.checkedIn = true
          uni.showToast({ title: i18n.t('pointsShop.alreadyCheckedIn'), icon: 'none' })
        } else {
          uni.showToast({ title: msg || i18n.t('pointsShop.checkinFailed'), icon: 'none' })
        }
      }
    },

    onRedeem(item) {
      if (this.points < item.points) {
        uni.showToast({ title: i18n.t('pointsShop.notEnoughPoints'), icon: 'none' })
        return
      }
      if (this.redeemingId) return
      uni.showModal({
        title: i18n.t('pointsShop.redeemTitle', { name: item.name }),
        content: i18n.t('pointsShop.redeemContent', { points: item.points }),
        success: async (res) => {
          if (!res.confirm) return
          try {
            this.redeemingId = item.id
            uni.showLoading({ title: i18n.t('pointsShop.redeeming'), mask: true })
            await pointsApi.exchangePointsGoods(item.id)
            uni.hideLoading()
            uni.showToast({ title: i18n.t('pointsShop.redeemSuccess'), icon: 'success' })
            // 重新拉取积分余额,保证与服务端一致
            this.loadData()
          } catch (e) {
            uni.hideLoading()
            uni.showToast({ title: e?.message || i18n.t('pointsShop.redeemFailed'), icon: 'none' })
          } finally {
            this.redeemingId = null
          }
        },
      })
    },

    logLabel(type) {
      // 积分变动类型映射(与后端枚举一致),未知类型回退原值
      const known = ['CHECKIN', 'SPEND', 'EARN', 'ORDER', 'SIGNUP']
      if (known.includes(type)) return i18n.t(`pointsShop.logs.${type}`)
      return type
    },

    formatTime(time) {
      if (!time) return ''
      return new Date(time).toLocaleDateString()
    },
  },
}
</script>

<style lang="scss" scoped>
.points-shop {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
}
.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.balance-card {
  text-align: center;
  padding: 40rpx 24rpx;
}
.balance-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  display: block;
}
.balance-value {
  font-size: 72rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
  display: block;
  margin: 16rpx 0;
}
.checked-in {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
}
.section {
  margin-bottom: 24rpx;
}
.section-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  display: block;
  margin-bottom: 16rpx;
  padding-left: 8rpx;
}
.reward-card {
  display: flex;
  gap: 16rpx;
  padding: 20rpx;
}
.reward-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}
.reward-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.reward-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}
.reward-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.reward-points {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
.log-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.log-type {
  font-size: var(--font-size-sm);
}
.log-amount {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}
.log-amount.positive {
  color: var(--color-primary);
}
.log-amount.negative {
  color: #e74c3c;
}
.log-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.empty {
  text-align: center;
  padding: 32rpx;
  color: var(--color-text-tertiary);
}
.loading-tip {
  text-align: center;
  padding: 32rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
.loading-line {
  display: block;
  margin-top: 8rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}
.btn-sm {
  padding: 12rpx 24rpx;
  font-size: var(--font-size-xs);
  display: inline-flex;
}
.btn-sm.disabled {
  opacity: 0.5;
}
</style>
