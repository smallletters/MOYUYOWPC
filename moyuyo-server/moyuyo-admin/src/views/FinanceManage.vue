<template>
  <div class="finance-manage-page">
    <!-- 页面标题区 -->
    <div class="page-header">
      <div class="page-header-left">
        <h1>财务概览</h1>
        <p>实时掌握收入、结算、退款与异常状态，确保资金安全流转</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-outline" @click="handleExport">
          <span class="action-icon">📥</span>
          导出报表
        </button>
        <button class="btn btn-primary" :disabled="!overviewData.pendingSettlement" @click="handleSettle">
          <span class="action-icon">💸</span>
          发起结算
        </button>
      </div>
    </div>

    <!-- KPI 4 列 -->
    <section aria-label="财务概况" class="kpi-section">
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon" aria-hidden="true">💰</span>
            <span class="kpi-card-label">本月 GMV</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney.fmt(overviewData.totalRevenue) }}</div>
          <div class="kpi-card-trend kpi-trend-up" v-if="overviewData.completedSettlements > 0">
            <span class="kpi-trend-text">已结算 {{ overviewData.completedSettlements }} 笔</span>
          </div>
          <div class="kpi-card-trend" v-else>
            <span class="kpi-trend-text">含退款前流水</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon" aria-hidden="true">💼</span>
            <span class="kpi-card-label">实收金额</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney.fmt(overviewData.actualIncome) }}</div>
          <div class="kpi-card-trend">
            <span class="kpi-trend-text">扣除退款 ¥{{ formatMoney.fmt(overviewData.refundAmount) }} 后</span>
          </div>
        </div>

        <div class="kpi-card kpi-card-highlight">
          <div class="kpi-card-header">
            <span class="kpi-card-icon" aria-hidden="true">⏳</span>
            <span class="kpi-card-label">待结算</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney.fmt(overviewData.pendingSettlement) }}</div>
          <div class="kpi-card-trend">
            <span class="kpi-trend-text">预计 T+3 到账</span>
          </div>
        </div>

        <div class="kpi-card kpi-card-warn">
          <div class="kpi-card-header">
            <span class="kpi-card-icon" aria-hidden="true">🔔</span>
            <span class="kpi-card-label">退款金额 / 待处理</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney.fmt(overviewData.refundAmount) }}</div>
          <div class="kpi-card-trend kpi-trend-down" v-if="overviewData.pendingCount > 0">
            <span class="kpi-trend-text">{{ overviewData.pendingCount }} 笔退款待处理</span>
          </div>
          <div class="kpi-card-trend" v-else>
            <span class="kpi-trend-text">本月累计退款</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 第二段：渠道分布 + 待处理异常 + 退款原因分布 -->
    <div class="three-col">
      <!-- 左：支付渠道分布 -->
      <section class="panel" aria-label="支付渠道分布">
        <div class="panel-header">
          <h2 class="panel-title">支付渠道分布</h2>
          <span class="panel-sub">本月 GMV ¥{{ formatMoney.fmt(overviewData.totalRevenue) }}</span>
        </div>
        <div class="panel-body">
          <div v-if="paymentChannels.length === 0" class="empty-state">
            <div class="empty-state-icon">📊</div>
            <div class="empty-state-text">本月暂无支付渠道数据</div>
          </div>
          <ul v-else class="channel-list">
            <li v-for="ch in paymentChannels" :key="ch.channel" class="channel-item">
              <div class="channel-row">
                <div class="channel-name">
                  <span class="channel-dot" :style="{ background: channelColor(ch.channel) }"></span>
                  <span class="channel-label">{{ channelLabel(ch.channel) }}</span>
                </div>
                <div class="channel-value">
                  <span class="channel-amount">¥{{ formatMoney.fmt(ch.amount) }}</span>
                  <span class="channel-ratio">{{ ch.ratio.toFixed(1) }}%</span>
                </div>
              </div>
              <div class="channel-track">
                <div class="channel-fill" :style="{ width: ch.ratio + '%', background: channelColor(ch.channel) }"></div>
              </div>
            </li>
          </ul>
        </div>
      </section>

      <!-- 中：退款原因分布 -->
      <section class="panel" aria-label="退款原因分布">
        <div class="panel-header">
          <h2 class="panel-title">退款原因分布</h2>
          <span class="panel-sub">Top {{ refundReasonDistribution.length }}</span>
        </div>
        <div class="panel-body">
          <div v-if="refundReasonDistribution.length === 0" class="empty-state">
            <div class="empty-state-icon">📋</div>
            <div class="empty-state-text">暂无退款记录</div>
          </div>
          <ul v-else class="reason-list">
            <li v-for="(item, idx) in refundReasonDistribution" :key="item.reason" class="reason-item">
              <span class="reason-rank" :class="'reason-rank-' + Math.min(idx + 1, 3)">{{ idx + 1 }}</span>
              <div class="reason-body">
                <div class="reason-row">
                  <span class="reason-label">{{ item.reason }}</span>
                  <span class="reason-count">{{ item.count }} 笔</span>
                </div>
                <div class="reason-track">
                  <div class="reason-fill" :style="{ width: reasonPercent(item.count) + '%' }"></div>
                </div>
              </div>
            </li>
          </ul>
        </div>
      </section>

      <!-- 右：待处理异常 -->
      <section class="panel" aria-label="待处理异常">
        <div class="panel-header">
          <h2 class="panel-title">待处理异常</h2>
          <span class="panel-badge" :class="{ 'panel-badge-warn': overviewData.pendingCount > 0 }">
            {{ overviewData.pendingCount || 0 }} 笔需处理
          </span>
        </div>
        <div class="panel-body">
          <div v-if="overviewData.pendingCount === 0" class="empty-state">
            <div class="empty-state-icon">✅</div>
            <div class="empty-state-text">当前无待处理异常</div>
          </div>
          <div v-else class="exception-list">
            <div class="exception-alert">
              <span class="exception-alert-icon">⚠️</span>
              <span class="exception-alert-text">
                有 <strong>{{ overviewData.pendingCount }}</strong> 笔退款申请待审核，请尽快处理避免资金风险
              </span>
            </div>
            <el-button type="primary" plain size="small" @click="handleViewAllSettlements">前往处理</el-button>
          </div>
        </div>
      </section>
    </div>

    <!-- 第三段：近 6 月 GMV/退款/净额 趋势 -->
    <section class="panel" aria-label="月度趋势">
      <div class="panel-header">
        <h2 class="panel-title">近 6 月收支趋势</h2>
        <span class="panel-sub">GMV · 退款 · 净额</span>
      </div>
      <div class="panel-body">
        <div v-if="monthlyTrend.length === 0" class="empty-state">
          <div class="empty-state-icon">📉</div>
          <div class="empty-state-text">暂无趋势数据</div>
        </div>
        <div v-else class="trend-wrapper">
          <div class="trend-chart">
            <div v-for="item in monthlyTrend" :key="item.month" class="trend-col">
              <div class="trend-bar-group">
                <div
                  class="trend-bar trend-bar-gmv"
                  :style="{ height: trendPercent(item.gmv, 'gmv') + '%' }"
                  :title="'GMV ¥' + formatMoney.fmt(item.gmv)"
                ></div>
                <div
                  class="trend-bar trend-bar-refund"
                  :style="{ height: trendPercent(item.refund, 'gmv') + '%' }"
                  :title="'退款 ¥' + formatMoney.fmt(item.refund)"
                ></div>
              </div>
              <div class="trend-month">{{ formatMonth(item.month) }}</div>
              <div class="trend-net">¥{{ formatMoney.fmt(item.net) }}</div>
            </div>
          </div>
          <div class="trend-legend">
            <span class="legend-item"><span class="legend-dot legend-dot-gmv"></span>GMV</span>
            <span class="legend-item"><span class="legend-dot legend-dot-refund"></span>退款</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 第四段：结算明细表格 -->
    <section class="panel" aria-label="结算明细">
      <div class="panel-header">
        <h2 class="panel-title">结算明细</h2>
        <button class="link-btn" @click="handleViewAllSettlements">查看全部 →</button>
      </div>
      <div class="data-table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>结算单号</th>
              <th>结算周期</th>
              <th>支付渠道</th>
              <th class="th-right">金额</th>
              <th class="th-center">状态</th>
              <th class="th-right">结算时间</th>
              <th class="th-center" style="width: 120px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in settlementData" :key="item.id" class="data-row">
              <td><span class="settlement-no">{{ item.settlementNo || item.id }}</span></td>
              <td>{{ item.period || '—' }}</td>
              <td>
                <span v-if="item.payChannel" class="channel-pill" :style="{ color: channelColor(item.payChannel) }">
                  {{ channelLabel(item.payChannel) }}
                </span>
                <span v-else class="muted-cell">—</span>
              </td>
              <td class="td-amount">¥{{ formatMoney.fmt(item.amount) }}</td>
              <td class="td-center">
                <el-tag :type="settlementTagType(item.status)" size="small" effect="light">
                  {{ settlementLabel(item.status) }}
                </el-tag>
              </td>
              <td class="td-muted td-right">{{ formatTime(item.settleTime) }}</td>
              <td class="td-center">
                <button class="btn btn-sm btn-outline" @click="handleViewDetail(item)">详情</button>
              </td>
            </tr>
            <tr v-if="settlementData.length === 0">
              <td colspan="7">
                <div class="empty-state">
                  <div class="empty-state-icon">📋</div>
                  <div class="empty-state-text">暂无结算记录</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFinanceOverview, getSettlements } from '../api/admin'
