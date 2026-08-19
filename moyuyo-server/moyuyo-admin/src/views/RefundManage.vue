<template>
  <div class="refund-manage">
    <h2 class="page-title">退款管理</h2>

    <!-- KPI 卡片 -->
    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-value orange">{{ kpiData.pending }}</div>
        <div class="kpi-label">待处理</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ kpiData.todayAmount }}</div>
        <div class="kpi-label">今日退款金额</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ kpiData.refundRate }}</div>
        <div class="kpi-label">退款率</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ kpiData.avgProcessTime }}</div>
        <div class="kpi-label">平均处理时长</div>
      </div>
    </div>

    <!-- 类型切换 -->
    <div class="tab-switcher">
      <button
        v-for="type in refundTypes"
        :key="type.key"
        class="tab-switcher-item"
        :class="{ active: activeType === type.key }"
        @click="activeType = type.key"
      >
        {{ type.label }}
      </button>
    </div>

    <!-- 状态切换 -->
    <div class="status-switcher">
      <button
        v-for="s in statusFilters"
        :key="s.key"
        class="status-chip"
        :class="{ active: activeStatus === s.key }"
        @click="activeStatus = s.key"
      >
        {{ s.label }}
        <span class="count" v-if="statusCount[s.key] !== undefined">{{ statusCount[s.key] }}</span>
      </button>
    </div>

    <!-- 退款卡片列表 -->
    <div class="refund-list" v-loading="loading">
      <div class="refund-card" v-for="item in refunds" :key="item.id">
        <div class="refund-top">
          <div class="refund-info">
            <span class="refund-no table-link" @click="handleDetail(item)">{{ item.refundNo }}</span>
            <span class="refund-order">订单 {{ item.orderId }}</span>
          </div>
          <div class="refund-sla" :class="item.slaClass">
            <span class="sla-icon">{{ item.slaIcon }}</span>
            {{ item.slaLabel }}
          </div>
        </div>
        <div class="refund-body">
          <div class="refund-product">
            <div class="refund-thumb">📦</div>
            <div class="refund-meta">
              <div class="refund-type">{{ typeLabel(item.type) }}</div>
              <div class="refund-time">{{ item.createTime }}</div>
            </div>
          </div>
          <div class="refund-amount">
            <span class="amount-label">退款金额</span>
            <span class="amount-value">¥{{ item.amount }}</span>
          </div>
          <div class="refund-status">
            <span class="tag" :class="item.statusClass">{{ item.statusLabel }}</span>
          </div>
        </div>
        <div class="refund-actions" v-if="item.statusLabel === '待处理'">
          <button class="btn btn-sm btn-primary" @click="handleApprove(item.id)">同意</button>
          <button class="btn btn-sm btn-outline" @click="handleReject(item.id)">拒绝</button>
          <button class="btn btn-sm btn-outline" @click="handleDetail(item)">详情</button>
        </div>
        <!-- 阻塞项 #1 闭环：APPROVED 状态下需要财务录入退款流水号完成退款 -->
        <div class="refund-actions" v-else-if="item.statusLabel === '已批准'">
          <button class="btn btn-sm btn-primary" @click="handleComplete(item.id)">完成退款</button>
          <button class="btn btn-sm btn-outline" @click="handleDetail(item)">详情</button>
        </div>
        <div class="refund-actions" v-else>
          <button class="btn btn-sm btn-outline" @click="handleDetail(item)">查看详情</button>
        </div>
      </div>
      <el-empty v-if="!loading && refunds.length === 0" description="暂无退款记录" />
    </div>

    <!-- 批量操作栏 -->
    <div class="batch-bar">
      <label>
        <input type="checkbox" v-model="selectAll" @change="toggleSelectAll" />
        全选
      </label>
      <span>已选 {{ selectedCount }} 项</span>
      <button class="btn btn-sm btn-primary" :disabled="selectedCount === 0" @click="batchApprove">批量同意</button>
    </div>

    <!-- 退款原因分布 -->
    <div class="card">
      <div class="card-header">
        <h3>退款原因分布</h3>
      </div>
      <div class="card-body">
        <div class="reason-dist" v-if="reasonDist.length > 0">
          <div class="reason-bar-item" v-for="r in reasonDist" :key="r.label">
            <div class="reason-bar-header">
              <span>{{ r.label }}</span>
              <span>{{ r.percent }}% ({{ r.count }})</span>
            </div>
            <div class="bar-track">
              <div class="bar-fill-rd" :style="{ width: r.percent + '%', background: r.color }"></div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>
    </div>

    <!-- 详情弹窗：展示用户举证图片、拒绝原因、流水号等 -->
    <el-dialog
      v-model="detailVisible"
      :title="`退款详情 - ${detail.refundNo || ''}`"
      width="640px"
      :close-on-click-modal="false"
    >
      <div class="detail-content" v-loading="detailLoading">
        <div class="detail-row">
          <span class="detail-label">退款单号</span>
          <span class="detail-value">{{ detail.refundNo }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">关联订单</span>
          <span class="detail-value">{{ detail.orderId }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">退款类型</span>
          <span class="detail-value">{{ typeLabel(detail.type) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">退款金额</span>
          <span class="detail-value highlight">¥{{ detail.amount }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">退款原因</span>
          <span class="detail-value">{{ detail.reason || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">用户描述</span>
          <span class="detail-value">{{ detail.description || '（无）' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <span class="detail-value">{{ statusLabel(detail.status) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">申请时间</span>
          <span class="detail-value">{{ detail.createTime || '-' }}</span>
        </div>
        <div class="detail-row" v-if="detail.completeTime">
          <span class="detail-label">完成时间</span>
          <span class="detail-value">{{ detail.completeTime }}</span>
        </div>
        <div class="detail-row" v-if="detail.transactionId">
          <span class="detail-label">第三方流水号</span>
          <span class="detail-value highlight">{{ detail.transactionId }}</span>
        </div>
        <div class="detail-row" v-if="detail.rejectReason">
          <span class="detail-label">拒绝原因</span>
          <span class="detail-value rejected">{{ detail.rejectReason }}</span>
        </div>
        <div class="detail-row" v-if="detail.rejectTime">
          <span class="detail-label">拒绝时间</span>
          <span class="detail-value">{{ detail.rejectTime }}</span>
        </div>
        <div class="detail-section" v-if="parsedImages.length > 0">
          <div class="detail-label">用户举证图片</div>
          <div class="evidence-grid">
            <el-image
              v-for="(img, idx) in parsedImages"
              :key="idx"
              :src="img"
              :preview-src-list="parsedImages"
              :initial-index="idx"
              fit="cover"
              class="evidence-image"
            >
              <template #error>
                <div class="image-error">加载失败</div>
              </template>
            </el-image>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRefundStats, getRefundList, getRefundDetail, getRefundReasonDistribution, getRefundStatusCount, approveRefund, rejectRefund, completeRefund, batchApproveRefund } from '../api/admin'
import { toArray } from '../utils/safeArray'

// #4：修复类型筛选 — 前端传入类型枚举，后端按 type/status 路由
const refundTypes = [
  { key: 'all', label: '全部' },
  { key: 'refund_only', label: '仅退款' },
  { key: 'refund_return', label: '退货退款' },
  { key: 'exchange', label: '换货' }
]

// #6：新增状态维度切换
const statusFilters = [
  { key: '', label: '全部状态' },
  { key: 'PENDING', label: '待处理' },
  { key: 'APPROVED', label: '已批准' },
  { key: 'COMPLETED', label: '已完成' },
  { key: 'REJECTED', label: '已拒绝' }
]

const activeType = ref('all')
const activeStatus = ref('')
const selectAll = ref(false)
const selectedItems = ref(new Set())
const loading = ref(false)
const refunds = ref([])

// 详情弹窗状态
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})

// KPI 数据
const kpiData = ref({
  pending: 0,
  todayAmount: '¥0',
  refundRate: '0%',
  avgProcessTime: '0h'
})

// 退款原因分布
const reasonDist = ref([])

// 各状态计数（用于 chip 显示）
const statusCount = ref({})

// 类型枚举 → 中文
const TYPE_MAP = {
  REFUND_ONLY: '仅退款',
  REFUND_RETURN: '退货退款',
  EXCHANGE: '换货'
}

// 状态枚举 → 中文
const STATUS_MAP = {
  PENDING: '待处理',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
  COMPLETED: '已完成',
  REFUNDING: '退款中'
}

const STATUS_CLASS_MAP = {
  PENDING: 'tag-orange',
  APPROVED: 'tag-blue',
  REJECTED: 'tag-red',
  COMPLETED: 'tag-green',
  REFUNDING: 'tag-blue'
}

function typeLabel(type) {
  return TYPE_MAP[type] || type || '-'
}

function statusLabel(status) {
  return STATUS_MAP[status] || status || '-'
}

// #7：SLA 倒计时 — 基于 create_time + 24h 阈值实时计算
function calcSla(createTime, status) {
  if (!createTime || status === 'COMPLETED' || status === 'REJECTED') {
    return { slaLabel: STATUS_MAP[status] || '已处理', slaClass: 'done', slaIcon: '✅' }
  }
  if (status === 'APPROVED' || status === 'REFUNDING') {
    return { slaLabel: '退款中', slaClass: 'normal', slaIcon: '⏳' }
  }
  const created = new Date(createTime).getTime()
  if (isNaN(created)) {
    return { slaLabel: '待处理', slaClass: 'normal', slaIcon: '⏰' }
  }
  const deadline = created + 24 * 60 * 60 * 1000
  const remainMs = deadline - Date.now()
  if (remainMs <= 0) {
    return { slaLabel: '已超时', slaClass: 'urgent', slaIcon: '⚠️' }
  }
  const hours = Math.floor(remainMs / (60 * 60 * 1000))
  const mins = Math.floor((remainMs % (60 * 60 * 1000)) / (60 * 1000))
  // < 4h 视为紧急
  const isUrgent = remainMs < 4 * 60 * 60 * 1000
  return {
    slaLabel: `剩余 ${hours}h${mins.toString().padStart(2, '0')}m`,
    slaClass: isUrgent ? 'urgent' : 'normal',
    slaIcon: isUrgent ? '⚠️' : '⏰'
  }
}

// 解析 images 字段（支持 JSON 数组或逗号分隔字符串）
const parsedImages = computed(() => {
  const raw = detail.value.images
  if (!raw) return []
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(arr) ? arr.filter(Boolean) : []
  } catch (e) {
    return raw.split(',').map(s => s.trim()).filter(Boolean)
  }
})

// 获取退款统计数据
async function fetchStats() {
  try {
    const res = await getRefundStats()
    if (res) {
      kpiData.value = {
        pending: res.pendingCount || 0,
        todayAmount: res.todayAmount ? '¥' + res.todayAmount : '¥0',
        refundRate: res.refundRate || '0%',
        avgProcessTime: res.avgProcessTime || '0h'
      }
    }
  } catch (err) {
    console.error('获取退款统计数据失败:', err)
  }
}

// 前端 type key → 后端 type 枚举的映射
const TYPE_KEY_TO_ENUM = {
  refund_only: 'REFUND_ONLY',
  refund_return: 'REFUND_RETURN',
  exchange: 'EXCHANGE'
}

// 把前端当前 activeType 翻译成后端 type 参数；'all' 返回空字符串（不过滤）
function currentTypeParam() {
  return TYPE_KEY_TO_ENUM[activeType.value] || ''
}

// 获取退款列表（只受 status + type 过滤影响）
async function fetchRefunds() {
  loading.value = true
  try {
    const params = {
      page: 1,
      size: 50,
      status: activeStatus.value || '',
      type: currentTypeParam()
    }
    if (!params.status) delete params.status
    if (!params.type) delete params.type
    const res = await getRefundList(params)
    const list = toArray(res)
    refunds.value = list.map(item => {
      const sla = calcSla(item.createTime, item.status)
      return {
        id: item.id,
        refundNo: item.refundNo || 'REF' + item.id,
        orderId: item.orderId || '',
        type: item.type || '',
        reasonLabel: item.reason || '其他',
        reasonClass: 'tag-orange',
        amount: item.amount || 0,
        statusLabel: STATUS_MAP[item.status] || item.status || '其他',
        statusClass: STATUS_CLASS_MAP[item.status] || '',
        createTime: item.createTime || '',
        slaLabel: sla.slaLabel,
        slaClass: sla.slaClass,
        slaIcon: sla.slaIcon,
        thumb: '📦'
      }
    })
  } catch (err) {
    console.error('获取退款列表失败:', err)
    ElMessage.error('获取退款列表失败')
  } finally {
    loading.value = false
  }
}

// 拉取按 type 维度精确的状态计数（用于 chip 角标）
// type 为空表示统计所有类型；计数仅与 type 维度相关，与 status 维度无关
async function fetchStatusCount() {
  try {
    const typeParam = currentTypeParam()
    const params = {}
    if (typeParam) params.type = typeParam
    const res = await getRefundStatusCount(params)
    if (res) {
      statusCount.value = {
        PENDING: Number(res.PENDING || 0),
        APPROVED: Number(res.APPROVED || 0),
        COMPLETED: Number(res.COMPLETED || 0),
        REJECTED: Number(res.REJECTED || 0)
      }
    }
  } catch (err) {
    // 降级：用空对象占位，UI 不会崩溃；后台日志可见，便于运维排查
    console.warn('获取退款状态计数失败:', err)
  }
}

// 获取退款原因分布
async function fetchReasonDist() {
  try {
    const res = await getRefundReasonDistribution()
    const distList = toArray(res)
    if (distList.length > 0) {
      const total = distList.reduce((sum, r) => sum + (r.count || 0), 0)
      const colors = ['#e74c3c', '#f39c12', '#2ecc71', '#3498db', '#9b59b6', '#1abc9c']
      reasonDist.value = distList.map((r, i) => ({
        label: r.reason || '其他',
        count: r.count || 0,
        percent: total > 0 ? Math.round((r.count / total) * 100) : 0,
        color: colors[i % colors.length]
      }))
    } else {
      reasonDist.value = []
    }
  } catch (err) {
    console.error('获取退款原因分布失败:', err)
  }
}

const selectedCount = computed(() => selectedItems.value.size)

function toggleSelectAll() {
  if (selectAll.value) {
    refunds.value.forEach(item => selectedItems.value.add(item.id))
  } else {
    selectedItems.value.clear()
  }
}

// #8：判断是否限流错误
function isRateLimitError(err) {
  const msg = err?.message || ''
  return msg.includes('过于频繁') || msg.includes('429') || msg.includes('限流')
}

async function handleApprove(id) {
  try {
    await ElMessageBox.confirm(
      '确定批准该退款申请吗？此操作将触发退款流程。',
      '批准退款确认',
      { type: 'warning', confirmButtonText: '确认批准', cancelButtonText: '取消' }
    )
    await approveRefund(id)
    ElMessage.success(`退款 #${id} 已批准`)
    fetchRefunds()
    fetchStats()
    fetchStatusCount()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    if (isRateLimitError(err)) {
      ElMessage.warning('操作过于频繁，请稍后再试')
      return
    }
    ElMessage.error('批准退款失败: ' + (err?.message || '未知错误'))
  }
}

async function handleReject(id) {
  try {
    // 阻塞项 #3：拒绝时必须填写原因，用于审计追溯
    const { value: reason } = await ElMessageBox.prompt(
      '请填写拒绝退款的原因（必填，将记录到审计日志）',
      '拒绝退款确认',
      {
        type: 'warning',
        confirmButtonText: '确认拒绝',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：商品已超过退款有效期 / 退款金额不符',
        inputValidator: (val) => (val && val.trim().length > 0) || '请填写拒绝原因'
      }
    )
    await rejectRefund(id, { reason: reason.trim() })
    ElMessage.success(`退款 #${id} 已拒绝`)
    fetchRefunds()
    fetchStats()
    fetchStatusCount()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    if (isRateLimitError(err)) {
      ElMessage.warning('操作过于频繁，请稍后再试')
      return
    }
    ElMessage.error('拒绝退款失败: ' + (err?.message || '未知错误'))
  }
}

// 阻塞项 #1 闭环：APPROVED → COMPLETED 入口，财务录入第三方流水号
async function handleComplete(id) {
  try {
    const { value: transactionId } = await ElMessageBox.prompt(
      '请输入第三方支付平台返回的退款流水号（必填）',
      '完成退款',
      {
        type: 'warning',
        confirmButtonText: '确认完成',
        cancelButtonText: '取消',
        inputPlaceholder: '如微信 / 支付宝 / 银联交易流水号',
        inputValidator: (val) => (val && val.trim().length > 0) || '请填写流水号'
      }
    )
    await completeRefund(id, transactionId.trim())
    ElMessage.success(`退款 #${id} 已完成`)
    fetchRefunds()
    fetchStats()
    fetchStatusCount()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    if (isRateLimitError(err)) {
      ElMessage.warning('操作过于频繁，请稍后再试')
      return
    }
    ElMessage.error('完成退款失败: ' + (err?.message || '未知错误'))
  }
}

async function batchApprove() {
  const ids = Array.from(selectedItems.value)
  if (ids.length === 0) return
  try {
    await batchApproveRefund({ ids })
    ElMessage.success(`批量同意 ${ids.length} 项退款`)
    selectedItems.value.clear()
    selectAll.value = false
    fetchRefunds()
    fetchStats()
    fetchStatusCount()
  } catch (err) {
    if (isRateLimitError(err)) {
      ElMessage.warning('批量操作过于频繁，请稍后再试')
      return
    }
    ElMessage.error('批量同意失败: ' + (err?.message || '未知错误'))
  }
}

// #2/#3：详情弹窗 — 真实展示举证图片、拒绝原因、流水号、操作日志
async function handleDetail(item) {
  detail.value = { ...item }
  detailVisible.value = true
  detailLoading.value = true
  try {
    const full = await getRefundDetail(item.id)
    if (full) {
      detail.value = { ...detail.value, ...full }
    }
  } catch (err) {
    console.warn('获取详情失败，使用列表缓存数据:', err)
  } finally {
    detailLoading.value = false
  }
}

// 监听类型切换：重新拉列表 + 重新拉精确计数
watch(activeType, () => {
  selectedItems.value.clear()
  selectAll.value = false
  fetchRefunds()
  fetchStatusCount()
})

// 监听状态切换：仅重新拉列表（chip 角标与 status 维度无关）
watch(activeStatus, () => {
  selectedItems.value.clear()
  selectAll.value = false
  fetchRefunds()
})

// #7：每分钟刷新一次 SLA 倒计时（避免显示陈旧时间）
let slaTimer = null
function startSlaTimer() {
  if (slaTimer) clearInterval(slaTimer)
  slaTimer = setInterval(() => {
    refunds.value = refunds.value.map(item => {
      const sla = calcSla(item.createTime, item.statusLabel === '待处理' ? 'PENDING'
        : item.statusLabel === '已批准' ? 'APPROVED'
        : item.statusLabel === '已完成' ? 'COMPLETED'
        : item.statusLabel === '已拒绝' ? 'REJECTED' : '')
      return { ...item, slaLabel: sla.slaLabel, slaClass: sla.slaClass, slaIcon: sla.slaIcon }
    })
  }, 60 * 1000)
}

onMounted(() => {
  fetchStats()
  fetchRefunds()
  fetchStatusCount()
  fetchReasonDist()
  startSlaTimer()
})
</script>

<style scoped lang="css">
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 20px;
}

/* KPI */
.kpi-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.kpi-card {
  flex: 1;
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 4px;
}

.kpi-value.orange { color: #e67e22; }

.kpi-label {
  font-size: 13px;
  color: var(--text-400);
}

/* Tab 切换 */
.tab-switcher {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.tab-switcher-item {
  padding: 8px 18px;
  border: 1px solid var(--border);
  background: var(--card);
  border-radius: 999px;
  font-size: 13px;
  color: var(--text-600);
  cursor: pointer;
  transition: all .18s ease;
}

.tab-switcher-item.active {
  background: var(--primary);
  color: var(--primary-foreground);
  border-color: var(--primary);
}

/* #6：状态 chip */
.status-switcher {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.status-chip {
  padding: 6px 14px;
  border: 1px solid var(--border);
  background: var(--card);
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-600);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-chip.active {
  background: var(--primary);
  color: var(--primary-foreground);
  border-color: var(--primary);
}

.status-chip .count {
  background: rgba(255, 255, 255, 0.25);
  padding: 0 6px;
  border-radius: 8px;
  font-size: 11px;
}

.status-chip:not(.active) .count {
  background: var(--background-200);
}

/* 退款卡片列表 */
.refund-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.refund-card {
  padding: 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.refund-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.refund-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.refund-no {
  font-weight: 600;
  color: var(--primary);
  cursor: pointer;
}

.refund-no:hover {
  text-decoration: underline;
}

.refund-order {
  font-size: 12px;
  color: var(--text-400);
}

.refund-sla {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 999px;
}

.refund-sla.urgent {
  background: var(--state-error-surface);
  color: var(--state-error);
}

.refund-sla.normal {
  background: var(--state-warning-surface);
  color: var(--state-warning);
}

.refund-sla.done {
  background: var(--state-success-surface);
  color: var(--state-success);
}

.refund-body {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 12px;
}

.refund-product {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.refund-thumb {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: var(--background-200);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.refund-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.refund-type {
  font-size: 13px;
  color: var(--text-800);
  font-weight: 500;
}

.refund-time {
  font-size: 11px;
  color: var(--text-400);
}

.refund-amount {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  min-width: 80px;
}

.amount-label {
  font-size: 11px;
  color: var(--text-400);
}

.amount-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--state-error);
}

.refund-actions {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--background-100);
}

/* 详情弹窗样式 */
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid var(--background-100);
}

.detail-label {
  width: 100px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--text-500);
}

.detail-value {
  flex: 1;
  font-size: 13px;
  color: var(--text-800);
  word-break: break-all;
}

.detail-value.highlight {
  color: var(--state-error);
  font-weight: 700;
}

.detail-value.rejected {
  color: var(--state-warning);
}

.detail-section {
  padding-top: 8px;
}

.evidence-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.evidence-image {
  width: 100%;
  height: 120px;
  border-radius: 6px;
  border: 1px solid var(--border);
  cursor: pointer;
}

.image-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: var(--background-100);
  color: var(--text-400);
  font-size: 12px;
}

/* 原因分布 */
.reason-dist {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reason-bar-header {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-600);
  margin-bottom: 4px;
}

.bar-track {
  height: 8px;
  border-radius: 4px;
  background: var(--background-100);
  overflow: hidden;
}

.bar-fill-rd {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}

/* 批量操作栏 */
.batch-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  margin: 16px 0;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-md);
  z-index: 10;
}

.batch-bar label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-600);
  cursor: pointer;
}
</style>
