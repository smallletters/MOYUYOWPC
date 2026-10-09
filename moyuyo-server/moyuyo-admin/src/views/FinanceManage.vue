<template>
  <div class="finance-manage-page">
    <!-- 页面标题区 -->
    <div class="page-header">
      <div class="page-header-left">
        <h1>财务概览</h1>
        <p>实时掌握收入、结算、退款与异常状态，确保资金安全流转</p>
      </div>
      <div class="header-actions">
        <el-select v-model="exportRange" style="width: 130px">
          <el-option label="本月" value="current_month" />
          <el-option label="上月" value="last_month" />
          <el-option label="最近 7 天" value="last_7d" />
          <el-option label="最近 30 天" value="last_30d" />
          <el-option label="全部数据" value="all" />
        </el-select>
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

    <!-- P1：标签页切换（概览 / 交易流水） -->
    <div class="tabs-bar">
      <button
        v-for="t in tabs"
        :key="t.key"
        :class="['tab-btn', { active: activeTab === t.key }]"
        @click="switchTab(t.key)"
      >
        {{ t.label }}
        <span v-if="t.badge" class="tab-badge">{{ t.badge }}</span>
      </button>
    </div>

    <!-- ============ 概览 Tab ============ -->
    <div v-show="activeTab === 'overview'">
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

      <!-- 右：待处理异常 + 退款 KPI -->
      <section class="panel" aria-label="待处理异常">
        <div class="panel-header">
          <h2 class="panel-title">待处理异常</h2>
          <span class="panel-badge" :class="{ 'panel-badge-warn': pendingAlertCount > 0 }">
            {{ pendingAlertCount }} 笔待处理
          </span>
        </div>
        <div class="panel-body">
          <!-- 退款 KPI 小汇总 -->
          <div class="refund-kpi-mini">
            <div class="refund-kpi-item">
              <span class="refund-kpi-label">退款总额</span>
              <span class="refund-kpi-value">¥{{ formatMoney.fmt(refundKpi.totalAmount) }}</span>
            </div>
            <div class="refund-kpi-item">
              <span class="refund-kpi-label">退款笔数</span>
              <span class="refund-kpi-value">{{ refundKpi.totalCount }}</span>
            </div>
            <div class="refund-kpi-item">
              <span class="refund-kpi-label">已完成</span>
              <span class="refund-kpi-value refund-kpi-ok">{{ refundKpi.completedCount }}</span>
            </div>
          </div>
          <div v-if="alerts.length === 0" class="empty-state">
            <div class="empty-state-icon">✅</div>
            <div class="empty-state-text">当前无待处理异常</div>
          </div>
          <div v-else class="exception-list">
            <div
              v-for="a in alerts.slice(0, 6)"
              :key="a.id"
              class="alert-row"
              :class="{ 'alert-resolved': a.status === '已处理' }"
            >
              <span class="alert-type" :style="{ color: levelColor(a.level) }">
                <span class="alert-dot" :style="{ background: levelColor(a.level) }"></span>
                {{ a.type }}
              </span>
              <span class="alert-desc">{{ a.desc }}</span>
              <span :class="['alert-status', a.status === '待处理' ? 'alert-pending' : 'alert-resolved-tag']">
                {{ a.status }}
              </span>
            </div>
            <div class="exception-footer">
              <button class="link-btn" @click="handleViewAllSettlements">前往处理 →</button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- P0：渠道 Payout 汇总条 -->
    <section v-if="payoutChannels.length" class="panel" aria-label="Payout 渠道汇总">
      <div class="panel-header">
        <h2 class="panel-title">最近 Payout 汇总</h2>
        <span class="panel-sub">按支付渠道聚合（已 SETTLED）</span>
      </div>
      <div class="panel-body">
        <ul class="payout-list">
          <li v-for="p in payoutChannels" :key="p.channel" class="payout-item">
            <div class="payout-left">
              <span class="channel-dot" :style="{ background: channelColor(p.channel) }"></span>
              <div class="payout-meta">
                <div class="payout-channel">{{ channelLabel(p.channel) }}</div>
                <div class="payout-count">{{ p.count }} 笔 Payout · {{ p.note }}</div>
              </div>
            </div>
            <div class="payout-right">
              <span class="payout-amount">¥{{ formatMoney.fmt(p.amount) }}</span>
              <span :class="['payout-status', p.status === '已到账' ? 'payout-ok' : 'payout-pending']">
                {{ p.status }}
              </span>
            </div>
          </li>
        </ul>
      </div>
    </section>

    <!-- 第三段：近 6 月 GMV/退款/净额 趋势（折线 + 柱状组合 + 渠道堆叠） -->
    <section class="panel" aria-label="月度趋势">
      <div class="panel-header">
        <h2 class="panel-title">近 6 月收支趋势</h2>
        <span class="panel-sub">折线 · 柱状 · 渠道占比</span>
      </div>
      <div class="panel-body">
        <div v-if="monthlyTrend.length === 0" class="empty-state">
          <div class="empty-state-icon">📉</div>
          <div class="empty-state-text">暂无趋势数据</div>
        </div>
        <div v-else>
          <!-- 折线 + 柱状组合图（纯 SVG） -->
          <div class="trend-svg-wrapper">
            <svg :viewBox="`0 0 ${trendSvg.w} ${trendSvg.h}`" class="trend-svg" preserveAspectRatio="none">
              <!-- 网格横线 -->
              <g v-for="(g, gi) in trendSvg.gridY" :key="'g'+gi">
                <line :x1="trendSvg.padL" :x2="trendSvg.w - trendSvg.padR" :y1="g.y" :y2="g.y" stroke="var(--background-200)" stroke-width="1" />
                <text :x="trendSvg.padL - 6" :y="g.y + 4" text-anchor="end" font-size="10" fill="var(--text-400)">{{ g.label }}</text>
              </g>
              <!-- GMV 柱状（淡色背景柱） -->
              <g v-for="(item, idx) in monthlyTrend" :key="'b'+idx">
                <rect
                  :x="trendSvg.barX(idx) - trendSvg.barW / 2"
                  :y="trendSvg.barY(item.gmv)"
                  :width="trendSvg.barW"
                  :height="trendSvg.h - trendSvg.padB - trendSvg.barY(item.gmv)"
                  fill="var(--brand-100)"
                  rx="3"
                />
              </g>
              <!-- GMV 折线 -->
              <polyline
                :points="trendSvg.lineGmv"
                fill="none"
                stroke="var(--brand-500)"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <!-- 退款折线 -->
              <polyline
                :points="trendSvg.lineRefund"
                fill="none"
                stroke="#ff3b30"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-dasharray="4 3"
              />
              <!-- 净额折线（突出） -->
              <polyline
                :points="trendSvg.lineNet"
                fill="none"
                stroke="#07c160"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <!-- 节点圆点 + 净额标注 -->
              <g v-for="(item, idx) in monthlyTrend" :key="'p'+idx">
                <circle :cx="trendSvg.x(idx)" :cy="trendSvg.lineY(item.gmv)" r="3" fill="var(--brand-500)" />
                <circle :cx="trendSvg.x(idx)" :cy="trendSvg.lineY(item.refund)" r="3" fill="#ff3b30" />
                <circle :cx="trendSvg.x(idx)" :cy="trendSvg.lineY(item.net)" r="3.5" fill="#07c160" stroke="#fff" stroke-width="1.5" />
                <!-- 月份标签 -->
                <text :x="trendSvg.x(idx)" :y="trendSvg.h - 8" text-anchor="middle" font-size="11" fill="var(--text-500)">{{ formatMonth(item.month) }}</text>
                <!-- 净额值标签 -->
                <text :x="trendSvg.x(idx)" :y="trendSvg.lineY(item.net) - 10" text-anchor="middle" font-size="10" fill="#07c160" font-weight="600">¥{{ formatMoney.fmt(item.net) }}</text>
              </g>
            </svg>
          </div>
          <div class="trend-legend">
            <span class="legend-item"><span class="legend-dot legend-dot-gmv"></span>GMV（柱+线）</span>
            <span class="legend-item"><span class="legend-dot legend-dot-refund"></span>退款</span>
            <span class="legend-item"><span class="legend-dot legend-dot-net"></span>净额</span>
          </div>

          <!-- 渠道堆叠条：本月各渠道金额横向占比 -->
          <div v-if="paymentChannels.length" class="channel-stack">
            <div class="channel-stack-title">本月渠道金额分布</div>
            <div class="channel-stack-bar">
              <div
                v-for="c in paymentChannels"
                :key="c.channel"
                class="channel-stack-segment"
                :style="{ width: c.ratio + '%', background: channelColor(c.channel) }"
                :title="channelLabel(c.channel) + ' ¥' + formatMoney.fmt(c.amount) + ' (' + c.ratio.toFixed(1) + '%)'"
              >
                <span v-if="c.ratio > 8" class="channel-stack-text">{{ channelLabel(c.channel) }}</span>
              </div>
            </div>
            <div class="channel-stack-legend">
              <span v-for="c in paymentChannels" :key="'lg-'+c.channel" class="channel-legend-item">
                <span class="channel-legend-dot" :style="{ background: channelColor(c.channel) }"></span>
                {{ channelLabel(c.channel) }} ¥{{ formatMoney.fmt(c.amount) }}
              </span>
            </div>
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
    </div><!-- /overview-tab -->

    <!-- ============ 交易流水 Tab ============ -->
    <div v-show="activeTab === 'records'" class="records-tab">
      <section class="panel" aria-label="交易流水">
        <div class="panel-header">
          <h2 class="panel-title">交易流水</h2>
          <span class="panel-sub">基于 mo_finance_record 真实记录</span>
          <div class="panel-actions">
            <button class="btn btn-sm btn-outline" @click="exportRecords" :disabled="recordsList.length === 0">
              导出 CSV
            </button>
          </div>
        </div>
        <div class="records-filters">
          <div class="filter-item">
            <label>类型</label>
            <el-select v-model="recordsFilters.type" clearable placeholder="全部" style="width: 140px">
              <el-option label="支付 PAYMENT" value="PAYMENT" />
              <el-option label="退款 REFUND" value="REFUND" />
              <el-option label="结算 SETTLEMENT" value="SETTLEMENT" />
            </el-select>
          </div>
          <div class="filter-item">
            <label>起始日期</label>
            <el-date-picker
              v-model="recordsFilters.startDate"
              type="date"
              placeholder="开始"
              value-format="YYYY-MM-DD"
              style="width: 150px"
            />
          </div>
          <div class="filter-item">
            <label>结束日期</label>
            <el-date-picker
              v-model="recordsFilters.endDate"
              type="date"
              placeholder="结束"
              value-format="YYYY-MM-DD"
              style="width: 150px"
            />
          </div>
          <div class="filter-item">
            <button class="btn btn-sm btn-primary" @click="onRecordsSearch">查询</button>
            <button class="btn btn-sm btn-outline" @click="onRecordsReset">重置</button>
          </div>
        </div>
        <div class="data-table-wrapper">
          <table class="data-table">
            <thead>
              <tr>
                <th>流水号</th>
                <th>关联订单</th>
                <th>类型</th>
                <th>支付渠道</th>
                <th class="th-right">金额</th>
                <th class="th-center">状态</th>
                <th class="th-right">创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in recordsList" :key="safeId(r.id) + '-' + (r.createTime || '')" class="data-row">
                <td><span class="settlement-no">{{ safeId(r.id) }}</span></td>
                <td><span class="order-no">{{ r.orderNo || '—' }}</span></td>
                <td>
                  <span class="channel-pill" :style="{ color: recordTypeColor(r.type) }">
                    {{ recordTypeLabel(r.type) }}
                  </span>
                </td>
                <td>{{ channelLabel(r.channel) }}</td>
                <td class="td-amount">¥{{ formatMoney.fmt(r.amount) }}</td>
                <td class="td-center">
                  <el-tag :type="recordStatusType(r)" size="small" effect="light">
                    {{ recordStatusLabel(r) }}
                  </el-tag>
                </td>
                <td class="td-muted td-right">{{ formatTime(r.createTime) }}</td>
              </tr>
              <tr v-if="recordsList.length === 0">
                <td colspan="7">
                  <div class="empty-state">
                    <div class="empty-state-icon">📋</div>
                    <div class="empty-state-text">暂无交易流水</div>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div style="display:flex;justify-content:flex-end;padding:14px 0 0">
          <el-pagination
            v-model:current-page="recordsPage"
            v-model:page-size="recordsPageSize"
            :total="recordsTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadRecords"
            @size-change="loadRecords"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFinanceOverview, getSettlements, getPayoutChannels, getReconcileAlerts, getRefundKpi, getFinanceRecords } from '../api/admin'
