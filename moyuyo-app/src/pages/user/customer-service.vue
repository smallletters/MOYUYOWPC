<template>
  <view class="cs">
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('csChat.title') }}</text>
      <view class="header-action" @click="goHistory">{{ $t('csChat.history') }}</view>
    </view>

    <scroll-view
      class="chat-area"
      scroll-y
      :scroll-into-view="scrollIntoView"
      :scroll-with-animation="true"
      :enhanced="true"
      :show-scrollbar="false"
    >
      <view class="msg-row msg-bot">
        <view class="bot-avatar">M</view>
        <view class="msg-content">
          <view class="msg-bubble msg-bot-bubble">
            <text class="msg-text">{{ $t('csChat.welcomeMsg') }}</text>
          </view>
        </view>
      </view>
      <view
        v-for="m in messages"
        :id="`msg-${m.id}`"
        :key="m.id"
        class="msg-row"
        :class="[
          m.senderType === 'USER' ? 'msg-user' : 'msg-bot',
          { 'msg-pending': m.pending, 'msg-failed': m.failed },
        ]"
      >
        <!-- 机器人消息:头像在左 -->
        <view v-if="m.senderType !== 'USER'" class="msg-avatar msg-avatar-bot">
          {{ (m.senderName || 'M')[0] }}
        </view>
        <!-- 消息内容(气泡 + 时间) -->
        <view class="msg-content" :class="{ 'msg-user-content': m.senderType === 'USER' }">
          <view
            class="msg-bubble"
            :class="[
              m.senderType === 'USER' ? 'msg-user-bubble' : 'msg-bot-bubble',
              {
                'msg-bubble-pending': m.pending,
                'msg-bubble-failed': m.failed,
                'msg-bubble-card': isCardMessage(m),
              },
            ]"
          >
            <!-- 商品/订单卡片：客服推荐商品或发送订单信息时渲染 -->
            <view v-if="isCardMessage(m)" class="msg-card" @tap="openCard(m)">
              <image
                v-if="cardImageOf(m)"
                class="msg-card-img"
                :src="cardImageOf(m)"
                mode="aspectFill"
              />
              <view class="msg-card-info">
                <text class="msg-card-title">{{ cardTitleOf(m) }}</text>
                <text class="msg-card-sub">{{ cardSubtitleOf(m) }}</text>
              </view>
              <text class="msg-card-action">
                {{ m.refType === 'PRODUCT' ? $t('csChat.viewProduct') : $t('csChat.viewOrder') }}
              </text>
            </view>
            <text v-else class="msg-text" :class="{ 'msg-user-text': m.senderType === 'USER' }">
              {{ m.content }}
            </text>
          </view>
          <text class="msg-time">
            <text v-if="m.pending">{{ $t('csChat.sending') }}</text>
            <text v-else-if="m.failed" class="msg-failed-text">{{ $t('csChat.sendFailed') }}</text>
            <text v-else>{{ formatTime(m.createTime) }}</text>
          </text>
        </view>
        <!-- 用户消息:头像在右 -->
        <view v-if="m.senderType === 'USER'" class="msg-avatar msg-avatar-user">
          {{ (m.senderName || $t('csChat.userFallbackChar'))[0] }}
        </view>
      </view>
    </scroll-view>

    <!-- 空闲超时提示条:2 分钟无活动时显示,引导用户重新发起 -->
    <view v-if="isIdleTimeout" class="idle-banner">
      <text class="idle-banner-icon">⏰</text>
      <text class="idle-banner-text">{{ $t('csChat.idleBanner') }}</text>
    </view>

    <view class="quick-replies">
      <view
        v-for="q in quickReplies"
        :key="q.text"
        class="quick-btn"
        :class="{ 'quick-btn-disabled': isIdleTimeout }"
        @click="sendText(q.text)"
      >
        <text class="quick-btn-text">{{ q.text }}</text>
      </view>
    </view>

    <!-- 商品上下文(从商品详情页跳转过来时携带) -->
    <view v-if="productName" class="product-context">
      <text class="product-context-label">
        {{ $t('csChat.productContextHint') }}{{ $t('csChat.productContextHintSuffix') }}
      </text>
      <text class="product-context-name">{{ productName }}</text>
      <view
        class="product-context-close"
        :aria-label="$t('csChat.closeAriaLabel')"
        @tap="clearProductContext"
      >
        <text class="luc luc-x" />
      </view>
    </view>

    <view class="bottom-bar">
      <template v-if="isIdleTimeout">
        <!-- 超时态:替换为"重新发起"按钮,禁用输入框 -->
        <view class="action-btn action-btn-resume" @click="resetSession">
          <text class="action-btn-text">{{ $t('csChat.reopen') }}</text>
        </view>
      </template>
      <template v-else>
        <input
          v-model="draft"
          class="input"
          :placeholder="$t('csChat.inputPlaceholder')"
          :adjust-position="true"
          @confirm="sendCurrent"
        >
        <view class="action-btn" @click="sendCurrent">
          <text class="action-btn-text">{{ $t('csChat.send') }}</text>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { csApi } from '@/api'
