<template>
  <div class="cs-performance">
    <h2 class="page-title">客服绩效看板</h2>

    <!-- 时间筛选（当前仅支持实时数据，保留筛选入口） -->
    <div class="time-filter">
      <button
        v-for="chip in timeChips"
        :key="chip.key"
        class="filter-chip"
        :class="{ active: activeChip === chip.key }"
        @click="activeChip = chip.key"
      >{{ chip.label }}</button>
    </div>

    <!-- 团队整体 KPI 卡片 -->
    <div class="section-header">
      <span class="section-title">团队整体 KPI</span>
      <span class="section-subtitle">实时数据</span>
    </div>
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">平均首次响应时间</div>
        <div class="kpi-value">{{ teamKpi.avgResponse }}<span class="kpi-unit">m</span></div>
        <div class="kpi-trend" :class="teamKpi.responseTrendClass">
          {{ teamKpi.responseTrendText }}
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">今日工单处理量</div>
        <div class="kpi-value">{{ teamKpi.todayTickets }}<span class="kpi-unit">单</span></div>
        <div class="kpi-trend">当前在线 {{ teamKpi.onlineAgents }} 人</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">平均满意度</div>
        <div class="kpi-value">{{ teamKpi.avgSatisfaction }}<span class="kpi-unit">/5.0</span></div>
        <div class="kpi-trend" :class="teamKpi.satisfactionTrendClass">
          {{ teamKpi.satisfactionTrendText }}
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">SLA 达标率</div>
        <div class="kpi-value">{{ teamKpi.slaRate }}<span class="kpi-unit">%</span></div>
        <div class="kpi-trend">待处理 {{ teamKpi.pending }} 单</div>
      </div>
    </div>

    <!-- 客服排名列表 -->
    <div class="section-header">
      <span class="section-title">客服排名</span>
      <span class="section-subtitle">按今日工单量排序</span>
    </div>
    <div class="ranking-list">
      <div v-for="(agent, index) in rankingList" :key="agent.agentId" class="ranking-item">
        <span class="ranking-badge" :class="badgeClass(index)">{{ index + 1 }}</span>
        <div class="ranking-avatar">{{ avatarText(agent.agentName) }}</div>
        <div class="ranking-info">
          <div class="ranking-name-row">
            <span class="ranking-name">{{ agent.agentName }}</span>
            <span class="rank-tag" :class="statusClass(agent.status)">{{ statusText(agent.status) }}</span>
          </div>
          <div class="ranking-stats">
            <span class="ranking-stat">响应 <strong>{{ agent.avgResponseTime }}m</strong></span>
            <span class="ranking-stat">满意度 <strong>{{ agent.satisfactionScore }}</strong></span>
            <span class="ranking-stat">工单 <strong>{{ agent.ticketCount }}</strong></span>
            <span class="ranking-stat">在线 <strong>{{ agent.todayOnlineDuration }}</strong></span>
          </div>
        </div>
      </div>
      <div v-if="!rankingList.length" class="empty-tip">暂无客服绩效数据</div>
    </div>

    <!-- 绩效趋势图（今日各客服工单处理量，纯 CSS 柱状图） -->
    <div class="section-header">
      <span class="section-title">今日工单处理量</span>
      <span class="section-subtitle">按客服对比</span>
    </div>
    <div class="chart-card">
      <div class="bar-chart">
        <div v-for="agent in rankingList" :key="'bar-' + agent.agentId" class="bar-col">
          <span class="bar-value">{{ agent.ticketCount }}</span>
          <div
            class="bar-fill"
            :class="{ highlight: index === 0 }"
            :style="{ height: barHeight(agent.ticketCount) + '%' }"
          ></div>
          <span class="bar-label">{{ agent.agentName }}</span>
        </div>
      </div>
      <div v-if="!rankingList.length" class="empty-tip">暂无趋势数据</div>
    </div>

    <!-- 低分预警卡片 -->
    <div class="section-header">
      <span class="section-title">低分预警</span>
      <span class="section-subtitle">满意度 &le; 4.0</span>
    </div>
    <div class="alert-card">
      <div class="alert-header">
        <div class="alert-icon-wrap">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </div>
        <span class="alert-title">满意度低分预警</span>
      </div>
      <div class="alert-count">
        {{ lowScoreAgents.length ? '共 ' + lowScoreAgents.length + ' 位客服存在低分评价，需及时关注' : '暂无低分客服，团队表现良好' }}
      </div>
      <div v-if="lowScoreAgents.length" class="alert-list">
        <div v-for="agent in lowScoreAgents" :key="'alert-' + agent.agentId" class="alert-item">
          <span class="alert-agent">{{ agent.agentName }}</span>
          <span class="alert-score">满意度 {{ agent.satisfactionScore }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getCsPerformance, getTicketStats } from '../api/admin'
