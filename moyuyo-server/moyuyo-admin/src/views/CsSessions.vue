<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>客服会话</h2>
      <div class="header-actions">
        <el-button size="small" @click="loadAll" :loading="refreshing">刷新</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">总会话数</span>
            <span class="stat-value">{{ stats.totalSessions || 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">进行中</span>
            <span class="stat-value">{{ stats.activeSessions || 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">今日新增</span>
            <span class="stat-value">{{ stats.todayNew || 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">平均响应(秒)</span>
            <span class="stat-value">{{ stats.avgResponseTime || 0 }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <el-form :model="searchForm" inline>
        <el-form-item label="会话ID">
          <el-input v-model="searchForm.sessionId" placeholder="搜索会话ID" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model="searchForm.userId" placeholder="搜索用户ID" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="选择状态" clearable style="width:130px">
            <el-option label="等待中" value="WAITING" />
            <el-option label="进行中" value="PROCESSING" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 会话列表 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="sessionId" label="会话号" width="140" show-overflow-tooltip />
        <el-table-column prop="userName" label="用户" min-width="120" show-overflow-tooltip />
        <el-table-column label="咨询内容" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.consultTitle" class="consult-cell">
              <el-tag :type="row.consultType === 'ORDER' ? 'warning' : 'success'" size="small">
                {{ row.consultType === 'ORDER' ? '订单' : '商品' }}
              </el-tag>
              <span class="consult-text">{{ row.consultType === 'ORDER' ? row.orderNo : row.productName }}</span>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="agentName" label="客服" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="messageCount" label="消息数" width="80" />
        <el-table-column label="最后消息" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastMessage || '-' }}</template>
        </el-table-column>
        <el-table-column prop="duration" label="持续时长" width="120" />
        <el-table-column prop="createdAt" label="开始时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openChat(row)">聊天</el-button>
            <el-button v-if="row.status !== 'CLOSED'" type="danger" link size="small" @click="handleClose(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 分页：支持切换每页条数、翻页与跳转到指定页 -->
      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 聊天抽屉：左右两栏 -->
    <el-drawer
      v-model="chatVisible"
      :title="chatTitle"
      size="1000px"
      direction="rtl"
      :close-on-click-modal="false"
      @closed="onChatClosed"
    >
      <div class="chat-layout" v-if="activeSession">
        <!-- 左：元信息 -->
        <aside class="chat-side">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="会话ID">{{ activeSession.id }}</el-descriptions-item>
            <el-descriptions-item label="会话号">{{ activeSession.sessionId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="用户">{{ activeSession.userName || ('用户' + (activeSession.userId || '')) }}</el-descriptions-item>
            <el-descriptions-item label="当前客服">
              {{ activeSession.csStaffId ? ('客服' + activeSession.csStaffId) : '未指派' }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(activeSession.status)" size="small">{{ getStatusText(activeSession.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ activeSession.createTime || activeSession.createdAt }}</el-descriptions-item>
            <el-descriptions-item label="消息数">{{ activeSession.messageCount || messages.length }}</el-descriptions-item>
          </el-descriptions>

          <!-- 用户进入客服时携带的商品 / 订单上下文 -->
          <el-divider content-position="left">用户咨询</el-divider>
          <div
            v-if="activeSession.contextProduct"
            class="context-card"
            @click="openProductRef(activeSession.contextProduct.id)"
          >
            <img
              v-if="activeSession.contextProduct.image"
              class="context-img"
              :src="resolveImageUrl(activeSession.contextProduct.image)"
              alt=""
              @error="onImgError"
            />
            <div class="context-info">
              <div class="context-title">{{ activeSession.contextProduct.name || ('商品#' + activeSession.contextProduct.id) }}</div>
              <div class="context-sub">
                <span v-if="activeSession.contextProduct.price != null" class="money">¥{{ activeSession.contextProduct.price }}</span>
                <el-tag
                  v-if="activeSession.contextProduct.onSale === false"
                  type="info"
                  size="small"
                  style="margin-left:6px"
                >已下架</el-tag>
              </div>
            </div>
          </div>
          <div
            v-if="activeSession.contextOrder"
            class="context-card"
            @click="openOrderRef(activeSession.contextOrder.id)"
          >
            <img
              v-if="orderCoverImage(activeSession.contextOrder)"
              class="context-img"
              :src="resolveImageUrl(orderCoverImage(activeSession.contextOrder))"
              alt=""
              @error="onImgError"
            />
            <div class="context-info">
              <div class="context-title">订单 {{ activeSession.contextOrder.orderNo }}</div>
              <div class="context-sub">
                <el-tag size="small" type="warning">{{ orderStatusText(activeSession.contextOrder.status) }}</el-tag>
                <span class="money" style="margin-left:6px">{{ formatAmount(activeSession.contextOrder.payAmount, activeSession.contextOrder.currency) }}</span>
              </div>
            </div>
          </div>
          <p v-if="!activeSession.contextProduct && !activeSession.contextOrder" class="side-tip">
            用户未携带商品或订单信息
          </p>

          <!-- 用户最近订单：客服可直接发送订单卡片 -->
          <el-divider content-position="left">用户订单</el-divider>
          <div v-loading="ordersLoading" class="order-list">
            <div v-for="o in userOrders" :key="o.id" class="order-item">
              <div class="order-head">
                <span class="order-no">{{ o.orderNo }}</span>
                <el-tag size="small" type="info">{{ orderStatusText(o.status) }}</el-tag>
              </div>
              <div class="order-body">
                <img
                  v-if="orderCoverImage(o)"
                  class="order-img"
                  :src="resolveImageUrl(orderCoverImage(o))"
                  alt=""
                  @error="onImgError"
                />
                <div class="order-info">
                  <div class="order-name">{{ orderMainTitle(o) }}</div>
                  <div class="order-meta">{{ (o.items || []).length }} 件 · {{ formatAmount(o.payAmount, o.currency) }}</div>
                </div>
                <el-button
                  type="primary"
                  link
                  size="small"
                  :loading="sendingCard"
                  @click="sendOrderCard(o)"
                >发送</el-button>
              </div>
            </div>
            <p v-if="!ordersLoading && userOrders.length === 0" class="side-tip">该用户暂无订单</p>
          </div>

          <el-divider />

          <!-- 转接客服：列出当前可转接的客服（取当前用户外其它管理员）-->
          <div class="side-actions">
            <el-button size="small" type="warning" plain @click="handleTransfer">转接客服</el-button>
            <el-button v-if="activeSession.status !== 'CLOSED'" size="small" type="danger" plain @click="handleCloseActive">关闭会话</el-button>
          </div>

          <p class="side-tip">系统会每 5 秒自动拉取新消息，无需刷新页面。</p>
        </aside>

        <!-- 右：消息列表 + 输入框 -->
        <section class="chat-main">
          <div class="chat-scroll" ref="scrollEl">
            <div v-if="messages.length === 0" class="chat-empty">
              暂无消息记录
            </div>
            <div
              v-for="(m, idx) in messages"
              :key="m.id || idx"
              class="bubble-row"
              :class="bubbleClassOf(m)"
            >
              <div class="bubble-meta">
                <span class="bubble-name">{{ m.senderName || senderTextOf(m) }}</span>
                <span class="bubble-time">{{ formatTime(m.createTime) }}</span>
              </div>
              <div class="bubble" :class="{ 'bubble--card': isCardMessage(m) }">
                <div v-if="isCardMessage(m)" class="chat-card" @click="openCardRef(m)">
                  <img
                    v-if="cardPrimaryImage(m)"
                    class="chat-card-img"
                    :src="resolveImageUrl(cardPrimaryImage(m))"
                    alt=""
                    @error="onImgError"
                  />
                  <div class="chat-card-info">
                    <div class="chat-card-title">{{ cardTitleOf(m) }}</div>
                    <div v-if="m.refType === 'PRODUCT'" class="chat-card-sub money">
                      ¥{{ cardDataOf(m)?.price ?? '-' }}
                    </div>
                    <div v-else class="chat-card-sub">
                      {{ formatAmount(cardDataOf(m)?.payAmount, cardDataOf(m)?.currency) }}
                      · {{ (cardDataOf(m)?.items || []).length }} 件
                    </div>
                  </div>
                  <span class="chat-card-link">查看</span>
                </div>
                <template v-else>{{ m.content }}</template>
              </div>
            </div>
          </div>
          <div class="chat-input">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="3"
              :disabled="activeSession.status === 'CLOSED' || sending"
              placeholder="输入回复内容，回车发送，Shift+Enter 换行"
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="chat-input-actions">
              <el-tag v-if="activeSession.status === 'CLOSED'" type="info" size="small">会话已关闭</el-tag>
              <el-tag v-if="unreadHint > 0" type="warning" size="small">{{ unreadHint }} 条新消息</el-tag>
              <el-button
                size="small"
                type="success"
                plain
                :disabled="activeSession.status === 'CLOSED'"
                @click="openProductPicker"
              >推荐商品</el-button>
              <el-button
                type="primary"
                :loading="sending"
                :disabled="!draft.trim() || activeSession.status === 'CLOSED'"
                @click="handleSend"
              >
                发送
              </el-button>
            </div>
          </div>
        </section>
      </div>
    </el-drawer>

    <!-- 推荐商品：搜索商品后点击卡片直接发送给用户 -->
    <el-dialog v-model="productPickerVisible" title="推荐商品" width="780px" append-to-body>
      <div class="picker-search">
        <el-input
          v-model="productKeyword"
          placeholder="输入商品名称或 SPU 编码搜索"
          clearable
          @keyup.enter="searchRecommendProducts"
          @clear="searchRecommendProducts"
        />
        <el-button type="primary" :loading="productLoading" @click="searchRecommendProducts">搜索</el-button>
      </div>
      <div v-loading="productLoading" class="product-grid">
        <div
          v-for="p in productOptions"
          :key="p.id"
          class="product-card"
          :class="{ 'product-card--off': p.onSale === false || p.onSale === 0 }"
          @click="sendProductCard(p)"
        >
          <img
            v-if="p.mainImage"
            class="product-img"
            :src="resolveImageUrl(p.mainImage)"
            alt=""
            @error="onImgError"
          />
          <div class="product-info">
            <div class="product-name">{{ p.name }}</div>
            <div class="product-meta">
              <span class="money">¥{{ p.price }}</span>
              <el-tag v-if="p.onSale === false || p.onSale === 0" type="info" size="small">已下架</el-tag>
            </div>
          </div>
        </div>
        <p v-if="!productLoading && productOptions.length === 0" class="picker-empty">
          未找到商品，换个关键字试试
        </p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCsSessionList, getCsSessionDetail, getCsSessionStats,
  getCsSessionMessages, pollCsSessionMessages, sendCsSessionMessage,
  markCsSessionRead, closeCsSession, transferCsSession, getRbacUsers,
  getCsSessionUserOrders, searchProductsLite
} from '../api/admin'
import { toArray } from '../utils/safeArray'

const tableData = ref([])
const stats = ref({})
const refreshing = ref(false)
const searchForm = reactive({ sessionId: '', userId: '', status: '' })
// 分页状态：页码 / 每页条数 / 总条数
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const { getStatusText, getStatusType } = (function () {
  const map = { WAITING: '等待中', PROCESSING: '进行中', CLOSED: '已关闭' }
  const types = { WAITING: 'warning', PROCESSING: 'success', CLOSED: 'info' }
  return {
    getStatusText: s => map[s] || (s || '-'),
    getStatusType: s => types[s] || 'info'
  }
})()

async function loadData() {
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (searchForm.sessionId) params.sessionId = searchForm.sessionId
    if (searchForm.userId) params.userId = searchForm.userId
    if (searchForm.status) params.status = searchForm.status
    const res = await getCsSessionList(params)
    tableData.value = toArray(res, 'list')
    // 后端返回 total 为总记录数，用于分页组件计算页数
    total.value = Number(res?.total || 0)
  } catch (e) {
    ElMessage.error('获取会话列表失败')
  }
}

/** 条件搜索：重新从第一页开始查询 */
function handleSearch() {
  currentPage.value = 1
  loadData()
}

/** 切换每页条数：重置到第一页 */
function handleSizeChange() {
  currentPage.value = 1
  loadData()
}

async function loadStats() {
  try {
    const res = await getCsSessionStats()
    stats.value = res || {}
  } catch (e) {
    // 静默失败
  }
}

function resetSearch() {
  searchForm.sessionId = ''
  searchForm.userId = ''
  searchForm.status = ''
  currentPage.value = 1
  loadData()
}

async function loadAll() {
  refreshing.value = true
  try { await Promise.all([loadData(), loadStats()]) }
  finally { refreshing.value = false }
}

// ============ 在线聊天 ============
const chatVisible = ref(false)
const activeSession = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scrollEl = ref(null)
// 轮询：上次拉到的最新 createTime（半开区间）
const lastSeenAt = ref(null)
let pollTimer = null
const unreadHint = ref(0)
// 用户最近订单 + 推荐商品弹窗
const userOrders = ref([])
const ordersLoading = ref(false)
const sendingCard = ref(false)
const productPickerVisible = ref(false)
const productKeyword = ref('')
const productOptions = ref([])
const productLoading = ref(false)
const router = useRouter()

const chatTitle = computed(() => {
  if (!activeSession.value) return '会话'
  const who = activeSession.value.userName || ('用户' + activeSession.value.userId)
  return `在线聊天 · ${who}`
})

function senderTextOf(m) {
  if (m.senderType === 'SYSTEM') return '系统'
  if (m.senderType === 'AGENT') return '客服'
  return '用户'
}

function bubbleClassOf(m) {
  if (m.senderType === 'AGENT') return 'bubble-row--agent'
  if (m.senderType === 'SYSTEM') return 'bubble-row--system'
  return 'bubble-row--user'
}

function formatTime(dt) {
  if (!dt) return ''
  try {
    const d = new Date(dt)
    if (isNaN(d.getTime())) return String(dt)
    const pad = n => String(n).padStart(2, '0')
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch (e) {
    return String(dt)
  }
}

/**
 * 解析图片地址：
 * - http(s):// 直接使用
 * - /uploads/... 是后端静态资源，生产环境与后台同源，直接拼当前 origin
 * - 本地 Vite dev（5173）不代理 /uploads/，需要指向后端 8080
 */
function resolveImageUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  if (path.startsWith('//')) return window.location.protocol + path
  const normalized = path.startsWith('/') ? path : `/${path}`
  if (window.location.origin.includes(':5173') || import.meta.env.DEV) {
    const apiBase = (import.meta.env.VITE_API_BASE_URL && import.meta.env.VITE_API_BASE_URL.startsWith('http'))
      ? import.meta.env.VITE_API_BASE_URL
      : 'http://localhost:8080'
    return `${apiBase.replace(/\/$/, '')}${normalized}`
  }
  return `${window.location.origin}${normalized}`
}

function onImgError(e) {
  if (e?.target) e.target.style.display = 'none'
}

function currencySymbolOf(code) {
  const map = { USD: '$', CNY: '¥', EUR: '€', GBP: '£', JPY: '¥' }
  return map[String(code || 'USD').toUpperCase()] || (code || '$')
}

/** 金额格式化：订单快照带 currency，商品快照默认 ¥ */
function formatAmount(value, currency) {
  if (value === null || value === undefined || value === '') return '-'
  const n = Number(value)
  return `${currencySymbolOf(currency)}${Number.isFinite(n) ? n.toFixed(2) : value}`
}

const ORDER_STATUS_TEXT = {
  PENDING_PAY: '待支付', PAID: '已支付', PENDING_SHIP: '待发货', SHIPPED: '已发货',
  RECEIVED: '已收货', CANCELLED: '已取消', REFUNDING: '退款中', REFUNDED: '已退款',
  COMPLETED: '已完成', EXCHANGING: '换货中', EXCHANGED: '已换货', HOLD: '已拦截'
}

function orderStatusText(status) {
  return ORDER_STATUS_TEXT[status] || status || '-'
}

function orderCoverImage(order) {
  return order?.items?.[0]?.image || ''
}

function orderMainTitle(order) {
  const first = order?.items?.[0]
  if (!first) return '订单商品'
  const name = first.name || '订单商品'
  return order.items.length > 1 ? `${name} 等 ${order.items.length} 件` : name
}

/** 消息是否为商品/订单卡片 */
function isCardMessage(m) {
  return !!m && (m.refType === 'PRODUCT' || m.refType === 'ORDER') && !!m.refSnapshot
}

/** 解析卡片 JSON 快照，解析失败时返回 null */
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
  if (m.refType === 'PRODUCT') return data?.name || '商品'
  return data?.orderNo ? `订单 ${data.orderNo}` : '订单'
}

function cardPrimaryImage(m) {
  const data = cardDataOf(m)
  if (!data) return ''
  if (m.refType === 'PRODUCT') return data.image || ''
  return data.items?.[0]?.image || ''
}

function openProductRef(id) {
  if (!id) return
  window.open(router.resolve(`/products/edit/${id}`).href, '_blank')
}

function openOrderRef(id) {
  if (!id) return
  window.open(router.resolve(`/orders/${id}`).href, '_blank')
}

function openCardRef(m) {
  const data = cardDataOf(m)
  if (m.refType === 'PRODUCT') openProductRef(data?.id || m.refId)
  else openOrderRef(data?.id || m.refId)
}

async function loadUserOrders(sessionId) {
  if (!sessionId) return
  ordersLoading.value = true
  try {
    const list = await getCsSessionUserOrders(sessionId, 10)
    userOrders.value = toArray(list)
  } catch (e) {
    userOrders.value = []
  } finally {
    ordersLoading.value = false
  }
}

/** 发送商品/订单卡片消息（快照由后端生成，前端只传类型与 ID） */
async function sendCardMessage(contentType, refId, successText) {
  if (!activeSession.value || sendingCard.value) return
  if (activeSession.value.status === 'CLOSED') {
    ElMessage.warning('会话已关闭，无法发送')
    return
  }
  sendingCard.value = true
  try {
    const resp = await sendCsSessionMessage(activeSession.value.id, {
      content: '',
      contentType,
      refId,
      senderId: 0,
      senderName: '客服'
    })
    if (resp && (resp.id || resp.content)) {
      messages.value.push(resp)
      lastSeenAt.value = resp.createTime || lastSeenAt.value
      activeSession.value.messageCount = messages.value.length
      scrollToBottom()
    }
    ElMessage.success(successText)
  } catch (e) {
    ElMessage.error((e && e.message) || '发送失败')
  } finally {
    sendingCard.value = false
  }
}

async function sendProductCard(product) {
  if (product.onSale === false || product.onSale === 0) {
    ElMessage.warning('该商品已下架，无法推荐')
    return
  }
  await sendCardMessage('PRODUCT', product.id, '商品已推荐给用户')
  productPickerVisible.value = false
}

async function sendOrderCard(order) {
  await sendCardMessage('ORDER', order.id, '订单卡片已发送')
}

function openProductPicker() {
  if (!activeSession.value) return
  productPickerVisible.value = true
  if (productOptions.value.length === 0) searchRecommendProducts()
}

async function searchRecommendProducts() {
  productLoading.value = true
  try {
    const rows = await searchProductsLite({ keyword: productKeyword.value.trim(), size: 30 })
    productOptions.value = toArray(rows)
  } catch (e) {
    productOptions.value = []
  } finally {
    productLoading.value = false
  }
}

async function openChat(row) {
  // 先确保详情最新（包含 messageCount 真实值）
  let detail = row
  try {
    const fresh = await getCsSessionDetail(row.id)
    if (fresh && (fresh.id || fresh.sessionId)) detail = { ...row, ...fresh }
  } catch (e) {
    // fallback 用列表行
  }
  activeSession.value = detail
  chatVisible.value = true
  messages.value = []
  userOrders.value = []
  lastSeenAt.value = null
  unreadHint.value = 0
  await loadAllMessages()
  await markCsSessionReadSafe()
  await loadUserOrders(detail.id)
  startPoll()
}

async function loadAllMessages() {
  if (!activeSession.value) return
  try {
    const list = await getCsSessionMessages(activeSession.value.id)
    messages.value = toArray(list)
    if (messages.value.length > 0) {
      const last = messages.value[messages.value.length - 1]
      // 始终把 ISO 时间戳转成 UTC ISO 字符串再传给后端（避免 Spring @DateTimeFormat LOCAL_DATE_TIME 解析时区错位）
      lastSeenAt.value = new Date(last.createTime).toISOString()
    }
    scrollToBottom()
  } catch (e) {
    ElMessage.error('加载消息失败')
  }
}

async function markCsSessionReadSafe() {
  if (!activeSession.value) return
  try { await markCsSessionRead(activeSession.value.id) } catch (e) { /* ignore */ }
}

function startPoll() {
  stopPoll()
  // 5s 增量轮询
  pollTimer = setInterval(async () => {
    if (!chatVisible.value || !activeSession.value) return
    try {
      const list = await pollCsSessionMessages(activeSession.value.id, lastSeenAt.value)
      const newMsgs = toArray(list)
      if (newMsgs.length > 0) {
        // 第一次追加：避免重复（lastSeenAt 用半开区间，理论上不会重复）
        messages.value = messages.value.concat(newMsgs)
        lastSeenAt.value = newMsgs[newMsgs.length - 1].createTime
        // 仅当抽屉隐藏/不可见时不滚动（此处抽屉打开，新消息追加到底部）
        scrollToBottom()
        await markCsSessionReadSafe()
        // 同步刷新会话列表的 messageCount 数字
        syncSessionCountFromMessages()
      }
    } catch (e) {
      // 静默：轮询失败不影响主流程
    }
  }, 5000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function onChatClosed() {
  stopPoll()
  activeSession.value = null
  messages.value = []
  userOrders.value = []
  draft.value = ''
  productPickerVisible.value = false
  loadData() // 关闭抽屉后刷新列表的 messageCount/lastMessage
}

async function handleSend() {
  const text = (draft.value || '').trim()
  if (!text || !activeSession.value) return
  if (activeSession.value.status === 'CLOSED') {
    ElMessage.warning('会话已关闭，无法发送')
    return
  }
  sending.value = true
  try {
    const resp = await sendCsSessionMessage(activeSession.value.id, {
      content: text,
      contentType: 'TEXT',
      senderId: 0,                 // 占位：实际 operatorId 在用户上下文拿不到，从前端 /me 缓存补
      senderName: '客服'
    })
    // 后端返回的是 CsMessageEntity，追加到本地
    if (resp && (resp.id || resp.content)) {
      messages.value.push(resp)
      lastSeenAt.value = resp.createTime || lastSeenAt.value
      draft.value = ''
      scrollToBottom()
    }
    // 同步会话的 messageCount
    if (activeSession.value) {
      activeSession.value.messageCount = messages.value.length
    }
  } catch (e) {
    ElMessage.error((e && e.message) || '发送失败')
  } finally {
    sending.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = scrollEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function syncSessionCountFromMessages() {
  if (!activeSession.value) return
  activeSession.value.messageCount = messages.value.length
}

// ============ 会话操作：关闭 / 转接 ============
async function handleClose(row) {
  try {
    await ElMessageBox.confirm(`确认关闭会话 #${row.id}?`, '关闭会话', {
      type: 'warning', confirmButtonText: '关闭', cancelButtonText: '取消'
    })
  } catch { return }
  try {
    await closeCsSession(row.id)
    ElMessage.success('会话已关闭')
    loadData()
  } catch (e) {
    ElMessage.error((e && e.message) || '关闭失败')
  }
}

async function handleCloseActive() {
  if (!activeSession.value) return
  await handleClose(activeSession.value)
  // 重新加载当前会话详情
  try {
    const fresh = await getCsSessionDetail(activeSession.value.id)
    if (fresh && fresh.id) {
      activeSession.value = { ...activeSession.value, ...fresh }
    }
  } catch (e) {}
  stopPoll()
}

// 管理员列表（用于转接下拉）
const agentOptions = ref([]) // [{id, name, label}]

async function loadAgentOptions() {
  // 复用 RBAC 用户列表（含 role），过滤掉 DISABLED 与无 role 的账号
  try {
    const r = await getRbacUsers()
    const list = (r && Array.isArray(r) ? r : (r && r.data) || []).filter(u =>
      u.status === 'ACTIVE' && u.role &&
      ['SUPER_ADMIN', 'OPERATOR', 'CUSTOMER_SVC', 'FINANCE'].includes(u.role)
    )
    agentOptions.value = list.map(u => ({
      id: u.id,
      name: u.name || u.username,
      label: `${u.name || u.username}（${u.role} · ID:${u.id}）`,
      role: u.role
    }))
  } catch (e) {
    agentOptions.value = []
  }
}

async function handleTransfer() {
  if (!activeSession.value) return
  if (agentOptions.value.length === 0) await loadAgentOptions()

  // 过滤掉当前客服（避免重复）
  const currentOpId = activeSession.value.csStaffId
  const candidates = agentOptions.value.filter(a => String(a.id) !== String(currentOpId))
  if (candidates.length === 0) {
    ElMessage.warning('没有可选的客服账号')
    return
  }

  try {
    const { value } = await ElMessageBox({
      title: '转接客服',
      message: '选择要转接给的客服',
      showInput: false,
      // Element Plus prompt 输入框不太适合下拉选择；改为自定义 prompt 弹窗
      dangerouslyUseHTMLString: false,
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      // 这里不能用 ElMessageBox 的下拉，简化为 prompt 输入 ID（保留兜底）
    }).catch(() => null)
  } catch (e) { /* ignore */ }

  // 简化：直接用 prompt 输入 ID（保留兜底），但优先提示列表
  ElMessageBox.prompt({
    title: '转接客服',
    message: `选择目标客服（可选 ID：${candidates.map(c => `${c.id}(${c.name})`).join(', ')}）`,
    inputPlaceholder: '输入客服 ID',
    inputPattern: /^[0-9]+$/,
    inputErrorMessage: '请输入数字 ID',
    confirmButtonText: '确认转接',
    cancelButtonText: '取消'
  }).then(({ value }) => {
    const newOpId = Number(value)
    const target = candidates.find(c => c.id === newOpId)
    if (!target) {
      ElMessage.warning(`客服 ID ${newOpId} 不在可选列表中`)
      return
    }
    transferCsSession(activeSession.value.id, {
      operatorId: newOpId,
      operatorName: target.name
    }).then(() => {
      ElMessage.success(`已转接给客服 ${target.name}`)
      loadAllMessages()
    }).catch((err) => {
      ElMessage.error((err && err.message) || '转接失败')
    })
  }).catch(() => { /* 用户取消 */ })
}

onMounted(() => { loadData(); loadStats() })
onBeforeUnmount(() => { stopPoll() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
.stat-item { display: flex; flex-direction: column; align-items: center; }
.stat-label { font-size: 13px; color: var(--text-400); margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--text-800); }

/* 列表分页 */
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 16px; }

/* 抽屉内的聊天布局 */
.chat-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  height: calc(100vh - 140px);
  min-height: 480px;
}
.chat-side {
  border-right: 1px solid var(--border);
  padding-right: 12px;
  overflow-y: auto;
}
.side-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.side-tip { font-size: 12px; color: var(--text-400); margin-top: 16px; line-height: 1.5; }

.chat-main {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  background: var(--background-100, #f9f9fb);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.chat-empty {
  text-align: center;
  color: var(--text-400);
  padding: 40px 0;
  font-size: 13px;
}

/* 聊天气泡 */
.bubble-row {
  display: flex;
  flex-direction: column;
  max-width: 75%;
  padding: 0;
}
.bubble-row--user { align-self: flex-start; }
.bubble-row--agent { align-self: flex-end; align-items: flex-end; }
.bubble-row--system { align-self: center; }

.bubble-meta {
  font-size: 11px;
  color: var(--text-400);
  margin-bottom: 4px;
  display: flex;
  gap: 8px;
}
.bubble-row--agent .bubble-meta { flex-direction: row-reverse; }

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--border);
  color: var(--text-800);
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble-row--agent .bubble {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}
.bubble-row--system .bubble {
  background: transparent;
  border-style: dashed;
  color: var(--text-400);
  font-style: italic;
}

/* 输入框 */
.chat-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 12px;
}
.chat-input :deep(.el-textarea__inner) {
  resize: none;
}
.chat-input-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

/* 会话列表：咨询内容 */
.consult-cell { display: flex; align-items: center; gap: 6px; }
.consult-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.text-muted { color: var(--text-400); }

/* 侧栏：咨询商品 / 订单卡片 */
.context-card {
  display: flex;
  gap: 10px;
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 8px;
  transition: border-color .2s;
}
.context-card:hover { border-color: #2563eb; }
.context-img {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
  background: var(--background-100, #f5f5f5);
}
.context-info { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }
.context-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-800);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.context-sub { margin-top: 4px; display: flex; align-items: center; }
.money { color: #e6a23c; font-weight: 600; }

/* 侧栏：用户订单列表 */
.order-list { max-height: 240px; overflow-y: auto; }
.order-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--border);
}
.order-item:last-child { border-bottom: none; }
.order-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.order-no { font-size: 12px; color: var(--text-600); overflow: hidden; text-overflow: ellipsis; }
.order-body { display: flex; align-items: center; gap: 8px; }
.order-img {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
  background: var(--background-100, #f5f5f5);
}
.order-info { flex: 1; min-width: 0; }
.order-name { font-size: 12px; color: var(--text-800); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.order-meta { font-size: 11px; color: var(--text-400); margin-top: 2px; }

/* 聊天气泡内的商品/订单卡片 */
.bubble--card {
  padding: 8px;
  background: #fff;
  border-color: var(--border);
  width: 260px;
  max-width: 100%;
}
.bubble-row--agent .bubble--card {
  background: #fff;
  color: var(--text-800);
  border-color: var(--border);
}
.chat-card { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.chat-card-img {
  width: 52px;
  height: 52px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
  background: var(--background-100, #f5f5f5);
}
.chat-card-info { flex: 1; min-width: 0; }
.chat-card-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.chat-card-sub { font-size: 12px; color: var(--text-400); margin-top: 4px; }
.chat-card-link { font-size: 12px; color: #2563eb; flex-shrink: 0; }

/* 推荐商品弹窗 */
.picker-search { display: flex; gap: 8px; margin-bottom: 12px; }
.product-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  max-height: 420px;
  overflow-y: auto;
  min-height: 120px;
}
.product-card {
  display: flex;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color .2s;
}
.product-card:hover { border-color: #2563eb; }
.product-card--off { opacity: .5; }
.product-img {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
  background: var(--background-100, #f5f5f5);
}
.product-info { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: space-between; }
.product-name {
  font-size: 12px;
  color: var(--text-800);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.product-meta { display: flex; align-items: center; justify-content: space-between; gap: 4px; margin-top: 4px; }
.picker-empty {
  grid-column: 1 / -1;
  text-align: center;
  color: var(--text-400);
  font-size: 13px;
  padding: 40px 0;
}
</style>
