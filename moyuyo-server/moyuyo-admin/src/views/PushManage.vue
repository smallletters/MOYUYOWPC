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
        <div class="stat-value">3,482</div>
        <div class="stat-trend stat-trend-up">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          +12.5%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本月推送</div>
        <div class="stat-value">58,247</div>
        <div class="stat-trend stat-trend-up">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          +8.3%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">推送成功率</div>
        <div class="stat-value">96.8<span class="stat-unit">%</span></div>
        <div class="stat-trend stat-trend-up">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          +0.3%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">订阅用户数</div>
        <div class="stat-value">24.6K</div>
        <div class="stat-trend stat-trend-up">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          +5.2%
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
import { ref } from 'vue'

const scheduledExpanded = ref(false)

const pushList = ref([
  {
    id: 1,
    title: '618 年中大促即将开始',
    summary: '精选宠物好物低至5折，限时抢购即将开始，千万别错过！',
    typeLabel: 'App Push',
    typeClass: 'app-push',
    status: 'sent',
    statusLabel: '已发送',
    statusClass: 'sent',
    time: '06-18 10:00',
    targetLabel: '全部用户',
    data: [
      { label: '送达', value: '52,136' },
      { label: '到达率', value: '96.8%' },
      { label: '打开', value: '12,834' },
      { label: '点击', value: '4,217' }
    ]
  },
  {
    id: 2,
    title: '新品到货通知',
    summary: '您关注的商品已到货，快来选购吧！限量库存先到先得。',
    typeLabel: '短信',
    typeClass: 'sms',
    status: 'pending',
    statusLabel: '待发送',
    statusClass: 'pending',
    time: '定时 06-20 18:00',
    targetLabel: '新用户',
    data: []
  },
  {
    id: 3,
    title: '六月会员权益报告',
    summary: '您的专属会员月度报告已生成，查看本月消费明细与专属优惠。',
    typeLabel: '邮件',
    typeClass: 'email',
    status: 'sent',
    statusLabel: '已发送',
    statusClass: 'sent',
    time: '06-15 09:00',
    targetLabel: 'VIP',
    data: [
      { label: '送达', value: '8,421' },
      { label: '到达率', value: '98.2%' },
      { label: '打开', value: '2,109' },
      { label: '点击', value: '893' }
    ]
  },
  {
    id: 4,
    title: '系统维护通知',
    summary: '尊敬的用户，系统将于今晚进行升级维护，预计持续2小时。',
    typeLabel: 'App Push',
    typeClass: 'app-push',
    status: 'cancelled',
    statusLabel: '已取消',
    statusClass: 'cancelled',
    time: '定时 06-14 22:00',
    targetLabel: '自定义',
    data: []
  },
  {
    id: 5,
    title: '新用户注册礼包',
    summary: '欢迎加入！您的新用户注册礼包已到账，登录即可领取。',
    typeLabel: '邮件',
    typeClass: 'email',
    status: 'sent',
    statusLabel: '已发送',
    statusClass: 'sent',
    time: '06-13 08:30',
    targetLabel: '新注册用户',
    data: [
      { label: '送达', value: '4,823' },
      { label: '到达率', value: '97.5%' },
      { label: '打开', value: '3,211' },
      { label: '点击', value: '1,876' }
    ]
  }
])

const scheduledList = ref([
  { title: '新品到货通知', time: '发送时间：06-20 18:00', countdown: '2天 3小时', iconClass: 'scheduled-blue' },
  { title: '周末促销邮件', time: '发送时间：06-21 09:00', countdown: '3天 18小时', iconClass: 'scheduled-orange' },
  { title: '618 回顾推送', time: '发送时间：06-22 10:00', countdown: '4天 19小时', iconClass: 'scheduled-purple' }
])

function handleQuickCreate(type) {
  console.log('快速新建:', type)
}

function handleView(item) {
  console.log('查看推送:', item.title)
}

function handleSend(item) {
  console.log('发送推送:', item.title)
}

function handleCancel(item) {
  console.log('取消推送:', item.title)
}

function handleCopy(item) {
  console.log('复制推送:', item.title)
}

function handleDelete(item) {
  console.log('删除推送:', item.title)
}
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
