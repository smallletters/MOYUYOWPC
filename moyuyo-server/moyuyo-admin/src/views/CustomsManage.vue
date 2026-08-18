<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header-left">
        <h2>清关管理</h2>
        <p>实时跟踪清关进度，管理异常工单，监控清关时效</p>
      </div>
      <div class="header-actions">
        <el-button v-if="activeTab === 'tariff'" type="primary" @click="handleSync">同步数据</el-button>
      </div>
    </div>

    <!-- Tab 切换：清关管理 / 税率管理 -->
    <div class="tab-switcher">
      <button class="tab-switcher-item" :class="{ active: activeTab === 'clearance' }" @click="switchTab('clearance')">清关管理</button>
      <button class="tab-switcher-item" :class="{ active: activeTab === 'tariff' }" @click="switchTab('tariff')">税率管理</button>
    </div>

    <!-- ==================== 清关管理 Tab ==================== -->
    <template v-if="activeTab === 'clearance'">
      <!-- 清关状态概览（6 状态卡片） -->
      <div class="overview-grid">
        <div class="overview-card" :class="'type-' + item.type" v-for="item in clearanceOverview" :key="item.label">
          <div class="overview-icon">
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
          </div>
          <div>
            <div class="overview-value">{{ item.value }}</div>
            <div class="overview-label">{{ item.label }}</div>
          </div>
        </div>
      </div>

      <!-- 清关进度跟踪 -->
      <div class="section-block">
        <div class="section-title">
          <el-icon class="section-icon" :size="18"><Van /></el-icon>
          <span>清关进度跟踪</span>
        </div>
        <div class="order-list">
          <div class="order-card" :class="{ 'order-card-error': order.status === 'abnormal' }" v-for="order in clearanceOrders" :key="order.orderNo">
            <div class="order-head">
              <div class="order-meta">
                <span class="order-no">{{ order.orderNo }}</span>
                <span class="country-tag">{{ order.country }}</span>
                <span class="order-agent">代理: {{ order.agent }}</span>
              </div>
              <span class="order-status-tag" :class="order.status === 'abnormal' ? 'tag-red' : 'tag-blue'">{{ order.status === 'abnormal' ? '异常' : '正常' }}</span>
            </div>
            <!-- 进度条：申报 → 审核 → 缴税 → 查验 → 放行 -->
            <div class="customs-progress">
              <template v-for="(step, idx) in order.steps" :key="step.name">
                <div class="customs-progress-node" :class="'node-' + step.state">
                  <div class="customs-progress-dot"></div>
                  <span class="customs-progress-label">{{ step.name }}</span>
                </div>
                <div v-if="idx < order.steps.length - 1" class="customs-progress-line"></div>
              </template>
            </div>
            <!-- 异常提示 -->
            <div v-if="order.abnormalReason" class="order-alert">
              <el-icon class="order-alert-icon" :size="16"><WarningFilled /></el-icon>
              <span>{{ order.abnormalReason }}</span>
            </div>
            <div class="order-footer" :class="{ 'released': order.releasedAt }">
              {{ order.releasedAt ? '已于 ' + order.releasedAt + ' 放行' : '预计放行: ' + order.expectedRelease }}
            </div>
          </div>
        </div>
      </div>

      <!-- 清关异常 + 清关时效统计（两栏） -->
      <div class="two-col-area">
        <!-- 清关异常 -->
        <div class="section-block">
          <div class="section-title-row">
            <div class="section-title">
              <el-icon class="section-icon" :size="18" style="color: var(--state-error)"><WarningFilled /></el-icon>
              <span>清关异常</span>
            </div>
            <span class="count-tag">{{ clearanceExceptions.length }} 条</span>
          </div>
          <div class="exception-list">
            <div class="exception-card" v-for="item in clearanceExceptions" :key="item.ticketNo">
              <div class="exception-head">
                <div class="exception-type">
                  <el-icon class="exception-type-icon" :class="'icon-' + item.statusType" :size="16">
                    <component :is="item.statusType === 'solved' ? CircleCheck : WarningFilled" />
                  </el-icon>
                  <span class="exception-type-name">{{ item.type }}</span>
                </div>
                <span class="exception-status" :class="'status-' + item.statusType">{{ item.status }}</span>
              </div>
              <div class="exception-meta">
                <span>工单号: {{ item.ticketNo }}</span>
                <span>订单号: {{ item.orderNo }}</span>
              </div>
              <p class="exception-desc">{{ item.description }}</p>
              <div class="exception-foot">
                <span class="exception-handler">处理人: {{ item.handler }}</span>
                <el-button size="small" round @click="handleViewException(item)">查看详情</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 清关时效统计 -->
        <div class="section-block">
          <div class="section-title">
            <el-icon class="section-icon" :size="18"><Clock /></el-icon>
            <span>清关时效统计</span>
          </div>
          <div class="timing-card">
            <!-- KPI：平均清关时长 + 时效达成率 -->
            <div class="timing-kpi">
              <div class="timing-kpi-item kpi-brand">
                <p class="kpi-value">{{ timingKpi.avgDays }}</p>
                <p class="kpi-label">平均清关时长(天)</p>
              </div>
              <div class="timing-kpi-item kpi-success">
                <p class="kpi-value">{{ timingKpi.achievementRate }}</p>
                <p class="kpi-label">时效达成率</p>
              </div>
            </div>

            <!-- 近 7 天清关时效（CSS 柱状图） -->
            <h3 class="timing-sub-title">近 7 天清关时效（天）</h3>
            <div class="week-bars">
              <div class="week-bar-group" v-for="item in weekTrend" :key="item.label">
                <div class="week-bar-track">
                  <div class="week-bar-fill" :style="{ height: barHeight(item.value) + '%' }" :title="item.label + ' ' + item.value + ' 天'"></div>
                </div>
                <span class="week-bar-value">{{ item.value }}</span>
                <span class="week-bar-label">{{ item.label }}</span>
              </div>
            </div>

            <!-- 各目的国平均时效对比（水平条形图） -->
            <h3 class="timing-sub-title">各目的国平均时效对比（天）</h3>
            <div class="hbar-list">
              <div class="hbar-item" v-for="item in countryTiming" :key="item.country">
                <div class="hbar-head">
                  <span class="hbar-name">{{ item.country }}</span>
                  <span class="hbar-count">{{ item.days }} 天</span>
                </div>
                <div class="hbar-track">
                  <div class="hbar-fill" :style="{ width: hbarWidth(item.days) + '%', background: item.color }"></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ==================== 税率管理 Tab ==================== -->
    <template v-else>
      <el-card shadow="never" class="filter-card">
        <el-form :model="filters" inline>
          <el-form-item label="关键词">
            <el-input v-model="filters.keyword" placeholder="请输入海关编码/商品名称" clearable />
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
          <el-table-column prop="hsCode" label="海关编码" width="130" />
          <el-table-column prop="productName" label="商品名称" width="180" />
          <el-table-column label="税率" width="100">
            <template #default="{ row }">{{ row.taxRate }}%</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
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
    </template>

    <!-- 编辑弹窗（税率管理） -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="海关编码">
          <el-input v-model="editForm.hsCode" placeholder="请输入海关编码" />
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="editForm.productName" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="税率(%)">
          <el-input-number v-model="editForm.taxRate" :min="0" :max="100" :step="0.1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width:100%">
            <el-option label="待申报" value="PENDING" />
            <el-option label="查验中" value="INSPECTING" />
            <el-option label="已放行" value="CLEARED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, QuestionFilled, PriceTag, WarningFilled, CircleCheck, CircleClose, Van, Clock } from '@element-plus/icons-vue'
