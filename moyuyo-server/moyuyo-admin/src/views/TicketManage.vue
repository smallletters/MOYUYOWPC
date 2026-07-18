<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">工单管理</h1>
        <p class="page-desc">追踪和处理所有客户工单，保障服务质量与 SLA 达标</p>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <section class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-header">
          <span class="kpi-label">待处理</span>
          <div class="kpi-icon kpi-icon-red">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          </div>
        </div>
        <div class="kpi-value kpi-value-red">{{ ticketStats.pending }}</div>
        <div class="kpi-desc">需优先处理</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-header">
          <span class="kpi-label">进行中</span>
          <div class="kpi-icon kpi-icon-blue">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          </div>
        </div>
        <div class="kpi-value kpi-value-blue">{{ ticketStats.inProgress }}</div>
        <div class="kpi-desc">正在跟进</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-header">
          <span class="kpi-label">今日已关闭</span>
          <div class="kpi-icon kpi-icon-green">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          </div>
        </div>
        <div class="kpi-value kpi-value-green">{{ ticketStats.closed }}</div>
        <div class="kpi-desc">已完成工单</div>
      </div>
      <div class="kpi-card kpi-card-accent">
        <div class="kpi-header">
          <span class="kpi-label" style="color: var(--primary)">SLA 达标率</span>
          <div class="kpi-icon kpi-icon-brand">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
          </div>
        </div>
        <div class="kpi-value" style="color: var(--primary)">{{ ticketStats.slaRate }}%</div>
        <div class="kpi-desc" style="color: var(--brand-600)">当前响应表现优秀，继续保持</div>
      </div>
    </section>

    <!-- 筛选面板 -->
    <section class="query-panel">
      <div class="filter-row">
        <!-- Tab 切换 -->
        <div class="tab-switcher-custom">
          <button
            v-for="tab in statusTabs"
            :key="tab.key"
            :class="['tab-item', { active: activeStatus === tab.key }]"
            :style="tab.key === 'timeout' ? { color: activeStatus === 'timeout' ? '' : 'var(--state-error)' } : {}"
            @click="activeStatus = tab.key; handleFilter()"
          >{{ tab.label }}</button>
        </div>

        <div class="filter-divider"></div>

        <!-- 类型筛选 -->
        <select v-model="filterType" class="filter-select" @change="handleFilter">
          <option value="">全部类型</option>
          <option value="退款">退款</option>
          <option value="物流">物流</option>
          <option value="咨询">咨询</option>
          <option value="投诉">投诉</option>
        </select>

        <!-- 优先级筛选 -->
        <select v-model="filterPriority" class="filter-select" @change="handleFilter">
          <option value="">全部优先级</option>
          <option value="高">高</option>
          <option value="中">中</option>
          <option value="低">低</option>
        </select>

        <div class="filter-divider"></div>

        <!-- 搜索框 -->
        <div class="search-field">
          <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="searchKeyword" class="search-input" type="text" placeholder="搜索工单号 / 用户名 / 标题" @input="handleFilter">
        </div>
      </div>
    </section>

    <!-- 工单表格 -->
    <section class="table-wrapper">
      <table class="ticket-table">
        <thead>
          <tr>
            <th style="width: 155px;">工单号</th>
            <th style="width: 85px;">类型</th>
            <th style="width: 70px;">优先级</th>
            <th>标题</th>
            <th style="width: 115px;">用户</th>
            <th style="width: 100px;">创建时间</th>
            <th style="width: 90px;">状态</th>
            <th style="width: 100px;">分配客服</th>
            <th style="width: 105px;">响应时间</th>
            <th style="width: 75px;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredList" :key="item.id">
            <td><span class="ticket-no">{{ item.ticketNo }}</span></td>
            <td><span :class="['tag', typeTagClass(item.type)]">{{ item.type }}</span></td>
            <td><span :class="['tag', priorityTagClass(item.priority)]">{{ item.priority }}</span></td>
            <td class="ticket-title">{{ item.title }}</td>
            <td>
              <div class="user-cell">
                <div :class="['user-avatar', userAvatarClass(item.user)]">{{ item.user.charAt(0) }}</div>
                <span>{{ item.user }}</span>
              </div>
            </td>
            <td class="cell-muted">{{ item.createTime }}</td>
            <td><span :class="['status-pill', statusPillClass(item.status)]">{{ item.status }}</span></td>
            <td>{{ item.agent || '待分配' }}</td>
            <td>
              <span v-if="item.timeout" class="response-timeout">{{ item.responseTime }}</span>
              <span v-else class="cell-muted">{{ item.responseTime }}</span>
            </td>
            <td>
              <button class="action-btn" @click="handleView(item)">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                查看
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- 分页 -->
    <div class="pagination-bar">
      <span class="pagination-info">共 {{ filteredList.length }} 条工单，当前第 {{ currentPage }}/{{ totalPages }} 页</span>
      <div class="pagination-btns">
        <button class="page-btn" :disabled="currentPage <= 1" @click="currentPage > 1 && currentPage--">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <template v-for="p in pageNumbers" :key="p">
          <button
            v-if="p !== '...'"
            :class="['page-btn', { active: currentPage === p }]"
            @click="currentPage = p"
          >{{ p }}</button>
          <span v-else class="page-ellipsis">...</span>
        </template>
        <button class="page-btn" :disabled="currentPage >= totalPages" @click="currentPage < totalPages && currentPage++">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getTicketList, getTicketStats } from '../api/admin'