import { i18n } from '@/i18n'
import { usePageTitle } from '@/utils/i18nPageMixin'
import { getPageQuery } from '@/utils/pageQuery'
import { toAbsoluteImageUrl } from '@/utils/imageUrl'
import { useUserStore } from '@/store'
import {
  loadCachedMessages,
  saveCachedMessages,
  deleteCachedConversation,
  MESSAGE_CACHE_RETENTION_DAYS,
} from '@/utils/messageCache'
usePageTitle('pageTitle.userCustomerService')

const sessionId = ref(null)
// 从消息页带入的已有会话 id：存在时直接展示历史消息，不再新建会话
const presetSessionId = ref('')

const messages = ref([])
const draft = ref('')
const scrollIntoView = ref('')
// 兜底:某些场景 scroll-into-view 不触发,仍用 scrollTop
const scrollTop = ref(9999)
// 商品上下文(从商品详情页跳转过来时携带)
const productId = ref('')
const productName = ref('')
// 订单上下文(从订单详情页跳转过来时携带)
const orderNo = ref('')
// 周期性拉取的定时器 id,离开页面时清理
let pollTimer = null
// 已渲染的最新消息 id,避免重复触发 UI 更新
let lastRenderedId = null
// 周期性检查空闲超时的定时器(前端层面)
let idleTimer = null
// 空闲超时阈值:2 分钟(120 秒)无任何消息活动则提示用户
const IDLE_TIMEOUT_MS = 2 * 60 * 1000
// 是否已超时(控制底部输入栏与超时提示条)
const isIdleTimeout = ref(false)
// 上一次活跃时间戳(用户发消息 / 收到消息时更新)
let lastActiveAt = 0

// 根据是否带商品上下文动态切换快捷问题(文案从 i18n 字典取)
const quickReplies = computed(() => {
  const key = productName.value ? 'csChat.quickRepliesProduct' : 'csChat.quickRepliesGeneral'
  const list = i18n.t(key) || []
  return list.map((text) => ({ text }))
})

/**
 * 接收路由参数(从商品详情页跳转时携带商品 ID/名称)。
 *
 * 不能只读 `getCurrentPages().slice(-1)[0].options` —— APP 运行时（initPageVm）
 * 从不写 pageVm.options，APP 真机上恒读到 {}，结果是客服会话丢失商品上下文。
 * 这里优先用 uni-app 官方 onLoad 入参，拿不到时回落 getPageQuery()（多路径兜底）。
 *
 * @param {Object} [options] uni-app onLoad 传入的页面参数
 */
function parseQuery(options) {
  const hasOptions = !!options && typeof options === 'object' && Object.keys(options).length > 0
  const q = hasOptions ? options : getPageQuery()
  // decodeURIComponent 遇到非法百分号编码（如商品名含「100%棉」）会抛 URIError。
  // 这里必须降级成空值而不是让异常冒泡：parseQuery 在 bootstrap 的最前面执行，
  // 一旦抛出，booted 还没置位、ensureSession() 永不执行，且 onMounted 再调仍会抛，
  // 结果是客服页彻底不可用且无自愈。其余读参页面同理都包了 try/catch。
  try {
    productId.value = decodeURIComponent(q.productId || '')
  } catch (e) {
    console.warn('[cs] decode productId failed', e)
    productId.value = ''
  }
  try {
    productName.value = decodeURIComponent(q.productName || '')
  } catch (e) {
    console.warn('[cs] decode productName failed', e)
    productName.value = ''
  }
  try {
    orderNo.value = decodeURIComponent(q.orderNo || '')
  } catch (e) {
    console.warn('[cs] decode orderNo failed', e)
    orderNo.value = ''
  }
  // 会话 id 为后端雪花数字 id，无需 decode
  presetSessionId.value = q.sessionId ? String(q.sessionId) : ''
}

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    return `${hh}:${mm}`
  } catch {
    return ''
  }
}