import {
  getCustoms, syncCustoms, createCustoms, updateCustoms, deleteCustoms,
  getClearanceOverview, getClearanceExceptions, getClearanceDocs,
  getClearanceDailyDays, getClearanceCountryCompare
} from '../api/admin'

// ==================== 清关管理 Tab（真实后端：清关模块同源数据） ====================

// 清关状态概览（真实后端）— 状态分布用 overview 接口聚合
const clearanceOverview = ref([])
const clearanceOverviewIconMap = {
  PENDING: Document,
  INSPECTING: QuestionFilled,
  CLEARED: CircleCheck,
  REJECTED: CircleClose,
  TAXING: PriceTag,
  INSPECT: WarningFilled
}
const clearanceOverviewLabelMap = {
  PENDING: '待申报',
  INSPECTING: '查验中',
  CLEARED: '已放行',
  REJECTED: '已扣留',
  TAXING: '缴税中',
  INSPECT: '查验中'
}

// 清关进度跟踪（真实后端：取最近 5 条 clearance 数据）
const clearanceOrders = ref([])

// 清关异常（真实后端：复用 /clearance/exceptions）
const clearanceExceptions = ref([])

// 清关时效统计（真实后端）
const timingKpi = reactive({ avgDays: 0, achievementRate: '0%' })
const weekTrend = ref([])
const countryTiming = ref([])

