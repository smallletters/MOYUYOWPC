<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack"><text class="back-icon">‹</text></view>
      <text class="title">分销中心</text>
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else class="content">
      <view class="hero">
        <text class="hero-title">分享赚佣金</text>
        <view class="hero-stats">
          <view class="stat">
            <text class="stat-num">{{ account?.totalInvites || 0 }}</text>
            <text class="stat-label">邀请数</text>
          </view>
          <view class="stat">
            <text class="stat-num">¥{{ account?.totalCommission || 0 }}</text>
            <text class="stat-label">累计佣金</text>
          </view>
          <view class="stat">
            <text class="stat-num">¥{{ account?.availableAmount || 0 }}</text>
            <text class="stat-label">可提现</text>
          </view>
        </view>
      </view>

      <view class="level-card">
        <text class="level-label">当前等级</text>
        <text class="level-name">{{ account?.level || 'BRONZE' }}</text>
      </view>

      <view class="section">
        <text class="section-title">佣金明细</text>
        <view v-if="loading" class="empty"><text class="empty-text">加载中…</text></view>
        <view v-else-if="!commissions.length" class="empty"><text class="empty-text">暂无佣金记录</text></view>
        <view v-else class="commission-list">
          <view v-for="c in commissions" :key="c.id" class="commission-item">
            <view class="ci-left">
              <text class="ci-title">{{ c.description || '分销订单' }}</text>
              <text class="ci-meta">{{ formatTime(c.createTime) }}</text>
            </view>
            <text class="ci-amount">+¥{{ c.amount || 0 }}</text>
          </view>
        </view>
      </view>

      <view class="actions">
        <view class="btn" @tap="shareLink">分享邀请链接</view>
        <view class="btn primary" @tap="withdraw">申请提现</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { marketingApi } from '@/api'

const account = ref(null)
const commissions = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [acc, list] = await Promise.all([
      marketingApi.getAffiliateAccount(),
      marketingApi.listCommissions({ size: 50 }),
    ])
    account.value = acc
    commissions.value = list?.records || list || []
  } catch (e) {
    console.warn('[affiliate] load failed', e)
  } finally { loading.value = false }
}

function formatTime(t) {
  if (!t) return ''
  try { const d = new Date(t); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}` } catch { return '' }
}

function shareLink() {
  uni.setClipboardData({
    data: 'https://moyuyo.com/invite/' + (account.value?.userId || ''),
    success: () => uni.showToast({ title: '邀请链接已复制', icon: 'none' }),
  })
}

function withdraw() {
  uni.showToast({ title: '提现功能开发中', icon: 'none' })
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
.hero { padding: 32rpx 24rpx; background: linear-gradient(135deg, #6a11cb, #2575fc); border-radius: 20rpx; color: #fff; }
.hero-title { display: block; font-size: 36rpx; font-weight: 700; margin-bottom: 16rpx; }
.hero-stats { display: flex; justify-content: space-between; }
.stat { display: flex; flex-direction: column; align-items: center; gap: 4rpx; }
.stat-num { font-size: 36rpx; font-weight: 700; }
.stat-label { font-size: 22rpx; opacity: 0.85; }
.level-card { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; background: var(--color-surface); border-radius: 16rpx; margin-top: 16rpx; }
.level-label { font-size: 26rpx; color: var(--color-text-tertiary); }
.level-name { font-size: 28rpx; font-weight: 600; color: var(--color-primary); }
.section { margin-top: 24rpx; padding: 24rpx; background: var(--color-surface); border-radius: 16rpx; }
.section-title { display: block; font-size: 28rpx; font-weight: 600; margin-bottom: 16rpx; }
.empty { padding: 30rpx; text-align: center; }
.empty-text { font-size: 24rpx; color: var(--color-text-tertiary); }
.commission-list { display: flex; flex-direction: column; gap: 12rpx; }
.commission-item { display: flex; justify-content: space-between; align-items: center; padding: 16rpx; background: var(--color-background); border-radius: 12rpx; }
.ci-left { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.ci-title { font-size: 26rpx; font-weight: 500; }
.ci-meta { font-size: 22rpx; color: var(--color-text-tertiary); }
.ci-amount { font-size: 28rpx; font-weight: 700; color: #ee5a52; }
.actions { display: flex; gap: 12rpx; margin-top: 24rpx; }
.btn { flex: 1; height: 88rpx; border-radius: 44rpx; display: flex; align-items: center; justify-content: center; border: 1rpx solid var(--color-divider); font-size: 28rpx; }
.btn.primary { background: var(--color-primary); color: #fff; border-color: transparent; }
</style>