import { exportCsv } from '../utils/exportCsv'
import { toArray } from '../utils/safeArray'

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

// P0：渠道 Payout 汇总（来自后端 /finance/payout-channels）
const payoutChannels = ref([])

// P0：对账异常告警（来自后端 /finance/reconcile-alerts）
const alerts = ref([])

// P0：退款 KPI（来自后端 /finance/refund-kpi）
const refundKpi = ref({ totalAmount: 0, totalCount: 0, pendingCount: 0, completedCount: 0 })

// 待处理告警条数（驱动 badge 高亮）
const pendingAlertCount = computed(() => alerts.value.filter(a => a.status === '待处理').length)

// ===== P2：导出范围筛选 =====
const exportRange = ref('current_month')

// 根据导出范围解析出 [startMs, endMs]（用于过滤月度数据 + 结算时间）
function resolveExportRange(range) {
  if (range === 'all') return null
  const now = new Date()
  const start = new Date(now)
  const end = new Date(now)
  if (range === 'current_month') {
    start.setDate(1)
    start.setHours(0, 0, 0, 0)
    end.setMonth(end.getMonth() + 1, 0)
    end.setHours(23, 59, 59, 999)
  } else if (range === 'last_month') {
    start.setMonth(start.getMonth() - 1, 1)
    start.setHours(0, 0, 0, 0)
    end.setDate(0)
    end.setHours(23, 59, 59, 999)
  } else if (range === 'last_7d') {
    start.setDate(now.getDate() - 6)
    start.setHours(0, 0, 0, 0)
    end.setHours(23, 59, 59, 999)
  } else if (range === 'last_30d') {
    start.setDate(now.getDate() - 29)
    start.setHours(0, 0, 0, 0)
    end.setHours(23, 59, 59, 999)
  }
  return [start.getTime(), end.getTime()]
}