/** 消息是否为商品/订单卡片 */
function isCardMessage(m) {
  return !!m && (m.refType === 'PRODUCT' || m.refType === 'ORDER') && !!m.refSnapshot
}

/** 解析后端下发的 JSON 卡片快照 */
function cardDataOf(m) {
  if (!m || !m.refSnapshot) return null
  if (typeof m.refSnapshot === 'object') return m.refSnapshot
  try {
    return JSON.parse(m.refSnapshot)
  } catch (e) {
    return null
  }
}

function cardTitleOf(m) {
  if (m.refTitle) return m.refTitle
  const data = cardDataOf(m)
  if (m.refType === 'PRODUCT') return data?.name || i18n.t('csChat.productFallback')
  return data?.orderNo
    ? i18n.t('csChat.orderCardTitleFmt', { orderNo: data.orderNo })
    : i18n.t('csChat.orderFallback')
}

function cardImageOf(m) {
  const data = cardDataOf(m)
  if (!data) return ''
  const path = m.refType === 'PRODUCT' ? data.image : data.items?.[0]?.image || ''
  return toAbsoluteImageUrl(path)
}

function cardCurrencySymbol(code) {
  const map = { USD: '$', CNY: '¥', EUR: '€', GBP: '£', JPY: '¥' }
  return map[String(code || 'USD').toUpperCase()] || code || '$'
}

function cardSubtitleOf(m) {
  const data = cardDataOf(m)
  if (!data) return ''
  if (m.refType === 'PRODUCT') {
    const price = parseFloat(data.price)
    return Number.isFinite(price) ? `$${price.toFixed(2)}` : ''
  }
  const amount =
    data.payAmount == null ? '-' : `${cardCurrencySymbol(data.currency)}${data.payAmount}`
  return i18n.t('csChat.orderCardSubFmt', { orderNo: data.orderNo || '-', amount })
}

/** 点击卡片跳转商品详情或订单详情 */
function openCard(m) {
  const data = cardDataOf(m)
  const id = data?.id || m.refId
  if (!id) return
  if (m.refType === 'PRODUCT') {
    uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
  } else {
    uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
  }
}

/** 当前登录用户 ID：本地缓存按用户隔离 */
function currentUserId() {
  return String(useUserStore().userId || '')
}

/** 把客服会话消息写入本地缓存（供下次秒开与弱网查看） */
function cacheSessionMessages(sid, records) {
  if (!sid) return
  saveCachedMessages({
    scope: 'cs',
    userId: currentUserId(),
    conversationId: String(sid),
    messages: Array.isArray(records) ? records : [],
    retentionDays: MESSAGE_CACHE_RETENTION_DAYS,
  })
}

async function ensureSession() {
  try {
    // 从消息页带已有会话 id 进入：直接加载历史消息，避免 createSession 新建空会话
    if (presetSessionId.value) {
      try {
        sessionId.value = presetSessionId.value
        // 本地优先：先渲染缓存，弱网/离线也能看到最近记录，云端返回后再覆盖
        const cached = await loadCachedMessages({
          scope: 'cs',
          userId: currentUserId(),
          conversationId: String(presetSessionId.value),
          retentionDays: MESSAGE_CACHE_RETENTION_DAYS,
        })
        if (cached.length) {
          messages.value = cached
          scrollToBottom()
        }
        messages.value = await fetchMessagesWithRetry(sessionId.value)
        scrollToBottom()
        return
      } catch (e) {
        // 会话不存在/无权访问时回退为创建新会话
        console.warn('[cs] resume session failed', e)
        sessionId.value = null
        presetSessionId.value = ''
      }
    }
    // 带商品/订单上下文时使用 PRODUCT 类目,便于后台客服识别来源
    const category = productName.value || orderNo.value ? 'PRODUCT' : 'GENERAL'
    // 把商品/订单上下文写入会话,后台客服会话页可直接展示商品图与订单
    const context = {}
    if (productId.value) context.productId = productId.value
    if (productName.value) context.productName = productName.value
    if (orderNo.value) context.orderNo = orderNo.value
    const sess = await csApi.createSession(category, context)
    sessionId.value = sess?.id || sess?.sessionId
    // 拉取会话内已有消息(轮询以兼容事务刚提交的瞬时不可见)
    messages.value = await fetchMessagesWithRetry(sessionId.value)
    // 若带了商品上下文且会话为空,自动发一条开场白消息
    if (productName.value && messages.value.length === 0) {
      const intro = i18n.t('csChat.productIntroFmt', { name: productName.value })
      await sendText(intro)
    }
    scrollToBottom()
  } catch (e) {
    console.warn('[cs] ensureSession failed', e)
  }
}

