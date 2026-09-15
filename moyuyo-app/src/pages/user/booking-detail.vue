<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="title">{{ $t('bookingDetail.title') }}</text>
    </view>

    <view v-if="loading" class="loading">
      <text class="loading-text">{{ $t('bookingDetail.loading') }}</text>
    </view>
    <view v-else-if="!detail" class="empty">
      <text class="empty-text">{{ $t('bookingDetail.notFound') }}</text>
    </view>
    <view v-else class="content">
      <view class="card">
        <view class="row">
          <text class="label">{{ $t('bookingDetail.bookingNo') }}</text>
          <text class="value">#{{ detail.id }}</text>
        </view>
        <view class="row">
          <text class="label">{{ $t('bookingDetail.type') }}</text>
          <text class="value">{{ detail.serviceType || detail.type || '—' }}</text>
        </view>
        <view class="row">
          <text class="label">{{ $t('bookingDetail.bookingTime') }}</text>
          <text class="value">{{ formatTime(detail.bookingTime || detail.scheduledAt) }}</text>
        </view>
        <view v-if="detail.location" class="row">
          <text class="label">{{ $t('bookingDetail.location') }}</text>
          <text class="value">{{ detail.location }}</text>
        </view>
        <view v-if="detail.contact" class="row">
          <text class="label">{{ $t('bookingDetail.contact') }}</text>
          <text class="value">{{ detail.contact }}</text>
        </view>
        <view class="row">
          <text class="label">{{ $t('bookingDetail.status') }}</text>
          <text class="value status" :class="'status-' + (detail.status || 'PENDING')">
            {{ statusLabel(detail.status) }}
          </text>
        </view>
        <view v-if="detail.remark" class="row">
          <text class="label">{{ $t('bookingDetail.remark') }}</text>
          <text class="value">{{ detail.remark }}</text>
        </view>
      </view>

      <view v-if="detail.status !== 'CANCELLED' && detail.status !== 'COMPLETED'" class="actions">
        <view class="btn" @tap="cancel">{{ $t('bookingDetail.cancelBooking') }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { marketingApi } from '@/api'
import { i18n } from '@/i18n'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userBookingDetail')

const detail = ref(null)

const loading = ref(false)
const bookingId = ref(null)
// 语言切换版本号：computed/函数内读取它以跟随语言刷新
const localeVersion = ref(0)
let _unsubLocale = null

async function load(id) {
  loading.value = true
  try {
    detail.value = await marketingApi.bookingDetail(id)
  } catch (e) {
    console.warn('[booking-detail] load failed', e)
  } finally {
    loading.value = false
  }
}

async function cancel() {
  try {
    await marketingApi.cancelBooking(bookingId.value)
    detail.value.status = 'CANCELLED'
    uni.showToast({ title: i18n.t('bookingDetail.cancelled'), icon: 'none' })
  } catch (e) {
    uni.showToast({ title: i18n.t('bookingDetail.cancelFailed'), icon: 'none' })
  }
}

function statusLabel(s) {
  void localeVersion.value // 建立响应式依赖，语言切换时刷新
  if (s === 'CONFIRMED') return i18n.t('bookingDetail.statusConfirmed')
  if (s === 'CANCELLED') return i18n.t('bookingDetail.statusCancelled')
  if (s === 'COMPLETED') return i18n.t('bookingDetail.statusCompleted')
  return i18n.t('bookingDetail.statusPending')
}

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return ''
  }
}

function goBack() {
  uni.navigateBack()
}

onMounted(() => {
  _unsubLocale = i18n.subscribe(() => {
    localeVersion.value += 1
  })
  try {
    const pages = getCurrentPages()
    const cur = pages[pages.length - 1]
    const q = cur?.options || {}
    if (q.id) {
      bookingId.value = q.id
      load(q.id)
    }
  } catch (e) {
    /* ignore */
  }
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
.loading,
.empty {
  padding: 80rpx 24rpx;
  text-align: center;
}
.loading-text,
.empty-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
.content {
  padding: 16rpx;
}
.card {
  background: var(--color-surface);
  border-radius: 16rpx;
  padding: 16rpx 24rpx;
}
.row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}
.row:last-child {
  border-bottom: none;
}
.label {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
.value {
  font-size: 26rpx;
  color: var(--color-text);
}
.status {
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  background: #f0f0f0;
}
.status-CONFIRMED {
  background: #e6f7ff;
  color: var(--color-primary);
}
.status-CANCELLED {
  background: #ffecec;
  color: #c0392b;
}
.status-COMPLETED {
  background: #f0f9eb;
  color: #67c23a;
}
.actions {
  margin-top: 24rpx;
}
.btn {
  height: 88rpx;
  border-radius: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #c0392b;
  color: #c0392b;
  font-size: 28rpx;
}
</style>
