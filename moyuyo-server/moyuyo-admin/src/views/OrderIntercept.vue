<template>
  <div class="page-wrapper">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>订单拦截</h1>
      <p>拦截风险订单，人工复核后放行，保障交易安全</p>
    </div>

    <!-- KPI 概览 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">⚠️</span>
          <span class="kpi-card-label">待拦截</span>
        </div>
        <div class="kpi-card-value">{{ countByStatus('待拦截') }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-down">待处理</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🚫</span>
          <span class="kpi-card-label">已拦截</span>
        </div>
        <div class="kpi-card-value">{{ countByStatus('已拦截') }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">已冻结</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">✅</span>
          <span class="kpi-card-label">已放行</span>
        </div>
        <div class="kpi-card-value">{{ countByStatus('已放行') }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-up">已解除</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">📦</span>
          <span class="kpi-card-label">拦截总数</span>
        </div>
        <div class="kpi-card-value">{{ total }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">累计记录</span></div>
      </div>
    </div>

    <!-- 查询面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>订单编号</label>
          <input v-model="filters.keyword" type="text" placeholder="请输入订单编号" @keyup.enter="handleSearch" />
        </div>
        <div class="form-group">
          <label>拦截状态</label>
          <select v-model="filters.interceptStatus" class="select-wrapper">
            <option value="">全部</option>
            <option value="待拦截">待拦截</option>
            <option value="已拦截">已拦截</option>
            <option value="已放行">已放行</option>
          </select>
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" @click="handleSearch">搜索</button>
          <button class="btn btn-outline" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 拦截列表 -->
    <div class="data-table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th>订单编号</th>
            <th>商品</th>
            <th>金额</th>
            <th>当前状态</th>
            <th>拦截状态</th>
            <th>下单时间</th>
            <th style="min-width: 160px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in tableData" :key="row.id">
            <td class="mono-cell">{{ row.orderNo }}</td>
            <td>{{ row.product }}</td>
            <td class="money">¥{{ row.amount }}</td>
            <td>{{ row.currentStatus }}</td>
            <td><span :class="interceptPillClass(row.interceptStatus)">{{ row.interceptStatus }}</span></td>
            <td class="time-cell">{{ row.orderTime }}</td>
            <td>
              <div class="cell-actions">
                <button class="btn btn-sm btn-danger" @click="handleIntercept(row)" :disabled="row.interceptStatus !== '待拦截'">拦截</button>
                <button class="btn btn-sm btn-primary" @click="handleRelease(row)" :disabled="row.interceptStatus !== '已拦截'">放行</button>
              </div>
            </td>
          </tr>
          <tr v-if="tableData.length === 0">
            <td colspan="7">
              <div class="empty-state">
                <div class="empty-state-icon">📦</div>
                <div class="empty-state-text">暂无拦截订单</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="pagination" v-if="total > 0">
        <span class="pagination-info">共 {{ total }} 条 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { getInterceptList, createIntercept, releaseIntercept } from '../api/admin'

const currentPage = ref(1)
const pageSize = 10
const total = ref(0)
const allList = ref([])

const filters = reactive({
  keyword: '',
  interceptStatus: ''
})

const tableData = ref([])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function interceptPillClass(status) {
  const map = { '待拦截': 'tag tag-warning', '已拦截': 'tag tag-red', '已放行': 'tag tag-green' }
  return map[status] || 'tag tag-gray'
}

// KPI 统计（基于全量数据）
function countByStatus(status) {
  return allList.value.filter(d => d.interceptStatus === status).length
}

// 加载拦截订单列表（拉取全量用于 KPI + 客户端过滤分页）
async function loadData() {
  try {
    const res = await getInterceptList({ page: 1, size: 1000 })
    const list = (res && res.list) || []
    // 确保状态字段存在，后端可能返回英文状态
    allList.value = list.map(item => ({
      ...item,
      interceptStatus: ({'PENDING':'待拦截','INTERCEPTED':'已拦截','RELEASED':'已放行'})[item.interceptStatus] || item.interceptStatus || '待拦截'
    }))
    applyFilters()
  } catch (error) {
    console.error('获取拦截订单数据失败:', error)
    ElMessage.error('获取拦截订单数据失败')
  }
}

// 前端过滤 + 分页
function applyFilters() {
  let list = [...allList.value]
  if (filters.keyword) {
    const kw = filters.keyword.toLowerCase()
    list = list.filter(d => (d.orderNo || '').toLowerCase().includes(kw))
  }
  if (filters.interceptStatus) {
    list = list.filter(d => d.interceptStatus === filters.interceptStatus)
  }
  total.value = list.length
  const start = (currentPage.value - 1) * pageSize
  tableData.value = list.slice(start, start + pageSize)
}

function handleSearch() { currentPage.value = 1; applyFilters() }
function handleReset() { filters.keyword = ''; filters.interceptStatus = ''; handleSearch() }

// 执行订单拦截
async function handleIntercept(row) {
  try {
    await ElMessageBox.confirm('确认拦截订单 ' + row.orderNo + ' 吗？', '提示', { type: 'warning' })
    await createIntercept({ orderId: row.id, interceptType: 'MANUAL', reason: '', reasonTemplate: '' })
    ElMessage.success('已成功拦截')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('拦截订单失败:', error)
      ElMessage.error('拦截订单失败')
    }
  }
}

// 执行订单放行
async function handleRelease(row) {
  try {
    await ElMessageBox.confirm('确认放行订单 ' + row.orderNo + ' 吗？', '提示', { type: 'info' })
    await releaseIntercept(row.id, { releaseReason: '', releaseOperator: '' })
    ElMessage.success('已放行')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('放行订单失败:', error)
      ElMessage.error('放行订单失败')
    }
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.mono-cell {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-600);
}
.time-cell {
  font-size: 12px;
  color: var(--text-500);
  font-variant-numeric: tabular-nums;
}
</style>
