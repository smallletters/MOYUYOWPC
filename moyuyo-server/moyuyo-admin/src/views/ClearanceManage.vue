<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建</el-button>
      </div>
    </div>

    <!-- ===== 异常告警区块（示例数据） ===== -->
    <el-card shadow="never" class="section-card">
      <div class="section-header">
        <h3>异常告警</h3>
        <span class="alert-badge">{{ alertList.length }} 条待处理</span>
      </div>
      <div class="alert-list">
        <div v-for="item in alertList" :key="item.orderNo" class="alert-item" :class="item.level === 'high' ? 'is-danger' : 'is-warning'">
          <div class="alert-icon" :class="item.level === 'high' ? 'is-danger' : 'is-warning'">{{ item.level === 'high' ? '!' : '⚠' }}</div>
          <div class="alert-info">
            <p class="alert-title">
              {{ item.orderNo }}
              <span class="alert-type" :class="item.level === 'high' ? 'is-danger' : 'is-warning'">{{ item.type }}</span>
            </p>
            <p class="alert-desc">{{ item.desc }}</p>
          </div>
          <div class="alert-actions">
            <span class="alert-time">{{ item.time }}</span>
            <el-button size="small" :type="item.level === 'high' ? 'danger' : 'warning'" plain @click="handleAlert(item)">处理</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ===== 清关订单列表（保留现有 CRUD） ===== -->
    <div class="section-header">
      <h3>清关订单列表</h3>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入报关单号/订单号" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="declarationNo" label="报关单号" width="160" />
        <el-table-column prop="orderNo" label="订单号" width="160" />
        <el-table-column prop="productName" label="商品名称" width="140" />
        <el-table-column label="清关状态" width="120">
          <template #default="{ row }">
            <el-tag :type="clearanceTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="declarationTime" label="申报时间" width="160" />
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

    <!-- ===== 单据管理区块（示例数据） ===== -->
    <el-card shadow="never" class="section-card">
      <div class="section-header">
        <h3>单据管理</h3>
      </div>
      <div class="tab-switcher">
        <button v-for="t in docTabs" :key="t.key" class="tab-switcher-item" :class="{ active: activeDocTab === t.key }" @click="activeDocTab = t.key">{{ t.label }}</button>
      </div>
      <el-table :data="activeDocList" stripe>
        <el-table-column :label="docNoLabel" prop="docNo" width="170" />
        <el-table-column label="关联订单" prop="orderNo" width="170" />
        <!-- 商业发票：金额列 -->
        <el-table-column v-if="activeDocTab === 'invoice'" label="金额" prop="amount" width="120" />
        <!-- 装箱单：件数 + 重量列 -->
        <template v-if="activeDocTab === 'packing'">
          <el-table-column label="件数" prop="pieces" width="80" />
          <el-table-column label="重量" prop="weight" width="110" />
        </template>
        <!-- 报关单：海关编码列 -->
        <el-table-column v-if="activeDocTab === 'declaration'" label="海关编码" prop="hsCode" width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="docStatusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="activeDocTab !== 'packing'" :label="docTimeLabel" prop="time" width="170" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDocAction(row)">{{ row.actionText }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ===== 清关时效统计区块（示例数据，纯 CSS 图表） ===== -->
    <el-card shadow="never" class="section-card">
      <div class="section-header">
        <h3>清关时效统计</h3>
      </div>
      <!-- KPI 指标卡片 -->
      <div class="stat-kpi-grid">
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">⏱</span>
            <span class="kpi-card-label">平均清关时效</span>
          </div>
          <div class="kpi-card-value">{{ kpiOverview.avgDays }}<span class="kpi-unit">天</span></div>
          <div class="kpi-trend-text" style="font-size:12px;margin-top:6px;">基于近 30 天已放行数据</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">🎯</span>
            <span class="kpi-card-label">放行率</span>
          </div>
          <div class="kpi-card-value" style="color: var(--state-success);">{{ kpiOverview.passRate }}<span class="kpi-unit">%</span></div>
          <div class="kpi-trend-text" style="font-size:12px;margin-top:6px;">已放行 / 全部</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">🚩</span>
            <span class="kpi-card-label">目标时效</span>
          </div>
          <div class="kpi-card-value">5<span class="kpi-unit">天</span></div>
          <div class="kpi-trend-text" style="font-size:12px;margin-top:6px;">SLA 承诺时效</div>
        </div>
      </div>
      <!-- 近 7 天时效 CSS 柱状图 -->
      <p class="chart-subtitle">近 7 天清关时效（小时）</p>
      <div class="bar-chart">
        <div v-for="d in weekData" :key="d.day" class="bar-item">
          <div class="bar-value">{{ d.hours }}h</div>
          <div class="bar-fill" :style="{ height: (d.hours / weekMax * 70) + '%' }"></div>
          <div class="bar-label">{{ d.day }}</div>
        </div>
      </div>
      <!-- 各环节耗时横向条形图 -->
      <p class="chart-subtitle">各环节平均耗时</p>
      <div class="h-bar-list">
        <div v-for="s in stageData" :key="s.name" class="h-bar-row">
          <span class="h-bar-name">{{ s.name }}</span>
          <div class="h-bar-track">
            <div class="h-bar-fill" :style="{ width: (s.hours / stageMax * 100) + '%', background: s.color }"></div>
          </div>
          <span class="h-bar-value">{{ s.hours }}h</span>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="报关单号">
          <el-input v-model="editForm.declarationNo" placeholder="请输入报关单号" />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="editForm.orderNo" placeholder="请输入订单号" />
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="editForm.productName" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="清关状态">
          <el-select v-model="editForm.status">
            <el-option label="待申报" value="待申报" />
            <el-option label="申报中" value="申报中" />
            <el-option label="已放行" value="已放行" />
            <el-option label="被查验" value="被查验" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 单据操作弹窗（查看 / 重新上传 / 重新提交） -->
    <el-dialog
      v-model="docActionDialogVisible"
      :title="docActionMode === 'upload' ? '重新上传单据' : docActionMode === 'resubmit' ? '重新提交单据' : '单据详情'"
      width="480px"
    >
      <div v-if="currentDoc">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="单据号">{{ currentDoc.docNo }}</el-descriptions-item>
          <el-descriptions-item label="关联订单">{{ currentDoc.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ currentDoc.status }}</el-descriptions-item>
          <el-descriptions-item label="操作" v-if="docActionMode === 'upload'">
            <el-input v-model="uploadForm.fileName" placeholder="请输入文件名称" />
          </el-descriptions-item>
          <el-descriptions-item label="备注" v-else-if="docActionMode === 'resubmit'">
            <el-input v-model="uploadForm.remark" type="textarea" :rows="2" placeholder="重新提交说明" />
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="docActionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmDocAction">
          {{ docActionMode === 'view' ? '关闭' : '确认' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getClearance, createClearance, updateClearance, deleteClearance,
  getClearanceOverview, getClearanceExceptions, getClearanceDailyDays,
  getClearanceCountryCompare
} from '../api/admin'

const pageTitle = '清关管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  declarationNo: '',
  orderNo: '',
  productName: '',
  status: '待申报'
})

