<template>
  <div class="page-wrapper">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>漏斗分析</h1>
      <p>追踪用户从浏览到完成的转化链路，定位流失关键节点</p>
    </div>

    <!-- KPI 概览 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">👀</span>
          <span class="kpi-card-label">总访问</span>
        </div>
        <div class="kpi-card-value">{{ fmtNum(totalVisits) }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">漏斗起点</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🎯</span>
          <span class="kpi-card-label">最终转化率</span>
        </div>
        <div class="kpi-card-value">{{ finalRate }}%</div>
        <div class="kpi-card-trend"><span class="kpi-trend-up">整体转化</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">📉</span>
          <span class="kpi-card-label">总流失</span>
        </div>
        <div class="kpi-card-value">{{ fmtNum(totalDrop) }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-down">流失用户</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🧩</span>
          <span class="kpi-card-label">转化环节</span>
        </div>
        <div class="kpi-card-value">{{ funnelData.length }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">漏斗阶段数</span></div>
      </div>
    </div>

    <!-- 核心转化漏斗（保留原有） -->
    <div class="content-card funnel-card">
      <div class="content-card-header">
        <span>核心转化漏斗</span>
        <span class="content-card-sub">各环节转化率逐级递减</span>
      </div>
      <div class="funnel-chart">
        <div v-for="(step, index) in funnelData" :key="step.name" class="funnel-step">
          <div class="funnel-label">{{ step.name }}</div>
          <div class="funnel-bar-wrapper">
            <div class="funnel-bar" :style="{ width: Math.max(8, step.percent) + '%', background: getColor(index) }">
              <span class="funnel-count">{{ step.count.toLocaleString() }}</span>
            </div>
          </div>
          <div class="funnel-rate">{{ step.rate }}%</div>
        </div>
        <div v-if="funnelData.length === 0" class="empty-state">
          <div class="empty-state-icon">📊</div>
          <div class="empty-state-text">暂无漏斗数据</div>
        </div>
      </div>
    </div>

    <!-- 流失分析：左列流失节点分析 / 右列流失用户画像（并排布局） -->
    <div class="analysis-grid">
      <!-- 左列：流失节点分析 -->
      <div>
        <div class="section-header">
          <span class="section-header-icon section-icon-error">⚠️</span>
          <h2>流失节点分析</h2>
        </div>

        <!-- 各环节流失率条形图（由真实漏斗数据推导） -->
        <div class="content-card analysis-card">
          <div class="content-card-header">
            <span>各环节流失率</span>
            <span class="content-card-sub">相邻环节流失比例</span>
          </div>
          <div class="drop-list">
            <div v-for="node in dropNodes" :key="node.from + node.to" class="drop-item">
              <div class="drop-item-top">
                <span class="drop-item-label">{{ node.from }} → {{ node.to }}</span>
                <span class="drop-item-rate" :class="{ 'drop-item-rate--max': node.isMax }">
                  {{ node.rate.toFixed(1) }}%
                </span>
              </div>
              <div class="drop-track">
                <div
                  class="drop-fill"
                  :class="{ 'drop-fill--max': node.isMax }"
                  :style="{ width: Math.max(4, node.rate) + '%' }"
                ></div>
              </div>
              <div class="drop-item-sub">流失 {{ fmtNum(node.lostUsers) }} 人</div>
            </div>
            <div v-if="dropNodes.length === 0" class="empty-state">
              <div class="empty-state-icon">📉</div>
              <div class="empty-state-text">暂无流失节点数据</div>
            </div>
          </div>
        </div>

        <!-- 最大流失节点：红色突出卡片（节点信息由真实数据推导） -->
        <div v-if="maxDropNode" class="max-drop-card">
          <div class="max-drop-icon">⚠️</div>
          <div class="max-drop-body">
            <h3>最大流失节点</h3>
            <p class="max-drop-name">{{ maxDropNode.from }} → {{ maxDropNode.to }}</p>
            <p class="max-drop-desc">流失率 {{ maxDropNode.rate.toFixed(1) }}%，流失用户 {{ fmtNum(maxDropNode.lostUsers) }} 人</p>
            <div class="max-drop-trend">📈 较上期流失增加 5.2%（示例数据）</div>
          </div>
        </div>

        <!-- 流失原因推测（示例数据） -->
        <div class="content-card analysis-card">
          <div class="content-card-header">
            <span>流失原因推测</span>
            <span class="content-card-sub">示例数据</span>
          </div>
          <div class="reason-list">
            <div v-for="reason in dropReasons" :key="reason.name" class="reason-item">
              <div class="reason-icon">{{ reason.icon }}</div>
              <div class="reason-info">
                <span class="reason-name">{{ reason.name }}</span>
                <span class="reason-desc">{{ reason.desc }}</span>
              </div>
              <span class="reason-percent">{{ reason.percent }}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右列：流失用户画像 -->
      <div>
        <div class="section-header">
          <span class="section-header-icon">👥</span>
          <h2>流失用户画像</h2>
        </div>

        <!-- 新老用户占比 + 设备分布（示例数据） -->
        <div class="content-card analysis-card">
          <div class="user-type-grid">
            <div class="user-type-box">
              <span class="user-type-label">新用户占比</span>
              <p class="user-type-value">{{ userProfile.newUsers }}%</p>
              <div class="user-type-track">
                <div class="user-type-fill user-type-fill--primary" :style="{ width: userProfile.newUsers + '%' }"></div>
              </div>
            </div>
            <div class="user-type-box">
              <span class="user-type-label">老用户占比</span>
              <p class="user-type-value">{{ userProfile.oldUsers }}%</p>
              <div class="user-type-track">
                <div class="user-type-fill user-type-fill--brand" :style="{ width: userProfile.oldUsers + '%' }"></div>
              </div>
            </div>
          </div>

          <div class="device-section">
            <span class="device-label">设备分布</span>
            <div class="device-tags">
              <span v-for="device in userProfile.devices" :key="device.name" class="device-tag">
                {{ device.icon }} {{ device.name }} {{ device.percent }}%
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 渠道漏斗对比（示例数据） -->
    <div class="section-header">
      <span class="section-header-icon">🔀</span>
      <h2>渠道漏斗对比</h2>
    </div>
    <div class="content-card">
      <div class="channel-body">
        <!-- 渠道图例 -->
        <div class="channel-legend">
          <span v-for="item in channelList" :key="item.name" class="legend-item">
            <span class="legend-dot" :style="{ background: item.color }"></span>{{ item.name }}
          </span>
        </div>

        <!-- 三列：各阶段 × 各渠道人数对比条形图 -->
        <div class="channel-grid">
          <div v-for="stage in channelData" :key="stage.name" class="channel-col">
            <div class="channel-col-title">{{ stage.name }}</div>
            <div v-for="(row, j) in stage.rows" :key="row.shortName" class="channel-row">
              <span class="channel-row-label">{{ row.shortName }}</span>
              <div class="channel-track">
                <div class="channel-fill" :style="{ width: row.percent + '%', background: channelList[j].color }"></div>
              </div>
              <span class="channel-row-value">{{ fmtNum(row.count) }}</span>
            </div>
          </div>
        </div>

        <!-- 分隔线 -->
        <div class="channel-divider"></div>

        <!-- 各渠道最终转化率 -->
        <div>
          <span class="channel-rate-title">各渠道最终转化率</span>
          <div class="channel-rate-grid">
            <div v-for="rate in channelRates" :key="rate.name" class="channel-rate-box">
              <span class="channel-rate-label">{{ rate.name }}</span>
              <p class="channel-rate-value" :style="{ color: rate.color }">{{ rate.rate }}%</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getFunnelAnalysis } from '../api/admin'
import { toArray } from '../utils/safeArray'

const funnelData = ref([])

// KPI：总访问 / 最终转化率 / 总流失
const totalVisits = computed(() => funnelData.value[0]?.count || 0)
const finalRate = computed(() => {
  const last = funnelData.value[funnelData.value.length - 1]
  return last ? last.rate : '0'
})
const totalDrop = computed(() => {
  const first = funnelData.value[0]?.count || 0
  const last = funnelData.value[funnelData.value.length - 1]?.count || 0
  return first - last
})

// ===== 流失节点分析：相邻环节流失率（由真实漏斗数据推导） =====
const dropNodes = computed(() => {
  const list = funnelData.value
  if (list.length < 2) return []
  const nodes = []
  for (let i = 0; i < list.length - 1; i++) {
    const fromCount = Number(list[i].count) || 0
    const toCount = Number(list[i + 1].count) || 0
    if (!fromCount) continue
    // 流失率 = (上一环节 - 下一环节) / 上一环节，保留 1 位小数，最小为 0
    const rate = Math.max(0, Math.round(((fromCount - toCount) / fromCount) * 1000) / 10)
    nodes.push({
      from: list[i].name,
      to: list[i + 1].name,
      rate,
      lostUsers: Math.max(0, fromCount - toCount)
    })
  }
  const maxRate = Math.max(...nodes.map(n => n.rate))
  // 标记流失率最大的节点（红色突出）
  return nodes.map(n => ({ ...n, isMax: maxRate > 0 && n.rate === maxRate }))
})

// ===== 最大流失节点：流失率最高的相邻环节 =====
const maxDropNode = computed(() => {
  const nodes = dropNodes.value
  return nodes.find(n => n.isMax) || null
})

// ===== 流失原因推测（示例数据：无真实接口，与设计稿保持一致） =====
const dropReasons = [
  { icon: '🚚', name: '运费过高', desc: '未达免邮门槛', percent: 38 },
  { icon: '🏷️', name: '价格偏高', desc: '对比竞品后放弃', percent: 29 },
  { icon: '💳', name: '支付方式', desc: '不支持期望的支付方式', percent: 18 },
  { icon: '⋯', name: '其他原因', desc: '页面异常/犹豫不决等', percent: 15 }
]

// ===== 流失用户画像（示例数据：无真实接口，与设计稿保持一致） =====
const userProfile = {
  newUsers: 72,
  oldUsers: 28,
  devices: [
    { name: 'iOS', percent: 58, icon: '📱' },
    { name: 'Android', percent: 35, icon: '🤖' },
    { name: 'PC', percent: 7, icon: '💻' }
  ]
}

// ===== 渠道漏斗对比（示例数据：无真实接口，与设计稿保持一致） =====
// 渠道图例与配色
const channelList = [
  { name: '自然搜索', color: 'var(--primary)' },
  { name: '付费广告', color: 'var(--state-success)' },
  { name: '社交媒体', color: 'var(--state-warning)' }
]

// 各阶段 × 各渠道人数（percent 为条形宽度占比，与设计稿一致）
const channelData = [
  {
    name: '浏览商品',
    rows: [
      { shortName: '自然', count: 5820, percent: 100 },
      { shortName: '广告', count: 3960, percent: 68 },
      { shortName: '社交', count: 2800, percent: 48 }
    ]
  },
  {
    name: '加入购物车',
    rows: [
      { shortName: '自然', count: 2211, percent: 38 },
      { shortName: '广告', count: 1633, percent: 28 },
      { shortName: '社交', count: 182, percent: 16 }
    ]
  },
  {
    name: '完成支付',
    rows: [
      { shortName: '自然', count: 930, percent: 16 },
      { shortName: '广告', count: 245, percent: 10 },
      { shortName: '社交', count: 83, percent: 5 }
    ]
  }
]

// 各渠道最终转化率
const channelRates = [
  { name: '自然搜索', rate: '16.0', color: 'var(--primary)' },
  { name: '付费广告', rate: '6.2', color: 'var(--state-success)' },
  { name: '社交媒体', rate: '3.0', color: 'var(--state-warning)' }
]

function fmtNum(n) {
  return Number(n || 0).toLocaleString('en-US', { maximumFractionDigits: 0 })
}

async function loadData() {
  try {
    const res = await getFunnelAnalysis()
    // 后端返回扁平数组：stage, userCount, conversionRate
    const list = toArray(res)
    funnelData.value = list.map(item => ({
      name: item.stage,
      count: item.userCount,
      rate: item.conversionRate,
      percent: item.conversionRate
    }))
  } catch (err) {
    console.error('获取漏斗数据失败', err)
  }
}

// 漏斗颜色（与设计稿 admin-funnel.html 一致：随漏斗层级由浅入深）
function getColor(index) {
  const colors = ['var(--brand-500)', 'var(--brand-400)', 'var(--brand-600)', 'var(--brand-700)', 'var(--brand-800)']
  return colors[index]
}

onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }

/* 内容卡片 */
.content-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}
.content-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
}
.content-card-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-400);
}
.funnel-card { margin-bottom: 24px; }

