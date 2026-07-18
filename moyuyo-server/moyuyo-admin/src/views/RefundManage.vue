<template>
  <div class="refund-manage">
    <h2 class="page-title">退款管理</h2>

    <!-- KPI 卡片 -->
    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-value orange">{{ kpiData.pending }}</div>
        <div class="kpi-label">待处理</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ kpiData.todayAmount }}</div>
        <div class="kpi-label">今日退款金额</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ kpiData.refundRate }}</div>
        <div class="kpi-label">退款率</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ kpiData.avgProcessTime }}</div>
        <div class="kpi-label">平均处理时长</div>
      </div>
    </div>

    <!-- 类型切换 -->
    <div class="tab-switcher">
      <button
        v-for="type in refundTypes"
        :key="type.key"
        class="tab-switcher-item"
        :class="{ active: activeType === type.key }"
        @click="activeType = type.key"
      >
        {{ type.label }}
      </button>
    </div>

    <!-- 退款卡片列表 -->
    <div class="refund-list">
      <div class="refund-card" v-for="item in refunds" :key="item.id">
        <div class="refund-top">
          <div class="refund-info">
            <span class="refund-no table-link">{{ item.refundNo }}</span>
            <span class="refund-order">订单 {{ item.orderNo }}</span>
          </div>
          <div class="refund-sla" :class="item.slaClass">
            <span class="sla-icon">{{ item.slaIcon }}</span>
            {{ item.slaLabel }}
          </div>
        </div>
        <div class="refund-body">
          <div class="refund-product">
            <div class="refund-thumb">{{ item.thumb }}</div>
            <div class="refund-reason">
              <span class="tag" :class="item.reasonClass">{{ item.reasonLabel }}</span>
            </div>
          </div>
          <div class="refund-amount">
            <span class="amount-label">退款金额</span>
            <span class="amount-value">¥{{ item.amount }}</span>
          </div>
          <div class="refund-status">
            <span class="tag" :class="item.statusClass">{{ item.statusLabel }}</span>
          </div>
        </div>
        <div class="refund-actions" v-if="item.statusLabel === '待处理'">
          <button class="btn btn-sm btn-primary" @click="handleApprove(item.id)">同意</button>
          <button class="btn btn-sm btn-outline" @click="handleReject(item.id)">拒绝</button>
          <button class="btn btn-sm btn-outline">详情</button>
        </div>
      </div>
    </div>

    <!-- 批量操作栏 -->
    <div class="batch-bar">
      <label>
        <input type="checkbox" v-model="selectAll" @change="toggleSelectAll" />
        全选
      </label>
      <span>已选 {{ selectedCount }} 项</span>
      <button class="btn btn-sm btn-primary" :disabled="selectedCount === 0" @click="batchApprove">批量同意</button>
    </div>

    <!-- 退款原因分布 -->
    <div class="card">
      <div class="card-header">
        <h3>退款原因分布</h3>
      </div>
      <div class="card-body">
        <div class="reason-dist">
          <div class="reason-bar-item" v-for="r in reasonDist" :key="r.label">
            <div class="reason-bar-header">
              <span>{{ r.label }}</span>
              <span>{{ r.percent }}%</span>
            </div>
            <div class="bar-track">
              <div class="bar-fill-rd" :style="{ width: r.percent + '%', background: r.color }"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import api from '../api/index'
import { ElMessage } from 'element-plus'

const activeType = ref('all')
const selectAll = ref(false)
const selectedItems = ref(new Set())
const loading = ref(false)

const refundTypes = [
  { key: 'all', label: '全部' },
  { key: 'refund_only', label: '仅退款' },
  { key: 'refund_return', label: '退货退款' },
  { key: 'exchange', label: '换货' }
]

const refunds = ref([])

// KPI 数据
const kpiData = ref({
  pending: 0,
  todayAmount: '¥0',
  refundRate: '0%',
  avgProcessTime: '0h'
})

// 退款原因分布
const reasonDist = ref([])

// 获取退款统计数据
async function fetchStats() {
  try {
    const res = await api.get('/refunds/stats')
    if (res) {
      kpiData.value = res
    }
  } catch (err) {
    console.error('获取退款统计数据失败:', err)
  }
}

// 获取退款列表
async function fetchRefunds() {
  loading.value = true
  try {
    const params = { type: activeType.value }
    const res = await api.get('/refunds/list', { params })
    if (res) {
      refunds.value = res.records || res.list || res
    }
  } catch (err) {
    console.error('获取退款列表失败:', err)
    ElMessage.error('获取退款列表失败')
  } finally {
    loading.value = false
  }
}

// 获取退款原因分布
async function fetchReasonDist() {
  try {
    const res = await api.get('/refunds/reason-distribution')
    if (res) {
      reasonDist.value = res
    }
  } catch (err) {
    console.error('获取退款原因分布失败:', err)
  }
}

const selectedCount = computed(() => selectedItems.value.size)

function toggleSelectAll() {
  if (selectAll.value) {
    refunds.value.forEach(item => selectedItems.value.add(item.id))
  } else {
    selectedItems.value.clear()
  }
}

function handleApprove(id) {
  ElMessage.success(`退款 #${id} 已批准`)
}

function handleReject(id) {
  ElMessage.success(`退款 #${id} 已拒绝`)
}

function batchApprove() {
  ElMessage.success(`批量同意 ${selectedCount.value} 项退款`)
}

onMounted(() => {
  fetchStats()
  fetchRefunds()
  fetchReasonDist()
})
</script>

<style scoped lang="css">
.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 20px;
}

/* KPI */
.kpi-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.kpi-card {
  flex: 1;
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 4px;
}

.kpi-value.orange { color: #e67e22; }

.kpi-label {
  font-size: 13px;
  color: var(--text-400);
}

/* 退款卡片列表 */
.refund-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.refund-card {
  padding: 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.refund-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.refund-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.refund-no {
  font-weight: 600;
  color: var(--primary);
}

.refund-order {
  font-size: 12px;
  color: var(--text-400);
}

.refund-sla {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 999px;
}

.refund-sla.urgent {
  background: var(--state-error-surface);
  color: var(--state-error);
}

.refund-sla.normal {
  background: var(--state-warning-surface);
  color: var(--state-warning);
}

.refund-sla.done {
  background: var(--state-success-surface);
  color: var(--state-success);
}

.refund-body {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 12px;
}

.refund-product {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.refund-thumb {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: var(--background-200);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.refund-amount {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  min-width: 80px;
}

.amount-label {
  font-size: 11px;
  color: var(--text-400);
}

.amount-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--state-error);
}

.refund-actions {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--background-100);
}

/* 原因分布 */
.reason-dist {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reason-bar-header {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-600);
  margin-bottom: 4px;
}

.bar-track {
  height: 8px;
  border-radius: 4px;
  background: var(--background-100);
  overflow: hidden;
}

.bar-fill-rd {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}
</style>