const activeStatus = ref('all')
const filterType = ref('')
const filterPriority = ref('')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = 5

// 工单统计KPI数据
const ticketStats = ref({
  pending: 0,
  inProgress: 0,
  closed: 0,
  slaRate: 0
})

// 从API加载的工单列表
const ticketList = ref([])

const statusTabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待处理' },
  { key: 'progress', label: '进行中' },
  { key: 'closed', label: '已关闭' },
  { key: 'timeout', label: '超时' }
]

// 前端筛选计算属性
const filteredList = computed(() => {
  let list = [...ticketList.value]
  if (activeStatus.value === 'pending') list = list.filter(i => i.status === '待处理')
  else if (activeStatus.value === 'progress') list = list.filter(i => i.status === '进行中')
  else if (activeStatus.value === 'closed') list = list.filter(i => i.status === '已关闭')
  else if (activeStatus.value === 'timeout') list = list.filter(i => i.timeout)
  if (filterType.value) list = list.filter(i => i.type === filterType.value)
  if (filterPriority.value) list = list.filter(i => i.priority === filterPriority.value)
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(i =>
      i.ticketNo.toLowerCase().includes(kw) ||
      i.user.toLowerCase().includes(kw) ||
      i.title.toLowerCase().includes(kw)
    )
  }
  return list
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredList.value.length / pageSize)))

const pageNumbers = computed(() => {
  const total = totalPages.value
  const current = currentPage.value
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages = []
  if (current <= 3) {
    for (let i = 1; i <= 5; i++) pages.push(i)
    pages.push('...', total)
  } else if (current >= total - 2) {
    pages.push(1, '...')
    for (let i = total - 4; i <= total; i++) pages.push(i)
  } else {
    pages.push(1, '...')
    for (let i = current - 1; i <= current + 1; i++) pages.push(i)
    pages.push('...', total)
  }
  return pages
})

// 从API加载工单统计和列表数据
async function loadData() {
  try {
    const [statsRes, listRes] = await Promise.all([
      getTicketStats(),
      getTicketList()
    ])
    if (statsRes) {
      ticketStats.value = statsRes
    }
    const list = (listRes && listRes.records) || listRes || []
    ticketList.value = list
  } catch (e) {
    console.error('加载工单数据失败:', e)
  }
}

function handleFilter() {
  currentPage.value = 1
}

function typeTagClass(type) {
  const map = { '退款': 'tag-red', '物流': 'tag-blue', '咨询': 'tag-green', '投诉': 'tag-orange' }
  return map[type] || 'tag-gray'
}

function priorityTagClass(priority) {
  const map = { '高': 'tag-red', '中': 'tag-warning', '低': 'tag-gray' }
  return map[priority] || 'tag-gray'
}

function statusPillClass(status) {
  const map = { '待处理': 'status-pending', '进行中': 'status-progress', '已关闭': 'status-closed' }
  return map[status] || ''
}

function userAvatarClass(user) {
  const chars = '李小张林王赵陈刘徐'
  const idx = chars.indexOf(user.charAt(0))
  const colors = ['avatar-red', 'avatar-blue', 'avatar-green', 'avatar-orange', 'avatar-gray', 'avatar-purple', 'avatar-cyan', 'avatar-pink', 'avatar-teal']
  return colors[idx >= 0 ? idx % colors.length : 0]
}

function handleView(item) {
  console.log('查看工单:', item.ticketNo)
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper {
  padding: 24px;
}
.page-header {
  margin-bottom: 24px;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 6px;
}
.page-desc {
  font-size: 13px;
  color: var(--text-400);
  margin: 0;
}

/* ===== KPI 卡片 ===== */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.kpi-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  box-shadow: var(--shadow-xs);
}
.kpi-card-accent {
  background: var(--brand-50);
  border-color: var(--brand-200);
}
.kpi-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.kpi-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-400);
}
.kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.kpi-icon-red { background: var(--state-error-surface); color: var(--state-error); }
.kpi-icon-blue { background: var(--brand-50); color: var(--primary); }
.kpi-icon-green { background: var(--state-success-surface); color: var(--state-success); }
.kpi-icon-brand { background: var(--brand-100); color: var(--brand-600); }
.kpi-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}
.kpi-value-red { color: var(--state-error); }
.kpi-value-blue { color: var(--primary); }
.kpi-value-green { color: var(--state-success); }
.kpi-desc {
  font-size: 11px;
  color: var(--text-400);
  margin-top: 4px;
}