const maxWeek = computed(() => Math.max(1, ...weekTrend.value.map(item => item.value)))
function barHeight(value) {
  return Math.round((value / maxWeek.value) * 100)
}
function hbarWidth(days) {
  return Math.round((days / 8) * 100)
}

// 异常工单查看详情（真实后端：弹窗展示）
function handleViewException(item) {
  ElMessage.success(`工单 ${item.ticketNo}（${item.type}）：${item.description || item.reason || '-'}（订单 ${item.orderNo}）`)
}

// 把后端 clearance 状态映射为前端步骤状态
function buildSteps(status) {
  // 简易映射：PENDING 申报；INSPECTING 查验；CLEARED 全部完成；REJECTED 查验异常
  const states = ['pending', 'pending', 'pending', 'pending', 'pending']
  if (status === 'PENDING') states[0] = 'completed'
  else if (status === 'INSPECTING') { states[0] = 'completed'; states[3] = 'active' }
  else if (status === 'CLEARED') states.fill('completed')
  else if (status === 'REJECTED') { states[0] = 'completed'; states[3] = 'error' }
  return [
    { name: '申报', state: states[0] },
    { name: '审核', state: states[1] },
    { name: '缴税', state: states[2] },
    { name: '查验', state: states[3] },
    { name: '放行', state: states[4] }
  ]
}

async function loadClearanceOverview() {
  try {
    const res = await getClearanceOverview()
    const items = [
      { key: 'PENDING', value: res?.pending || 0, type: 'brand' },
      { key: 'INSPECTING', value: res?.inspecting || 0, type: 'warning' },
      { key: 'CLEARED', value: res?.cleared || 0, type: 'success' },
      { key: 'REJECTED', value: res?.rejected || 0, type: 'error' },
      { key: 'avgDays', value: res?.avgDays || 0, type: 'brand', isAvg: true, label: '平均时效(天)' },
      { key: 'passRate', value: (res?.passRate || 0) + '%', type: 'success', isRate: true, label: '放行率' }
    ]
    clearanceOverview.value = items.map(it => ({
      label: it.isAvg || it.isRate ? it.label : (clearanceOverviewLabelMap[it.key] || it.key),
      value: it.value,
      type: it.type,
      icon: clearanceOverviewIconMap[it.key] || Document
    }))
  } catch (e) {
    console.error('获取清关概览失败:', e)
    clearanceOverview.value = []
  }
}

async function loadClearanceOrders() {
  try {
    const res = await getClearanceDocs({ page: 1, size: 5 })
    const list = res?.records || []
    clearanceOrders.value = list.map(c => ({
      orderNo: c.orderNo || c.declarationNo,
      country: c.country || '-',
      agent: c.handler || '系统',
      status: c.status === 'CLEARED' ? 'normal' : (c.status === 'REJECTED' ? 'abnormal' : 'normal'),
      abnormalReason: c.status === 'REJECTED' ? (c.exceptionReason || '清关被退回') : '',
      expectedRelease: c.clearanceTime ? String(c.clearanceTime).slice(0, 10) : (c.declareTime ? String(c.declareTime).slice(0, 10) : ''),
      releasedAt: c.status === 'CLEARED' && c.clearanceTime ? String(c.clearanceTime).slice(0, 10) : '',
      steps: buildSteps(c.status)
    }))
  } catch (e) {
    console.error('获取清关进度失败:', e)
    clearanceOrders.value = []
  }
}

