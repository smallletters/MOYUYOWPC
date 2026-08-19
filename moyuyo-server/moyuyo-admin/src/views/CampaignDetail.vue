<template>
  <div class="page-wrapper">
    <!-- 页面标题区 -->
    <div class="page-title-area">
      <h1>活动详情</h1>
      <p>查看营销活动的完整信息、状态与基础指标</p>
    </div>

    <!-- 加载失败 / 活动不存在 -->
    <div v-if="loadError" class="error-banner">
      <div class="error-banner-inner">
        <span class="error-banner-icon">⚠️</span>
        <div>
          <strong class="error-banner-title">活动加载失败：</strong>
          <span class="error-banner-msg">{{ loadError }}</span>
        </div>
      </div>
      <div class="error-banner-tip">
        可能原因：活动已被删除 / ID 无效 / 权限不足。请返回列表查看其他活动。
      </div>
    </div>

    <!-- 加载中 -->
    <div v-else-if="loading && !hasData" class="loading-state">
      <div class="loading-spinner"></div>
      <div>正在加载活动数据...</div>
    </div>

    <!-- 活动详情内容 -->
    <div v-else-if="hasData">
      <!-- 顶部状态横幅 -->
      <div class="campaign-banner" :style="bannerStyle">
        <div class="campaign-banner-left">
          <div class="campaign-icon">{{ campaign.icon || '活' }}</div>
          <div class="campaign-info">
            <h2 class="campaign-name">{{ campaign.name }}</h2>
            <div class="campaign-meta">
              <el-tag :type="statusTagType(campaign.status)" size="default" effect="dark">
                {{ statusLabel(campaign.status) }}
              </el-tag>
              <span class="campaign-type">{{ campaign.type }}</span>
            </div>
          </div>
        </div>
        <div class="campaign-banner-right">
          <button class="btn btn-outline" @click="handleBack">← 返回列表</button>
        </div>
      </div>

      <!-- KPI 4 列 -->
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">💰</span>
            <span class="kpi-card-label">活动 GMV</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney(campaign.gmv) }}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">👥</span>
            <span class="kpi-card-label">参与人数</span>
          </div>
          <div class="kpi-card-value">{{ campaign.participants ?? 0 }}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">💵</span>
            <span class="kpi-card-label">预算 / 已花费</span>
          </div>
          <div class="kpi-card-value">¥{{ formatMoney(campaign.budget) }}</div>
          <div class="kpi-card-trend">
            <span class="kpi-trend-text">已花费 ¥{{ formatMoney(campaign.cost) }}</span>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">📊</span>
            <span class="kpi-card-label">ROI</span>
          </div>
          <div class="kpi-card-value">{{ campaign.roi ?? '-' }}</div>
        </div>
      </div>

      <!-- 基本信息卡片 -->
      <div class="detail-panel">
        <div class="detail-panel-header">
          <h3 class="detail-panel-title">活动基本信息</h3>
        </div>
        <div class="detail-panel-body">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="活动 ID">{{ campaign.id }}</el-descriptions-item>
            <el-descriptions-item label="活动名称">{{ campaign.name || '—' }}</el-descriptions-item>
            <el-descriptions-item label="活动类型">{{ campaign.type || '—' }}</el-descriptions-item>
            <el-descriptions-item label="活动状态">
              <el-tag :type="statusTagType(campaign.status)" size="small" effect="light">
                {{ statusLabel(campaign.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开始日期">{{ campaign.startDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="结束日期">{{ campaign.endDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(campaign.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="预算金额">¥{{ formatMoney(campaign.budget) }}</el-descriptions-item>
            <el-descriptions-item label="已花费">¥{{ formatMoney(campaign.cost) }}</el-descriptions-item>
            <el-descriptions-item label="参与人数">{{ campaign.participants ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="活动 GMV" :span="2">¥{{ formatMoney(campaign.gmv) }}</el-descriptions-item>
            <el-descriptions-item v-if="campaign.description" label="活动描述" :span="2">
              {{ campaign.description }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </div>

    <!-- 未加载到任何数据时的兜底空态 -->
    <div v-else class="empty-state">
      <div class="empty-state-icon">📭</div>
      <div class="empty-state-text">活动 ID: {{ campaignId }}</div>
      <button class="btn btn-outline" style="margin-top:12px" @click="handleBack">返回列表</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCampaignDetail } from '../api/admin'

const route = useRoute()
const router = useRouter()

// 活动详情数据
const campaign = reactive({
  id: '',
  name: '',
  type: '',
  status: '',
  description: '',
  startDate: '',
  endDate: '',
  participants: 0,
  gmv: 0,
  budget: 0,
  cost: 0,
  roi: '',
  createTime: ''
})

const loading = ref(false)
const loadError = ref('')
const campaignId = computed(() => route.query.id || route.params.id || '')

const hasData = computed(() => !!campaign.id)

// 顶部横幅渐变背景
const bannerStyle = computed(() => ({
  background: 'linear-gradient(135deg, #ebf5ff 0%, #f0fdf4 100%)'
}))

// 状态 -> el-tag type
function statusTagType(status) {
  const map = {
    ACTIVE: 'success',
    UPCOMING: 'warning',
    PAUSED: 'info',
    ENDED: 'info',
    DRAFT: 'info'
  }
  return map[status] || 'info'
}

// 状态 -> 中文标签
function statusLabel(status) {
  const map = {
    ACTIVE: '进行中',
    UPCOMING: '即将开始',
    PAUSED: '已暂停',
    ENDED: '已结束',
    DRAFT: '草稿'
  }
  return map[status] || status || '未知'
}

// 金额格式化（保留两位小数 + 千分位）
function formatMoney(value) {
  if (value == null) return '0.00'
  const num = typeof value === 'number' ? value : Number(value)
  if (Number.isNaN(num)) return '0.00'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 时间格式化：后端格式 "yyyy-MM-dd HH:mm:ss" 或 ISO
function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').replace(/\..*$/, '') || '—'
}

// 加载活动详情
async function loadCampaign() {
  const id = campaignId.value
  if (!id) {
    loadError.value = '缺少活动 ID'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await getCampaignDetail(id)
    // 后端返回结构：{ campaign: { id, name, type, status, ... }, effects: { roi, ... } }
    // （CampaignDetailResponse 嵌套结构，不是扁平的 id/name）
    const data = res?.campaign || res
    if (data && data.id) {
      Object.assign(campaign, {
        id: data.id || '',
        name: data.name || '',
        type: data.type || '',
        status: data.status || '',
        description: data.description || '',
        startDate: data.startDate || '',
        endDate: data.endDate || '',
        participants: data.participants ?? 0,
        gmv: Number(data.gmv ?? data.revenue ?? 0),
        budget: Number(data.budget ?? 0),
        cost: Number(data.cost ?? 0),
        // roi 在 effects 子对象里；兼容扁平/嵌套两种返回结构
        roi: res?.effects?.roi ?? data.roi ?? '',
        createTime: data.createTime || ''
      })
    } else {
      // 接口成功但 data 为空（活动不存在）
      loadError.value = `活动「${id}」不存在或已被删除`
    }
  } catch (err) {
    console.error('加载活动详情失败:', err)
    const serverMsg = err?.response?.data?.message || err?.message || '未知错误'
    loadError.value = `${serverMsg}（ID: ${id}）`
  } finally {
    loading.value = false
  }
}

// 返回营销管理列表
function handleBack() {
  window.location.href = '/admin/marketing'
}

// 组件挂载 + 路由 id 变化时自动加载
onMounted(() => {
  if (campaignId.value) loadCampaign()
})

// 监听路由 id 变化：从一个活动详情跳到另一个活动详情时重新加载
watch(
  () => route.query.id,
  (newId) => {
    if (newId && newId !== campaign.id) loadCampaign()
  }
)
</script>

<style scoped>
.page-wrapper { padding: 20px; }

/* 错误横幅（与 UserProfile 错误状态保持一致风格） */
.error-banner {
  background: var(--state-error-surface);
  border: 1px solid rgba(255, 59, 48, 0.18);
  border-radius: var(--radius);
  padding: 18px 24px;
  margin-bottom: 20px;
}
.error-banner-inner {
  display: flex;
  align-items: center;
  gap: 10px;
}
.error-banner-icon {
  font-size: 18px;
}
.error-banner-title {
  color: var(--state-error);
  margin-right: 6px;
}
.error-banner-msg {
  color: var(--text-700);
}
.error-banner-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-400);
}

/* 加载中状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--text-400);
  font-size: 14px;
  gap: 16px;
}
.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--background-200);
  border-top-color: var(--brand-500);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 顶部活动横幅 */
.campaign-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px;
  border-radius: var(--radius);
  margin-bottom: 20px;
  border: 1px solid var(--border);
}
.campaign-banner-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.campaign-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: var(--brand-600);
  box-shadow: var(--shadow-xs);
}
.campaign-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 6px;
}
.campaign-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}
.campaign-type {
  font-size: 13px;
  color: var(--text-500);
  padding: 2px 8px;
  background: var(--background-200);
  border-radius: 4px;
}
.campaign-banner-right {
  display: flex;
  gap: 8px;
}

/* KPI 4 列 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 详情面板 */
.detail-panel {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
  margin-bottom: 20px;
}
.detail-panel-header {
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  background: var(--background-50);
}
.detail-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  margin: 0;
}
.detail-panel-body {
  padding: 18px;
}

/* 空态 */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-400);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.empty-state-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
.empty-state-text {
  font-size: 14px;
}
</style>