// 把"yyyy-MM"字符串转换为该月月初/月末的 ms
function monthRangeMs(ym) {
  if (!ym || typeof ym !== 'string' || !/^\d{4}-\d{2}$/.test(ym)) return null
  const [y, m] = ym.split('-').map(Number)
  const start = new Date(y, m - 1, 1, 0, 0, 0, 0).getTime()
  const end = new Date(y, m, 0, 23, 59, 59, 999).getTime()
  return [start, end]
}

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
  // 兼容 "yyyy-MM-dd HH:mm:ss" / ISO / 时间戳：统一替换 T 为空格、截掉毫秒
  const str = String(value).replace('T', ' ').replace(/\..*$/, '')
  return str || '—'
}

// 把日期字符串解析为本地时区的毫秒数（用于日期范围筛选）
// 后端 LocalDateTime 不带时区信息；Safari 对无时区的 ISO 字符串会按 UTC 解析，会造成 1 天偏差
// 这里改为手工拆分年月日时分秒，绕过 Date 构造器的时区歧义
function parseLocalDateMs(value) {
  if (!value) return 0
  const s = String(value).replace(/\..*$/, '').replace('T', ' ').trim()
  // 期望形如 "YYYY-MM-DD HH:mm:ss"
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})/)
  if (!m) {
    // 兜底：返回原 Date 解析（按 ISO 语义）
    const t = new Date(s).getTime()
    return Number.isNaN(t) ? 0 : t
  }
  return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]).getTime()
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

