<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <!-- 导出报表（示例功能） -->
        <el-button @click="handleExport">导出报表</el-button>
        <el-button type="primary" @click="handleAdd">新建</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入结算单号/商家" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ===== 结算概览 KPI 卡片 ===== -->
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
        <!-- 最近 Payout 汇总 -->
        <el-card shadow="never" class="block-card">
          <div class="card-head">
            <h3>最近 Payout 汇总</h3>
            <span class="card-sub">自动对账 T+3</span>
          </div>
          <div v-for="p in payoutChannels" :key="p.channel" class="payout-item">
            <div class="payout-left">
              <p class="payout-channel">{{ p.channel }}</p>
              <p class="payout-count">{{ p.count }} 笔 Payout · {{ p.note }}</p>
            </div>
            <div class="payout-right">
              <span class="payout-amount">￥{{ formatMoney(p.amount) }}</span>
              <span class="status-badge reconciled">{{ p.status }}</span>
            </div>
          </div>
        </el-card>

        <!-- 对账记录 -->
        <el-card shadow="never" class="block-card">
          <div class="card-head">
            <h3>对账记录</h3>
            <span class="card-sub">本月</span>
          </div>
          <el-table :data="reconRecords" size="small">
            <el-table-column prop="no" label="对账单号" min-width="140" />
            <el-table-column prop="time" label="时间" min-width="120" />
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
        </el-card>
      </div>

      <div class="analytics-col">
        <!-- 税务报表 -->
        <el-card shadow="never" class="block-card">
          <div class="card-head">
            <h3>税务报表</h3>
            <span class="card-sub">{{ taxMonth }}</span>
          </div>
          <div class="tax-total-row">
            <span>当月税额汇总</span>
            <span class="tax-total-amount">￥{{ formatMoney(taxTotal) }}</span>
          </div>
          <div v-for="t in taxReports" :key="t.name" class="tax-row">
            <div class="tax-info">
              <p class="tax-name">{{ t.name }}</p>
              <p class="tax-desc">{{ t.desc }}</p>
            </div>
            <div class="tax-actions">
              <span class="tax-amount">￥{{ formatMoney(t.amount) }}</span>
              <button class="tax-btn" @click="handleExportTax(t.name)">
                <el-icon :size="14"><Download /></el-icon>
                导出
              </button>
            </div>
          </div>
        </el-card>

        <!-- 对账异常告警 -->
        <el-card shadow="never" class="block-card">
          <div class="card-head">
            <div class="alert-title">
              <el-icon :size="16" color="var(--state-error)"><WarningFilled /></el-icon>
              <h3>对账异常告警</h3>
              <span class="alert-badge">{{ alerts.length }}</span>
            </div>
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
            <div class="alert-actions">
              <button class="action-link" @click="handleAlertDetail(a)">查看详情</button>
              <button v-if="a.status === '待处理'" class="action-link" @click="handleResolveAlert(a)">手动核销</button>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="settlementNo" label="结算单号" width="160" />
        <el-table-column prop="period" label="周期" width="120" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="结算金额" width="120">
          <template #default="{ row }">￥{{ Number(row.amount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === '已结算' ? 'success' : row.status === '已确认' ? 'primary' : 'warning'">{{ row.status }}</el-tag>
          </template>
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
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="结算金额">
          <el-input-number v-model="editForm.amount" :min="0" :step="100" :precision="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="待确认" value="待确认" />
            <el-option label="已确认" value="已确认" />
            <el-option label="已结算" value="已结算" />
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

    <!-- 告警详情 / 核销 -->
    <el-dialog
      v-model="alertDialogVisible"
      :title="alertDialogMode === 'detail' ? '告警详情' : '手动核销告警'"
      width="480px"
    >
      <el-descriptions v-if="currentAlert" :column="1" border>
        <el-descriptions-item label="告警类型">{{ currentAlert.type }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ currentAlert.level }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ currentAlert.status }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ currentAlert.desc }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="alertDialogVisible = false">关闭</el-button>
        <el-button v-if="alertDialogMode === 'resolve' && currentAlert?.status === '待处理'" type="primary" @click="confirmResolveAlert">确认核销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Wallet, CircleCheck, WarningFilled, Money, Download } from '@element-plus/icons-vue'
import { getSettlements, createSettlement, updateSettlement, deleteSettlement } from '../api/admin'
import api from '../api'
import { toArray } from '../utils/safeArray'

const pageTitle = '结算管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  settlementNo: '',
  period: '',
  remark: '',
  amount: 0,
  status: '待确认'
})