/* 区块标题（复用设计稿样式） */
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.section-header h2 {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0;
}
.section-header-icon { font-size: 16px; color: var(--primary); }
.section-icon-error { color: var(--state-error); }

/* 流失分析两栏布局 */
.analysis-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}
.analysis-card { margin-bottom: 16px; }
.analysis-card:last-child { margin-bottom: 0; }

/* 各环节流失率条形图 */
.drop-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
}
.drop-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.drop-item-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-600);
}
.drop-item-rate {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-500);
  font-variant-numeric: tabular-nums;
}
.drop-item-rate--max { color: var(--state-error); }
.drop-track {
  height: 10px;
  border-radius: 5px;
  background: var(--background-200);
  overflow: hidden;
}
.drop-fill {
  height: 100%;
  border-radius: 5px;
  background: var(--brand-400);
  transition: width 0.4s ease;
}
.drop-fill--max { background: var(--state-error); }
.drop-item-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-400);
}

/* 最大流失节点（红色突出卡片） */
.max-drop-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  border-radius: var(--radius);
  padding: 18px;
  margin-bottom: 16px;
  background: var(--card);
  border: 1px solid var(--state-error);
  box-shadow: var(--shadow-sm);
}
.max-drop-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--state-error-surface);
  font-size: 20px;
}
.max-drop-body { flex: 1; min-width: 0; }
.max-drop-body h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  margin: 0 0 4px;
}
.max-drop-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--state-error);
  margin: 0 0 4px;
}
.max-drop-desc {
  font-size: 12px;
  color: var(--text-500);
  margin: 0 0 8px;
}
.max-drop-trend {
  font-size: 12px;
  font-weight: 500;
  color: var(--state-error);
}

