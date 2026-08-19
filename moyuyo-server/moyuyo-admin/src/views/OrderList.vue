<template>
  <div class="order-list-page">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>订单列表</h1>
      <p>查看与管理所有订单，处理发货、售后与 WooCommerce 同步</p>
    </div>

    <!-- 订单状态概览 -->
    <div class="order-summary">
      <div class="summary-card">
        <div class="summary-icon summary-icon--blue">📦</div>
        <div class="summary-body">
          <div class="summary-label">待发货</div>
          <div class="summary-value">{{ statsData.pendingShip }}</div>
        </div>
      </div>
      <div class="summary-card">
        <div class="summary-icon summary-icon--orange">🔄</div>
        <div class="summary-body">
          <div class="summary-label">售后中</div>
          <div class="summary-value">{{ statsData.afterSale }}</div>
        </div>
      </div>
      <div class="summary-card" :class="{ 'summary-card--warn': statsData.abnormal > 0 }">
        <div class="summary-icon" :class="statsData.abnormal > 0 ? 'summary-icon--red' : 'summary-icon--gray'">⚠</div>
        <div class="summary-body">
          <div class="summary-label">异常订单</div>
          <div class="summary-value">{{ statsData.abnormal }}</div>
        </div>
      </div>
      <div class="summary-card summary-card--total">
        <div class="summary-icon summary-icon--green">✓</div>
        <div class="summary-body">
          <div class="summary-label">全部订单</div>
          <div class="summary-value">{{ total }}</div>
        </div>
      </div>
    </div>

    <!-- 查询面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>订单状态</label>
          <select v-model="filters.status" class="select-wrapper">
            <option value="">全部状态</option>
            <option value="PENDING_PAY">待付款</option>
            <option value="PENDING_SHIP">待发货</option>
            <option value="SHIPPED">已发货</option>
            <option value="COMPLETED">已完成</option>
            <option value="CANCELLED">已取消</option>
          </select>
        </div>
        <div class="form-group">
          <label>开始日期</label>
          <input v-model="filters.dateStart" type="date" />
        </div>
        <div class="form-group">
          <label>结束日期</label>
          <input v-model="filters.dateEnd" type="date" />
        </div>
        <div class="form-group">
          <label>关键词</label>
          <input v-model="filters.search" type="text" placeholder="订单号 / 用户名" />
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" @click="handleSearch">搜索</button>
          <button class="btn btn-outline" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="data-table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th class="checkbox-cell"><input type="checkbox" v-model="selectAll" @change="toggleSelectAll" /></th>
            <th>订单号</th>
            <th>用户</th>
            <th>商品</th>
            <th>金额</th>
            <th>状态</th>
            <th>承运商 / 运单号</th>
            <th>WC 同步</th>
            <th>下单时间</th>
            <th style="min-width: 240px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in filteredOrders" :key="order.id">
            <td class="checkbox-cell"><input type="checkbox" v-model="selectedIds" :value="order.id" /></td>
            <td>
              <span class="order-no">{{ order.orderNo || order.no }}</span>
            </td>
            <td>
              <div class="user-info-cell">
                <div class="user-avatar">{{ (order.userName || order.user || '?').toString().charAt(0).toUpperCase() }}</div>
                <div>
                  <div class="user-name-text">{{ order.userName || order.user || order.userId || '匿名用户' }}</div>
                </div>
              </div>
            </td>
            <td>
              <div class="order-items-cell">{{ orderItemsSummary(order) }}</div>
            </td>
            <td class="money">¥{{ order.payAmount ?? order.amount ?? 0 }}</td>
            <td>
              <span :class="getStatusTagClass(order.statusEnum || order.status)">
                <span :class="getStatusDotClass(order.statusEnum || order.status)"></span>
                {{ statusLabel(order.statusEnum || order.status) }}
              </span>
            </td>
            <td>
              <div class="shipping-cell">
                <div v-if="order.shippingCarrier || order.trackingNumber" class="shipping-info">
                  <div class="shipping-carrier">
                    <span class="shipping-icon">🚚</span>
                    {{ order.shippingCarrier || '—' }}
                  </div>
                  <div class="shipping-tracking">
                    {{ order.trackingNumber || '—' }}
                  </div>
                </div>
                <div v-else class="shipping-empty">
                  <span v-if="order.wooOrderId" class="shipping-hint" @click="handleLogistics(order)">
                    点击查看物流
                  </span>
                  <span v-else>—</span>
                </div>
              </div>
            </td>
            <td>
              <span :class="wooOrderClass(order)">
                <span :class="order.wooOrderId ? 'status-dot green' : (order.syncStatus === -1 ? 'status-dot red' : 'status-dot gray')"></span>
                {{ wooOrderLabel(order) }}
              </span>
            </td>
            <td class="order-time">{{ formatTime(order.createTime || order.time) }}</td>
            <td>
              <div class="cell-actions">
                <button class="btn btn-sm btn-outline" @click="handleConfirmShip(order)">确认发货</button>
                <button class="btn btn-sm btn-outline" @click="handleLogistics(order)">物流</button>
                <button class="btn btn-sm btn-outline" @click="handleDetail(order)">详情</button>
                <button class="btn btn-sm btn-outline" :disabled="syncingIds.includes(order.id)"
                  @click="handleSyncToWoo(order)">
                  {{ syncingIds.includes(order.id) ? '同步中…' : '推 WC' }}
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="filteredOrders.length === 0">
            <td colspan="9">
              <div class="empty-state">
                <div class="empty-state-icon">📋</div>
                <div class="empty-state-text">暂无订单数据</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="pagination" v-if="total > 0">
        <span class="pagination-info">共 {{ total }} 条 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
        <div class="pagination-btns">
          <button class="pagination-btn" :disabled="currentPage <= 1" @click="currentPage--">‹ 上一页</button>
          <button class="pagination-btn" :disabled="currentPage >= totalPages" @click="currentPage++">下一页 ›</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getOrderOpsStats, getOrderList, getOrderDetail, shipOrder, syncOrderToWoo } from '../api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { toArray } from '../utils/safeArray'