// ===== 金额格式化：千分位 + 保留两位小数 =====
function formatMoney(n) {
  return Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// ===== 结算概览 KPI（待结算/已结算由列表数据驱动，异常数/退款为示例数据） =====
const settledAmount = computed(() => tableData.value.filter(i => i.status === '已结算').reduce((s, i) => s + (Number(i.amount) || 0), 0))
const settledCount = computed(() => tableData.value.filter(i => i.status === '已结算').length)
const pendingAmount = computed(() => tableData.value.filter(i => i.status !== '已结算').reduce((s, i) => s + (Number(i.amount) || 0), 0))
const pendingCount = computed(() => tableData.value.filter(i => i.status !== '已结算').length)
const pendingAlertCount = computed(() => alerts.value.filter(a => a.status === '待处理').length)
const resolvedAlertCount = computed(() => alerts.value.filter(a => a.status === '已处理').length)
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
    sub: pendingAlertCount.value + ' 待处理 / ' + resolvedAlertCount.value + ' 已处理',
    icon: WarningFilled,
    iconBg: 'var(--state-error-surface)',
    iconColor: 'var(--state-error)',
    valueColor: 'var(--state-error)',
    subColor: 'var(--text-400)'
  },
  {
    // 示例数据：退款总额（无真实 API，展示设计稿形态）
    label: '退款总额',
    value: '￥3,420.00',
    sub: '本月退款率 2.7%',
    icon: Money,
    iconBg: 'var(--state-success-surface)',
    iconColor: 'var(--state-success)',
    valueColor: 'var(--text-800)',
    subColor: 'var(--text-400)'
  }
])

// ===== 示例数据：各渠道最近 Payout 汇总（无真实 API） =====
const payoutChannels = [
  { channel: 'Stripe', count: 24, amount: 105379.5, status: '已到账', note: '自动对账 T+3' },
  { channel: 'PayPal', count: 8, amount: 18460.2, status: '已到账', note: '自动对账 T+3' }
]

// ===== 示例数据：对账记录（无真实 API） =====
const reconRecords = [
  { no: 'PO_20260705_001', time: '2026-07-05 10:32', amount: 12580, status: '已对账' },
  { no: 'PO_20260702_003', time: '2026-07-02 14:18', amount: 8920.5, status: '已对账' },
  { no: 'PO_20260628_007', time: '2026-06-28 09:45', amount: 3150, status: '差异', diff: -45.8 },
  { no: 'PO_20260707_002', time: '2026-07-07 预计 07-10', amount: 15320, status: '待对账' },
  { no: 'PO_20260625_012', time: '2026-06-25 16:50', amount: 22780, status: '已对账' }
]

// ===== 示例数据：税务报表（无真实 API） =====
const taxMonth = '2026年7月'
const taxTotal = 8962.3
const taxReports = [
  { name: 'EU VAT', desc: '欧盟增值税', amount: 4120 },
  { name: 'US Sales Tax', desc: '美国销售税', amount: 2850.5 },
  { name: 'UK VAT', desc: '英国增值税', amount: 1991.8 }
]

// ===== 示例数据：对账异常告警（无真实 API） =====
const alerts = ref([
  { type: '金额差异', level: 'warning', status: '待处理', desc: 'Payout PO_20260628_007 与系统订单合计差额 ￥-45.80，涉及 3 笔订单。' },
  { type: '订单缺失', level: 'error', status: '待处理', desc: 'Stripe Payout PO_20260702_003 中包含 1 笔系统未匹配的订单 (#ORD-98012)，金额 ￥189.00。' },
  { type: '退款不一致', level: 'success', status: '已处理', desc: 'PayPal 订单 #ORD-97856 退款金额 ￥68.00 与渠道记录 ￥65.50 不一致，已手动核销。' },
  { type: '金额差异', level: 'success', status: '已处理', desc: 'Stripe Payout PO_20260625_012 汇率换算差异 ￥12.30，确认为汇率浮动导致，已核销。' },
  { type: '订单缺失', level: 'success', status: '已处理', desc: '测试订单 #ORD-TEST-001 未同步至结算系统，已补录并核销。' }
])

// ===== 对账状态 → 状态标签样式类 =====
function statusClass(s) {
  if (s === '已对账') return 'reconciled'
  if (s === '差异') return 'discrepancy'
  return 'pending'
}

// ===== 告警级别 → 颜色令牌 =====
function levelColor(level) {
  if (level === 'error') return 'var(--state-error)'
  if (level === 'success') return 'var(--state-success)'
  return 'var(--state-warning)'
}

// ===== 报表导出 / 告警处理（已有可用 API，未接入则导出 Excel） =====
// 报表导出对话框
const exportDialogVisible = ref(false)
const exportName = ref('')

function handleExport() {
  exportName.value = '汇总报表-' + new Date().toISOString().slice(0, 10)
  exportDialogVisible.value = true
}

