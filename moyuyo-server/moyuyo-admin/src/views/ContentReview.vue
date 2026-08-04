<template>
  <div class="content-review">
    <h2 class="page-title">内容审核</h2>

    <!-- 审核模式切换 -->
    <div class="mode-switcher">
      <button
        v-for="mode in reviewModes"
        :key="mode.key"
        class="mode-chip"
        :class="{ active: activeMode === mode.key }"
        @click="switchMode(mode.key)"
      >
        {{ mode.label }}
      </button>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card pending">
        <div class="stat-value">{{ reviewStats.pending }}</div>
        <div class="stat-label">待审核</div>
      </div>
      <div class="stat-card sla">
        <div class="stat-value">{{ reviewStats.slaRemaining }}</div>
        <div class="stat-label">SLA剩余</div>
      </div>
      <div class="stat-card done">
        <div class="stat-value">{{ reviewStats.todayReviewed }}</div>
        <div class="stat-label">今日已审</div>
      </div>
    </div>

    <!-- 违规类型标签 -->
    <div class="tab-switcher">
      <button
        v-for="tab in violationTabs"
        :key="tab.key"
        class="tab-switcher-item"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 审核卡片列表 -->
    <div class="review-list">
      <div class="review-card" v-for="item in reviewItems" :key="item.id">
        <div class="review-thumb">
          {{ item.thumb }}
        </div>
        <div class="review-body">
          <div class="review-top">
            <div class="review-tags">
              <span class="tag" :class="item.contentTypeClass">{{ item.contentType }}</span>
              <span class="tag tag-red" v-if="item.autoResult === '违规'">{{ item.autoResult }}</span>
              <span class="tag tag-green" v-else-if="item.autoResult === '通过'">{{ item.autoResult }}</span>
              <span class="tag tag-gray" v-else>{{ item.autoResult }}</span>
            </div>
            <span class="review-time">{{ item.submitTime }}</span>
          </div>
          <div class="review-desc">{{ item.description }}</div>
          <div class="review-publisher">
            <div class="user-info-cell">
              <div class="user-avatar">{{ item.publisher.charAt(0) }}</div>
              <span>{{ item.publisher }}</span>
            </div>
          </div>
          <div class="review-actions">
            <button class="btn btn-sm btn-primary" @click="handleReview(item.id, 'approve')">通过</button>
            <button class="btn btn-sm btn-outline" @click="handleReview(item.id, 'hide')">隐藏</button>
            <button class="btn btn-sm btn-outline" @click="handleReview(item.id, 'delete')">删除</button>
            <button class="btn btn-sm btn-danger" @click="handleReview(item.id, 'ban')">封禁</button>
            <button class="btn btn-sm btn-outline" @click="goDetail(item.id)">详情</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 审核趋势图 -->
    <div class="card">
      <div class="card-header">
        <h3>近7日审核趋势</h3>
      </div>
      <div class="card-body">
        <div class="trend-chart">
          <div class="bar-group" v-for="(day, idx) in trendData" :key="idx">
            <div class="bar-stack">
              <div class="bar bar-pass" :style="{ height: day.pass + '%' }"></div>
              <div class="bar bar-reject" :style="{ height: day.reject + '%' }"></div>
            </div>
            <div class="bar-label">{{ day.label }}</div>
          </div>
        </div>
        <div class="chart-legend">
          <span class="legend-item"><span class="legend-dot pass"></span>通过</span>
          <span class="legend-item"><span class="legend-dot reject"></span>违规</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getContentReviewList, approveContentReview, getContentReviewDetail, getContentReviewStats, getContentReviewTrend, hideContentReview, deleteContentReview, banContentReview } from '../api/admin'
import { toArray } from '../utils/safeArray'

const router = useRouter()

const activeMode = ref('auto_manual')
const activeTab = ref('all')

// 审核统计数据
const reviewStats = ref({
  pending: 0,
  slaRemaining: '0h',
  todayReviewed: 0
})

const reviewModes = [
  { key: 'auto', label: '机审自动通过' },
  { key: 'auto_manual', label: '机审+人审' },
  { key: 'manual', label: '纯人审' }
]

const violationTabs = [
  { key: 'all', label: '全部' },
  { key: 'porn', label: '色情' },
  { key: 'violence', label: '暴力' },
  { key: 'hate', label: '仇恨言论' },
  { key: 'infringement', label: '侵权' },
  { key: 'misinfo', label: '虚假信息' },
  { key: 'abuse', label: '虐待动物' }
]

const reviewItems = ref([])

const trendData = reactive([
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 }
])

// 切换审核模式
function switchMode(mode) {
  activeMode.value = mode
  loadReviewItems()
}

