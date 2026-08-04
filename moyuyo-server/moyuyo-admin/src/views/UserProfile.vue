<template>
  <div class="page-wrapper">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>用户画像</h1>
      <p>输入用户 ID 或用户名，查看用户基本信息、标签与行为数据</p>
    </div>

    <!-- 查询面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>用户名 / ID</label>
          <input v-model="searchForm.keyword" type="text" placeholder="请输入用户名或ID" @keyup.enter="handleSearch" />
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" :disabled="loading" @click="handleSearch">搜索</button>
          <button class="btn btn-outline" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 用户画像卡 -->
    <div class="profile-card" v-if="hasData">
      <div class="profile-head">
        <div class="profile-avatar">{{ avatarText }}</div>
        <div class="profile-main">
          <div class="profile-name">
            {{ userInfo.nickname !== '-' ? userInfo.nickname : userInfo.username }}
            <span class="badge" :class="levelBadgeClass(userInfo.level)">{{ userInfo.level }}</span>
          </div>
          <div class="profile-sub">ID: {{ userInfo.userId }} · 注册于 {{ userInfo.registerTime }}</div>
        </div>
        <div class="profile-spent">
          <div class="profile-spent-label">累计消费</div>
          <div class="profile-spent-value">{{ userInfo.totalSpent }}</div>
        </div>
      </div>
      <div class="info-grid">
        <div class="info-item"><span class="info-label">用户名</span><span class="info-value">{{ userInfo.username }}</span></div>
        <div class="info-item"><span class="info-label">手机号</span><span class="info-value">{{ userInfo.phone }}</span></div>
        <div class="info-item"><span class="info-label">邮箱</span><span class="info-value">{{ userInfo.email }}</span></div>
        <div class="info-item">
          <span class="info-label">用户标签</span>
          <span class="info-value">
            <span v-for="tag in userInfo.tags" :key="tag" class="tag tag-blue" style="margin-right:4px">{{ tag }}</span>
            <span v-if="userInfo.tags.length === 0" style="color:var(--text-400);font-size:12px;">暂无标签</span>
          </span>
        </div>
      </div>
    </div>
    <div v-else class="empty-state" style="background:var(--card);border:1px solid var(--border);border-radius:var(--radius);">
      <div class="empty-state-icon">👤</div>
      <div class="empty-state-text">输入用户 ID 或用户名，点击「搜索」查看画像</div>
    </div>

    <!-- 行为数据 -->
    <div v-if="hasData" style="margin-top:20px">
      <div class="section-title">用户行为数据</div>
      <div class="data-table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>行为类型</th>
              <th>次数</th>
              <th>最近时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in behaviorData" :key="item.type">
              <td>
                <span class="tag tag-blue">{{ item.type }}</span>
              </td>
              <td class="count-cell">{{ item.count }}</td>
              <td class="time-cell">{{ item.lastTime }}</td>
            </tr>
            <tr v-if="behaviorData.length === 0">
              <td colspan="3">
                <div class="empty-state">
                  <div class="empty-state-icon">📊</div>
                  <div class="empty-state-text">暂无行为数据</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 画像分析区块（补齐设计稿图表，暂无真实接口，数据为前端示例数据） -->
    <div v-if="hasData" class="charts-area">
      <!-- 第一行：消费趋势 + 用户标签 -->
      <div class="charts-row">
        <!-- 消费趋势：CSS 柱状图（按月） -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">消费趋势（近 12 个月）</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="trend-chart">
            <div v-for="d in spendTrend" :key="d.month" class="trend-chart__group">
              <span class="trend-chart__value">{{ fmtMoney(d.amount) }}</span>
              <div
                class="trend-chart__bar"
                :style="{ height: barHeight(d.amount) + '%' }"
                :title="d.month + ' ' + fmtMoney(d.amount)"
              ></div>
              <span class="trend-chart__label">{{ d.month }}</span>
            </div>
          </div>
        </section>

        <!-- 用户标签：el-tag 标签云 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">用户标签</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="tag-cloud">
            <el-tag
              v-for="(t, i) in userTags"
              :key="t.label"
              :type="tagType(i)"
              effect="light"
              round
              class="tag-cloud__item"
            >
              {{ t.label }}
            </el-tag>
          </div>
          <div class="tag-cloud__sub">基于近 90 天行为自动打标，权重越高代表特征越显著</div>
        </section>
      </div>

      <!-- 第二行：品类偏好 + 行为漏斗 -->
      <div class="charts-row">
        <!-- 用户偏好分布：品类偏好分段条形图 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">用户偏好分布（品类）</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="stacked-bar">
            <div class="stacked-bar__track">
              <div
                v-for="cat in categoryPrefs"
                :key="cat.name"
                class="stacked-bar__seg"
                :style="{ width: cat.value + '%', background: cat.color }"
                :title="cat.name + ' ' + cat.value + '%'"
              ></div>
            </div>
          </div>
          <div class="stacked-legend">
            <span v-for="cat in categoryPrefs" :key="cat.name" class="stacked-legend__item">
              <i class="stacked-legend__dot" :style="{ background: cat.color }"></i>
              {{ cat.name }} {{ cat.value }}%
            </span>
          </div>
        </section>

        <!-- 行为漏斗：浏览→加购→下单→支付 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">行为漏斗</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="funnel">
            <div v-for="stage in funnelStages" :key="stage.name" class="funnel__item">
              <div class="funnel__bar" :style="{ width: stage.rate + '%' }">
                <span class="funnel__name">{{ stage.name }}</span>
                <span class="funnel__meta">{{ stage.value.toLocaleString() }} 次 · 转化率 {{ stage.rate }}%</span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <!-- 第三行：活跃时段 + 设备/渠道分布 -->
      <div class="charts-row">
        <!-- 活跃时段分布：水平条形图 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">活跃时段分布</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="dist-bar" v-for="h in activeHours" :key="h.hour">
            <span class="dist-bar__label">{{ h.hour }}</span>
            <div class="dist-bar__track">
              <div class="dist-bar__fill" :style="{ width: activePct(h.value) + '%' }"></div>
            </div>
            <span class="dist-bar__value">{{ h.value }}</span>
          </div>
        </section>

        <!-- 设备/渠道分布：分段条 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">设备 / 渠道分布</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="sub-dist">
            <div class="sub-dist__label">设备分布</div>
            <div class="stacked-bar">
              <div class="stacked-bar__track">
                <div
                  v-for="d in deviceDist"
                  :key="d.name"
                  class="stacked-bar__seg"
                  :style="{ width: d.value + '%', background: d.color }"
                  :title="d.name + ' ' + d.value + '%'"
                ></div>
              </div>
            </div>
            <div class="stacked-legend">
              <span v-for="d in deviceDist" :key="d.name" class="stacked-legend__item">
                <i class="stacked-legend__dot" :style="{ background: d.color }"></i>
                {{ d.name }} {{ d.value }}%
              </span>
            </div>
          </div>
          <div class="sub-dist">
            <div class="sub-dist__label">渠道分布</div>
            <div class="stacked-bar">
              <div class="stacked-bar__track">
                <div
                  v-for="c in channelDist"
                  :key="c.name"
                  class="stacked-bar__seg"
                  :style="{ width: c.value + '%', background: c.color }"
                  :title="c.name + ' ' + c.value + '%'"
                ></div>
              </div>
            </div>
            <div class="stacked-legend">
              <span v-for="c in channelDist" :key="c.name" class="stacked-legend__item">
                <i class="stacked-legend__dot" :style="{ background: c.color }"></i>
                {{ c.name }} {{ c.value }}%
              </span>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserProfile, getUserBehaviors } from '../api/admin'
