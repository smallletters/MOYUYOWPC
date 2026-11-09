<template>
  <div class="order-detail">
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/orders">订单管理</router-link>
      <span class="separator">/</span>
      <span class="current">订单详情</span>
    </div>

    <!-- 订单不存在（如已被删除）时的空状态，避免详情页空白 -->
    <el-empty v-if="orderNotFound" description="订单不存在或已被删除">
      <button class="btn btn-primary" @click="router.push('/orders')">返回订单列表</button>
    </el-empty>

    <template v-else>
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
        <button v-if="canUseRefund" class="btn btn-danger" :disabled="!canRefund" @click="openRefundModal">退款</button>
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
                    <!-- 商品主图：优先用 item.mainImage；没有则回落为首字符占位图 -->
                    <img
                      v-if="item.mainImage"
                      :src="resolveImageUrl(item.mainImage)"
                      :alt="item.name || item.productName || '商品图'"
                      class="product-image"
                      @error="onImageError"
                    />
                    <div v-else class="product-thumb">{{ thumbChar(item) }}</div>
                    <div>
                      <div class="product-name">{{ item.name || item.productName || '商品' }}</div>
                      <!-- 变体商品：skuSpec 已有规格文本（颜色/尺码），放到名称下面再提醒一次 -->
                      <div v-if="item.skuSpec" class="sku-spec-tag">{{ item.skuSpec }}</div>
                    </div>
                  </div>
                </td>
                <!-- SKU 列：优先展示后端填充的 skuCode（mo_product_sku.sku_code），其次兼容旧字段 skuSpec（规格名），再其次才是 sku id -->
                <td>
                  <div v-if="item.skuCode" class="sku-code">{{ item.skuCode }}</div>
                  <div v-else-if="item.skuSpec" class="sku-spec-text">{{ item.skuSpec }}</div>
                  <span v-else class="dash">-</span>
                </td>
                <td><span class="money">{{ formatCurrency(item.price, currencySymbol) }}</span></td>
                <td>{{ item.qty ?? item.quantity ?? 0 }}</td>
                <td><span class="money">{{ formatCurrency(item.subtotal, currencySymbol) }}</span></td>
              </tr>
            </tbody>
            </table>
          </div>
        </div>

        <!-- 价格汇总 -->
        <div class="price-summary card">
          <div class="card-body">
            <div class="price-row"><span>商品金额</span><span class="money">{{ formatCurrency(priceSummary.goodsAmount, currencySymbol) }}</span></div>
            <div class="price-row"><span>运费</span><span class="money">{{ formatCurrency(priceSummary.freight, currencySymbol) }}</span></div>
            <div class="price-row"><span>优惠减免</span><span class="money">-{{ formatCurrency(priceSummary.discount, currencySymbol) }}</span></div>
            <!-- 已发生退款时展示退款金额与剩余可退金额（对齐 WooCommerce 订单详情） -->
            <div v-if="refundInfo.refundedAmount > 0" class="price-row refunded-row">
              <span>已退款</span><span class="money refund-amount">-{{ formatCurrency(refundInfo.refundedAmount, currencySymbol) }}</span>
            </div>
            <div v-if="refundInfo.refundedAmount > 0" class="price-row">
              <span>剩余可退</span><span class="money">{{ formatCurrency(refundInfo.refundableAmount, currencySymbol) }}</span>
            </div>
            <div class="price-row total"><span>实付金额</span><span class="money total-amount">{{ formatCurrency(priceSummary.total, currencySymbol) }}</span></div>
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
                  <div class="timeline-meta">{{ log.operator || '-' }} · {{ log.time }}</div>
                  <div v-if="log._detail" class="timeline-detail">{{ log._detail }}</div>
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
            <div class="info-row"><span class="info-label">支付时间</span><span class="info-value">{{ orderInfo.payTime || '尚未支付' }}</span></div>
            <div class="info-row">
              <span class="info-label">支付方式</span>
              <span class="info-value">
                <!-- 优先用后端返回的 payMethodName（友好名），没有就用 payChannel 原始值 -->
                <span v-if="orderInfo.payMethodName" class="pay-method-label">{{ orderInfo.payMethodName }}</span>
                <span v-else-if="orderInfo.payChannel" class="pay-method-label">{{ orderInfo.payChannel }}</span>
                <span v-else class="pay-method-label pending">待支付</span>
              </span>
            </div>
            <div class="info-row"><span class="info-label">订单来源</span><span class="info-value">{{ orderInfo.source || '—' }}</span></div>
            <div class="info-row"><span class="info-label">币种</span><span class="info-value">{{ orderInfo.currency || 'USD' }}</span></div>
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

    <!-- 退款弹窗（对齐 WooCommerce 退款操作） -->
    <div v-if="showRefundModal" class="modal-overlay" @click.self="closeRefundModal">
      <div class="modal-content refund-modal">
        <div class="modal-header">
          <h3>订单退款</h3>
          <button class="modal-close" @click="closeRefundModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="refund-summary">
            <div class="refund-summary-item"><span>订单实付</span><strong>{{ formatCurrency(priceSummary.total, currencySymbol) }}</strong></div>
            <div class="refund-summary-item"><span>已退款</span><strong class="refund-amount">{{ formatCurrency(refundInfo.refundedAmount, currencySymbol) }}</strong></div>
            <div class="refund-summary-item"><span>剩余可退</span><strong>{{ formatCurrency(refundInfo.refundableAmount, currencySymbol) }}</strong></div>
          </div>
          <div class="form-group">
            <label>退款金额</label>
            <input
              v-model="refundForm.amount"
              type="number"
              min="0.01"
              step="0.01"
              :max="refundInfo.refundableAmount"
              placeholder="请输入退款金额"
            />
          </div>
          <div class="form-group">
            <label>退款原因</label>
            <select v-model="refundForm.reason">
              <option v-for="r in refundReasons" :key="r" :value="r">{{ r }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>退款备注（可选）</label>
            <textarea v-model="refundForm.description" rows="2" placeholder="补充说明，将写入退款单备注"></textarea>
          </div>
          <div class="form-group">
            <label>退款方式</label>
            <div class="refund-mode-list">
              <label class="refund-mode-item" :class="{ active: refundForm.mode === 'AUTO' }">
                <input type="radio" value="AUTO" v-model="refundForm.mode" />
                <span>自动原路退回（{{ orderInfo.payMethodName || orderInfo.payChannel || '原支付渠道' }}）</span>
              </label>
              <label class="refund-mode-item" :class="{ active: refundForm.mode === 'MANUAL' }">
                <input type="radio" value="MANUAL" v-model="refundForm.mode" />
                <span>手动录入流水号（线下已退款）</span>
              </label>
            </div>
          </div>
          <div v-if="refundForm.mode === 'MANUAL'" class="form-group">
            <label>第三方退款流水号</label>
            <input v-model="refundForm.transactionId" placeholder="例如 Stripe / PayPal 后台的退款交易号" />
          </div>
          <div class="refund-hint">
            自动退款会真实调用支付渠道接口，提交后立即发起退款且不可撤销；渠道退款失败时本次操作整体回滚。
          </div>
          <div v-if="refundInfo.refunds.length" class="refund-history">
            <div class="refund-history-title">退款记录</div>
            <div class="refund-history-item" v-for="r in refundInfo.refunds" :key="r.id">
              <span class="refund-no">{{ r.refundNo }}</span>
              <span class="refund-history-amount">{{ formatCurrency(r.amount, currencySymbol) }}</span>
              <span class="refund-status" :class="'is-' + String(r.status || '').toLowerCase()">{{ refundStatusLabel(r.status) }}</span>
              <span class="refund-history-time">{{ formatTimelineTime(r.createTime) }}</span>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="closeRefundModal">取消</button>
          <button class="btn btn-danger" :disabled="refundSubmitting" @click="submitRefund">
            {{ refundSubmitting ? '退款中…' : '确认退款' }}
          </button>
        </div>
      </div>
    </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, shipOrder, updateOrderAddress, updateOrderRemark, syncOrderToWoo,
  refundOrder, getOrderTimeline } from '../api/admin'
