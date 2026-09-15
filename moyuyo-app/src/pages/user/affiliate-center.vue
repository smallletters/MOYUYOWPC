<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="title">{{ $t('affiliateCenter.title') }}</text>
    </view>

    <view v-if="loading" class="loading">
      <text class="loading-text">{{ $t('common.loading') }}</text>
    </view>
    <view v-else class="content">
      <view class="hero">
        <text class="hero-title">{{ $t('affiliateCenter.heroTitle') }}</text>
        <view class="hero-stats">
          <view class="stat">
            <text class="stat-num">{{ account?.totalInvites || 0 }}</text>
            <text class="stat-label">{{ $t('affiliateCenter.statInvites') }}</text>
          </view>
          <view class="stat">
            <text class="stat-num">{{ currencySymbol }}{{ account?.totalCommission || 0 }}</text>
            <text class="stat-label">{{ $t('affiliateCenter.statCommission') }}</text>
          </view>
          <view class="stat">
            <text class="stat-num">{{ currencySymbol }}{{ account?.availableAmount || 0 }}</text>
            <text class="stat-label">{{ $t('affiliateCenter.statAvailable') }}</text>
          </view>
        </view>
      </view>

      <view class="level-card">
        <text class="level-label">{{ $t('affiliateCenter.levelLabel') }}</text>
        <text class="level-name">{{ account?.level || 'BRONZE' }}</text>
      </view>

      <view class="section">
        <text class="section-title">{{ $t('affiliateCenter.commissionTitle') }}</text>
        <view v-if="loading" class="empty">
          <text class="empty-text">{{ $t('common.loading') }}</text>
        </view>
        <view v-else-if="!commissions.length" class="empty">
          <text class="empty-text">{{ $t('affiliateCenter.emptyCommission') }}</text>
        </view>
        <view v-else class="commission-list">
          <view v-for="c in commissions" :key="c.id" class="commission-item">
            <view class="ci-left">
              <text class="ci-title">{{ c.description || $t('affiliateCenter.orderLabel') }}</text>
              <text class="ci-meta">{{ formatTime(c.createTime) }}</text>
            </view>
            <text class="ci-amount">+{{ currencySymbol }}{{ c.amount || 0 }}</text>
          </view>
        </view>
      </view>

      <view class="actions">
        <view class="btn" @tap="shareLink">{{ $t('affiliateCenter.shareLink') }}</view>
        <view class="btn primary" @tap="withdraw">{{ $t('affiliateCenter.withdraw') }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { marketingApi } from '@/api'
import { usePageTitle } from '@/utils/i18nPageMixin'
import { i18n } from '@/i18n'
usePageTitle('pageTitle.userAffiliateCenter')

const account = ref(null)

const commissions = ref([])
const loading = ref(false)

// 语言版本号：语言切换时触发依赖刷新
const localeVersion = ref(0)
let _unsubLocale = null
// 货币符号（随语言切换）
const currencySymbol = computed(() => {
  void localeVersion.value
  return i18n.currencySymbol
})

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
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
  } catch {
    return ''
  }
}

function shareLink() {
  uni.setClipboardData({
    data: 'https://moyuyo.com/invite/' + (account.value?.userId || ''),
    success: () => uni.showToast({ title: i18n.t('affiliateCenter.linkCopied'), icon: 'none' }),
  })
}

function withdraw() {
  uni.showToast({ title: i18n.t('affiliateCenter.withdrawDev'), icon: 'none' })
}

function goBack() {
  uni.navigateBack()
}
onMounted(() => {
  load()
  _unsubLocale = i18n.subscribe(() => {
    localeVersion.value += 1
  })
})
onBeforeUnmount(() => {
  if (_unsubLocale) _unsubLocale()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--color-background);
}
.header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.nav-back {
  width: 60rpx;
}
.back-icon {
  font-size: 44rpx;
  color: var(--color-primary);
}
.title {
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
  padding: 16rpx;
}
.hero {
  padding: 32rpx 24rpx;
  background: linear-gradient(135deg, #6a11cb, #2575fc);
  border-radius: 20rpx;
  color: #fff;
}
.hero-title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  margin-bottom: 16rpx;
}
.hero-stats {
  display: flex;
  justify-content: space-between;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}
.stat-num {
  font-size: 36rpx;
  font-weight: 700;
}
.stat-label {
  font-size: 22rpx;
  opacity: 0.85;
}
.level-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: 16rpx;
  margin-top: 16rpx;
}
.level-label {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
.level-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-primary);
}
.section {
  margin-top: 24rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: 16rpx;
}
.section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}
.empty {
  padding: 30rpx;
  text-align: center;
}
.empty-text {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}
.commission-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.commission-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx;
  background: var(--color-background);
  border-radius: 12rpx;
}
.ci-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.ci-title {
  font-size: 26rpx;
  font-weight: 500;
}
.ci-meta {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.ci-amount {
  font-size: 28rpx;
  font-weight: 700;
  color: #ee5a52;
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