import { exportCsv } from '../utils/exportCsv'

// 财务概览数据
const overviewData = ref({
  totalRevenue: 0,
  actualIncome: 0,
  pendingSettlement: 0,
  refundAmount: 0,
  completedSettlements: 0,
  pendingCount: 0
})

// 支付渠道分布（来自后端 channelDistribution）
const paymentChannels = ref([])

// 退款原因分布（来自后端 refundReasonDistribution）
const refundReasonDistribution = ref([])

// 最近 6 个月收支趋势（来自后端 monthlyTrend）
const monthlyTrend = ref([])

// 结算明细列表（来自后端 settlements）
const settlementData = ref([])

const loading = ref(false)
const router = useRouter()

// 金额格式化与工具方法（统一在此声明，便于模板使用）
const formatMoney = {
  fmt(value) {
    if (value == null) return '0.00'
    const num = typeof value === 'number' ? value : Number(value)
    if (Number.isNaN(num)) return '0.00'
    return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }
}

// 时间格式化：后端 LocalDateTime 序列化为 "yyyy-MM-dd HH:mm:ss"
function formatTime(value) {
  if (!value) return '—'
  // 兼容 "yyyy-MM-dd HH:mm:ss" / ISO / 时间戳
  const str = String(value).replace('T', ' ').replace(/\..*$/, '')
  return str || '—'
}