/* ===== 筛选面板 ===== */
.query-panel {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
  margin-bottom: 20px;
}
.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.filter-divider {
  width: 1px;
  height: 28px;
  background: var(--border);
}

/* Tab 切换器 */
.tab-switcher-custom {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--background-200);
  border-radius: 10px;
  width: fit-content;
}
.tab-item {
  padding: 8px 18px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-500);
  cursor: pointer;
  transition: all 0.15s ease;
  border: none;
  background: transparent;
  font-family: var(--font-sans);
}
.tab-item:hover { color: var(--text-700); }
.tab-item.active {
  background: var(--card);
  color: var(--primary);
  box-shadow: var(--shadow-sm);
}

/* 筛选下拉 */
.filter-select {
  appearance: none;
  -webkit-appearance: none;
  background: var(--background-200);
  color: var(--text-600);
  border: none;
  border-radius: 8px;
  padding: 8px 32px 8px 12px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%238e8e93' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  font-family: var(--font-sans);
}

/* 搜索框 */
.search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 260px;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--input);
  border-radius: var(--radius);
  background: var(--background);
  color: var(--foreground);
  box-shadow: var(--shadow-xs);
  font-size: 13px;
}
.search-field:focus-within {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}
.search-icon {
  color: var(--icon-muted);
  flex-shrink: 0;
}
.search-input {
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13px;
}
.search-input::placeholder { color: var(--muted-foreground); }

/* ===== 表格 ===== */
.table-wrapper {
  overflow-x: auto;
}
.ticket-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}
.ticket-table thead th {
  background: var(--background-100);
  padding: 12px 16px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-600);
  text-align: left;
  border-bottom: 1px solid var(--border);
  letter-spacing: 0.02em;
  white-space: nowrap;
}
.ticket-table tbody td {
  padding: 14px 16px;
  font-size: 13px;
  color: var(--text-700);
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}
.ticket-table tbody tr:last-child td {
  border-bottom: none;
}
.ticket-table tbody tr:hover {
  background: var(--accent);
}
.ticket-no {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  color: var(--text-500);
}
.ticket-title {
  font-weight: 500;
  color: var(--text-800);
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cell-muted {
  color: var(--text-400);
  font-size: 12px;
}

/* 用户列 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.avatar-red { background: var(--state-error-surface); color: var(--state-error); }
.avatar-blue { background: var(--brand-100); color: var(--brand-600); }
.avatar-green { background: var(--state-success-surface); color: var(--state-success); }
.avatar-orange { background: #fff0e6; color: #e67e22; }
.avatar-gray { background: var(--background-200); color: var(--text-600); }
.avatar-purple { background: #f0e6ff; color: #5856d6; }
.avatar-cyan { background: #e0f7fa; color: #00acc1; }
.avatar-pink { background: #fce4ec; color: #e91e63; }
.avatar-teal { background: #e0f2f1; color: #00897b; }

/* 标签 */
.tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.tag-red { background: var(--state-error-surface); color: var(--state-error); }
.tag-blue { background: var(--brand-50); color: var(--brand-600); }
.tag-green { background: var(--state-success-surface); color: var(--state-success); }
.tag-gray { background: var(--background-200); color: var(--text-500); }
.tag-warning { background: #fff3cd; color: #b8860b; }
.tag-orange { background: #fff0e6; color: #e67e22; }

/* 状态胶囊 */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.status-pending { background: #fff3cd; color: #b8860b; }
.status-progress { background: var(--brand-50); color: var(--brand-500); }
.status-closed { background: var(--background-200); color: var(--text-500); }

/* 响应超时 */
.response-timeout {
  font-weight: 600;
  color: var(--state-error);
}

/* 操作按钮 */
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-600);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  font-family: var(--font-sans);
  transition: all 0.15s ease;
}
.action-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

/* ===== 分页 ===== */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}
.pagination-info {
  font-size: 12px;
  color: var(--text-400);
}
.pagination-btns {
  display: flex;
  align-items: center;
  gap: 4px;
}
.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-600);
  cursor: pointer;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  transition: all 0.15s ease;
}
.page-btn:hover:not(:disabled):not(.active) {
  border-color: var(--primary);
  color: var(--primary);
}
.page-btn.active {
  border: none;
  background: var(--primary);
  color: var(--primary-foreground);
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.page-ellipsis {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 12px;
  color: var(--text-400);
}
</style>