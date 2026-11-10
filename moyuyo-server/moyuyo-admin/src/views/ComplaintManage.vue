<template>
  <div class="page-wrapper">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>投诉管理</h1>
      <p>跟踪用户投诉的处理进度，保障服务质量与售后体验</p>
    </div>

    <!-- KPI 概览 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🔔</span>
          <span class="kpi-card-label">待处理</span>
        </div>
        <div class="kpi-card-value">{{ countByStatus('待处理') }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-down">需尽快响应</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">⏳</span>
          <span class="kpi-card-label">处理中</span>
        </div>
        <div class="kpi-card-value">{{ countByStatus('处理中') }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">进行中</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">✅</span>
          <span class="kpi-card-label">已完结</span>
        </div>
        <div class="kpi-card-value">{{ countByStatus('已完结') }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-up">已完成</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">📋</span>
          <span class="kpi-card-label">投诉总数</span>
        </div>
        <div class="kpi-card-value">{{ total }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">累计记录</span></div>
      </div>
    </div>

    <!-- 查询面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>关键词</label>
          <input v-model="filters.keyword" type="text" placeholder="投诉编号/投诉人" @keyup.enter="handleSearch" />
        </div>
        <div class="form-group">
          <label>投诉类型</label>
          <select v-model="filters.type" class="select-wrapper">
            <option value="">全部类型</option>
            <option value="商品质量">商品质量</option>
            <option value="服务态度">服务态度</option>
            <option value="物流问题">物流问题</option>
            <option value="虚假宣传">虚假宣传</option>
            <option value="其他">其他</option>
          </select>
        </div>
        <div class="form-group">
          <label>状态</label>
          <select v-model="filters.status" class="select-wrapper">
            <option value="">全部状态</option>
            <option value="待处理">待处理</option>
            <option value="处理中">处理中</option>
            <option value="已完结">已完结</option>
          </select>
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" @click="handleSearch">搜索</button>
          <button class="btn btn-outline" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <span class="list-title">投诉列表</span>
      <button class="btn btn-primary" @click="handleAdd">＋ 新增投诉记录</button>
    </div>

    <!-- 投诉表格 -->
    <div class="data-table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th>投诉编号</th>
            <th>投诉人</th>
            <th>被投诉对象</th>
            <th>投诉类型</th>
            <th>状态</th>
            <th>提交时间</th>
            <th style="min-width: 150px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in tableData" :key="row.id">
            <td class="mono-cell">{{ row.complaintNo }}</td>
            <td>{{ row.complainant }}</td>
            <td>{{ row.defendant }}</td>
            <td><span class="tag tag-blue">{{ row.type }}</span></td>
            <td><span :class="statusPillClass(row.status)">{{ row.status }}</span></td>
            <td class="time-cell">{{ row.submitTime }}</td>
            <td>
              <div class="cell-actions">
                <button class="btn btn-sm btn-outline" @click="handleEdit(row)">处理</button>
                <button class="btn btn-sm btn-primary" @click="handleDetail(row)">详情</button>
              </div>
            </td>
          </tr>
          <tr v-if="tableData.length === 0">
            <td colspan="7">
              <div class="empty-state">
                <div class="empty-state-icon">📋</div>
                <div class="empty-state-text">暂无投诉记录</div>
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

      <!-- 最近处理记录与投诉趋势 - 双列布局 -->
      <div class="chart-grid">
        <!-- 最近处理记录 - 时间线（真实数据） -->
        <section class="chart-section">
          <div class="section-title">最近处理记录</div>
          <div class="section-subtitle">最新 10 条投诉处理流水</div>
          <div class="timeline">
          <div v-for="node in escalationChain" :key="node.level" :class="['timeline-node', node.status]">
            <div :class="['timeline-dot', node.status]">{{ node.level }}</div>
            <div class="timeline-content">
              <div class="timeline-title">{{ node.title }}</div>
              <div class="timeline-desc">{{ node.desc }}</div>
              <div class="timeline-time">{{ node.time }} 处理人: {{ node.handler }}</div>
            </div>
          </div>
        </div>
      </section>

      <!-- 投诉趋势图 - 近7天 CSS 柱状图（真实数据） -->
      <section class="chart-section">
        <div class="section-title">投诉趋势（近7天）</div>
        <div class="chart-container">
          <div class="chart-head">
            <span class="chart-head-label">日投诉量</span>
            <span class="chart-head-avg">日均 {{ avgDaily }} 件</span>
          </div>
          <div class="bar-chart">
            <div v-for="bar in trendData" :key="bar.label" class="bar-col">
              <span class="bar-value">{{ bar.value }}</span>
              <div class="bar-fill" :style="{ height: bar.height, background: bar.color }"></div>
              <span class="bar-label">{{ bar.label }}</span>
            </div>
          </div>
          <!-- 图例 -->
          <div class="chart-legend">
            <div class="legend-item">
              <span class="legend-dot" style="background: var(--brand-500);"></span>正常
            </div>
            <div class="legend-item">
              <span class="legend-dot" style="background: var(--state-warning);"></span>偏高
            </div>
            <div class="legend-item">
              <span class="legend-dot" style="background: var(--state-error);"></span>预警
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 新增/处理弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="投诉编号">
          <el-input v-model="editForm.complaintNo" disabled />
        </el-form-item>
        <el-form-item label="投诉人" required>
          <el-input v-model="editForm.complainant" />
        </el-form-item>
        <el-form-item label="被投诉对象" required>
          <el-input v-model="editForm.defendant" />
        </el-form-item>
        <el-form-item label="投诉类型" required>
          <el-select v-model="editForm.type" placeholder="请选择" style="width:100%">
            <el-option label="商品质量" value="商品质量" />
            <el-option label="服务态度" value="服务态度" />
            <el-option label="物流问题" value="物流问题" />
            <el-option label="虚假宣传" value="虚假宣传" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="投诉内容">
          <el-input v-model="editForm.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="editForm.status" placeholder="请选择" style="width:100%">
            <el-option label="待处理" value="待处理" />
            <el-option label="处理中" value="处理中" />
            <el-option label="已完结" value="已完结" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="3" />
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getComplaintList, createComplaint, startComplaintProcess, closeComplaint, getComplaintTrend, getComplaintProcessRecent } from '../api/admin'

const router = useRouter()
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const filters = reactive({
  keyword: '',
  type: '',
  status: ''
})

const editForm = reactive({
  id: null,
  complaintNo: '',
  complainant: '',
  defendant: '',
  type: '',
  content: '',
  status: '待处理',
  remark: '',
  submitTime: ''
})

const tableData = ref([])
const allList = ref([])

// 最近投诉处理记录（升级链路时间线，来自 /complaint/process-recent）
const escalationChain = ref([])

// 投诉趋势（近7天，来自 /complaint/trend）
const trendData = ref([])

// 近7天日均投诉量（基于真实趋势数据）
const avgDaily = computed(() => {
  if (!trendData.value.length) return '0.0'
  const sum = trendData.value.reduce((acc, item) => acc + item.value, 0)
  return (sum / trendData.value.length).toFixed(1)
})

// 加载最近处理记录
async function loadProcessRecent() {
  try {
    const list = await getComplaintProcessRecent(10)
    escalationChain.value = (list || []).map((p, idx) => ({
      level: 'L' + (idx + 1),
      status: 'completed',
      title: `${p.action || '处理'} · ${p.complaintNo || ''}`,
      desc: p.remark || '-',
      time: p.createTime ? String(p.createTime).replace('T', ' ').slice(0, 16) : '-',
      handler: p.operator || '-'
    }))
  } catch (e) {
    console.error('获取投诉处理记录失败', e)
    escalationChain.value = []
  }
}

// 加载近7天投诉趋势
async function loadTrend() {
  try {
    const list = await getComplaintTrend(7)
    const max = Math.max(...(list || []).map(i => i.count), 1)
    trendData.value = (list || []).map(i => {
      const value = Number(i.count || 0)
      const ratio = value / max
      // 柱高按最大值归一化；颜色按占比分级（>=80% 预警 / >=50% 偏高）
      return {
        label: i.label,
        value,
        height: Math.max(6, Math.round(ratio * 100)) + '%',
        color: ratio >= 0.8 ? 'var(--state-error)' : ratio >= 0.5 ? 'var(--state-warning)' : 'var(--brand-500)'
      }
    })
  } catch (e) {
    console.error('获取投诉趋势失败', e)
    trendData.value = []
  }
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function statusPillClass(status) {
  const map = { '待处理': 'tag tag-red', '处理中': 'tag tag-warning', '已完结': 'tag tag-green' }
  return map[status] || 'tag tag-gray'
}

// KPI 统计（基于全量数据推导）
function countByStatus(status) {
  return allList.value.filter(d => d.status === status).length
}

// 从API加载投诉列表数据
async function loadData() {
  try {
    const res = await getComplaintList()
    // 后端返回 {list: [...], total, page, size} 格式
    const rawList = (res && res.list) || []
    // 转换为前端表格期望的格式
    allList.value = rawList.map(item => ({
      id: item.id,
      complaintNo: 'CP' + item.id,
      complainant: item.userId ? '用户' + item.userId : '',
      defendant: item.type || '',
      type: item.type || '',
      content: item.content || '',
      status: ({'PENDING':'待处理','PROCESSING':'处理中','CLOSED':'已完结','RESOLVED':'已完结'})[item.status] || item.status || '待处理',
      submitTime: item.createTime || '',
      remark: item.remark || ''
    }))
    applyFilters()
  } catch (e) {
    console.error('加载投诉列表失败:', e)
    ElMessage.error('加载投诉列表失败')
  }
}

// 前端过滤 + 分页
function applyFilters() {
  let list = [...allList.value]
  if (filters.keyword) {
    const kw = filters.keyword.toLowerCase()
    list = list.filter(d => (d.complaintNo && d.complaintNo.toLowerCase().includes(kw)) || (d.complainant && d.complainant.includes(kw)))
  }
  if (filters.type) {
    list = list.filter(d => d.type === filters.type)
  }
  if (filters.status) {
    list = list.filter(d => d.status === filters.status)
  }
  total.value = list.length
  const start = (currentPage.value - 1) * pageSize.value
  tableData.value = list.slice(start, start + pageSize.value)
}

function handleSearch() { currentPage.value = 1; applyFilters() }

function handleReset() {
  filters.keyword = ''; filters.type = ''; filters.status = ''
  handleSearch()
}

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增投诉记录'
  editForm.id = null
  editForm.complaintNo = ''
  editForm.complainant = ''
  editForm.defendant = ''
  editForm.type = ''
  editForm.content = ''
  editForm.status = '待处理'
  editForm.remark = ''
  editForm.submitTime = ''
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '处理投诉'
  Object.assign(editForm, row)
  dialogVisible.value = true
}

function handleDetail(row) {
  // 跳转到投诉处理详情页，并携带工单 ID，详情页可基于此 ID 拉取详细数据
  router.push({ path: '/complaint-handle', query: { id: row.id } })
}

// 保存投诉（调用API）
async function handleSave() {
  if (!editForm.complainant || !editForm.defendant || !editForm.type) {
    ElMessage.warning('请填写必要信息')
    return
  }
  try {
    if (isEdit.value) {
      // 更新投诉状态
      if (editForm.status === '已完结') {
        await closeComplaint(editForm.id)
      } else {
        await startComplaintProcess(editForm.id)
      }
      ElMessage.success('保存成功')
    } else {
      // 新建投诉，调用 CREATE 端点
      await createComplaint({
        type: editForm.type,
        content: editForm.content,
        userId: editForm.complainant
      })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存投诉失败:', e)
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  loadData()
  loadProcessRecent()
  loadTrend()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }

/* 操作栏 */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.list-title { font-size: 14px; font-weight: 600; color: var(--text-600); }

.mono-cell {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-600);
}
.time-cell {
  font-size: 12px;
  color: var(--text-500);
  font-variant-numeric: tabular-nums;
}

/* ===== 升级链路与投诉趋势 - 双列布局 ===== */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.chart-section {
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.section-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 6px;
}

.section-subtitle {
  font-size: 13px;
  margin-bottom: 14px;
  color: var(--text-400);
}

/* ===== 升级链路时间线 ===== */
.timeline {
  padding: 4px 0;
}

.timeline-node {
  display: flex;
  gap: 14px;
  position: relative;
  padding-bottom: 24px;
}

.timeline-node:last-child {
  padding-bottom: 0;
}

.timeline-node:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 15px;
  top: 36px;
  width: 2px;
  height: calc(100% - 36px);
  background: var(--background-300);
}

.timeline-node.completed:not(:last-child)::after {
  background: var(--state-success);
}

.timeline-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  z-index: 1;
}

.timeline-dot.completed {
  background: var(--state-success);
  color: var(--state-success-foreground);
}

.timeline-dot.current {
  background: var(--primary);
  color: var(--primary-foreground);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.2);
}

.timeline-dot.pending {
  background: var(--background-300);
  color: var(--text-400);
}

.timeline-content {
  flex: 1;
  min-width: 0;
}

.timeline-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
}

.timeline-desc {
  font-size: 13px;
  color: var(--text-500);
  margin-top: 2px;
  line-height: 1.5;
}

.timeline-time {
  font-size: 12px;
  color: var(--text-400);
  margin-top: 4px;
}

/* ===== 投诉趋势 - 纯 CSS 柱状图 ===== */
.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.chart-head-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-500);
}

.chart-head-avg {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-400);
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 160px;
  padding-top: 10px;
}

.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
  justify-content: flex-end;
}

.bar-fill {
  width: 100%;
  max-width: 36px;
  border-radius: 6px 6px 2px 2px;
  background: var(--primary);
  transition: height 0.4s ease;
  min-height: 4px;
}

.bar-label {
  font-size: 11px;
  color: var(--text-400);
  font-weight: 500;
  white-space: nowrap;
}

.bar-value {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}

/* 图例 */
.chart-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-400);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}
</style>
