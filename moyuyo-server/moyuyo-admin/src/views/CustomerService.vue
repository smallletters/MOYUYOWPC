<template>
  <div class="customer-service">
    <h2 class="page-title">客服管理</h2>

    <!-- KPI 卡片 -->
    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-value red">{{ kpiData.pending }}</div>
        <div class="kpi-label">待处理</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value blue">{{ kpiData.processing }}</div>
        <div class="kpi-label">进行中</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value green">{{ kpiData.closedToday }}</div>
        <div class="kpi-label">今日已关闭</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ kpiData.slaRate }}</div>
        <div class="kpi-label">SLA达标率</div>
      </div>
    </div>

    <!-- 筛选面板 -->
    <div class="query-panel">
      <div class="tab-switcher">
        <button
          v-for="tab in statusTabs"
          :key="tab.key"
          class="tab-switcher-item"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>工单类型</label>
          <select v-model="typeFilter">
            <option value="">全部</option>
            <option value="refund">退款</option>
            <option value="complaint">投诉</option>
            <option value="consult">咨询</option>
          </select>
        </div>
        <div class="form-group">
          <label>优先级</label>
          <select v-model="priorityFilter">
            <option value="">全部</option>
            <option value="high">高</option>
            <option value="medium">中</option>
            <option value="low">低</option>
          </select>
        </div>
        <div class="form-group">
          <label>搜索</label>
          <input v-model="searchText" placeholder="工单编号 / 用户" />
        </div>
        <div class="form-actions">
          <button class="btn btn-primary btn-sm">查询</button>
          <button class="btn btn-outline btn-sm" @click="resetFilter">重置</button>
        </div>
      </div>
    </div>

    <!-- 工单表格 -->
    <div class="data-table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th>工单编号</th>
            <th>类型</th>
            <th>优先级</th>
            <th>标题</th>
            <th>用户</th>
            <th>创建时间</th>
            <th>状态</th>
            <th>处理人</th>
            <th>响应时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ticket in tickets" :key="ticket.id">
            <td><span class="table-link">{{ ticket.no }}</span></td>
            <td><span class="tag" :class="ticket.typeClass">{{ ticket.typeLabel }}</span></td>
            <td><span class="tag" :class="ticket.priorityClass">{{ ticket.priorityLabel }}</span></td>
            <td>{{ ticket.title }}</td>
            <td><span class="table-link">{{ ticket.user }}</span></td>
            <td>{{ ticket.createTime }}</td>
            <td><span class="status-dot" :class="ticket.statusDot"></span>{{ ticket.statusLabel }}</td>
            <td>{{ ticket.assignee }}</td>
            <td>{{ ticket.responseTime }}</td>
            <td class="cell-actions">
              <span class="table-link">处理</span>
              <span class="table-link">转交</span>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="pagination">
        <div class="pagination-info">共 {{ tickets.length }} 条</div>
        <div class="pagination-btns">
          <button class="pagination-btn">上一页</button>
          <button class="pagination-btn active">1</button>
          <button class="pagination-btn">2</button>
          <button class="pagination-btn">3</button>
          <button class="pagination-btn">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getTicketList, getTicketStats } from '../api/admin'
import { ElMessage } from 'element-plus'

const activeTab = ref('all')
const typeFilter = ref('')
const priorityFilter = ref('')
const searchText = ref('')
const loading = ref(false)

const statusTabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待处理' },
  { key: 'processing', label: '进行中' },
  { key: 'closed', label: '已关闭' },
  { key: 'overdue', label: '超时' }
]

// KPI数据
const kpiData = ref({
  pending: 0,
  processing: 0,
  closedToday: 0,
  slaRate: '0%'
})

const tickets = ref([])

// 获取工单统计数据
async function fetchStats() {
  try {
    const res = await getTicketStats()
    if (res) {
      kpiData.value = res
    }
  } catch (err) {
    console.error('获取工单统计数据失败:', err)
  }
}

// 获取工单列表
async function fetchTickets() {
  loading.value = true
  try {
    const params = {
      status: activeTab.value,
      type: typeFilter.value,
      priority: priorityFilter.value,
      search: searchText.value
    }
    Object.keys(params).forEach(k => {
      if (!params[k]) delete params[k]
    })
    const res = await getTicketList()
    if (res) {
      const list = res.records || res.list || res
      // 如果有筛选参数，在前端过滤（简化处理）
      if (activeTab.value !== 'all' || typeFilter.value || priorityFilter.value || searchText.value) {
        tickets.value = Array.isArray(list) ? list.filter(t => {
          let match = true
          if (activeTab.value !== 'all' && t.statusKey !== activeTab.value) match = false
          if (typeFilter.value && t.typeKey !== typeFilter.value) match = false
          if (priorityFilter.value && t.priorityKey !== priorityFilter.value) match = false
          if (searchText.value && !t.no?.includes(searchText.value) && !t.user?.includes(searchText.value)) match = false
          return match
        }) : list
      } else {
        tickets.value = list
      }
    }
  } catch (err) {
    console.error('获取工单列表失败:', err)
    ElMessage.error('获取工单列表失败')
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  typeFilter.value = ''
  priorityFilter.value = ''
  searchText.value = ''
  fetchTickets()
}

onMounted(() => {
  fetchStats()
  fetchTickets()
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

.kpi-value.red { color: var(--state-error); }
.kpi-value.blue { color: var(--primary); }
.kpi-value.green { color: var(--state-success); }

.kpi-label {
  font-size: 13px;
  color: var(--text-400);
}
</style>