// 状态映射：后端 PENDING/INSPECTING/CLEARED/REJECTED → 中文
const statusLabelMap = {
  PENDING: '待申报',
  INSPECTING: '申报中',
  CLEARED: '已放行',
  REJECTED: '已退回'
}

function clearanceTagType(status) {
  const map = { '待申报': 'info', '申报中': 'warning', '已放行': 'success', '被查验': 'danger', '已退回': 'danger' }
  return map[statusLabelMap[status] || status] || 'info'
}

// ===== 异常告警（真实后端 /clearance/exceptions） =====
const alertList = ref([])

// ===== KPI / 时效（真实后端） =====
const kpiOverview = reactive({ avgDays: 0, passRate: 0 })
const weekData = ref([])
const weekMax = computed(() => Math.max(1, ...weekData.value.map(d => d.hours)))
const stageData = computed(() => {
  // 用国家对比数据简化映射为环节（最多 5 个）
  return getClearanceCountryCompareCached.value.slice(0, 5).map((c, i) => ({
    name: c.country,
    hours: c.avgDays,
    color: ['#2e8dff', '#ff9500', '#5856d6', '#ff3b30', '#34c759'][i] || '#2e8dff'
  }))
})
const stageMax = computed(() => Math.max(1, ...stageData.value.map(s => s.hours || 1)))
const getClearanceCountryCompareCached = ref([])