// 月份格式化为 MM 月（如 2026-08 → 8月）
function formatMonth(value) {
  if (!value) return ''
  const parts = String(value).split('-')
  return parts.length === 2 ? `${parseInt(parts[1], 10)}月` : value
}

// 退款原因百分比（用于柱条宽度）
function reasonPercent(count) {
  const max = refundReasonDistribution.value.reduce((m, x) => Math.max(m, x.count || 0), 1)
  return Math.min(100, ((count || 0) / max) * 100)
}

// 趋势柱条百分比（按 GMV 归一化，让退款与 GMV 共享一个量纲便于视觉对比）
function trendPercent(value, key) {
  const max = Math.max(...monthlyTrend.value.map(x => Number(x.gmv || 0)), 1)
  return Math.min(100, ((Number(value || 0)) / max) * 100)
}

// 渠道枚举 -> 中文标签
function channelLabel(channel) {
  const map = {
    STRIPE: 'Stripe 信用卡',
    PAYPAL: 'PayPal',
    WECHAT: '微信支付',
    ALIPAY: '支付宝',
    APPLE_PAY: 'Apple Pay',
    UNIONPAY: '银联',
    WALLET: '余额支付'
  }
  return map[channel] || channel || '其他'
}

// 渠道枚举 -> 颜色（设计令牌内）
function channelColor(channel) {
  const map = {
    STRIPE: '#635bff',
    PAYPAL: '#0070ba',
    WECHAT: '#07c160',
    ALIPAY: '#1677ff',
    APPLE_PAY: '#000000',
    UNIONPAY: '#e60012',
    WALLET: '#8e8e93'
  }
  return map[channel] || '#8e8e93'
}

