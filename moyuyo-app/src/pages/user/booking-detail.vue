<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="title">预约详情</text>
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else-if="!detail" class="empty"><text class="empty-text">预约不存在</text></view>
    <view v-else class="content">
      <view class="card">
        <view class="row">
          <text class="label">预约编号</text>
          <text class="value">#{{ detail.id }}</text>
        </view>
        <view class="row">
          <text class="label">类型</text>
          <text class="value">{{ detail.serviceType || detail.type || '—' }}</text>
        </view>
        <view class="row">
          <text class="label">预约时间</text>
          <text class="value">{{ formatTime(detail.bookingTime || detail.scheduledAt) }}</text>
        </view>
        <view v-if="detail.location" class="row">
          <text class="label">地点</text>
          <text class="value">{{ detail.location }}</text>
        </view>
        <view v-if="detail.contact" class="row">
          <text class="label">联系方式</text>
          <text class="value">{{ detail.contact }}</text>
        </view>
        <view class="row">
          <text class="label">状态</text>
          <text class="value status" :class="'status-' + (detail.status || 'PENDING')">
            {{ statusLabel(detail.status) }}
          </text>
        </view>
        <view v-if="detail.remark" class="row">
          <text class="label">备注</text>
          <text class="value">{{ detail.remark }}</text>
        </view>
      </view>

      <view v-if="detail.status !== 'CANCELLED' && detail.status !== 'COMPLETED'" class="actions">
        <view class="btn" @tap="cancel">取消预约</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { marketingApi } from '@/api'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userBookingDetail')


const detail = ref(null)


const loading = ref(false)
const bookingId = ref(null)

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
    uni.showToast({ title: '已取消预约', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: '取消失败', icon: 'none' })
  }
}

function statusLabel(s) {
  if (s === 'CONFIRMED') return '已确认'
  if (s === 'CANCELLED') return '已取消'
  if (s === 'COMPLETED') return '已完成'
  return '待确认'
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
