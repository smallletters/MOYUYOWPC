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
          <button class="btn btn-primary btn-sm" @click="fetchTickets">查询</button>
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
              <span class="table-link" @click="handleTicket(ticket.id)">处理</span>
              <span class="table-link" @click="handleTransfer(ticket.id)">转交</span>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="pagination">
        <div class="pagination-info">共 {{ total }} 条</div>
        <div class="pagination-btns">
          <button class="pagination-btn" :disabled="currentPage <= 1" @click="prevPage">上一页</button>
          <button
            v-for="p in displayPages"
            :key="p"
            class="pagination-btn"
            :class="{ active: p === currentPage }"
            @click="goToPage(p)"
          >{{ p }}</button>
          <button class="pagination-btn" :disabled="currentPage >= pageCount" @click="nextPage">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { getTicketList, getTicketStats, assignTicket } from '../api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { toArray } from '../utils/safeArray'

const router = useRouter()

const activeTab = ref('all')
const typeFilter = ref('')
const priorityFilter = ref('')
const searchText = ref('')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 10
const total = ref(0)

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

// 后端返回的字段名与 TicketManage.vue 保持一致：status, type, priority
// 前端模板使用 statusLabel/typeLabel/priorityLabel 等显示字段，此处统一做映射
function mapStatus(status) {
  const s = String(status || '').toUpperCase()
  const map = {
    'PENDING': { label: '待处理', dot: 'dot-warning', class: 'tag-yellow' },
    'PROCESSING': { label: '进行中', dot: 'dot-success', class: 'tag-blue' },
    'CLOSED': { label: '已关闭', dot: 'dot-gray', class: 'tag-gray' },
    'RESOLVED': { label: '已解决', dot: 'dot-gray', class: 'tag-green' }
  }
  return map[s] || { label: s || '未知', dot: 'dot-gray', class: 'tag-gray' }
}

function mapType(type) {
  const t = String(type || '').toUpperCase()
  const map = {
    'REFUND': { label: '退款', class: 'tag-red' },
    'COMPLAINT': { label: '投诉', class: 'tag-orange' },
    'CONSULT': { label: '咨询', class: 'tag-blue' },
    'OTHER': { label: '其他', class: 'tag-gray' }
  }
  return map[t] || { label: type || '其他', class: 'tag-gray' }
}

function mapPriority(priority) {
  const p = String(priority || '').toUpperCase()
  const map = {
    'HIGH': { label: '高', class: 'tag-red' },
    'MEDIUM': { label: '中', class: 'tag-yellow' },
    'LOW': { label: '低', class: 'tag-blue' }
  }
  return map[p] || { label: priority || '中', class: 'tag-yellow' }
}

const tickets = ref([])
const pageCount = computed(() => Math.ceil(total.value / pageSize) || 1)

// 动态计算显示页码（最多显示5页）
const displayPages = computed(() => {
  const pages = []
  const pCount = pageCount.value
  const current = currentPage.value
  let start = Math.max(1, current - 2)
  let end = Math.min(pCount, start + 4)
  if (end - start < 4) {
    start = Math.max(1, end - 4)
  }
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }
  return pages
})

// 获取工单统计数据
async function fetchStats() {
  try {
    const res = await getTicketStats()
    if (res) {
      kpiData.value = res
    }
  } catch (err) {
    console.error('获取工单统计数据失败:', err)
    ElMessage.warning('工单统计数据加载失败')
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
    const res = await getTicketList(params)
    if (res) {
      const list = toArray(res)
      total.value = res.total || list.length
      // 后端返回的字段与 TicketManage.vue 一致，需要映射为模板使用的显示字段
      const mappedList = list.map(t => {
        const statusInfo = mapStatus(t.status || t.statusKey)
        const typeInfo = mapType(t.type || t.typeKey)
        const priorityInfo = mapPriority(t.priority || t.priorityKey)
        return {
          ...t,
          no: t.ticketNo || t.no || t.id,
          user: t.user || t.userName || '',
          assignee: t.agent || t.assignee || t.agentName || '',
          createTime: t.createTime || t.createdAt || '',
          responseTime: t.responseTime || t.respondedAt || '',
          statusLabel: statusInfo.label,
          statusDot: statusInfo.dot,
          statusClass: statusInfo.class,
          typeLabel: typeInfo.label,
          typeClass: typeInfo.class,
          priorityLabel: priorityInfo.label,
          priorityClass: priorityInfo.class,
          // 保留原始字段用于过滤
          _status: String(t.status || t.statusKey || '').toUpperCase(),
          _type: String(t.type || t.typeKey || '').toUpperCase(),
          _priority: String(t.priority || t.priorityKey || '').toUpperCase()
        }
      })
      // 根据筛选条件决定是否在前端过滤
      const needFilter = activeTab.value !== 'all' || typeFilter.value || priorityFilter.value || searchText.value
      if (needFilter) {
        tickets.value = mappedList.filter(t => {
          let match = true
          if (activeTab.value !== 'all' && t._status !== activeTab.value.toUpperCase()) match = false
          if (typeFilter.value && t._type !== typeFilter.value.toUpperCase()) match = false
          if (priorityFilter.value && t._priority !== priorityFilter.value.toUpperCase()) match = false
          if (searchText.value && !String(t.no).includes(searchText.value) && !String(t.user).includes(searchText.value)) match = false
          return match
        })
      } else {
        tickets.value = mappedList
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
  currentPage.value = 1
  fetchTickets()
}

// Tab 切换时重新加载数据
watch(activeTab, () => {
  currentPage.value = 1
  fetchTickets()
})

// 分页处理
function prevPage() {
  if (currentPage.value > 1) {
    currentPage.value--
    fetchTickets()
  }
}

function nextPage() {
  const maxPage = Math.ceil(total.value / pageSize) || 1
  if (currentPage.value < maxPage) {
    currentPage.value++
    fetchTickets()
  }
}

function goToPage(page) {
  currentPage.value = page
  fetchTickets()
}

onMounted(() => {
  fetchStats()
  fetchTickets()
})

// 处理工单
function handleTicket(id) {
  // 导航到工单管理页并传递工单ID和操作类型
  router.push({ path: '/ticket', query: { id, action: 'process' } })
}

// 转交工单
async function handleTransfer(id) {
  // 弹出转交确认框，选择转交人
  try {
    const { value: assignee } = await ElMessageBox.prompt('请输入转交人ID（客服ID）', '转交工单', {
      confirmButtonText: '确认转交',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入客服ID'
    })
    if (assignee) {
      await assignTicket(id, { assignee })
      ElMessage.success('工单已转交')
      fetchTickets()
    }
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('转交失败: ' + (err.response?.data?.message || err.message))
    }
  }
}
</script>

<style scoped lang="css">
.page-title {
  font-size: 22px;
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
