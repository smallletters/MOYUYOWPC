<template>
  <view class="points-shop">
    <view class="balance-card card">
      <text class="balance-label">Your Points</text>
      <text class="balance-value">{{ points }}</text>
      <view v-if="checkedIn" class="checked-in">✓ Today checked in</view>
      <view v-else class="btn btn-sm btn-outline" @click="onCheckin">Check in +5</view>
    </view>

    <view class="section">
      <text class="section-title">Redeem Rewards</text>
      <view v-for="item in rewards" :key="item.id" class="reward-card card">
        <image :src="item.image" class="reward-image" mode="aspectFill" />
        <view class="reward-info">
          <text class="reward-name">{{ item.name }}</text>
          <text class="reward-desc">{{ item.description }}</text>
          <text class="reward-points">{{ item.points }} pts</text>
          <view
            class="btn btn-sm btn-primary"
            :class="{ disabled: points < item.points }"
            @click="onRedeem(item)"
          >
            Redeem
          </view>
        </view>
      </view>
    </view>

    <view class="section">
      <text class="section-title">Points History</text>
      <view v-for="log in logs" :key="log.id" class="log-item">
        <text class="log-type">{{ logLabel(log.type) }}</text>
        <text class="log-amount" :class="log.changeValue > 0 ? 'positive' : 'negative'">
          {{ log.changeValue > 0 ? '+' : '' }}{{ log.changeValue }}
        </text>
        <text class="log-time">{{ formatTime(log.createdAt) }}</text>
      </view>
      <view v-if="!logs.length" class="empty">No history</view>
    </view>
  </view>
</template>

<script>
import { pointsApi } from '@/api'

export default {
  data() {
    return {
      points: 0,
      checkedIn: false,
      logs: [],
      rewards: [
        { id: 1, name: 'MOYUYO Tote Bag', description: 'Limited edition canvas tote', points: 500, image: 'https://picsum.photos/200/200?random=21' },
        { id: 2, name: '$5 Off Coupon', description: 'Valid on orders over $30', points: 1000, image: 'https://picsum.photos/200/200?random=22' },
        { id: 3, name: 'Free Shipping', description: 'One-time free shipping', points: 800, image: 'https://picsum.photos/200/200?random=23' },
      ],
    }
  },

  onLoad() {
    this.loadData()
  },

  methods: {
    async loadData() {
      try {
        const [balance, logRes] = await Promise.all([
          pointsApi.getPointsBalance(),
          pointsApi.getPointsLog({ page: 1, size: 20 }),
        ])
        this.points = balance || 0
        this.logs = logRes?.records || logRes || []
      } catch (e) {
        console.error('[points] load error', e)
      }
    },

    async onCheckin() {
      try {
        const res = await pointsApi.checkin()
        const payload = res?.data || res || {}
        const result = payload.data || payload
        this.checkedIn = true
        this.points += result.points || 5
        // 重新拉取流水与余额，确保展示与服务端一致
        this.loadData()
        uni.showToast({
          title: `签到成功 +${result.points || 5} 积分${result.doubleReward ? ' (x2)' : ''}`,
          icon: 'success',
        })
      } catch (e) {
        const msg = (e && e.message) || ''
        if (msg.includes('今日已签到')) {
          this.checkedIn = true
          uni.showToast({ title: '今日已签到', icon: 'none' })
        } else {
          uni.showToast({ title: msg || '签到失败', icon: 'none' })
        }
      }
    },

    onRedeem(item) {
      if (this.points < item.points) {
        uni.showToast({ title: 'Not enough points', icon: 'none' })
        return
      }
      uni.showModal({
        title: 'Redeem ' + item.name,
        content: 'This will cost ' + item.points + ' points. Continue?',
        success: (res) => {
          if (res.confirm) {
            uni.showToast({ title: 'Redeemed! (demo)', icon: 'success' })
          }
        },
      })
    },

    logLabel(type) {
      const map = { CHECKIN: 'Check-in', SPEND: 'Spent', EARN: 'Earned', ORDER: 'Purchase', SIGNUP: 'Welcome' }
      return map[type] || type
    },

    formatTime(time) {
      if (!time) return ''
      return new Date(time).toLocaleDateString()
    },
  },
}
</script>

<style lang="scss" scoped>
.points-shop { min-height: 100vh; background: var(--color-background); padding: 16rpx; }
.card { background: var(--color-surface); border-radius: var(--radius-md); padding: 24rpx; margin-bottom: 16rpx; }
.balance-card { text-align: center; padding: 40rpx 24rpx; }
.balance-label { font-size: var(--font-size-sm); color: var(--color-text-tertiary); display: block; }
.balance-value { font-size: 72rpx; font-weight: var(--font-weight-bold); color: var(--color-primary); display: block; margin: 16rpx 0; }
.checked-in { font-size: var(--font-size-sm); color: var(--color-primary); }
.section { margin-bottom: 24rpx; }
.section-title { font-size: var(--font-size-md); font-weight: var(--font-weight-semibold); display: block; margin-bottom: 16rpx; padding-left: 8rpx; }
.reward-card { display: flex; gap: 16rpx; padding: 20rpx; }
.reward-image { width: 160rpx; height: 160rpx; border-radius: var(--radius-sm); flex-shrink: 0; }
.reward-info { flex: 1; display: flex; flex-direction: column; gap: 8rpx; }
.reward-name { font-size: var(--font-size-base); font-weight: var(--font-weight-medium); }
.reward-desc { font-size: var(--font-size-xs); color: var(--color-text-tertiary); }
.reward-points { font-size: var(--font-size-sm); color: var(--color-primary); font-weight: var(--font-weight-semibold); }
.log-item { display: flex; justify-content: space-between; align-items: center; padding: 16rpx 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.log-type { font-size: var(--font-size-sm); }
.log-amount { font-size: var(--font-size-sm); font-weight: var(--font-weight-semibold); }
.log-amount.positive { color: var(--color-primary); }
.log-amount.negative { color: #e74c3c; }
.log-time { font-size: var(--font-size-xs); color: var(--color-text-tertiary); }
.empty { text-align: center; padding: 32rpx; color: var(--color-text-tertiary); }
.btn-sm { padding: 12rpx 24rpx; font-size: var(--font-size-xs); display: inline-flex; }
.btn-sm.disabled { opacity: 0.5; }
</style>