// ===== P2-2：SVG 折线/柱状组合图所需的派生坐标 =====
// 所有值基于 viewBox 坐标绘制，外部容器通过 CSS 拉伸以适配宽度
const trendSvg = computed(() => {
  const data = monthlyTrend.value
  const w = 720
  const h = 220
  const padL = 56
  const padR = 20
  const padT = 24
  const padB = 36
  const innerW = w - padL - padR
  const innerH = h - padT - padB
  const n = data.length
  const maxVal = Math.max(
    1,
    ...data.flatMap(d => [Number(d.gmv || 0), Number(d.refund || 0), Number(d.net || 0)])
  )
  // 5 等分网格（0% / 25% / 50% / 75% / 100%）
  const gridY = [0, 0.25, 0.5, 0.75, 1].map(p => ({
    y: padT + innerH * (1 - p),
    label: '¥' + formatMoney.fmt(maxVal * p)
  }))
  // 节点 X 坐标：n 个节点等分 innerW（用括号显式包裹避免 + 与 <= 的优先级歧义）
  const x = (idx) => (n <= 1 ? padL + innerW / 2 : padL + (innerW * idx) / (n - 1))
  const lineY = (v) => padT + innerH * (1 - Math.min(1, Number(v || 0) / maxVal))
  // 柱状 X 中心 = lineX，柱宽 = innerW / n * 0.5
  const barW = n > 0 ? Math.max(8, (innerW / Math.max(n, 1)) * 0.5) : 0
  const barX = (idx) => (n <= 1 ? padL + innerW / 2 : padL + (innerW * idx) / Math.max(n - 1, 1))
  const barY = (v) => padT + innerH * (1 - Math.min(1, Number(v || 0) / maxVal))
  const buildLine = (key) => data.map((d, i) => `${x(i)},${lineY(d[key])}`).join(' ')
  return {
    w, h, padL, padR, padT, padB,
    gridY,
    x, lineY, barW, barX, barY,
    lineGmv: buildLine('gmv'),
    lineRefund: buildLine('refund'),
    lineNet: buildLine('net')
  }
})

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