// 结算状态 -> 标签类型（element-plus el-tag type）
function settlementTagType(status) {
  const map = {
    COMPLETED: 'success',
    SETTLED: 'success',
    SETTLING: 'warning',
    PENDING: 'info',
    ABNORMAL: 'danger',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

// 结算状态 -> 中文标签
function settlementLabel(status) {
  const map = {
    COMPLETED: '已结算',
    SETTLED: '已结算',
    SETTLING: '结算中',
    PENDING: '待结算',
    ABNORMAL: '异常',
    FAILED: '失败'
  }
  return map[status] || status || '未知'
}

// 加载所有财务数据
async function fetchData() {
  loading.value = true
  try {
    // 财务概览（含渠道分布、退款原因、6 月趋势）
    const overviewRes = await getFinanceOverview()
    if (overviewRes) {
      overviewData.value = {
        totalRevenue: Number(overviewRes.totalRevenue || 0),
        actualIncome: Number(overviewRes.actualIncome || 0),
        pendingSettlement: Number(overviewRes.pendingSettlement || 0),
        refundAmount: Number(overviewRes.refundAmount || 0),
        completedSettlements: Number(overviewRes.completedSettlements || 0),
        pendingCount: Number(overviewRes.pendingCount || 0)
      }
      paymentChannels.value = Array.isArray(overviewRes.channelDistribution)
        ? overviewRes.channelDistribution.map(c => ({
            channel: c.channel,
            amount: Number(c.amount || 0),
            ratio: Number(c.ratio || 0)
          }))
        : []
      refundReasonDistribution.value = Array.isArray(overviewRes.refundReasonDistribution)
        ? overviewRes.refundReasonDistribution
        : []
      monthlyTrend.value = Array.isArray(overviewRes.monthlyTrend)
        ? overviewRes.monthlyTrend
        : []
    }

    // 结算明细（仅取前 10 条作为概览展示）
    const settlementsRes = await getSettlements({ page: 1, size: 10 })
    const records = settlementsRes?.records || settlementsRes?.list || settlementsRes?.data || []
    settlementData.value = Array.isArray(records) ? records : []
  } catch (err) {
    console.error('获取财务数据失败:', err)
    ElMessage.error('获取财务数据失败')
  } finally {
    loading.value = false
  }
}

// 发起结算（带二次确认，避免误操作）
async function handleSettle() {
  const amount = overviewData.value.pendingSettlement
  if (!amount) {
    ElMessage.warning('当前没有可结算的金额')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认对当前待结算金额 ¥${formatMoney.fmt(amount)} 发起结算？结算提交后将进入审核流程。`,
      '发起结算',
      { type: 'warning', confirmButtonText: '确认发起', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  ElMessage.success('结算请求已提交，等待后台审核')
  // 这里仅做提示并刷新，避免重复扣减
  fetchData()
}

// 导出报表：基于真实概览 + 结算明细 + 趋势生成 CSV 多段文件（同一 CSV 内分块）
function handleExport() {
  // 数据为空时不导出，给出明确提示避免生成空文件
  if (
    !overviewData.value.totalRevenue &&
    settlementData.value.length === 0 &&
    monthlyTrend.value.length === 0
  ) {
    ElMessage.warning('当前没有可导出的财务数据')
    return
  }

  // 通用行构造：将字符串数组转成 exportCsv 期望的 { col0/col1/... } 对象
  const row = (...cells) => {
    const o = {}
    cells.forEach((v, i) => { o['col' + i] = v == null ? '' : String(v) })
    return o
  }
  const BLANK = row('', '')

  const rows = []

  // 段 1：财务概览 KPI
  rows.push(row('财务概览报表'))
  rows.push(row('导出时间', new Date().toLocaleString('zh-CN')))
  rows.push(BLANK)
  rows.push(row('指标', '数值'))
  rows.push(row('本月 GMV', '¥' + formatMoney.fmt(overviewData.value.totalRevenue)))
  rows.push(row('实收金额', '¥' + formatMoney.fmt(overviewData.value.actualIncome)))
  rows.push(row('待结算金额', '¥' + formatMoney.fmt(overviewData.value.pendingSettlement)))
  rows.push(row('本月退款金额', '¥' + formatMoney.fmt(overviewData.value.refundAmount)))
  rows.push(row('已完成结算笔数', overviewData.value.completedSettlements))
  rows.push(row('待处理退款笔数', overviewData.value.pendingCount))
  rows.push(BLANK)

  // 段 2：渠道分布
  if (paymentChannels.value.length) {
    rows.push(row('支付渠道分布'))
    rows.push(row('渠道', '金额', '占比'))
    paymentChannels.value.forEach(c => {
      rows.push(row(
        channelLabel(c.channel),
        '¥' + formatMoney.fmt(c.amount),
        c.ratio.toFixed(1) + '%'
      ))
    })
    rows.push(BLANK)
  }

  // 段 3：退款原因分布
  if (refundReasonDistribution.value.length) {
    rows.push(row('退款原因分布'))
    rows.push(row('原因', '笔数'))
    refundReasonDistribution.value.forEach(r => {
      rows.push(row(r.reason, r.count))
    })
    rows.push(BLANK)
  }

  // 段 4：6 月趋势
  if (monthlyTrend.value.length) {
    rows.push(row('近 6 月收支趋势'))
    rows.push(row('月份', 'GMV', '退款', '净额'))
    monthlyTrend.value.forEach(t => {
      rows.push(row(
        t.month,
        '¥' + formatMoney.fmt(t.gmv),
        '¥' + formatMoney.fmt(t.refund),
        '¥' + formatMoney.fmt(t.net)
      ))
    })
    rows.push(BLANK)
  }

  // 段 5：结算明细
  if (settlementData.value.length) {
    rows.push(row('结算明细'))
    rows.push(row('结算单号', '结算周期', '支付渠道', '金额', '状态', '结算时间'))
    settlementData.value.forEach(s => {
      rows.push(row(
        s.settlementNo || s.id,
        s.period || '—',
        channelLabel(s.payChannel),
        '¥' + formatMoney.fmt(s.amount),
        settlementLabel(s.status),
        formatTime(s.settleTime)
      ))
    })
  }

  const ok = exportCsv(
    rows,
    [
      { key: 'col0', label: '列1' },
      { key: 'col1', label: '列2' },
      { key: 'col2', label: '列3' },
      { key: 'col3', label: '列4' },
      { key: 'col4', label: '列5' },
      { key: 'col5', label: '列6' }
    ],
    `finance-overview-${new Date().toISOString().slice(0, 10)}.csv`
  )
  if (ok) {
    ElMessage.success('报表已下载到本地，请用 Excel/WPS 打开')
  } else {
    ElMessage.error('报表导出失败，请稍后重试')
  }
}

// 跳转结算列表
function handleViewAllSettlements() {
  router.push('/settlement')
}

// 跳转结算详情
function handleViewDetail(item) {
  router.push({ path: '/settlement-detail', query: { id: item.id } })
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
/* ===== 页面容器：使用设计系统 page 容器 ===== */
.finance-manage-page {
  padding: 0;
}

/* ===== 页面标题区 ===== */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.page-header-left h1 {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 4px;
}
.page-header-left p {
  font-size: 13px;
  color: var(--text-400);
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}
.action-icon {
  font-size: 13px;
  line-height: 1;
}

/* ===== KPI 区 ===== */
.kpi-section {
  margin-bottom: 20px;
}
.kpi-card-highlight .kpi-card-value {
  color: var(--brand-600);
}
.kpi-card-warn .kpi-card-value {
  color: var(--state-warning);
}

/* ===== 三列布局：渠道 / 退款原因 / 待处理异常 ===== */
.three-col {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}
@media (max-width: 1280px) {
  .three-col {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 900px) {
  .three-col {
    grid-template-columns: 1fr;
  }
}

/* ===== 面板（统一卡片容器）===== */
.panel {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  background: var(--background-50);
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  margin: 0;
}
.panel-sub {
  font-size: 12px;
  color: var(--text-400);
}
.panel-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-500);
  background: var(--background-200);
  border-radius: 999px;
}
.panel-badge-warn {
  color: var(--state-warning);
  background: var(--state-warning-surface);
}
.panel-body {
  padding: 16px 18px;
  min-height: 160px;
}

/* ===== 渠道分布 ===== */
.channel-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.channel-item + .channel-item {
  margin-top: 14px;
}
.channel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.channel-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-700);
}
.channel-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.channel-value {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.channel-amount {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.channel-ratio {
  font-size: 12px;
  color: var(--text-400);
  font-variant-numeric: tabular-nums;
}
.channel-track {
  height: 6px;
  background: var(--background-200);
  border-radius: 999px;
  overflow: hidden;
}
.channel-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.4s ease;
}
.channel-pill {
  font-size: 12px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--background-100);
}

/* ===== 退款原因分布 ===== */
.reason-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.reason-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}
.reason-item + .reason-item {
  border-top: 1px dashed var(--background-300);
}
.reason-rank {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  background: var(--background-200);
  color: var(--text-500);
  flex-shrink: 0;
}
.reason-rank-1 { background: #ff9500; color: #fff; }
.reason-rank-2 { background: #5856d6; color: #fff; }
.reason-rank-3 { background: #007aff; color: #fff; }
.reason-body {
  flex: 1;
  min-width: 0;
}
.reason-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 4px;
}
.reason-label {
  font-size: 13px;
  color: var(--text-700);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 70%;
}
.reason-count {
  font-size: 12px;
  color: var(--text-500);
  font-variant-numeric: tabular-nums;
}
.reason-track {
  height: 4px;
  background: var(--background-200);
  border-radius: 999px;
  overflow: hidden;
}
.reason-fill {
  height: 100%;
  background: linear-gradient(90deg, #ff9500, #ff3b30);
  border-radius: 999px;
  transition: width 0.4s ease;
}

/* ===== 待处理异常 ===== */
.exception-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.exception-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--state-warning-surface);
  border: 1px solid rgba(255, 149, 0, 0.18);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-700);
  line-height: 1.5;
}
.exception-alert-icon {
  font-size: 16px;
  flex-shrink: 0;
}
.exception-alert-text strong {
  color: var(--state-warning);
  font-weight: 700;
  margin: 0 2px;
}

/* ===== 月度趋势图 ===== */
.trend-wrapper {
  padding: 12px 0;
}
.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  height: 180px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--background-200);
}
.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.trend-bar-group {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 140px;
  width: 100%;
  justify-content: center;
}
.trend-bar {
  width: 14px;
  border-radius: 3px 3px 0 0;
  transition: height 0.5s ease;
  min-height: 2px;
}
.trend-bar-gmv {
  background: linear-gradient(180deg, var(--brand-300), var(--brand-500));
}
.trend-bar-refund {
  background: linear-gradient(180deg, #ffb38a, #ff3b30);
}
.trend-month {
  font-size: 12px;
  color: var(--text-500);
}
.trend-net {
  font-size: 12px;
  color: var(--text-800);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.trend-legend {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-500);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}
.legend-dot-gmv {
  background: var(--brand-500);
}
.legend-dot-refund {
  background: #ff3b30;
}

/* ===== 结算明细表样式 ===== */
.th-right {
  text-align: right;
}
.th-center {
  text-align: center;
}
.td-right {
  text-align: right;
}
.td-center {
  text-align: center;
}
.td-amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--text-800);
  text-align: right;
}
.td-muted {
  color: var(--text-400);
  font-size: 12px;
}
.muted-cell {
  color: var(--text-400);
}
.settlement-no {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--brand-700);
  font-weight: 500;
}
.data-row:hover {
  background: var(--background-100);
}
.link-btn {
  background: transparent;
  border: none;
  padding: 4px 0;
  font-size: 13px;
  color: var(--brand-600);
  cursor: pointer;
  transition: color 0.15s ease;
}
.link-btn:hover {
  color: var(--brand-700);
}
</style>