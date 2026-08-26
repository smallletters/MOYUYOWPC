<template>
  <view class="cs">
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">客服中心</text>
      <view class="header-action" @click="goHistory">会话记录</view>
    </view>

    <scroll-view class="chat-area" scroll-y :scroll-top="scrollTop">
      <view class="msg-row msg-bot">
        <view class="bot-avatar">M</view>
        <view class="msg-content">
          <view class="msg-bubble msg-bot-bubble">
            <text class="msg-text">您好！我是 MOYUYO 智能客服小助手，请问需要什么帮助？</text>
          </view>
        </view>
      </view>
      <view v-for="m in messages" :key="m.id" :id="`msg-${m.id}`" class="msg-row" :class="m.senderType === 'USER' ? 'msg-user' : 'msg-bot'">
        <view v-if="m.senderType !== 'USER'" class="bot-avatar">{{ (m.senderName || 'M')[0] }}</view>
        <view class="msg-content" :class="{ 'msg-user-content': m.senderType === 'USER' }">
          <view class="msg-bubble" :class="m.senderType === 'USER' ? 'msg-user-bubble' : 'msg-bot-bubble'">
            <text class="msg-text" :class="{ 'msg-user-text': m.senderType === 'USER' }">{{ m.content }}</text>
          </view>
          <text class="msg-time">{{ formatTime(m.createTime) }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="quick-replies">
      <view v-for="q in quickReplies" :key="q.text" class="quick-btn" @click="sendText(q.text)">
        <text class="quick-btn-text">{{ q.text }}</text>
      </view>
    </view>

    <view class="bottom-bar">
      <input class="input" v-model="draft" placeholder="请输入消息…" @confirm="sendCurrent" />
      <view class="action-btn" @click="sendCurrent">
        <text class="action-btn-text">发送</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { csApi } from '@/api'

const sessionId = ref(null)
const messages = ref([])
const draft = ref('')
const scrollTop = ref(0)
const quickReplies = ref([
  { text: '查询物流' },
  { text: '申请退款' },
  { text: '如何开发票' },
  { text: '优惠券怎么用' },
])

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    return `${hh}:${mm}`
  } catch { return '' }
}

async function ensureSession() {
  try {
    const sess = await csApi.createSession('GENERAL')
    sessionId.value = sess?.id || sess?.sessionId
    const list = await csApi.listMessages(sessionId.value)
    messages.value = Array.isArray(list) ? list : (list?.records || [])
    scrollToBottom()
  } catch (e) {
    console.warn('[cs] ensureSession failed', e)
  }
}

function scrollToBottom() {
  nextTick(() => { scrollTop.value = 9999 })
}

async function sendText(text) {
  if (!text) return
  if (!sessionId.value) await ensureSession()
  const sid = sessionId.value
  if (!sid) return
  // 乐观更新
  const tempId = Date.now()
  messages.value.push({ id: tempId, senderType: 'USER', content: text, createTime: new Date().toISOString() })
  draft.value = ''
  scrollToBottom()
  try {
    await csApi.sendMessage(sid, text)
    // 拉一次最新消息
    const list = await csApi.listMessages(sid)
    messages.value = Array.isArray(list) ? list : (list?.records || [])
    scrollToBottom()
  } catch (e) {
    console.warn('[cs] send failed', e)
  }
}

function sendCurrent() {
  const t = draft.value.trim()
  if (!t) return
  sendText(t)
}

function goBack() { uni.navigateBack() }
function goHistory() { uni.navigateTo({ url: '/pages/community/chat-history' }) }

onMounted(() => { ensureSession() })
</script>

<style lang="scss" scoped>
.cs {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.back-btn { width: 60rpx; }
.back-icon { font-size: 44rpx; color: var(--color-primary); }
.header-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.header-action { font-size: 26rpx; color: var(--color-primary); }
.chat-area { flex: 1; padding: 16rpx 24rpx; }
.msg-row { display: flex; gap: 12rpx; margin-bottom: 16rpx; }
.msg-bot { align-self: flex-start; }
.msg-user { justify-content: flex-end; }
.bot-avatar { width: 56rpx; height: 56rpx; border-radius: 50%; background: var(--color-primary); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 28rpx; flex-shrink: 0; }
.msg-content { display: flex; flex-direction: column; max-width: 70%; }
.msg-user-content { align-items: flex-end; }
.msg-bubble { padding: 16rpx 20rpx; border-radius: 20rpx; font-size: 26rpx; line-height: 1.5; }
.msg-bot-bubble { background: var(--color-surface); border-top-left-radius: 4rpx; }
.msg-user-bubble { background: var(--color-primary); border-top-right-radius: 4rpx; }
.msg-user-text { color: #fff; }
.msg-time { font-size: 20rpx; color: var(--color-text-tertiary); margin-top: 4rpx; }
.quick-replies { display: flex; gap: 12rpx; padding: 12rpx 24rpx; overflow-x: auto; background: var(--color-surface); border-top: 1rpx solid var(--color-divider); }
.quick-btn { flex-shrink: 0; padding: 0 20rpx; height: 56rpx; border-radius: 999rpx; border: 1rpx solid var(--color-divider); display: flex; align-items: center; }
.quick-btn-text { font-size: 24rpx; color: var(--color-text-secondary); white-space: nowrap; }
.bottom-bar { display: flex; align-items: center; gap: 12rpx; padding: 12rpx 24rpx; padding-bottom: calc(12rpx + env(safe-area-inset-bottom, 0)); background: var(--color-surface); border-top: 1rpx solid var(--color-divider); }
.input { flex: 1; height: 72rpx; padding: 0 20rpx; background: var(--color-background); border-radius: 36rpx; font-size: 26rpx; }
.action-btn { padding: 0 24rpx; height: 72rpx; background: var(--color-primary); color: #fff; border-radius: 36rpx; display: flex; align-items: center; justify-content: center; }
.action-btn-text { font-size: 26rpx; color: #fff; }
</style>