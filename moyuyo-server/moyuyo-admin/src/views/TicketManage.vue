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
          <tr v-for="item in filteredList" :key="item.id" :class="{ 'row-highlight': String(item.id) === String(focusTicketId) || String(item.ticketNo) === String(focusTicketId) }">
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

    <!-- 工单详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="detailTitle"
      size="780px"
      direction="rtl"
      :close-on-click-modal="false"
      @closed="onDetailClosed"
    >
      <div class="ticket-detail" v-if="detail">
        <!-- 元信息 -->
        <section class="detail-section">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="工单号">{{ detail.ticketNo || detail.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusPillType(detail.status)" size="small">{{ detail.statusText || detail.status || '-' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="类型">{{ detail.type }}</el-descriptions-item>
            <el-descriptions-item label="优先级">
              <span :class="['priority-pill', priorityPillClass(detail.priority)]">{{ detail.priority }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="用户">{{ detail.userName }}<span v-if="detail.userId">（ID: {{ detail.userId }}）</span></el-descriptions-item>
              <el-descriptions-item label="分配客服">{{ detail.agentName || '待分配' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="首响耗时">
              {{ detail.firstResponseMinutes != null ? `${detail.firstResponseMinutes} 分钟` : '-' }}
              <el-tag v-if="detail.timeout" type="danger" size="small" style="margin-left:6px">超时</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          </el-descriptions>
          <!-- 用户原始诉求（工单 content 字段） -->
          <div v-if="detail.content" class="original-content">
            <h5 class="content-title">用户原始诉求</h5>
            <div class="content-body">{{ detail.content }}</div>
          </div>
        </section>

        <!-- 对话历史 -->
        <section class="detail-section">
          <h4 class="section-title">对话记录（{{ messages.length }}）</h4>
          <div ref="msgScroll" class="msg-scroll">
            <div v-if="messagesLoading" class="msg-loading">加载中…</div>
            <div v-else-if="messages.length === 0" class="msg-empty">暂无对话记录</div>
            <div
              v-for="(m, idx) in messages"
              :key="m.id || idx"
              class="msg-row"
              :class="msgClassOf(m)"
            >
              <div class="msg-meta">
                <span class="msg-name">{{ m.senderName || senderTextOf(m) }}</span>
                <span class="msg-time">{{ formatDateTime(m.createTime) }}</span>
              </div>
              <div class="msg-bubble">{{ m.content }}</div>
            </div>
          </div>
        </section>

        <!-- 回复输入区 -->
        <section class="detail-section reply-box">
          <h4 class="section-title">客服回复</h4>
          <el-input
            v-model="replyDraft"
            type="textarea"
            :rows="4"
            :disabled="detail && detail.status === '已关闭' || replying"
            :placeholder="detail && detail.status === '已关闭' ? '工单已关闭，无法回复' : '输入回复内容，回车发送，Shift+Enter 换行'"
            @keydown.enter.exact.prevent="handleReply"
          />
          <div class="reply-actions">
            <el-button
              type="primary"
              :loading="replying"
              :disabled="!replyDraft.trim() || (detail && detail.status === '已关闭')"
              @click="handleReply"
            >
              发送回复
            </el-button>
          </div>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getTicketList, getTicketStats, getTicketDetail, getTicketMessages, replyTicket } from '../api/admin'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()

// 跨页跳转定位的工单ID（如客服管理"处理"跳转携带的 id 参数）
const focusTicketId = ref(null)

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
    ticketList.value = list.map(item => ({
      id: item.id,
      ticketNo: item.ticketNo || item.ticketNo || '',
      type: item.type || '咨询',
      priority: item.priority || '低',
      title: item.title || '',
      user: item.user || '',
      createTime: item.createTimeFormatted || item.createTime || '',
      status: ({'PENDING':'待处理','PROCESSING':'进行中','CLOSED':'已关闭'})[item.status] || item.status || '待处理',
      agent: item.agent || '待分配',
      responseTime: item.responseTime || '-',
      timeout: item.timeout || false
    }))
    // 消费跨页跳转参数：定位目标工单并给出提示
    const qId = route.query?.id
    const qAction = route.query?.action
    if (qId) {
      focusTicketId.value = String(qId)
      const target = ticketList.value.find(t => String(t.id) === String(qId) || String(t.ticketNo) === String(qId))
      const actionText = qAction === 'process' ? '处理' : '查看'
      if (target) {
        ElMessage.info(`正在${actionText}工单 ${target.ticketNo || qId}`)
      } else {
        ElMessage.warning(`未找到工单 ${qId}`)
      }
    }
  } catch (e) {
    console.error('加载工单数据失败:', e)
    ElMessage.error('工单数据加载失败')
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
  // 打开工单详情抽屉（取代之前"跳转自身路由"的做法）
  openDetail(item)
}

// ============ 工单详情抽屉 ============
const detailVisible = ref(false)
const detail = ref(null)        // 当前工单详情（来自 /ticket/{id}）
const messages = ref([])         // 当前工单消息历史
const messagesLoading = ref(false)
const replyDraft = ref('')
const replying = ref(false)
const msgScroll = ref(null)

const detailTitle = computed(() => {
  if (!detail.value) return '工单详情'
  return `工单详情 · ${detail.value.ticketNo || detail.value.id}`
})

// 从后端 /ticket/{id} 接口拉详情；同时复用列表行数据补全 type/priority/userName 等
async function openDetail(item) {
  detail.value = null
  messages.value = []
  replyDraft.value = ''
  detailVisible.value = true
  try {
    const res = await getTicketDetail(item.id)
    // 后端 detail 接口只返回固定字段：id/ticketNo/title/status/assignee/replyContent/firstResponseMinutes/createdAt/replies
    // 其它字段（type/priority/userName/userId/agentName/content）需要用列表行 item 补
    const baseRow = item || {}
    detail.value = normalizeDetail(res || {}, baseRow)
  } catch (e) {
    // fallback 使用行数据
    detail.value = normalizeDetail({}, item)
  }
  await loadMessages(item.id)
  scrollMsgToBottom()
}

function normalizeDetail(apiResp, row) {
  const statusMap = { 'PENDING': '待处理', 'PROCESSING': '进行中', 'CLOSED': '已关闭' }
  // 后端 getTicketDetail 的状态是 code（PENDING 等），列表行的 status 已经是中文
  const statusText = statusMap[apiResp.status] || row.status || '待处理'
  return {
    id: apiResp.id ?? row.id,
    ticketNo: apiResp.ticketNo ?? row.ticketNo,
    title: apiResp.title ?? row.title,
    // 列表行才有这些字段
    type: row.type,
    priority: row.priority,
    status: statusText,
    statusCode: apiResp.status,
    userId: row.userId,
    userName: row.userName,
    agentName: apiResp.assignee || row.agentName,
    createTime: apiResp.createdAt || row.createTime,
    firstResponseMinutes: apiResp.firstResponseMinutes,
    timeout: row.timeout,
    content: row.content
  }
}

async function loadMessages(ticketId) {
  messagesLoading.value = true
  try {
    const list = await getTicketMessages(ticketId)
    messages.value = (list || [])
  } catch (e) {
    ElMessage.error('加载对话记录失败')
  } finally {
    messagesLoading.value = false
  }
}

function senderTextOf(m) {
  if (m.senderType === 'SYSTEM') return '系统'
  if (m.senderType === 'AGENT') return '客服'
  return '用户'
}

function msgClassOf(m) {
  if (m.senderType === 'AGENT') return 'msg-row--agent'
  if (m.senderType === 'SYSTEM') return 'msg-row--system'
  return 'msg-row--user'
}

function statusPillType(statusText) {
  return { '待处理': 'warning', '进行中': 'success', '已关闭': 'info' }[statusText] || 'info'
}

function priorityPillClass(p) {
  return { '高': 'priority-high', '中': 'priority-mid', '低': 'priority-low' }[p] || ''
}

function formatDate(d) {
  if (!d) return '-'
  try {
    const dt = new Date(d)
    if (isNaN(dt.getTime())) return String(d)
    const pad = n => String(n).padStart(2, '0')
    return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())} ${pad(dt.getHours())}:${pad(dt.getMinutes())}`
  } catch (e) {
    return String(d)
  }
}

function formatDateTime(d) {
  if (!d) return ''
  try {
    const dt = new Date(d)
    if (isNaN(dt.getTime())) return String(d)
    const pad = n => String(n).padStart(2, '0')
    return `${pad(dt.getMonth() + 1)}-${pad(dt.getDate())} ${pad(dt.getHours())}:${pad(dt.getMinutes())}`
  } catch (e) {
    return String(d)
  }
}

function scrollMsgToBottom() {
  nextTick(() => {
    const el = msgScroll.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function handleReply() {
  const content = (replyDraft.value || '').trim()
  if (!content) return
  if (!detail.value) return
  if (detail.value.status === '已关闭') {
    ElMessage.warning('工单已关闭，无法回复')
    return
  }
  replying.value = true
  try {
    await replyTicket(detail.value.id, { content })
    // 乐观更新：直接把消息追加到列表
    messages.value.push({
      senderType: 'AGENT',
      senderName: '客服',
      content,
      createTime: new Date().toISOString()
    })
    replyDraft.value = ''
    ElMessage.success('回复成功')
    // 同步拉一次历史，确保与服务端一致
    await loadMessages(detail.value.id)
    scrollMsgToBottom()
    // 顺带刷新列表的 status（已从 PENDING → PROCESSING）
    loadData()
  } catch (e) {
    ElMessage.error((e && e.message) || '回复失败')
  } finally {
    replying.value = false
  }
}

function onDetailClosed() {
  detail.value = null
  messages.value = []
  replyDraft.value = ''
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
.ticket-table tbody tr.row-highlight {
  background: var(--brand-50);
  box-shadow: inset 3px 0 0 var(--primary);
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

/* ===== 工单详情抽屉 ===== */
.ticket-detail { display: flex; flex-direction: column; gap: 16px; padding: 0 4px; }
.detail-section { padding: 4px 0; }
.section-title {
  font-size: 13px; font-weight: 600; color: var(--text-700);
  margin: 0 0 10px; padding-left: 6px; border-left: 3px solid var(--primary);
}
.priority-pill {
  display: inline-flex; align-items: center;
  padding: 2px 10px; border-radius: 999px;
  font-size: 11px; font-weight: 600;
}
.priority-high { background: var(--state-error-surface); color: var(--state-error); }
.priority-mid  { background: #fff3cd; color: #b8860b; }
.priority-low  { background: var(--background-200); color: var(--text-500); }

.msg-scroll {
  max-height: 340px; overflow-y: auto;
  display: flex; flex-direction: column; gap: 12px;
  padding: 6px 4px;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}
.msg-loading, .msg-empty {
  text-align: center; color: var(--text-400); font-size: 13px;
  padding: 24px 0;
}
.msg-row {
  display: flex; flex-direction: column;
  max-width: 80%; padding: 0;
}
.msg-row--user { align-self: flex-start; }
.msg-row--agent { align-self: flex-end; align-items: flex-end; }
.msg-row--system { align-self: center; }
.msg-meta {
  font-size: 11px; color: var(--text-400); margin-bottom: 4px;
  display: flex; gap: 8px;
}
.msg-row--agent .msg-meta { flex-direction: row-reverse; }
.msg-bubble {
  padding: 9px 13px; border-radius: 12px;
  background: #fff; border: 1px solid var(--border);
  font-size: 13px; line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
  color: var(--text-800);
}
.msg-row--agent .msg-bubble {
  background: #2563eb; color: #fff; border-color: #2563eb;
}
.msg-row--system .msg-bubble {
  background: transparent; border-style: dashed; color: var(--text-400); font-style: italic;
}

.original-content {
  margin-top: 12px;
  padding: 12px 14px;
  background: var(--brand-50);
  border-left: 3px solid var(--primary);
  border-radius: 6px;
}
.content-title {
  font-size: 12px; font-weight: 600; color: var(--brand-700);
  margin: 0 0 6px;
}
.content-body {
  font-size: 13px; line-height: 1.6; color: var(--text-700);
  white-space: pre-wrap; word-break: break-word;
}
.reply-box :deep(.el-textarea__inner) { resize: none; }
.reply-actions {
  display: flex; justify-content: flex-end;
  padding-top: 10px;
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