// 告警级别 → 颜色令牌（与 SettlementManage 保持一致）
function levelColor(level) {
  if (level === 'error') return 'var(--state-error)'
  if (level === 'success') return 'var(--state-success)'
  return 'var(--state-warning)'
}

// ===== P1：标签页切换 =====
const activeTab = ref('overview')

// 流水类型映射（PAYMENT / REFUND / SETTLEMENT）
function recordTypeLabel(t) {
  return { PAYMENT: '支付', REFUND: '退款', SETTLEMENT: '结算' }[t] || t || '—'
}
function recordTypeColor(t) {
  return { PAYMENT: '#1677ff', REFUND: '#ff3b30', SETTLEMENT: '#07c160' }[t] || '#8e8e93'
}
function recordStatusLabel(record) {
  // 优先用后端 status 字段；缺失或未知则按 type 派生
  const s = record?.status
  if (s === 'SUCCESS' || s === 'success') return '已入账'
  if (s === 'FAILED' || s === 'failed') return '失败'
  if (s === 'PENDING' || s === 'pending') return '处理中'
  return record?.type === 'REFUND' ? '已退款' : '已入账'
}
function recordStatusType(record) {
  const t = record?.type
  const s = record?.status
  if (s === 'FAILED' || s === 'failed') return 'danger'
  if (t === 'REFUND') return 'warning'
  return 'success'
}

// 加载所有财务数据
async function fetchData() {
  loading.value = true
  try {
    // 财务概览 + 结算明细列表 + P0 三项（payout-channels / reconcile-alerts / refund-kpi）并发加载
    // 任一接口失败不影响其他模块渲染
    const [overviewRes, settlementsRes, payoutRes, alertsRes, refundKpiRes] = await Promise.all([
      getFinanceOverview().catch(e => { console.error('finance/overview 失败:', e); return null }),
      getSettlements({ page: 1, size: 10 }).catch(e => { console.error('finance/settlements 失败:', e); return null }),
      getPayoutChannels().catch(e => { console.error('finance/payout-channels 失败:', e); return null }),
      getReconcileAlerts().catch(e => { console.error('finance/reconcile-alerts 失败:', e); return null }),
      getRefundKpi().catch(e => { console.error('finance/refund-kpi 失败:', e); return null })
    ])

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

    const records = settlementsRes?.records || settlementsRes?.list || settlementsRes?.data || []
    settlementData.value = Array.isArray(records) ? records : []

    payoutChannels.value = Array.isArray(payoutRes) ? payoutRes : []
    alerts.value = Array.isArray(alertsRes) ? alertsRes : []
    refundKpi.value = refundKpiRes && typeof refundKpiRes === 'object'
      ? {
          totalAmount: Number(refundKpiRes.totalAmount || 0),
          totalCount: Number(refundKpiRes.totalCount || 0),
          pendingCount: Number(refundKpiRes.pendingCount || 0),
          completedCount: Number(refundKpiRes.completedCount || 0)
        }
      : { totalAmount: 0, totalCount: 0, pendingCount: 0, completedCount: 0 }
  } catch (err) {
    console.error('获取财务数据失败:', err)
    ElMessage.error('获取财务数据失败')
  } finally {
    loading.value = false
  }
}