import { toArray } from '../utils/safeArray'

const searchForm = reactive({ keyword: '' })

// 用户基本信息
const userInfo = reactive({
  userId: '-',
  username: '-',
  nickname: '-',
  phone: '-',
  email: '-',
  registerTime: '-',
  level: '-',
  levelTag: '',
  totalSpent: '-',
  tags: []
})

// 用户行为数据
const behaviorData = ref([])

const loading = ref(false)

const hasData = computed(() => userInfo.userId !== '-')

// 头像首字母
const avatarText = computed(() => {
  const name = userInfo.nickname !== '-' ? userInfo.nickname : userInfo.username
  return (name || '?').charAt(0).toUpperCase()
})

// 会员等级徽章样式
function levelBadgeClass(level) {
  const lv = String(level || '')
  if (lv.includes('金')) return 'badge-gold'
  if (lv.includes('银')) return 'badge-silver'
  if (lv.includes('钻')) return 'badge-diamond'
  if (lv.includes('铂')) return 'badge-platinum'
  return 'badge-regular'
}

// 搜索用户
async function handleSearch() {
  const keyword = searchForm.keyword.trim()
  if (!keyword) {
    ElMessage.warning('请输入用户名或ID')
    return
  }
  loading.value = true
  try {
    const res = await getUserProfile(keyword)
    if (res) {
      Object.assign(userInfo, {
        userId: res.userId || res.id || '-',
        username: res.username || '-',
        nickname: res.nickname || '-',
        phone: res.phone || '-',
        email: res.email || '-',
        registerTime: res.registerTime || '-',
        level: res.level || '-',
        levelTag: res.levelTag || '',
        totalSpent: res.totalSpent || '-',
        tags: res.tags || []
      })
    }

    // 获取行为数据
    const behaviorRes = await getUserBehaviors(keyword)
    if (behaviorRes) {
      behaviorData.value = toArray(behaviorRes)
    }

    ElMessage.success('搜索完成')
  } catch (err) {
    console.error('获取用户数据失败:', err)
    ElMessage.error('未找到该用户或查询失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.keyword = ''
  Object.assign(userInfo, {
    userId: '-', username: '-', nickname: '-', phone: '-', email: '-',
    registerTime: '-', level: '-', levelTag: '', totalSpent: '-', tags: []
  })
  behaviorData.value = []
}

// ===== 画像分析区块数据（暂无真实 API，使用与设计稿一致形态的结构化示例数据） =====

// 消费趋势：近 12 个月消费金额（元），示例数据
const spendTrend = [
  { month: '1月', amount: 680 },
  { month: '2月', amount: 1200 },
  { month: '3月', amount: 980 },
  { month: '4月', amount: 1560 },
  { month: '5月', amount: 1320 },
  { month: '6月', amount: 2100 },
  { month: '7月', amount: 1880 },
  { month: '8月', amount: 2450 },
  { month: '9月', amount: 2200 },
  { month: '10月', amount: 2760 },
  { month: '11月', amount: 3100 },
  { month: '12月', amount: 2980 }
]

// 消费趋势最大值，用于计算柱高占比
const maxSpend = computed(() => Math.max(...spendTrend.map((d) => d.amount)))

// 计算消费趋势柱高百分比（保留最小高度保证可见）
function barHeight(amount) {
  return Math.max(4, Math.round((amount / maxSpend.value) * 100))
}

// 金额格式化
function fmtMoney(amount) {
  return '¥' + Number(amount).toLocaleString()
}

// 用户标签云（示例数据）：label 为标签名，weight 为特征权重（仅作展示区分）
const userTags = [
  { label: '高活跃', weight: 5 },
  { label: '猫奴', weight: 4 },
  { label: '深夜购', weight: 3 },
  { label: '价格敏感', weight: 3 },
  { label: '新品尝鲜', weight: 2 },
  { label: '复购达人', weight: 4 },
  { label: '跨品类', weight: 2 },
  { label: '内容互动', weight: 1 }
]

// el-tag 可选类型，按标签顺序循环取色
const TAG_TYPES = ['primary', 'success', 'warning', 'danger', 'info']
function tagType(index) {
  return TAG_TYPES[index % TAG_TYPES.length]
}

// 品类偏好（示例数据）：value 为该品类消费占比（%）
const categoryPrefs = [
  { name: '猫粮', value: 32, color: 'var(--brand-500)' },
  { name: '猫砂', value: 24, color: 'var(--brand-300)' },
  { name: '零食', value: 18, color: 'var(--state-success)' },
  { name: '玩具', value: 15, color: 'var(--state-warning)' },
  { name: '用品', value: 11, color: 'var(--state-error)' }
]

// 行为漏斗（示例数据）：浏览→加购→下单→支付，rate 为相对首环节的转化率（%）
const funnelStages = [
  { name: '浏览商品', value: 12000, rate: 100 },
  { name: '加入购物车', value: 4380, rate: 36.5 },
  { name: '提交订单', value: 2150, rate: 17.9 },
  { name: '支付成功', value: 1680, rate: 14 }
]

// 活跃时段分布（示例数据）：value 为对应时段的行为次数
const activeHours = [
  { hour: '0-4时', value: 32 },
  { hour: '4-8时', value: 18 },
  { hour: '8-12时', value: 45 },
  { hour: '12-16时', value: 88 },
  { hour: '16-20时', value: 96 },
  { hour: '20-24时', value: 120 }
]

// 活跃时段最大值，用于计算水平条宽度占比
const maxActive = computed(() => Math.max(...activeHours.map((d) => d.value)))

// 活跃时段水平条宽度百分比
function activePct(value) {
  return Math.max(4, Math.round((value / maxActive.value) * 100))
}

// 设备分布（示例数据）：value 为占比（%）
const deviceDist = [
  { name: 'iOS', value: 46, color: 'var(--brand-500)' },
  { name: 'Android', value: 38, color: 'var(--brand-300)' },
  { name: 'Web', value: 16, color: 'var(--background-400)' }
]

// 渠道分布（示例数据）：value 为占比（%）
const channelDist = [
  { name: 'APP', value: 58, color: 'var(--state-success)' },
  { name: '小程序', value: 27, color: 'var(--state-warning)' },
  { name: 'H5', value: 15, color: 'var(--brand-300)' }
]
</script>

<style scoped>
.page-wrapper { padding: 20px; }

/* 画像卡 */
.profile-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  padding: 20px 24px;
}
.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: var(--brand-50);
  color: var(--brand-600);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  flex-shrink: 0;
}
.profile-main { flex: 1; min-width: 200px; }
.profile-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-800);
  display: flex;
  align-items: center;
  gap: 10px;
}
.profile-sub {
  font-size: 12px;
  color: var(--text-400);
  margin-top: 4px;
}
.profile-spent { text-align: right; }
.profile-spent-label { font-size: 12px; color: var(--text-400); }
.profile-spent-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}

