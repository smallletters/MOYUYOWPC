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
          <div class="kpi-value">$156,800</div>
          <div class="kpi-trend kpi-trend-up">
            <span class="trend-arrow">↑</span>
            <span class="trend-value">+12.5%</span>
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
          <div class="kpi-value">$148,200</div>
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
          <div class="kpi-value kpi-value-brand">$8,600</div>
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
          <div class="kpi-value kpi-value-error">$5,230</div>
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
          <span class="section-subtitle">本月总 GMV $156,800</span>
        </div>
        <div class="chart-card">
          <div class="bar-chart">
            <!-- Stripe 62% -->
            <div class="bar-row-item">
              <div class="bar-header-row">
                <div class="bar-label-group">
                  <span class="bar-dot bar-dot-primary"></span>
                  <span class="bar-name">Stripe</span>
                </div>
                <div class="bar-value-group">
                  <span class="bar-amount">$97,100</span>
                  <span class="bar-percent">62%</span>
                </div>
              </div>
              <div class="bar-track-bg">
                <div class="bar-fill-h bar-fill-primary" style="width: 62%;"></div>
              </div>
            </div>
            <!-- Apple Pay 23% -->
            <div class="bar-row-item">
              <div class="bar-header-row">
                <div class="bar-label-group">
                  <span class="bar-dot bar-dot-chart3"></span>
                  <span class="bar-name">Apple Pay</span>
                </div>
                <div class="bar-value-group">
                  <span class="bar-amount">$36,100</span>
                  <span class="bar-percent">23%</span>
                </div>
              </div>
              <div class="bar-track-bg">
                <div class="bar-fill-h bar-fill-chart3" style="width: 23%;"></div>
              </div>
            </div>
            <!-- PayPal 12% -->
            <div class="bar-row-item">
              <div class="bar-header-row">
                <div class="bar-label-group">
                  <span class="bar-dot bar-dot-chart4"></span>
                  <span class="bar-name">PayPal</span>
                </div>
                <div class="bar-value-group">
                  <span class="bar-amount">$18,800</span>
                  <span class="bar-percent">12%</span>
                </div>
              </div>
              <div class="bar-track-bg">
                <div class="bar-fill-h bar-fill-chart4" style="width: 12%;"></div>
              </div>
            </div>
            <!-- 其他 3% -->
            <div class="bar-row-item">
              <div class="bar-header-row">
                <div class="bar-label-group">
                  <span class="bar-dot bar-dot-muted"></span>
                  <span class="bar-name">其他</span>
                </div>
                <div class="bar-value-group">
                  <span class="bar-amount">$4,700</span>
                  <span class="bar-percent">3%</span>
                </div>
              </div>
              <div class="bar-track-bg">
                <div class="bar-fill-h bar-fill-muted" style="width: 3%;"></div>
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
          <span class="section-badge-error">2 笔需处理</span>
        </div>
        <!-- 红色提示条 -->
        <div class="alert-banner-error">
          <span class="alert-banner-icon">❗</span>
          <span class="alert-banner-text">2 笔结算异常需处理，请尽快核实并操作</span>
        </div>
        <!-- 异常列表 -->
        <div class="exception-list">
          <!-- 异常 1 -->
          <div class="exception-card">
            <div class="exception-card-body">
              <div class="exception-info">
                <div class="exception-meta">
                  <span class="exception-badge">异常</span>
                  <span class="exception-source">PayPal / 07.01-07.07</span>
                </div>
                <p class="exception-desc">结算失败：银行账户信息已过期，需更新收款账户后重新发起结算。</p>
                <div class="exception-amount">$3,760</div>
              </div>
              <button class="btn btn-primary exception-btn">处理</button>
            </div>
          </div>
          <!-- 异常 2 -->
          <div class="exception-card">
            <div class="exception-card-body">
              <div class="exception-info">
                <div class="exception-meta">
                  <span class="exception-badge">异常</span>
                  <span class="exception-source">Stripe / 06.18-06.24</span>
                </div>
                <p class="exception-desc">退款争议：买家发起 chargeback，争议金额暂冻结，需提交举证材料。</p>
                <div class="exception-amount">$1,470</div>
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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const settlementData = ref([
  { id: 1, period: '2026/06/25 - 2026/06/30', channel: 'Stripe', amount: '$24,350', status: '已结算' },
  { id: 2, period: '2026/06/25 - 2026/06/30', channel: 'Apple Pay', amount: '$8,920', status: '已结算' },
  { id: 3, period: '2026/07/01 - 2026/07/07', channel: 'Stripe', amount: '$18,640', status: '结算中' },
  { id: 4, period: '2026/07/01 - 2026/07/07', channel: 'PayPal', amount: '$3,760', status: '异常' },
  { id: 5, period: '2026/07/01 - 2026/07/07', channel: 'Apple Pay', amount: '$6,980', status: '结算中' }
])

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
</script>
