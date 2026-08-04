<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">推送管理</h1>
        <p class="page-desc">管理 App Push、短信和邮件推送任务，追踪送达与转化数据</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" @click="openCreateDialog()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新建推送
        </button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <section class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">今日推送数</div>
        <div class="stat-value">{{ pushStats.todaySent }}</div>
        <div class="stat-trend stat-trend-up">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          +{{ pushStats.todayGrowth }}%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本月推送</div>
        <div class="stat-value">{{ pushStats.monthlySent }}</div>
        <div class="stat-trend stat-trend-up">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          +{{ pushStats.monthlyGrowth }}%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">推送成功率</div>
        <div class="stat-value">{{ pushStats.successRate }}<span class="stat-unit">%</span></div>
        <div class="stat-trend stat-trend-up">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          +{{ pushStats.rateGrowth }}%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">订阅用户数</div>
        <div class="stat-value">{{ pushStats.subscriberCount }}</div>
        <div class="stat-trend stat-trend-up">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          +{{ pushStats.subscriberGrowth }}%
        </div>
      </div>
    </section>

    <!-- 推送类型 Tab + 快速新建入口 -->
    <section class="quick-entry-card">
      <div class="section-header">
        <span class="section-title">快速新建</span>
      </div>
      <div class="quick-entry-grid">
        <button class="quick-entry-item" @click="openCreateDialog('APP')">
          <div class="quick-entry-icon quick-entry-blue">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 2H7a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>
          </div>
          <span class="quick-entry-label">App 推送</span>
          <span class="quick-entry-desc">发送手机应用通知</span>
        </button>
        <button class="quick-entry-item" @click="openCreateDialog('SMS')">
          <div class="quick-entry-icon quick-entry-green">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          </div>
          <span class="quick-entry-label">短信推送</span>
          <span class="quick-entry-desc">发送手机短信通知</span>
        </button>
        <button class="quick-entry-item" @click="openCreateDialog('EMAIL')">
          <div class="quick-entry-icon quick-entry-orange">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
          </div>
          <span class="quick-entry-label">邮件推送</span>
          <span class="quick-entry-desc">发送电子邮件通知</span>
        </button>
      </div>
    </section>

    <!-- 推送记录 -->
    <div class="section-header" style="margin: 24px 0 16px;">
      <span class="section-title">推送记录</span>
    </div>
    <div class="push-tabs">
      <button
        v-for="tab in typeTabs"
        :key="tab.value"
        class="push-tab"
        :class="{ active: typeFilter === tab.value }"
        @click="typeFilter = tab.value"
      >{{ tab.label }}</button>
    </div>
    <div class="push-list">
      <div v-if="filteredList.length === 0" class="empty-state">暂无推送记录</div>
      <div v-for="item in filteredList" :key="item.id" class="push-record">
        <div class="push-record-header">
          <div class="push-record-title-area">
            <div class="push-record-title">{{ item.title }}</div>
            <div class="push-record-summary">{{ item.content }}</div>
          </div>
          <span :class="['push-type-tag', item.typeClass]">{{ item.typeLabel }}</span>
        </div>
        <!-- 元信息 -->
        <div class="push-meta">
          <span :class="['push-status-tag', item.statusClass]">
            <span v-if="item.status === 'SENT' || item.status === 'sent'" class="status-dot" style="background: var(--state-success)"></span>
            <span v-else-if="item.status === 'PENDING' || item.status === 'pending'" class="status-dot" style="background: var(--state-warning)"></span>
            <span v-else class="status-dot" style="background: var(--text-400)"></span>
            {{ item.statusLabel }}
          </span>
          <span class="push-meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            {{ item.time }}
          </span>
          <span class="push-meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            全部用户
          </span>
        </div>
        <!-- 数据指标 -->
        <div v-if="item.status === 'SENT' || item.status === 'sent'" class="push-data-row">
          <div class="push-data-item">
            <span class="push-data-value">{{ item.sentCount }}</span>
            <span class="push-data-label">送达</span>
          </div>
          <div class="push-data-item">
            <span class="push-data-value">{{ item.openCount }}</span>
            <span class="push-data-label">打开</span>
          </div>
          <div class="push-data-item">
            <span class="push-data-value">{{ item.clickCount }}</span>
            <span class="push-data-label">点击</span>
          </div>
        </div>
        <!-- 操作按钮 -->
        <div class="push-actions">
          <button class="push-action-btn" @click="goDetail(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
            详情
          </button>
          <button class="push-action-btn push-action-primary" @click="openEditDialog(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
            编辑
          </button>
          <button v-if="item.status === 'PENDING' || item.status === 'pending'" class="push-action-btn push-action-primary" @click="handleSend(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
            发送
          </button>
          <button v-if="item.status === 'PENDING' || item.status === 'pending'" class="push-action-btn push-action-danger" @click="handleCancel(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            取消
          </button>
          <button class="push-action-btn" @click="openCopyDialog(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
            复制
          </button>
          <button v-if="item.status !== 'SENT' && item.status !== 'sent'" class="push-action-btn push-action-danger" @click="handleDelete(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            删除
          </button>
        </div>
      </div>
    </div>

    <!-- 定时推送折叠区域 -->
    <div class="collapsible-section">
      <button class="collapsible-trigger" :class="{ expanded: scheduledExpanded }" @click="scheduledExpanded = !scheduledExpanded" :aria-expanded="String(scheduledExpanded)">
        <div class="collapsible-trigger-left">
          <span class="collapsible-trigger-title">定时推送</span>
          <span class="collapsible-badge">{{ scheduledList.length }}</span>
        </div>
        <svg class="chevron-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>
      </button>
      <div class="collapsible-content" :class="{ show: scheduledExpanded }">
        <div v-if="scheduledList.length === 0" class="scheduled-empty">暂无定时推送任务</div>
        <div v-for="item in scheduledList" :key="item.id" class="scheduled-item">
          <div class="scheduled-icon scheduled-blue">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          </div>
          <div class="scheduled-info">
            <div class="scheduled-title">{{ item.title }}</div>
            <div class="scheduled-time">{{ item.scheduledTime }}</div>
          </div>
          <button class="scheduled-cancel" @click="handleCancelScheduled(item)">取消</button>
        </div>
      </div>
    </div>

    <!-- 新建/编辑推送弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑推送' : '新建推送'" width="560px">
      <el-form :model="pushForm" label-width="90px">
        <el-form-item label="推送渠道" required>
          <el-select v-model="pushForm.channel" style="width: 100%">
            <el-option label="App 推送" value="APP" />
            <el-option label="短信推送" value="SMS" />
            <el-option label="邮件推送" value="EMAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="推送标题" required>
          <el-input v-model="pushForm.title" maxlength="50" show-word-limit placeholder="输入推送标题" />
        </el-form-item>
        <el-form-item label="推送内容" required>
          <el-input v-model="pushForm.content" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="输入推送内容" />
        </el-form-item>
        <el-form-item label="定时发送">
          <el-date-picker v-model="pushForm.scheduledTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="留空表示立即创建" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPush">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPushStats, getPushRecords, getPushScheduled, createPush, updatePush, sendPush, cancelPush, deletePush } from '../api/admin'

