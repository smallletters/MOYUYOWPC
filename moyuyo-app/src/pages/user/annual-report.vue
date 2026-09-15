<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="title">{{ t('annualReport.yearReport', { year }) }}</text>
    </view>

    <view v-if="loading" class="loading">
      <text class="loading-text">{{ t('common.loading') }}</text>
    </view>
    <view v-else class="content">
      <view class="hero">
        <text class="hero-year">{{ year }}</text>
        <text class="hero-title">{{ t('annualReport.heroTitle') }}</text>
      </view>

      <view class="stat-grid">
        <view class="stat-card">
          <text class="stat-num">{{ report?.orderCount || 0 }}</text>
          <text class="stat-label">{{ t('annualReport.ordersCount') }}</text>
        </view>
        <view class="stat-card">
          <text class="stat-num">{{ currencySymbol }}{{ report?.totalSpent || 0 }}</text>
          <text class="stat-label">{{ t('annualReport.totalSpent') }}</text>
        </view>
        <view class="stat-card">
          <text class="stat-num">+{{ report?.pointsEarned || 0 }}</text>
          <text class="stat-label">{{ t('annualReport.pointsEarned') }}</text>
        </view>
        <view class="stat-card">
          <text class="stat-num">
            {{ t('annualReport.daysSuffix', { n: report?.daysWithUs || 0 }) }}
          </text>
          <text class="stat-label">{{ t('annualReport.daysWithUs') }}</text>
        </view>
      </view>

      <view class="current-points">
        <text class="cp-label">{{ t('annualReport.currentPoints') }}</text>
        <text class="cp-num">{{ report?.currentPoints || 0 }}</text>
      </view>

      <view class="tip">
        <text class="tip-text">
          {{ t('annualReport.thanks', { year, nextYear: year + 1 }) }}
        </text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { i18n } from '@/i18n'
import { marketingApi } from '@/api'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userAnnualReport')

const report = ref(null)

const loading = ref(false)
const year = ref(new Date().getFullYear())
// 语言版本号:locale 变化时自增,触发模板中依赖 t() 的内容重新求值
const localeVersion = ref(0)
// 语言订阅解绑函数
let unsubLocale = null

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
  // 订阅语言切换，触发模板与本页 t() 依赖重新求值
  unsubLocale = i18n.subscribe(() => {
    localeVersion.value += 1
  })
})
onBeforeUnmount(() => {
  if (unsubLocale) unsubLocale()
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
  /* APP 端因 navigationStyle:custom 自渲染 header,
     需为系统状态栏预留顶部空间 */
  height: calc(88rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  padding: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px)) 24rpx 0;
  box-sizing: border-box;
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
