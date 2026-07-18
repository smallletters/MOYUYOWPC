<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">推送管理</h1>
        <p class="page-desc">管理 App Push、短信和邮件推送任务，追踪送达与转化数据</p>
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

    <!-- 快速新建入口 -->
    <section class="quick-entry-card">
      <div class="section-header">
        <span class="section-title">快速新建</span>
      </div>
      <div class="quick-entry-grid">
        <button class="quick-entry-item" @click="handleQuickCreate('app')">
          <div class="quick-entry-icon quick-entry-blue">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 2H7a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>
          </div>
          <span class="quick-entry-label">App 推送</span>
          <span class="quick-entry-desc">发送手机应用通知</span>
        </button>
        <button class="quick-entry-item" @click="handleQuickCreate('sms')">
          <div class="quick-entry-icon quick-entry-green">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          </div>
          <span class="quick-entry-label">短信推送</span>
          <span class="quick-entry-desc">发送手机短信通知</span>
        </button>
        <button class="quick-entry-item" @click="handleQuickCreate('email')">
          <div class="quick-entry-icon quick-entry-orange">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
          </div>
          <span class="quick-entry-label">邮件推送</span>
          <span class="quick-entry-desc">发送电子邮件通知</span>
        </button>
      </div>
    </section>

    <!-- 推送记录列表 -->
    <div class="section-header" style="margin: 24px 0 16px;">
      <span class="section-title">推送记录</span>
    </div>
    <div class="push-list">
      <div v-for="item in pushList" :key="item.id" class="push-record">
        <div class="push-record-header">
          <div class="push-record-title-area">
            <div class="push-record-title">{{ item.title }}</div>
            <div class="push-record-summary">{{ item.summary }}</div>
          </div>
          <span :class="['push-type-tag', item.typeClass]">{{ item.typeLabel }}</span>
        </div>
        <!-- 元信息 -->
        <div class="push-meta">
          <span :class="['push-status-tag', item.statusClass]">
            <span v-if="item.status === 'sent'" class="status-dot" style="background: var(--state-success)"></span>
            <span v-else-if="item.status === 'pending'" class="status-dot" style="background: var(--state-warning)"></span>
            <span v-else class="status-dot" style="background: var(--text-400)"></span>
            {{ item.statusLabel }}
          </span>
          <span class="push-meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            {{ item.time }}
          </span>
          <span class="push-meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            {{ item.targetLabel }}
          </span>
        </div>
        <!-- 数据指标 -->
        <div v-if="item.status === 'sent'" class="push-data-row">
          <div v-for="(data, di) in item.data" :key="di" class="push-data-item">
            <span class="push-data-value">{{ data.value }}</span>
            <span class="push-data-label">{{ data.label }}</span>
          </div>
        </div>
        <!-- 操作按钮 -->
        <div class="push-actions">
          <button class="push-action-btn push-action-primary" @click="handleView(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
            编辑
          </button>
          <button v-if="item.status === 'pending'" class="push-action-btn push-action-primary" @click="handleSend(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
            发送
          </button>
          <button v-if="item.status === 'pending'" class="push-action-btn push-action-danger" @click="handleCancel(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            取消
          </button>
          <button class="push-action-btn" @click="handleCopy(item)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
            复制
          </button>
          <button v-if="item.status !== 'sent'" class="push-action-btn push-action-danger" @click="handleDelete(item)">
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
        <div v-for="(item, idx) in scheduledList" :key="idx" class="scheduled-item">
          <div :class="['scheduled-icon', item.iconClass]">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          </div>
          <div class="scheduled-info">
            <div class="scheduled-title">{{ item.title }}</div>
            <div class="scheduled-time">{{ item.time }}</div>
          </div>
          <span class="scheduled-countdown">{{ item.countdown }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPushStats, getPushRecords, getPushScheduled, createPush, sendPush, cancelPush, deletePush } from '../api/admin'

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
    pushList.value = records
    // 填充定时推送列表
    const scheduled = (scheduledRes && scheduledRes.records) || scheduledRes || []
    scheduledList.value = scheduled
  } catch (e) {
    console.error('加载推送数据失败:', e)
    ElMessage.error('加载推送数据失败')
  }
}

function handleQuickCreate(type) {
  ElMessage.info(`快速新建: ${type}`)
}

async function handleView(item) {
  console.log('查看推送:', item.title)
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

function handleCopy(item) {
  ElMessage.success('推送已复制')
}

// 删除推送（调用API）
async function handleDelete(item) {
  try {
    ElMessageBox.confirm('确定删除该推送？', '提示', { type: 'warning' }).then(async () => {
      await deletePush(item.id)
      ElMessage.success('删除成功')
      loadData()
    }).catch(() => {})
  } catch (e) {
    console.error('删除推送失败:', e)
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

/* ===== 推送记录列表 ===== */
.push-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
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
.scheduled-countdown {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 999px;
  background: #fff4e5;
  color: var(--state-warning);
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  flex-shrink: 0;
}
</style>
