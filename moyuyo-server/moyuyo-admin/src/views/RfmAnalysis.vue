<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>RFM 分析</h2>
    </div>

    <!-- ===== KPI 卡片（保留原有） ===== -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">R 平均（天）</div>
            <div class="kpi-value">{{ kpi.avgR }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">F 平均（次）</div>
            <div class="kpi-value">{{ kpi.avgF }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">M 平均（元）</div>
            <div class="kpi-value">{{ kpi.avgM }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 用户群组矩阵（新增：纯 CSS 矩阵，点击筛选下方群组用户） ===== -->
    <el-card shadow="never" class="section-card">
      <div class="section-head">
        <div class="section-title">
          <span class="section-icon">▦</span>
          <h3>用户群组矩阵</h3>
          <span class="section-sub">按 R/F/M 三维高低划分 8 个用户群组，点击格子筛选下方群组用户</span>
        </div>
        <span class="section-meta">总用户 {{ summary.totalUsers }} 人 | 总 GMV ${{ fmtMoney(summary.totalGmv) }}</span>
      </div>
      <div class="matrix-blocks">
        <!-- R 高组与 R 低组两块矩阵 -->
        <div v-for="(block, key) in matrixGroups" :key="key" class="matrix-block">
          <div class="matrix-block-label">{{ block.label }}</div>
          <div class="matrix-grid">
            <div class="matrix-axis-placeholder"></div>
            <div class="matrix-col-head">F 高</div>
            <div class="matrix-col-head">F 低</div>
            <div class="matrix-axis">M 高</div>
            <div
              v-for="g in block.mHigh"
              :key="g.name"
              class="matrix-cell"
              :class="['tone-' + g.tone, { selected: selectedGroup === g.name }]"
              @click="selectGroup(g.name)"
            >
              <div class="matrix-cell-name">{{ g.name }}</div>
              <div class="matrix-cell-count">{{ g.count }}</div>
              <div class="matrix-cell-pct">{{ g.pct }}</div>
              <div class="matrix-cell-gmv">GMV {{ g.gmvPct }}%</div>
            </div>
            <div class="matrix-axis">M 低</div>
            <div
              v-for="g in block.mLow"
              :key="g.name"
              class="matrix-cell"
              :class="['tone-' + g.tone, { selected: selectedGroup === g.name }]"
              @click="selectGroup(g.name)"
            >
              <div class="matrix-cell-name">{{ g.name }}</div>
              <div class="matrix-cell-count">{{ g.count }}</div>
              <div class="matrix-cell-pct">{{ g.pct }}</div>
              <div class="matrix-cell-gmv">GMV {{ g.gmvPct }}%</div>
            </div>
          </div>
        </div>
      </div>
      <div class="matrix-note">注：按 R/F/M 三维值的三分位阈值划分群组，数据来自订单实时聚合。</div>
    </el-card>

    <!-- ===== 群组用户详情 + LTV 预测（并排） ===== -->
    <div class="detail-layout">
      <!-- 群组用户详情（新增：随矩阵选中群组联动） -->
      <el-card shadow="never" class="section-card">
        <div class="section-head">
          <div class="section-title">
            <span class="section-icon">👥</span>
            <h3>群组用户详情</h3>
            <span class="group-badge" :class="'badge-' + groupTone">{{ selectedGroup }}</span>
          </div>
          <el-button size="small" @click="handleExport">导出</el-button>
        </div>
        <el-table :data="filteredUsers" stripe style="width: 100%">
          <el-table-column prop="id" label="用户ID" width="110">
            <template #default="{ row }">
              <span class="user-id">{{ row.id }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="昵称" min-width="120" />
          <el-table-column label="R 得分" width="90">
            <template #default="{ row }">
              <span class="score-badge" :class="'score-' + scoreTone(row.rScore)">{{ row.rScore }}</span>
            </template>
          </el-table-column>
          <el-table-column label="F 得分" width="90">
            <template #default="{ row }">
              <span class="score-badge" :class="'score-' + scoreTone(row.fScore)">{{ row.fScore }}</span>
            </template>
          </el-table-column>
          <el-table-column label="M 得分" width="90">
            <template #default="{ row }">
              <span class="score-badge" :class="'score-' + scoreTone(row.mScore)">{{ row.mScore }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastBuy" label="最近购买" width="100" />
          <el-table-column prop="buyCount" label="购买次数" width="100" />
          <el-table-column prop="totalSpend" label="累计消费" width="110">
            <template #default="{ row }">
              <span class="money-text">{{ row.totalSpend }}</span>
            </template>
          </el-table-column>
          <el-table-column label="群组" width="110">
            <template #default="{ row }">
              <span class="group-badge" :class="'badge-' + groupToneOf(row.group)">{{ row.group }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-note">注：展示当前群组下的真实用户，按累计消费金额倒序。</div>
      </el-card>

      <!-- LTV 预测（新增：纯 CSS 条形图 + 说明） -->
      <div class="ltv-column">
        <el-card shadow="never" class="ltv-card">
          <div class="section-title">
            <span class="section-icon">📈</span>
            <h3>LTV 预测</h3>
          </div>
          <div class="ltv-hero">
            <div class="ltv-hero-label">平均消费</div>
            <div class="ltv-hero-value">${{ fmtMoney(summary.avgSpend) }}</div>
            <div class="ltv-hero-sub">全量用户累计消费均值（真实数据）</div>
          </div>
          <div class="ltv-cac">
            <div class="ltv-hero-label">平均购买频次</div>
            <div class="ltv-cac-row">
              <span class="ltv-cac-value">{{ summary.avgBuyCount }}</span>
              <span class="ltv-cac-health">次/人</span>
            </div>
            <div class="ltv-hero-sub">全量用户平均下单次数</div>
          </div>
        </el-card>
        <el-card shadow="never" class="ltv-card">
          <div class="section-title">
            <span class="section-icon">📊</span>
            <h3>分群组 LTV</h3>
          </div>
          <div class="ltv-bar-list">
            <div v-for="item in ltvData" :key="item.name" class="ltv-bar-row">
              <span class="ltv-bar-name">{{ item.name }}</span>
              <div class="ltv-bar-track">
                <div class="ltv-bar-fill" :style="{ width: barWidth(item.value) + '%', background: item.color }"></div>
              </div>
              <span class="ltv-bar-value">${{ item.value }}</span>
            </div>
          </div>
          <div class="ltv-note">
            <p>LTV（生命周期价值）≈ 平均客单价 × 年均购买频次 × 平均留存年数，用于评估各群组长期收益潜力。</p>
            <p>当前条形图展示各群组人均累计消费金额，数据来自订单实时聚合。</p>
          </div>
        </el-card>
      </div>
    </div>

    <!-- ===== 客户表（保留原有） ===== -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="客户名称" />
        <el-table-column prop="rValue" label="R 值（天）" width="120" />
        <el-table-column prop="fValue" label="F 值（次）" width="120" />
        <el-table-column prop="mValue" label="M 值（元）" width="130" />
        <el-table-column prop="level" label="RFM 等级" width="150">
          <template #default="{ row }">
            <el-tag :type="tagType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 客户详情弹窗（保留原有） -->
    <el-dialog v-model="detailDialogVisible" title="客户 RFM 详情" width="520px">
      <el-descriptions v-if="currentRow" :column="1" border>
        <el-descriptions-item label="客户名称">{{ currentRow.name }}</el-descriptions-item>
        <el-descriptions-item label="R 值（最近购买间隔天数）">{{ currentRow.rValue }}</el-descriptions-item>
        <el-descriptions-item label="F 值（购买频次）">{{ currentRow.fValue }}</el-descriptions-item>
        <el-descriptions-item label="M 值（累计消费金额）">{{ currentRow.mValue }}</el-descriptions-item>
        <el-descriptions-item label="RFM 等级">
          <el-tag :type="tagType(currentRow.level)" size="small">{{ currentRow.level }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- RFM 导出对话框 -->
    <el-dialog v-model="exportDialogVisible" title="导出 RFM 报表" width="420px">
      <el-form label-width="90px">
        <el-form-item label="导出类型">
          <el-select v-model="exportType" style="width:100%">
            <el-option label="全部客户" value="all" />
            <el-option label="高价值用户" value="high" />
            <el-option label="中等价值用户" value="mid" />
            <el-option label="低价值用户" value="low" />
          </el-select>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRfmAnalysis } from '../api/admin'
import { toArray } from '../utils/safeArray'
import api from '../api'

// ===== 原有：KPI / 客户表 =====
const kpi = ref({ avgR: '—', avgF: '—', avgM: '—' })
const tableData = ref([])

// 客户详情弹窗状态
const detailDialogVisible = ref(false)
const currentRow = ref(null)

async function loadData() {
  try {
    const res = await getRfmAnalysis()
    // 后端返回扁平数组 [{ customerName, rDays, fCount, mAmount, rfmLevel }, ...]
    const list = toArray(res)
    if (list.length > 0) {
      // 从列表数据计算平均值
      const avgR = (list.reduce((sum, r) => sum + Number(r.rDays || 0), 0) / list.length).toFixed(1)
      const avgF = (list.reduce((sum, r) => sum + Number(r.fCount || 0), 0) / list.length).toFixed(1)
      const avgM = (list.reduce((sum, r) => sum + Number(r.mAmount || 0), 0) / list.length).toFixed(2)
      kpi.value = { avgR, avgF, avgM }
      // 映射字段以匹配表格的 prop
      tableData.value = list.map((item, idx) => ({
        id: idx + 1,
        name: item.customerName || '',
        rValue: item.rDays || 0,
        fValue: item.fCount || 0,
        mValue: item.mAmount || 0,
        level: item.rfmLevel || ''
      }))
      // 用真实数据聚合 8 个群组、用户明细与概览指标
      calcGroups(list)
    } else {
      kpi.value = { avgR: '—', avgF: '—', avgM: '—' }
      tableData.value = []
      calcGroups([])
    }
  } catch (err) {
    console.error('获取RFM数据失败', err)
    calcGroups([])
  }
}

function tagType(level) {
  if (level === '高价值') return 'danger'
  if (level === '重要发展') return 'warning'
  if (level === '重要保持') return 'primary'
  return 'info'
}

function handleEdit(row) {
  currentRow.value = row
  detailDialogVisible.value = true
}

onMounted(() => loadData())

// ===== 用户群组矩阵：基于真实 RFM 数据按三分位阈值划分 =====
// 8 个群组的维度组合定义（rHigh/fHigh/mHigh 表示该维度是否高于阈值）
const GROUP_DEFS = [
  { name: '重要价值', rHigh: true, fHigh: true, mHigh: true, tone: 'success' },
  { name: '重要发展', rHigh: true, fHigh: false, mHigh: true, tone: 'primary' },
  { name: '重要保持', rHigh: true, fHigh: true, mHigh: false, tone: 'primary' },
  { name: '重要挽留', rHigh: true, fHigh: false, mHigh: false, tone: 'error' },
  { name: '一般价值', rHigh: false, fHigh: true, mHigh: true, tone: 'gray' },
  { name: '一般发展', rHigh: false, fHigh: false, mHigh: true, tone: 'gray' },
  { name: '一般保持', rHigh: false, fHigh: true, mHigh: false, tone: 'gray' },
  { name: '一般挽留', rHigh: false, fHigh: false, mHigh: false, tone: 'error' }
]

// 群组名称 → 矩阵/徽章色调映射
const GROUP_TONE = {
  '重要价值': 'success',
  '重要发展': 'primary',
  '重要保持': 'primary',
  '重要挽留': 'error',
  '一般价值': 'gray',
  '一般发展': 'gray',
  '一般保持': 'gray',
  '一般挽留': 'error'
}

// 群组 → 色调
function groupToneOf(name) {
  return GROUP_TONE[name] || 'gray'
}

// 群组统计（由真实用户数据计算填充）
const groupMeta = ref(GROUP_DEFS.map(g => ({ ...g, count: 0, gmv: 0, pct: '0%', gmvPct: 0 })))
// 全量真实用户（用于群组用户列表）
const allUsers = ref([])
// 概览指标（真实数据）
const summary = ref({ totalUsers: 0, totalGmv: 0, avgSpend: 0, avgBuyCount: 0 })

// 取排序数组的百分位值
function percentile(sorted, pct) {
  if (!sorted.length) return 0
  const idx = Math.min(Math.floor((sorted.length * pct) / 100), sorted.length - 1)
  return sorted[idx]
}

// 金额格式化（千分位 + 两位小数）
function fmtMoney(value) {
  return Number(value || 0).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

// 根据真实用户列表计算 8 个群组统计与用户归属
function calcGroups(list) {
  const rValues = list.map(u => Number(u.rDays || 0)).sort((a, b) => a - b)
  const fValues = list.map(u => Number(u.fCount || 0)).sort((a, b) => a - b)
  const mValues = list.map(u => Number(u.mAmount || 0)).sort((a, b) => a - b)
  // R 越小越好，取 33 分位；F/M 越大越好，取 66 分位（与后端 RFM 评分口径一致）
  const rThreshold = percentile(rValues, 33)
  const fThreshold = percentile(fValues, 66)
  const mThreshold = percentile(mValues, 66)

  const stats = GROUP_DEFS.map(g => ({ ...g, count: 0, gmv: 0 }))
  const totalGmv = mValues.reduce((sum, v) => sum + v, 0)

  const users = list.map((item) => {
    const rDays = Number(item.rDays || 0)
    const fCount = Number(item.fCount || 0)
    const mAmount = Number(item.mAmount || 0)
    const rHigh = rDays <= rThreshold
    const fHigh = fCount >= fThreshold
    const mHigh = mAmount >= mThreshold
    const def = GROUP_DEFS.find(g => g.rHigh === rHigh && g.fHigh === fHigh && g.mHigh === mHigh) || GROUP_DEFS[7]
    const target = stats.find(s => s.name === def.name)
    if (target) {
      target.count += 1
      target.gmv += mAmount
    }
    return {
      id: item.userId || '-',
      name: item.customerName || '匿名用户',
      group: def.name,
      // 1~10 分制展示，映射后端 1/3/5 分档
      rScore: rHigh ? (rDays <= rThreshold / 2 ? 10 : 6) : 2,
      fScore: fHigh ? 10 : (fCount > 0 ? 6 : 2),
      mScore: mHigh ? 10 : 2,
      lastBuy: rDays + ' 天前',
      buyCount: fCount,
      totalSpend: '$' + fmtMoney(mAmount)
    }
  })

  groupMeta.value = stats.map(s => ({
    ...s,
    pct: list.length ? ((s.count / list.length) * 100).toFixed(1) + '%' : '0%',
    gmvPct: totalGmv > 0 ? Math.round((s.gmv / totalGmv) * 100) : 0
  }))
  allUsers.value = users
  summary.value = {
    totalUsers: list.length,
    totalGmv,
    avgSpend: list.length ? Math.round(totalGmv / list.length) : 0,
    avgBuyCount: list.length
      ? (list.reduce((sum, u) => sum + Number(u.fCount || 0), 0) / list.length).toFixed(1)
      : '0.0'
  }
}

// 当前选中的群组（默认选中重要价值）
const selectedGroup = ref('重要价值')
const groupTone = computed(() => groupToneOf(selectedGroup.value))

// 按 R 维度分组矩阵格子：R 高组 / R 低组，每组内按 M 高 / M 低分行，行内按 F 高 / F 低分列
const matrixGroups = computed(() => ({
  rHigh: {
    label: 'R 高 · 最近有购买',
    mHigh: groupMeta.value.filter((g) => g.rHigh && g.mHigh),
    mLow: groupMeta.value.filter((g) => g.rHigh && !g.mHigh)
  },
  rLow: {
    label: 'R 低 · 长期未购买',
    mHigh: groupMeta.value.filter((g) => !g.rHigh && g.mHigh),
    mLow: groupMeta.value.filter((g) => !g.rHigh && !g.mHigh)
  }
}))

// 点击矩阵格子选中群组（联动下方群组用户详情）
function selectGroup(name) {
  selectedGroup.value = name
}

// ===== 群组用户详情：来自真实订单 RFM 数据 =====
// 按当前选中群组过滤出详情列表（已按累计消费倒序）
const filteredUsers = computed(() => allUsers.value.filter((u) => u.group === selectedGroup.value))

// 得分徽章色调：≥7 绿 / ≥4 蓝 / 其余橙
function scoreTone(score) {
  if (score >= 7) return 'success'
  if (score >= 4) return 'primary'
  return 'warning'
}

// 导出 RFM 报表：调用后端 /analysis/rfm/export 导出，接口未接入时降级为前端 CSV
const exportDialogVisible = ref(false)
const exportType = ref('all') // all / high / mid / low

function handleExport() {
  exportDialogVisible.value = true
}

async function confirmExport() {
  try {
    const res = await api.get('/analysis/rfm/export', { params: { type: exportType.value }, responseType: 'blob' })
    // 后端返回二进制流则直接下载
    const blob = new Blob([res.data || res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'rfm-' + exportType.value + '-' + new Date().toISOString().slice(0, 10) + '.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出 RFM 报表')
  } catch (e) {
    // 本地降级：从当前真实群组统计导出 CSV
    const rows = groupMeta.value.map(s => ({ segment: s.name, count: s.count, percentage: s.pct }))
    const csv = [
      '分层,人数,占比',
      ...rows.map(r => [r.segment, r.count, r.percentage].join(','))
    ].join('\n')
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'rfm-' + exportType.value + '-' + new Date().toISOString().slice(0, 10) + '.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出本地 CSV 文件')
  } finally {
    exportDialogVisible.value = false
  }
}

// ===== 分群组人均消费（真实数据派生） =====
// 各群组人均累计消费金额，颜色对应群组色调
const GROUP_COLOR = {
  '重要价值': 'var(--state-success)',
  '重要发展': 'var(--primary)',
  '重要保持': 'var(--primary)',
  '重要挽留': 'var(--state-error)',
  '一般价值': 'var(--text-500)',
  '一般发展': 'var(--text-400)',
  '一般保持': 'var(--text-400)',
  '一般挽留': 'var(--state-error)'
}

const ltvData = computed(() => groupMeta.value.map((g) => ({
  name: g.name,
  value: g.count > 0 ? Math.round(g.gmv / g.count) : 0,
  color: GROUP_COLOR[g.name] || 'var(--text-400)'
})))

// 条形图最大基准值（取最大群组人均消费），用于计算条宽百分比
const ltvMax = computed(() => Math.max(...ltvData.value.map((item) => item.value), 1))

// 计算 LTV 条形宽度百分比
function barWidth(value) {
  return Math.round((value / ltvMax.value) * 100)
}
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.kpi-row { margin-bottom: 16px; }
.kpi-card-content { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }

/* ===== 通用区块卡片 ===== */
.section-card { margin-bottom: 16px; }
.section-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 14px; border-bottom: 1px solid var(--border); margin-bottom: 16px; }
.section-title { display: flex; align-items: center; gap: 8px; }
.section-icon { font-size: 14px; color: var(--primary); }
.section-title h3 { font-size: 15px; font-weight: 700; color: var(--text-800); margin: 0; }
.section-sub { font-size: 11px; color: var(--text-400); margin-left: 4px; }
.section-meta { font-size: 11px; color: var(--text-400); }

/* ===== 用户群组矩阵 ===== */
.matrix-blocks { display: flex; flex-direction: column; gap: 18px; }
.matrix-block-label { font-size: 12px; font-weight: 600; color: var(--text-600); margin-bottom: 8px; }
.matrix-grid { display: grid; grid-template-columns: 44px 1fr 1fr; gap: 4px; align-items: stretch; }
.matrix-axis-placeholder { grid-column: 1; }
.matrix-col-head { text-align: center; font-size: 10px; color: var(--text-400); font-weight: 600; text-transform: uppercase; letter-spacing: 0.1em; padding: 6px 0 8px; }
.matrix-axis { display: flex; align-items: center; justify-content: center; font-size: 10px; color: var(--text-400); font-weight: 600; text-transform: uppercase; letter-spacing: 0.1em; writing-mode: vertical-lr; transform: rotate(180deg); }
.matrix-cell { border-radius: 8px; padding: 14px 12px; text-align: center; cursor: pointer; transition: transform 0.15s ease, box-shadow 0.15s ease; border: 1px solid var(--border); }
.matrix-cell:hover { transform: translateY(-1px); box-shadow: var(--shadow-md); }
.matrix-cell.selected { box-shadow: 0 0 0 2px var(--primary); border-color: var(--primary); }
.matrix-cell-name { font-size: 13px; font-weight: 700; margin-bottom: 6px; }
.matrix-cell-count { font-size: 18px; font-weight: 700; color: var(--text-800); }
.matrix-cell-pct { font-size: 11px; color: var(--text-500); margin: 2px 0; }
.matrix-cell-gmv { font-size: 11px; font-weight: 600; }

/* 矩阵格子色调：成功 / 主色 / 错误 / 灰 */
.matrix-cell.tone-success { background: rgba(52, 199, 89, 0.15); border-color: rgba(52, 199, 89, 0.3); }
.matrix-cell.tone-success .matrix-cell-name, .matrix-cell.tone-success .matrix-cell-gmv { color: var(--state-success); }
.matrix-cell.tone-primary { background: rgba(0, 122, 255, 0.08); border-color: rgba(0, 122, 255, 0.15); }
.matrix-cell.tone-primary .matrix-cell-name, .matrix-cell.tone-primary .matrix-cell-gmv { color: var(--primary); }
.matrix-cell.tone-error { background: rgba(255, 59, 48, 0.12); border-color: rgba(255, 59, 48, 0.2); }
.matrix-cell.tone-error .matrix-cell-name, .matrix-cell.tone-error .matrix-cell-gmv { color: var(--state-error); }
.matrix-cell.tone-gray { background: rgba(142, 142, 147, 0.06); border-color: rgba(142, 142, 147, 0.12); }
.matrix-cell.tone-gray .matrix-cell-name, .matrix-cell.tone-gray .matrix-cell-gmv { color: var(--text-600); }
.matrix-note { font-size: 11px; color: var(--text-400); margin-top: 14px; }

/* ===== 群组用户详情 + LTV 并排布局 ===== */
.detail-layout { display: grid; grid-template-columns: 1fr 340px; gap: 16px; margin-bottom: 16px; }
.table-note { font-size: 11px; color: var(--text-400); padding: 10px 4px 2px; }

/* 用户 ID（等宽字体） */
.user-id { font-family: var(--font-mono); font-size: 12px; color: var(--primary); }

/* 得分徽章 */
.score-badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; }
.score-success { background: var(--state-success-surface); color: var(--state-success); }
.score-primary { background: var(--brand-50); color: var(--primary); }
.score-warning { background: var(--state-warning-surface); color: var(--state-warning); }

/* 群组徽章 */
.group-badge { font-size: 11px; padding: 2px 8px; border-radius: 999px; font-weight: 600; }
.badge-success { background: rgba(52, 199, 89, 0.12); color: var(--state-success); }
.badge-primary { background: rgba(0, 122, 255, 0.1); color: var(--primary); }
.badge-error { background: rgba(255, 59, 48, 0.1); color: var(--state-error); }
.badge-gray { background: rgba(142, 142, 147, 0.1); color: var(--text-600); }

/* 累计消费 */
.money-text { font-weight: 600; color: var(--text-800); font-variant-numeric: tabular-nums; }

/* ===== LTV 预测 ===== */
.ltv-column { display: flex; flex-direction: column; gap: 16px; }
.ltv-card { margin-bottom: 0; }
.ltv-hero, .ltv-cac { background: var(--accent); border-radius: 10px; padding: 16px; margin-top: 14px; }
.ltv-cac { margin-bottom: 0; }
.ltv-hero-label { font-size: 11px; color: var(--text-400); text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 4px; }
.ltv-hero-value { font-size: 28px; font-weight: 700; color: var(--text-800); line-height: 1.2; }
.ltv-hero-sub { font-size: 10px; color: var(--text-400); margin-top: 4px; }
.ltv-cac-row { display: flex; align-items: baseline; gap: 6px; }
.ltv-cac-value { font-size: 28px; font-weight: 700; color: var(--state-success); line-height: 1.2; }
.ltv-cac-health { font-size: 12px; color: var(--state-success); font-weight: 500; }

/* 分群组 LTV 横向条形图（纯 CSS） */
.ltv-bar-list { display: flex; flex-direction: column; gap: 8px; margin-top: 14px; }
.ltv-bar-row { display: flex; align-items: center; gap: 8px; }
.ltv-bar-name { width: 56px; flex-shrink: 0; font-size: 11px; color: var(--text-600); }
.ltv-bar-track { flex: 1; height: 14px; border-radius: 7px; background: var(--background-200); overflow: hidden; }
.ltv-bar-fill { height: 100%; border-radius: 7px; transition: width 0.3s ease; min-width: 4px; }
.ltv-bar-value { width: 48px; flex-shrink: 0; text-align: right; font-size: 12px; font-weight: 700; color: var(--text-700); font-variant-numeric: tabular-nums; }

/* LTV 说明 */
.ltv-note { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--border); }
.ltv-note p { font-size: 11px; color: var(--text-400); line-height: 1.7; margin: 0 0 4px; }
.ltv-note p:last-child { margin-bottom: 0; }

/* 窄屏时详情与 LTV 改为纵向堆叠 */
@media (max-width: 1200px) {
  .detail-layout { grid-template-columns: 1fr; }
}
</style>