const router = useRouter()
const scheduledExpanded = ref(false)

// 推送统计KPI数据
const pushStats = reactive({
  todaySent: 0,
  todayGrowth: 0,
  monthlySent: 0,
  monthlyGrowth: 0,
  successRate: 0,
  rateGrowth: 0,
  subscriberCount: 0,
  subscriberGrowth: 0
})

// 推送记录列表
const pushList = ref([])

// 定时推送列表
const scheduledList = ref([])

// 类型筛选
const typeFilter = ref('ALL')
const typeTabs = [
  { label: '全部', value: 'ALL' },
  { label: 'App Push', value: 'APP' },
  { label: '短信', value: 'SMS' },
  { label: '邮件', value: 'EMAIL' }
]

// 新建/编辑弹窗
const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const pushForm = reactive({ channel: 'APP', title: '', content: '', scheduledTime: '' })

// 渠道映射（兼容后端各种 type 值）
function channelOf(raw) {
  const v = String(raw || '').toUpperCase()
  if (v.includes('SMS')) return 'SMS'
  if (v.includes('EMAIL') || v.includes('MAIL')) return 'EMAIL'
  return 'APP'
}

function typeLabelOf(raw) {
  const map = { SMS: '短信推送', EMAIL: '邮件推送', APP: 'App 推送' }
  return map[channelOf(raw)] || '通知'
}