import { toArray } from '../utils/safeArray'

// 时间筛选（当前接口返回实时数据，切换仅刷新）
const timeChips = [
  { key: 'today', label: '今日' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' }
]
const activeChip = ref('today')

// 客服绩效原始列表
const agents = ref([])
// 工单统计（用于 SLA / 待处理 KPI）
const ticketStats = ref({})

// 团队 KPI 计算
const teamKpi = computed(() => {
  const list = agents.value
  const count = list.length
  const avgResponse = count
    ? (list.reduce((sum, a) => sum + (Number(a.avgResponseTime) || 0), 0) / count).toFixed(1)
    : '0.0'
  const avgSatisfaction = count
    ? (list.reduce((sum, a) => sum + (Number(a.satisfactionScore) || 0), 0) / count).toFixed(1)
    : '0.0'
  const todayTickets = list.reduce((sum, a) => sum + (Number(a.ticketCount) || 0), 0)
  const onlineAgents = list.filter(a => String(a.status || '').toUpperCase() === 'ONLINE').length
  const slaRate = ticketStats.value.slaRate ?? 0
  return {
    avgResponse,
    todayTickets,
    avgSatisfaction,
    slaRate,
    onlineAgents,
    pending: ticketStats.value.pending ?? 0,
    // 趋势文本（无历史基线时显示占位）
    responseTrendClass: 'trend-flat',
    responseTrendText: '实时数据',
    satisfactionTrendClass: 'trend-flat',
    satisfactionTrendText: '实时数据'
  }
})

// 排名列表（按今日工单量降序）
const rankingList = computed(() => {
  return [...agents.value].sort((a, b) => (Number(b.ticketCount) || 0) - (Number(a.ticketCount) || 0))
})

// 低分预警（满意度 <= 4.0）
const lowScoreAgents = computed(() => {
  return agents.value.filter(a => (Number(a.satisfactionScore) || 0) <= 4.0)
})

// 排名徽章样式：前三名金银铜
function badgeClass(index) {
  if (index === 0) return 'gold'
  if (index === 1) return 'silver'
  if (index === 2) return 'bronze'
  return ''
}

// 头像取姓氏
function avatarText(name) {
  return name ? name.charAt(0) : '?'
}

// 客服状态文本
function statusText(status) {
  const s = String(status || '').toUpperCase()
  return { ONLINE: '在线', BUSY: '忙碌', OFFLINE: '离线' }[s] || '离线'
}

// 客服状态样式
function statusClass(status) {
  const s = String(status || '').toUpperCase()
  return { ONLINE: 'tag-online', BUSY: 'tag-busy', OFFLINE: 'tag-offline' }[s] || 'tag-offline'
}

// 柱状图高度（基于最大工单量归一化，最低 10% 保证可读）
function barHeight(ticketCount) {
  const max = Math.max(...rankingList.value.map(a => Number(a.ticketCount) || 0), 1)
  return Math.max(Math.round((Number(ticketCount) || 0) / max * 100), 10)
}

// 加载客服绩效
async function loadPerformance() {
  try {
    const res = await getCsPerformance()
    agents.value = toArray(res)
  } catch (err) {
    console.error('获取客服绩效失败:', err)
    agents.value = []
  }
}

// 加载工单统计
async function loadTicketStats() {
  try {
    const res = await getTicketStats()
    ticketStats.value = res || {}
  } catch (err) {
    console.error('获取工单统计失败:', err)
    ticketStats.value = {}
  }
}

onMounted(() => {
  loadPerformance()
  loadTicketStats()
})
</script>

<style scoped lang="css">
.cs-performance {
  padding: 4px 0;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 16px;
}

/* ===== 时间筛选 ===== */
.time-filter {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.filter-chip {
  padding: 6px 16px;
  font-size: 13px;
  color: var(--text-600);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.filter-chip.active {
  color: #fff;
  background: var(--primary);
  border-color: var(--primary);
}

/* ===== 区块标题 ===== */
.section-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 24px 0 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-800);
}

.section-subtitle {
  font-size: 12px;
  color: var(--text-400);
}

/* ===== KPI 网格 ===== */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.kpi-card {
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.kpi-label {
  font-size: 13px;
  color: var(--text-400);
  margin-bottom: 8px;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 6px;
}

.kpi-unit {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-400);
  margin-left: 2px;
}

.kpi-trend {
  font-size: 12px;
  color: var(--text-400);
}

.trend-flat {
  color: var(--text-400);
}

/* ===== 排名列表 ===== */
.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.ranking-badge {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: var(--text-600);
  background: var(--background-200);
  flex-shrink: 0;
}

.ranking-badge.gold {
  color: #fff;
  background: linear-gradient(135deg, #f5b84c, #e39a1d);
}

.ranking-badge.silver {
  color: #fff;
  background: linear-gradient(135deg, #c9ccd4, #9aa0ab);
}

.ranking-badge.bronze {
  color: #fff;
  background: linear-gradient(135deg, #d9a06a, #b97e45);
}

.ranking-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--background-300);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-400);
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.ranking-info {
  flex: 1;
  min-width: 0;
}

.ranking-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.ranking-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
}

.rank-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
}

