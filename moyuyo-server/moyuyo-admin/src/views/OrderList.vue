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
                  <span class="shipping-hint" @click="handleLogistics(order)">
                    点击设置物流
                  </span>
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

    <!-- 物流弹窗：设置承运商 / 运单号 + 展示物流轨迹 -->
    <div v-if="logisticsDialog.visible" class="dialog-mask" @click.self="closeLogisticsDialog">
      <div class="logistics-dialog" role="dialog" aria-label="订单物流">
        <div class="dialog-header">
          <div>
            <div class="dialog-title">📦 订单物流管理</div>
            <div class="dialog-subtitle">
              {{ logisticsDialog.order?.orderNo || logisticsDialog.order?.no || '订单' }}
              <span v-if="logisticsDialog.data?.currentStatusLabel" class="status-tag">{{ logisticsDialog.data.currentStatusLabel }}</span>
            </div>
          </div>
          <button class="dialog-close" type="button" aria-label="关闭" @click="closeLogisticsDialog">×</button>
        </div>

        <!-- 录入区：承运商 + 运单号 + "设置后立即发货"开关 -->
        <div class="dialog-body">
          <div class="edit-card">
            <div class="edit-card-title">手动设置 / 修改 物流信息</div>
            <div class="form-row">
              <div class="form-group">
                <label>承运商 <span class="required">*</span></label>
                <select v-model="logisticsDialog.form.carrier" class="select-wrapper carrier-select">
                  <option value="">请选择或手动输入承运商</option>
                  <option v-for="c in carrierOptions" :key="c.id" :value="c.name">
                    {{ c.name }}<span v-if="c.transportMode"> · {{ c.transportMode }}</span>
                  </option>
                </select>
                <input v-model="logisticsDialog.form.carrier" type="text" class="carrier-custom-input"
                  placeholder="也可直接输入承运商名称（如 UPS / DHL / 顺丰国际）" maxlength="64" />
              </div>
              <div class="form-group">
                <label>运单号 <span class="required">*</span></label>
                <input v-model="logisticsDialog.form.trackingNo" type="text" maxlength="64"
                  placeholder="请输入运单号（最长 64 字符）" />
              </div>
              <div class="form-group form-group--checkbox" v-if="canShipOrder(logisticsDialog.order)">
                <label class="checkbox-label">
                  <input type="checkbox" v-model="logisticsDialog.form.forceShip" />
                  设置后立即发货（订单状态：待发货 → 已发货）
                </label>
                <p class="hint-text">
                  仅在订单为「待发货/已支付」时可用；未支付订单无法勾选此项。
                </p>
              </div>
            </div>
            <div class="edit-actions">
              <button class="btn btn-outline" @click="closeLogisticsDialog">取消</button>
              <button class="btn btn-primary" :disabled="logisticsDialog.saving"
                @click="saveOrderLogistics">
                {{ logisticsDialog.saving ? '保存中…' : '保存' }}
              </button>
            </div>
          </div>

          <!-- 信息展示：运单号、发货/收货时间 -->
          <div class="info-summary">
            <div class="summary-line"><span>承运商：</span><b>{{ logisticsDialog.data?.carrier || logisticsDialog.form.carrier || '—' }}</b></div>
            <div class="summary-line"><span>运单号：</span>
              <b v-if="logisticsDialog.data?.trackingNumber" class="mono">{{ logisticsDialog.data.trackingNumber }}</b>
              <span v-else>—</span>
              <button v-if="logisticsDialog.data?.trackingNumber" class="btn btn-ghost copy-btn"
                @click="copyText(logisticsDialog.data.trackingNumber)">复制</button>
            </div>
            <div class="summary-line"><span>发货时间：</span>{{ formatTime(logisticsDialog.data?.shippedAt) }}</div>
            <div class="summary-line"><span>收货时间：</span>{{ formatTime(logisticsDialog.data?.receivedAt) }}</div>
            <div class="summary-line">
              <span>状态：</span>
              <span :class="statusClassMap[logisticsDialog.data?.currentStatus] || 'tag tag-gray'">
                {{ logisticsDialog.data?.currentStatusLabel || '暂无' }}
              </span>
            </div>
          </div>

          <!-- 物流轨迹时间轴 -->
          <div class="trace-card">
            <div class="trace-card-title">
              <span>物流轨迹</span>
              <span class="trace-tip">接入快递100/17track/AfterShip 后将展示真实轨迹</span>
            </div>
            <ul v-if="logisticsDialog.data?.traces && logisticsDialog.data.traces.length" class="trace-list">
              <li v-for="(item, idx) in logisticsDialog.data.traces" :key="idx"
                :class="{ 'trace-item': true, 'is-first': idx === 0 }">
                <div class="trace-node">
                  <div :class="['trace-dot', idx === 0 ? 'trace-dot--active' : '']"></div>
                  <div v-if="idx !== (logisticsDialog.data.traces.length - 1)" class="trace-bar"></div>
                </div>
                <div class="trace-body">
                  <div class="trace-time">{{ formatLogisticsTime(item.time) }}</div>
                  <div class="trace-desc">{{ item.desc || '暂无描述' }}</div>
                  <div v-if="item.location" class="trace-location">📍 {{ item.location }}</div>
                </div>
              </li>
            </ul>
            <div v-else class="trace-empty">
              <div class="trace-empty-icon">📭</div>
              <div>当前暂无物流轨迹</div>
              <p v-if="!logisticsDialog.data?.trackingNumber" class="trace-empty-hint">
                请先填写承运商和运单号并"保存"，商家交运后可在此查看轨迹。
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getOrderOpsStats, getOrderList, shipOrder, syncOrderToWoo,
  getOrderLogistics, updateOrderLogistics, getCarriers
} from '../api/admin'
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

