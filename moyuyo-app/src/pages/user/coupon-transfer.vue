<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack"><text class="back-icon">‹</text></view>
      <text class="nav-title">转赠优惠券</text>
      <view class="nav-placeholder" />
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else class="content">
      <view class="coupon-preview">
        <text class="cp-name">{{ detail?.name || '未选择优惠券' }}</text>
        <text class="cp-meta">价值 ¥{{ detail?.discountValue || 0 }} · 满 {{ detail?.minOrderAmount || 0 }} 元可用</text>
      </view>

      <view class="form-card">
        <text class="form-label">接收人用户 ID</text>
        <input class="form-input" type="number" v-model="toUserId" placeholder="请输入对方用户 ID" />
        <text class="form-hint">目前转赠需输入用户 ID；后续将支持扫码/手机号</text>
      </view>

      <view class="actions">
        <view class="btn" @tap="goBack">取消</view>
        <view class="btn primary" @tap="submit">确认转赠</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { get, post } from '@/utils/request'

const detail = ref(null)
const loading = ref(false)
const userCouponId = ref(null)
const toUserId = ref('')

async function loadCoupon(id) {
  loading.value = true
  try {
    detail.value = await get(`/api/v1/coupons/user-coupon/${id}`)
  } catch (e) {
    console.warn('[coupon-transfer] load failed', e)
  } finally { loading.value = false }
}

async function submit() {
  if (!userCouponId.value || !toUserId.value) {
    uni.showToast({ title: '请填写接收人 ID', icon: 'none' })
    return
  }
  uni.showModal({
    title: '确认转赠？',
    content: `将优惠券转赠给用户 ${toUserId.value}`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await post(`/api/v1/coupons/${userCouponId.value}/transfer?toUserId=${toUserId.value}`)
          uni.showToast({ title: '转赠成功', icon: 'success' })
          setTimeout(() => uni.navigateBack(), 1200)
        } catch (e) {
          uni.showToast({ title: '转赠失败', icon: 'none' })
        }
      }
    },
  })
}

function goBack() { uni.navigateBack() }

onMounted(() => {
  try {
    const pages = getCurrentPages()
    const cur = pages[pages.length - 1]
    const q = cur?.options || {}
    if (q.id) {
      userCouponId.value = q.id
      loadCoupon(q.id)
    }
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
.loading { padding: 80rpx 24rpx; text-align: center; }
.loading-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.content { padding: 24rpx; }
.coupon-preview { padding: 24rpx; background: linear-gradient(135deg, #ff9a9e, #fad0c4); border-radius: 16rpx; color: #fff; margin-bottom: 24rpx; }
.cp-name { display: block; font-size: 30rpx; font-weight: 600; }
.cp-meta { display: block; font-size: 24rpx; margin-top: 6rpx; opacity: 0.85; }
.form-card { background: var(--color-surface); padding: 24rpx; border-radius: 16rpx; }
.form-label { display: block; font-size: 26rpx; font-weight: 500; margin-bottom: 12rpx; }
.form-input { width: 100%; height: 80rpx; padding: 0 20rpx; background: var(--color-background); border-radius: 16rpx; font-size: 28rpx; }
.form-hint { display: block; margin-top: 12rpx; font-size: 22rpx; color: var(--color-text-tertiary); }
.actions { display: flex; gap: 12rpx; margin-top: 24rpx; }
.btn { flex: 1; height: 88rpx; border-radius: 44rpx; display: flex; align-items: center; justify-content: center; border: 1rpx solid var(--color-divider); font-size: 28rpx; }
.btn.primary { background: var(--color-primary); color: #fff; border-color: transparent; }
</style>