import { getAdminInfo } from '../api/auth'

const router = useRouter()
const route = useRoute()

const currentStep = ref(0)
const orderNo = ref('')
const loading = ref(false)
// 订单不存在/已删除标记：为 true 时展示空状态而不是空白页
const orderNotFound = ref(false)

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
  payMethod: '',    // 原始 payChannel（保留兼容旧引用）
  payChannel: '',
  payMethodName: '', // 后端填充的友好名
  currency: 'USD',
  source: '',
  status: '',
  statusCode: '',   // 原始英文状态码，用于退款等业务判断
  statusClass: ''
})

// 当前订单的货币符号（优先用后端返回的 currency，没有则 USD=$）
const currencySymbol = computed(() => currencyToSymbol(orderInfo.currency || 'USD'))

// 收货信息
const shippingInfo = reactive({
  name: '',
  phone: '',
  address: ''
})

const showAddressModal = ref(false)
const showNoteModal = ref(false)
const noteContent = ref('')

// 退款相关状态（对齐 WooCommerce 订单详情退款弹窗）
const showRefundModal = ref(false)
const refundSubmitting = ref(false)
const currentRole = ref('')  // 当前登录管理员角色，用于退款按钮权限控制
// 允许发起退款的角色（与后端 AdminPermissionFilter.REFUND_ALLOWED_ROLES 保持一致）
const REFUND_ALLOWED_ROLES = ['SUPER_ADMIN', 'OPERATOR', 'FINANCE']
const refundReasons = ['买家申请退款', '商品缺货', '商品损坏/质量问题', '重复下单', '买家未收到货', '其他']
const refundForm = reactive({
  amount: '',
  reason: refundReasons[0],
  description: '',
  mode: 'AUTO',       // AUTO=自动原路退回，MANUAL=手动录入线下流水号
  transactionId: ''
})
// 退款汇总（来自订单详情接口）
const refundInfo = reactive({
  refundedAmount: 0,
  refundableAmount: 0,
  refunds: []
})