const router = useRouter()

const filters = reactive({
  status: '',
  dateStart: '',
  dateEnd: '',
  search: ''
})

const statsData = ref({
  pendingShip: 0,
  afterSale: 0,
  abnormal: 0
})

const selectAll = ref(false)
const selectedIds = ref([])
const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)
const total = ref(0)

const orderList = ref([])

// WooCommerce 同步状态：记录正在同步的订单ID，避免重复点击
const syncingIds = ref([])

// 获取订单统计数据
async function fetchStats() {
  try {
    const res = await getOrderOpsStats()
    if (res) {
      // 后端返回: { totalOrders, statusStats, pendingPayment, pendingShip, shipped, completed, cancelled }
      // 映射到前端卡片: pendingShip / afterSale / abnormal / totalOrders
      const statusStats = res.statusStats || {}
      statsData.value = {
        pendingShip: Number(res.pendingShip) || 0,
        // 售后中 = 退款中 + 已退款 + 退款失败（从 statusStats 聚合）
        afterSale: (Number(statusStats.REFUNDING) || 0)
          + (Number(statusStats.REFUNDED) || 0)
          + (Number(statusStats.REFUND_FAILED) || 0),
        // 异常订单 = 支付超时 + 发货超时等（后端如果有 exception 字段则直接使用）
        abnormal: Number(res.abnormalOrders) || Number(statusStats.EXCEPTION) || 0,
        totalOrders: Number(res.totalOrders) || 0
      }
    }
  } catch (err) {
    console.error('获取订单统计数据失败:', err)
  }
}

// 获取订单列表
async function fetchOrders() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize,
      status: filters.status,
      keyword: filters.search,
      startDate: filters.dateStart,
      endDate: filters.dateEnd
    }
    Object.keys(params).forEach(k => {
      if (!params[k]) delete params[k]
    })
    const res = await getOrderList(params)
    if (res) {
      orderList.value = toArray(res)
      total.value = res.total || 0
    }
  } catch (err) {
    console.error('获取订单列表失败:', err)
    ElMessage.error('获取订单列表失败')
  } finally {
    loading.value = false
  }
}

// 提取订单商品概要（列表只显示前 1-2 个商品名称 + 数量）
function orderItemsSummary(order) {
  const items = Array.isArray(order.items) ? order.items : []
  if (items.length === 0) return '-'
  const head = items.slice(0, 2).map(it => {
    const name = it.productName || it.name || it.skuSpec || it.sku || '商品'
    const qty = it.quantity ?? it.qty ?? 1
    return `${name} x${qty}`
  })
  return head.join('、') + (items.length > 2 ? ` 等${items.length}件` : '')
}