function typeClassOf(raw) {
  const map = { SMS: 'sms', EMAIL: 'email', APP: 'app-push' }
  return map[channelOf(raw)] || 'app-push'
}

function statusLabelOf(status) {
  const map = { SENT: '已发送', PENDING: '待发送', CANCELLED: '已取消', CANCELED: '已取消', DRAFT: '草稿' }
  return map[String(status || '').toUpperCase()] || (status || '未知')
}

function statusClassOf(status) {
  const s = String(status || '').toUpperCase()
  if (s === 'SENT') return 'sent'
  if (s === 'PENDING') return 'pending'
  return 'cancelled'
}

// 记录映射：补齐展示字段
function mapRecord(r) {
  const status = String(r.status || '').toUpperCase()
  return {
    id: r.id,
    title: r.title,
    content: r.content,
    channel: r.channel || r.type,
    status,
    sentCount: r.sentCount ?? 0,
    openCount: r.openCount ?? 0,
    clickCount: r.clickCount ?? 0,
    time: formatTime(r.sendTime || r.createTime),
    typeLabel: typeLabelOf(r.channel || r.type),
    typeClass: typeClassOf(r.channel || r.type),
    statusLabel: statusLabelOf(status),
    statusClass: statusClassOf(status)
  }
}

const filteredList = computed(() => {
  if (typeFilter.value === 'ALL') return pushList.value
  return pushList.value.filter(item => channelOf(item.channel) === typeFilter.value)
})

// 时间格式化
function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

// 从API加载推送数据
async function loadData() {
  try {
    const [statsRes, recordsRes, scheduledRes] = await Promise.all([
      getPushStats(),
      getPushRecords(),
      getPushScheduled()
    ])
    // 填充统计KPI
    if (statsRes) {
      Object.assign(pushStats, statsRes)
    }
    // 填充推送记录列表
    const records = (recordsRes && recordsRes.records) || recordsRes || []
    pushList.value = (Array.isArray(records) ? records : []).map(mapRecord)
    // 填充定时推送列表
    const scheduled = (scheduledRes && scheduledRes.records) || scheduledRes || []
    scheduledList.value = Array.isArray(scheduled) ? scheduled : []
  } catch (e) {
    console.error('加载推送数据失败:', e)
    ElMessage.error('加载推送数据失败')
  }
}

// ---- 新建 / 编辑 / 复制 ----
function openCreateDialog(channel) {
  editingId.value = null
  pushForm.channel = channel || 'APP'
  pushForm.title = ''
  pushForm.content = ''
  pushForm.scheduledTime = ''
  dialogVisible.value = true
}

function openEditDialog(item) {
  editingId.value = item.id
  pushForm.channel = channelOf(item.channel)
  pushForm.title = item.title
  pushForm.content = item.content
  pushForm.scheduledTime = ''
  dialogVisible.value = true
}

// 跳转到推送详情页
function goDetail(item) {
  router.push('/push-detail')
}

function openCopyDialog(item) {
  editingId.value = null
  pushForm.channel = channelOf(item.channel)
  pushForm.title = item.title + '（副本）'
  pushForm.content = item.content
  pushForm.scheduledTime = ''
  dialogVisible.value = true
}