/** 关闭商品上下文(用户点击 X) */
function clearProductContext() {
  productId.value = ''
  productName.value = ''
}

function scrollToBottom() {
  if (!messages.value.length) return
  // 用最后一条消息 id 作为锚点(更稳:H5 端 scrollTop 在某些情况下不生效)
  const lastId = messages.value[messages.value.length - 1].id
  const target = `msg-${lastId}`
  // 关键技巧:先清空锚点再设置,确保 vue 检测到值变化,避免同样值不触发
  scrollIntoView.value = ''
  nextTick(() => {
    scrollIntoView.value = target
    // 双保险:同时设置 scrollTop,scrollTop 必须递增才能在 uni-app H5 端生效
    scrollTop.value = 0
    requestAnimationFrame(() => {
      scrollTop.value = 99999
    })
  })
}

async function sendText(text) {
  if (!text) return
  // 标记用户活跃,重置空闲超时
  touchActive()
  if (!sessionId.value) await ensureSession()
  const sid = sessionId.value
  if (!sid) return
  // 乐观更新:先在前端插入一条"待确认"用户消息,提升体感
  const tempId = `temp-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`
  const tempMsg = {
    id: tempId,
    senderType: 'USER',
    content: text,
    createTime: new Date().toISOString(),
    pending: true,
  }
  messages.value.push(tempMsg)
  draft.value = ''
  scrollToBottom()
  try {
    // 1. 调用后端发送消息(同步事务提交:autoReply 也会落库)
    await csApi.sendMessage(sid, text)
    // 2. 拉取真实消息列表,带轮询以兼容"前一次事务可见、后一次事务不可见"的瞬时窗口
    const realMsgs = await fetchMessagesWithRetry(sid)
    // 3. 直接用服务端真实数据覆盖前端乐观数据
    //    (服务端真实数据包含用户消息 + 自动回复;前端的临时项会被丢弃)
    messages.value = Array.isArray(realMsgs) ? realMsgs : []
    scrollToBottom()
  } catch (e) {
    console.warn('[cs] send failed', e)
    // 失败时把乐观消息标记为失败,保留在列表
    const idx = messages.value.findIndex((m) => m.id === tempId)
    if (idx >= 0) messages.value[idx].failed = true
  }
}

/**
 * 拉取消息列表(单次):业务逻辑上 sendUserMessage 事务提交后,
 * 立即调 listMessages 应可见。这里保留重试入口(目前单次即可),
 * 如果将来发现 race condition,可在此扩展轮询逻辑。
 */
async function fetchMessagesWithRetry(sid) {
  const list = await csApi.listMessages(sid)
  const records = Array.isArray(list) ? list : list?.records || []
  // 拉取成功后覆盖本地缓存，保证下次进入可秒开
  cacheSessionMessages(sid, records)
  return records
}

function sendCurrent() {
  const t = draft.value.trim()
  if (!t) return
  sendText(t)
}

function goBack() {
  uni.navigateBack()
}
function goHistory() {
  uni.navigateTo({ url: '/pages/community/chat-history' })
}

/**
 * 标记一次"用户活跃":
 * - 用户发送消息、收到服务端消息、点击快捷问题都视为活跃
 * - 重置 lastActiveAt,关闭超时提示
 */
function touchActive() {
  lastActiveAt = Date.now()
  if (isIdleTimeout.value) isIdleTimeout.value = false
}

