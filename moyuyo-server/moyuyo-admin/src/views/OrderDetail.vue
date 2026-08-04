<template>
  <div class="order-detail">
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/orders">订单管理</router-link>
      <span class="separator">/</span>
      <span class="current">订单详情</span>
    </div>

    <!-- 标题栏 -->
    <div class="page-header">
      <div class="page-header-left">
        <h2 class="page-title">订单详情</h2>
        <span class="page-subtitle">Order #{{ orderNo }}</span>
      </div>
      <div class="page-header-actions">
        <button class="btn btn-primary" @click="handleShip">确认发货</button>
        <button class="btn btn-outline" :disabled="syncingWoo" @click="handleSyncToWoo">
          {{ syncingWoo ? '同步中…' : '同步WC' }}
        </button>
        <button class="btn btn-outline" @click="showAddressModal = true">修改地址</button>
        <button class="btn btn-outline" @click="showNoteModal = true">备注</button>
      </div>
    </div>

    <!-- 进度条 -->
    <div class="order-progress">
      <div
        v-for="(step, index) in progressSteps"
        :key="step.key"
        class="progress-step"
        :class="{ active: index <= currentStep, done: index < currentStep }"
      >
        <div class="step-indicator">
          <span class="step-icon">{{ index < currentStep ? '✓' : index + 1 }}</span>
        </div>
        <div class="step-label">{{ step.label }}</div>
        <div class="step-date" v-if="step.date">{{ step.date }}</div>
      </div>
    </div>

    <!-- 两栏布局 -->
    <div class="detail-layout">
      <!-- 左栏：商品明细 -->
      <div class="detail-main">
        <!-- 商品列表 -->
        <div class="card">
          <div class="card-header">
            <h3>商品明细</h3>
            <span class="item-count">共 {{ orderItems.length }} 件商品</span>
          </div>
          <div class="card-body no-padding">
            <table class="data-table">
              <thead>
                <tr>
                  <th>商品</th>
                  <th>SKU</th>
                  <th>单价</th>
                  <th>数量</th>
                  <th>小计</th>
                </tr>
              </thead>
              <tbody>
              <tr v-for="item in orderItems" :key="item.id">
                <td>
                  <div class="product-cell">
                    <div class="product-thumb">{{ thumbChar(item) }}</div>
                    <div>
                      <div class="product-name">{{ item.name || item.productName || '商品' }}</div>
                    </div>
                  </div>
                </td>
                <td>{{ item.sku || item.skuSpec || '-' }}</td>
                <td><span class="money">¥{{ formatMoney(item.price) }}</span></td>
                <td>{{ item.qty ?? item.quantity ?? 0 }}</td>
                <td><span class="money">¥{{ formatMoney(item.subtotal) }}</span></td>
              </tr>
            </tbody>
            </table>
          </div>
        </div>

        <!-- 价格汇总 -->
        <div class="price-summary card">
          <div class="card-body">
            <div class="price-row"><span>商品金额</span><span class="money">¥{{ priceSummary.goodsAmount }}</span></div>
            <div class="price-row"><span>运费</span><span class="money">¥{{ priceSummary.freight }}</span></div>
            <div class="price-row"><span>优惠减免</span><span class="money">-¥{{ priceSummary.discount }}</span></div>
            <div class="price-row total"><span>实付金额</span><span class="money total-amount">¥{{ priceSummary.total }}</span></div>
          </div>
        </div>

        <!-- 操作日志 -->
        <div class="card">
          <div class="card-header">
            <h3>操作日志</h3>
          </div>
          <div class="card-body">
            <div class="operation-timeline">
              <div v-for="(log, index) in operationLogs" :key="index" class="timeline-item">
                <div class="timeline-dot" :class="log.status"></div>
                <div class="timeline-content">
                  <div class="timeline-title">{{ log.action }}</div>
                  <div class="timeline-meta">{{ log.operator }} · {{ log.time }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：信息卡 -->
      <div class="detail-side">
        <!-- 订单信息 -->
        <div class="card info-card">
          <div class="card-header"><h3>订单信息</h3></div>
          <div class="card-body">
            <div class="info-row"><span class="info-label">订单编号</span><span class="info-value">{{ orderInfo.orderNo }}</span></div>
            <div class="info-row"><span class="info-label">下单时间</span><span class="info-value">{{ orderInfo.createTime }}</span></div>
            <div class="info-row"><span class="info-label">支付时间</span><span class="info-value">{{ orderInfo.payTime }}</span></div>
            <div class="info-row"><span class="info-label">支付方式</span><span class="info-value">{{ orderInfo.payMethod }}</span></div>
            <div class="info-row"><span class="info-label">订单来源</span><span class="info-value">{{ orderInfo.source }}</span></div>
            <div class="info-row"><span class="info-label">WC同步</span><span class="info-value">{{ wooOrderLabel }}</span></div>
            <div class="info-row"><span class="info-label">订单状态</span><span class="tag" :class="orderInfo.statusClass">{{ orderInfo.status }}</span></div>
          </div>
        </div>

        <!-- 收货信息 -->
        <div class="card info-card">
          <div class="card-header"><h3>收货信息</h3></div>
          <div class="card-body">
            <div class="info-row"><span class="info-label">收件人</span><span class="info-value">{{ shippingInfo.name }}</span></div>
            <div class="info-row"><span class="info-label">联系电话</span><span class="info-value">{{ shippingInfo.phone }}</span></div>
            <div class="info-row address-row">
              <span class="info-label">收货地址</span>
              <div class="info-value address-value">
                <span>{{ shippingInfo.address }}</span>
                <button class="copy-btn" @click="copyAddress">复制</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改地址弹窗 -->
    <div v-if="showAddressModal" class="modal-overlay" @click.self="showAddressModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>修改收货地址</h3>
          <button class="modal-close" @click="showAddressModal = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>收件人</label>
            <input v-model="addressForm.name" placeholder="请输入收件人" />
          </div>
          <div class="form-group">
            <label>联系电话</label>
            <input v-model="addressForm.phone" placeholder="请输入联系电话" />
          </div>
          <div class="form-group">
            <label>详细地址</label>
            <input v-model="addressForm.address" placeholder="请输入详细地址" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="showAddressModal = false">取消</button>
          <button class="btn btn-primary" @click="confirmAddress">确认修改</button>
        </div>
      </div>
    </div>

    <!-- 备注弹窗 -->
    <div v-if="showNoteModal" class="modal-overlay" @click.self="showNoteModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>订单备注</h3>
          <button class="modal-close" @click="showNoteModal = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>备注内容</label>
            <textarea v-model="noteContent" rows="4" placeholder="请输入备注内容"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="showNoteModal = false">取消</button>
          <button class="btn btn-primary" @click="confirmNote">保存备注</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrderDetail, shipOrder, updateOrderAddress, updateOrderRemark, syncOrderToWoo } from '../api/admin'

