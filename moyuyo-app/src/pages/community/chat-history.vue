<template>
  <view class="chat-history-page">


    <view class="tab-bar">
      <view
        v-for="(tab, index) in tabs"
        :key="index"
        class="tab-item"
        :class="{ 'tab-active': activeTab === index }"
        @tap="switchTab(index)"
      >
        <text class="tab-text" :class="{ 'tab-text-active': activeTab === index }">{{ tab }}</text>
      </view>
    </view>

    <scroll-view class="chat-list" scroll-y>
      <view v-if="loading" class="loading">
        <text class="loading-text">加载中…</text>
      </view>
      <view v-else-if="filteredChats.length === 0" class="empty">
        <text class="empty-text">暂无会话记录</text>
      </view>
      <view
        v-for="chat in filteredChats"
        :key="chat.id"
        class="chat-item"
        @tap="enterChat(chat)">
        <view class="chat-avatar">{{ statusLabel(chat.status).charAt(0) }}</view>
        <view class="chat-info">
          <view class="chat-row1">
            <text class="chat-title">会话 {{ chat.sessionId || chat.id }}</text>
            <text class="chat-time">{{ formatTime(chat.lastMessageAt || chat.createTime) }}</text>
          </view>
          <view class="chat-row2">
            <text class="chat-status" :class="'status-' + (chat.status || 'WAITING')">
              {{ statusLabel(chat.status) }}
            </text>
            <text class="chat-count">{{ chat.messageCount || 0 }} 条消息</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { csApi } from '@/api'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.communityChatHistory')


const tabs = ['全部', '进行中', '已关闭']


const activeTab = ref(0)
const sessions = ref([])
const loading = ref(false)

const filteredChats = computed(() => {
  if (activeTab.value === 1) return sessions.value.filter((s) => s.status !== 'CLOSED')
  if (activeTab.value === 2) return sessions.value.filter((s) => s.status === 'CLOSED')
  return sessions.value
})

function switchTab(i) {
  activeTab.value = i
}

function statusLabel(s) {
  if (s === 'PROCESSING') return '进行中'
  if (s === 'CLOSED') return '已关闭'
  return '待响应'
}

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return ''
  }
}

async function loadSessions() {
  loading.value = true
  try {
    const res = await csApi.listSessions({ page: 1, size: 50 })
    sessions.value = res?.records || res || []
  } catch (e) {
    console.warn('[chat-history] load failed', e)
  } finally {
    loading.value = false
  }
}

function enterChat(chat) {
  // 复用 customer-service 页面（新建/续接会话时使用其 sessionId）
  uni.navigateTo({ url: '/pages/user/customer-service' })
}

function goBack() {
  uni.navigateBack()
}

onMounted(() => {
  loadSessions()
})
</script>

<style lang="scss" scoped>
.chat-history-page {
  min-height: 100vh;
  background: var(--color-background);
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
.tab-bar {
  display: flex;
  gap: 24rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.tab-item {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
}
.tab-active {
  background: var(--color-primary);
}
.tab-text {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}
.tab-text-active {
  color: #fff;
  font-weight: 600;
}
.chat-list {
  /* 显式宽度让 scroll-view 跟随屏幕宽度 */
  width: 100%;
  height: calc(100vh - 160rpx);
  box-sizing: border-box;
}
.chat-item {
  display: flex;
  gap: 16rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.chat-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  flex-shrink: 0;
}
.chat-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.chat-row1 {
  display: flex;
  justify-content: space-between;
}
.chat-title {
  font-size: 28rpx;
  font-weight: 600;
}
.chat-time {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.chat-row2 {
  display: flex;
  gap: 12rpx;
  align-items: center;
}
.chat-status {
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
}
.status-WAITING {
  background: #fff7e6;
  color: #b8860b;
}
.status-PROCESSING {
  background: #e6f7ff;
  color: #007aff;
}
.status-CLOSED {
  background: #f0f0f0;
  color: #999;
}
.chat-count {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
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
</style>