/* 流失原因推测 */
.reason-list {
  display: flex;
  flex-direction: column;
  padding: 20px;
}
.reason-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
}
.reason-item + .reason-item {
  border-top: 1px solid var(--border);
}
.reason-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--brand-50);
  font-size: 16px;
}
.reason-info { flex: 1; min-width: 0; }
.reason-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-800);
}
.reason-desc {
  display: block;
  font-size: 12px;
  color: var(--text-400);
  margin-top: 1px;
}
.reason-percent {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-800);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

/* 流失用户画像 */
.user-type-grid {
  display: flex;
  gap: 12px;
  padding: 18px;
}
.user-type-box {
  flex: 1;
  border-radius: 12px;
  padding: 14px;
  background: var(--accent);
}
.user-type-label {
  font-size: 12px;
  color: var(--text-400);
}
.user-type-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 6px 0 10px;
}
.user-type-track {
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: var(--background-300);
}
.user-type-fill {
  height: 6px;
  border-radius: 3px;
  transition: width 0.4s ease;
}
.user-type-fill--primary { background: var(--primary); }
.user-type-fill--brand { background: var(--brand-300); }
.device-section {
  padding: 14px 18px 18px;
  border-top: 1px solid var(--border);
}
.device-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-500);
}
.device-tags {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.device-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  background: var(--secondary);
  color: var(--text-600);
}