async function loadClearanceExceptions() {
  try {
    const list = await getClearanceExceptions(20)
    clearanceExceptions.value = (list || []).map((item, idx) => ({
      ticketNo: 'EXC-' + String(item.id).padStart(8, '0'),
      orderNo: item.orderNo,
      type: item.status === 'REJECTED' ? '商品归类错误' : '查验异常',
      status: item.status === 'REJECTED' ? '已拒绝' : '查验中',
      statusType: item.status === 'REJECTED' ? 'pending' : 'processing',
      handler: item.handler || '系统',
      description: item.reason || (item.status === 'REJECTED' ? '清关被退回，请补充资料' : '清关查验中')
    }))
  } catch (e) {
    console.error('获取清关异常失败:', e)
    clearanceExceptions.value = []
  }
}

async function loadTimingStats() {
  try {
    const [overview, daily, country] = await Promise.all([
      getClearanceOverview(),
      getClearanceDailyDays(7),
      getClearanceCountryCompare()
    ])
    timingKpi.avgDays = overview?.avgDays ?? 0
    timingKpi.achievementRate = (overview?.passRate ?? 0) + '%'
    weekTrend.value = (daily || []).map(d => ({
      label: d.date ? String(d.date).slice(5) : '',
      value: d.avgDays || 0
    }))
    const palette = ['var(--brand-500)', 'var(--state-success)', 'var(--chart-4)', 'var(--state-warning)', 'var(--chart-5)', 'var(--state-error)']
    countryTiming.value = (country || []).map((c, i) => ({
      country: c.country,
      days: c.avgDays,
      color: palette[i % palette.length]
    }))
  } catch (e) {
    console.error('获取清关时效统计失败:', e)
  }
}

// ==================== Tab 切换 ====================
const activeTab = ref('clearance')
function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'clearance') {
    // 重新加载清关相关真实数据
    loadClearanceOverview()
    loadClearanceOrders()
    loadClearanceExceptions()
    loadTimingStats()
  } else {
    loadData()
  }
}

// ==================== 税率管理 Tab：原海关编码税率 CRUD ====================
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  hsCode: '',
  productName: '',
  taxRate: 0,
  status: 'PENDING'
})

// 海关状态映射
function statusLabel(s) {
  return { PENDING: '待申报', INSPECTING: '查验中', CLEARED: '已放行', REJECTED: '已拒绝' }[s] || s || '-'
}
function statusTagType(s) {
  if (s === 'CLEARED') return 'success'
  if (s === 'REJECTED') return 'danger'
  if (s === 'INSPECTING') return 'warning'
  return 'info'
}