// 承运商选项：弹窗下拉使用（来自 /api/admin/logistics/carriers，mo_carrier 表）
const carrierOptions = ref([])

// 物流弹窗状态（数据+表单+控制）
const logisticsDialog = reactive({
  visible: false,
  order: null,
  data: null,
  saving: false,
  form: {
    carrier: '',
    trackingNo: '',
    forceShip: false
  }
})

// 物流状态英文字段 → 管理后台已有 tag 样式（保持和订单列表 status 风格一致）
const statusClassMap = {
  NO_RECORD: 'tag tag-gray',
  PENDING_PICKUP: 'tag tag-yellow',
  IN_TRANSIT: 'tag tag-blue',
  OUT_FOR_DELIVERY: 'tag tag-blue',
  DELIVERED: 'tag tag-green',
  EXCEPTION: 'tag tag-red'
}

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

// 查看物流（弹窗：手动设置承运商 / 运单号 + 展示轨迹）
async function handleLogistics(order) {
  try {
    logisticsDialog.order = order
    logisticsDialog.visible = true
    logisticsDialog.saving = false
    // 并行：承运商下拉 + 当前订单物流详情
    const [carriers, data] = await Promise.all([
      carrierOptions.value.length ? Promise.resolve(carrierOptions.value) : fetchCarrierOptions(),
      getOrderLogistics(order.id)
    ])
    logisticsDialog.data = data || null
    // 初始化表单默认值：优先显示当前已保存的承运商/运单号
    logisticsDialog.form = {
      carrier: (data?.carrier || order?.shippingCarrier || '').toString(),
      trackingNo: (data?.trackingNumber || order?.trackingNumber || '').toString(),
      forceShip: false
    }
  } catch (err) {
    ElMessage.error('查询物流信息失败: ' + (err?.message || '未知错误'))
    logisticsDialog.visible = false
  }
}

function closeLogisticsDialog() {
  logisticsDialog.visible = false
  logisticsDialog.order = null
  logisticsDialog.data = null
  logisticsDialog.form = { carrier: '', trackingNo: '', forceShip: false }
  logisticsDialog.saving = false
}

