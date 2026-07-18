<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-title-section">
      <h1 class="page-title">财务概览</h1>
      <p class="page-desc">实时掌握收入、结算与异常状态，确保资金安全流转</p>
    </div>

    <!-- KPI 卡片 4列 -->
    <section aria-label="财务概况" class="section-block">
      <div class="kpi-grid-4">
        <!-- 本月 GMV -->
        <div class="kpi-card-custom">
          <div class="kpi-card-header">
            <div class="kpi-icon-box">
              <span class="kpi-emoji">💰</span>
            </div>
            <span class="kpi-label">本月 GMV</span>
          </div>
          <div class="kpi-value">{{ overviewData.gmv }}</div>
          <div class="kpi-trend" :class="overviewData.gmvTrend && overviewData.gmvTrend.startsWith('+') ? 'kpi-trend-up' : 'kpi-trend-down'" v-if="overviewData.gmvTrend">
            <span class="trend-arrow">{{ overviewData.gmvTrend.startsWith('+') ? '↑' : '↓' }}</span>
            <span class="trend-value">{{ overviewData.gmvTrend }}</span>
            <span class="trend-label">vs 上月</span>
          </div>
        </div>
        <!-- 实收金额 -->
        <div class="kpi-card-custom">
          <div class="kpi-card-header">
            <div class="kpi-icon-box">
              <span class="kpi-emoji">👛</span>
            </div>
            <span class="kpi-label">实收金额</span>
          </div>
          <div class="kpi-value">{{ overviewData.received }}</div>
          <div class="kpi-trend">
            <span class="trend-label">扣除退款/优惠后</span>
          </div>
        </div>
        <!-- 待结算 -->
        <div class="kpi-card-custom">
          <div class="kpi-card-header">
            <div class="kpi-icon-box kpi-icon-brand">
              <span class="kpi-emoji">⏰</span>
            </div>
            <span class="kpi-label">待结算</span>
          </div>
          <div class="kpi-value kpi-value-brand">{{ overviewData.pendingSettlement }}</div>
          <div class="kpi-trend">
            <span class="trend-label">预计 T+3 到账</span>
          </div>
        </div>
        <!-- 退款金额 -->
        <div class="kpi-card-custom">
          <div class="kpi-card-header">
            <div class="kpi-icon-box kpi-icon-error">
              <span class="kpi-emoji">🔔</span>
            </div>
            <span class="kpi-label">退款金额</span>
          </div>
          <div class="kpi-value kpi-value-error">{{ overviewData.refundAmount }}</div>
          <div class="kpi-trend">
            <span class="trend-label">本月累计</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 中间区域 2列 -->
    <div class="two-col-section">
      <!-- 左列：支付渠道分布 -->
      <section aria-label="支付渠道分布">
        <div class="section-header">
          <h2 class="section-title">
            <span class="section-title-icon">📊</span>
            支付渠道分布
          </h2>
          <span class="section-subtitle">本月总 GMV {{ overviewData.gmv }}</span>
        </div>
        <div class="chart-card">
          <div class="bar-chart">
            <div class="bar-row-item" v-for="ch in paymentChannels" :key="ch.name">
              <div class="bar-header-row">
                <div class="bar-label-group">
                  <span class="bar-dot" :class="ch.dotClass || 'bar-dot-muted'"></span>
                  <span class="bar-name">{{ ch.name }}</span>
                </div>
                <div class="bar-value-group">
                  <span class="bar-amount">{{ ch.amount }}</span>
                  <span class="bar-percent">{{ ch.percent }}%</span>
                </div>
              </div>
              <div class="bar-track-bg">
                <div class="bar-fill-h" :class="ch.fillClass || 'bar-fill-muted'" :style="{ width: ch.percent + '%' }"></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 右列：待处理异常 -->
      <section aria-label="待处理异常">
        <div class="section-header">
          <h2 class="section-title">
            <span class="section-title-icon section-title-icon-error">⚠️</span>
            待处理异常
          </h2>
          <span class="section-badge-error">{{ exceptions.length }} 笔需处理</span>
        </div>
        <!-- 红色提示条 -->
        <div class="alert-banner-error" v-if="exceptions.length > 0">
          <span class="alert-banner-icon">❗</span>
          <span class="alert-banner-text">{{ exceptions.length }} 笔结算异常需处理，请尽快核实并操作</span>
        </div>
        <!-- 异常列表 -->
        <div class="exception-list">
          <div class="exception-card" v-for="exc in exceptions" :key="exc.id">
            <div class="exception-card-body">
              <div class="exception-info">
                <div class="exception-meta">
                  <span class="exception-badge">异常</span>
                  <span class="exception-source">{{ exc.source }}</span>
                </div>
                <p class="exception-desc">{{ exc.description }}</p>
                <div class="exception-amount">{{ exc.amount }}</div>
              </div>
              <button class="btn btn-primary exception-btn">处理</button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 结算明细表格 -->
    <section aria-label="结算明细">
      <div class="section-header">
        <h2 class="section-title">
          <span class="section-title-icon">📋</span>
          结算明细
        </h2>
        <button class="link-btn">查看全部 →</button>
      </div>
      <div class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th class="th-left">结算周期</th>
              <th class="th-left">支付渠道</th>
              <th class="th-right">结算金额</th>
              <th class="th-center">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr class="data-row" v-for="item in settlementData" :key="item.id">
              <td class="td-muted">{{ item.period }}</td>
              <td class="td-bold">{{ item.channel }}</td>
              <td class="td-amount">{{ item.amount }}</td>
              <td class="td-center">
                <span :class="statusClass(item.status)">{{ item.status }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 底部操作按钮 -->
    <section class="bottom-actions">
      <button class="btn btn-primary btn-lg" style="height:44px;padding:0 28px;font-size:14px;gap:8px;" @click="handleSettle">发起结算</button>
      <button class="btn btn-secondary" style="height:44px;padding:0 28px;font-size:14px;gap:8px;" @click="handleExport">导出报表</button>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFinanceOverview, getSettlements, getFinanceRecords } from '../api/admin'