/* 信息网格 */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 24px;
  border-top: 1px solid var(--background-100);
  padding-top: 16px;
}
.info-item { display: flex; gap: 12px; font-size: 13px; }
.info-label {
  color: var(--text-400);
  width: 60px;
  flex-shrink: 0;
}
.info-value { color: var(--text-700); font-weight: 500; word-break: break-all; }

/* 分区标题 */
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-600);
  margin-bottom: 12px;
}

.count-cell {
  font-weight: 600;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.time-cell { font-size: 12px; color: var(--text-500); }

/* ===== 画像分析区块（设计稿补齐） ===== */
.charts-area {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
/* 双列布局，窄屏降为单列 */
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.chart-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  padding: 20px;
}
.chart-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.chart-card__head .section-title { margin-bottom: 0; }
/* 示例数据徽标 */
.demo-badge {
  font-size: 11px;
  color: var(--text-400);
  background: var(--background-200);
  border-radius: 999px;
  padding: 2px 8px;
  flex-shrink: 0;
}

/* 消费趋势柱状图 */
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 200px;
  padding-top: 16px;
  border-bottom: 1px solid var(--border);
}
.trend-chart__group {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}
.trend-chart__value {
  font-size: 10px;
  color: var(--text-400);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  transform: scale(0.9);
  transform-origin: bottom center;
}
.trend-chart__bar {
  width: 70%;
  max-width: 34px;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, var(--brand-400), var(--brand-500));
  transition: height 0.5s ease;
}
.trend-chart__label {
  font-size: 11px;
  color: var(--text-400);
  padding-top: 6px;
}