// ===== P1：交易流水相关状态 =====
// 后端 /finance/records 仅支持 page/size 入参，类型/日期筛选在前端过滤（数据量不大）
const recordsList = ref([])
const recordsPage = ref(1)
const recordsPageSize = ref(20)
const recordsTotal = ref(0)
const recordsFilters = reactive({ type: '', startDate: '', endDate: '' })

// 切换 Tab 时按需懒加载流水，避免首屏多发一次请求
function switchTab(key) {
  activeTab.value = key
  if (key === 'records' && recordsList.value.length === 0) {
    loadRecords()
  }
}

// Tab 配置（含 badge 提示）
const tabs = computed(() => [
  { key: 'overview', label: '概览' },
  {
    key: 'records',
    label: '交易流水',
    badge: pendingAlertCount.value > 0 ? pendingAlertCount.value : null
  }
])

// 加载交易流水（服务端分页 + 前端二次过滤）
async function loadRecords() {
  try {
    const res = await getFinanceRecords({
      page: recordsPage.value,
      size: recordsPageSize.value
    })
    const all = toArray(res?.records != null ? res.records : res)
    // 前端过滤：类型 / 日期范围（覆盖 createTime）
    // 日期范围：起始 00:00:00，结束次日 00:00:00（半开区间，含结束当天）
    // 用手工构造函数避免 Safari 把无时区 ISO 字符串当 UTC 解析
    const startMs = recordsFilters.startDate
      ? new Date(+recordsFilters.startDate.slice(0,4), +recordsFilters.startDate.slice(5,7) - 1, +recordsFilters.startDate.slice(8,10), 0, 0, 0).getTime()
      : null
    const endMs = recordsFilters.endDate
      ? new Date(+recordsFilters.endDate.slice(0,4), +recordsFilters.endDate.slice(5,7) - 1, +recordsFilters.endDate.slice(8,10), 0, 0, 0).getTime() + 86400000
      : null
    let filtered = all
    if (recordsFilters.type) filtered = filtered.filter(r => r.type === recordsFilters.type)
    if (startMs || endMs) {
      filtered = filtered.filter(r => {
        const t = parseLocalDateMs(r.createTime)
        if (!t) return true
        if (startMs && t < startMs) return false
        if (endMs && t > endMs) return false
        return true
      })
    }
    recordsList.value = filtered
    // 筛选后总数 = 当前筛选结果数（仅限当前页筛选，前端无法统计全量）
    // 不显示后端 total，避免与表格实际行数不一致造成误导
    recordsTotal.value = filtered.length
  } catch (e) {
    console.error('加载交易流水失败:', e)
    ElMessage.error('加载交易流水失败')
    recordsList.value = []
    recordsTotal.value = 0
  }
}

function onRecordsSearch() {
  recordsPage.value = 1
  loadRecords()
}

function onRecordsReset() {
  recordsFilters.type = ''
  recordsFilters.startDate = ''
  recordsFilters.endDate = ''
  recordsPage.value = 1
  loadRecords()
}

// 把任何 ID 值（number / BigInt-as-string / number 字符串）转为字符串展示，避免 19 位 snowflake ID 在 JS 中丢失精度
function safeId(id) {
  if (id == null) return ''
  return String(id)
}

// 导出全量数据的硬上限页数，防止极端数据量下死循环
const HARD_PAGE_CAP = 50

