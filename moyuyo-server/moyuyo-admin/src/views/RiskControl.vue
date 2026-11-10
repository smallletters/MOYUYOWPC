<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header-left">
        <h2>风控管理</h2>
        <p>实时监控平台风险状态，管理风控规则</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建规则</el-button>
      </div>
    </div>

    <!-- Tab 切换：风控总览 / 规则管理 -->
    <div class="tab-switcher">
      <button class="tab-switcher-item" :class="{ active: activeTab === 'overview' }" @click="switchTab('overview')">风控总览</button>
      <button class="tab-switcher-item" :class="{ active: activeTab === 'rules' }" @click="switchTab('rules')">规则管理</button>
    </div>

    <!-- ==================== 风控总览 Tab ==================== -->
    <template v-if="activeTab === 'overview'">
      <!-- KPI 指标卡片 -->
      <div class="risk-kpi-grid">
        <div class="risk-kpi-card" :class="k.color" v-for="k in kpiList" :key="k.label">
          <div class="stat-label">{{ k.label }}</div>
          <div class="stat-value">{{ k.value }}<span class="stat-unit">{{ k.unit }}</span></div>
          <div class="stat-sub">{{ k.sub }}</div>
        </div>
      </div>

      <!-- 风险事件趋势（近 7 天）CSS 柱状图 -->
      <div class="chart-card">
        <div class="chart-title">风险事件趋势（近 7 天）</div>
        <div class="trend-chart">
          <div class="trend-bars">
            <div class="trend-bar-group" v-for="item in trendData" :key="item.date">
              <div class="trend-bar-stack">
                <div class="trend-bar trend-bar-total" :style="{ height: barHeight(item.total) + '%' }" :title="'风控触发 ' + item.total + ' 次'"></div>
                <div class="trend-bar trend-bar-blocked" :style="{ height: barHeight(item.blocked) + '%' }" :title="'拦截 ' + item.blocked + ' 次'"></div>
              </div>
              <span class="trend-bar-date">{{ item.date }}</span>
            </div>
          </div>
          <div class="trend-legend">
            <span class="trend-legend-item"><i class="legend-dot" style="background:var(--primary)"></i>风控触发</span>
            <span class="trend-legend-item"><i class="legend-dot" style="background:var(--state-error)"></i>拦截数</span>
          </div>
        </div>
      </div>

      <!-- 风险类型分布 + 拦截渠道分布 -->
      <div class="chart-grid">
        <!-- 风险类型分布（水平条形图） -->
        <div class="chart-card">
          <div class="chart-title">风险类型分布</div>
          <div class="hbar-list">
            <div class="hbar-item" v-for="item in typeDist" :key="item.name">
              <div class="hbar-head">
                <span class="hbar-name">{{ item.name }}</span>
                <span class="hbar-count">{{ item.count }} 次 · {{ item.percent }}%</span>
              </div>
              <div class="hbar-track">
                <div class="hbar-fill" :style="{ width: item.percent + '%', background: item.color }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 风险等级分布（分段条） -->
        <div class="chart-card">
          <div class="chart-title">风险等级分布</div>
          <div class="segment-bar">
            <div class="segment-item" v-for="item in levelDist" :key="item.name"
              :style="{ width: item.percent + '%', background: item.color }"></div>
          </div>
          <div class="segment-legend">
            <div class="segment-legend-item" v-for="item in levelDist" :key="item.name">
              <span class="segment-dot" :style="{ background: item.color }"></span>
              <span class="segment-name">{{ item.name }}</span>
              <span class="segment-percent">{{ item.percent }}%</span>
            </div>
          </div>
          <div v-if="levelDist.length === 0" class="segment-name">暂无风险等级数据</div>
        </div>
      </div>

      <!-- 最新风险事件列表 -->
      <div class="chart-card">
        <div class="chart-title">最新风险事件</div>
        <table class="risk-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>类型</th>
              <th>用户</th>
              <th>业务ID</th>
              <th>处置</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ev in latestEvents" :key="ev.id">
              <td>{{ ev.time }}</td>
              <td><span class="event-type-tag" :class="eventTypeClass(ev.type)">{{ ev.type }}</span></td>
              <td class="event-user">{{ ev.user }}</td>
              <td class="event-amount">{{ ev.businessId || '-' }}</td>
              <td><span class="action-tag" :class="ev.actionType">{{ ev.action }}</span></td>
            </tr>
            <tr v-if="latestEvents.length === 0">
              <td colspan="5" style="text-align:center;color:var(--text-400)">暂无风险事件</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- ==================== 规则管理 Tab ==================== -->
    <template v-else>
      <!-- 筛选 -->
      <el-card shadow="never" class="filter-card">
        <el-form :model="filters" inline>
          <el-form-item label="规则类型">
            <el-select v-model="filters.ruleType" placeholder="全部" clearable style="width:140px">
              <el-option label="登录" value="LOGIN" />
              <el-option label="下单" value="ORDER" />
              <el-option label="支付" value="PAYMENT" />
              <el-option label="优惠券" value="COUPON" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.enabled" placeholder="全部" clearable style="width:140px">
              <el-option label="启用" value="true" />
              <el-option label="禁用" value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
      <!-- 表格 -->
      <el-card shadow="never">
        <el-table :data="tableData" stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="ruleName" label="规则名称" />
          <el-table-column prop="ruleType" label="规则类型" width="100">
            <template #default="{ row }">
              <el-tag :type="ruleTypeTag(row.ruleType)" size="small">{{ ruleTypeLabel(row.ruleType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="conditionJson" label="触发条件" min-width="200" show-overflow-tooltip />
          <el-table-column prop="enabled" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button type="primary" link size="small" @click="handleToggle(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 新建/编辑规则弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑规则' : '新建规则'" width="560px">
      <el-form :model="ruleForm" label-width="90px">
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleForm.ruleName" placeholder="如：登录频率异常" />
        </el-form-item>
        <el-form-item label="规则编码">
          <el-input v-model="ruleForm.ruleCode" placeholder="留空则使用规则名称" />
        </el-form-item>
        <el-form-item label="规则类型" required>
          <el-select v-model="ruleForm.ruleType" style="width: 100%">
            <el-option label="登录" value="LOGIN" />
            <el-option label="下单" value="ORDER" />
            <el-option label="支付" value="PAYMENT" />
            <el-option label="优惠券" value="COUPON" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行动作" required>
          <el-select v-model="ruleForm.action" style="width: 100%">
            <el-option label="拦截" value="BLOCK" />
            <el-option label="人工审核" value="REVIEW" />
            <el-option label="二次验证" value="VERIFY" />
            <el-option label="仅记录" value="LOG" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="ruleForm.priority" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
        <el-form-item label="触发条件">
          <el-input v-model="ruleForm.conditionJson" type="textarea" :rows="3" placeholder="JSON 格式条件配置" />
        </el-form-item>
        <el-form-item label="规则描述">
          <el-input v-model="ruleForm.description" type="textarea" :rows="2" placeholder="规则说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRiskRules, deleteRiskRule, createRiskRule, updateRiskRule, getRiskOverview } from '../api/admin'
import { toArray } from '../utils/safeArray'

// ==================== Tab 切换 ====================
const activeTab = ref('overview')

// 切换到指定 Tab
function switchTab(name) {
  activeTab.value = name
}

// ==================== 风控总览（真实数据） ====================

// KPI 指标卡片：今日拦截 / 拦截命中率 / 待处理事件 / 风控规则数
const kpiList = ref([])

// 风险事件趋势（近 7 天）：total 为风控触发总数，blocked 为拦截数
const trendData = ref([])

// 趋势最大值（用于柱高归一化）
const trendMax = ref(1)

// 计算柱状图高度百分比（相对最大值）
function barHeight(value) {
  return Math.round((value / trendMax.value) * 100)
}

// 风险类型分布（水平条形图）
const typeDist = ref([])

// 风险等级分布（分段条）
const levelDist = ref([])

// 最新风险事件列表（时间 / 类型 / 用户 / 业务ID / 处置）
const latestEvents = ref([])

// 加载风控总览
async function loadOverview() {
  try {
    const res = await getRiskOverview()
    if (!res) return
    const kpi = res.kpi || {}
    kpiList.value = [
      { label: '今日拦截', value: kpi.todayBlocked ?? 0, unit: '单', sub: `今日触发 ${kpi.todayTotal ?? 0} 次`, color: 'red' },
      { label: '拦截命中率', value: kpi.hitRate ?? 0, unit: '%', sub: '今日拦截 / 今日触发', color: 'orange' },
      { label: '待处理事件', value: kpi.pendingCount ?? 0, unit: '单', sub: '状态为 PENDING', color: 'red' },
      { label: '风控规则数', value: kpi.ruleTotal ?? 0, unit: '条', sub: `启用 ${kpi.ruleEnabled ?? 0} / 停用 ${(kpi.ruleTotal ?? 0) - (kpi.ruleEnabled ?? 0)}`, color: 'blue' }
    ]
    trendData.value = res.trend || []
    trendMax.value = Math.max(...trendData.value.map(d => d.total), 1)
    typeDist.value = (res.typeDist || []).map((item, idx) => ({
      ...item,
      color: ['var(--state-error)', 'var(--chart-3)', 'var(--primary)', 'var(--chart-4)', 'var(--text-400)'][idx % 5]
    }))
    levelDist.value = (res.levelDist || []).map((item, idx) => ({
      ...item,
      color: ['var(--state-error)', 'var(--state-warning)', 'var(--primary)', 'var(--state-success)'][idx % 4]
    }))
    latestEvents.value = res.latestEvents || []
  } catch (e) {
    console.error('获取风控总览失败', e)
    kpiList.value = []
    trendData.value = []
    typeDist.value = []
    levelDist.value = []
    latestEvents.value = []
  }
}

// 事件类型 → 标签颜色修饰类
function eventTypeClass(type) {
  const map = { '登录风险': 'red', '支付风险': 'orange', '下单风险': 'blue' }
  return map[type] || 'gray'
}

// ==================== 规则管理 ====================
const filters = reactive({ ruleType: '', enabled: '' })
const tableData = ref([])

// 新建/编辑弹窗状态
const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const ruleForm = reactive({
  ruleCode: '',
  ruleName: '',
  ruleType: 'LOGIN',
  action: 'BLOCK',
  priority: 10,
  enabled: true,
  conditionJson: '',
  description: ''
})

function ruleTypeTag(type) {
  if (type === 'LOGIN') return ''
  if (type === 'ORDER') return 'warning'
  if (type === 'PAYMENT') return 'danger'
  if (type === 'COUPON') return 'success'
  return 'info'
}

function ruleTypeLabel(type) {
  const map = { LOGIN: '登录', ORDER: '下单', PAYMENT: '支付', COUPON: '优惠券' }
  return map[type] || type
}

// 加载风控规则列表
async function loadData() {
  try {
    const res = await getRiskRules()
    // 根据筛选条件过滤
    let list = toArray(res)
    if (filters.ruleType) {
      list = list.filter(item => item.ruleType === filters.ruleType)
    }
    if (filters.enabled) {
      list = list.filter(item => String(item.enabled) === filters.enabled)
    }
    tableData.value = list
  } catch (e) {
    ElMessage.error('获取风控规则失败')
  }
}

function handleSearch() { loadData() }
function handleReset() { filters.ruleType = ''; filters.enabled = ''; loadData() }

// ---- 新建 / 编辑规则 ----
function openDialog() {
  ruleForm.ruleCode = ''
  ruleForm.ruleName = ''
  ruleForm.ruleType = 'LOGIN'
  ruleForm.action = 'BLOCK'
  ruleForm.priority = 10
  ruleForm.enabled = true
  ruleForm.conditionJson = ''
  ruleForm.description = ''
  editingId.value = null
  dialogVisible.value = true
}

// 新建规则：切换到规则管理 Tab 并打开弹窗
function handleAdd() {
  activeTab.value = 'rules'
  openDialog()
}

function handleEdit(row) {
  editingId.value = row.id
  ruleForm.ruleCode = row.ruleCode || ''
  ruleForm.ruleName = row.ruleName || ''
  ruleForm.ruleType = row.ruleType || 'LOGIN'
  ruleForm.action = row.action || 'BLOCK'
  ruleForm.priority = row.priority ?? 10
  ruleForm.enabled = row.enabled !== false
  ruleForm.conditionJson = row.conditionJson || ''
  ruleForm.description = row.description || ''
  dialogVisible.value = true
}

async function submitRule() {
  if (!ruleForm.ruleName.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      ruleCode: ruleForm.ruleCode.trim() || ruleForm.ruleName.trim(),
      ruleName: ruleForm.ruleName.trim(),
      ruleType: ruleForm.ruleType,
      action: ruleForm.action,
      priority: Number(ruleForm.priority) || 10,
      enabled: ruleForm.enabled,
      conditionJson: ruleForm.conditionJson,
      description: ruleForm.description
    }
    if (editingId.value) {
      await updateRiskRule(editingId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await createRiskRule(payload)
      ElMessage.success('规则创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (err) {
    console.error('保存风控规则失败:', err)
    ElMessage.error('保存失败：' + (err?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

async function handleToggle(row) {
  try {
    const newEnabled = !row.enabled
    const { toggleRiskRule } = await import('../api/admin')
    await toggleRiskRule(row.id, { enabled: newEnabled })
    ElMessage.success('规则已' + (newEnabled ? '启用' : '禁用'))
    await loadData()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除规则「' + row.ruleName + '」吗？', '提示')
    await deleteRiskRule(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('删除风控规则失败:', e)
      ElMessage.error('删除失败: ' + (e?.message || '未知错误'))
    }
  }
}

onMounted(() => {
  loadData()
  loadOverview()
})
</script>

<style scoped>
.page-wrapper { }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0; }
.page-header p { font-size: 13px; color: var(--text-400); margin: 4px 0 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }

/* ===== Tab 切换 ===== */
.tab-switcher {
  display: flex;
  gap: 0;
  margin-bottom: 20px;
  background: var(--background-200);
  border-radius: var(--radius-sm);
  padding: 3px;
  width: fit-content;
}
.tab-switcher-item {
  padding: 6px 16px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-500);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}
.tab-switcher-item.active {
  background: var(--card);
  color: var(--text-800);
  box-shadow: var(--shadow-xs);
}
.tab-switcher-item:hover:not(.active) {
  color: var(--text-600);
}

/* ===== 风控总览 KPI 卡片 ===== */
.risk-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.risk-kpi-card {
  padding: 20px;
  background: var(--background-50);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.risk-kpi-card .stat-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-400);
}
.risk-kpi-card .stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}
.risk-kpi-card .stat-unit {
  font-size: 14px;
  font-weight: 400;
  margin-left: 2px;
}
.risk-kpi-card .stat-sub {
  font-size: 11px;
  color: var(--text-400);
}
.risk-kpi-card.red .stat-value { color: var(--state-error); }
.risk-kpi-card.orange .stat-value { color: var(--chart-3); }
.risk-kpi-card.blue .stat-value { color: var(--primary); }

/* ===== 通用图表卡片 ===== */
.chart-card {
  padding: 20px;
  background: var(--background-50);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  margin-bottom: 20px;
}
.chart-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ===== 风险事件趋势 CSS 柱状图 ===== */
.trend-bars {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 180px;
  padding: 0 12px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 12px;
}
.trend-bar-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  height: 100%;
}
.trend-bar-stack {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 150px;
}
.trend-bar {
  width: 10px;
  border-radius: 3px 3px 0 0;
  transition: height 0.3s ease;
  min-height: 4px;
}
.trend-bar-total { background: var(--primary); }
.trend-bar-blocked { background: var(--state-error); }
.trend-bar-date {
  font-size: 11px;
  color: var(--text-400);
}
.trend-legend {
  display: flex;
  justify-content: center;
  gap: 20px;
}
.trend-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-500);
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

/* ===== 两栏图表布局 ===== */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

/* ===== 风险类型分布（水平条形图） ===== */
.hbar-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.hbar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.hbar-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
}
.hbar-count {
  font-size: 12px;
  color: var(--text-400);
}
.hbar-track {
  height: 10px;
  border-radius: 999px;
  background: var(--background-200);
  overflow: hidden;
}
.hbar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}

/* ===== 拦截渠道分布（分段条） ===== */
.segment-bar {
  display: flex;
  height: 18px;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 16px;
  background: var(--background-200);
}
.segment-item {
  height: 100%;
  transition: width 0.3s ease;
}
.segment-legend {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 20px;
}
.segment-legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-600);
}
.segment-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.segment-name {
  flex: 1;
}
.segment-percent {
  font-weight: 600;
  color: var(--text-700);
  font-variant-numeric: tabular-nums;
}