// 财务概览KPI数据
const overviewData = ref({
  gmv: 0,
  gmvTrend: '',
  received: 0,
  pendingSettlement: 0,
  refundAmount: 0
})

// 支付渠道分布
const paymentChannels = ref([])

// 待处理异常
const exceptions = ref([])

// 结算明细
const settlementData = ref([])

// 加载状态
const loading = ref(false)

// 获取所有财务数据
async function fetchData() {
  loading.value = true
  try {
    // 获取财务概览
    const overviewRes = await getFinanceOverview()
    if (overviewRes) {
      overviewData.value = {
        gmv: overviewRes.gmv || 0,
        gmvTrend: overviewRes.gmvTrend || '',
        received: overviewRes.received || 0,
        pendingSettlement: overviewRes.pendingSettlement || 0,
        refundAmount: overviewRes.refundAmount || 0
      }
      paymentChannels.value = overviewRes.paymentChannels || []
      exceptions.value = overviewRes.exceptions || []
    }

    // 获取结算明细
    const settlementsRes = await getSettlements()
    if (settlementsRes) {
      settlementData.value = settlementsRes
    }
  } catch (err) {
    console.error('获取财务数据失败:', err)
    ElMessage.error('获取财务数据失败')
  } finally {
    loading.value = false
  }
}

function statusClass(status) {
  const map = {
    '已结算': 'status-tag status-tag-success',
    '结算中': 'status-tag status-tag-pending',
    '异常': 'status-tag status-tag-error'
  }
  return map[status] || 'status-tag'
}

function handleSettle() {
  ElMessage.success('结算请求已提交')
}

function handleExport() {
  ElMessage.success('报表导出任务已提交，请稍后在下载中心查看')
}

onMounted(() => {
  fetchData()
})
</script>