async function submitPush() {
  if (!pushForm.title.trim()) {
    ElMessage.warning('请输入推送标题')
    return
  }
  if (!pushForm.content.trim()) {
    ElMessage.warning('请输入推送内容')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: pushForm.title.trim(),
      content: pushForm.content.trim(),
      channel: pushForm.channel
    }
    if (pushForm.scheduledTime) payload.scheduledTime = pushForm.scheduledTime
    if (editingId.value) {
      await updatePush(editingId.value, payload)
      ElMessage.success('推送已更新')
    } else {
      await createPush(payload)
      ElMessage.success('推送创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (err) {
    console.error('保存推送失败:', err)
    ElMessage.error('保存失败：' + (err?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 发送推送（调用API）
async function handleSend(item) {
  try {
    await sendPush(item.id)
    ElMessage.success('推送发送成功')
    loadData()
  } catch (e) {
    console.error('发送推送失败:', e)
    ElMessage.error('发送失败')
  }
}

// 取消推送（调用API）
async function handleCancel(item) {
  try {
    await cancelPush(item.id)
    ElMessage.success('推送已取消')
    loadData()
  } catch (e) {
    console.error('取消推送失败:', e)
    ElMessage.error('取消失败')
  }
}

// 取消定时推送
async function handleCancelScheduled(item) {
  try {
    await cancelPush(item.id)
    ElMessage.success('定时推送已取消')
    loadData()
  } catch (e) {
    console.error('取消定时推送失败:', e)
    ElMessage.error('取消失败')
  }
}

// 删除推送（调用API）
async function handleDelete(item) {
  try {
    await ElMessageBox.confirm('确定删除该推送？删除后不可恢复。', '警告', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await deletePush(item.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('删除推送失败:', e)
      ElMessage.error('删除失败: ' + (e?.message || '未知错误'))
    }
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper {
  padding: 24px;
}
.page-header {
  margin-bottom: 24px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
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
.header-actions {
  display: flex;
  gap: 8px;
}
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 18px;
  border-radius: calc(var(--radius) * 0.7);
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  border: none;
  transition: all 0.15s ease;
}
.btn-primary {
  background: var(--primary);
  color: var(--primary-foreground);
}
.btn-primary:hover { filter: brightness(0.92); }

/* ===== 统计卡片 ===== */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.stat-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 20px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s ease, transform 0.2s ease;
}
.stat-card:hover {
  border-color: var(--primary);
  transform: translateY(-1px);
}
.stat-label {
  font-size: 13px;
  color: var(--text-500);
  font-weight: 500;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-800);
  margin-top: 6px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.stat-value .stat-unit {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-500);
  margin-left: 2px;
}
.stat-trend {
  font-size: 12px;
  font-weight: 500;
  margin-top: 6px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.stat-trend-up { color: var(--state-success); }

/* ===== 快速新建 ===== */
.quick-entry-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 20px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.section-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-800);
}
.quick-entry-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.quick-entry-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 18px 12px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--card);
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: var(--font-sans);
}
.quick-entry-item:hover {
  background: var(--accent);
  border-color: var(--primary);
  transform: translateY(-1px);
}
.quick-entry-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: calc(var(--radius) * 0.7);
}
.quick-entry-blue { background: var(--brand-50); color: var(--brand-500); }
.quick-entry-green { background: var(--state-success-surface); color: var(--state-success); }
.quick-entry-orange { background: #fff4e5; color: var(--state-warning); }
.quick-entry-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
}
.quick-entry-desc {
  font-size: 12px;
  color: var(--text-400);
  text-align: center;
}

/* ===== 类型 Tab ===== */
.push-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.push-tab {
  height: 32px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-500);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: all 0.15s ease;
}
.push-tab:hover { border-color: var(--primary); color: var(--primary); }
.push-tab.active { background: var(--primary); border-color: var(--primary); color: var(--primary-foreground); }

/* ===== 推送记录列表 ===== */
.push-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}
.empty-state {
  padding: 40px 0;
  text-align: center;
  color: var(--text-400);
  font-size: 13px;
}
.push-record {
  background: var(--card);
  border-radius: var(--radius);
  padding: 20px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s ease;
}
.push-record:hover {
  border-color: var(--primary);
}
.push-record-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.push-record-title-area {
  flex: 1;
  min-width: 0;
}
.push-record-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-800);
  line-height: 1.3;
}
.push-record-summary {
  font-size: 13px;
  color: var(--text-500);
  line-height: 1.4;
  margin-top: 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 类型标签 */
.push-type-tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}
.push-type-tag.app-push { background: var(--brand-50); color: var(--brand-600); }
.push-type-tag.sms { background: #fff4e5; color: var(--state-warning); }
.push-type-tag.email { background: #f0e6ff; color: #5856d6; }

/* 状态标签 */
.push-status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.push-status-tag.sent { background: var(--state-success-surface); color: var(--state-success); }
.push-status-tag.pending { background: #fff4e5; color: var(--state-warning); }
.push-status-tag.cancelled { background: var(--background-200); color: var(--text-400); }

/* 元信息 */
.push-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  flex-wrap: wrap;
}
.push-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--text-500);
  white-space: nowrap;
}