// 拉取承运商列表（成功时做全局缓存，弹窗多次打开不重复请求）
async function fetchCarrierOptions() {
  try {
    const res = await getCarriers()
    const records = Array.isArray(res) ? res : (Array.isArray(res?.records) ? res.records : (Array.isArray(res?.data) ? res.data : []))
    carrierOptions.value = records
    return records
  } catch (e) {
    // 承运商列表拉取失败不阻断主流程（仍允许用户手动输入）
    console.warn('承运商列表拉取失败，已降级为手动输入', e)
    carrierOptions.value = []
    return []
  }
}

// 判断订单是否允许"设置后立即发货"（必须是待发货或已支付状态）
function canShipOrder(order) {
  if (!order) return false
  const status = (order.status || order.statusEnum || '').toUpperCase()
  if (['SHIPPED', 'RECEIVED', 'COMPLETED', 'CANCELLED', 'CANCELED'].includes(status)) return false
  return true
}

// 保存物流信息（写承运商 / 运单号；如果勾选了 forceShip 则顺便发货）
async function saveOrderLogistics() {
  if (!logisticsDialog.order?.id) return
  const carrier = (logisticsDialog.form.carrier || '').trim()
  const trackingNo = (logisticsDialog.form.trackingNo || '').trim()
  if (!carrier) {
    ElMessage.warning('请先填写承运商（可在下拉选择或手动输入）')
    return
  }
  if (!trackingNo) {
    ElMessage.warning('请先填写运单号')
    return
  }
  if (carrier.length > 64) {
    ElMessage.warning('承运商名称长度不能超过 64 字符')
    return
  }
  if (trackingNo.length > 64) {
    ElMessage.warning('运单号长度不能超过 64 字符')
    return
  }
  logisticsDialog.saving = true
  try {
    const payload = {
      carrier,
      trackingNo,
      forceShip: !!logisticsDialog.form.forceShip
    }
    const res = await updateOrderLogistics(logisticsDialog.order.id, payload)
    ElMessage.success(res?.message || '保存成功')
    // 保存成功后：1) 刷新本弹窗的物流详情；2) 刷新订单行，让"承运商/运单号"列和订单状态立即更新
    const fresh = await getOrderLogistics(logisticsDialog.order.id)
    logisticsDialog.data = fresh || null
    await fetchOrders()
  } catch (err) {
    ElMessage.error('保存失败: ' + (err?.message || '未知错误'))
  } finally {
    logisticsDialog.saving = false
  }
}

// 统一的复制文本（封装 navigator.clipboard，降级用 document.execCommand，兼容老浏览器）
function copyText(text) {
  if (!text) return
  const onDone = () => ElMessage.success('已复制运单号')
  const onFail = () => {
    // 兜底：浏览器不允许剪贴板时，使用 prompt 让用户手动复制
    window.prompt('请按 Ctrl+C / Cmd+C 复制：', String(text))
  }
  if (navigator?.clipboard?.writeText) {
    navigator.clipboard.writeText(String(text)).then(onDone, onFail)
  } else {
    try {
      const ta = document.createElement('textarea')
      ta.value = String(text)
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      const ok = document.execCommand('copy')
      document.body.removeChild(ta)
      ok ? onDone() : onFail()
    } catch {
      onFail()
    }
  }
}

// 格式化物流轨迹时间（支持 yyyy-MM-ddTHH:mm:ss 或 yyyy-MM-dd HH:mm:ss 等常见格式；解析失败则原样返回）
function formatLogisticsTime(t) {
  if (!t) return '—'
  if (typeof t !== 'string') return String(t)
  const d = new Date(t.replace(/\s/, 'T'))
  if (!isNaN(d.getTime())) {
    const pad = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return t
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

/* ======== 物流弹窗 ======== */
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 20px;
  z-index: 1000;
  backdrop-filter: blur(2px);
  overflow: auto;
}
.logistics-dialog {
  width: 100%;
  max-width: 720px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.18);
  overflow: hidden;
  animation: fadeIn 0.2s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-8px); }
  to   { opacity: 1; transform: translateY(0); }
}
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 22px;
  border-bottom: 1px solid var(--divider);
  background: linear-gradient(135deg, #f0f7ff 0%, #eef7f1 100%);
}
.dialog-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-900);
  line-height: 1.3;
}
.dialog-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-500);
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
}
.dialog-subtitle .status-tag {
  display: inline-block;
  padding: 1px 8px;
  background: #eef2ff;
  color: #4f46e5;
  border-radius: 10px;
  font-family: inherit;
  font-size: 11px;
}
.dialog-close {
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
  color: var(--text-500);
  transition: all 0.15s;
}
.dialog-close:hover { background: var(--bg-100); color: var(--text-800); }

