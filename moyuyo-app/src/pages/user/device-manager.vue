<template>
  <view class="device-manager">
    <view class="page-header">
      <view class="back" @click="goBack">‹</view>
      <text class="title">设备管理</text>
    </view>

    <scroll-view scroll-y class="content">
      <view class="banner">
        <text class="banner-icon">🛡</text>
        <view class="banner-info">
          <text class="banner-title">登录设备管理</text>
          <text class="banner-desc">查看已登录设备，异常设备请及时移除</text>
        </view>
      </view>

      <view v-if="loading" class="empty">加载中…</view>
      <view v-else-if="!devices.length" class="empty">暂无登录设备</view>
      <view v-else class="device-list">
        <view v-for="d in devices" :key="d.id" class="device-card">
          <text class="device-icon">📱</text>
          <view class="device-info">
            <text class="device-name">{{ d.deviceName || d.model }}</text>
            <text class="device-meta">{{ d.os }} · {{ d.location }}</text>
            <text class="device-time">{{ formatTime(d.lastActiveAt || d.loginAt) }}</text>
          </view>
          <view v-if="d.isCurrent" class="current-tag">当前</view>
          <view v-else class="remove-btn" @click="onRemove(d)">退出</view>
        </view>
      </view>
    </scroll-view>
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
    console.warn('[device-manager] load failed', e)
  } finally { loading.value = false }
}

function formatTime(t) {
  if (!t) return ''
  try { const d = new Date(t); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}` } catch { return '' }
}

function onRemove(d) {
  uni.showModal({
    title: '退出该设备？',
    success: async (r) => {
      if (r.confirm) {
        try {
          await deviceApi.removeDevice(d.id)
          devices.value = devices.value.filter((x) => x.id !== d.id)
        } catch (e) { uni.showToast({ title: '操作失败', icon: 'none' }) }
      }
    },
  })
}

function goBack() { uni.navigateBack() }
onMounted(() => { load() })
</script>

<style lang="scss" scoped>
.device-manager { min-height: 100vh; background: var(--color-background); display: flex; flex-direction: column; }
.page-header { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.back { width: 60rpx; font-size: 44rpx; color: var(--color-primary); }
.title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.content { flex: 1; padding: 16rpx; }
.banner { display: flex; gap: 16rpx; align-items: center; padding: 24rpx; background: var(--color-surface); border-radius: 16rpx; margin-bottom: 16rpx; }
.banner-icon { font-size: 48rpx; }
.banner-title { display: block; font-size: 28rpx; font-weight: 600; }
.banner-desc { display: block; font-size: 22rpx; color: var(--color-text-tertiary); margin-top: 4rpx; }
.empty { padding: 60rpx 24rpx; text-align: center; color: var(--color-text-tertiary); font-size: 26rpx; }
.device-list { display: flex; flex-direction: column; gap: 12rpx; }
.device-card { display: flex; gap: 16rpx; align-items: center; padding: 24rpx; background: var(--color-surface); border-radius: 16rpx; }
.device-icon { font-size: 40rpx; }
.device-info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.device-name { font-size: 28rpx; font-weight: 600; }
.device-meta, .device-time { font-size: 22rpx; color: var(--color-text-tertiary); }
.current-tag { padding: 4rpx 12rpx; background: var(--color-primary); color: #fff; border-radius: 999rpx; font-size: 22rpx; }
.remove-btn { padding: 8rpx 16rpx; border: 1rpx solid #c0392b; color: #c0392b; border-radius: 999rpx; font-size: 22rpx; }
</style>