// 仅超管/运营/财务可发起退款（按钮可见性）
const canUseRefund = computed(() => REFUND_ALLOWED_ROLES.includes(currentRole.value))

// 可退款订单状态（与后端 RefundServiceImpl.adminRefundOrder 保持一致）
// REFUNDED 兜底：历史部分退款可能把订单置为已退款，但仍有剩余可退金额
const REFUNDABLE_STATUS = ['PAID', 'PENDING_SHIP', 'SHIPPED', 'RECEIVED', 'COMPLETED', 'REFUNDED']
const canRefund = computed(() => {
  if (!canUseRefund.value) return false
  if (refundSubmitting.value) return false
  if (!REFUNDABLE_STATUS.includes(orderInfo.statusCode)) return false
  return Number(refundInfo.refundableAmount) > 0
})

// 退款单状态 → 中文标签
function refundStatusLabel(status) {
  const map = {
    PENDING: '待处理', APPROVED: '已批准', PROCESSING: '退款中',
    COMPLETED: '已完成', REJECTED: '已拒绝'
  }
  return map[String(status || '').toUpperCase()] || status || '-'
}

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

/**
 * ISO 4217 货币代码 → 符号映射
 * 美国市场主要用 USD=$，同时兼容 CNY=¥ 等未来多币种场景
 */
function currencyToSymbol(code) {
  switch ((code || 'USD').toUpperCase()) {
    case 'USD': case 'AUD': case 'CAD': case 'NZD': case 'HKD': case 'SGD': return '$'
    case 'CNY': case 'RMB': case 'JPY': return '¥'
    case 'EUR': return '€'
    case 'GBP': return '£'
    case 'KRW': return '₩'
    default: return code.toUpperCase() + ' '
  }
}

/**
 * 金额格式化（带货币符号，toFixed(2)）
 * 例： formatCurrency(74, '$') → '$74.00'
 */
function formatCurrency(v, symbol) {
  const n = Number(v)
  const fixed = isNaN(n) ? '0.00' : n.toFixed(2)
  const s = symbol || currencyToSymbol(orderInfo.currency)
  return `${s}${fixed}`
}

/**
 * 解析商品图片 URL：
 * - 已经是 http(s)://  → 直接用
 * - 以 /uploads/ 开头 → 拼接后端 base 地址（VITE_BASE_URL/api）
 * - 其他相对路径    → VITE_BASE_URL/api 拼接
 */
function resolveImageUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  const base = (import.meta.env.VITE_BASE_URL || '/api').replace(/\/$/, '')
  // Windows 路径不用处理，生产部署统一用 /uploads/xxx
  if (path.startsWith('/')) return `${base}${path}`
  return `${base}/${path}`
}

/** 图片加载失败：静默隐藏，避免红叉图影响后台视觉 */
function onImageError(e) {
  if (e?.target) e.target.style.display = 'none'
}

// 获取订单详情
async function fetchOrderDetail() {
  const id = route.params.id
  if (!id) {
    orderNotFound.value = true
    return
  }
  orderNotFound.value = false
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
      // 优先用后端 /orders/{id}/timeline 接口（聚合 5 张表），
      //   接口失败 / 404 时降级到本地 buildOperationLogs。
      // 传入 data 是为了降级时能用完整的原始订单数据构造时间轴。
      await loadTimeline(data)

      priceSummary.goodsAmount = data.goodsAmount || '0.00'
      priceSummary.freight = data.freight || '0.00'
      // 后端返回的字段：memberDiscount + couponDiscount + pointsDiscount
      const discount = Number(data.memberDiscount || 0) + Number(data.couponDiscount || 0) + Number(data.pointsDiscount || 0)
      priceSummary.discount = discount > 0 ? discount.toFixed(2) : (data.discount || '0.00')
      // 实付金额优先用 payAmount
      priceSummary.total = data.payAmount || data.total || '0.00'

      Object.assign(orderInfo, {
        orderNo: data.orderNo || data.no || '',
        createTime: data.createTime || '',
        payTime: data.paidAt || data.payTime || '',
        // 支付方式同时保留：原始渠道(payChannel) + 后端友好名(payMethodName)
        payChannel: data.payChannel || '',
        payMethod: data.payChannel || data.payMethod || '',
        payMethodName: data.payMethodName || '',
        currency: data.currency || 'USD',
        source: data.source || '',
        status: statusLabel(data.status),
        statusCode: String(data.status || '').toUpperCase(),
        statusClass: mapStatusClass(data.status)
      })

      // 退款汇总：后端返回 refundedAmount / refundableAmount / refunds
      refundInfo.refundedAmount = Number(data.refundedAmount || 0)
      refundInfo.refundableAmount = data.refundableAmount != null
        ? Number(data.refundableAmount)
        : Math.max(0, Number(data.payAmount || 0) - refundInfo.refundedAmount)
      refundInfo.refunds = Array.isArray(data.refunds) ? data.refunds : []

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
    // 订单不存在（如已被软删除）时展示空状态，避免详情页完全空白
    if (err?.code === 404) {
      orderNotFound.value = true
    } else {
      ElMessage.error('获取订单详情失败')
    }
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

// 打开退款弹窗：默认按剩余可退金额填充
function openRefundModal() {
  if (!canRefund.value) return
  refundForm.amount = Number(refundInfo.refundableAmount).toFixed(2)
  refundForm.reason = refundReasons[0]
  refundForm.description = ''
  refundForm.mode = 'AUTO'
  refundForm.transactionId = ''
  showRefundModal.value = true
}

function closeRefundModal() {
  if (refundSubmitting.value) return
  showRefundModal.value = false
}

// 提交退款：AUTO 模式会真实调用支付渠道退款接口
async function submitRefund() {
  const id = route.params.id
  if (!id || refundSubmitting.value) return

  const amount = Number(refundForm.amount)
  if (!(amount > 0)) {
    ElMessage.warning('请输入大于 0 的退款金额')
    return
  }
  if (amount > Number(refundInfo.refundableAmount) + 1e-6) {
    ElMessage.warning(`退款金额不能超过剩余可退 ${formatCurrency(refundInfo.refundableAmount, currencySymbol.value)}`)
    return
  }
  if (refundForm.mode === 'MANUAL' && !refundForm.transactionId.trim()) {
    ElMessage.warning('手动退款必须填写第三方退款流水号')
    return
  }
  // AUTO 是内部自动退款的保留值，手动模式下禁止使用，避免被后端误判为自动退款
  if (refundForm.mode === 'MANUAL' && refundForm.transactionId.trim().toUpperCase() === 'AUTO') {
    ElMessage.warning('第三方退款流水号不能填写 AUTO，请填写渠道后台的真实退款交易号')
    return
  }

  const confirmText = refundForm.mode === 'AUTO'
    ? `确认通过「${orderInfo.payMethodName || orderInfo.payChannel || '原支付渠道'}」原路退回 ${formatCurrency(amount, currencySymbol.value)}？该操作会真实调用支付渠道接口。`
    : `确认登记手动退款 ${formatCurrency(amount, currencySymbol.value)}？请确保已在线下完成退款。`
  try {
    await ElMessageBox.confirm(confirmText, '确认退款', {
      type: 'warning',
      confirmButtonText: '确认退款',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }

  refundSubmitting.value = true
  try {
    const res = await refundOrder(id, {
      amount,
      reason: refundForm.reason,
      description: refundForm.description,
      mode: refundForm.mode,
      transactionId: refundForm.mode === 'MANUAL' ? refundForm.transactionId.trim() : null
    })
    const data = res?.data || res || {}
    ElMessage.success(`退款成功：${data.refundNo || ''}，渠道流水号 ${data.transactionId || '-'}`)
    showRefundModal.value = false
    await fetchOrderDetail()
  } catch (err) {
    ElMessage.error('退款失败: ' + (err.response?.data?.message || err.message))
  } finally {
    refundSubmitting.value = false
  }
}

// 加载当前管理员角色：仅超管/运营/财务展示退款按钮
async function loadCurrentRole() {
  try {
    const info = await getAdminInfo()
    currentRole.value = String(info?.role || '').toUpperCase()
  } catch (err) {
    // 角色获取失败按无权限处理，前端不展示退款入口（后端仍会二次校验）
    currentRole.value = ''
    console.warn('获取管理员角色失败:', err?.message || err)
  }
}

onMounted(() => {
  loadCurrentRole()
  fetchOrderDetail()
})

// 加载订单时间轴（聚合 5 张表事件），失败时降级到本地 buildOperationLogs
// 参数 fallbackData：调用方传入完整订单 data，用于失败时本地构造完整时间轴。
async function loadTimeline(fallbackData) {
  // 注意：orderInfo.id 此时可能还没赋值（fetchOrderDetail 中
  //   Object.assign(orderInfo, ...) 在 await loadTimeline 之后），
  //   这里从路由参数取订单 ID，确保能拿到。
  const id = route.params.id
  // 降级源：fallbackData（传入的原始 data）> orderInfo > {}
  const fallback = fallbackData || orderInfo || {}
  if (!id) {
    operationLogs.value = buildOperationLogs(fallback)
    return
  }
  try {
    const res = await getOrderTimeline(id)
    // 后端返回结构：{ events: [...], total, limit, offset }
    const data = res?.data || res || {}
    const events = data.events || []
    // 后端返回 events 元素 { time, type, title, detail }，映射为 operationLogs 的字段格式
    operationLogs.value = events.map(e => ({
      action: e.title || e.type || '-',
      operator: '',
      time: formatTimelineTime(e.time),
      status: mapTimelineTypeToStatus(e.type),
      // 原始字段（便于前端需要 detail 时取用）
      _type: e.type,
      _detail: e.detail
    }))
    // 后端无事件（如全新订单） → 用本地构造兜底
    if (operationLogs.value.length === 0) {
      operationLogs.value = buildOperationLogs(fallback)
    }
  } catch (e) {
    console.warn('加载订单时间轴失败，降级到本地操作日志：', e?.message || e)
    operationLogs.value = buildOperationLogs(fallback)
  }
}

// 后端返回的 time 可能是以下两种格式：
//   1) ISO 字符串："2026-09-29T10:30:00"（Jackson 默认 write-dates-as-timestamps=false）
//   2) Timestamp.toString()："2026-09-29 10:30:00.0"（某些边缘配置）
// 这里统一渲染成 "YYYY-MM-DD HH:mm:ss" 形式（去掉毫秒、T 分隔符）。
function formatTimelineTime(t) {
  if (!t) return ''
  let str = String(t)
  // 截掉末尾微秒（.0 或 .000）
  str = str.replace(/\.0+(\s|$)/, '$1')
  // ISO 8601 的 T 分隔符替换为空格（更符合中文用户阅读习惯）
  str = str.replace('T', ' ')
  return str
}

// 后端 event_type → 现有 timeline-dot 的状态类（done/current/...）
//   - done（绿色）：所有"已发生的历史事件"，包括订单生命周期和运营动作。
//   - current（蓝色）：仅保留给仍处于活动态的事件（INTERCEPT_ACTIVE 未解除时）。
//   拦截点对调是因为时间轴所有事件都是过去时点，不应大量出现 current 标记。
function mapTimelineTypeToStatus(type) {
  if (!type) return ''
  // 进行中的活动事件（拦截还未解除）—— 用蓝色强调
  if (type === 'INTERCEPT_ACTIVE') {
    return 'current'
  }
  // 其余全部算"已完成的历史动作"，绿色统一标识
  // 包含：订单生命周期（CREATED/PAID/SHIPPED/RECEIVED）+ 物流（LOGISTICS_*）
  //       + 改价（PRICE_MODIFIED）+ 打印（PRINT_RECORD）+ 拦截解除（INTERCEPT_RELEASED）
  return 'done'
}
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

/* 商品主图：后台展示 48x48 缩略图，与旧占位图视觉一致 */
.product-image {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--background-100);
  border: 1px solid var(--border);
  display: block;
}

/* 变体商品规格名（颜色/尺码）：放在商品名下面的灰小字 tag */
.sku-spec-tag {
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-400);
  line-height: 1.4;
}

/* SKU 编码：等宽字体，灰色背景胶囊，方便运营复制核对库存 */
.sku-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-700);
  background: var(--background-100);
  border: 1px solid var(--border);
  padding: 2px 8px;
  border-radius: 999px;
  display: inline-block;
}
.sku-spec-text {
  font-size: 12px;
  color: var(--text-500);
}
.dash {
  color: var(--text-300);
}

