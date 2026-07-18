<template>
  <div class="user-list-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="kpi-card" v-for="stat in statsList" :key="stat.label">
        <div class="kpi-label">{{ stat.label }}</div>
        <div class="kpi-value">{{ stat.value }}</div>
        <div v-if="stat.change" class="kpi-change" :class="stat.trend === 'up' ? 'kpi-change--up' : 'kpi-change--down'">
          {{ stat.change }}
        </div>
      </div>
    </div>

    <!-- 筛选面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>搜索</label>
          <input v-model="filters.search" type="text" class="form-input" placeholder="用户名/邮箱/ID" />
        </div>
        <div class="form-group">
          <label>会员等级</label>
          <select v-model="filters.level" class="form-input">
            <option value="">全部等级</option>
            <option value="普通会员">普通会员</option>
            <option value="银卡会员">银卡会员</option>
            <option value="金卡会员">金卡会员</option>
            <option value="钻石会员">钻石会员</option>
          </select>
        </div>
        <div class="form-group">
          <label>注册渠道</label>
          <select v-model="filters.channel" class="form-input">
            <option value="">全部渠道</option>
            <option value="web">网页端</option>
            <option value="app">App</option>
            <option value="wechat">微信小程序</option>
          </select>
        </div>
        <div class="form-group">
          <label>状态</label>
          <select v-model="filters.status" class="form-input">
            <option value="">全部</option>
            <option value="active">正常</option>
            <option value="banned">封禁</option>
            <option value="inactive">未激活</option>
          </select>
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
            <th>ID</th>
            <th>用户信息</th>
            <th>邮箱</th>
            <th>注册时间</th>
            <th>会员等级</th>
            <th>订单数</th>
            <th>消费额</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in filteredUsers" :key="user.id">
            <td><input type="checkbox" v-model="selectedIds" :value="user.id" /></td>
            <td class="user-id">{{ user.id }}</td>
            <td class="user-info-cell">
              <div class="user-avatar" :style="{ backgroundColor: user.avatarColor }">
                {{ user.name.charAt(0) }}
              </div>
              <span class="user-name">{{ user.name }}</span>
            </td>
            <td>{{ user.email }}</td>
            <td class="user-time">{{ user.registerTime }}</td>
            <td>
              <span :class="'user-level level-' + user.levelClass">{{ user.level }}</span>
            </td>
            <td>{{ user.orders }}</td>
            <td>¥{{ user.spent }}</td>
            <td>
              <span class="status-dot" :class="'status-' + user.statusClass"></span>
              {{ user.status }}
            </td>
            <td class="action-cell">
              <button class="btn btn-sm" @click="handleDetail(user)">详情</button>
              <button class="btn btn-sm" @click="handleBan(user)">
                {{ user.statusClass === 'active' ? '封禁' : '解封' }}
              </button>
            </td>
          </tr>
          <tr v-if="filteredUsers.length === 0">
            <td colspan="10" class="empty-cell">暂无数据</td>
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
import { ElMessage } from 'element-plus'

const selectAll = ref(false)
const selectedIds = ref([])
const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)
const total = ref(0)

const statsList = ref([
  { label: '总用户数', value: '-' },
  { label: '今日新增', value: '-', change: '', trend: 'up' },
  { label: '活跃用户', value: '-', change: '', trend: 'up' },
  { label: 'GDPR 待处理', value: '-', change: '', trend: 'down' }
])

const filters = reactive({
  search: '',
  level: '',
  channel: '',
  status: ''
})

const userList = ref([])

// 获取用户统计数据
async function fetchStats() {
  try {
    const res = await api.get('/users/stats')
    if (res) {
      statsList.value = res
    }
  } catch (err) {
    console.error('获取用户统计数据失败:', err)
  }
}

// 获取用户列表
async function fetchUsers() {
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
    const res = await api.get('/users/list', { params })
    if (res) {
      userList.value = res.records || res.list || res
      total.value = res.total || 0
    }
  } catch (err) {
    console.error('获取用户列表失败:', err)
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const filteredUsers = computed(() => {
  return userList.value
})

const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

function toggleSelectAll() {
  if (selectAll.value) {
    selectedIds.value = filteredUsers.value.map(u => u.id)
  } else {
    selectedIds.value = []
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchUsers()
}

function handleReset() {
  filters.search = ''
  filters.level = ''
  filters.channel = ''
  filters.status = ''
  currentPage.value = 1
  fetchUsers()
}

function handleDetail(user) {
  console.log('用户详情', user.name)
}

function handleBan(user) {
  console.log('封禁/解封', user.name)
}

onMounted(() => {
  fetchStats()
  fetchUsers()
})
</script>

<style scoped lang="css">
.user-list-page {
  max-width: 1200px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  margin: 0;
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.kpi-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.kpi-label {
  font-size: 13px;
  color: #8e8e93;
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 4px;
}

.kpi-change {
  font-size: 13px;
  font-weight: 500;
}

.kpi-change--up {
  color: #10b981;
}

.kpi-change--down {
  color: #ef4444;
}

/* 查询面板 */
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

/* 表格 */
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
  white-space: nowrap;
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

.user-id {
  font-family: monospace;
  font-size: 12px;
  color: #8e8e93;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.user-name {
  font-weight: 500;
  color: #1d1d1f;
}

.user-time {
  font-size: 12px;
  color: #8e8e93;
  white-space: nowrap;
}

/* 会员等级标签 */
.user-level {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.level-diamond {
  background: #fefce8;
  color: #ca8a04;
}

.level-gold {
  background: #fff7ed;
  color: #c2410c;
}

.level-silver {
  background: #f3f4f6;
  color: #6b7280;
}

.level-normal {
  background: #eff6ff;
  color: #2563eb;
}

/* 状态点 */
.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.status-active {
  background: #10b981;
}

.status-banned {
  background: #ef4444;
}

.status-inactive {
  background: #d1d5db;
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
