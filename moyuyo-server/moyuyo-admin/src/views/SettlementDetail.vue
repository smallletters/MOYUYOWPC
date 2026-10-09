<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button @click="router.back()">返回</el-button>
        <el-button type="primary" :disabled="tableData.length === 0" @click="exportDetail">
          导出明细 CSV
        </el-button>
      </div>
    </div>
    <!-- 结算单摘要 -->
    <el-card shadow="never" class="summary-card">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="结算单号">{{ summary.settlementNo }}</el-descriptions-item>
        <el-descriptions-item label="周期">{{ summary.period }}</el-descriptions-item>
        <el-descriptions-item label="结算状态">
          <el-tag :type="statusTagType(summary.status)" size="small" effect="light">{{ statusLabel(summary.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="结算时间">{{ summary.settleTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="订单总数">{{ summary.orderCount }}</el-descriptions-item>
        <el-descriptions-item label="总金额">￥{{ Number(summary.totalAmount || 0).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="手续费">￥{{ Number(summary.fee || 0).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="实际结算">￥{{ Number(summary.netAmount || 0).toFixed(2) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
    <el-card shadow="never">
      <el-table :data="pagedTableData" stripe>
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">￥{{ Number(row.amount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="手续费" width="100">
          <template #default="{ row }">￥{{ Number(row.fee || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="实际结算" width="110">
          <!-- 后端 OrderSummary 不返回 netAmount，实际结算 = amount - fee（前端推算） -->
          <template #default="{ row }">￥{{ (Number(row.amount || 0) - Number(row.fee || 0)).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettlementDetail } from '../api/admin'
import { useRoute, useRouter } from 'vue-router'
import { toArray } from '../utils/safeArray'
import { exportCsv } from '../utils/exportCsv'

const pageTitle = '结算详情'
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)

// 后端一次性返回该结算单的全部订单（不真正分页），前端按 page/pageSize 切片显示
const pagedTableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return tableData.value.slice(start, start + pageSize.value)
})

// 分页器回调（v-model 已经改了 currentPage/pageSize，这里只需要保证 currentPage 越界时复位）
function onPageChange() {
  // 防止 total 缩小（比如删除结算单）导致 currentPage 越界
  const maxPage = Math.max(1, Math.ceil(tableData.value.length / pageSize.value))
  if (currentPage.value > maxPage) currentPage.value = maxPage
}
function onSizeChange() {
  // 切 size 时回到第一页，避免越界
  currentPage.value = 1
}

// 结算单摘要信息（字段名与后端 /api/admin/finance/settlements/{id} 返回对齐）
const summary = reactive({
  id: null,
  settlementNo: '',
  period: '',
  merchant: '',
  totalAmount: 0,
  fee: 0,
  netAmount: 0,
  orderCount: 0,
  status: '',
  settleTime: ''
})

const route = useRoute()
const router = useRouter()

// 从API加载结算详情数据
async function loadData() {
  try {
    const settlementId = route.query.id
    if (!settlementId) {
      ElMessage.warning('缺少结算单ID')
      return
    }
    const res = await getSettlementDetail(settlementId)
    // 接口已由 axios 拦截器解包为 data 对象本身，这里再做一次空值兜底
    const data = res || {}
    // 摘要信息：后端 /api/admin/finance/settlements/{id} 返回的是扁平字段，
    // 不是嵌套的 data.summary。需要把扁平字段直接合并到 summary。
    // merchant 字段后端暂未提供，保留默认空值即可
    // id 需要保留给文件名兜底，所以不扔
    const { orders, ...rest } = data
    Object.assign(summary, rest)
    // 填充明细列表：后端返回的数组字段是 orders，按 'records' / 'orders' / 'list' 顺序查找
    const list = toArray(data, 'orders')
    tableData.value = list
    total.value = tableData.value.length
  } catch (e) {
    console.error('加载结算详情失败:', e)
    ElMessage.error('加载结算详情失败')
  }
}
function handleDetail(row) {
  // 后端 OrderSummary 仅返回 orderNo，不返回 id，所以无法直接跳到 /orders/:id 详情
  // 直接弹提示让用户按订单号到订单列表中检索（避免错跳）
  if (row.orderNo) {
    ElMessage.info(`订单号：${row.orderNo}，请到订单管理页面检索`)
  } else {
    ElMessage.info('暂无关联订单信息')
  }
}

// ===== P2-3：导出当前结算单的订单明细为 CSV =====
// 后端 OrderSummary 实际返回：orderNo / amount / fee / payTime
function exportDetail() {
  if (tableData.value.length === 0) {
    ElMessage.warning('当前结算单没有可导出的订单明细')
    return
  }
  const rows = tableData.value.map(r => ({
    col0: r.orderNo || safeId(r.id) || '—',
    col1: '¥' + Number(r.amount || 0).toFixed(2),
    col2: '¥' + Number(r.fee || 0).toFixed(2),
    col3: '¥' + (Number(r.amount || 0) - Number(r.fee || 0)).toFixed(2),
    col4: formatTimeField(r.payTime)
  }))
  const ok = exportCsv(rows, [
    { key: 'col0', label: '订单号' },
    { key: 'col1', label: '订单金额' },
    { key: 'col2', label: '手续费' },
    { key: 'col3', label: '实际结算' },
    { key: 'col4', label: '支付时间' }
  ], `settlement-detail-${summary.settlementNo || safeId(summary.id) || 'unknown'}.csv`)
  if (ok) ElMessage.success('结算明细已导出，请用 Excel/WPS 打开')
  else ElMessage.error('导出失败，请稍后重试')
}

// 时间格式化（结算明细专用，LocalDateTime → 'YYYY-MM-DD HH:mm:ss'）
function formatTimeField(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').replace(/\..*$/, '')
}

// 把 ID 统一转为字符串，避免 snowflake 精度丢失
function safeId(id) {
  if (id == null) return ''
  return String(id)
}

// 结算状态 -> el-tag type（与 SettlementEntity 状态机对齐：PENDING/SETTLING/SETTLED/ABNORMAL）
function statusTagType(status) {
  const map = { SETTLED: 'success', SETTLING: 'warning', PENDING: 'info', ABNORMAL: 'danger' }
  return map[status] || 'info'
}
function statusLabel(status) {
  const map = { SETTLED: '已结算', SETTLING: '结算中', PENDING: '待结算', ABNORMAL: '异常' }
  return map[status] || status || '未知'
}
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.summary-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