/* 用户标签云 */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag-cloud__item { margin-right: 0; }
.tag-cloud__sub {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-400);
}

/* 分段条形图 */
.stacked-bar { margin-bottom: 16px; }
.stacked-bar__track {
  display: flex;
  height: 14px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--background-200);
}
.stacked-bar__seg {
  height: 100%;
  transition: width 0.6s ease;
}
.stacked-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
}
.stacked-legend__item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-600);
}
.stacked-legend__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

/* 行为漏斗 */
.funnel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.funnel__item {
  display: flex;
  justify-content: center;
}
.funnel__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  min-width: 200px;
  background: linear-gradient(90deg, var(--brand-500), var(--brand-300));
  color: #fff;
  font-size: 13px;
}
/* 逐层递减的渐变，体现转化率下降 */
.funnel__item:nth-child(2) .funnel__bar { background: linear-gradient(90deg, var(--brand-400), var(--brand-200)); }
.funnel__item:nth-child(3) .funnel__bar { background: linear-gradient(90deg, var(--brand-300), var(--brand-100)); }
.funnel__item:nth-child(4) .funnel__bar { background: linear-gradient(90deg, var(--brand-200), var(--brand-100)); }
.funnel__name {
  font-weight: 600;
  white-space: nowrap;
}
.funnel__meta {
  font-size: 12px;
  opacity: 0.9;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* 活跃时段分布（水平条形图） */
.dist-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.dist-bar:last-child { margin-bottom: 0; }
.dist-bar__label {
  width: 56px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-600);
  flex-shrink: 0;
}
.dist-bar__track {
  flex: 1;
  height: 10px;
  border-radius: 999px;
  background: var(--background-200);
  overflow: hidden;
}
.dist-bar__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--brand-300), var(--brand-500));
  transition: width 0.6s ease;
}
.dist-bar__value {
  width: 32px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-500);
  flex-shrink: 0;
}

/* 设备 / 渠道分布 */
.sub-dist { margin-bottom: 18px; }
.sub-dist:last-child { margin-bottom: 0; }
.sub-dist__label {
  font-size: 12px;
  color: var(--text-400);
  margin-bottom: 8px;
}

/* 窄屏单列展示 */
@media (max-width: 960px) {
  .charts-row { grid-template-columns: 1fr; }
}
</style>
