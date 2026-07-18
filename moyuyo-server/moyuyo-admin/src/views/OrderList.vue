<template>
  <div class="order-list-page">
    <div class="page-header">
      <h2 class="page-title">订单列表</h2>
      <div class="status-summary">
        <span class="summary-item">待发货 <strong>{{ statsData.pendingShip }}</strong></span>
        <span class="summary-divider">|</span>
        <span class="summary-item">售后中 <strong>{{ statsData.afterSale }}</strong></span>
        <span class="summary-divider">|</span>
        <span class="summary-item" :class="{ 'summary-item--warn': statsData.abnormal > 0 }">异常 <strong>{{ statsData.abnormal }}</strong></span>
      </div>
    </div>

    <!-- 查询面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>订单状态</label>
          <select v-model="filters.status" class="form-input">
            <option value="">全部状态</option>
            <option value="pending">待付款</option>
            <option value="shipping">待发货</option>
            <option value="shipped">已发货</option>
            <option value="completed">已完成</option>
            <option value="canceled">已取消</option>
          </select>
        </div>
        <div class="form-group">
          <label>开始日期</label>
          <input v-model="filters.dateStart" type="date" class="form-input" />
        </div>
        <div class="form-group">
          <label>结束日期</label>
          <input v-model="filters.dateEnd" type="date" class="form-input" />
        </div>
        <div class="form-group">
          <label>搜索</label>
          <input v-model="filters.search" type="text" class="form-input" placeholder="订单号/用户名" />
        </div>
        <div class="form-group form-group--action">
          <label>&nbsp;</label>
          <button class="btn btn-primary" @click="handleSearch">搜索</button>
          <button class="btn" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th><input type="checkbox" v-model="selectAll" @change="toggleSelectAll" /></th>
            <th>订单号</th>
            <th>用户</th>
            <th>商品</th>
            <th>金额</th>
            <th>状态</th>
            <th>下单时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in filteredOrders" :key="order.id">
            <td><input type="checkbox" v-model="selectedIds" :value="order.id" /></td>
            <td class="order-no">{{ order.no }}</td>
            <td>{{ order.user }}</td>
            <td>{{ order.items }}</td>
            <td>¥{{ order.amount }}</td>
            <td><span :class="'tag-' + order.statusClass">{{ order.status }}</span></td>
            <td class="order-time">{{ order.time }}</td>
            <td class="action-cell">
              <button class="btn btn-sm" @click="handleConfirmShip(order)">确认发货</button>
              <button class="btn btn-sm" @click="handleLogistics(order)">物流</button>
              <button class="btn btn-sm" @click="handleDetail(order)">详情</button>
            </td>
          </tr>
          <tr v-if="filteredOrders.length === 0">
            <td colspan="8" class="empty-cell">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <button class="btn btn-sm" :disabled="currentPage <= 1" @click="currentPage--">上一页</button>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
      <button class="btn btn-sm" :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import api from '../api/index'
import { getOrderOpsStats } from '../api/admin'
import { ElMessage } from 'element-plus'

const filters = reactive({
  status: '',
  dateStart: '',
  dateEnd: '',
  search: ''
})

const statsData = ref({
  pendingShip: 0,
  afterSale: 0,
  abnormal: 0
})

const selectAll = ref(false)
const selectedIds = ref([])
const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)
const total = ref(0)

const orderList = ref([])

// 获取订单统计数据
async function fetchStats() {
  try {
    const res = await getOrderOpsStats()
    if (res) {
      statsData.value = res
    }
  } catch (err) {
    console.error('获取订单统计数据失败:', err)
  }
}

// 获取订单列表
async function fetchOrders() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize,
      ...filters
    }
    Object.keys(params).forEach(k => {
      if (!params[k]) delete params[k]
    })
    const res = await api.get('/orders/list', { params })
    if (res) {
      orderList.value = res.records || res.list || res
      total.value = res.total || 0
    }
  } catch (err) {
    console.error('获取订单列表失败:', err)
    ElMessage.error('获取订单列表失败')
  } finally {
    loading.value = false
  }
}

const filteredOrders = computed(() => {
  return orderList.value
})

const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

function toggleSelectAll() {
  if (selectAll.value) {
    selectedIds.value = filteredOrders.value.map(o => o.id)
  } else {
    selectedIds.value = []
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchOrders()
}

function handleReset() {
  filters.status = ''
  filters.dateStart = ''
  filters.dateEnd = ''
  filters.search = ''
  currentPage.value = 1
  fetchOrders()
}

function handleConfirmShip(order) {
  console.log('确认发货', order.no)
}

function handleLogistics(order) {
  console.log('查看物流', order.no)
}

function handleDetail(order) {
  console.log('查看详情', order.no)
}

onMounted(() => {
  fetchStats()
  fetchOrders()
})
</script>

<style scoped lang="css">
.order-list-page {
  max-width: 1200px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  margin: 0;
}

.status-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #8e8e93;
}

.summary-item strong {
  color: #1d1d1f;
  margin-left: 4px;
}

.summary-item--warn {
  color: #ef4444;
}

.summary-divider {
  color: #e5e5ea;
}

.query-panel {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.form-row {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.form-group {
  flex: 1;
  min-width: 160px;
}

.form-group--action {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #555;
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e5e5ea;
  border-radius: 6px;
  font-size: 13px;
  color: #1d1d1f;
  background: #f9f9fb;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #2563eb;
}

.table-wrapper {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: #8e8e93;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

.data-table td {
  padding: 12px 14px;
  font-size: 13px;
  color: #333;
  border-bottom: 1px solid #f5f5f7;
}

.data-table tr:hover {
  background: #fafafa;
}

.order-no {
  font-family: monospace;
  font-size: 12px;
  color: #2563eb;
}

.order-time {
  font-size: 12px;
  color: #8e8e93;
  white-space: nowrap;
}

.action-cell {
  display: flex;
  gap: 6px;
  white-space: nowrap;
}

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid #e5e5ea;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  color: #555;
  transition: all 0.2s;
}

.btn-sm:hover {
  border-color: #2563eb;
  color: #2563eb;
}

.empty-cell {
  text-align: center;
  padding: 40px 0;
  color: #aeaeb2;
  font-size: 14px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.page-info {
  font-size: 13px;
  color: #8e8e93;
}
</style>
