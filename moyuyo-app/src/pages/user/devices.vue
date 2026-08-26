<template>
  <view class="devices">
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack"><text class="back-icon">‹</text></view>
      <text class="nav-title">登录设备</text>
      <view class="nav-placeholder" />
    </view>

    <view class="tip">您最多可在 3 台设备上登录。在陌生设备上发现账户活动？立即踢出。</view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else-if="!devices.length" class="empty"><text class="empty-text">暂无登录设备</text></view>
    <view v-else>
      <view v-for="d in devices" :key="d.id" class="card device-card">
        <view class="device-info">
          <text class="device-icon">{{ deviceIcon(d.deviceType) }}</text>
          <view class="device-meta">
            <text class="device-name">
              {{ d.deviceName || d.model || '未知设备' }}
              <text v-if="d.isCurrent" class="current-tag">当前</text>
            </text>
            <text class="device-detail">{{ d.os || '' }} {{ d.browser ? '· ' + d.browser : '' }}</text>
            <text class="device-detail">{{ d.location || '' }} · 最近活跃 {{ formatTime(d.lastActiveAt || d.loginAt) }}</text>
          </view>
        </view>
        <view class="device-actions">
          <view v-if="!d.isCurrent" class="trust" @tap="onKick(d)">踢出</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { deviceApi } from '@/api'

const devices = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await deviceApi.listDevices({ size: 50 })
    devices.value = res?.records || res || []
  } catch (e) {
    console.warn('[devices] load failed', e)
  } finally {
    loading.value = false
  }
}

function deviceIcon(t) {
  if (!t) return '📱'
  if (/ios|iphone|ipad/i.test(t)) return '📱'
  if (/android/i.test(t)) return '🤖'
  if (/mac|windows|linux|desktop/i.test(t)) return '💻'
  return '📱'
}

function formatTime(t) {
  if (!t) return '未知'
  try { const d = new Date(t); return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` } catch { return '' }
}

function onKick(d) {
  uni.showModal({
    title: '踢出该设备？',
    content: d.deviceName || '该设备',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deviceApi.removeDevice(d.id)
          devices.value = devices.value.filter((x) => x.id !== d.id)
          uni.showToast({ title: '已踢出', icon: 'none' })
        } catch (e) {
          uni.showToast({ title: '操作失败', icon: 'none' })
        }
      }
    },
  })
}

function goBack() { uni.navigateBack() }

onMounted(() => { load() })
</script>

<style lang="scss" scoped>
.devices { min-height: 100vh; background: var(--color-background); padding: 16rpx; }
.nav-bar { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); margin: -16rpx -16rpx 16rpx; }
.nav-back { width: 60rpx; }
.back-icon { font-size: 44rpx; color: var(--color-primary); }
.nav-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.nav-placeholder { width: 60rpx; }
.tip { font-size: 24rpx; color: var(--color-text-tertiary); padding: 16rpx; line-height: 1.6; }
.loading, .empty { padding: 60rpx 24rpx; text-align: center; }
.loading-text, .empty-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.device-card { background: var(--color-surface); border-radius: 16rpx; padding: 24rpx; margin-bottom: 16rpx; }
.device-info { display: flex; gap: 16rpx; align-items: flex-start; }
.device-icon { font-size: 48rpx; }
.device-meta { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.device-name { font-size: 28rpx; font-weight: 600; }
.current-tag { display: inline-block; margin-left: 8rpx; padding: 2rpx 10rpx; background: var(--color-primary); color: #fff; font-size: 20rpx; border-radius: 999rpx; }
.device-detail { font-size: 22rpx; color: var(--color-text-tertiary); }
.device-actions { margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx solid var(--color-divider); display: flex; justify-content: flex-end; }
.trust { font-size: 26rpx; color: #c0392b; }
</style>