// 处理告警：根据真实数据 row.id 调 updateClearance（标记为 INSPECTING 视为处理中）
async function handleAlert(row) {
  try {
    await ElMessageBox.confirm(
      '将根据告警【' + (row.reason || row.status) + '】为订单 ' + row.orderNo + ' 发起处理流程，是否继续？',
      '处理告警',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateClearance(row.id, { status: 'INSPECTING' })
    ElMessage.success('已发起处理：' + row.orderNo)
    await loadAll()
  } catch (e) {
    ElMessage.error('处理失败：' + (e?.message || '未知错误'))
  }
}

// ===== 单据管理（基于清关真实数据派生，三 Tab 共用） =====
const docTabs = [
  { key: 'invoice', label: '商业发票' },
  { key: 'packing', label: '装箱单' },
  { key: 'declaration', label: '报关单' }
]
const activeDocTab = ref('invoice')
const docDataMap = computed(() => {
  const map = { invoice: [], packing: [], declaration: [] }
  for (const c of tableData.value) {
    const status = statusLabelMap[c.status] || c.status
    map.invoice.push({
      docNo: 'INV-' + (c.declarationNo || c.id),
      orderNo: c.orderNo,
      amount: c.taxRate ? c.taxRate + '%' : '-',
      status,
      time: c.declareTime ? String(c.declareTime).slice(0, 16).replace('T', ' ') : '',
      actionText: status === '已退回' ? '重新提交' : '查看'
    })
    map.packing.push({
      docNo: 'PK-' + (c.declarationNo || c.id),
      orderNo: c.orderNo,
      pieces: '-',
      weight: '-',
      status,
      time: '',
      actionText: '查看'
    })
    map.declaration.push({
      docNo: c.declarationNo,
      orderNo: c.orderNo,
      hsCode: c.hsCode || '-',
      status,
      time: c.declareTime ? String(c.declareTime).slice(0, 16).replace('T', ' ') : '',
      actionText: status === '已退回' ? '重新提交' : '查看'
    })
  }
  return map
})
// 当前 Tab 对应的单据列表
const activeDocList = computed(() => docDataMap.value[activeDocTab.value] || [])
// 单号列与时间列标题随 Tab 变化
const docNoLabel = computed(() => ({ invoice: '发票编号', packing: '装箱单编号', declaration: '报关单号' }[activeDocTab.value]))
const docTimeLabel = computed(() => ({ invoice: '上传时间', declaration: '提交时间' }[activeDocTab.value]))

function docStatusTagType(status) {
  const map = { '待补充': 'warning', '审核中': 'warning', '已通过': 'success', '已确认': 'success', '待确认': 'warning', '已退回': 'danger', '待申报': 'info', '申报中': 'warning', '已放行': 'success', '被查验': 'danger' }
  return map[status] || 'info'
}

// 单据操作
const docActionDialogVisible = ref(false)
const docActionMode = ref('view')
const currentDoc = ref(null)

function handleDocAction(row) {
  currentDoc.value = row
  if (row.actionText === '重新上传') docActionMode.value = 'upload'
  else if (row.actionText === '重新提交') docActionMode.value = 'resubmit'
  else docActionMode.value = 'view'
  docActionDialogVisible.value = true
}

const uploadForm = reactive({ fileName: '', remark: '' })

async function confirmDocAction() {
  if (!currentDoc.value) return
  try {
    if (docActionMode.value === 'upload' || docActionMode.value === 'resubmit') {
      // 把对应清关记录状态置为 INSPECTING（处理中）
      const matched = tableData.value.find(c => c.declarationNo === currentDoc.value.docNo.replace(/^INV-|^PK-/, ''))
      if (matched) {
        await updateClearance(matched.id, { status: 'INSPECTING' })
      }
      ElMessage.success('已' + (docActionMode.value === 'upload' ? '重新上传' : '重新提交') + '：' + currentDoc.value.docNo)
      await loadData()
    } else {
      ElMessage.info('已查看：' + currentDoc.value.docNo)
    }
    docActionDialogVisible.value = false
  } catch (e) {
    ElMessage.error('操作失败：' + (e?.message || '未知错误'))
  }
}

// 加载报关数据
async function loadData() {
  try {
    const res = await getClearance({ page: currentPage.value, size: pageSize.value })
    const list = Array.isArray(res) ? res : (res?.records || [])
    let filtered = [...list]
    if (filters.keyword) {
      const kw = filters.keyword
      filtered = filtered.filter(item => (item.declarationNo || '').includes(kw) || (item.orderNo || '').includes(kw))
    }
    // 翻译状态为中文以兼容前端的 tag 映射
    tableData.value = filtered.map(item => ({ ...item, status: statusLabelMap[item.status] || item.status }))
    total.value = res?.total ?? filtered.length
  } catch (error) {
    console.error('获取报关数据失败:', error)
    ElMessage.error('获取报关数据失败')
  }
}

// 加载异常告警
async function loadAlerts() {
  try {
    const list = await getClearanceExceptions(20)
    alertList.value = (list || []).map(item => ({
      id: item.id,
      orderNo: item.declarationNo || item.orderNo,
      type: item.statusType === 'error' ? '扣留' : item.statusType === 'warning' ? '查验中' : '审核',
      level: item.statusType === 'error' ? 'high' : 'mid',
      desc: item.reason || (item.status === 'REJECTED' ? '清关被退回，请尽快补充资料' : '清关查验中'),
      time: item.declareTime ? String(item.declareTime).slice(0, 16).replace('T', ' ') : '',
      ...item
    }))
  } catch (e) {
    console.error('获取清关异常失败:', e)
    alertList.value = []
  }
}

// 加载 KPI 概览
async function loadOverview() {
  try {
    const res = await getClearanceOverview()
    kpiOverview.avgDays = res?.avgDays ?? 0
    kpiOverview.passRate = res?.passRate ?? 0
  } catch (e) {
    console.error('获取清关概览失败:', e)
  }
}

// 加载近 7 天时效
async function loadDailyDays() {
  try {
    const list = await getClearanceDailyDays(7)
    weekData.value = (list || []).map(d => ({
      day: d.date ? String(d.date).slice(5) : '',
      hours: Math.round((d.avgDays || 0) * 24)
    }))
  } catch (e) {
    console.error('获取清关时效失败:', e)
    weekData.value = []
  }
}

// 加载国家对比
async function loadCountryCompare() {
  try {
    const list = await getClearanceCountryCompare()
    getClearanceCountryCompareCached.value = list || []
  } catch (e) {
    console.error('获取国家清关时效失败:', e)
    getClearanceCountryCompareCached.value = []
  }
}

async function loadAll() {
  await Promise.all([loadData(), loadAlerts(), loadOverview(), loadDailyDays(), loadCountryCompare()])
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建清关申报'; editForm.declarationNo = ''; editForm.orderNo = ''; editForm.productName = ''; editForm.status = '待申报'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑清关'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteClearance(row.id)
    ElMessage.success('删除成功')
    await loadAll()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}
async function handleSave() {
  try {
    // 中文 → 英文
    const statusMap = { '待申报': 'PENDING', '申报中': 'INSPECTING', '已放行': 'CLEARED', '被查验': 'INSPECTING' }
    const payload = {
      declarationNo: editForm.declarationNo,
      orderNo: editForm.orderNo,
      productName: editForm.productName,
      status: statusMap[editForm.status] || 'PENDING'
    }
    if (editForm.id) {
      await updateClearance(editForm.id, payload)
    } else {
      await createClearance(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadAll()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}
onMounted(() => loadAll())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }

/* 区块标题与间距 */
.section-card { margin-bottom: 16px; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.section-header h3 { font-size: 15px; font-weight: 600; color: var(--text-800); margin: 0; }

/* ===== 异常告警列表 ===== */
.alert-badge { padding: 3px 10px; border-radius: 999px; background: var(--state-error-surface); color: var(--state-error); font-size: 12px; font-weight: 600; }
.alert-list { display: flex; flex-direction: column; gap: 12px; }
.alert-item { display: flex; align-items: flex-start; gap: 12px; padding: 12px 16px; border-radius: var(--radius); border: 1px solid; }
.alert-item.is-danger { background: var(--state-error-surface); border-color: var(--state-error); }
.alert-item.is-warning { background: var(--state-warning-surface); border-color: var(--state-warning); }
.alert-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0; }
.alert-icon.is-danger { background: var(--state-error-surface); color: var(--state-error); }
.alert-icon.is-warning { background: var(--state-warning-surface); color: var(--state-warning); }
.alert-info { flex: 1; min-width: 0; }
.alert-title { font-size: 14px; font-weight: 600; color: var(--text-800); margin: 0; display: flex; align-items: center; gap: 8px; }
.alert-type { padding: 1px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; }
.alert-type.is-danger { background: var(--state-error-surface); color: var(--state-error); }
.alert-type.is-warning { background: var(--state-warning-surface); color: var(--state-warning); }
.alert-desc { font-size: 12px; color: var(--text-400); margin: 4px 0 0; }
.alert-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.alert-time { font-size: 12px; color: var(--text-400); }

/* ===== 单据管理 Tab ===== */
.tab-switcher { margin-bottom: 16px; }

/* ===== 清关时效统计 ===== */
.stat-kpi-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 8px; }
.chart-subtitle { font-size: 13px; font-weight: 600; color: var(--text-700); margin: 20px 0 8px; }
/* 各环节耗时横向条形图（纯 CSS） */
.h-bar-list { display: flex; flex-direction: column; gap: 10px; }
.h-bar-row { display: flex; align-items: center; gap: 12px; }
.h-bar-name { width: 72px; font-size: 12px; color: var(--text-500); text-align: right; flex-shrink: 0; }
.h-bar-track { flex: 1; height: 18px; background: var(--background-200); border-radius: 999px; overflow: hidden; }
.h-bar-fill { height: 100%; border-radius: 999px; transition: width 0.3s ease; }
.h-bar-value { width: 48px; font-size: 12px; font-weight: 600; color: var(--text-600); flex-shrink: 0; }
</style>