// 加载海关数据
async function loadData() {
  try {
    const res = await getCustoms({ page: currentPage.value, size: pageSize.value })
    const list = Array.isArray(res) ? res : (res?.records || [])
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => (item.hsCode || '').includes(filters.keyword) || (item.productName || '').includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = res?.total ?? filtered.length
  } catch (error) {
    console.error('获取海关数据失败:', error)
    ElMessage.error('获取海关数据失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
// 同步海关数据（逐条同步）
async function handleSync() {
  if (tableData.value.length === 0) {
    ElMessage.info('没有可同步的数据')
    return
  }
  ElMessage.info('正在同步海关数据...')
  let successCount = 0
  let failCount = 0
  for (const item of tableData.value) {
    try {
      await syncCustoms(item.id)
      successCount++
    } catch (e) {
      failCount++
    }
  }
  ElMessage.success(`同步完成：成功 ${successCount} 条，失败 ${failCount} 条`)
  loadData()
}
function handleEdit(row) { dialogTitle.value = '编辑海关编码'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteCustoms(row.id)
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
      hsCode: editForm.hsCode,
      productName: editForm.productName,
      taxRate: editForm.taxRate,
      status: editForm.status
    }
    if (editForm.id) {
      await updateCustoms(editForm.id, payload)
    } else {
      await createCustoms(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}
onMounted(() => {
  // 默认进入清关管理 Tab，加载全部清关相关真实数据
  loadClearanceOverview()
  loadClearanceOrders()
  loadClearanceExceptions()
  loadTimingStats()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header-left h2 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0 0 4px; }
.page-header-left p { font-size: 13px; color: var(--text-400); margin: 0; }
.header-actions { display: flex; gap: 8px; }
.filter-card { margin-bottom: 16px; }
.tab-switcher { margin-bottom: 20px; }

/* ===== 清关状态概览（6 列卡片） ===== */
.overview-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.overview-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  box-shadow: var(--shadow-xs);
  display: flex;
  align-items: center;
  gap: 14px;
  transition: border-color 0.2s ease, transform 0.2s ease;
}
.overview-card:hover {
  border-color: var(--primary);
  transform: translateY(-1px);
}
.overview-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.overview-card.type-brand .overview-icon { background: var(--brand-50); color: var(--brand-500); }
.overview-card.type-warning .overview-icon { background: var(--state-warning-surface); color: var(--state-warning); }
.overview-card.type-success .overview-icon { background: var(--state-success-surface); color: var(--state-success); }
.overview-card.type-error .overview-icon { background: var(--state-error-surface); color: var(--state-error); }
.overview-value { font-size: 24px; font-weight: 700; line-height: 1.1; }
.overview-card.type-brand .overview-value { color: var(--brand-500); }
.overview-card.type-warning .overview-value { color: var(--state-warning); }
.overview-card.type-success .overview-value { color: var(--state-success); }
.overview-card.type-error .overview-value { color: var(--state-error); }
.overview-label { font-size: 13px; color: var(--text-500); margin-top: 2px; }

/* ===== 区块通用 ===== */
.section-block {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  box-shadow: var(--shadow-xs);
  margin-bottom: 24px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 16px;
}
.section-icon { color: var(--primary); }
.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.section-title-row .section-title { margin-bottom: 0; }
.count-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: var(--state-error-surface);
  color: var(--state-error);
}
.two-col-area {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
  align-items: start;
}

/* ===== 清关进度跟踪 ===== */
.order-list { display: flex; flex-direction: column; gap: 12px; }
.order-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px 20px;
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s ease;
}
.order-card:hover { border-color: var(--primary); }
.order-card-error { background: var(--state-error-surface); border-color: var(--state-error); }
.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.order-meta { display: flex; align-items: center; gap: 12px; }
.order-no { font-size: 14px; font-weight: 600; color: var(--text-800); }
.country-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: var(--brand-50);
  color: var(--brand-500);
}
.order-agent { font-size: 12px; color: var(--text-400); }
.order-status-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.order-status-tag.tag-blue { background: var(--brand-50); color: var(--brand-500); }
.order-status-tag.tag-red { background: var(--state-error); color: var(--state-error-foreground); }
.order-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: 10px;
  padding: 10px 14px;
  margin-top: 10px;
  background: var(--state-error-surface);
  font-size: 12px;
  color: var(--state-error);
}
.order-alert-icon { flex-shrink: 0; }
.order-footer { margin-top: 8px; font-size: 12px; color: var(--text-400); }
.order-footer.released { color: var(--state-success); }