// 导出当前筛选结果为 CSV（与流水表头对齐）
// 重要：当前分页只展示一页数据，导出必须按筛选条件从后端拉全量，避免误导出
async function exportRecords() {
  try {
    // 分批拉取直到覆盖后端 total（防止后端 total=0 时空转，加最多 50 页硬上限）
    const PAGE_SIZE = 200
    const HARD_CAP = HARD_PAGE_CAP
    let page = 1
    let allRows = []
    let total = null
    while (page <= HARD_CAP) {
      const res = await getFinanceRecords({ page, size: PAGE_SIZE })
      const records = toArray(res?.records != null ? res.records : res)
      if (total == null) total = res?.total != null ? Number(res.total) : null
      if (!records.length) break
      allRows = allRows.concat(records)
      // total 已知且已拉完则提前结束
      if (total != null && allRows.length >= total) break
      // 防御：本页不足一页说明已到末页
      if (records.length < PAGE_SIZE) break
      page++
    }

    // 应用与当前页面相同的筛选条件
    const startMs = recordsFilters.startDate
      ? new Date(+recordsFilters.startDate.slice(0,4), +recordsFilters.startDate.slice(5,7) - 1, +recordsFilters.startDate.slice(8,10), 0, 0, 0).getTime()
      : null
    const endMs = recordsFilters.endDate
      ? new Date(+recordsFilters.endDate.slice(0,4), +recordsFilters.endDate.slice(5,7) - 1, +recordsFilters.endDate.slice(8,10), 0, 0, 0).getTime() + 86400000
      : null
    let filtered = allRows
    if (recordsFilters.type) filtered = filtered.filter(r => r.type === recordsFilters.type)
    if (startMs || endMs) {
      filtered = filtered.filter(r => {
        const t = parseLocalDateMs(r.createTime)
        if (!t) return true
        if (startMs && t < startMs) return false
        if (endMs && t > endMs) return false
        return true
      })
    }

    if (filtered.length === 0) {
      ElMessage.warning('当前筛选条件下没有可导出的流水')
      return
    }

    // 数据量超过硬上限（10000 条）时，明确告知用户已被截断
    const HARD_LIMIT = HARD_PAGE_CAP * 200
    const truncated = allRows.length >= HARD_LIMIT
    if (truncated) {
      ElMessage.warning(`流水数据已超过 ${HARD_LIMIT} 条上限，本次仅导出已加载的 ${filtered.length} 条，请缩小筛选范围后再导出全部`)
    }

    const rows = filtered.map(r => ({
      col0: safeId(r.id),
      col1: r.orderNo || '—',
      col2: recordTypeLabel(r.type),
      col3: channelLabel(r.channel),
      col4: '¥' + formatMoney.fmt(r.amount),
      col5: recordStatusLabel(r),
      col6: formatTime(r.createTime)
    }))
    const ok = exportCsv(rows, [
      { key: 'col0', label: '流水号' },
      { key: 'col1', label: '关联订单' },
      { key: 'col2', label: '类型' },
      { key: 'col3', label: '支付渠道' },
      { key: 'col4', label: '金额' },
      { key: 'col5', label: '状态' },
      { key: 'col6', label: '创建时间' }
    ], `finance-records-${new Date().toISOString().slice(0, 10)}.csv`)
    if (ok) ElMessage.success(`已导出 ${filtered.length} 条流水，请用 Excel/WPS 打开`)
    else ElMessage.error('导出失败，请稍后重试')
  } catch (e) {
    console.error('导出流水失败:', e)
    ElMessage.error('导出失败，请稍后重试')
  }
}