const router = useRouter()
const route = useRoute()

const currentStep = ref(0)
const orderNo = ref('')
const loading = ref(false)

const progressSteps = ref([])
const orderItems = ref([])
const operationLogs = ref([])

// 价格汇总
const priceSummary = reactive({
  goodsAmount: '0.00',
  freight: '0.00',
  discount: '0.00',
  total: '0.00'
})

// 订单信息
const orderInfo = reactive({
  orderNo: '',
  createTime: '',
  payTime: '',
  payMethod: '',
  source: '',
  status: '',
  statusClass: ''
})

// 收货信息
const shippingInfo = reactive({
  name: '',
  phone: '',
  address: ''
})

const showAddressModal = ref(false)
const showNoteModal = ref(false)
const noteContent = ref('')

// WC 同步状态
const syncingWoo = ref(false)
const wooOrderId = ref(null)
const syncStatus = ref(null)

// WC 同步展示文本：未同步 / 同步失败 / 已同步#id
const wooOrderLabel = computed(() => {
  if (wooOrderId.value) return `已同步 #${wooOrderId.value}`
  if (syncStatus.value === -1) return '同步失败'
  return '未同步'
})

const addressForm = reactive({
  name: '',
  phone: '',
  address: ''
})

// 商品名首字符（带容错：空值/非字符串时返回 ?）
function thumbChar(item) {
  const name = item?.name || item?.productName || item?.skuSpec || item?.sku
  if (!name) return '?'
  const s = String(name)
  return s.charAt ? s.charAt(0) : '?'
}