/* 渠道漏斗对比 */
.channel-body { padding: 20px; }
.channel-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-500);
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.channel-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20px;
}
.channel-col-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-600);
  margin-bottom: 8px;
}
.channel-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.channel-row:last-child { margin-bottom: 0; }
.channel-row-label {
  font-size: 12px;
  width: 36px;
  flex-shrink: 0;
  color: var(--text-400);
}
.channel-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--background-200);
}
.channel-fill {
  height: 8px;
  border-radius: 4px;
  transition: width 0.4s ease;
}
.channel-row-value {
  font-size: 12px;
  font-weight: 600;
  width: 44px;
  text-align: right;
  flex-shrink: 0;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.channel-divider {
  height: 1px;
  background: var(--border);
  margin: 20px 0;
}
.channel-rate-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-600);
}
.channel-rate-grid {
  display: flex;
  gap: 12px;
  margin-top: 10px;
}
.channel-rate-box {
  flex: 1;
  border-radius: 12px;
  padding: 12px;
  text-align: center;
  background: var(--brand-50);
}
.channel-rate-box:nth-child(2) { background: var(--state-success-surface); }
.channel-rate-box:nth-child(3) { background: var(--state-warning-surface); }
.channel-rate-label {
  font-size: 12px;
  color: var(--text-400);
}
.channel-rate-value {
  font-size: 18px;
  font-weight: 700;
  margin: 4px 0 0;
  font-variant-numeric: tabular-nums;
}

/* 漏斗图（保留原有） */
.funnel-chart { display: flex; flex-direction: column; gap: 16px; padding: 24px; }
.funnel-step { display: flex; align-items: center; gap: 16px; }
.funnel-label { width: 120px; font-size: 14px; font-weight: 500; color: var(--text-600); text-align: right; }
.funnel-bar-wrapper { flex: 1; height: 44px; background: var(--background-100); border-radius: 8px; overflow: hidden; }
.funnel-bar {
  height: 100%;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 16px;
  transition: width 0.5s ease;
}
.funnel-count { color: #fff; font-size: 14px; font-weight: 600; font-variant-numeric: tabular-nums; }
.funnel-rate { width: 70px; font-size: 14px; font-weight: 600; color: var(--text-500); font-variant-numeric: tabular-nums; }
</style>