/* ===== 最新风险事件表格 ===== */
.risk-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.risk-table th {
  text-align: left;
  padding: 10px 12px;
  background: var(--background-100);
  color: var(--text-500);
  font-weight: 600;
  font-size: 11px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  border-bottom: 1px solid var(--border);
}
.risk-table td {
  padding: 12px;
  color: var(--text-700);
  border-bottom: 1px solid var(--background-200);
  vertical-align: middle;
}
.risk-table tr:last-child td {
  border-bottom: none;
}
.risk-table tr:hover td {
  background: var(--background-100);
}
.event-user {
  font-weight: 500;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.event-amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--text-800);
}

/* 事件类型标签 */
.event-type-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.event-type-tag.red { background: var(--state-error-surface); color: var(--state-error); }
.event-type-tag.orange { background: #fff3e0; color: #e65100; }
.event-type-tag.blue { background: var(--brand-50); color: var(--brand-700); }
.event-type-tag.purple { background: #f3e8ff; color: #7c3aed; }
.event-type-tag.gray { background: var(--background-200); color: var(--text-500); }

/* 处置标签 */
.action-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.action-tag.block { background: var(--state-error-surface); color: var(--state-error); }
.action-tag.review { background: #fff3e0; color: #e65100; }
.action-tag.verify { background: var(--brand-50); color: var(--brand-700); }
.action-tag.pass { background: var(--state-success-surface); color: var(--state-success); }
</style>
