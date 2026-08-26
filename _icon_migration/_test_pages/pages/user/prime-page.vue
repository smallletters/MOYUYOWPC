<template>
  <view class="prime-page">
    <view class="header">
      <view class="nav-back" @tap="goBack"><text class="back-icon"><text class="luc luc-arrow-left"></text></text></view>
      <text class="title">MOYUYO Prime 会员</text>
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else class="content">
      <view class="hero">
        <text class="hero-title">解锁全部权益</text>
        <text class="hero-sub">加入 Prime，免费包邮、专属折扣、新品优先</text>
      </view>

      <view class="plan-list">
        <view v-for="p in plans" :key="p.id" class="plan-card">
          <view class="plan-row1">
            <text class="plan-name">{{ p.name }}</text>
            <text v-if="p.level" class="plan-level">{{ p.level }}</text>
          </view>
          <view v-if="p.benefits" class="plan-benefits">
            <text v-for="(b, i) in parseBenefits(p.benefits)" :key="i" class="benefit-item">· {{ b }}</text>
          </view>
          <view class="plan-row2">
            <view class="price-wrap">
              <text class="price">¥{{ p.price }}</text>
              <text v-if="p.originalPrice && p.originalPrice > p.price" class="price-origin">¥{{ p.originalPrice }}</text>
              <text class="price-unit">/ {{ p.durationMonths }} 个月</text>
            </view>
            <view class="plan-btn" @tap="subscribe(p)">立即开通</view>
          </view>
        </view>
        <view v-if="!plans.length" class="empty"><text class="empty-text">暂无可用套餐</text></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { marketingApi } from '@/api'

const plans = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    plans.value = await marketingApi.listPrimePlans() || []
  } catch (e) {
    console.warn('[prime-page] load failed', e)
  } finally { loading.value = false }
}

function parseBenefits(b) {
  if (!b) return []
  try { const arr = JSON.parse(b); return Array.isArray(arr) ? arr : [] }
  catch { return String(b).split(/[,;|]/).map((x) => x.trim()).filter(Boolean) }
}

function subscribe(p) {
  uni.showModal({
    title: '确认开通',
    content: `开通 ${p.name}，¥${p.price} / ${p.durationMonths} 个月`,
    success: () => uni.showToast({ title: '支付功能开发中', icon: 'none' }),
  })
}

function goBack() { uni.navigateBack() }
onMounted(() => { load() })
</script>

<style lang="scss" scoped>
.prime-page { min-height: 100vh; background: linear-gradient(180deg, #1f1f1f, #2c2c2c 30%, var(--color-background) 60%); }
.header { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; color: #fff; }
.nav-back { width: 60rpx; }
.back-icon { font-size: 44rpx; color: #fff; }
.title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: #fff; }
.loading { padding: 80rpx 24rpx; text-align: center; }
.loading-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.content { padding: 16rpx 24rpx 40rpx; }
.hero { padding: 32rpx 24rpx; color: #fff; }
.hero-title { display: block; font-size: 44rpx; font-weight: 700; }
.hero-sub { display: block; font-size: 26rpx; opacity: 0.8; margin-top: 8rpx; }
.plan-list { display: flex; flex-direction: column; gap: 16rpx; }
.plan-card { background: var(--color-surface); border-radius: 20rpx; padding: 24rpx; }
.plan-row1 { display: flex; justify-content: space-between; align-items: center; }
.plan-name { font-size: 32rpx; font-weight: 700; }
.plan-level { padding: 4rpx 12rpx; background: linear-gradient(135deg, #f5af19, #f12711); color: #fff; border-radius: 999rpx; font-size: 22rpx; }
.plan-benefits { margin-top: 12rpx; }
.benefit-item { display: block; font-size: 24rpx; color: var(--color-text-secondary); line-height: 1.7; }
.plan-row2 { display: flex; justify-content: space-between; align-items: center; margin-top: 16rpx; }
.price-wrap { display: flex; align-items: baseline; gap: 6rpx; }
.price { font-size: 36rpx; font-weight: 700; color: #ee5a52; }
.price-origin { font-size: 22rpx; color: var(--color-text-tertiary); text-decoration: line-through; }
.price-unit { font-size: 22rpx; color: var(--color-text-tertiary); }
.plan-btn { padding: 12rpx 32rpx; background: linear-gradient(135deg, #f5af19, #f12711); color: #fff; border-radius: 32rpx; font-size: 26rpx; }
.empty { padding: 60rpx 24rpx; text-align: center; }
.empty-text { font-size: 26rpx; color: var(--color-text-tertiary); }
</style>