/**
 * 启动空闲超时检测:每秒检查一次,若 2 分钟内无任何活动,
 * 标记 isIdleTimeout = true(显示超时条,禁用输入框)
 */
function startIdleCheck() {
  stopIdleCheck()
  lastActiveAt = Date.now()
  idleTimer = setInterval(() => {
    if (!sessionId.value) return
    if (isIdleTimeout.value) return
    if (Date.now() - lastActiveAt >= IDLE_TIMEOUT_MS) {
      isIdleTimeout.value = true
      // 插入一条本地 SYSTEM 提示(不持久化到后端)
      messages.value.push({
        id: `timeout-${Date.now()}`,
        senderType: 'SYSTEM',
        senderName: i18n.t('csChat.systemName'),
        content: i18n.t('csChat.timeoutContent'),
        createTime: new Date().toISOString(),
        isTimeout: true,
      })
      scrollToBottom()
    }
  }, 1000)
}

function stopIdleCheck() {
  if (idleTimer) {
    clearInterval(idleTimer)
    idleTimer = null
  }
}

/**
 * 重新发起会话:
 * - 关闭当前会话(若仍处于打开状态)
 * - 创建新会话,清空本地消息列表,重置超时状态
 */
async function resetSession() {
  try {
    if (sessionId.value) {
      const closedSid = String(sessionId.value)
      try {
        await csApi.closeSession(sessionId.value)
      } catch (e) {
        /* 忽略 */
      }
      // 旧会话已结束，清掉它的本地缓存，避免残留到新会话
      deleteCachedConversation({ scope: 'cs', userId: currentUserId(), conversationId: closedSid })
    }
    sessionId.value = null
    messages.value = []
    isIdleTimeout.value = false
    lastActiveAt = Date.now()
    lastRenderedId = null
    await ensureSession()
    lastRenderedId = messages.value.length ? messages.value[messages.value.length - 1].id : null
    touchActive()
    uni.showToast({ title: i18n.t('csChat.reopenSession'), icon: 'success' })
  } catch (e) {
    console.warn('[cs] resetSession failed', e)
    uni.showToast({ title: e?.message || i18n.t('csChat.reopenFailedFallback'), icon: 'none' })
  }
}

/**
 * 启动周期性轮询:
 * - 兜底方案,确保即使单次 sendText 后 listMessages 因 race 漏掉消息,
 *   也能在 3s 内自动补齐
 * - 只有当消息列表发生变化(最新消息 id 不同)时才更新 UI,避免重复渲染
 */
