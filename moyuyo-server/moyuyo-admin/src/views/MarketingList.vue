<template>
  <div class="marketing-list">
    <div class="page-header">
      <h2 class="page-title">营销管理</h2>
      <button class="btn btn-primary" @click="router.push('/campaign')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        创建活动
      </button>
    </div>

    <!-- 核心模块入口卡片（含优惠券管理） -->
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

    <!-- 营销子模块快捷入口（补全 AdminLayout 侧边栏之外的所有营销模块） -->
    <div class="card sub-module-card">
      <div class="card-header">
        <h3>营销子模块</h3>
        <span class="card-subtitle">点击进入各模块管理</span>
      </div>
      <div class="card-body">
        <div class="sub-module-grid">
          <div
            v-for="sub in subModules"
            :key="sub.path"
            class="sub-module-item"
            @click="router.push(sub.path)"
          >
            <span class="sub-module-icon" :style="{ background: sub.bg }">{{ sub.icon }}</span>
            <span class="sub-module-label">{{ sub.label }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 活动卡片列表 -->
    <div class="card">
      <div class="card-header">
        <h3>进行中的活动</h3>
        <button class="btn btn-sm btn-primary" @click="handleQuickAction('campaign')">创建活动</button>
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
                <button class="btn btn-sm btn-outline" @click="handleDetail(cp)">查看详情</button>
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
import { useRouter } from 'vue-router'
import { getCampaigns, getCouponStats, getFlashSaleStats, getPointsStats } from '../api/admin'
import { ElMessage } from 'element-plus'
import { toArray } from '../utils/safeArray'

const router = useRouter()

// 模块路由映射
// 分销管理暂未独立成页，统一跳转到营销效果分析页的分销维度 Tab
const moduleRoutes = {
  '优惠券管理': '/coupon-manage',
  '秒杀活动': '/flash-sale-manage',
  '积分活动': '/points-manage',
  '分销管理': '/marketing-effect'
}

const modules = ref([
  { title: '优惠券管理', icon: '🎫', iconBg: '#ebf5ff', metrics: [{ value: '-', label: '活跃券' }, { value: '-', label: '即将过期' }, { value: '-', label: '总计' }] },
  { title: '秒杀活动', icon: '⚡', iconBg: '#fff4e5', metrics: [{ value: '-', label: '进行中' }, { value: '-', label: '参与' }, { value: '-', label: '转化率' }] },
  { title: '积分活动', icon: '⭐', iconBg: '#f0fdf4', metrics: [{ value: '-', label: '进行中' }, { value: '-', label: '已兑换' }, { value: '-', label: '积分价值' }] },
  { title: '分销管理', icon: '🔗', iconBg: '#fdf2f8', metrics: [{ value: '-', label: '分销商' }, { value: '-', label: '分销额' }, { value: '-', label: '佣金率' }] }
])

// 营销子模块快捷入口（覆盖 AdminLayout 侧边栏未收纳的营销域模块）
// 背景色统一为柔和色块，与模块卡片区分；点击直接路由跳转
const subModules = [
  { path: '/coupon-manage', label: '优惠券管理', icon: '🎫', bg: '#ebf5ff' },
  { path: '/flash-sale-manage', label: '秒杀管理', icon: '⚡', bg: '#fff4e5' },
  { path: '/points-manage', label: '积分管理', icon: '⭐', bg: '#f0fdf4' },
  { path: '/campaign', label: '活动创建', icon: '🎯', bg: '#fef3c7' },
  { path: '/campaign-detail', label: '活动详情', icon: '📋', bg: '#fef3c7' },
  { path: '/push-manage', label: '推送管理', icon: '📨', bg: '#ede9fe' },
  { path: '/sms', label: '短信营销', icon: '💬', bg: '#dbeafe' },
  { path: '/live-manage', label: '直播管理', icon: '🎥', bg: '#fee2e2' },
  { path: '/marketing-effect', label: '营销效果', icon: '📊', bg: '#f0fdf4' },
  { path: '/ab-test', label: 'A/B 测试', icon: '🧪', bg: '#fce7f3' }
]

const campaigns = ref([])

// 活动状态映射：后端状态 -> 卡片标签与样式
const CAMPAIGN_STATUS_MAP = {
  ACTIVE: { label: '进行中', cls: 'tag-success' },
  PENDING: { label: '即将开始', cls: 'tag-warning' },
  PAUSED: { label: '已暂停', cls: 'tag-info' },
  ENDED: { label: '已结束', cls: 'tag-info' }
}

// 将后端活动字段映射为卡片展示结构
function mapCampaign(raw) {
  const st = CAMPAIGN_STATUS_MAP[raw.status] || { label: raw.status || '未知', cls: 'tag-info' }
  const gmv = Number(raw.gmv ?? raw.revenue ?? 0)
  const roi = raw.effects?.roi ?? raw.roi
  return {
    ...raw,
    // 横幅使用类型首字作为占位图标，配合柔和底色
    icon: (raw.type || raw.name || '活').toString().charAt(0),
    bg: 'linear-gradient(135deg, #ebf5ff 0%, #f0fdf4 100%)',
    statusLabel: st.label,
    statusClass: st.cls,
    participants: raw.participants ?? 0,
    revenue: `$${gmv.toLocaleString('en-US', { minimumFractionDigits: 0 })}`,
    roi: roi != null ? String(roi) : '-',
    dateRange: raw.startDate && raw.endDate ? `${raw.startDate} ~ ${raw.endDate}` : ''
  }
}

// 获取活动列表
async function fetchCampaigns() {
  try {
    const res = await getCampaigns()
    campaigns.value = toArray(res).map(mapCampaign)
  } catch (err) {
    console.error('获取活动列表失败:', err)
  }
}

// 从各子模块 API 获取模块指标数据
async function fetchModules() {
  try {
    // 并行请求三个模块的统计数据
    const [couponRes, flashSaleRes, pointsRes] = await Promise.allSettled([
      getCouponStats(),
      getFlashSaleStats(),
      getPointsStats()
    ])

    // 更新优惠券管理指标
    if (couponRes.status === 'fulfilled' && couponRes.value) {
      const data = couponRes.value
      modules.value[0].metrics = [
        { value: String(data.activeCount || data.active || '-'), label: '活跃券' },
        { value: String(data.expiringCount || data.expiring || '-'), label: '即将过期' },
        { value: String(data.totalCount || data.total || '-'), label: '总计' }
      ]
    }

    // 更新秒杀活动指标
    if (flashSaleRes.status === 'fulfilled' && flashSaleRes.value) {
      const data = flashSaleRes.value
      modules.value[1].metrics = [
        { value: String(data.activeCount || data.active || '-'), label: '进行中' },
        { value: String(data.participants || data.joinCount || '-'), label: '参与' },
        { value: String(data.conversionRate != null ? data.conversionRate + '%' : '-'), label: '转化率' }
      ]
    }

    // 更新积分活动指标
    if (pointsRes.status === 'fulfilled' && pointsRes.value) {
      const data = pointsRes.value
      modules.value[2].metrics = [
        { value: String(data.activeCount || data.active || '-'), label: '进行中' },
        { value: String(data.exchangedCount || data.exchanged || '-'), label: '已兑换' },
        { value: String(data.pointValue || data.value || '-'), label: '积分价值' }
      ]
    }
  } catch (err) {
    console.error('获取模块指标数据失败:', err)
  }
}

function handleModuleClick(mod) {
  const route = moduleRoutes[mod.title] || '/marketing'
  router.push(route)
}

// 活动详情：使用 window.location.href 强制整页跳转，避免 SPA chunk 缓存导致详情页组件未加载
function handleDetail(campaign) {
  if (!campaign || !campaign.id) {
    ElMessage.warning('活动数据异常，缺少 ID')
    return
  }
  window.location.href = `/admin/campaign-detail?id=${campaign.id}`
}

function handleQuickAction(action) {
  const actionRoutes = { coupon: '/coupon-manage', campaign: '/campaign', notification: '/push-manage' }
  router.push(actionRoutes[action] || '/marketing')
}

onMounted(() => {
  fetchCampaigns()
  fetchModules()
})
</script>

<style scoped lang="css">
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
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

/* 营销子模块快捷入口 */
.sub-module-card {
  margin-bottom: 24px;
}

.sub-module-card .card-subtitle {
  font-size: 12px;
  color: var(--text-400);
}

.sub-module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.sub-module-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.15s ease;
  background: var(--card);
}

.sub-module-item:hover {
  border-color: var(--primary);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.sub-module-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.sub-module-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-700);
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