.dialog-body {
  padding: 20px 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.edit-card,
.info-summary,
.trace-card {
  border: 1px solid var(--divider);
  border-radius: 12px;
  padding: 16px 18px;
  background: #fafbfc;
}
.edit-card-title,
.trace-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-800);
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.trace-card-title .trace-tip {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-400);
}
.form-row {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
/* 顶部筛选面板：所有字段同一行排列 */
.query-panel .form-row {
  flex-direction: row;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 16px;
}
.query-panel .form-group {
  flex: 1 1 140px;
  min-width: 120px;
}
.query-panel .form-actions {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-group label {
  font-size: 12px;
  color: var(--text-600);
  font-weight: 500;
}
.form-group .required { color: #ef4444; }
.form-group input[type="text"],
.carrier-select {
  height: 36px;
  border: 1px solid var(--divider);
  border-radius: 8px;
  padding: 0 12px;
  font-size: 13px;
  color: var(--text-800);
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
}
.form-group input[type="text"]:focus,
.carrier-select:focus {
  border-color: var(--brand-500);
}
.carrier-custom-input {
  margin-top: 4px;
}
.form-group--checkbox {
  gap: 2px;
  padding: 8px 10px;
  background: #fff;
  border-radius: 8px;
  border: 1px dashed var(--divider);
}
.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-700);
  cursor: pointer;
  user-select: none;
}
.hint-text {
  margin: 4px 0 0 26px;
  font-size: 11px;
  color: var(--text-400);
  line-height: 1.5;
}
.edit-actions {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.info-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-600);
}
.summary-line {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.8;
}
.summary-line span { color: var(--text-500); min-width: 72px; }
.summary-line b { color: var(--text-800); font-weight: 600; }
.summary-line .mono { font-family: var(--font-mono); letter-spacing: 0.2px; }
.copy-btn {
  padding: 2px 10px;
  font-size: 11px;
  margin-left: 6px;
}

/* 物流轨迹时间轴 */
.trace-list {
  list-style: none;
  padding: 4px 0 0 0;
  margin: 0;
  max-height: 360px;
  overflow: auto;
}
.trace-item {
  display: flex;
  gap: 12px;
}
.trace-node {
  width: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}
.trace-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #cbd5e1;
  margin-top: 5px;
  flex-shrink: 0;
}
.trace-dot--active {
  width: 14px;
  height: 14px;
  margin-top: 3px;
  background: #16a34a;
  box-shadow: 0 0 0 4px rgba(22, 163, 74, 0.12);
}
.trace-bar {
  flex: 1;
  width: 2px;
  min-height: 28px;
  background: #e2e8f0;
  margin: 4px 0;
}
.trace-body {
  flex: 1;
  padding-bottom: 16px;
}
.trace-time {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-500);
  margin-bottom: 2px;
}
.trace-desc {
  font-size: 13px;
  color: var(--text-700);
  line-height: 1.55;
}
.is-first .trace-desc {
  font-weight: 600;
  color: var(--text-900);
}
.trace-location {
  font-size: 11px;
  color: var(--text-500);
  margin-top: 2px;
}

.trace-empty {
  padding: 28px 16px;
  text-align: center;
  color: var(--text-500);
  font-size: 13px;
}
.trace-empty-icon {
  font-size: 32px;
  margin-bottom: 6px;
}
.trace-empty-hint {
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-400);
}
</style>
