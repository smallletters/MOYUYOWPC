<template>
  <view class="device-manager">
    <view class="page-header">
      <view class="back" @click="goBack"><text class="luc luc-arrow-left" /></view>
      <text class="title">{{ t('deviceManager.title') }}</text>
    </view>

    <scroll-view scroll-y class="content">
      <view class="banner">
        <text class="banner-icon luc-shield" />
        <view class="banner-info">
          <text class="banner-title">{{ t('deviceManager.bannerTitle') }}</text>
          <text class="banner-desc">{{ t('deviceManager.bannerDesc') }}</text>
        </view>
      </view>

      <view v-if="loading" class="empty">{{ t('deviceManager.loading') }}</view>
      <view v-else-if="!devices.length" class="empty">{{ t('deviceManager.empty') }}</view>
      <view v-else class="device-list">
        <view v-for="d in devices" :key="d.id" class="device-card">
          <text class="device-icon luc-smartphone" />
          <view class="device-info">
            <text class="device-name">{{ d.deviceName || d.model }}</text>
            <text class="device-meta">{{ d.os }} · {{ d.location }}</text>
            <text class="device-time">{{ formatTime(d.lastActiveAt || d.loginAt) }}</text>
          </view>
          <view v-if="d.isCurrent" class="current-tag">{{ t('deviceManager.current') }}</view>
          <view v-else class="remove-btn" @click="onRemove(d)">
            {{ t('deviceManager.remove') }}
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { deviceApi } from '@/api'
import { i18n } from '@/i18n'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userDeviceManager')

// 轻量翻译函数（响应 localeVersion 变化，刷新依赖本地化的模板）
const localeVersion = ref(0)
let _unsubLocale = null
function t(key, params) {
  void localeVersion.value // 触发依赖追踪
  return i18n.t(key, params)
}

const devices = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await deviceApi.listDevices({ size: 50 })
    devices.value = res?.records || res || []
  } catch (e) {
    console.warn('[device-manager] load failed', e)
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

function onRemove(d) {
  uni.showModal({
    title: t('deviceManager.removeTitle'),
    success: async (r) => {
      if (r.confirm) {
        try {
          await deviceApi.removeDevice(d.id)
          devices.value = devices.value.filter((x) => x.id !== d.id)
        } catch (e) {
          uni.showToast({ title: t('deviceManager.operationFailed'), icon: 'none' })
        }
      }
    },
  })
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
.device-manager {
  min-height: 100vh;
  background: var(--color-background);
  display: flex;
  flex-direction: column;
}
.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.back {
  width: 60rpx;
  font-size: 44rpx;
  color: var(--color-primary);
}
.title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
.content {
  flex: 1;
  padding: 16rpx;
}
.banner {
  display: flex;
  gap: 16rpx;
  align-items: center;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: 16rpx;
  margin-bottom: 16rpx;
}
.banner-icon {
  font-size: 48rpx;
}
.banner-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
}
.banner-desc {
  display: block;
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}
.empty {
  padding: 60rpx 24rpx;
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: 26rpx;
}
.device-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.device-card {
  display: flex;
  gap: 16rpx;
  align-items: center;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: 16rpx;
}
.device-icon {
  font-size: 40rpx;
}
.device-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.device-name {
  font-size: 28rpx;
  font-weight: 600;
}
.device-meta,
.device-time {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.current-tag {
  padding: 4rpx 12rpx;
  background: var(--color-primary);
  color: #fff;
  border-radius: 999rpx;
  font-size: 22rpx;
}
.remove-btn {
  padding: 8rpx 16rpx;
  border: 1rpx solid #c0392b;
  color: #c0392b;
  border-radius: 999rpx;
  font-size: 22rpx;
}
</style>
