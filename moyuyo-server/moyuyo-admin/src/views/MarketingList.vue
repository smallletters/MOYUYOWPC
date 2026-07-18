<template>
  <div class="marketing-list">
    <h2 class="page-title">营销管理</h2>

    <!-- 模块入口卡片 -->
    <div class="module-grid">
      <div class="module-card" v-for="mod in modules" :key="mod.title" @click="handleModuleClick(mod)">
        <div class="module-icon" :style="{ background: mod.iconBg }">{{ mod.icon }}</div>
        <div class="module-info">
          <h3>{{ mod.title }}</h3>
          <div class="module-metrics">
            <span v-for="(metric, idx) in mod.metrics" :key="idx" class="metric-item">
              <strong>{{ metric.value }}</strong>{{ metric.label }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 活动卡片列表 -->
    <div class="card">
      <div class="card-header">
        <h3>进行中的活动</h3>
        <button class="btn btn-sm btn-primary">创建活动</button>
      </div>
      <div class="card-body">
        <div class="campaign-list">
          <div class="campaign-card" v-for="cp in campaigns" :key="cp.id">
            <div class="campaign-banner" :style="{ background: cp.bg }">
              <span class="campaign-banner-text">{{ cp.icon }}</span>
            </div>
            <div class="campaign-body">
              <div class="campaign-header">
                <h4>{{ cp.name }}</h4>
                <span class="tag" :class="cp.statusClass">{{ cp.statusLabel }}</span>
              </div>
              <div class="campaign-metrics">
                <div class="campaign-metric">
                  <span class="metric-value">{{ cp.participants }}</span>
                  <span class="metric-label">参与人数</span>
                </div>
                <div class="campaign-metric">
                  <span class="metric-value">{{ cp.revenue }}</span>
                  <span class="metric-label">带动销售额</span>
                </div>
                <div class="campaign-metric">
                  <span class="metric-value">{{ cp.roi }}</span>
                  <span class="metric-label">ROI</span>
                </div>
              </div>
              <div class="campaign-footer">
                <span class="campaign-date">{{ cp.dateRange }}</span>
                <button class="btn btn-sm btn-outline">查看详情</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="quick-actions">
      <button class="btn btn-outline" @click="handleQuickAction('coupon')">创建优惠券</button>
      <button class="btn btn-outline" @click="handleQuickAction('campaign')">创建活动</button>
      <button class="btn btn-outline" @click="handleQuickAction('notification')">推送通知</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCampaigns } from '../api/admin'
import { ElMessage } from 'element-plus'

const modules = ref([
  { title: '优惠券管理', icon: '🎫', iconBg: '#ebf5ff', metrics: [{ value: '-', label: '活跃券' }, { value: '-', label: '即将过期' }, { value: '-', label: '总计' }] },
  { title: '秒杀活动', icon: '⚡', iconBg: '#fff4e5', metrics: [{ value: '-', label: '进行中' }, { value: '-', label: '参与' }, { value: '-', label: '转化率' }] },
  { title: '积分活动', icon: '⭐', iconBg: '#f0fdf4', metrics: [{ value: '-', label: '进行中' }, { value: '-', label: '已兑换' }, { value: '-', label: '积分价值' }] },
  { title: '分销管理', icon: '🔗', iconBg: '#fdf2f8', metrics: [{ value: '-', label: '分销商' }, { value: '-', label: '分销额' }, { value: '-', label: '佣金率' }] }
])

const campaigns = ref([])

// 获取活动列表
async function fetchCampaigns() {
  try {
    const res = await getCampaigns()
    if (res) {
      campaigns.value = res.records || res.list || res
    }
  } catch (err) {
    console.error('获取活动列表失败:', err)
  }
}

// 获取模块数据（后端暂无独立模块端点，使用本地默认数据）
async function fetchModules() {
  // 预留：后端接入时替换为 api.get('/marketing/modules')
}

function handleModuleClick(mod) {
  ElMessage.info(`进入 ${mod.title}`)
}

function handleQuickAction(action) {
  const labels = { coupon: '创建优惠券', campaign: '创建活动', notification: '推送通知' }
  ElMessage.info(labels[action])
}

onMounted(() => {
  fetchCampaigns()
  fetchModules()
})
</script>

<style scoped lang="css">
.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 20px;
}

/* 模块入口 */
.module-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.module-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.18s ease;
  box-shadow: var(--shadow-xs);
}

.module-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.module-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.module-info h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  margin: 0 0 8px;
}

.module-metrics {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.metric-item {
  font-size: 12px;
  color: var(--text-400);
}

.metric-item strong {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 2px;
}

/* 活动列表 */
.campaign-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.campaign-card {
  display: flex;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  transition: box-shadow 0.18s ease;
}

.campaign-card:hover {
  box-shadow: var(--shadow-sm);
}

.campaign-banner {
  width: 120px;
  min-height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.campaign-banner-text {
  font-size: 36px;
}

.campaign-body {
  flex: 1;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.campaign-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.campaign-header h4 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-800);
  margin: 0;
}

.campaign-metrics {
  display: flex;
  gap: 24px;
}

.campaign-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-800);
}

.metric-label {
  font-size: 11px;
  color: var(--text-400);
}

.campaign-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.campaign-date {
  font-size: 12px;
  color: var(--text-400);
}

/* 快捷操作 */
.quick-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
</style>
