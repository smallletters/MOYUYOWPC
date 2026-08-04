<template>
  <div class="page-wrapper">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>审计日志</h1>
      <p>追踪管理后台操作记录，保障操作可追溯、可审计</p>
    </div>

    <!-- 审计说明 -->
    <div class="alert-bar alert-info" style="margin-bottom:16px">
      <span class="alert-bar-icon">🛡️</span>
      <span class="alert-bar-text">审计日志默认保留 <strong>180 天</strong>，超期记录将被自动清理。如需长期归档，请及时导出。</span>
    </div>

    <!-- KPI 概览 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">📋</span>
          <span class="kpi-card-label">日志总数</span>
        </div>
        <div class="kpi-card-value">{{ tableData.length }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">当前结果集</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">➕</span>
          <span class="kpi-card-label">新增</span>
        </div>
        <div class="kpi-card-value">{{ countByType('新增') }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-up">新增操作</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">✏️</span>
          <span class="kpi-card-label">修改</span>
        </div>
        <div class="kpi-card-value">{{ countByType('修改') }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">修改操作</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🗑️</span>
          <span class="kpi-card-label">删除</span>
        </div>
        <div class="kpi-card-value">{{ countByType('删除') }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-down">删除操作</span></div>
      </div>
    </div>

    <!-- 查询面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>操作人</label>
          <input v-model="filters.operator" type="text" placeholder="操作人" @keyup.enter="handleSearch" />
        </div>
        <div class="form-group">
          <label>操作类型</label>
          <select v-model="filters.actionType" class="select-wrapper">
            <option value="">全部</option>
            <option value="新增">新增</option>
            <option value="修改">修改</option>
            <option value="删除">删除</option>
            <option value="查询">查询</option>
          </select>
        </div>
        <div class="form-group" style="min-width: 280px">
          <label>日期范围</label>
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" @click="handleSearch">搜索</button>
          <button class="btn btn-outline" @click="handleReset">重置</button>
          <button class="btn btn-outline" @click="handleExport">⤓ 导出日志</button>
        </div>
      </div>
    </div>

    <!-- 日志表格 -->
    <div class="data-table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th>操作人</th>
            <th>操作类型</th>
            <th>操作对象</th>
            <th>操作详情</th>
            <th>IP 地址</th>
            <th>操作时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pagedData" :key="row.id">
            <td>{{ row.operator }}</td>
            <td><span :class="actionPillClass(row.actionType)">{{ row.actionType }}</span></td>
            <td>{{ row.target }}</td>
            <td class="detail-cell" :title="row.detail">{{ row.detail }}</td>
            <td class="mono-cell">{{ row.ipAddress }}</td>
            <td class="time-cell">{{ row.createTime }}</td>
          </tr>
          <tr v-if="pagedData.length === 0">
            <td colspan="6">
              <div class="empty-state">
                <div class="empty-state-icon">📋</div>
                <div class="empty-state-text">暂无审计日志</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="pagination" v-if="tableData.length > pageSize">
        <span class="pagination-info">共 {{ tableData.length }} 条 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
        <div class="pagination-btns">
          <button class="pagination-btn" :disabled="currentPage <= 1" @click="currentPage--">‹ 上一页</button>
          <button class="pagination-btn" :disabled="currentPage >= totalPages" @click="currentPage++">下一页 ›</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuditLogList } from '../api/admin'
import { toArray } from '../utils/safeArray'
import { exportCsv } from '../utils/exportCsv'

const filters = reactive({
  operator: '',
  actionType: '',
  dateRange: null,
})

const tableData = ref([])

// 客户端分页
const pageSize = 12
const currentPage = ref(1)
const pagedData = computed(() => tableData.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))
const totalPages = computed(() => Math.max(1, Math.ceil(tableData.value.length / pageSize)))

function actionPillClass(type) {
  const map = { '新增': 'tag tag-green', '修改': 'tag tag-warning', '删除': 'tag tag-red', '查询': 'tag tag-blue' }
  return map[type] || 'tag tag-gray'
}

function countByType(type) {
  return tableData.value.filter(d => d.actionType === type).length
}

// 从API加载审计日志数据
async function loadData() {
  try {
    const res = await getAuditLogList()
    let list = toArray(res)
    // 前端筛选
    list = list.filter(item => {
      if (filters.operator && !(item.operator || '').includes(filters.operator)) return false
      if (filters.actionType && item.actionType !== filters.actionType) return false
      if (filters.dateRange && filters.dateRange.length === 2) {
        const t = (item.createTime || '').slice(0, 10)
        if (t < filters.dateRange[0] || t > filters.dateRange[1]) return false
      }
      return true
    })
    tableData.value = list
    currentPage.value = 1
  } catch (e) {
    console.error('加载审计日志失败:', e)
    ElMessage.error('加载审计日志失败')
  }
}
function handleSearch() { loadData() }
function handleReset() { filters.operator = ''; filters.actionType = ''; filters.dateRange = null; loadData() }
// 导出当前筛选结果到 CSV
function handleExport() {
  const ok = exportCsv(tableData.value, [
    { key: 'id', label: 'ID' },
    { key: 'operator', label: '操作人' },
    { key: 'actionType', label: '操作类型' },
    { key: 'target', label: '操作对象' },
    { key: 'detail', label: '操作详情' },
    { key: 'ipAddress', label: 'IP 地址' },
    { key: 'createTime', label: '操作时间' }
  ], `审计日志_${new Date().toISOString().slice(0, 10)}.csv`)
  if (ok) {
    ElMessage.success('审计日志已导出')
  } else {
    ElMessage.warning('暂无可导出的日志数据')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.detail-cell {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mono-cell {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-500);
}
.time-cell {
  font-size: 12px;
  color: var(--text-500);
  font-variant-numeric: tabular-nums;
}
</style>
