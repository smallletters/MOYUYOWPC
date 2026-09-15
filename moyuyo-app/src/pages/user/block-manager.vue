<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="title">{{ t('blockManager.title') }}</text>
    </view>

    <view class="tip">{{ t('blockManager.tip') }}</view>

    <view v-if="loading" class="loading">
      <text class="loading-text">{{ t('blockManager.loading') }}</text>
    </view>
    <view v-else-if="!blocks.length" class="empty">
      <text class="empty-text">{{ t('blockManager.empty') }}</text>
    </view>
    <view v-else class="block-list">
      <view v-for="b in blocks" :key="b.id" class="block-card">
        <view class="avatar">{{ (b.targetNickname || b.targetName || 'U')[0] }}</view>
        <view class="info">
          <text class="name">
            {{ b.targetNickname || t('blockManager.userFmt', { id: b.targetId }) }}
          </text>
          <text v-if="b.reason" class="reason">
            {{ t('blockManager.reasonFmt', { reason: b.reason }) }}
          </text>
          <text class="time">{{ formatTime(b.createdAt || b.blockedAt) }}</text>
        </view>
        <view class="unblock" @tap="unblock(b)">{{ t('blockManager.unblock') }}</view>
      </view>
    </view>

    <view class="bottom-bar">
      <view class="add-btn" @tap="showAddDialog">{{ t('blockManager.addBlock') }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { blockApi } from '@/api'
import { i18n } from '@/i18n'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userBlockManager')

// 轻量翻译函数（响应 localeVersion 变化，刷新依赖本地化的模板）
const localeVersion = ref(0)
let _unsubLocale = null
function t(key, params) {
  void localeVersion.value // 触发依赖追踪
  return i18n.t(key, params)
}

const blocks = ref([])

const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await blockApi.listBlocks({ size: 100 })
    blocks.value = res?.records || res || []
  } catch (e) {
    console.warn('[block] load failed', e)
  } finally {
    loading.value = false
  }
}

function showAddDialog() {
  uni.showModal({
    title: t('blockManager.addTitle'),
    editable: true,
    placeholderText: t('blockManager.userIdPlaceholder'),
    success: async (res) => {
      if (res.confirm && res.content) {
        try {
          await blockApi.blockUser(Number(res.content))
          await load()
          uni.showToast({ title: t('blockManager.added'), icon: 'none' })
        } catch (e) {
          uni.showToast({ title: t('blockManager.operationFailed'), icon: 'none' })
        }
      }
    },
  })
}

async function unblock(b) {
  uni.showModal({
    title: t('blockManager.unblockTitle'),
    content: b.targetNickname || t('blockManager.userFmt', { id: b.targetId }),
    success: async (res) => {
      if (res.confirm) {
        try {
          await blockApi.unblockUser(b.id)
          blocks.value = blocks.value.filter((x) => x.id !== b.id)
        } catch (e) {
          uni.showToast({ title: t('blockManager.operationFailed'), icon: 'none' })
        }
      }
    },
  })
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
.tip {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  padding: 16rpx 24rpx;
  line-height: 1.5;
}
.loading {
  padding: 60rpx 24rpx;
  text-align: center;
}
.loading-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
.empty {
  padding: 80rpx 24rpx;
  text-align: center;
}
.empty-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
.block-list {
  padding: 0 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.block-card {
  display: flex;
  gap: 16rpx;
  align-items: center;
  padding: 20rpx;
  background: var(--color-surface);
  border-radius: 16rpx;
}
.avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #999;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}
.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.name {
  font-size: 28rpx;
  font-weight: 600;
}
.reason {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.time {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.unblock {
  padding: 8rpx 16rpx;
  border: 1rpx solid var(--color-primary);
  color: var(--color-primary);
  border-radius: 999rpx;
  font-size: 22rpx;
}
.bottom-bar {
  padding: 24rpx;
}
.add-btn {
  height: 88rpx;
  border-radius: 44rpx;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}
</style>