// 根据订单状态生成进度步骤
function buildProgressSteps(data) {
  const status = String(data.status || '').toUpperCase()
  // 定义所有可能的步骤
  const allSteps = [
    { key: 'created', label: '已下单', date: data.createTime || '' },
    { key: 'paid', label: '已支付', date: data.paidAt || data.payTime || '' },
    { key: 'shipped', label: '已发货', date: data.shippedAt || data.shipTime || '' },
    { key: 'completed', label: '已完成', date: data.completedAt || data.doneTime || '' }
  ]
  // 根据状态确定当前步骤索引
  const statusStepMap = {
    'PENDING_PAY': 0, 'PAID': 1, 'PENDING_SHIP': 1,
    'SHIPPED': 2, 'IN_TRANSIT': 2,
    'DELIVERED': 3, 'RECEIVED': 3, 'COMPLETED': 3,
    'CANCELLED': -1, 'REFUNDING': -1, 'REFUNDED': -1
  }
  const stepIndex = statusStepMap[status]
  if (stepIndex === undefined || stepIndex < 0) {
    return { steps: allSteps, current: 0 }
  }
  return { steps: allSteps, current: stepIndex }
}

// 根据状态映射 statusClass
function mapStatusClass(status) {
  const s = String(status || '').toUpperCase()
  const map = {
    'PENDING_PAY': 'tag tag-yellow', 'PAID': 'tag tag-blue',
    'PENDING_SHIP': 'tag tag-blue', 'SHIPPED': 'tag tag-blue',
    'IN_TRANSIT': 'tag tag-blue', 'COMPLETED': 'tag tag-green',
    'DELIVERED': 'tag tag-green', 'RECEIVED': 'tag tag-green',
    'CANCELLED': 'tag tag-gray', 'REFUNDING': 'tag tag-orange',
    'REFUNDED': 'tag tag-gray'
  }
  return map[s] || 'tag tag-gray'
}

// 状态英文码 → 中文标签（与 mapStatusClass 保持一致）
function statusLabel(status) {
  const s = String(status || '').toUpperCase()
  const map = {
    'PENDING_PAY': '待支付', 'PAID': '已支付', 'PENDING_SHIP': '待发货',
    'SHIPPED': '已发货', 'IN_TRANSIT': '运输中', 'COMPLETED': '已完成',
    'DELIVERED': '已送达', 'RECEIVED': '已收货', 'CANCELLED': '已取消',
    'REFUNDING': '退款中', 'REFUNDED': '已退款'
  }
  return map[s] || status || '-'
}

// 生成基本操作日志（基于已有数据时间字段）
function buildOperationLogs(data) {
  const logs = []
  if (data.createTime) {
    logs.push({ action: '订单创建', operator: '系统', time: data.createTime, status: 'done' })
  }
  if (data.paidAt || data.payTime) {
    logs.push({ action: '支付完成', operator: '系统', time: data.paidAt || data.payTime, status: 'done' })
  }
  if (data.shippedAt || data.shipTime) {
    logs.push({ action: '订单发货', operator: '管理员', time: data.shippedAt || data.shipTime, status: 'done' })
  }
  if (data.completedAt || data.doneTime) {
    logs.push({ action: '订单完成', operator: '系统', time: data.completedAt || data.doneTime, status: 'done' })
  }
  // 如果状态为售后相关
  const status = String(data.status || '').toUpperCase()
  if (status === 'REFUNDING' || status === 'REFUNDED') {
    logs.push({ action: '发起退款', operator: '系统', time: data.updatedAt || data.refundTime || '', status: 'current' })
  }
  return logs
}
function formatMoney(v) {
  const n = Number(v)
  if (isNaN(n)) return '0.00'
  return n.toFixed(2)
}