function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!sessionId.value) return
    try {
      const list = await csApi.listMessages(sessionId.value)
      const real = Array.isArray(list) ? list : list?.records || []
      if (!real.length) return
      // 用最后一条消息 id 做变化检测(雪花 id 递增)
      const tailId = real[real.length - 1].id
      if (tailId !== lastRenderedId) {
        messages.value = real
        lastRenderedId = tailId
        // 轮询发现新消息时同步本地缓存，保证下次进入可秒开
        cacheSessionMessages(sessionId.value, real)
        scrollToBottom()
        // 收到服务端新消息也算一次活跃,延长超时
        touchActive()
      }
    } catch (e) {
      // 静默失败,下次轮询继续
      console.warn('[cs] poll failed', e)
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// 从其他页面返回时立即拉取最新消息(覆盖因 race 漏掉的数据)
const onShowRefresh = async () => {
  if (!sessionId.value) return
  try {
    const list = await csApi.listMessages(sessionId.value)
    const real = Array.isArray(list) ? list : list?.records || []
    if (real.length) {
      const tailId = real[real.length - 1].id
      if (tailId !== lastRenderedId) {
        messages.value = real
        lastRenderedId = tailId
        // 从其他页面返回发现新消息时同步本地缓存
        cacheSessionMessages(sessionId.value, real)
        scrollToBottom()
        touchActive()
      }
    }
  } catch (e) {
    console.warn('[cs] onShow refresh failed', e)
  }
}

// 幂等启动：onLoad 先于 onMounted 触发，两者都调用，副作用只发生一次。
// 注意 parseQuery 必须在 ensureSession 之前执行：ensureSession 依赖 productName
// 决定会话类目（PRODUCT / GENERAL）与是否自动发送开场白。
let booted = false

function bootstrap(options) {
  parseQuery(options)
  if (booted) return
  booted = true
  ensureSession()
  // 等 session 建立后再开始轮询
  setTimeout(() => {
    if (sessionId.value) {
      lastRenderedId = messages.value.length ? messages.value[messages.value.length - 1].id : null
      startPolling()
      startIdleCheck()
    } else {
      // session 还没创建好,延迟再启动
      setTimeout(startPolling, 1500)
      setTimeout(startIdleCheck, 1500)
    }
  }, 600)
}

onLoad((options) => {
  bootstrap(options)
})

onMounted(() => {
  bootstrap()
})

onBeforeUnmount(() => {
  stopPolling()
  stopIdleCheck()
})

// uni-app 页面生命周期:每次回到页面(onShow)主动刷新最新数据
// 同时重置空闲超时(用户切回页面视为一次活跃)
onShow(() => {
  touchActive()
  startIdleCheck()
  onShowRefresh()
})
</script>

<style lang="scss" scoped>
.cs {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--color-background);
  /* 底部固定操作栏需要预留空间,否则快捷问题、商品上下文等会被遮 */
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom, 0));
  box-sizing: border-box;
}
/* 顶部导航栏
   APP 端因为 navigationStyle:custom 自渲染 header,
   需为系统状态栏(刘海/灵动岛/胶囊)预留顶部空间,
   加上 var(--status-bar-height) 与 env(safe-area-inset-top) 兜底 */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 取二者较大值（而非相加）：iOS 上 env 与 status-bar-height 同为状态栏高度，相加会翻倍 */
  height: calc(88rpx + var(--status-bar-height, 0px));
  height: calc(88rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  /* 把内容下移避开状态栏,三个 flex 子项会一起下移 */
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  /* 左右保持原有 24rpx 内边距 */
  padding-right: 24rpx;
  padding-left: 24rpx;
  padding-bottom: 0;
  box-sizing: border-box;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.back-btn {
  width: 60rpx;
}
.back-icon {
  font-size: 44rpx;
  color: var(--color-primary);
}
.header-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
.header-action {
  font-size: 26rpx;
  color: var(--color-primary);
}
.chat-area {
  flex: 1;
  /* 内层 uni-scroll-view-content 不继承 padding,这里给 chat-area 自己 + 内层都加 padding */
  padding: 16rpx 10rpx;
  box-sizing: border-box;
  /* 宽度跟随屏幕:rpx 会自动按 750rpx 设计稿缩放 */
  width: 100%;
  min-height: 0;
}
.msg-row {
  display: flex;
  gap: 12rpx;
  margin-bottom: 16rpx;
  min-width: 0;
  max-width: 100%;
  align-items: flex-start;
  box-sizing: border-box;
  padding-right: 0;
  padding-left: 0;
}
.msg-bot {
  align-self: flex-start;
}
.msg-user {
  flex-direction: row;
  align-items: flex-start;
  justify-content: flex-end;
  /* 关键:用户消息预留右侧头像位置(64rpx + 12rpx gap),确保气泡不顶到屏右 */
  padding-right: 76rpx;
  position: relative;
  min-height: 80rpx;
}
/* 用户消息右侧头像:绝对定位钉在屏幕右 5px,布局完全不受 flex 约束 */
.msg-user .msg-avatar-user {
  position: absolute;
  right: 10rpx; /* 距屏右 5px */
  top: 0;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #b38a5a;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: #fff;
  box-sizing: border-box;
  flex-shrink: 0;
}
/* 用户消息:头像紧跟 msg-content 之后,内容 + 头像作为一个整体右贴边 */
.msg-user .msg-content {
  margin-left: 0;
}
/* 头像统一样式 */
.msg-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: #fff;
  flex-shrink: 0;
  box-sizing: border-box;
  overflow: hidden;
}
.msg-avatar-bot {
  background: var(--color-primary);
}
/* 内容容器:fit-content 让气泡宽度 = 内容宽度,不撑满父容器 */
.msg-content {
  display: flex;
  flex-direction: column;
  width: fit-content;
  /* 用固定 rpx 限制最大宽度,750rpx 设计稿下
     屏宽 750 - chat-area padding 20rpx - bot 头像 64rpx - gap 12rpx - 安全 16rpx = 638rpx
     因为右头像已绝对定位脱离布局,这里只需扣 bot 头像占的位置 */
  max-width: 638rpx;
  min-width: 0;
  flex-shrink: 1;
  overflow: hidden;
  box-sizing: border-box;
}
.msg-bot .msg-content {
  align-items: flex-start;
}
.msg-user-content {
  align-items: flex-end;
}
.msg-bubble {
  padding: 16rpx 20rpx;
  border-radius: 20rpx;
  font-size: 26rpx;
  line-height: 1.5;
  max-width: 100%;
  min-width: 0;
  word-break: break-word;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  box-sizing: border-box;
}
.msg-bubble .msg-text {
  display: inline-block;
  max-width: 100%;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}
