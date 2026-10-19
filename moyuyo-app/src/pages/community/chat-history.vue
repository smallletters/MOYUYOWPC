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
        <text class="loading-text">{{ $t('common.loading') }}</text>
      </view>
      <view v-else-if="filteredChats.length === 0" class="empty">
        <text class="empty-text">{{ $t('communityChatHistory.empty') }}</text>
      </view>
      <view
        v-for="chat in filteredChats"
        :key="chat.id"
        class="chat-item"
        @tap="enterChat(chat)">
        <view class="chat-avatar">{{ statusLabel(chat.status).charAt(0) }}</view>
        <view class="chat-info">
          <view class="chat-row1">
            <text class="chat-title">
              {{ $t('communityChatHistory.sessionTitle', { id: chat.sessionId || chat.id }) }}
            </text>
            <text class="chat-time">{{ formatTime(chat.lastMessageAt || chat.createTime) }}</text>
          </view>
          <view class="chat-row2">
            <text class="chat-status" :class="'status-' + (chat.status || 'WAITING')">
              {{ statusLabel(chat.status) }}
            </text>
            <text class="chat-count">
              {{ $t('communityChatHistory.messageCount', { count: chat.messageCount || 0 }) }}
            </text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { i18n } from '@/i18n'
import { csApi } from '@/api'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.communityChatHistory')

const tabs = computed(() => [
  i18n.t('communityChatHistory.tabAll'),
  i18n.t('communityChatHistory.tabProcessing'),
  i18n.t('communityChatHistory.tabClosed'),
])

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
  if (s === 'PROCESSING') return i18n.t('communityChatHistory.statusProcessing')
  if (s === 'CLOSED') return i18n.t('communityChatHistory.statusClosed')
  return i18n.t('communityChatHistory.statusWaiting')
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
  /* 视口锁定外壳：唯一滚动权威是内部 .chat-list(scroll-y)。tab-bar 为常流前置子项，
     必须 flex-shrink:0 防塌陷。 */
  display: flex;
  flex-direction: column;
  /* 视口高度：三层渐进增强（100vh 兜底 → 100dvh → 扣除 --window-top 顶部窗口偏移）。
     非 custom 页 --window-top = 导航栏高 + 状态栏高，必须扣除，否则顶部内容顶到导航栏下。
     末尾 0px 兜底：避免变量缺失时整条声明 invalid-at-computed-value → height 退化为 auto。 */
  height: 100vh;
  height: 100dvh;
  height: calc(100dvh - var(--window-top, 0px));
  overflow: hidden;
  background: var(--color-background);
}
/* 说明：原 .nav-back / .nav-title 为死代码（模板中不存在对应元素，本页导航栏由原生
   titleNView 提供），已移除。 */
.tab-bar {
  flex-shrink: 0;
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
  /* 唯一滚动权威：flex:1 占满 tab-bar 之后的剩余空间，min-height:0 允许收缩以产生滚动。
     取代 calc(100vh - 160rpx)（160=导航88+tab-bar72 的硬编码和，任一变化即失配）。 */
  flex: 1;
  min-height: 0;
  /* 显式宽度让 scroll-view 跟随屏幕宽度 */
  width: 100%;
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