// 订单状态映射为 CSS class 后缀
function statusClass(status) {
  if (!status) return 'default'
  const s = String(status).toUpperCase()
  const map = {
    PENDING: 'pending', PAID: 'paid', HOLD: 'hold',
    SHIPPED: 'shipped', IN_TRANSIT: 'transit',
    DELIVERED: 'delivered', RECEIVED: 'received',
    COMPLETED: 'completed', CANCELLED: 'canceled', CANCELED: 'canceled',
    REFUNDING: 'refunding', REFUNDED: 'refunded'
  }
  return map[s] || s.toLowerCase()
}

// 订单状态映射为 tag 样式（统一 design system）
function getStatusTagClass(status) {
  const cls = statusClass(status)
  // 映射到 design-system 提供的颜色
  const colorMap = {
    pending: 'tag tag-yellow',
    paid: 'tag tag-blue',
    hold: 'tag tag-orange',
    shipped: 'tag tag-blue',
    transit: 'tag tag-blue',
    delivered: 'tag tag-green',
    received: 'tag tag-green',
    completed: 'tag tag-green',
    canceled: 'tag tag-gray',
    refunding: 'tag tag-yellow',
    refunded: 'tag tag-gray',
    default: 'tag tag-gray'
  }
  return colorMap[cls] || 'tag tag-gray'
}

// 状态点颜色
function getStatusDotClass(status) {
  const cls = statusClass(status)
  const colorMap = {
    pending: 'status-dot yellow',
    paid: 'status-dot green',
    hold: 'status-dot yellow',
    shipped: 'status-dot green',
    transit: 'status-dot green',
    delivered: 'status-dot green',
    received: 'status-dot green',
    completed: 'status-dot green',
    canceled: 'status-dot gray',
    refunding: 'status-dot yellow',
    refunded: 'status-dot gray',
    default: 'status-dot gray'
  }
  return colorMap[cls] || 'status-dot gray'
}

// 订单状态映射为中文标签
function statusLabel(status) {
  const map = {
    PENDING: '待付款', PAID: '已付款', HOLD: '挂起',
    SHIPPED: '已发货', IN_TRANSIT: '运输中',
    DELIVERED: '已签收', RECEIVED: '已签收',
    COMPLETED: '已完成', CANCELLED: '已取消', CANCELED: '已取消',
    REFUNDING: '退款中', REFUNDED: '已退款'
  }
  return map[String(status || '').toUpperCase()] || (status || '未知')
}

// 时间格式化为 yyyy-MM-dd HH:mm
function formatTime(t) {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return String(t)
    const pad = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return String(t)
  }
}

const filteredOrders = computed(() => {
  // 过滤 null/undefined 条目，防止 v-for 渲染时报错
  return (orderList.value || []).filter(o => o != null)
})

const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

function toggleSelectAll() {
  if (selectAll.value) {
    selectedIds.value = filteredOrders.value.map(o => o.id)
  } else {
    selectedIds.value = []
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchOrders()
}

function handleReset() {
  filters.status = ''
  filters.dateStart = ''
  filters.dateEnd = ''
  filters.search = ''
  currentPage.value = 1
  fetchOrders()
}

// 确认发货
async function handleConfirmShip(order) {
  try {
    await ElMessageBox.confirm(
      `确认订单 ${order.no} 已发货？请确保物流单号已填写。`,
      '发货确认',
      { type: 'warning', confirmButtonText: '确认发货', cancelButtonText: '取消' }
    )
    await shipOrder(order.id)
    ElMessage.success(`订单 ${order.no} 已确认发货`)
    fetchOrders()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      console.error('确认发货失败:', err)
      ElMessage.error('确认发货失败: ' + (err?.message || '未知错误'))
    }
  }
}

