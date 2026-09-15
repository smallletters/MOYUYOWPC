<template>
  <view class="devices">
    <view class="tip">{{ t('devices.tip') }}</view>

    <view v-if="loading" class="loading">
      <text class="loading-text">{{ t('devices.loading') }}</text>
    </view>
    <view v-else-if="!devices.length" class="empty">
      <text class="empty-text">{{ t('devices.empty') }}</text>
    </view>
    <view v-else>
      <view v-for="d in devices" :key="d.id" class="card device-card">
        <view class="device-info">
          <text class="device-icon luc" :class="$luc(deviceIcon(d.deviceType))" />
          <view class="device-meta">
            <text class="device-name">
              {{ d.deviceName || d.model || t('devices.unknownDevice') }}
              <text v-if="d.isCurrent" class="current-tag">{{ t('devices.current') }}</text>
            </text>
            <text class="device-detail">
              {{ d.os || '' }} {{ d.browser ? '· ' + d.browser : '' }}
            </text>
            <text class="device-detail">
              {{ d.location || '' }} ·
              {{ t('devices.lastActive', { time: formatTime(d.lastActiveAt || d.loginAt) }) }}
            </text>
          </view>
        </view>
        <view class="device-actions">
          <view v-if="!d.isCurrent" class="trust" @tap="onKick(d)">{{ t('devices.kick') }}</view>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { deviceApi } from '@/api'
import { i18n } from '@/i18n'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userDevices')

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
    console.warn('[devices] load failed', e)
  } finally {
    loading.value = false
  }
}

function deviceIcon(t) {
  if (!t) return 'smartphone'
  if (/ios|iphone|ipad/i.test(t)) return 'smartphone'
  if (/android/i.test(t)) return 'cpu'
  if (/mac|windows|linux|desktop/i.test(t)) return 'laptop'
  return 'smartphone'
}

function formatTime(t) {
  if (!t) return i18n.t('devices.unknownTime')
  try {
    const d = new Date(t)
    return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return ''
  }
}

function onKick(d) {
  uni.showModal({
    title: t('devices.kickTitle'),
    content: d.deviceName || t('devices.thisDevice'),
    success: async (res) => {
      if (res.confirm) {
        try {
          await deviceApi.removeDevice(d.id)
          devices.value = devices.value.filter((x) => x.id !== d.id)
          uni.showToast({ title: t('devices.kicked'), icon: 'none' })
        } catch (e) {
          uni.showToast({ title: t('devices.operationFailed'), icon: 'none' })
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
.devices {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
}
.nav-back {
  width: 60rpx;
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
.tip {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  padding: 16rpx;
  line-height: 1.6;
}
.loading,
.empty {
  padding: 60rpx 24rpx;
  text-align: center;
}
.loading-text,
.empty-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
.device-card {
  background: var(--color-surface);
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.device-info {
  display: flex;
  gap: 16rpx;
  align-items: flex-start;
}
.device-icon {
  font-size: 48rpx;
}
.device-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.device-name {
  font-size: 28rpx;
  font-weight: 600;
}
.current-tag {
  display: inline-block;
  margin-left: 8rpx;
  padding: 2rpx 10rpx;
  background: var(--color-primary);
  color: #fff;
  font-size: 20rpx;
  border-radius: 999rpx;
}
.device-detail {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.device-actions {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
  display: flex;
  justify-content: flex-end;
}
.trust {
  font-size: 26rpx;
  color: #c0392b;
}
</style>
