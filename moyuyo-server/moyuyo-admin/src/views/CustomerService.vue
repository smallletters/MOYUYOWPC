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
            <option value="logistics">物流</option>
            <option value="consult">咨询</option>
            <option value="complaint">投诉</option>
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
          <button class="btn btn-primary btn-sm" @click="handleSearch">查询</button>
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
        <div class="pagination-left">
          <div class="pagination-info">共 {{ total }} 条</div>
          <!-- 每页条数切换：变更后回到第 1 页重新查询 -->
          <select class="page-size-select" v-model.number="pageSize" @change="handleSizeChange">
            <option :value="10">10 条/页</option>
            <option :value="20">20 条/页</option>
            <option :value="50">50 条/页</option>
            <option :value="100">100 条/页</option>
          </select>
        </div>
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

    <!-- 转交工单弹窗：选择客服下拉 -->
    <el-dialog
      v-model="transferDialogVisible"
      title="转交工单"
      width="420px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="form-row">
        <div class="form-group">
          <label>请选择客服</label>
          <select v-model="transferAssignee" :disabled="csStaffLoading">
            <option value="" disabled>请选择客服</option>
            <option v-for="s in csStaffList" :key="s.agentId" :value="s.agentId">
              {{ s.agentName }}（{{ s.agentId }}）
            </option>
          </select>
        </div>
      </div>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <button class="btn btn-outline btn-sm" @click="transferDialogVisible = false">取消</button>
          <button
            class="btn btn-primary btn-sm"
            :disabled="!transferAssignee || transferSubmitting"
            @click="confirmTransfer"
          >
            {{ transferSubmitting ? '转交中...' : '确认转交' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { getTicketList, getTicketStats, assignTicket, getCsStaff } from '../api/admin'
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
// 每页条数：支持 10 / 20 / 50 / 100 切换
const pageSize = ref(10)
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
const pageCount = computed(() => Math.ceil(total.value / pageSize.value) || 1)

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
    // 后端 mo_ticket 表存储约定：
    //   status   = PENDING / PROCESSING / CLOSED （英文枚举）
    //   type     = 退款 / 物流 / 咨询 / 投诉      （中文）
    //   priority = 高 / 中 / 低                  （中文）
    // 前端 type/priority 下拉选项是英文枚举，所以需要在这里把英文映射成后端真实值
    // 'all' / 'overdue' 不传 status，超时改用后端 overdue 参数过滤
    const typeMap = {
      REFUND: '退款',
      LOGISTICS: '物流',
      CONSULT: '咨询',
      COMPLAINT: '投诉',
      OTHER: '其他'
    }
    const priorityMap = {
      HIGH: '高',
      MEDIUM: '中',
      LOW: '低'
    }

    // 分页参数交给后端：保证 total 与当前页数据一致，避免前端过滤导致空页
    const params = { page: currentPage.value, size: pageSize.value }
    // 状态：all / overdue 不传 status；超时由 overdue 参数在数据库侧过滤
    if (activeTab.value && activeTab.value !== 'all' && activeTab.value !== 'overdue') {
      params.status = activeTab.value.toUpperCase()
    }
    if (activeTab.value === 'overdue') {
      params.overdue = true
    }
    if (typeFilter.value) {
      params.type = typeMap[typeFilter.value.toUpperCase()] || typeFilter.value
    }
    if (priorityFilter.value) {
      params.priority = priorityMap[priorityFilter.value.toUpperCase()] || priorityFilter.value
    }
    if (searchText.value.trim()) {
      // 后端 keyword 同时匹配工单编号 / 用户名 / 标题
      params.keyword = searchText.value.trim()
    }
    const res = await getTicketList(params)
    if (res) {
      const list = toArray(res)
      total.value = Number(res.total || 0)
      // 后端返回的字段与 TicketManage.vue 一致，需要映射为模板使用的显示字段
      tickets.value = list.map(t => {
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
          priorityClass: priorityInfo.class
        }
      })
    }
  } catch (err) {
    console.error('获取工单列表失败:', err)
    ElMessage.error('获取工单列表失败')
  } finally {
    loading.value = false
  }
}

/** 条件查询：重置到第一页，避免筛完后当前页码超出范围出现空页 */
function handleSearch() {
  currentPage.value = 1
  fetchTickets()
}

/** 切换每页条数：回到第一页后重新查询 */
function handleSizeChange() {
  currentPage.value = 1
  fetchTickets()
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
  const maxPage = Math.ceil(total.value / pageSize.value) || 1
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

// 转交工单：弹窗下拉选择客服（不再让用户手填 ID）
const transferDialogVisible = ref(false)
const transferTicketId = ref(null)
const transferAssignee = ref('')
const transferSubmitting = ref(false)
const csStaffList = ref([])
const csStaffLoading = ref(false)

async function fetchCsStaff() {
  csStaffLoading.value = true
  try {
    const res = await getCsStaff()
    csStaffList.value = Array.isArray(res) ? res : []
  } catch (err) {
    console.error('获取客服列表失败:', err)
    csStaffList.value = []
    ElMessage.warning('客服列表加载失败')
  } finally {
    csStaffLoading.value = false
  }
}

async function handleTransfer(id) {
  transferTicketId.value = id
  transferAssignee.value = ''
  transferDialogVisible.value = true
  // 每次打开都重新拉一次客服列表，保持最新可分配人员
  await fetchCsStaff()
}

async function confirmTransfer() {
  if (!transferTicketId.value || !transferAssignee.value) {
    ElMessage.warning('请选择客服')
    return
  }
  transferSubmitting.value = true
  try {
    await assignTicket(transferTicketId.value, { assignee: transferAssignee.value })
    ElMessage.success('工单已转交')
    transferDialogVisible.value = false
    fetchTickets()
  } catch (err) {
    ElMessage.error('转交失败: ' + (err.response?.data?.message || err.message))
  } finally {
    transferSubmitting.value = false
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

/* 分页左侧：总数 + 每页条数切换 */
.pagination-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-size-select {
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--card);
  color: var(--text-600);
  font-size: 13px;
  cursor: pointer;
}
</style>