// 获取订单详情
async function fetchOrderDetail() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getOrderDetail(id)
    if (res) {
      const data = res
      orderNo.value = data.orderNo || data.no || ''
      // 使用辅助函数生成进度步骤
      const progress = buildProgressSteps(data)
      currentStep.value = progress.current
      progressSteps.value = progress.steps
      orderItems.value = data.items || []
      // 使用辅助函数生成操作日志（优先使用后端返回的日志）
      operationLogs.value = data.operationLogs && data.operationLogs.length > 0
        ? data.operationLogs
        : buildOperationLogs(data)

      priceSummary.goodsAmount = data.goodsAmount || '0.00'
      priceSummary.freight = data.freight || '0.00'
      // 后端返回的字段：couponDiscount + pointsDiscount
      const discount = Number(data.couponDiscount || 0) + Number(data.pointsDiscount || 0)
      priceSummary.discount = discount > 0 ? discount.toFixed(2) : (data.discount || '0.00')
      // 实付金额优先用 payAmount
      priceSummary.total = data.payAmount || data.total || '0.00'

      Object.assign(orderInfo, {
        orderNo: data.orderNo || data.no || '',
        createTime: data.createTime || '',
        payTime: data.paidAt || data.payTime || '',
        payMethod: data.payChannel || data.payMethod || '',
        source: data.source || '',
        status: statusLabel(data.status),
        statusClass: mapStatusClass(data.status)
      })

      Object.assign(shippingInfo, {
        name: data.receiverName || data.shippingName || '',
        phone: data.receiverPhone || data.shippingPhone || '',
        address: data.receiverAddress || data.shippingAddress || ''
      })

      Object.assign(addressForm, {
        name: data.receiverName || data.shippingName || '',
        phone: data.receiverPhone || data.shippingPhone || '',
        address: data.receiverAddress || data.shippingAddress || ''
      })

      // WC 同步状态字段
      wooOrderId.value = data.wooOrderId || null
      syncStatus.value = typeof data.syncStatus === 'number' ? data.syncStatus : null
    }
  } catch (err) {
    console.error('获取订单详情失败:', err)
    ElMessage.error('获取订单详情失败')
  } finally {
    loading.value = false
  }
}

// 手动重推当前订单到 WooCommerce
async function handleSyncToWoo() {
  const id = route.params.id
  if (!id || syncingWoo.value) return
  syncingWoo.value = true
  try {
    const res = await syncOrderToWoo(id)
    const data = res?.data || res
    if (data?.wooOrderId) {
      ElMessage.success(`订单已同步到 WC，wooOrderId=${data.wooOrderId}`)
      wooOrderId.value = data.wooOrderId
      syncStatus.value = data.syncStatus ?? 0
    } else {
      ElMessage.warning(`同步失败：${data?.message || '未知原因'}`)
      syncStatus.value = -1
    }
  } catch (err) {
    console.error('同步订单到 WC 失败:', err)
    ElMessage.error('同步订单到 WC 失败：' + (err?.message || ''))
  } finally {
    syncingWoo.value = false
  }
}

async function handleShip() {
  const id = route.params.id
  if (!id) return
  try {
    await shipOrder(id)
    ElMessage.success('订单已确认发货')
    await fetchOrderDetail()
  } catch (err) {
    ElMessage.error('确认发货失败: ' + (err.response?.data?.message || err.message))
  }
}

function copyAddress() {
  navigator.clipboard.writeText(shippingInfo.address)
  ElMessage.success('地址已复制')
}

async function confirmAddress() {
  const id = route.params.id
  if (!id) return
  try {
    await updateOrderAddress(id, {
      shippingName: addressForm.name,
      shippingPhone: addressForm.phone,
      shippingAddress: addressForm.address
    })
    Object.assign(shippingInfo, addressForm)
    showAddressModal.value = false
    ElMessage.success('地址已修改')
  } catch (err) {
    ElMessage.error('修改地址失败: ' + (err.response?.data?.message || err.message))
  }
}

