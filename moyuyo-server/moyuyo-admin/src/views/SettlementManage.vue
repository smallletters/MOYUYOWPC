<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button @click="handleExport">导出报表</el-button>
        <el-button type="primary" @click="handleAdd">新建</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入结算单号/周期" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ===== 结算概览 KPI 卡片（全部由真实接口驱动） ===== -->
    <div class="kpi-grid">
      <div v-for="kpi in kpiCards" :key="kpi.label" class="kpi-card">
        <div class="kpi-top">
          <div class="kpi-icon" :style="{ background: kpi.iconBg }">
            <el-icon :size="16" :color="kpi.iconColor">
              <component :is="kpi.icon" />
            </el-icon>
          </div>
          <span class="kpi-label">{{ kpi.label }}</span>
        </div>
        <p class="kpi-value" :style="{ color: kpi.valueColor }">{{ kpi.value }}</p>
        <p class="kpi-sub" :style="{ color: kpi.subColor }">{{ kpi.sub }}</p>
      </div>
    </div>

    <!-- ===== 财务分析区块（两列布局） ===== -->
    <div class="analytics-grid">
      <div class="analytics-col">
        <!-- 最近 Payout 汇总（真实数据：mo_settlement 按 payChannel 聚合） -->
        <el-card shadow="never" class="block-card">
          <div class="card-head">
            <h3>最近 Payout 汇总</h3>
            <span class="card-sub">按支付渠道聚合</span>
          </div>
          <div v-if="payoutChannels.length === 0" class="empty-tip">
            <el-empty description="暂无已结算渠道数据" :image-size="60" />
          </div>
          <div v-for="p in payoutChannels" :key="p.channel" class="payout-item">
            <div class="payout-left">
              <p class="payout-channel">{{ channelLabel(p.channel) }}</p>
              <p class="payout-count">{{ p.count }} 笔 Payout · {{ p.note }}</p>
            </div>
            <div class="payout-right">
              <span class="payout-amount">￥{{ formatMoney(p.amount) }}</span>
              <span class="status-badge reconciled">{{ p.status }}</span>
            </div>
          </div>
        </el-card>

        <!-- 对账记录（真实数据：最近 10 条 settlement） -->
        <el-card shadow="never" class="block-card">
          <div class="card-head">
            <h3>对账记录</h3>
            <span class="card-sub">最近 10 条结算</span>
          </div>
          <el-table :data="reconRecords" size="small">
            <el-table-column prop="no" label="对账单号" min-width="140" />
            <el-table-column prop="time" label="时间" min-width="160" />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">
                <span :class="['recon-amount', { 'recon-diff': row.status === '差异' }]">￥{{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['status-badge', statusClass(row.status)]">{{ row.status }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="reconRecords.length === 0" class="empty-tip">
            <el-empty description="暂无对账记录" :image-size="60" />
          </div>
        </el-card>
      </div>

      <div class="analytics-col">
        <!-- 退款概况（真实数据：mo_refund 聚合） -->
        <el-card shadow="never" class="block-card">
          <div class="card-head">
            <h3>退款概况</h3>
            <span class="card-sub">全部退款统计</span>
          </div>
          <div class="tax-total-row">
            <span>退款总额</span>
            <span class="tax-total-amount">￥{{ formatMoney(refundKpi.totalAmount) }}</span>
          </div>
          <div class="refund-kpi-row">
            <div class="refund-kpi-item">
              <span class="refund-kpi-label">退款笔数</span>
              <span class="refund-kpi-value">{{ refundKpi.totalCount }}</span>
            </div>
            <div class="refund-kpi-item">
              <span class="refund-kpi-label">待处理</span>
              <span class="refund-kpi-value refund-kpi-warn">{{ refundKpi.pendingCount }}</span>
            </div>
            <div class="refund-kpi-item">
              <span class="refund-kpi-label">已完成</span>
              <span class="refund-kpi-value refund-kpi-ok">{{ refundKpi.completedCount }}</span>
            </div>
          </div>
          <div class="refund-link-row">
            <button class="tax-btn" @click="goRefund">
              <el-icon :size="14"><ArrowRight /></el-icon>
              前往退款管理
            </button>
          </div>
        </el-card>

        <!-- 对账异常告警（真实数据：settlement ABNORMAL + refund PENDING） -->
        <el-card shadow="never" class="block-card">
          <div class="card-head">
            <div class="alert-title">
              <el-icon :size="16" :color="alerts.filter(a=>a.status==='待处理').length > 0 ? 'var(--state-error)' : 'var(--state-success)'">
                <component :is="alerts.filter(a=>a.status==='待处理').length > 0 ? WarningFilled : CircleCheck" />
              </el-icon>
              <h3>对账异常告警</h3>
              <span class="alert-badge" :class="{ 'alert-badge-zero': alerts.filter(a=>a.status==='待处理').length === 0 }">
                {{ alerts.filter(a => a.status === '待处理').length }}
              </span>
            </div>
          </div>
          <div v-if="alerts.length === 0" class="empty-tip">
            <el-empty description="暂无告警，资金对账健康" :image-size="60" />
          </div>
          <div v-for="(a, i) in alerts" :key="i" class="alert-item" :class="{ resolved: a.status === '已处理' }">
            <div class="alert-top">
              <span class="alert-type" :style="{ color: levelColor(a.level) }">
                <el-icon :size="14">
                  <component :is="a.level === 'success' ? CircleCheck : WarningFilled" />
                </el-icon>
                {{ a.type }}
              </span>
              <span :class="['alert-status', a.status === '待处理' ? 'alert-pending' : 'alert-resolved']">{{ a.status }}</span>
            </div>
            <p class="alert-desc">{{ a.desc }}</p>
          </div>
        </el-card>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="settlementNo" label="结算单号" width="160" />
        <el-table-column prop="period" label="周期" width="120" />
        <el-table-column prop="payChannel" label="支付渠道" width="120">
          <template #default="{ row }">
            <span class="channel-pill">{{ channelLabel(row.payChannel) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="结算金额" width="120">
          <template #default="{ row }">￥{{ Number(row.amount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="settlementTagType(row.status)" size="small" effect="light">{{ settlementLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结算时间" width="160">
          <template #default="{ row }">{{ formatTime(row.settleTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
        />
      </div>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="结算单号">
          <el-input v-model="editForm.settlementNo" placeholder="新建时由系统自动生成" :disabled="!editForm.id" />
        </el-form-item>
        <el-form-item label="周期">
          <el-input v-model="editForm.period" placeholder="如 2026-07上" />
        </el-form-item>
        <el-form-item label="支付渠道">
          <el-select v-model="editForm.payChannel" placeholder="请选择">
            <el-option label="Stripe 信用卡" value="STRIPE" />
            <el-option label="PayPal" value="PAYPAL" />
            <el-option label="微信支付" value="WECHAT" />
            <el-option label="支付宝" value="ALIPAY" />
            <el-option label="Apple Pay" value="APPLE_PAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="结算金额">
          <el-input-number v-model="editForm.amount" :min="0" :step="100" :precision="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="待结算" value="PENDING" />
            <el-option label="结算中" value="SETTLING" />
            <el-option label="已结算" value="SETTLED" />
            <el-option label="异常" value="ABNORMAL" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 报表导出对话框 -->
    <el-dialog v-model="exportDialogVisible" title="导出报表" width="420px">
      <el-form label-width="80px">
        <el-form-item label="报表名称">
          <el-input v-model="exportName" placeholder="输入报表名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmExport">导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Wallet, CircleCheck, WarningFilled, Money, Download, ArrowRight } from '@element-plus/icons-vue'
import {
  getSettlements, createSettlement, updateSettlement, deleteSettlement,
  getPayoutChannels, getReconcileAlerts, getRefundKpi
} from '../api/admin'
import { toArray } from '../utils/safeArray'

const router = useRouter()
const pageTitle = '结算管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  id: null,
  settlementNo: '',
  period: '',
  payChannel: '',
  remark: '',
  amount: 0,
  status: 'PENDING'
})

// 真实数据：Payout 渠道汇总
const payoutChannels = ref([])
// 真实数据：对账异常告警
const alerts = ref([])
// 真实数据：退款 KPI
const refundKpi = ref({ totalAmount: 0, totalCount: 0, pendingCount: 0, completedCount: 0 })

// ===== 金额格式化：千分位 + 保留两位小数 =====
function formatMoney(n) {
  return Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// ===== 时间格式化：后端 LocalDateTime 序列化为 "yyyy-MM-dd HH:mm:ss" =====
function formatTime(value) {
  if (!value) return '—'
  const str = String(value).replace('T', ' ').replace(/\..*$/, '')
  return str || '—'
}

// ===== 渠道枚举 -> 中文标签（与财务概览保持一致） =====
function channelLabel(channel) {
  const map = {
    STRIPE: 'Stripe 信用卡',
    PAYPAL: 'PayPal',
    WECHAT: '微信支付',
    ALIPAY: '支付宝',
    APPLE_PAY: 'Apple Pay',
    UNIONPAY: '银联',
    WALLET: '余额支付'
  }
  return map[channel] || channel || '—'
}

// ===== 结算状态 -> 标签类型（与 SettlementEntity 状态机对齐） =====
function settlementTagType(status) {
  const map = {
    SETTLED: 'success',
    COMPLETED: 'success',
    SETTLING: 'warning',
    PENDING: 'info',
    ABNORMAL: 'danger',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

// ===== 结算状态 -> 中文标签 =====
function settlementLabel(status) {
  const map = {
    SETTLED: '已结算',
    COMPLETED: '已结算',
    SETTLING: '结算中',
    PENDING: '待结算',
    ABNORMAL: '异常',
    FAILED: '失败'
  }
  return map[status] || status || '未知'
}

// ===== 对账记录状态映射（与状态机一致） =====
function reconStatus(s) {
  const map = {
    SETTLED: '已对账',
    COMPLETED: '已对账',
    SETTLING: '待对账',
    PENDING: '待对账',
    ABNORMAL: '差异',
    FAILED: '差异'
  }
  return map[s] || '待对账'
}

// ===== KPI 卡片（全部由真实数据派生） =====
const pendingAmount = computed(() => tableData.value
  .filter(i => ['PENDING', 'SETTLING'].includes(i.status))
  .reduce((s, i) => s + (Number(i.amount) || 0), 0))
const pendingCount = computed(() => tableData.value.filter(i => ['PENDING', 'SETTLING'].includes(i.status)).length)
const settledAmount = computed(() => tableData.value
  .filter(i => ['SETTLED', 'COMPLETED'].includes(i.status))
  .reduce((s, i) => s + (Number(i.amount) || 0), 0))
const settledCount = computed(() => tableData.value.filter(i => ['SETTLED', 'COMPLETED'].includes(i.status)).length)
const pendingAlertCount = computed(() => alerts.value.filter(a => a.status === '待处理').length)

const kpiCards = computed(() => [
  {
    label: '待结算金额',
    value: '￥' + formatMoney(pendingAmount.value),
    sub: pendingCount.value + ' 笔待结算',
    icon: Wallet,
    iconBg: 'var(--state-warning-surface)',
    iconColor: 'var(--state-warning)',
    valueColor: 'var(--text-800)',
    subColor: 'var(--text-400)'
  },
  {
    label: '已结算金额',
    value: '￥' + formatMoney(settledAmount.value),
    sub: settledCount.value + ' 笔已结算',
    icon: CircleCheck,
    iconBg: 'var(--state-success-surface)',
    iconColor: 'var(--state-success)',
    valueColor: 'var(--text-800)',
    subColor: 'var(--text-400)'
  },
  {
    label: '对账异常数',
    value: String(alerts.value.length),
    sub: pendingAlertCount.value + ' 待处理',
    icon: WarningFilled,
    iconBg: 'var(--state-error-surface)',
    iconColor: 'var(--state-error)',
    valueColor: 'var(--state-error)',
    subColor: 'var(--text-400)'
  },
  {
    label: '退款总额',
    value: '￥' + formatMoney(refundKpi.value.totalAmount),
    sub: refundKpi.value.totalCount + ' 笔退款 · ' + refundKpi.value.pendingCount + ' 待处理',
    icon: Money,
    iconBg: 'var(--state-success-surface)',
    iconColor: 'var(--state-success)',
    valueColor: 'var(--text-800)',
    subColor: 'var(--text-400)'
  }
])

// ===== 对账记录派生：取最近 10 条 settlement，按状态映射文案 =====
const reconRecords = computed(() => tableData.value.slice(0, 10).map(s => ({
  no: s.settlementNo || String(s.id),
  time: formatTime(s.settleTime || s.createTime),
  amount: Number(s.amount || 0),
  status: reconStatus(s.status)
})))

// ===== 对账状态 -> 状态标签样式类 =====
function statusClass(s) {
  if (s === '已对账') return 'reconciled'
  if (s === '差异') return 'discrepancy'
  return 'pending'
}

// ===== 告警级别 -> 颜色令牌 =====
function levelColor(level) {
  if (level === 'error') return 'var(--state-error)'
  if (level === 'success') return 'var(--state-success)'
  return 'var(--state-warning)'
}

// ===== 报表导出 =====
const exportDialogVisible = ref(false)
const exportName = ref('')

function handleExport() {
  exportName.value = '结算报表-' + new Date().toISOString().slice(0, 10)
  exportDialogVisible.value = true
}

function confirmExport() {
  // 基于当前真实数据生成 CSV
  const rows = tableData.value.map(s => ({
    col0: s.settlementNo || s.id,
    col1: s.period || '—',
    col2: channelLabel(s.payChannel),
    col3: '￥' + formatMoney(s.amount),
    col4: settlementLabel(s.status),
    col5: formatTime(s.settleTime)
  }))
  exportTableToCSV(rows, exportName.value, [
    { key: 'col0', label: '结算单号' },
    { key: 'col1', label: '周期' },
    { key: 'col2', label: '支付渠道' },
    { key: 'col3', label: '金额' },
    { key: 'col4', label: '状态' },
    { key: 'col5', label: '结算时间' }
  ])
  ElMessage.success('已导出 ' + exportName.value + '（含 ' + rows.length + ' 条记录）')
  exportDialogVisible.value = false
}

// 通用 CSV 导出
function exportTableToCSV(rows, filename, columns) {
  if (!rows || rows.length === 0) {
    ElMessage.warning('暂无可导出数据')
    return
  }
  const headers = columns.map(c => c.label).join(',')
  const body = rows.map(r => columns.map(c => {
    const v = r[c.key] ?? ''
    const s = String(v).replace(/"/g, '""')
    return /[",\n]/.test(s) ? `"${s}"` : s
  }).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + headers + '\n' + body], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename + '.csv'
  a.click()
  URL.revokeObjectURL(url)
}

// 跳转退款管理
function goRefund() {
  router.push('/refund')
}

// ===== 结算单 CRUD =====
async function loadData() {
  try {
    const res = await getSettlements({ page: currentPage.value, size: pageSize.value })
    const list = toArray(res?.records != null ? res.records : res)
    // 根据关键词过滤
    let filtered = [...list]
    if (filters.keyword) {
      const kw = filters.keyword.toLowerCase()
      filtered = filtered.filter(item =>
        (item.settlementNo || '').toLowerCase().includes(kw)
        || (item.period || '').toLowerCase().includes(kw))
    }
    tableData.value = filtered
    total.value = res?.total != null ? Number(res.total) : filtered.length
  } catch (e) {
    console.error('加载结算列表失败:', e)
    ElMessage.error('加载结算列表失败')
  }
}

// 加载 Payout 渠道汇总
async function loadPayoutChannels() {
  try {
    payoutChannels.value = await getPayoutChannels() || []
  } catch (e) {
    console.error('加载 Payout 渠道汇总失败:', e)
    payoutChannels.value = []
  }
}

// 加载对账异常告警
async function loadAlerts() {
  try {
    alerts.value = await getReconcileAlerts() || []
  } catch (e) {
    console.error('加载对账告警失败:', e)
    alerts.value = []
  }
}

// 加载退款 KPI
async function loadRefundKpi() {
  try {
    refundKpi.value = await getRefundKpi() || { totalAmount: 0, totalCount: 0, pendingCount: 0, completedCount: 0 }
  } catch (e) {
    console.error('加载退款 KPI 失败:', e)
    refundKpi.value = { totalAmount: 0, totalCount: 0, pendingCount: 0, completedCount: 0 }
  }
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() {
  dialogTitle.value = '新建结算单'
  editForm.id = null
  editForm.settlementNo = ''
  editForm.period = ''
  editForm.payChannel = ''
  editForm.remark = ''
  editForm.amount = 0
  editForm.status = 'PENDING'
  dialogVisible.value = true
}
function handleEdit(row) {
  dialogTitle.value = '编辑结算单'
  Object.assign(editForm, row)
  dialogVisible.value = true
}
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteSettlement(row.id)
    ElMessage.success('删除成功')
    await Promise.all([loadData(), loadPayoutChannels(), loadAlerts(), loadRefundKpi()])
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}
async function handleSave() {
  try {
    const payload = {
      period: editForm.period,
      payChannel: editForm.payChannel,
      remark: editForm.remark,
      amount: editForm.amount,
      status: editForm.status
    }
    if (editForm.id) {
      await updateSettlement(editForm.id, payload)
    } else {
      await createSettlement(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await Promise.all([loadData(), loadPayoutChannels(), loadAlerts(), loadRefundKpi()])
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}
onMounted(() => {
  Promise.all([loadData(), loadPayoutChannels(), loadAlerts(), loadRefundKpi()])
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
.empty-tip { padding: 16px 0; }

/* ===== 结算概览 KPI 卡片 ===== */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.kpi-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 16px;
}
.kpi-top { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.kpi-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.kpi-label { font-size: 12px; font-weight: 500; color: var(--text-400); }
.kpi-value {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
  font-variant-numeric: tabular-nums;
}
.kpi-sub { font-size: 12px; margin: 6px 0 0; }

/* ===== 财务分析两列布局 ===== */
.analytics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.analytics-col { display: flex; flex-direction: column; gap: 16px; }
@media (max-width: 1100px) {
  .analytics-grid { grid-template-columns: 1fr; }
}
.block-card :deep(.el-card__body) { padding: 16px; }
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--background-200);
  margin-bottom: 8px;
}
.card-head h3 { font-size: 14px; font-weight: 600; color: var(--text-800); margin: 0; }
.card-sub { font-size: 12px; color: var(--text-400); }

/* ===== 最近 Payout 汇总 ===== */
.payout-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--background-200);
}
.payout-item:last-child { border-bottom: none; }
.payout-channel { font-size: 14px; font-weight: 600; color: var(--text-800); margin: 0 0 2px; }
.payout-count { font-size: 12px; color: var(--text-400); margin: 0; }
.payout-right { display: flex; align-items: center; gap: 10px; }
.payout-amount {
  font-size: 16px;
  font-weight: 700;
  color: var(--brand-500);
  font-variant-numeric: tabular-nums;
}

/* ===== 对账状态标签 ===== */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.status-badge.reconciled { background: var(--state-success-surface); color: var(--state-success); }
.status-badge.discrepancy { background: var(--state-error-surface); color: var(--state-error); }
.status-badge.pending { background: var(--background-200); color: var(--text-500); }
.recon-amount { font-weight: 600; color: var(--text-800); font-variant-numeric: tabular-nums; }
.recon-amount.recon-diff { color: var(--state-error); }

/* ===== 退款概况（原"税务报表"位替换） ===== */
.tax-total-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--background-200);
}
.tax-total-row span:first-child { font-size: 13px; color: var(--text-500); }
.tax-total-amount {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.refund-kpi-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--background-200);
}
.refund-kpi-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.refund-kpi-label { font-size: 12px; color: var(--text-400); }
.refund-kpi-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.refund-kpi-warn { color: var(--state-warning); }
.refund-kpi-ok { color: var(--state-success); }
.refund-link-row { padding-top: 12px; }
.tax-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--background-50);
  color: var(--primary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}
.tax-btn:hover { background: var(--background-200); border-color: var(--primary); }

/* ===== 对账异常告警 ===== */
.alert-title { display: flex; align-items: center; gap: 8px; }
.alert-title h3 { margin: 0; }
.alert-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--state-error);
  color: var(--state-error-foreground);
  font-size: 11px;
  font-weight: 700;
}
.alert-badge-zero { background: var(--background-200); color: var(--text-500); }
.alert-item {
  padding: 12px;
  border-bottom: 1px solid var(--background-200);
  border-left: 3px solid var(--state-warning);
}
.alert-item:last-child { border-bottom: none; }
.alert-item.resolved { border-left-color: var(--state-success); opacity: 0.75; }
.alert-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.alert-type { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; font-weight: 600; }
.alert-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}
.alert-pending { background: var(--background-200); color: var(--text-500); }
.alert-resolved { background: var(--state-success-surface); color: var(--state-success); }
.alert-desc { font-size: 12px; line-height: 1.6; color: var(--text-600); margin: 0; }

/* ===== 渠道徽标 ===== */
.channel-pill {
  display: inline-block;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  background: var(--background-100);
  color: var(--text-700);
}
</style>