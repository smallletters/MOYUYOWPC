<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-title-section">
      <h1 class="page-title">库存管理</h1>
      <p class="page-desc">实时监控库存水位，管理批次与调拨，预警临期与低库存风险</p>
    </div>

    <!-- KPI 卡片 4列 -->
    <section aria-label="库存概览" class="section-block">
      <div class="kpi-grid-4">
        <!-- 总 SKU 数 -->
        <div class="kpi-card-custom">
          <div class="kpi-card-header">
            <div class="kpi-icon-box kpi-icon-brand-light">
              <span class="kpi-emoji">📦</span>
            </div>
            <span class="kpi-label">总 SKU 数</span>
          </div>
          <div class="kpi-value">{{ overview.totalSku }}</div>
          <div class="kpi-trend">
            <span class="trend-label">较上周 +{{ overview.weeklyIncrease }}</span>
          </div>
        </div>
        <!-- 低库存预警 -->
        <div class="kpi-card-custom">
          <div class="kpi-card-header">
            <div class="kpi-icon-box kpi-icon-error-light">
              <span class="kpi-emoji">⚠️</span>
            </div>
            <span class="kpi-label">低库存预警</span>
          </div>
          <div class="kpi-value kpi-value-error">{{ overview.lowStockAlerts }}</div>
          <div class="kpi-trend">
            <span class="trend-label" style="color:var(--state-error);font-weight:600;">需立即补货 {{ overview.urgentReplenish }} 项</span>
          </div>
        </div>
        <!-- 临期批次 -->
        <div class="kpi-card-custom">
          <div class="kpi-card-header">
            <div class="kpi-icon-box kpi-icon-warning-light">
              <span class="kpi-emoji">⏰</span>
            </div>
            <span class="kpi-label">临期批次</span>
          </div>
          <div class="kpi-value kpi-value-warning">{{ overview.expiringBatches }}</div>
          <div class="kpi-trend">
            <span class="trend-label">30天内到期</span>
          </div>
        </div>
        <!-- 在途调拨 -->
        <div class="kpi-card-custom">
          <div class="kpi-card-header">
            <div class="kpi-icon-box kpi-icon-success-light">
              <span class="kpi-emoji">🚚</span>
            </div>
            <span class="kpi-label">在途调拨</span>
          </div>
          <div class="kpi-value kpi-value-success">{{ overview.inTransit }}</div>
          <div class="kpi-trend">
            <span class="trend-label">预计3日内到货</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 预警看板 -->
    <section aria-label="预警看板" class="section-block">
      <div class="section-header">
        <h2 class="section-title">预警看板</h2>
        <button class="link-btn">查看全部 →</button>
      </div>
      <div class="alert-dashboard-grid">
        <!-- 严重预警 -->
        <div class="alert-panel alert-panel-severe">
          <div class="alert-panel-header">
            <span class="alert-dot alert-dot-error"></span>
            <span class="alert-panel-title">严重预警</span>
            <span class="alert-count-badge alert-count-badge-error">{{ severeAlerts.length }} 项</span>
          </div>
          <div class="alert-item" v-for="item in severeAlerts" :key="item.sku">
            <div class="alert-item-top">
              <div class="alert-item-info">
                <p class="alert-item-name">{{ item.name }}</p>
                <p class="alert-item-sku">SKU: {{ item.sku }}</p>
              </div>
              <span class="alert-item-gap" :style="{ color: 'var(--state-error)' }">缺口 -{{ item.gap }}</span>
            </div>
            <div class="alert-item-progress">
              <div class="progress-label-row">
                <span class="progress-label">当前库存</span>
                <span class="progress-value" :style="{ color: 'var(--state-error)' }">{{ item.currentStock }}</span>
              </div>
              <div class="progress-track">
                <div class="progress-fill progress-fill-error" :style="{ width: item.percent + '%' }"></div>
              </div>
              <div class="progress-threshold">安全阈值 {{ item.safeThreshold }}</div>
            </div>
          </div>
        </div>
        <!-- 一般预警 -->
        <div class="alert-panel alert-panel-general">
          <div class="alert-panel-header">
            <span class="alert-dot alert-dot-warning"></span>
            <span class="alert-panel-title">一般预警</span>
            <span class="alert-count-badge alert-count-badge-warning">{{ generalAlerts.length }} 项</span>
          </div>
          <div class="alert-item" v-for="item in generalAlerts" :key="item.sku">
            <div class="alert-item-top">
              <div class="alert-item-info">
                <p class="alert-item-name">{{ item.name }}</p>
                <p class="alert-item-sku">SKU: {{ item.sku }}</p>
              </div>
              <span class="alert-item-gap" :style="{ color: 'var(--chart-3)' }">缺口 -{{ item.gap }}</span>
            </div>
            <div class="alert-item-progress">
              <div class="progress-label-row">
                <span class="progress-label">当前库存</span>
                <span class="progress-value" :style="{ color: 'var(--chart-3)' }">{{ item.currentStock }}</span>
              </div>
              <div class="progress-track">
                <div class="progress-fill progress-fill-warning" :style="{ width: item.percent + '%' }"></div>
              </div>
              <div class="progress-threshold">安全阈值 {{ item.safeThreshold }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 商品库存表格 -->
    <section aria-label="商品库存">
      <div class="section-header">
        <h2 class="section-title">商品库存</h2>
      </div>
      <div class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th class="th-left">商品名称</th>
              <th class="th-left">SKU</th>
              <th class="th-right">库存数量</th>
              <th class="th-right">安全阈值</th>
              <th class="th-center">库存状态</th>
              <th class="th-left">最后更新时间</th>
              <th class="th-center">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr class="data-row" v-for="item in inventoryData" :key="item.id">
              <td class="td-bold">{{ item.name }}</td>
              <td class="td-muted">{{ item.sku }}</td>
              <td class="td-amount" :class="stockColor(item.stock, item.safeThreshold)">{{ item.stock }}</td>
              <td class="td-amount">{{ item.safeThreshold }}</td>
              <td class="td-center">
                <span :class="stockStatusClass(item)">{{ stockStatusText(item) }}</span>
              </td>
              <td class="td-muted">{{ item.updateTime }}</td>
              <td class="td-center">
                <button class="action-link-btn" @click="handleEdit(item)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getInventoryOverview, getInventoryAlerts, getInventoryList } from '../api/admin'

// 库存概览KPI数据
const overview = reactive({
  totalSku: 0,
  weeklyIncrease: 0,
  lowStockAlerts: 0,
  urgentReplenish: 0,
  expiringBatches: 0,
  inTransit: 0
})

// 严重预警和一般预警数据
const severeAlerts = ref([])
const generalAlerts = ref([])

// 商品库存列表
const inventoryData = ref([])

// 从API加载所有库存数据
async function loadData() {
  try {
    // 并行加载概览、预警和列表数据
    const [overviewRes, alertsRes, listRes] = await Promise.all([
      getInventoryOverview(),
      getInventoryAlerts(),
      getInventoryList()
    ])
    // 填充概览KPI
    if (overviewRes) {
      Object.assign(overview, overviewRes)
    }
    // 填充预警数据
    if (alertsRes) {
      severeAlerts.value = alertsRes.severe || []
      generalAlerts.value = alertsRes.general || []
    }
    // 填充库存列表
    const list = (listRes && listRes.records) || listRes || []
    inventoryData.value = list
  } catch (e) {
    console.error('加载库存数据失败:', e)
    ElMessage.error('加载库存数据失败')
  }
}

function stockColor(stock, threshold) {
  if (stock === 0) return 'td-stock-out'
  if (stock < threshold) return 'td-stock-low'
  return ''
}

function stockStatusClass(item) {
  if (item.stock === 0) return 'inv-status-tag inv-status-out'
  if (item.stock < item.safeThreshold) return 'inv-status-tag inv-status-low'
  return 'inv-status-tag inv-status-normal'
}

function stockStatusText(item) {
  if (item.stock === 0) return '缺货'
  if (item.stock < item.safeThreshold) return '低库存'
  return '正常'
}

function handleEdit(item) {
  ElMessage.info(`编辑: ${item.name}`)
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-title-section { margin-bottom: 24px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0 0 6px; }
.page-desc { font-size: 13px; color: var(--text-400); margin: 0; }
.section-block { margin-bottom: 24px; }

/* KPI 网格 */
.kpi-grid-4 {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.kpi-card-custom {
  border-radius: var(--radius);
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s ease, transform 0.2s ease;
}
.kpi-card-custom:hover {
  border-color: var(--primary);
  transform: translateY(-1px);
}
.kpi-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.kpi-icon-box {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--background-200);
  display: flex;
  align-items: center;
  justify-content: center;
}
.kpi-icon-brand-light { background: var(--brand-50); }
.kpi-icon-error-light { background: var(--state-error-surface); }
.kpi-icon-warning-light { background: #fff8ed; }
.kpi-icon-success-light { background: var(--state-success-surface); }
.kpi-emoji { font-size: 16px; }
.kpi-label { font-size: 13px; font-weight: 500; color: var(--text-500); }
.kpi-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 8px;
  font-variant-numeric: tabular-nums;
}
.kpi-value-error { color: var(--state-error); }
.kpi-value-warning { color: var(--chart-3); }
.kpi-value-success { color: var(--state-success); }
.kpi-trend { display: flex; align-items: center; gap: 4px; }
.trend-label { font-size: 12px; color: var(--text-400); }

/* Section header */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0;
}
.link-btn {
  font-size: 13px;
  font-weight: 600;
  color: var(--primary);
  background: none;
  border: none;
  cursor: pointer;
  font-family: var(--font-sans);
}

/* Alert dashboard grid */
.alert-dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* Alert panel */
.alert-panel {
  border-radius: 1rem;
  padding: 16px;
  box-shadow: var(--shadow-sm);
}
.alert-panel-severe { background: var(--state-error-surface); }
.alert-panel-general { background: #fff8ed; }

.alert-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.alert-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.alert-dot-error { background: var(--state-error); }
.alert-dot-warning { background: var(--chart-3); }
.alert-panel-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.alert-panel-severe .alert-panel-title { color: var(--state-error); }
.alert-panel-general .alert-panel-title { color: var(--chart-3); }
.alert-count-badge {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 999px;
}
.alert-count-badge-error {
  background: var(--state-error);
  color: #fff;
}
.alert-count-badge-warning {
  background: var(--chart-3);
  color: #fff;
}

/* Alert item card */
.alert-item {
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 8px;
  background: var(--background-50);
}
.alert-item:last-child { margin-bottom: 0; }
.alert-item-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.alert-item-info { flex: 1; min-width: 0; }
.alert-item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-800);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.alert-item-sku {
  font-size: 12px;
  color: var(--text-400);
  margin: 2px 0 0;
}
.alert-item-gap {
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  margin-left: 8px;
}

/* Progress bar */
.alert-item-progress { margin-top: 8px; }
.progress-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.progress-label {
  font-size: 12px;
  color: var(--text-500);
}
.progress-value {
  font-size: 12px;
  font-weight: 600;
}
.progress-track {
  width: 100%;
  height: 6px;
  border-radius: 999px;
  background: var(--background-300);
  overflow: hidden;
}
.progress-fill {
  height: 6px;
  border-radius: 999px;
}
.progress-fill-error { background: var(--state-error); }
.progress-fill-warning { background: var(--chart-3); }
.progress-threshold {
  font-size: 11px;
  color: var(--text-400);
  margin-top: 4px;
}

/* Table wrapper */
.table-wrapper {
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
}

/* Data table */
.data-table {
  width: 100%;
  border-collapse: collapse;
}
.data-table thead th {
  padding: 12px 20px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-600);
  border-bottom: 1px solid var(--border);
  background: var(--background-100);
  letter-spacing: 0.02em;
}
.th-left { text-align: left; }
.th-right { text-align: right; }
.th-center { text-align: center; }

.data-row {
  border-bottom: 1px solid var(--border);
  transition: background 0.15s ease;
}
.data-row:last-child { border-bottom: none; }
.data-row:hover { background: var(--accent); }
.data-row td {
  padding: 14px 20px;
  font-size: 13px;
  vertical-align: middle;
}
.td-bold {
  font-weight: 600;
  color: var(--text-800);
}
.td-muted {
  color: var(--text-500);
}
.td-amount {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  text-align: right;
  color: var(--text-800);
}
.td-stock-low { color: var(--state-error); }
.td-stock-out { color: var(--state-error); }
.td-center { text-align: center; }

/* Inventory status tag */
.inv-status-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.inv-status-normal {
  background: var(--state-success-surface);
  color: var(--state-success);
}
.inv-status-low {
  background: var(--state-error-surface);
  color: var(--state-error);
}
.inv-status-out {
  background: var(--state-error-surface);
  color: var(--state-error);
}

/* Action link */
.action-link-btn {
  font-size: 13px;
  font-weight: 600;
  color: var(--primary);
  background: none;
  border: none;
  cursor: pointer;
  font-family: var(--font-sans);
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.15s ease;
}
.action-link-btn:hover {
  background: var(--accent);
}
</style>
