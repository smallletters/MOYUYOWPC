<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="title">{{ year }} 年报</text>
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else class="content">
      <view class="hero">
        <text class="hero-year">{{ year }}</text>
        <text class="hero-title">属于你的 MOYUYO 年度报告</text>
      </view>

      <view class="stat-grid">
        <view class="stat-card">
          <text class="stat-num">{{ report?.orderCount || 0 }}</text>
          <text class="stat-label">订单数</text>
        </view>
        <view class="stat-card">
          <text class="stat-num">${{ report?.totalSpent || 0 }}</text>
          <text class="stat-label">消费总额</text>
        </view>
        <view class="stat-card">
          <text class="stat-num">+{{ report?.pointsEarned || 0 }}</text>
          <text class="stat-label">获得积分</text>
        </view>
        <view class="stat-card">
          <text class="stat-num">{{ report?.daysWithUs || 0 }} 天</text>
          <text class="stat-label">相伴时长</text>
        </view>
      </view>

      <view class="current-points">
        <text class="cp-label">当前积分</text>
        <text class="cp-num">{{ report?.currentPoints || 0 }}</text>
      </view>

      <view class="tip">
        <text class="tip-text">
          感谢 {{ year }} 年与 MOYUYO 一起走过，{{ year + 1 }} 我们继续相伴！
        </text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { marketingApi } from '@/api'

const report = ref(null)
const loading = ref(false)
const year = ref(new Date().getFullYear())

async function load() {
  loading.value = true
  try {
    report.value = await marketingApi.annualReport()
    if (report.value?.year) year.value = report.value.year
  } catch (e) {
    console.warn('[annual-report] load failed', e)
  } finally {
    loading.value = false
  }
}

function goBack() {
  uni.navigateBack()
}
onMounted(() => {
  load()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #1a1a2e, #16213e, var(--color-background));
  color: #fff;
}
.header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
}
.nav-back {
  width: 60rpx;
}
.back-icon {
  font-size: 44rpx;
  color: #fff;
}
.title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
}
.loading {
  padding: 80rpx 24rpx;
  text-align: center;
}
.loading-text {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.7);
}
.content {
  padding: 16rpx 24rpx;
}
.hero {
  padding: 48rpx 24rpx;
  text-align: center;
}
.hero-year {
  display: block;
  font-size: 100rpx;
  font-weight: 800;
  color: #f5af19;
  line-height: 1;
}
.hero-title {
  display: block;
  margin-top: 16rpx;
  font-size: 28rpx;
  opacity: 0.85;
}
.stat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}
.stat-card {
  padding: 24rpx;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 16rpx;
  text-align: center;
}
.stat-num {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #f5af19;
}
.stat-label {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.7);
}
.current-points {
  margin-top: 24rpx;
  padding: 24rpx;
  background: linear-gradient(135deg, #f5af19, #f12711);
  border-radius: 16rpx;
  text-align: center;
}
.cp-label {
  display: block;
  font-size: 24rpx;
  opacity: 0.85;
}
.cp-num {
  display: block;
  margin-top: 8rpx;
  font-size: 60rpx;
  font-weight: 800;
}
.tip {
  margin-top: 24rpx;
  padding: 24rpx;
}
.tip-text {
  font-size: 24rpx;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.75);
}
</style>
