<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack"><text class="back-icon">‹</text></view>
      <text class="title">新人专享</text>
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else class="content">
      <view class="hero">
        <text class="hero-title">欢迎来到 MOYUYO</text>
        <text class="hero-sub">完成新手任务，领取专属福利</text>
      </view>

      <view class="gift-list">
        <view v-for="g in gifts" :key="g.id" class="gift-card">
          <view class="gift-icon">🎁</view>
          <view class="gift-info">
            <text class="gift-name">{{ g.name }}</text>
            <text class="gift-desc">{{ g.description || `价值 ¥${g.amount || 0}` }}</text>
            <text class="gift-points">+{{ g.points || 0 }} 积分</text>
          </view>
          <view class="gift-btn" @tap="claim(g)">领取</view>
        </view>
        <view v-if="!gifts.length" class="empty"><text class="empty-text">暂无新手福利</text></view>
      </view>

      <view class="claimed">
        <text class="claimed-title">已领取</text>
        <view v-if="!claimed.length" class="empty"><text class="empty-text">尚未领取任何福利</text></view>
        <view v-else>
          <view v-for="c in claimed" :key="c.id" class="claimed-item">
            <text class="ci-name">{{ c.giftName || `礼包 #${c.giftId}` }}</text>
            <text class="ci-time">{{ formatTime(c.claimedAt || c.createTime) }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { marketingApi } from '@/api'

const gifts = ref([])
const claimed = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [list, mine] = await Promise.all([
      marketingApi.listNewuserGifts(),
      marketingApi.myGifts(),
    ])
    gifts.value = list || []
    claimed.value = mine?.records || mine || []
  } catch (e) {
    console.warn('[newuser] load failed', e)
  } finally { loading.value = false }
}

async function claim(g) {
  try {
    await marketingApi.claimGift(g.id)
    uni.showToast({ title: '领取成功', icon: 'success' })
    claimed.value.unshift({ giftId: g.id, giftName: g.name, claimedAt: new Date().toISOString() })
  } catch (e) {
    uni.showToast({ title: '领取失败', icon: 'none' })
  }
}

function formatTime(t) {
  if (!t) return ''
  try { const d = new Date(t); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}` } catch { return '' }
}

function goBack() { uni.navigateBack() }
onMounted(() => { load() })
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--color-background); }
.header { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.nav-back { width: 60rpx; }
.back-icon { font-size: 44rpx; color: var(--color-primary); }
.title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.loading { padding: 80rpx 24rpx; text-align: center; }
.loading-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.content { padding: 16rpx; }
.hero { padding: 32rpx 24rpx; background: linear-gradient(135deg, #ff9a9e, #fad0c4); border-radius: 20rpx; color: #fff; margin-bottom: 16rpx; }
.hero-title { display: block; font-size: 36rpx; font-weight: 700; }
.hero-sub { display: block; margin-top: 6rpx; font-size: 24rpx; opacity: 0.85; }
.gift-list { display: flex; flex-direction: column; gap: 12rpx; }
.gift-card { display: flex; align-items: center; gap: 16rpx; padding: 24rpx; background: var(--color-surface); border-radius: 16rpx; }
.gift-icon { font-size: 48rpx; }
.gift-info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.gift-name { font-size: 28rpx; font-weight: 600; }
.gift-desc { font-size: 22rpx; color: var(--color-text-tertiary); }
.gift-points { font-size: 22rpx; color: var(--color-primary); }
.gift-btn { padding: 12rpx 24rpx; background: var(--color-primary); color: #fff; border-radius: 999rpx; font-size: 24rpx; }
.claimed { margin-top: 24rpx; padding: 24rpx; background: var(--color-surface); border-radius: 16rpx; }
.claimed-title { display: block; font-size: 28rpx; font-weight: 600; margin-bottom: 12rpx; }
.claimed-item { display: flex; justify-content: space-between; padding: 12rpx 0; border-bottom: 1rpx solid var(--color-divider); }
.claimed-item:last-child { border-bottom: none; }
.ci-name { font-size: 26rpx; }
.ci-time { font-size: 22rpx; color: var(--color-text-tertiary); }
.empty { padding: 40rpx; text-align: center; }
.empty-text { font-size: 24rpx; color: var(--color-text-tertiary); }
</style>