// 切换违规类型标签
function switchTab(tab) {
  activeTab.value = tab
  loadReviewItems()
}

// 加载审核统计
async function loadReviewStats() {
  try {
    const res = await getContentReviewStats()
    if (res) {
      reviewStats.value = {
        pending: res.pending || 0,
        slaRemaining: res.slaRemaining || '0h',
        todayReviewed: res.todayReviewed || 0
      }
    }
  } catch (e) {
    console.error('获取审核统计失败', e)
  }
}

// 加载审核趋势
async function loadReviewTrend() {
  try {
    const res = await getContentReviewTrend({ days: 7 })
    const trendList = toArray(res)
    if (trendList.length > 0) {
      trendData.length = 0
      trendData.push(...trendList.map(d => ({
        label: d.label || d.date || '...',
        pass: d.pass || d.approve || 0,
        reject: d.reject || d.violation || 0
      })))
    }
  } catch (e) {
    console.error('获取审核趋势失败', e)
  }
}

// 加载待审核内容列表
async function loadReviewItems() {
  try {
    const params = {}
    // 传递审核模式参数
    if (activeMode.value) params.mode = activeMode.value
    // 传递违规类型参数
    if (activeTab.value !== 'all') params.contentType = activeTab.value
    const res = await getContentReviewList(params)
    const records = toArray(res)
      // 映射为前端需要的格式
      reviewItems.value = records.map((item, index) => ({
        id: item.id,
        thumb: item.contentType === '视频' ? '🎬' : item.contentType === '图片' ? '📷' : '📝',
        contentType: item.contentType || (item.rating ? '评论' : '图文'),
        contentTypeClass: item.contentType === '视频' ? 'tag-orange' : item.contentType === '图片' ? 'tag-blue' : 'tag-green',
        autoResult: item.status || '待审核',
        description: item.contentExcerpt || '',
        publisher: '用户' + (item.userId || ''),
        submitTime: item.reviewTime || item.createTime || ''
      }))
  } catch (e) {
    ElMessage.error('获取审核内容失败')
  }
}

async function handleReview(id, action) {
  const actionLabels = {
    approve: '已通过',
    hide: '已隐藏',
    delete: '已删除',
    ban: '已封禁'
  }
  try {
    if (action === 'approve') {
      await approveContentReview(id)
    } else if (action === 'hide') {
      await hideContentReview(id)
    } else if (action === 'delete') {
      await deleteContentReview(id)
    } else if (action === 'ban') {
      await banContentReview(id)
    }
    ElMessage.success(`内容 #${id} ${actionLabels[action]}`)
    await loadReviewItems()
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.message || '未知错误'))
  }
}

// 跳转到审核详情页
function goDetail(id) {
  router.push(`/content-review-detail/${id}`)
}

onMounted(() => {
  loadReviewStats()
  loadReviewTrend()
  loadReviewItems()
})
</script>

<style scoped lang="css">
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 20px;
}

/* 审核模式 */
.mode-switcher {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.mode-chip {
  padding: 6px 18px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  color: var(--text-600);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.mode-chip.active {
  background: var(--primary);
  color: #fff;
  border-color: var(--primary);
}

.mode-chip:hover:not(.active) {
  border-color: var(--primary);
  color: var(--primary);
}

/* 统计 */
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  flex: 1;
  padding: 20px;
  border-radius: var(--radius);
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: var(--text-400);
}

.stat-card.pending .stat-value { color: var(--state-warning); }
.stat-card.sla .stat-value { color: var(--primary); }
.stat-card.done .stat-value { color: var(--state-success); }

/* 审核卡片 */
.review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.review-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.review-thumb {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  background: var(--background-200);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
}

.review-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-tags {
  display: flex;
  gap: 6px;
}

.review-time {
  font-size: 12px;
  color: var(--text-400);
}

.review-desc {
  font-size: 13px;
  color: var(--text-600);
  line-height: 1.5;
}

.review-publisher {
  font-size: 12px;
  color: var(--text-500);
}

.review-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

/* 趋势图 */
.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 160px;
  padding: 0 8px;
}

.bar-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.bar-stack {
  width: 32px;
  height: 120px;
  display: flex;
  flex-direction: column-reverse;
  gap: 2px;
  border-radius: 4px;
  background: var(--background-100);
  overflow: hidden;
}

.bar {
  width: 100%;
  border-radius: 2px;
  transition: height 0.3s ease;
}

.bar-pass {
  background: var(--state-success);
}

.bar-reject {
  background: var(--state-error);
}

.chart-legend {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-500);
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.legend-dot.pass { background: var(--state-success); }
.legend-dot.reject { background: var(--state-error); }
</style>