// 发起结算（带二次确认，避免误操作）
// 注意：当前后端没有真实"发起结算"接口，仅做前端提示，因此不刷新数据，避免清空已加载的结算明细/异常告警列表
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
  // 不调用 fetchData()：后端没真正发起，刷新数据无意义且会清空用户当前视图状态
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

  // 解析导出范围（用于过滤月度趋势 + 结算明细时间）
  const range = resolveExportRange(exportRange.value)
  const rangeLabel = { current_month: '本月', last_month: '上月', last_7d: '最近 7 天', last_30d: '最近 30 天', all: '全部' }[exportRange.value] || '本月'

  const rows = []

  // 段 1：财务概览 KPI
  rows.push(row('财务概览报表（' + rangeLabel + '）'))
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

  // 段 4：6 月趋势（按日期范围过滤月份）
  const trendFiltered = monthlyTrend.value.filter(t => {
    if (!range) return true
    const ms = monthRangeMs(t.month)
    if (!ms) return true
    const [s, e] = range
    return ms[1] >= s && ms[0] <= e
  })
  if (trendFiltered.length) {
    rows.push(row('月度收支趋势'))
    rows.push(row('月份', 'GMV', '退款', '净额'))
    trendFiltered.forEach(t => {
      rows.push(row(
        t.month,
        '¥' + formatMoney.fmt(t.gmv),
        '¥' + formatMoney.fmt(t.refund),
        '¥' + formatMoney.fmt(t.net)
      ))
    })
    rows.push(BLANK)
  }

  // 段 5：结算明细（按日期范围过滤结算时间）
  const settleFiltered = settlementData.value.filter(s => {
    if (!range) return true
    const t = parseLocalDateMs(s.settleTime)
    if (!t) return true
    // 注意：形参 s 已占用变量名，这里用 startMs / endMs 避免遮蔽冲突
    const [startMs, endMs] = range
    return t >= startMs && t <= endMs
  })
  if (settleFiltered.length) {
    rows.push(row('结算明细'))
    rows.push(row('结算单号', '结算周期', '支付渠道', '金额', '状态', '结算时间'))
    settleFiltered.forEach(s => {
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
    `finance-overview-${exportRange.value}-${new Date().toISOString().slice(0, 10)}.csv`
  )
  if (ok) {
    ElMessage.success(`报表（${rangeLabel}）已下载到本地，请用 Excel/WPS 打开`)
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
  gap: 8px;
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
.exception-footer {
  padding-top: 4px;
  text-align: right;
}

/* ===== 退款 KPI 小汇总 ===== */
.refund-kpi-mini {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
  padding: 10px 0 14px;
  border-bottom: 1px dashed var(--background-300);
  margin-bottom: 10px;
}
.refund-kpi-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.refund-kpi-label { font-size: 11px; color: var(--text-400); }
.refund-kpi-value {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.refund-kpi-ok { color: var(--state-success); }

/* ===== 异常告警行 ===== */
.alert-row {
  display: grid;
  grid-template-columns: 78px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--background-200);
  font-size: 12px;
}
.alert-row:last-of-type { border-bottom: none; }
.alert-row.alert-resolved { opacity: 0.7; }
.alert-type {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 12px;
}
.alert-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.alert-desc {
  color: var(--text-600);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
}
.alert-status {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 4px;
  font-weight: 600;
  white-space: nowrap;
}
.alert-pending { background: var(--background-200); color: var(--text-500); }
.alert-resolved-tag { background: var(--state-success-surface); color: var(--state-success); }

/* ===== Payout 渠道汇总 ===== */
.payout-list { list-style: none; margin: 0; padding: 0; }
.payout-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--background-200);
}
.payout-item:last-child { border-bottom: none; }
.payout-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.payout-meta { display: flex; flex-direction: column; gap: 2px; }
.payout-channel {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-800);
}
.payout-count {
  font-size: 11px;
  color: var(--text-400);
}
.payout-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.payout-amount {
  font-size: 15px;
  font-weight: 700;
  color: var(--brand-600);
  font-variant-numeric: tabular-nums;
}
.payout-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}
.payout-ok { background: var(--state-success-surface); color: var(--state-success); }
.payout-pending { background: var(--background-200); color: var(--text-500); }

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

/* ===== P2-2：折线 + 柱状 SVG 组合图 ===== */
.trend-svg-wrapper {
  width: 100%;
  overflow-x: auto;
}
.trend-svg {
  width: 100%;
  min-width: 480px;
  height: 240px;
  display: block;
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
.legend-dot-net { background: #07c160; }

/* ===== 渠道堆叠条 ===== */
.channel-stack {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed var(--background-300);
}
.channel-stack-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
  margin-bottom: 8px;
}
.channel-stack-bar {
  display: flex;
  width: 100%;
  height: 26px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--background-100);
}
.channel-stack-segment {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: opacity 0.15s ease;
}
.channel-stack-segment:hover { opacity: 0.85; }
.channel-stack-text {
  padding: 0 6px;
  text-shadow: 0 1px 1px rgba(0,0,0,0.2);
}
.channel-stack-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 18px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-500);
}
.channel-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}
.channel-legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  display: inline-block;
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

/* ===== Tabs（概览/记录） ===== */
.tabs-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}
.tab-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: none;
  background: transparent;
  color: var(--text-500);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s ease;
}
.tab-btn:hover { color: var(--text-700); }
.tab-btn.active {
  color: var(--brand-600);
  font-weight: 600;
}
.tab-btn.active::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: -1px;
  height: 2px;
  background: var(--brand-500);
  border-radius: 2px;
}
.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--state-error);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

/* ===== Panel actions ===== */
.panel-header { gap: 12px; }
.panel-actions { margin-left: auto; }

/* ===== 交易流水 ===== */
.records-tab { padding-top: 0; }
.records-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding: 0 0 14px;
  border-bottom: 1px dashed var(--background-300);
  margin-bottom: 14px;
}
.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.filter-item label {
  font-size: 12px;
  color: var(--text-500);
  white-space: nowrap;
}
.order-no {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--text-700);
}
</style>