.msg-bot-bubble {
  background: var(--color-surface);
  border-top-left-radius: 4rpx;
}
.msg-user-bubble {
  background: var(--color-primary);
  border-top-right-radius: 4rpx;
}
.msg-user-text {
  color: #fff;
}

/* 商品/订单卡片消息：外层气泡透明，由卡片自身提供背景 */
.msg-bubble-card {
  padding: 0;
  background: transparent;
  border-radius: 0;
}
.msg-card {
  display: flex;
  align-items: center;
  width: 500rpx;
  max-width: 100%;
  padding: 16rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: 16rpx;
  box-sizing: border-box;
}
.msg-card-img {
  width: 96rpx;
  height: 96rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  background: var(--color-background);
}
.msg-card-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  margin-left: 16rpx;
}
.msg-card-title {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.4;
  word-break: break-word;
}
.msg-card-sub {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  margin-top: 6rpx;
}
.msg-card-action {
  font-size: 22rpx;
  color: var(--color-primary);
  flex-shrink: 0;
  margin-left: 12rpx;
}

/* 发送中状态:半透明 */
.msg-bubble-pending {
  opacity: 0.6;
}
/* 发送失败状态:红色描边 */
.msg-bubble-failed {
  border: 1rpx solid var(--color-danger, #c96e5f);
  background: #fef0ed;
}
.msg-failed-text {
  color: var(--color-danger, #c96e5f);
}
.msg-time {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}
.quick-replies {
  display: flex;
  gap: 12rpx;
  padding: 12rpx 24rpx;
  overflow-x: auto;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}
.quick-btn {
  flex-shrink: 0;
  padding: 0 20rpx;
  height: 56rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--color-divider);
  display: flex;
  align-items: center;
}
.quick-btn-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

/* 商品上下文条 */
.product-context {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
  font-size: 24rpx;
}
.product-context-label {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}
.product-context-name {
  flex: 1;
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-context-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: var(--color-divider);
  color: var(--color-text-secondary);
  font-size: 18rpx;
  flex-shrink: 0;
}
.bottom-bar {
  /* 固定在屏幕底部,不被聊天区滚动条带走 */
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 24rpx;
  /* iPhone 安全区 + 自身高度预留 */
  padding-bottom: calc(12rpx + env(safe-area-inset-bottom, 0));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
  box-shadow: var(--shadow-top);
}
.input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  background: var(--color-background);
  border-radius: 36rpx;
  font-size: 26rpx;
}
.action-btn {
  padding: 0 24rpx;
  height: 72rpx;
  background: var(--color-primary);
  color: #fff;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.action-btn-text {
  font-size: 26rpx;
  color: #fff;
}

/* 聊天区底部留白由 .cs 容器统一处理:padding-bottom: calc(120rpx + env(safe-area-inset-bottom))
   这里不再重复定义 .chat-area,避免覆盖上方用户头像定位关键样式 */

/* "重新发起"按钮:占满整行,更醒目 */
.action-btn-resume {
  flex: 1;
  background: var(--color-primary);
  color: var(--color-text);
  font-weight: 600;
}

/* 空闲超时提示条 */
.idle-banner {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  background: #fff7e6;
  border-top: 1rpx solid #ffd591;
  border-bottom: 1rpx solid #ffd591;
  color: #d48806;
}
.idle-banner-icon {
  font-size: 32rpx;
  line-height: 1;
}
.idle-banner-text {
  font-size: 26rpx;
  font-weight: 500;
}

/* 超时态快捷问题禁用 */
.quick-btn-disabled {
  opacity: 0.4;
  pointer-events: none;
}
</style>
