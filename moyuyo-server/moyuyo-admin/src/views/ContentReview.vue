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
        @click="activeMode = mode.key"
      >
        {{ mode.label }}
      </button>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card pending">
        <div class="stat-value">23</div>
        <div class="stat-label">待审核</div>
      </div>
      <div class="stat-card sla">
        <div class="stat-value">4h</div>
        <div class="stat-label">SLA剩余</div>
      </div>
      <div class="stat-card done">
        <div class="stat-value">156</div>
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
        @click="activeTab = tab.key"
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
import { ElMessage } from 'element-plus'
import { getReviewList, approveReview } from '../api/admin'

const activeMode = ref('auto_manual')
const activeTab = ref('all')

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
  { label: '07/10', pass: 80, reject: 20 },
  { label: '07/11', pass: 75, reject: 25 },
  { label: '07/12', pass: 90, reject: 10 },
  { label: '07/13', pass: 70, reject: 30 },
  { label: '07/14', pass: 85, reject: 15 },
  { label: '07/15', pass: 78, reject: 22 },
  { label: '07/16', pass: 82, reject: 18 }
])

// 加载待审核内容列表
async function loadReviewItems() {
  try {
    const res = await getReviewList()
    const records = res.records || res || []
      // 映射为前端需要的格式
      reviewItems.value = records.map((item, index) => ({
        id: item.id,
        thumb: item.contentType === '视频' ? '🎬' : item.contentType === '图片' ? '📷' : '📝',
        contentType: item.contentType || (item.rating ? '评论' : '图文'),
        contentTypeClass: item.contentType === '视频' ? 'tag-orange' : item.contentType === '图片' ? 'tag-blue' : 'tag-green',
        autoResult: item.auditStatus || '待审核',
        description: item.content || item.title || '',
        publisher: item.userName || item.submitter || '用户',
        submitTime: item.submitTime || item.createTime || ''
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
      await approveReview(id)
    }
    ElMessage.success(`内容 #${id} ${actionLabels[action]}`)
    await loadReviewItems()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

onMounted(() => { loadReviewItems() })
</script>

<style scoped lang="css">
.page-title {
  font-size: 20px;
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