// 查看物流（从订单详情取真实字段；后端 detail 已自动从 WooCommerce 拉取最新运单号）
async function handleLogistics(order) {
  try {
    const res = await getOrderDetail(order.id)
    if (!res) {
      ElMessage.warning('订单不存在')
      return
    }
    const trackingNo = res.trackingNumber || res.trackingNo || ''
    const carrier = res.shippingCarrier || res.carrier || 'N/A'
    const status = ({'PENDING':'待发货','IN_TRANSIT':'运输中','DELIVERED':'已签收'})[res.shippingStatus] || res.shippingStatus || res.status || '暂无'
    const wooTip = res.wooOrderId ? `（已从 WooCommerce #${res.wooOrderId} 同步）` : '（本地数据）'
    ElMessage.info(
      `订单 ${order.orderNo || order.no} ${wooTip}\n` +
      `承运商: ${carrier}\n` +
      `运单号: ${trackingNo || '暂无'}\n` +
      `状态: ${status}`
    )
    // 同步刷新本地订单的运单信息，让表格列立即更新
    const idx = orderList.value.findIndex(o => o.id === order.id)
    if (idx >= 0) {
      orderList.value[idx] = {
        ...orderList.value[idx],
        trackingNumber: res.trackingNumber,
        shippingCarrier: res.shippingCarrier
      }
    }
  } catch (err) {
    ElMessage.error('查询物流信息失败: ' + (err?.message || '未知错误'))
  }
}

// 查看订单详情
function handleDetail(order) {
  router.push(`/orders/${order.id}`)
}

// WC 同步状态：根据 wooOrderId / syncStatus 展示
function wooOrderClass(order) {
  if (order.wooOrderId) return 'tag tag-green'
  if (order.syncStatus === -1) return 'tag tag-red'
  return 'tag tag-gray'
}

function wooOrderLabel(order) {
  if (order.wooOrderId) return `已同步 #${order.wooOrderId}`
  if (order.syncStatus === -1) return '同步失败'
  return '未同步'
}

// 手动重推订单到 WooCommerce
async function handleSyncToWoo(order) {
  if (syncingIds.value.includes(order.id)) return
  syncingIds.value.push(order.id)
  try {
    const res = await syncOrderToWoo(order.id)
    const data = res?.data || res
    if (data?.wooOrderId) {
      ElMessage.success(`订单 ${order.no || order.orderNo} 已同步，wooOrderId=${data.wooOrderId}`)
    } else {
      ElMessage.warning(`订单同步失败：${data?.message || '未知原因'}`)
    }
    fetchOrders()
  } catch (err) {
    console.error('同步订单到 WC 失败:', err)
    ElMessage.error('同步订单到 WC 失败：' + (err?.message || ''))
  } finally {
    syncingIds.value = syncingIds.value.filter(id => id !== order.id)
  }
}

// 监听页码变化，重新加载数据
watch(currentPage, () => {
  fetchOrders()
})

onMounted(() => {
  fetchStats()
  fetchOrders()
})
</script>

<style scoped>
/* 订单状态概览 */
.order-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  transition: all 0.18s ease;
}

.summary-card:hover {
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.summary-card--warn {
  border-color: rgba(255, 59, 48, 0.35);
  background: linear-gradient(0deg, var(--state-error-surface), var(--state-error-surface));
}

.summary-card--total {
  background: linear-gradient(135deg, var(--brand-50), var(--card));
  border-color: var(--brand-200);
}

.summary-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.summary-icon--blue { background: var(--brand-50); color: var(--brand-600); }
.summary-icon--orange { background: #fff4e5; color: #ff9500; }
.summary-icon--green { background: var(--state-success-surface); color: var(--state-success); }
.summary-icon--red { background: var(--state-error-surface); color: var(--state-error); }
.summary-icon--gray { background: var(--background-200); color: var(--text-500); }

.summary-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-label {
  font-size: 12px;
  color: var(--text-400);
  font-weight: 500;
}

.summary-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.summary-card--warn .summary-value { color: var(--state-error); }

/* 订单号 */
.order-no {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--brand-600);
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.order-no:hover {
  opacity: 0.7;
}

/* 订单时间 */
.order-time {
  font-size: 12px;
  color: var(--text-500);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* 承运商 / 运单号 */
.shipping-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 130px;
}
.shipping-info { display: flex; flex-direction: column; gap: 2px; }
.shipping-carrier {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-800);
  display: flex;
  align-items: center;
  gap: 4px;
}
.shipping-icon { font-size: 12px; }
.shipping-tracking {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-500);
}
.shipping-empty {
  font-size: 12px;
  color: var(--text-400);
}
.shipping-hint {
  cursor: pointer;
  color: var(--brand-600);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.shipping-hint:hover { color: var(--brand-700); }

/* 商品概要 */
.order-items-cell {
  max-width: 280px;
  font-size: 12px;
  color: var(--text-600);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