async function confirmExport() {
  try {
    // 调用导出 API：POST /finance/settlements/export
    const res = await api.post('/finance/settlements/export', { name: exportName.value })
    if (res && res.url) {
      window.open(res.url, '_blank')
      ElMessage.success('报表已生成：' + exportName.value)
    } else {
      // 无 URL 时降级为前端生成 CSV
      exportTableToCSV(settlements.value, exportName.value)
      ElMessage.success('已导出本地 CSV 文件')
    }
  } catch (e) {
    // 接口未接入时使用前端 CSV 导出
    exportTableToCSV(settlements.value, exportName.value)
    ElMessage.success('已导出本地 CSV 文件')
  } finally {
    exportDialogVisible.value = false
  }
}

// 单项报表导出（同样降级到 CSV）
function handleExportTax(name) {
  try {
    const rows = filteredSettlements.value
    exportTableToCSV(rows, name + '-' + new Date().toISOString().slice(0, 10))
    ElMessage.success('已导出 ' + name + ' 报表')
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.message || '未知错误'))
  }
}

// 告警详情 / 手动核销
const alertDialogVisible = ref(false)
const alertDialogMode = ref('detail') // detail / resolve
const currentAlert = ref(null)

function handleAlertDetail(alert) {
  currentAlert.value = alert
  alertDialogMode.value = 'detail'
  alertDialogVisible.value = true
}

function handleResolveAlert(alert) {
  currentAlert.value = alert
  alertDialogMode.value = 'resolve'
  alertDialogVisible.value = true
}

async function confirmResolveAlert() {
  if (!currentAlert.value) return
  try {
    // 调用核销 API：POST /finance/settlements/alerts/{id}/resolve
    await api.post(`/finance/settlements/alerts/${currentAlert.value.id}/resolve`)
    // 更新本地状态
    const idx = alerts.value.findIndex(a => a === currentAlert.value)
    if (idx >= 0) {
      alerts.value[idx].status = '已处理'
      alerts.value[idx].level = 'success'
    }
    ElMessage.success('已核销告警：' + currentAlert.value.type)
    alertDialogVisible.value = false
  } catch (e) {
    // 接口未接入时本地修改
    const idx = alerts.value.findIndex(a => a === currentAlert.value)
    if (idx >= 0) {
      alerts.value[idx].status = '已处理'
      alerts.value[idx].level = 'success'
    }
    ElMessage.success('已核销告警（本地模式）')
    alertDialogVisible.value = false
  }
}

// 简易 CSV 导出工具
function exportTableToCSV(rows, filename) {
  if (!rows || rows.length === 0) {
    ElMessage.warning('暂无可导出数据')
    return
  }
  const headers = Object.keys(rows[0])
  const csv = [
    headers.join(','),
    ...rows.map(r => headers.map(h => JSON.stringify(r[h] ?? '')).join(','))
  ].join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename + '.csv'
  a.click()
  URL.revokeObjectURL(url)
}

// ===== 结算单 CRUD（保留原有逻辑） =====
// 从API加载结算列表数据
async function loadData() {
  try {
    const res = await getSettlements()
    const list = toArray(res)
    // 根据关键词过滤
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => (item.settlementNo || '').includes(filters.keyword) || (item.period || '').includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (e) {
    console.error('加载结算列表失败:', e)
    ElMessage.error('加载结算列表失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建结算单'; editForm.id = null; editForm.settlementNo = ''; editForm.period = ''; editForm.remark = ''; editForm.amount = 0; editForm.status = '待确认'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑结算单'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteSettlement(row.id)
    ElMessage.success('删除成功')
    await loadData()
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
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }

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
/* 金额高亮：品牌色 */
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
/* 对账记录金额 */
.recon-amount { font-weight: 600; color: var(--text-800); font-variant-numeric: tabular-nums; }
.recon-amount.recon-diff { color: var(--state-error); }

/* ===== 税务报表 ===== */
.tax-total-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--background-200);
}
.tax-total-row span:first-child { font-size: 12px; color: var(--text-500); }
.tax-total-amount {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.tax-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--background-200);
}
.tax-row:last-child { border-bottom: none; }
.tax-name { font-size: 14px; font-weight: 500; color: var(--text-800); margin: 0 0 2px; }
.tax-desc { font-size: 12px; color: var(--text-400); margin: 0; }
.tax-actions { display: flex; align-items: center; gap: 10px; }
.tax-amount {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.tax-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
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
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--state-error);
  color: var(--state-error-foreground);
  font-size: 12px;
  font-weight: 700;
}
/* 告警条：红色左边框，已处理转绿色 */
.alert-item {
  padding: 12px;
  border-bottom: 1px solid var(--background-200);
  border-left: 3px solid var(--state-warning);
}
.alert-item:last-child { border-bottom: none; }
.alert-item.resolved { border-left-color: var(--state-success); opacity: 0.7; }
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
.alert-desc { font-size: 12px; line-height: 1.6; color: var(--text-600); margin: 0 0 8px; }
.alert-actions { display: flex; gap: 16px; }
.action-link {
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
}
</style>