.tag-online {
  color: var(--state-success);
  background: var(--state-success-surface);
}

.tag-busy {
  color: var(--state-warning);
  background: var(--state-warning-surface);
}

.tag-offline {
  color: var(--text-400);
  background: var(--background-200);
}

.ranking-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.ranking-stat {
  font-size: 12px;
  color: var(--text-400);
}

.ranking-stat strong {
  color: var(--text-700);
  font-weight: 600;
}

/* ===== 柱状图 ===== */
.chart-card {
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  height: 180px;
  padding-top: 20px;
}

.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
}

.bar-value {
  font-size: 12px;
  color: var(--text-500);
  margin-bottom: 4px;
}

.bar-fill {
  width: 60%;
  max-width: 42px;
  border-radius: 6px 6px 0 0;
  background: var(--primary);
  opacity: 0.75;
  transition: height 0.3s ease;
}

.bar-fill.highlight {
  opacity: 1;
}

.bar-label {
  font-size: 12px;
  color: var(--text-600);
  margin-top: 8px;
  white-space: nowrap;
}

/* ===== 低分预警 ===== */
.alert-card {
  padding: 16px 20px;
  background: var(--state-warning-surface);
  border: 1px solid rgba(255, 149, 0, 0.25);
  border-radius: var(--radius);
}

.alert-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.alert-icon-wrap {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--state-warning);
  background: rgba(255, 149, 0, 0.12);
}

.alert-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
}

.alert-count {
  font-size: 13px;
  color: var(--text-600);
  margin-bottom: 8px;
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.alert-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-700);
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 8px;
}

.alert-score {
  color: var(--state-error);
  font-weight: 600;
}

.empty-tip {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--text-400);
}

/* ===== 响应式适配 ===== */
@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .ranking-stats {
    gap: 8px;
  }
}
</style>
