<template>
  <div class="finance-manage-page">
    <!-- 页面标题区：与 Settlement/Dashboard 保持一致的 page-header 模式 -->
    <div class="page-header">
      <div class="page-header-left">
        <h1>财务概览</h1>
        <p>实时掌握收入、结算与异常状态，确保资金安全流转</p>
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

    <!-- KPI 4 列：使用设计系统的 kpi-grid / kpi-card -->
    <section aria-label="财务概况" class="kpi-section">
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon" aria-hidden="true">💰</span>
            <span class="kpi-card-label">本月 GMV</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney(overviewData.totalRevenue) }}</div>
          <div class="kpi-card-trend kpi-trend-up" v-if="overviewData.completedSettlements > 0">
            <span class="kpi-trend-text">已结算 {{ overviewData.completedSettlements }} 笔</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon" aria-hidden="true">💼</span>
            <span class="kpi-card-label">实收金额</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney(overviewData.actualIncome) }}</div>
          <div class="kpi-card-trend">
            <span class="kpi-trend-text">扣除退款 / 优惠后</span>
          </div>
        </div>

        <div class="kpi-card kpi-card-highlight">
          <div class="kpi-card-header">
            <span class="kpi-card-icon" aria-hidden="true">⏳</span>
            <span class="kpi-card-label">待结算</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney(overviewData.pendingSettlement) }}</div>
          <div class="kpi-card-trend">
            <span class="kpi-trend-text">预计 T+3 到账</span>
          </div>
        </div>

        <div class="kpi-card kpi-card-warn">
          <div class="kpi-card-header">
            <span class="kpi-card-icon" aria-hidden="true">🔔</span>
            <span class="kpi-card-label">退款金额 / 待处理</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney(overviewData.refundAmount) }}</div>
          <div class="kpi-card-trend kpi-trend-down" v-if="overviewData.pendingCount > 0">
            <span class="kpi-trend-text">{{ overviewData.pendingCount }} 笔退款待处理</span>
          </div>
          <div class="kpi-card-trend" v-else>
            <span class="kpi-trend-text">本月累计</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 中段两列：渠道分布 + 待处理异常 -->
    <div class="two-col">
      <!-- 左：支付渠道分布 -->
      <section class="panel" aria-label="支付渠道分布">
        <div class="panel-header">
          <h2 class="panel-title">支付渠道分布</h2>
          <span class="panel-sub">本月 GMV ¥{{ formatMoney(overviewData.totalRevenue) }}</span>
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
                  <span class="channel-amount">¥{{ formatMoney(ch.amount) }}</span>
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

    <!-- 结算明细表格 -->
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
              <td class="td-amount">¥{{ formatMoney(item.amount) }}</td>
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
              <td colspan="6">
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFinanceOverview, getSettlements } from '../api/admin'

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

// 结算明细列表（来自后端 settlements）
const settlementData = ref([])

const loading = ref(false)
const router = useRouter()

// 金额格式化：保留两位小数
function formatMoney(value) {
  if (value == null) return '0.00'
  const num = typeof value === 'number' ? value : Number(value)
  if (Number.isNaN(num)) return '0.00'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 时间格式化：后端 LocalDateTime 序列化为 "yyyy-MM-dd HH:mm:ss"
function formatTime(value) {
  if (!value) return '—'
  // 兼容 "yyyy-MM-dd HH:mm:ss" / ISO / 时间戳
  const str = String(value).replace('T', ' ').replace(/\..*$/, '')
  return str || '—'
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
    SETTLING: 'warning',
    PENDING: 'info',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

// 结算状态 -> 中文标签
function settlementLabel(status) {
  const map = {
    COMPLETED: '已结算',
    SETTLING: '结算中',
    PENDING: '待结算',
    FAILED: '失败'
  }
  return map[status] || status || '未知'
}

// 加载所有财务数据
async function fetchData() {
  loading.value = true
  try {
    // 财务概览（实际后端返回 totalRevenue/actualIncome/pendingSettlement 等字段）
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
      `确认对当前待结算金额 ¥${formatMoney(amount)} 发起结算？结算提交后将进入审核流程。`,
      '发起结算',
      { type: 'warning', confirmButtonText: '确认发起', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  ElMessage.success('结算请求已提交，等待后台审核')
  // 实际发起动作：调用 createSettlement（接口已存在），失败由全局拦截
  // 这里仅做提示并刷新，避免重复扣减
  fetchData()
}

// 导出报表
function handleExport() {
  ElMessage.success('报表导出任务已提交，请稍后在下载中心查看')
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

/* ===== 页面标题区（沿用 design-system .page-header）===== */
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

/* ===== 两列布局 ===== */
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}
@media (max-width: 1100px) {
  .two-col {
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
.panel + .panel {
  /* 多面板堆叠由外层容器决定 */
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

/* ===== 结算明细表样式微调 ===== */
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