async function confirmNote() {
  const id = route.params.id
  if (!id) return
  try {
    await updateOrderRemark(id, { remark: noteContent.value })
    showNoteModal.value = false
    ElMessage.success('备注已保存')
  } catch (err) {
    ElMessage.error('保存备注失败: ' + (err.response?.data?.message || err.message))
  }
}

onMounted(() => {
  fetchOrderDetail()
})
</script>

<style scoped lang="css">
.order-detail {
  height: 100%;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px 0 20px;
}

.page-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0;
}

.page-subtitle {
  font-size: 13px;
  color: var(--text-400);
  font-weight: 500;
}

.page-header-actions {
  display: flex;
  gap: 8px;
}

/* 进度条 */
.order-progress {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 24px 0;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.progress-step {
  flex: 1;
  text-align: center;
  position: relative;
}

.progress-step::after {
  content: '';
  position: absolute;
  top: 16px;
  left: 50%;
  width: 100%;
  height: 2px;
  background: var(--background-200);
  z-index: 0;
}

.progress-step:last-child::after {
  display: none;
}

.progress-step.active::after {
  background: var(--primary);
}

.step-indicator {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

.step-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  background: var(--background-200);
  color: var(--text-400);
}

.progress-step.active .step-icon {
  background: var(--primary);
  color: #fff;
}

.progress-step.done .step-icon {
  background: var(--state-success);
  color: #fff;
}

.step-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-400);
  margin-bottom: 4px;
}

.progress-step.active .step-label {
  color: var(--primary);
}

.progress-step.done .step-label {
  color: var(--state-success);
}

.step-date {
  font-size: 11px;
  color: var(--text-400);
}

/* 两栏布局 */
.detail-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.detail-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-side {
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 产品缩略图 */
.product-thumb {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  background: var(--background-200);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: var(--text-400);
  flex-shrink: 0;
}

.item-count {
  font-size: 12px;
  color: var(--text-400);
}

.no-padding {
  padding: 0;
}

/* 价格汇总 */
.price-summary {
  margin-bottom: 0;
}

.price-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  color: var(--text-600);
}

.price-row.total {
  border-top: 1px solid var(--border);
  margin-top: 8px;
  padding-top: 12px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-800);
}

.total-amount {
  font-size: 18px;
  color: var(--state-error);
}

/* 时间线 */
.operation-timeline {
  position: relative;
  padding-left: 20px;
}

.operation-timeline::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 4px;
  bottom: 4px;
  width: 2px;
  background: var(--background-200);
}

.timeline-item {
  position: relative;
  padding-bottom: 20px;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-dot {
  position: absolute;
  left: -18px;
  top: 4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--background-200);
  border: 2px solid var(--background-200);
}

.timeline-dot.done {
  background: var(--state-success);
  border-color: var(--state-success-surface);
}

.timeline-dot.current {
  background: var(--primary);
  border-color: var(--brand-100);
}

.timeline-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-800);
}

.timeline-meta {
  font-size: 12px;
  color: var(--text-400);
  margin-top: 2px;
}

/* 信息卡 */
.info-card .info-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid var(--background-100);
  font-size: 13px;
}

.info-card .info-row:last-child {
  border-bottom: none;
}

.info-label {
  width: 72px;
  flex-shrink: 0;
  color: var(--text-400);
}

.info-value {
  flex: 1;
  color: var(--text-700);
  font-weight: 500;
}

.address-row .info-value {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.copy-btn {
  align-self: flex-start;
  padding: 2px 10px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--card);
  color: var(--primary);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
}

.copy-btn:hover {
  background: var(--brand-50);
}

/* 弹窗内表单 */
.form-group textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--input);
  border-radius: var(--radius-sm);
  background: var(--background);
  color: var(--foreground);
  font-size: 13px;
  outline: none;
  resize: vertical;
}

.form-group textarea:focus {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}

.form-group + .form-group {
  margin-top: 12px;
}
</style>