/* 支付方式标签：不同友好名加颜色区分，后台一眼识别渠道 */
.pay-method-label {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid var(--border);
  background: var(--background-100);
  color: var(--text-700);
}
.pay-method-label.pending {
  color: #b45309;
  background: #fff7ed;
  border-color: #fdba74;
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
.timeline-detail {
  font-size: 12px;
  color: var(--text-500);
  margin-top: 4px;
  padding: 4px 8px;
  background: var(--background-100, #f5f7fa);
  border-radius: 4px;
  border-left: 2px solid var(--brand-300, #93c5fd);
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

/* ===== 退款弹窗（对齐 WooCommerce 退款操作） ===== */
.refund-modal {
  max-width: 640px;
}

.refund-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--background);
}

.refund-summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--text-500);
}

.refund-summary-item strong {
  font-size: 15px;
  color: var(--text-900);
}

.refund-amount {
  color: var(--state-error) !important;
}

.refund-mode-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.refund-mode-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.refund-mode-item.active {
  border-color: var(--state-error);
  background: var(--background-100);
}

.refund-hint {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--background-100);
  color: var(--text-500);
  font-size: 12px;
  line-height: 1.6;
}

.refund-history {
  margin-top: 16px;
  border-top: 1px solid var(--border);
  padding-top: 12px;
}

.refund-history-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.refund-history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  font-size: 12px;
  color: var(--text-600);
  border-bottom: 1px dashed var(--border);
}

.refund-history-item:last-child {
  border-bottom: none;
}

.refund-no {
  font-family: monospace;
  color: var(--text-800);
}

.refund-history-amount {
  color: var(--state-error);
}

.refund-status {
  padding: 1px 8px;
  border-radius: 10px;
  background: var(--background-100);
}

.refund-status.is-completed {
  color: #16a34a;
  background: #dcfce7;
}

.refund-status.is-rejected {
  color: var(--state-error);
  background: #fee2e2;
}

.refund-history-time {
  margin-left: auto;
  color: var(--text-400);
}

.refunded-row .money {
  color: var(--state-error);
}
</style>
