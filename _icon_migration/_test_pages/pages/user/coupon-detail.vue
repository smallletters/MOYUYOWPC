<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack"><text class="back-icon"><text class="luc luc-arrow-left"></text></text></view>
      <text class="nav-title">优惠券详情</text>
      <view class="nav-placeholder" />
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else-if="!detail" class="empty"><text class="empty-text">优惠券不存在或已失效</text></view>
    <view v-else class="content">
      <view class="hero">
        <view class="hero-left">
          <text class="amount">
            <text v-if="detail.type === 'PERCENT'" class="amount-num">{{ detail.discountValue || 0 }}</text>
            <text v-else class="amount-num">¥{{ detail.discountValue || 0 }}</text>
            <text class="amount-unit">{{ detail.type === 'PERCENT' ? '%' : '' }}</text>
          </text>
          <text class="condition">满 {{ detail.minOrderAmount || 0 }} 元可用</text>
        </view>
        <view class="hero-right">
          <text class="status" :class="'status-' + (detail.status || 'UNUSED')">{{ statusLabel(detail.status) }}</text>
        </view>
      </view>

      <view class="card">
        <view class="row"><text class="row-label">名称</text><text class="row-value">{{ detail.name }}</text></view>
        <view v-if="detail.description" class="row"><text class="row-label">说明</text><text class="row-value">{{ detail.description }}</text></view>
        <view class="row"><text class="row-label">类型</text><text class="row-value">{{ typeLabel(detail.type) }}</text></view>
        <view v-if="detail.maxDiscountAmount" class="row"><text class="row-label">最高优惠</text><text class="row-value">¥{{ detail.maxDiscountAmount }}</text></view>
        <view class="row"><text class="row-label">领取时间</text><text class="row-value">{{ formatTime(detail.createTime) }}</text></view>
        <view v-if="detail.usedTime" class="row"><text class="row-label">使用时间</text><text class="row-value">{{ formatTime(detail.usedTime) }}</text></view>
      </view>

      <view class="actions">
        <view v-if="detail.status === 'UNUSED'" class="btn primary" @tap="goUse">立即使用</view>
        <view v-if="detail.status === 'UNUSED'" class="btn" @tap="goTransfer">转赠好友</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { get } from '@/utils/request'

const detail = ref(null)
const loading = ref(false)

async function load(id) {
  loading.value = true
  try {
    detail.value = await get(`/api/v1/coupons/user-coupon/${id}`)
  } catch (e) {
    console.warn('[coupon-detail] load failed', e)
  } finally { loading.value = false }
}

function statusLabel(s) {
  if (s === 'USED') return '已使用'
  if (s === 'EXPIRED') return '已过期'
  return '未使用'
}
function typeLabel(t) {
  if (t === 'PERCENT') return '折扣券'
  if (t === 'FIXED') return '满减券'
  return t || '通用'
}
function formatTime(t) {
  if (!t) return ''
  try { const d = new Date(t); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` } catch { return '' }
}

function goUse() {
  uni.switchTab({ url: '/pages/tabbar/category' })
}
function goTransfer() {
  uni.navigateTo({ url: `/pages/user/coupon-transfer?id=${detail.value.id}` })
}
function goBack() { uni.navigateBack() }

onMounted(() => {
  try {
    const pages = getCurrentPages()
    const cur = pages[pages.length - 1]
    const q = cur?.options || {}
    if (q.id) load(q.id)
  } catch (e) { /* ignore */ }
})
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--color-background); }
.nav-bar { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.nav-back { width: 60rpx; }
.back-icon { font-size: 44rpx; color: var(--color-primary); }
.nav-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.nav-placeholder { width: 60rpx; }
.loading, .empty { padding: 80rpx 24rpx; text-align: center; }
.loading-text, .empty-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.content { padding: 24rpx; }
.hero { display: flex; justify-content: space-between; align-items: center; padding: 32rpx 24rpx; background: linear-gradient(135deg, #ff6b6b, #ee5a52); border-radius: 16rpx; color: #fff; margin-bottom: 16rpx; }
.amount { display: flex; align-items: baseline; }
.amount-num { font-size: 64rpx; font-weight: 700; }
.amount-unit { font-size: 32rpx; margin-left: 4rpx; }
.condition { display: block; margin-top: 4rpx; font-size: 24rpx; opacity: 0.85; }
.status { padding: 6rpx 16rpx; border-radius: 999rpx; font-size: 22rpx; background: rgba(255,255,255,0.25); }
.status-USED { background: rgba(255,255,255,0.4); }
.status-EXPIRED { background: rgba(0,0,0,0.3); }
.card { background: var(--color-surface); padding: 16rpx 24rpx; border-radius: 16rpx; }
.row { display: flex; justify-content: space-between; padding: 16rpx 0; border-bottom: 1rpx solid var(--color-divider); }
.row:last-child { border-bottom: none; }
.row-label { font-size: 26rpx; color: var(--color-text-tertiary); }
.row-value { font-size: 26rpx; color: var(--color-text); }
.actions { display: flex; gap: 12rpx; margin-top: 24rpx; }
.btn { flex: 1; height: 88rpx; border-radius: 44rpx; display: flex; align-items: center; justify-content: center; border: 1rpx solid var(--color-divider); font-size: 28rpx; }
.btn.primary { background: var(--color-primary); color: #fff; border-color: transparent; }
</style>