/* 数据指标 */
.push-data-row {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}
.push-data-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.push-data-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.push-data-label {
  font-size: 12px;
  color: var(--text-400);
}

/* 操作按钮 */
.push-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  flex-wrap: wrap;
}
.push-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-600);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: all 0.2s ease;
}
.push-action-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}
.push-action-primary {
  background: var(--primary);
  color: var(--background-50);
  border-color: var(--primary);
}
.push-action-primary:hover {
  filter: brightness(0.96);
}
.push-action-danger {
  color: var(--state-error);
  border-color: var(--state-error);
}
.push-action-danger:hover {
  background: var(--state-error);
  color: var(--state-error-foreground);
}

/* ===== 定时推送折叠 ===== */
.collapsible-section {
  border-radius: var(--radius);
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
  transition: border-color 0.2s ease;
}
.collapsible-section:hover {
  border-color: var(--primary);
}
.collapsible-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 16px 20px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-family: var(--font-sans);
}
.collapsible-trigger:hover {
  background: var(--accent);
}
.collapsible-trigger-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.collapsible-trigger-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-800);
}
.collapsible-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--state-error);
  color: var(--background-50);
  font-size: 12px;
  font-weight: 700;
}
.chevron-icon {
  color: var(--text-400);
  transition: transform 0.3s ease;
}
.collapsible-trigger.expanded .chevron-icon {
  transform: rotate(180deg);
}
.collapsible-content {
  display: none;
  padding: 0 20px 20px;
}
.collapsible-content.show {
  display: block;
}
.scheduled-empty {
  padding: 20px 0;
  text-align: center;
  color: var(--text-400);
  font-size: 13px;
}

/* 定时推送项 */
.scheduled-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}
.scheduled-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.scheduled-item:first-child {
  padding-top: 0;
}
.scheduled-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: calc(var(--radius) * 0.6);
  flex-shrink: 0;
}
.scheduled-blue { background: var(--brand-50); color: var(--brand-500); }
.scheduled-orange { background: #fff4e5; color: var(--state-warning); }
.scheduled-purple { background: #f0e6ff; color: #5856d6; }
.scheduled-info {
  flex: 1;
  min-width: 0;
}
.scheduled-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.scheduled-time {
  font-size: 13px;
  color: var(--text-500);
  margin-top: 2px;
}
.scheduled-cancel {
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--state-error);
  background: var(--card);
  color: var(--state-error);
  font-size: 12px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  flex-shrink: 0;
}
.scheduled-cancel:hover {
  background: var(--state-error);
  color: var(--state-error-foreground);
}
</style>