/* ===== 清关进度条（时间线） ===== */
.customs-progress {
  display: flex;
  align-items: center;
  position: relative;
}
.customs-progress-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  z-index: 1;
  flex-shrink: 0;
}
.customs-progress-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid var(--background-300);
  background: var(--background-200);
  transition: all 0.2s ease;
}
.customs-progress-label {
  font-size: 10px;
  margin-top: 4px;
  color: var(--text-400);
  white-space: nowrap;
}
.customs-progress-line {
  flex: 1;
  height: 2px;
  background: var(--background-300);
  margin: 0 -2px;
  position: relative;
  top: -7px;
}
/* 前一个节点已完成 → 连接线变绿 */
.customs-progress-node.node-completed + .customs-progress-line { background: var(--state-success); }
.node-completed .customs-progress-dot { background: var(--state-success); border-color: var(--state-success); }
.node-completed .customs-progress-label { color: var(--state-success); font-weight: 600; }
.node-active .customs-progress-dot { background: var(--brand-500); border-color: var(--brand-500); box-shadow: 0 0 0 3px var(--brand-50); }
.node-active .customs-progress-label { color: var(--brand-500); font-weight: 600; }
.node-error .customs-progress-dot { background: var(--state-error); border-color: var(--state-error); box-shadow: 0 0 0 3px var(--state-error-surface); }
.node-error .customs-progress-label { color: var(--state-error); font-weight: 600; }
.node-pending .customs-progress-dot { background: var(--background-200); border-color: var(--background-300); }
.node-pending .customs-progress-label { color: var(--text-400); }

/* ===== 清关异常 ===== */
.exception-list { display: flex; flex-direction: column; gap: 12px; }
.exception-card {
  background: var(--card);
  border: 1px solid var(--state-error);
  border-radius: var(--radius);
  padding: 16px 20px;
  box-shadow: var(--shadow-xs);
}
.exception-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.exception-type { display: flex; align-items: center; gap: 8px; }
.exception-type-icon { flex-shrink: 0; color: var(--state-error); }
.exception-type-icon.icon-success { color: var(--state-success); }
.exception-type-name { font-size: 14px; font-weight: 600; color: var(--text-800); }
.exception-status {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.exception-status.status-pending { background: var(--state-error); color: var(--state-error-foreground); }
.exception-status.status-processing { background: var(--state-warning); color: var(--state-warning-foreground); }
.exception-status.status-solved { background: var(--state-success); color: var(--state-success-foreground); }
.exception-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 8px;
  color: var(--text-500);
}
.exception-desc {
  font-size: 12px;
  margin: 0 0 12px;
  color: var(--text-600);
  line-height: 1.6;
}
.exception-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.exception-handler { font-size: 12px; color: var(--text-400); }

/* ===== 清关时效统计 ===== */
.timing-card { background: var(--card); }
.timing-kpi {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 20px;
}
.timing-kpi-item {
  border-radius: 10px;
  padding: 16px;
  text-align: center;
}
.kpi-brand { background: var(--brand-50); }
.kpi-success { background: var(--state-success-surface); }
.kpi-value {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
  line-height: 1.1;
}
.kpi-brand .kpi-value { color: var(--brand-500); }
.kpi-success .kpi-value { color: var(--state-success); }
.kpi-label {
  font-size: 12px;
  margin: 4px 0 0;
  color: var(--text-500);
}
.timing-sub-title {
  font-size: 12px;
  font-weight: 600;
  margin: 0 0 14px;
  color: var(--text-600);
}

/* 近 7 天时效柱状图 */
.week-bars {
  display: flex;
  align-items: stretch;
  gap: 8px;
  height: 110px;
  margin-bottom: 4px;
}
.week-bar-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.week-bar-track {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.week-bar-fill {
  width: 18px;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, var(--brand-400), var(--brand-500));
  transition: height 0.3s ease;
}
.week-bar-value { font-size: 10px; font-weight: 600; color: var(--text-500); }
.week-bar-label { font-size: 10px; color: var(--text-400); }

/* 各目的国平均时效对比（水平条形图） */
.hbar-list { display: flex; flex-direction: column; gap: 12px; }
.hbar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.hbar-name { font-size: 12px; font-weight: 500; color: var(--text-600); }
.hbar-count { font-size: 12px; font-weight: 700; color: var(--text-800); }
.hbar-track {
  width: 100%;
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--background-200);
}
.hbar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}
</style>
